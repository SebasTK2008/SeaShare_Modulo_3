package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.application.dto.RefundGatewayRequest;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.exception.GatewayResourceNotFoundException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundOperationType;
import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato HTTP del ACL de reembolso contra WireMock. */
class MercadoPagoRefundAdapterTest {

    private static WireMockServer wireMock;
    private MercadoPagoRefundAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void createAdapter() {
        wireMock.resetAll();
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setAccessToken("test-access-token");
        adapter = new MercadoPagoRefundAdapter(properties);
    }

    @Test
    void sendsThePartialRefundWithTheIdempotencyKey() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(okJson("{\"status\":\"approved\",\"id\":555,\"amount\":30000.00,"
                        + "\"status_detail\":\"accredited\",\"fee_details\":[{\"amount\":1000.00}]}")));

        RefundGatewayResult result = adapter.send(request());

        assertThat(result.status()).isEqualTo("approved");
        assertThat(result.externalId()).isEqualTo("555");
        assertThat(result.amount().amount()).isEqualByComparingTo("30000.00");
        assertThat(result.transactionCost().amount()).isEqualByComparingTo("1000.00");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .withHeader("X-Idempotency-Key", equalTo("refund:CANCELLED_STRICT:op-1"))
                .withRequestBody(containing("\"amount\":30000.00")));
    }

    @Test
    void badRequestIsANonRetryableBusinessRejection() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(aResponse().withStatus(400).withBody("{\"message\":\"invalid\"}")));

        assertThatThrownBy(() -> adapter.send(request())).isInstanceOf(GatewayBusinessRejectionException.class);
    }

    @Test
    void serverErrorIsACommunicationFailure() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/mp-payment-1/refunds")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.send(request())).isInstanceOf(GatewayCommunicationException.class);
    }

    @Test
    void fetchReturnsEmptyWhenTheGatewayDoesNotKnowTheRefund() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds/555"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(adapter.fetchRefund("mp-payment-1", "555")).isEmpty();
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/payments/mp-payment-1/refunds/555")));
    }

    @Test
    void listsTheRefundsOfThePayment() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(okJson("[{\"status\":\"approved\",\"id\":555,\"amount\":30000.00}]")));

        List<RefundGatewayResult> refunds = adapter.fetchRefunds("mp-payment-1");

        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).externalId()).isEqualTo("555");
    }

    @Test
    void fetchRefundsFailsWhenTheGatewayDoesNotKnowThePayment() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> adapter.fetchRefunds("mp-payment-1"))
                .isInstanceOf(GatewayResourceNotFoundException.class);
    }

    @Test
    void unknownPaymentsDoNotOpenTheCircuitBreaker() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/unknown/refunds")).willReturn(aResponse().withStatus(404)));
        wireMock.stubFor(post(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(okJson("{\"status\":\"approved\",\"id\":555,\"amount\":30000.00}")));

        // Mas llamadas que el minimo del breaker (5): todas son 404 de la pasarela, que esta sana.
        for (int call = 0; call < 8; call++) {
            assertThatThrownBy(() -> adapter.fetchRefunds("unknown"))
                    .isInstanceOf(GatewayResourceNotFoundException.class);
        }

        assertThat(adapter.send(request()).status()).isEqualTo("approved");
    }

    @Test
    void fetchRefundsFallsBackToThePaymentDetailWhenTheListEndpointIsNotAllowed() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1"))
                .willReturn(okJson("""
                        {
                          "id": 1355357033,
                          "status": "refunded",
                          "refunds": [
                            {"id": 1355334253, "status": "approved", "amount": 400000.00}
                          ]
                        }
                        """)));

        List<RefundGatewayResult> refunds = adapter.fetchRefunds("mp-payment-1");

        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).externalId()).isEqualTo("1355334253");
        assertThat(refunds.get(0).status()).isEqualTo("approved");
        assertThat(refunds.get(0).amount().amount()).isEqualByComparingTo("400000.00");
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/payments/mp-payment-1/refunds")));
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/payments/mp-payment-1")));
    }

    @Test
    void fetchRefundFallsBackToThePaymentDetailWhenTheDetailEndpointIsNotAllowed() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds/556"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1"))
                .willReturn(okJson("""
                        {
                          "id": 1355357033,
                          "refunds": [
                            {"id": 555, "status": "approved", "amount": 30000.00},
                            {"id": 556, "status": "approved", "amount": 400000.00}
                          ]
                        }
                        """)));

        Optional<RefundGatewayResult> result = adapter.fetchRefund("mp-payment-1", "556");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().externalId()).isEqualTo("556");
        assertThat(result.orElseThrow().amount().amount()).isEqualByComparingTo("400000.00");
    }

    @Test
    void fetchRefundFallbackReturnsEmptyWhenTheRefundIsNotInThePayment() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds/999"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1"))
                .willReturn(okJson("""
                        {"id": 1355357033, "refunds": [{"id": 555, "status": "approved", "amount": 30000.00}]}
                        """)));

        assertThat(adapter.fetchRefund("mp-payment-1", "999")).isEmpty();
    }

    @Test
    void fetchRefundFallbackReturnsEmptyWhenThePaymentDoesNotExist() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/unknown/refunds/999"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/unknown")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter.fetchRefund("unknown", "999")).isEmpty();
    }

    @Test
    void fetchRefundsFallbackFailsWhenThePaymentDoesNotExist() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/unknown/refunds"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/unknown"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> adapter.fetchRefunds("unknown"))
                .isInstanceOf(GatewayResourceNotFoundException.class);
    }

    @Test
    void fetchRefundsFallbackServerErrorIsACommunicationFailure() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1")).willReturn(serverError()));

        assertThatThrownBy(() -> adapter.fetchRefunds("mp-payment-1"))
                .isInstanceOf(GatewayCommunicationException.class);
    }

    @Test
    void methodNotAllowedWithSuccessfulFallbackDoesNotOpenTheCircuitBreaker() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(aResponse().withStatus(405).withHeader("Allow", "POST")));
        wireMock.stubFor(get(urlEqualTo("/v1/payments/mp-payment-1"))
                .willReturn(okJson("{\"id\": 1355357033, \"refunds\": []}")));
        wireMock.stubFor(post(urlEqualTo("/v1/payments/mp-payment-1/refunds"))
                .willReturn(okJson("{\"status\":\"approved\",\"id\":555,\"amount\":30000.00}")));

        for (int call = 0; call < 8; call++) {
            assertThat(adapter.fetchRefunds("mp-payment-1")).isEmpty();
        }

        assertThat(adapter.send(request()).status()).isEqualTo("approved");
    }

    private static RefundGatewayRequest request() {
        return new RefundGatewayRequest(RefundOperationType.REFUND, "mp-payment-1", UUID.randomUUID(),
                new Money(new BigDecimal("30000.00")), "refund:CANCELLED_STRICT:op-1");
    }
}
