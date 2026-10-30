package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.valueobject.Money;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrato HTTP del ACL de liquidacion contra WireMock. */
class MercadoPagoSettlementAdapterTest {

    private static final UUID RESERVATION_ID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static WireMockServer wireMock;
    private MercadoPagoSettlementAdapter adapter;

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
        adapter = new MercadoPagoSettlementAdapter(properties);
    }

    @Test
    void sendsTheDisbursementWithCollectorAndIdempotencyKey() {
        wireMock.stubFor(post(urlEqualTo("/v1/advanced_payments/mp-payment-1/disbursements"))
                .willReturn(okJson("{\"status\":\"approved\",\"id\":777,\"amount\":660000.00,"
                        + "\"status_detail\":\"accredited\"}")));

        GatewaySettlementResult result = adapter.send(request());

        assertThat(result.status()).isEqualTo("approved");
        assertThat(result.externalId()).isEqualTo("777");
        assertThat(result.amount().amount()).isEqualByComparingTo("660000.00");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/advanced_payments/mp-payment-1/disbursements"))
                .withHeader("X-Idempotency-Key", equalTo("settlement:COMPLETED:op-1"))
                .withRequestBody(containing("\"collector_id\":123456789"))
                .withRequestBody(containing("\"external_reference\":\"" + RESERVATION_ID + "\""))
                .withRequestBody(containing("\"amount\":660000.00")));
    }

    @Test
    void badRequestIsANonRetryableBusinessRejection() {
        wireMock.stubFor(post(urlEqualTo("/v1/advanced_payments/mp-payment-1/disbursements"))
                .willReturn(aResponse().withStatus(400).withBody("{\"message\":\"collector not linked\"}")));

        assertThatThrownBy(() -> adapter.send(request())).isInstanceOf(GatewayBusinessRejectionException.class);
    }

    @Test
    void fetchReturnsEmptyWhenTheGatewayDoesNotKnowTheSettlement() {
        wireMock.stubFor(get(urlEqualTo("/v1/advanced_payments/777")).willReturn(aResponse().withStatus(404)));

        assertThat(adapter.fetch("777")).isEmpty();
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/advanced_payments/777")));
    }

    private static GatewaySettlementRequest request() {
        return new GatewaySettlementRequest(123456789L, RESERVATION_ID,
                new Money(new BigDecimal("660000.00")), "mp-payment-1", "settlement:COMPLETED:op-1");
    }
}
