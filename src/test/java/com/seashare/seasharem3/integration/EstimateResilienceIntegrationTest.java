package com.seashare.seasharem3.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;
import com.seashare.seasharem3.application.dto.UnavailableBoat;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.service.EstimateBatchService;
import com.seashare.seasharem3.application.service.ProvideBaseRateService;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateProperties;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateRestClient;
import com.seashare.seasharem3.support.FixedClockPort;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración de UC01 con el adaptador real de UC02 ({@link FleetRateRestClient} + WireMock) y
 * {@link ProvideBaseRateService} reales, sin Docker ni Spring.
 *
 * <p>Cubre CE-004 (fallas simuladas de Flota sin estimaciones parciales) y la carga ligera de HU3
 * (50 embarcaciones en una sola consulta). El nombre termina en {@code Test} para que Surefire lo
 * ejecute: el proyecto no configura Failsafe.</p>
 */
class EstimateResilienceIntegrationTest {

    private static final String BASE_RATES_PATH = "/api/v1/fleet/base-rates";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final int MAX_BATCH_SIZE = 50;
    private static final FinancialParameters PARAMETERS = FinancialParameters.create(
            UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"), 1,
            new Percentage(new BigDecimal("10.00")),
            new Money(new BigDecimal("15000.00")),
            new Percentage(new BigDecimal("10.00")),
            new Percentage(new BigDecimal("25.00")),
            "admin@seashare.com",
            Instant.parse("2026-09-20T15:04:05Z"));

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

    @ParameterizedTest(name = "{0}")
    @EnumSource(FleetFailureScenario.class)
    void ce004_fleetFailureProducesNoPartialEstimate(FleetFailureScenario scenario)
            throws IOException {
        switch (scenario) {
            case TIMEOUT -> {
                wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                        .willReturn(ratesResponse(List.of(boat(0))).withFixedDelay(300)));
                assertFleetFailure(slice(wireMock.baseUrl(), Duration.ofMillis(100)));
            }
            case CLOSED_PORT -> assertFleetFailure(slice(closedPortUrl(), Duration.ofMillis(100)));
            case INVALID_JSON -> {
                wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH)).willReturn(okJson("{not-json")));
                assertFleetFailure(slice(wireMock.baseUrl(), Duration.ofMillis(300)));
            }
            case SERVER_ERROR -> {
                wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH)).willReturn(serverError()));
                assertFleetFailure(slice(wireMock.baseUrl(), Duration.ofMillis(300)));
            }
        }
    }

    @Test
    void ce004_parametersNotConfiguredFailsWithoutCallingFleet() {
        TestSlice slice = sliceWithoutParameters(wireMock.baseUrl(), Duration.ofMillis(300));

        assertThatThrownBy(
                () -> slice.batchService().estimate(new EstimateBatchCommand(List.of(boat(0)))))
                .isInstanceOf(ContractException.class)
                .extracting(e -> ((ContractException) e).code())
                .isEqualTo(ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED);
        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    @Test
    void hu3_fiftyBoatsAreEstimatedWithASingleFleetCallUnderLightLoad() {
        List<BoatId> boats = IntStream.range(0, MAX_BATCH_SIZE)
                .mapToObj(EstimateResilienceIntegrationTest::boat).toList();
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(ratesResponse(boats)));

        TestSlice slice = slice(wireMock.baseUrl(), Duration.ofMillis(500));

        long start = System.nanoTime();
        BatchEstimateResult result = slice.batchService()
                .estimate(new EstimateBatchCommand(boats));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result.estimates()).hasSize(MAX_BATCH_SIZE);
        assertThat(result.unavailable()).isEmpty();
        // TODAY (2026-10-25) es domingo: 350000 + 10% fin de semana = 385000, + seguro 15000.
        assertThat(result.estimates()).allSatisfy(estimate ->
                assertThat(estimate.estimatedTotal().toOutputAmount())
                        .isEqualByComparingTo(new BigDecimal("400000.00")));
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        wireMock.verify(1, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    @Test
    void unavailableReportsExactlyTheBoatsTheRealUc02Omits() {
        List<BoatId> boats = IntStream.range(0, MAX_BATCH_SIZE)
                .mapToObj(EstimateResilienceIntegrationTest::boat).toList();
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(ratesResponse(boats.subList(0, MAX_BATCH_SIZE - 1))));

        TestSlice slice = slice(wireMock.baseUrl(), Duration.ofMillis(500));

        BatchEstimateResult result = slice.batchService()
                .estimate(new EstimateBatchCommand(boats));

        assertThat(result.estimates()).hasSize(MAX_BATCH_SIZE - 1);
        assertThat(result.unavailable()).containsExactly(
                new UnavailableBoat(boats.get(MAX_BATCH_SIZE - 1), UnavailableBoat.NO_BASE_RATE));
        assertThat(slice.failureRecorder().failures())
                .anySatisfy(failure -> assertThat(failure.useCase()).isEqualTo("UC02"));
    }

    private void assertFleetFailure(TestSlice slice) {
        assertThatThrownBy(() -> slice.batchService()
                .estimate(new EstimateBatchCommand(List.of(boat(0)))))
                .isInstanceOfSatisfying(BaseRateException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(BaseRateFailureReason.FLEET_UNAVAILABLE));
    }

    private TestSlice slice(String baseUrl, Duration readTimeout) {
        return buildSlice(baseUrl, readTimeout, true);
    }

    private TestSlice sliceWithoutParameters(String baseUrl, Duration readTimeout) {
        return buildSlice(baseUrl, readTimeout, false);
    }

    private TestSlice buildSlice(String baseUrl, Duration readTimeout, boolean withParameters) {
        FleetRateProperties properties = new FleetRateProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setReadTimeout(readTimeout);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setWaitDuration(Duration.ofMillis(10));
        properties.getCircuitBreaker().setFailureRateThreshold(50.0f);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(10);
        properties.getCircuitBreaker().setSlidingWindowSize(10);
        properties.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofSeconds(5));

        InMemoryFinancialParametersRepository paramsRepo = new InMemoryFinancialParametersRepository();
        if (withParameters) {
            paramsRepo.saveNewVersion(PARAMETERS);
        }
        InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();
        ProvideBaseRateService uc02 = new ProvideBaseRateService(
                new FleetRateRestClient(properties, Mappers.getMapper(FleetRateMapper.class)),
                paramsRepo,
                new DynamicRatePolicy(new HighSeasonCalendar()),
                failureRecorder);
        EstimateBatchService batchService = new EstimateBatchService(uc02, paramsRepo,
                new FixedClockPort(TODAY), new PricingCalculator(), MAX_BATCH_SIZE);
        return new TestSlice(batchService, failureRecorder);
    }

    private static String closedPortUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort();
        }
    }

    private static BoatId boat(int index) {
        return new BoatId(UUID.nameUUIDFromBytes(("boat-" + index).getBytes()));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder ratesResponse(
            List<BoatId> boats) {
        String rates = boats.stream()
                .map(id -> """
                        {"boat_id":"%s","base_rate":"350000.00"}""".formatted(id.value()))
                .collect(Collectors.joining(","));
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"rates\":[" + rates + "]}");
    }

    private enum FleetFailureScenario {
        TIMEOUT,
        CLOSED_PORT,
        INVALID_JSON,
        SERVER_ERROR
    }

    private record TestSlice(EstimateBatchService batchService,
            InMemoryFailureRecorder failureRecorder) {
    }
}
