package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.application.dto.RefundGatewayRequest;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundOperationType;
import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    private static RefundGatewayRequest request() {
        return new RefundGatewayRequest(RefundOperationType.REFUND, "mp-payment-1", UUID.randomUUID(),
                new Money(new BigDecimal("30000.00")), "refund:CANCELLED_STRICT:op-1");
    }
}
