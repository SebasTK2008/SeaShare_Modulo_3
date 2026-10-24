package com.seashare.seasharem3.application.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.BoatBaseRateDto;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetBaseRatesRequestDto;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateProperties;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateRestClient;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

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
 * Pruebas de aceptación de UC02: CE-001 (precisión y vigencia de la condición dinámica), CE-002
 * (tarifa dinámica solo en UC02 y contratos sin tipo/categoría) y CE-003 (fallas de Flota sin
 * tarifa asumida).
 *
 * <p>CE-003 se ejecuta como una slice sin Docker: WireMock + los componentes reales de UC02
 * ({@link FleetRateRestClient}, {@link ProvideBaseRateService}, {@link DynamicRatePolicy}) y los
 * dobles en memoria de los puertos compartidos.</p>
 */
class BaseRateAcceptanceTest {

    private static final String BASE_RATES_PATH = "/api/v1/fleet/base-rates";
    private static final LocalDate WEEKEND_DATE = LocalDate.of(2026, 10, 3);
    private static final BoatId BOAT_A = new BoatId(
            UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final BoatId BOAT_B = new BoatId(
            UUID.fromString("4d7a9c10-5a2b-4e6f-8d01-3f2c1a548b3e"));
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("350000.00")));
    private static final FinancialParameters PARAMETERS = parameters(
            new Percentage(new BigDecimal("10.00")), new Percentage(new BigDecimal("25.00")));

    private final DynamicRatePolicy policy = new DynamicRatePolicy(new HighSeasonCalendar());

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

    @ParameterizedTest(name = "[{index}] {2}: {0} -> {1}")
    @MethodSource("ce001Cases")
    void ce001_tarifa_refleja_condicion_dinamica_vigente(LocalDate date, String expectedRate,
            String condition) {
        BaseRate result = policy.apply(BASE_RATE, date, PARAMETERS);

        assertThat(result.amount().amount())
                .as("Condición %s para %s", condition, date)
                .isEqualTo(new BigDecimal(expectedRate));
    }

    @Test
    void ce001_conserva_la_precision_de_cuatro_decimales_sin_redondeo_final() {
        FinancialParameters fractionalParameters = parameters(
                new Percentage(new BigDecimal("12.3456")),
                new Percentage(new BigDecimal("25.4321")));

        BaseRate weekend = policy.apply(BASE_RATE, WEEKEND_DATE, fractionalParameters);
        BaseRate highSeason = policy.apply(BASE_RATE, LocalDate.of(2026, 10, 5), fractionalParameters);
        BaseRate overlap = policy.apply(BASE_RATE, LocalDate.of(2026, 10, 11), fractionalParameters);

        assertThat(weekend.amount().amount()).isEqualTo(new BigDecimal("393209.6000"));
        assertThat(highSeason.amount().amount()).isEqualTo(new BigDecimal("439012.3500"));
        assertThat(overlap.amount().amount()).isEqualTo(new BigDecimal("439012.3500"));
        assertThat(weekend.amount().amount().scale()).isEqualTo(4);
        assertThat(highSeason.amount().amount().scale()).isEqualTo(4);
        assertThat(overlap.amount().amount().scale()).isEqualTo(4);
    }

    @Test
    void ce002_tarifa_dinamica_solo_en_uc02_sin_tipo_ni_categoria() {
        List<Class<?>> contracts = List.of(
                BoatBaseRateDto.class,
                FleetBaseRatesRequestDto.class,
                BaseRateResult.class);

        for (Class<?> contract : contracts) {
            assertThat(recordComponentNames(contract))
                    .as("Atributos de %s", contract.getSimpleName())
                    .noneMatch(BaseRateAcceptanceTest::isClassificationAttribute);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(FleetFailureScenario.class)
    void ce003_fallas_de_flota_manejadas_sin_tarifa_asumida(FleetFailureScenario scenario)
            throws IOException {
        switch (scenario) {
            case TIMEOUT -> assertTimeoutFailure();
            case CLOSED_PORT -> assertClosedPortFailure();
            case INVALID_JSON -> assertInvalidJsonFailure();
            case SERVER_ERROR -> assertServerErrorFailure();
            case OPEN_CIRCUIT -> assertOpenCircuitFailure();
        }
    }

    private static void assertTimeoutFailure() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(validResponse().withFixedDelay(300)));

        TestSlice slice = slice(wireMock.baseUrl(), Duration.ofMillis(100), 2);

        assertControlledFleetFailure(slice);
        assertThat(slice.failureRecorder().failures()).hasSize(1);
    }

    private static void assertClosedPortFailure() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        TestSlice slice = slice("http://localhost:" + closedPort, Duration.ofMillis(100), 2);

        assertControlledFleetFailure(slice);
        assertThat(slice.failureRecorder().failures()).hasSize(1);
    }

    private static void assertInvalidJsonFailure() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("{not-json")));

        TestSlice slice = slice(wireMock.baseUrl(), Duration.ofMillis(300), 2);

        assertControlledFleetFailure(slice);
        assertThat(slice.failureRecorder().failures()).hasSize(1);
    }

    private static void assertServerErrorFailure() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(serverError()));

        TestSlice slice = slice(wireMock.baseUrl(), Duration.ofMillis(300), 2);

        assertControlledFleetFailure(slice);
        assertThat(slice.failureRecorder().failures()).hasSize(1);
    }

    private static void assertOpenCircuitFailure() {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(serverError()));

        TestSlice slice = slice(wireMock.baseUrl(), Duration.ofMillis(300), 1);

        assertControlledFleetFailure(slice);
        assertControlledFleetFailure(slice);

        assertThat(slice.failureRecorder().failures()).hasSize(2);
        wireMock.verify(1, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    private static void assertControlledFleetFailure(TestSlice slice) {
        assertThatThrownBy(() -> slice.service().provideBaseRates(command()))
                .isInstanceOfSatisfying(BaseRateException.class, exception -> {
                    assertThat(exception.reason())
                            .isEqualTo(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE);
                    assertThat(exception.isTransient()).isTrue();
                });

        List<InMemoryFailureRecorder.RecordedFailure> failures = slice.failureRecorder().failures();
        assertThat(failures).isNotEmpty();
        assertThat(failures).last().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC02");
            assertThat(failure.reservationId()).isNull();
            assertThat(failure.reason())
                    .isEqualTo(BaseRateException.BaseRateFailureReason.FLEET_UNAVAILABLE.name());
            assertThat(failure.payloadRef())
                    .contains("evaluatedDate=" + WEEKEND_DATE)
                    .contains(BOAT_A.value().toString())
                    .contains(BOAT_B.value().toString());
        });
    }

    private static TestSlice slice(String baseUrl, Duration readTimeout, int minimumNumberOfCalls) {
        FleetRateProperties properties = new FleetRateProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setReadTimeout(readTimeout);
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setWaitDuration(Duration.ofMillis(10));
        properties.getCircuitBreaker().setFailureRateThreshold(50.0f);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(minimumNumberOfCalls);
        properties.getCircuitBreaker().setSlidingWindowSize(minimumNumberOfCalls);
        properties.getCircuitBreaker().setWaitDurationInOpenState(Duration.ofSeconds(5));
        properties.getCircuitBreaker().setPermittedNumberOfCallsInHalfOpenState(1);

        FleetRateRestClient fleetRatePort = new FleetRateRestClient(properties,
                Mappers.getMapper(FleetRateMapper.class));
        InMemoryFinancialParametersRepository parametersRepository =
                new InMemoryFinancialParametersRepository();
        parametersRepository.saveNewVersion(PARAMETERS);
        InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();
        ProvideBaseRateService service = new ProvideBaseRateService(fleetRatePort,
                parametersRepository, new DynamicRatePolicy(new HighSeasonCalendar()), failureRecorder);

        return new TestSlice(service, failureRecorder);
    }

    private static ProvideBaseRateCommand command() {
        return new ProvideBaseRateCommand(List.of(BOAT_A, BOAT_B), WEEKEND_DATE);
    }

    private static ResponseDefinitionBuilder validResponse() {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"rates":[{"boat_id":"%s","base_rate":"350000.00"}]}
                        """.formatted(BOAT_A.value()));
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static boolean isClassificationAttribute(String attribute) {
        String normalized = attribute.toLowerCase(Locale.ROOT);
        return normalized.contains("type")
                || normalized.contains("category")
                || normalized.contains("tipo")
                || normalized.contains("categoria")
                || normalized.contains("class");
    }

    private static FinancialParameters parameters(Percentage weekendIncrease,
            Percentage highSeasonIncrease) {
        return FinancialParameters.create(
                UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"),
                1,
                new Percentage(new BigDecimal("10.00")),
                new Money(new BigDecimal("15000.00")),
                weekendIncrease,
                highSeasonIncrease,
                "admin@seashare.com",
                Instant.parse("2026-09-20T15:04:05Z"));
    }

    private static Stream<Arguments> ce001Cases() {
        return Stream.of(
                // Fecha regular: no aplica ajuste.
                Arguments.of(LocalDate.parse("2026-09-30"), "350000.0000", "regular"),
                Arguments.of(LocalDate.parse("2026-07-31"), "350000.0000", "regular tras mitad de año"),
                Arguments.of(LocalDate.parse("2026-10-13"), "350000.0000", "regular tras receso"),
                Arguments.of(LocalDate.parse("2026-04-01"), "350000.0000", "regular antes de Semana Santa"),
                Arguments.of(LocalDate.parse("2026-08-17"), "350000.0000", "festivo entre semana"),
                Arguments.of(LocalDate.parse("2027-01-18"), "350000.0000", "regular tras fin de año"),

                // Fin de semana fuera de temporada alta.
                Arguments.of(LocalDate.parse("2026-10-03"), "385000.0000", "fin de semana"),
                Arguments.of(LocalDate.parse("2026-11-14"), "385000.0000", "sábado previo a fin de año"),
                Arguments.of(LocalDate.parse("2027-01-16"), "385000.0000", "sábado tras fin de año"),
                Arguments.of(LocalDate.parse("2026-05-31"), "385000.0000", "domingo previo a mitad de año"),
                Arguments.of(LocalDate.parse("2026-10-04"), "385000.0000", "domingo previo a receso"),
                Arguments.of(LocalDate.parse("2026-04-04"), "385000.0000", "sábado de Semana Santa"),
                Arguments.of(LocalDate.parse("2026-04-05"), "385000.0000", "domingo de Pascua"),

                // Temporada alta entre semana.
                Arguments.of(LocalDate.parse("2026-01-01"), "437500.0000", "enero dentro de fin de año"),
                Arguments.of(LocalDate.parse("2026-01-15"), "437500.0000", "límite de fin de año"),
                Arguments.of(LocalDate.parse("2026-11-16"), "437500.0000", "temporada alta fin de año"),
                Arguments.of(LocalDate.parse("2027-01-15"), "437500.0000", "límite 15-ene"),
                Arguments.of(LocalDate.parse("2026-06-01"), "437500.0000", "inicio mitad de año"),
                Arguments.of(LocalDate.parse("2026-07-30"), "437500.0000", "fin mitad de año"),
                Arguments.of(LocalDate.parse("2026-10-05"), "437500.0000", "inicio receso"),
                Arguments.of(LocalDate.parse("2026-10-07"), "437500.0000", "receso"),
                Arguments.of(LocalDate.parse("2026-10-12"), "437500.0000", "límite receso"),

                // Coincidencia fin de semana + temporada alta: manda el mayor ajuste.
                Arguments.of(LocalDate.parse("2026-06-06"), "437500.0000", "sábado en mitad de año"),
                Arguments.of(LocalDate.parse("2026-10-11"), "437500.0000", "domingo en receso"),
                Arguments.of(LocalDate.parse("2026-11-15"), "437500.0000", "domingo en fin de año"),
                Arguments.of(LocalDate.parse("2026-12-20"), "437500.0000", "domingo en fin de año"),
                Arguments.of(LocalDate.parse("2027-01-10"), "437500.0000", "domingo en enero"),

                // Pascua 2024-2030: Jueves/Viernes Santo son temporada alta; Pascua solo es fin de semana.
                Arguments.of(LocalDate.parse("2024-03-28"), "437500.0000", "Jueves Santo 2024"),
                Arguments.of(LocalDate.parse("2024-03-29"), "437500.0000", "Viernes Santo 2024"),
                Arguments.of(LocalDate.parse("2024-03-31"), "385000.0000", "Pascua 2024"),
                Arguments.of(LocalDate.parse("2025-04-17"), "437500.0000", "Jueves Santo 2025"),
                Arguments.of(LocalDate.parse("2025-04-18"), "437500.0000", "Viernes Santo 2025"),
                Arguments.of(LocalDate.parse("2025-04-20"), "385000.0000", "Pascua 2025"),
                Arguments.of(LocalDate.parse("2027-03-25"), "437500.0000", "Jueves Santo 2027"),
                Arguments.of(LocalDate.parse("2027-03-26"), "437500.0000", "Viernes Santo 2027"),
                Arguments.of(LocalDate.parse("2027-03-28"), "385000.0000", "Pascua 2027"),
                Arguments.of(LocalDate.parse("2028-04-13"), "437500.0000", "Jueves Santo 2028"),
                Arguments.of(LocalDate.parse("2028-04-14"), "437500.0000", "Viernes Santo 2028"),
                Arguments.of(LocalDate.parse("2028-04-16"), "385000.0000", "Pascua 2028"),
                Arguments.of(LocalDate.parse("2029-03-29"), "437500.0000", "Jueves Santo 2029"),
                Arguments.of(LocalDate.parse("2029-03-30"), "437500.0000", "Viernes Santo 2029"),
                Arguments.of(LocalDate.parse("2029-04-01"), "385000.0000", "Pascua 2029"),
                Arguments.of(LocalDate.parse("2030-04-18"), "437500.0000", "Jueves Santo 2030"),
                Arguments.of(LocalDate.parse("2030-04-19"), "437500.0000", "Viernes Santo 2030"),
                Arguments.of(LocalDate.parse("2030-04-21"), "385000.0000", "Pascua 2030"));
    }

    private enum FleetFailureScenario {
        TIMEOUT,
        CLOSED_PORT,
        INVALID_JSON,
        SERVER_ERROR,
        OPEN_CIRCUIT
    }

    private record TestSlice(ProvideBaseRateService service, InMemoryFailureRecorder failureRecorder) {
    }
}
