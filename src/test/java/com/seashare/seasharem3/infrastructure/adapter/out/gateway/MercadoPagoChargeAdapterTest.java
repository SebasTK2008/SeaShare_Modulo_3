package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.application.dto.ChargeGatewayRequest;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.infrastructure.config.GatewayProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Contrato HTTP del adaptador de Mercado Pago contra WireMock: cuerpo del comando de cobro, cabecera
 * idempotente, clasificacion de errores y consulta del pago.
 * Fuente: UC05 plan T012, T013 y T021; contrato {@code pasarela-comando-cobro.md} §2, §4 y §5.
 */
class MercadoPagoChargeAdapterTest {

    private static final String OPERATION_KEY = "reservation|PENDING|2026-10-20T15:04:05Z";
    private static final String RESERVATION_ID = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10";

    private static WireMockServer wireMock;

    private MercadoPagoChargeAdapter adapter;

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
        adapter = new MercadoPagoChargeAdapter(properties);
    }

    private static ChargeGatewayRequest request() {
        return new ChargeGatewayRequest(new Money(new BigDecimal("990000.00")), "token-123",
                "Reserva " + RESERVATION_ID, 1, "CREDIT_CARD", "arrendatario@example.com", RESERVATION_ID,
                OPERATION_KEY);
    }

    @Test
    void sendsTheContractBodyWithTheIdempotencyKeyOfTheAttempt() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .willReturn(okJson("{\"status\":\"approved\",\"id\":987654,\"status_detail\":\"accredited\"}")));

        ChargeGatewayResult result = adapter.send(request());

        assertThat(result.status()).isEqualTo("approved");
        assertThat(result.externalId()).isEqualTo("987654");
        assertThat(result.statusDetail()).isEqualTo("accredited");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/payments"))
                .withHeader("Authorization", equalTo("Bearer test-access-token"))
                .withHeader("X-Idempotency-Key", equalTo(OPERATION_KEY))
                .withRequestBody(containing("\"transaction_amount\":990000.00"))
                .withRequestBody(containing("\"token\":\"token-123\""))
                .withRequestBody(containing("\"installments\":1"))
                .withRequestBody(containing("\"payment_method_id\":\"credit_card\""))
                .withRequestBody(containing("\"email\":\"arrendatario@example.com\""))
                .withRequestBody(containing("\"external_reference\":\"" + RESERVATION_ID + "\"")));
    }

    @Test
    void doesNotSendAnySensitiveCardData() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments")).willReturn(okJson("{\"status\":\"approved\",\"id\":1}")));

        adapter.send(request());

        String body = wireMock.findAll(postRequestedFor(urlEqualTo("/v1/payments"))).get(0).getBodyAsString();
        assertThat(body).contains("\"transaction_amount\"", "\"token\"", "\"description\"", "\"installments\"",
                "\"payment_method_id\"", "\"payer\"", "\"external_reference\"");
        assertThat(body).doesNotContain("card_number", "cvv", "expiration", "\"pan\"");
    }

    @Test
    void rejectsTheTokenWhenTheGatewayAnswersBadRequest() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"invalid token\"}")));

        assertThatExceptionOfType(InvalidPaymentTokenException.class).isThrownBy(() -> adapter.send(request()));
    }

    @Test
    void reportsACommunicationFailureWhenTheGatewayIsUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments")).willReturn(serviceUnavailable()));

        assertThatExceptionOfType(GatewayCommunicationException.class).isThrownBy(() -> adapter.send(request()));
    }

    @Test
    void reportsACommunicationFailureWhenTheGatewayFails() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments")).willReturn(serverError()));

        assertThatExceptionOfType(GatewayCommunicationException.class).isThrownBy(() -> adapter.send(request()));
    }

    @Test
    void reportsACommunicationFailureWhenTheGatewayDoesNotAnswerInTime() {
        GatewayProperties properties = new GatewayProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setAccessToken("test-access-token");
        properties.setReadTimeout(Duration.ofMillis(150));
        properties.setConnectTimeout(Duration.ofMillis(150));
        MercadoPagoChargeAdapter slowAdapter = new MercadoPagoChargeAdapter(properties);
        wireMock.stubFor(post(urlEqualTo("/v1/payments")).willReturn(okJson("{\"status\":\"approved\",\"id\":1}")
                .withFixedDelay(1000)));

        assertThatExceptionOfType(GatewayCommunicationException.class)
                .isThrownBy(() -> slowAdapter.send(request()));
    }

    @Test
    void queriesThePaymentByItsExternalReference() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/987654"))
                .willReturn(okJson("{\"status\":\"approved\",\"id\":987654,\"status_detail\":\"accredited\"}")));

        Optional<ChargeGatewayResult> result = adapter.fetchPayment("987654");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("approved");
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/payments/987654")));
    }

    @Test
    void returnsNoResultWhenTheGatewayDoesNotKnowThePayment() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/999999")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter.fetchPayment("999999")).isEmpty();
    }

    @Test
    void queriesTheRejectedPayment() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/987654"))
                .willReturn(okJson("{\"status\":\"rejected\",\"id\":987654,"
                        + "\"status_detail\":\"cc_rejected_insufficient_amount\"}")));

        Optional<ChargeGatewayResult> result = adapter.fetchPayment("987654");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("rejected");
        assertThat(result.get().statusDetail()).isEqualTo("cc_rejected_insufficient_amount");
    }

    @Test
    void queriesTheExpiredPayment() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/987654"))
                .willReturn(okJson("{\"status\":\"expired\",\"id\":987654,\"status_detail\":\"expired\"}")));

        Optional<ChargeGatewayResult> result = adapter.fetchPayment("987654");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("expired");
    }

    @Test
    void queriesThePaymentInProcess() {
        wireMock.stubFor(get(urlEqualTo("/v1/payments/987654"))
                .willReturn(okJson("{\"status\":\"in_process\",\"id\":987654,\"status_detail\":\"pending\"}")));

        Optional<ChargeGatewayResult> result = adapter.fetchPayment("987654");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("in_process");
    }
}
