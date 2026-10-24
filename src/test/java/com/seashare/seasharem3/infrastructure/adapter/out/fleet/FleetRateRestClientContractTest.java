package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato HTTP del adaptador de Flota contra WireMock.
 *
 * <p>Cubre respuesta feliz, tarifa ausente, cuerpo inválido, tarifa no numérica/negativa/cero,
 * 500, timeout, puerto cerrado y circuito abierto. En todos los fallos la salida es
 * {@code FLEET_UNAVAILABLE} y nunca se construye una tarifa asumida.</p>
 */
class FleetRateRestClientContractTest {

    private static final String BASE_RATES_PATH = "/api/v1/fleet/base-rates";
    private static final BoatId BOAT_A = new BoatId(
            UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final BoatId BOAT_B = new BoatId(
            UUID.fromString("4d7a9c10-5a2b-4e6f-8d01-3f2c1a548b3e"));
    private static final BaseRate RATE_A = new BaseRate(new Money(new BigDecimal("350000.00")));
    private static final BaseRate RATE_B = new BaseRate(new Money(new BigDecimal("125000.50")));

    private static WireMockServer wireMock;

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
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void mapsAValidBatchResponseAndSendsTheContractRequest() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("""
                        {"rates":[{"boat_id":"%s","base_rate":"350000.00"}]}
                        """.formatted(BOAT_A.value()))));

        Map<BoatId, BaseRate> result = client().findBaseRates(Set.of(BOAT_A));

        assertThat(result).hasSize(1).containsEntry(BOAT_A, RATE_A);
        wireMock.verify(postRequestedFor(urlEqualTo(BASE_RATES_PATH))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.boat_ids[0]",
                        equalTo(BOAT_A.value().toString()))));
    }

    @Test
    void omitsBoatsWithoutBaseRateAndKeepsTheValidOnes() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("""
                        {"rates":[
                          {"boat_id":"%s"},
                          {"boat_id":"%s","base_rate":"125000.50"}
                        ]}
                        """.formatted(BOAT_A.value(), BOAT_B.value()))));

        Map<BoatId, BaseRate> result = client().findBaseRates(Set.of(BOAT_A, BOAT_B));

        assertThat(result).hasSize(1).containsEntry(BOAT_B, RATE_B);
    }

    @Test
    void returnsAnEmptyMapWhenFleetReturnsNoRates() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("{\"rates\":[]}")));

        assertThat(client().findBaseRates(Set.of(BOAT_A))).isEmpty();
    }

    @Test
    void doesNotCallFleetForAnEmptyBoatSet() {
        assertThat(client().findBaseRates(Set.of())).isEmpty();

        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    @Test
    void mapsInvalidJsonToFleetUnavailable() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("{not-json")));

        assertFleetUnavailable(() -> client().findBaseRates(Set.of(BOAT_A)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "-1.00", "0.00"})
    void mapsInvalidBaseRateToFleetUnavailable(String baseRate) {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("""
                        {"rates":[{"boat_id":"%s","base_rate":"%s"}]}
                        """.formatted(BOAT_A.value(), baseRate))));

        assertFleetUnavailable(() -> client().findBaseRates(Set.of(BOAT_A)));
    }

    @Test
    void mapsServerErrorToFleetUnavailableAndRetriesOnce() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(serverError()));

        assertFleetUnavailable(() -> client().findBaseRates(Set.of(BOAT_A)));
        wireMock.verify(2, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    @Test
    void mapsReadTimeoutToFleetUnavailable() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(300)
                        .withBody("""
                                {"rates":[{"boat_id":"%s","base_rate":"350000.00"}]}
                                """.formatted(BOAT_A.value()))));

        assertFleetUnavailable(() -> client(Duration.ofMillis(100))
                .findBaseRates(Set.of(BOAT_A)));
    }

    @Test
    void mapsClosedPortToFleetUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertFleetUnavailable(() -> client("http://localhost:" + closedPort,
                Duration.ofMillis(100)).findBaseRates(Set.of(BOAT_A)));
    }

    @Test
    void mapsOpenCircuitBreakerToFleetUnavailableWithoutCallingFleetAgain() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(serverError()));
        FleetRateRestClient client = client();

        assertFleetUnavailable(() -> client.findBaseRates(Set.of(BOAT_A)));
        assertFleetUnavailable(() -> client.findBaseRates(Set.of(BOAT_A)));

        wireMock.verify(2, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    private FleetRateRestClient client() {
        return client(wireMock.baseUrl(), Duration.ofMillis(300));
    }

    private FleetRateRestClient client(Duration readTimeout) {
        return client(wireMock.baseUrl(), readTimeout);
    }

    private FleetRateRestClient client(String baseUrl, Duration readTimeout) {
        FleetRateProperties properties = new FleetRateProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setReadTimeout(readTimeout);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setWaitDuration(Duration.ofMillis(10));
        properties.getCircuitBreaker().setFailureRateThreshold(50.0f);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        properties.getCircuitBreaker().setSlidingWindowSize(2);
        properties.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofSeconds(5));
        properties.getCircuitBreaker().setPermittedNumberOfCallsInHalfOpenState(1);
        return new FleetRateRestClient(properties, Mappers.getMapper(FleetRateMapper.class));
    }

    private static void assertFleetUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BaseRateException.class)
                .satisfies(exception -> assertThat(((BaseRateException) exception).reason())
                        .isEqualTo(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE));
    }
}
