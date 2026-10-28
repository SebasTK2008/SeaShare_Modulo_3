package com.seashare.seasharem3.application.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.ReservationInfoListener;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessage;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ReservationInfoMessageMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateProperties;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateRestClient;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import com.seashare.seasharem3.support.MessagingJsonMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de aceptación CE-004 de UC03: el sistema nunca consulta a Flota por {@code owner_id} ni
 * {@code max_capacity}. Se usa el {@link ProvideBaseRateService} real de UC02 con WireMock como
 * Flota, de modo que la prueba detecta regresiones en la inclusión.
 */
class ReservationInformationSourceIntegrityAcceptanceTest {

    private static final String BASE_RATES_PATH = "/api/v1/fleet/base-rates";
    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OWNER_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final LocalDate START = LocalDate.of(2026, 12, 20);
    private static final LocalDate END = LocalDate.of(2026, 12, 22);

    private static final FinancialParameters PARAMETERS = FinancialParameters.create(
            UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"),
            1,
            new Percentage(new BigDecimal("10.00")),
            new Money(new BigDecimal("15000.00")),
            new Percentage(new BigDecimal("10.00")),
            new Percentage(new BigDecimal("25.00")),
            "admin@seashare.com",
            Instant.parse("2026-09-20T15:04:05Z"));

    private static WireMockServer wireMock;

    private JsonMapper jsonMapper;
    private InMemoryReservationInformationRepository repository;
    private InMemoryFailureRecorder failureRecorder;
    private ReservationInfoListener listener;

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
    void setUp() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("""
                        {"rates":[{"boat_id":"%s","base_rate":"350000.00"}]}
                        """.formatted(BOAT_UUID))));

        jsonMapper = MessagingJsonMapper.create();
        repository = new InMemoryReservationInformationRepository();
        failureRecorder = new InMemoryFailureRecorder();

        FleetRateProperties properties = new FleetRateProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.getRetry().setMaxAttempts(1);
        properties.getRetry().setWaitDuration(Duration.ofMillis(10));
        properties.getCircuitBreaker().setMinimumNumberOfCalls(5);
        properties.getCircuitBreaker().setSlidingWindowSize(5);

        FleetRateRestClient fleetRatePort = new FleetRateRestClient(properties,
                Mappers.getMapper(FleetRateMapper.class));
        InMemoryFinancialParametersRepository parametersRepository =
                new InMemoryFinancialParametersRepository();
        parametersRepository.saveNewVersion(PARAMETERS);
        ProvideBaseRateService uc02 = new ProvideBaseRateService(fleetRatePort, parametersRepository,
                new DynamicRatePolicy(new HighSeasonCalendar()), failureRecorder);
        RegisterReservationInformationService service = new RegisterReservationInformationService(uc02,
                repository);
        listener = new ReservationInfoListener(service, new ReservationInfoMessageMapper(),
                new AmqpListenerFailurePolicy(failureRecorder), jsonMapper);
    }

    @Test
    void ce004_flotaRecibeSoloBoatIdsSinPropietarioNiCapacidad() {
        listener.onMessage(messageWithPayload(new ReservationInfoMessage(
                RESERVATION_UUID, BOAT_UUID, START, END, 4, OWNER_UUID, 6)));

        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
        assertThat(requests).hasSize(1);
        String body = requests.get(0).getBodyAsString();
        assertThat(body)
                .contains("boat_ids")
                .contains(BOAT_UUID.toString())
                .doesNotContain("owner_id")
                .doesNotContain("max_capacity");
        assertThat(repository.findByReservationId(RESERVATION_ID)).isPresent();
        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void ce004_missingOwnerDoesNotQueryFlota() {
        listener.onMessage(messageWithPayload(new ReservationInfoMessage(
                RESERVATION_UUID, BOAT_UUID, START, END, 4, null, 6)));

        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
        assertThat(repository.findByReservationId(RESERVATION_ID)).isEmpty();
        assertFailureRecorded();
    }

    @Test
    void ce004_missingCapacityDoesNotQueryFlota() {
        listener.onMessage(messageWithPayload(new ReservationInfoMessage(
                RESERVATION_UUID, BOAT_UUID, START, END, 4, OWNER_UUID, null)));

        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
        assertThat(repository.findByReservationId(RESERVATION_ID)).isEmpty();
        assertFailureRecorded();
    }

    @Test
    void ce004_invalidPassengersDoesNotQueryFlota() {
        listener.onMessage(messageWithPayload(new ReservationInfoMessage(
                RESERVATION_UUID, BOAT_UUID, START, END, 7, OWNER_UUID, 6)));

        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
        assertThat(repository.findByReservationId(RESERVATION_ID)).isEmpty();
        assertFailureRecorded();
    }

    private void assertFailureRecorded() {
        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC03");
            assertThat(failure.reservationId()).isEqualTo(RESERVATION_UUID);
            assertThat(failure.reason()).isNotBlank();
        });
    }

    private Message messageWithPayload(ReservationInfoMessage payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(jsonMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8), properties);
    }
}
