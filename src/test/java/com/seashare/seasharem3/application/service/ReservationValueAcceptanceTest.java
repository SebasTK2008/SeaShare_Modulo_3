package com.seashare.seasharem3.application.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.domain.exception.FinancialParametersNotConfiguredException;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.service.ReservationValueCalculator;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateMapper;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateProperties;
import com.seashare.seasharem3.infrastructure.adapter.out.fleet.FleetRateRestClient;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import com.seashare.seasharem3.support.NoOpReservationLockPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de aceptación de UC04 (CE-001…CE-004) con los componentes reales: UC03 registra la
 * información, UC04 la calcula y los puertos compartidos se usan con dobles en memoria. Se ejecutan
 * sin Spring ni Docker; solo CE-002 necesita WireMock como Flota.
 */
class ReservationValueAcceptanceTest {

    private static final String BASE_RATES_PATH = "/api/v1/fleet/base-rates";
    private static final UUID RESERVATION_UUID = UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10");
    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OWNER_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    private static final ReservationId RESERVATION_ID = new ReservationId(RESERVATION_UUID);
    private static final LocalDate START = LocalDate.of(2026, 9, 10);
    private static final LocalDate END = LocalDate.of(2026, 9, 12);
    private static final Instant CALCULATED_AT = Instant.parse("2026-11-01T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(CALCULATED_AT, ZoneOffset.UTC);
    private static final FinancialParameters PARAMETERS = FinancialParameters.create(
            UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"),
            1,
            new Percentage(new BigDecimal("20.00")),
            new Money(new BigDecimal("15000.00")),
            new Percentage(new BigDecimal("10.00")),
            new Percentage(new BigDecimal("25.00")),
            "admin@seashare.com",
            Instant.parse("2026-09-20T15:04:05Z"));

    private static WireMockServer wireMock;

    private InMemoryReservationInformationRepository repository;
    private InMemoryFinancialParametersRepository parametersRepository;
    private InMemoryFailureRecorder failureRecorder;
    private NoOpReservationLockPort lockPort;
    private StubProvideBaseRateUseCase uc02;
    private RegisterReservationInformationService uc03;
    private CalculateReservationValueService uc04;

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
        repository = new InMemoryReservationInformationRepository();
        parametersRepository = new InMemoryFinancialParametersRepository();
        parametersRepository.saveNewVersion(PARAMETERS);
        failureRecorder = new InMemoryFailureRecorder();
        lockPort = new NoOpReservationLockPort();
        uc02 = new StubProvideBaseRateUseCase();
        uc02.respondWith(new BaseRateResult(new BoatId(BOAT_UUID),
                new BaseRate(new Money(new BigDecimal("300000.00"))), null));
        uc03 = new RegisterReservationInformationService(uc02, repository);
        uc04 = calculateWith(parametersRepository);
    }

    // --- CE-001: precisión financiera ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedBreakdowns")
    void ce001_totalIsExactlyTheSumOfTheThreeAmountsWithoutRoundingErrors(ExpectedBreakdown expected) {
        registerReservation(expected.baseRate(), expected.passengers(), START, END);

        ReservationValueResult result = uc04.calculate(command());

        assertThat(result.rentalAmount().amount()).isEqualByComparingTo(expected.rental());
        assertThat(result.insuranceAmount().amount()).isEqualByComparingTo(expected.insurance());
        assertThat(result.guaranteeDepositAmount().amount()).isEqualByComparingTo(expected.deposit());
        assertThat(result.totalAmount().amount()).isEqualByComparingTo(expected.total());
        // CE-001: cero errores de redondeo: el total publicado es exactamente la suma de los componentes.
        assertThat(result.totalAmount()).isEqualTo(result.rentalAmount()
                .add(result.insuranceAmount())
                .add(result.guaranteeDepositAmount()));
    }

    static Stream<Arguments> acceptedBreakdowns() {
        return Stream.of(
                Arguments.of(new ExpectedBreakdown("300000.00", 4,
                        "900000.00", "60000.00", "30000.00", "990000.00")),
                Arguments.of(new ExpectedBreakdown("437500.1234", 3,
                        "1312500.3702", "45000.0000", "43750.0123", "1401250.3825")),
                Arguments.of(new ExpectedBreakdown("33333.3333", 7,
                        "99999.9999", "105000.0000", "3333.3333", "208333.3332")),
                Arguments.of(new ExpectedBreakdown("0.0001", 1,
                        "0.0003", "15000.0000", "0.0000", "15000.0003")));
    }

    // --- CE-002: cálculo solo desde la información registrada ---

    @Test
    void ce002_calculationUsesTheRegisteredRateWithoutQueryingFlota() {
        registerReservationWithFlotaRate("350000.00");
        ReservationInformation registered = repository.findByReservationId(RESERVATION_ID).orElseThrow();
        wireMock.resetRequests();

        ReservationValueResult first = uc04.calculate(command());

        // La tarifa registrada por UC03 es la de Flota del momento del registro (fecha regular, sin ajuste).
        assertThat(registered.baseRate().amount()).isEqualTo(new Money(new BigDecimal("350000.00")));
        assertThat(first.rentalAmount().amount()).isEqualByComparingTo("1050000.00");
        assertThat(first.guaranteeDepositAmount().amount()).isEqualByComparingTo("35000.00");
        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));

        // Cambiar la tarifa de Flota después del registro no altera el desglose de UC04.
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("""
                        {"rates":[{"boat_id":"%s","base_rate":"999999.00"}]}
                        """.formatted(BOAT_UUID))));
        ReservationValueResult second = uc04.calculate(command());

        assertThat(second).isEqualTo(first);
        wireMock.verify(0, postRequestedFor(urlEqualTo(BASE_RATES_PATH)));
    }

    // --- CE-003: respuesta controlada ante información ausente o parámetros sin configurar ---

    @Test
    void ce003_unknownReservationIsAnsweredWithAControlledErrorAndNoPartialCalculation() {
        assertThatThrownBy(() -> uc04.calculate(command()))
                .isInstanceOfSatisfying(ReservationInfoNotFoundException.class,
                        exception -> assertThat(exception.getMessage()).contains(RESERVATION_UUID.toString()));

        assertThat(repository.findByReservationId(RESERVATION_ID)).isEmpty();
        assertThat(failureRecorder.failures()).isEmpty();
        assertThat(lockPort.locked()).containsExactly(RESERVATION_ID);
    }

    @Test
    void ce003_missingParametersAreRecordedAndNothingIsCalculated() {
        registerReservation("300000.00", 4);
        CalculateReservationValueService withoutParameters = calculateWith(new InMemoryFinancialParametersRepository());

        assertThatThrownBy(() -> withoutParameters.calculate(command()))
                .isInstanceOf(FinancialParametersNotConfiguredException.class);

        assertThat(failureRecorder.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC04");
            assertThat(failure.reservationId()).isEqualTo(RESERVATION_UUID);
            assertThat(failure.reason()).isEqualTo("FINANCIAL_PARAMETERS_NOT_CONFIGURED");
        });
        assertThat(repository.findByReservationId(RESERVATION_ID).orElseThrow().isCalculated()).isFalse();
    }

    // El caso 422 (información incompleta) no es alcanzable con datos reales (base_rate NOT NULL);
    // su vía defensiva se prueba con un doble en CalculateReservationValueServiceTest.

    // --- CE-004: el desglose queda disponible para "Procesar cobro" ---

    @Test
    void ce004_breakdownIsPersistedAndReadableForTheChargeWithoutRecalculation() {
        registerReservation("300000.00", 4);

        ReservationValueResult result = uc04.calculate(command());

        // Lectura directa de la fila (como la haría UC05): sin recalcular y con la referencia de versión.
        ReservationInformation persisted = repository.findByReservationId(RESERVATION_ID).orElseThrow();
        assertThat(persisted.isCalculated()).isTrue();
        assertThat(persisted.rentalAmount()).isEqualTo(result.rentalAmount());
        assertThat(persisted.insuranceAmount()).isEqualTo(result.insuranceAmount());
        assertThat(persisted.depositAmount()).isEqualTo(result.guaranteeDepositAmount());
        assertThat(persisted.totalAmount()).isEqualTo(result.totalAmount());
        assertThat(persisted.frozenParametersId()).isEqualTo(PARAMETERS.id());
        assertThat(persisted.calculatedAt()).isEqualTo(CALCULATED_AT);
        // El bloque informativo queda intacto.
        assertThat(persisted.baseRate()).isEqualTo(new BaseRate(new Money(new BigDecimal("300000.00"))));
        assertThat(persisted.passengers()).isEqualTo(4);
    }

    @Test
    void recalculationKeepsTheReferencedVersionWhenANewVersionIsPublished() {
        registerReservation("300000.00", 4);
        ReservationValueResult first = uc04.calculate(command());

        parametersRepository.saveNewVersion(FinancialParameters.create(UUID.randomUUID(), 2,
                new Percentage(new BigDecimal("20.00")), new Money(new BigDecimal("99999.00")),
                Percentage.ZERO, Percentage.ZERO, "admin@seashare.com", Instant.parse("2026-10-02T12:00:00Z")));

        ReservationValueResult second = uc04.calculate(command());

        assertThat(second).isEqualTo(first);
        assertThat(repository.findByReservationId(RESERVATION_ID).orElseThrow().frozenParametersId())
                .isEqualTo(PARAMETERS.id());
    }

    @Test
    void recalculationAfterUc03InvalidatesTheRowUsesTheCurrentVersion() {
        registerReservation("300000.00", 4);
        uc04.calculate(command());

        // UC03 vuelve a registrar la reserva (mismo día): los montos, la referencia y calculated_at quedan en NULL.
        uc03.register(new RegisterReservationInformationCommand(RESERVATION_ID, new BoatId(BOAT_UUID),
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), 2, new OwnerId(OWNER_UUID), 10));
        assertThat(repository.findByReservationId(RESERVATION_ID).orElseThrow().isCalculated()).isFalse();

        ReservationValueResult recalculated = uc04.calculate(command());

        // Nueva solicitud: se regenera el desglose con los datos nuevos y la versión vigente.
        assertThat(recalculated.rentalAmount().amount()).isEqualByComparingTo("300000.00");
        assertThat(recalculated.insuranceAmount().amount()).isEqualByComparingTo("30000.00");
        assertThat(recalculated.guaranteeDepositAmount().amount()).isEqualByComparingTo("30000.00");
        assertThat(repository.findByReservationId(RESERVATION_ID).orElseThrow().frozenParametersId())
                .isEqualTo(PARAMETERS.id());
    }

    // --- ayuda ---

    private void registerReservation(String baseRate, int passengers) {
        registerReservation(baseRate, passengers, START, END);
    }

    private void registerReservation(String baseRate, int passengers, LocalDate start, LocalDate end) {
        uc02.respondWith(new BaseRateResult(new BoatId(BOAT_UUID),
                new BaseRate(new Money(new BigDecimal(baseRate))), null));
        uc03.register(new RegisterReservationInformationCommand(RESERVATION_ID, new BoatId(BOAT_UUID),
                start, end, passengers, new OwnerId(OWNER_UUID), 60));
        assertThat(failureRecorder.failures()).isEmpty();
    }

    /** Registra con los componentes reales de UC02 y Flota simulada con WireMock (CE-002). */
    private void registerReservationWithFlotaRate(String baseRate) {
        wireMock.stubFor(post(urlEqualTo(BASE_RATES_PATH))
                .willReturn(okJson("""
                        {"rates":[{"boat_id":"%s","base_rate":"%s"}]}
                        """.formatted(BOAT_UUID, baseRate))));

        FleetRateProperties properties = new FleetRateProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.getRetry().setMaxAttempts(1);
        properties.getRetry().setWaitDuration(Duration.ofMillis(10));
        properties.getCircuitBreaker().setMinimumNumberOfCalls(5);
        properties.getCircuitBreaker().setSlidingWindowSize(5);

        ProvideBaseRateService realUc02 = new ProvideBaseRateService(
                new FleetRateRestClient(properties, Mappers.getMapper(FleetRateMapper.class)),
                parametersRepository, new DynamicRatePolicy(new HighSeasonCalendar()), failureRecorder);
        RegisterReservationInformationService realUc03 = new RegisterReservationInformationService(realUc02,
                repository);
        realUc03.register(new RegisterReservationInformationCommand(RESERVATION_ID, new BoatId(BOAT_UUID),
                START, END, 4, new OwnerId(OWNER_UUID), 60));
        assertThat(failureRecorder.failures()).isEmpty();
    }

    private CalculateReservationValueService calculateWith(InMemoryFinancialParametersRepository parameters) {
        return new CalculateReservationValueService(repository, parameters, failureRecorder, lockPort,
                new ReservationValueCalculator(), FIXED_CLOCK);
    }

    private static CalculateReservationValueCommand command() {
        return new CalculateReservationValueCommand(RESERVATION_ID);
    }

    private record ExpectedBreakdown(String baseRate, int passengers, String rental, String insurance,
            String deposit, String total) {
    }

    private static final class StubProvideBaseRateUseCase implements ProvideBaseRateUseCase {

        private List<BaseRateResult> results = List.of();

        @Override
        public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
            return results;
        }

        void respondWith(BaseRateResult result) {
            results = List.of(result);
        }
    }
}
