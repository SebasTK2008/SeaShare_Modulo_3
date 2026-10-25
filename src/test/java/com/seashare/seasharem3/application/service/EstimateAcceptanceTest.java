package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;
import com.seashare.seasharem3.application.dto.EstimateSingleCommand;
import com.seashare.seasharem3.application.dto.EstimateWarning;
import com.seashare.seasharem3.application.dto.SingleEstimateResult;
import com.seashare.seasharem3.application.port.out.FleetRatePort;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.service.DynamicRatePolicy;
import com.seashare.seasharem3.domain.service.HighSeasonCalendar;
import com.seashare.seasharem3.domain.service.PricingCalculator;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.support.FixedClockPort;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryFinancialParametersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de aceptación de UC01 (CE-001 y CE-003) con los componentes reales de UC01 y UC02:
 * {@link PricingCalculator}, {@link DynamicRatePolicy}, {@link HighSeasonCalendar} y
 * {@link ProvideBaseRateService}. Solo el puerto de Flota es un doble en memoria, de modo que no se
 * hace ninguna operacion de precio fuera del sistema (CE-002 del SPEC).
 *
 * <p>CE-001: el total es exactamente {@code tarifa final × días + seguro × pasajeros} con escala
 * interna 4 y sin redondeo intermedio. CE-003: toda estimación individual exitosa lleva el aviso
 * obligatorio, literal y no vacío.</p>
 */
class EstimateAcceptanceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final BoatId BOAT = new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"));
    private static final Money INSURANCE = new Money(new BigDecimal("15000.00"));
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("350000.00")));
    private static final FinancialParameters PARAMETERS = FinancialParameters.create(
            UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"), 1,
            new Percentage(new BigDecimal("10.00")),
            INSURANCE,
            new Percentage(new BigDecimal("10.00")),
            new Percentage(new BigDecimal("25.00")),
            "admin@seashare.com",
            Instant.parse("2026-09-20T15:04:05Z"));

    private static final DynamicRatePolicy POLICY = new DynamicRatePolicy(new HighSeasonCalendar());

    private InMemoryFinancialParametersRepository paramsRepo;
    private InMemoryFailureRecorder failureRecorder;
    private ProvideBaseRateService uc02;
    private EstimateSingleService singleService;
    private EstimateBatchService batchService;

    @BeforeEach
    void setUp() {
        paramsRepo = new InMemoryFinancialParametersRepository();
        paramsRepo.saveNewVersion(PARAMETERS);
        failureRecorder = new InMemoryFailureRecorder();
        FleetRatePort fleet = new StaticFleetRatePort(Map.of(BOAT, BASE_RATE));
        uc02 = new ProvideBaseRateService(fleet, paramsRepo, POLICY, failureRecorder);
        FixedClockPort clock = new FixedClockPort(TODAY);
        singleService = new EstimateSingleService(uc02, paramsRepo, clock, new PricingCalculator());
        batchService = new EstimateBatchService(uc02, paramsRepo, clock, new PricingCalculator(), 50);
    }

    /**
     * CE-001: la precision financiera se mantiene para fechas regulares, fin de semana, temporada
     * alta y su coincidencia, con distintas duraciones y pasajeros.
     */
    @ParameterizedTest(name = "[{index}] {0} {1} días, {2} pasajeros")
    @MethodSource("ce001Cases")
    void ce001_estimatedTotalIsBaseRateTimesDaysPlusInsuranceTimesPassengers(
            LocalDate startDate, int days, int passengers) {

        LocalDate endDate = startDate.plusDays(days - 1);
        BigDecimal expectedDailyRate = POLICY.apply(BASE_RATE, startDate, PARAMETERS).amount().amount();
        BigDecimal expectedTotal = expectedDailyRate
                .multiply(BigDecimal.valueOf(days))
                .add(INSURANCE.amount().multiply(BigDecimal.valueOf(passengers)));

        SingleEstimateResult result = singleService.estimate(
                new EstimateSingleCommand(BOAT, startDate, endDate, passengers));

        assertThat(result.durationDays()).isEqualTo(days);
        assertThat(result.passengers()).isEqualTo(passengers);
        assertThat(result.estimatedTotal().amount())
                .as("Total para %s (%s días, %s pasajeros)", startDate, days, passengers)
                .isEqualByComparingTo(expectedTotal);
        assertThat(result.estimatedTotal().amount().scale()).isEqualTo(Money.INTERNAL_SCALE);
    }

    @Test
    void ce001_batchUsesTodaysDynamicRateAndOneDayOnePassenger() {
        BigDecimal expectedDailyRate = POLICY.apply(BASE_RATE, TODAY, PARAMETERS).amount().amount();
        BigDecimal expectedTotal = expectedDailyRate.add(INSURANCE.amount());

        BatchEstimateResult result = batchService.estimate(
                new EstimateBatchCommand(List.of(BOAT)));

        assertThat(result.evaluationDate()).isEqualTo(TODAY);
        assertThat(result.durationDays()).isEqualTo(1);
        assertThat(result.passengers()).isEqualTo(1);
        assertThat(result.estimates()).hasSize(1);
        assertThat(result.estimates().get(0).estimatedTotal().amount())
                .isEqualByComparingTo(expectedTotal);
    }

    @Test
    void ce001_generatesAtLeastOneHundredCases() {
        assertThat(ce001Cases().count()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void ce003_individualEstimateAlwaysCarriesTheMandatoryWarning() {
        List<LocalDate> dates = List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(9),
                TODAY.plusDays(30), LocalDate.of(2027, 3, 26));

        for (LocalDate date : dates) {
            SingleEstimateResult result = singleService.estimate(
                    new EstimateSingleCommand(BOAT, date, date.plusDays(2), 2));

            assertThat(result.warning())
                    .as("Aviso obligatorio para %s", date)
                    .isEqualTo(EstimateWarning.TEXT)
                    .isNotBlank();
        }
    }

    @Test
    void t027_uc01DoesNotRecordOperationalFailuresOnSuccessfulEstimates() {
        batchService.estimate(new EstimateBatchCommand(List.of(BOAT)));
        singleService.estimate(new EstimateSingleCommand(BOAT, TODAY, TODAY.plusDays(2), 3));

        assertThat(failureRecorder.failures()).isEmpty();
    }

    /**
     * Fechas con cobertura de condición regular, fin de semana, temporada alta y coincidencia,
     * desde hoy (la fecha mínima admitida) hasta dos años vista.
     */
    private static Stream<Arguments> ce001Cases() {
        List<LocalDate> dates = Stream.concat(
                        Stream.iterate(TODAY, day -> day.plusDays(1)).limit(60),
                        Stream.of(
                                LocalDate.of(2026, 11, 16),
                                LocalDate.of(2026, 12, 25),
                                LocalDate.of(2027, 1, 15),
                                LocalDate.of(2027, 1, 16),
                                LocalDate.of(2027, 3, 25),
                                LocalDate.of(2027, 3, 26),
                                LocalDate.of(2027, 3, 28),
                                LocalDate.of(2027, 6, 1),
                                LocalDate.of(2027, 7, 30),
                                LocalDate.of(2027, 7, 31),
                                LocalDate.of(2027, 12, 24),
                                LocalDate.of(2028, 4, 14)))
                .distinct()
                .toList();

        int[] dayCounts = {1, 2, 3};
        int[] passengerCounts = {1, 2, 4};

        return dates.stream().flatMap(date -> IntStream.of(dayCounts).boxed()
                .flatMap(days -> IntStream.of(passengerCounts).boxed()
                        .map(passengers -> Arguments.of(date, days, passengers))));
    }

    /** Doble de Flota: solo las embarcaciones conocidas, sin HTTP. */
    private record StaticFleetRatePort(Map<BoatId, BaseRate> rates) implements FleetRatePort {

        @Override
        public Map<BoatId, BaseRate> findBaseRates(Set<BoatId> boatIds) {
            return boatIds.stream()
                    .filter(rates::containsKey)
                    .collect(java.util.stream.Collectors.toMap(id -> id, rates::get));
        }
    }
}
