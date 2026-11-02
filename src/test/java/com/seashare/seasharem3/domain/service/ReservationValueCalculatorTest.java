package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Pruebas de la fórmula de UC04 (T009): días inclusivos, alquiler, seguro, depósito sin
 * multiplicadores, valor total y redondeo HALF_UP a 2 decimales [SPEC RF-003–RF-006, RNF-002, CE-001].
 */
class ReservationValueCalculatorTest {

    private static final ReservationValueCalculator CALCULATOR = new ReservationValueCalculator();

    @Test
    void calculatesTheContractExampleBreakdown() {
        ReservationValue value = CALCULATOR.calculate(
                information("300000.00", LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12), 4),
                parameters("15000.00"));

        assertThat(value.rentalAmount().amount()).isEqualByComparingTo("900000.00");
        assertThat(value.insuranceAmount().amount()).isEqualByComparingTo("60000.00");
        assertThat(value.guaranteeDeposit().amount()).isEqualByComparingTo("30000.00");
        assertThat(value.totalAmount().amount()).isEqualByComparingTo("990000.00");
    }

    @Test
    void countsOneDayWhenStartAndEndAreTheSame() {
        LocalDate day = LocalDate.of(2026, 12, 10);

        ReservationValue value = CALCULATOR.calculate(
                information("300000.00", day, day, 1), parameters("15000.00"));

        assertThat(value.rentalAmount().amount()).isEqualByComparingTo("300000.00");
        assertThat(value.guaranteeDeposit().amount()).isEqualByComparingTo("30000.00");
    }

    @ParameterizedTest(name = "depósito {0} días / {1} pasajeros = {2}")
    @MethodSource("depositCases")
    void depositIsTenPercentOfTheDailyRateWithoutAnyMultiplier(int days, int passengers, String expectedDeposit) {
        LocalDate start = LocalDate.of(2026, 12, 10);

        ReservationValue value = CALCULATOR.calculate(
                information("300000.00", start, start.plusDays(days - 1L), passengers), parameters("15000.00"));

        assertThat(value.guaranteeDeposit().amount()).isEqualByComparingTo(expectedDeposit);
    }

    static Stream<Arguments> depositCases() {
        return Stream.of(
                Arguments.of(1, 1, "30000.00"),
                Arguments.of(3, 4, "30000.00"),
                Arguments.of(14, 60, "30000.00"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundingCases")
    void keepsEveryAmountAtInternalScaleAndTheExactTotal(CalculationCase testCase) {
        ReservationValue value = CALCULATOR.calculate(
                information(testCase.baseRate(), testCase.start(), testCase.end(), testCase.passengers()),
                parameters(testCase.insurancePerPassenger()));

        assertThat(value.rentalAmount().amount()).isEqualByComparingTo(testCase.rental());
        assertThat(value.insuranceAmount().amount()).isEqualByComparingTo(testCase.insurance());
        assertThat(value.guaranteeDeposit().amount()).isEqualByComparingTo(testCase.deposit());
        assertThat(value.totalAmount().amount()).isEqualByComparingTo(testCase.total());
        // CE-001: el total es exactamente la suma de los tres montos ya redondeados, sin error de redondeo.
        assertThat(value.totalAmount()).isEqualTo(value.rentalAmount()
                .add(value.insuranceAmount())
                .add(value.guaranteeDeposit()));
    }

    static Stream<Arguments> roundingCases() {
        return Stream.of(
                Arguments.of(new CalculationCase("437500.1234", "15000.1234",
                        LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 11), 3,
                        "875000.2468", "45000.3702", "43750.0123", "963750.6293")),
                Arguments.of(new CalculationCase("33333.3333", "1111.1111",
                        LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14), 7,
                        "166666.6665", "7777.7777", "3333.3333", "177777.7775")),
                Arguments.of(new CalculationCase("0.0001", "0.0001",
                        LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 10), 1,
                        "0.0001", "0.0001", "0.0000", "0.0002")));
    }

    @Test
    void rejectsNullArguments() {
        ReservationInformation information = information("300000.00",
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 12), 4);
        FinancialParameters parameters = parameters("15000.00");

        assertThatNullPointerException().isThrownBy(() -> CALCULATOR.calculate(null, parameters));
        assertThatNullPointerException().isThrownBy(() -> CALCULATOR.calculate(information, null));
    }

    private static ReservationInformation information(String baseRate, LocalDate start, LocalDate end,
            int passengers) {
        return ReservationInformation.create(
                new ReservationId(UUID.fromString("b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")),
                new BoatId(UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")),
                new BaseRate(new Money(new BigDecimal(baseRate))), null, start, end, passengers,
                new OwnerId(UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")), 60);
    }

    private static FinancialParameters parameters(String insurancePerPassenger) {
        return FinancialParameters.create(UUID.randomUUID(), 1, new Percentage(new BigDecimal("20.00")),
                new Money(new BigDecimal(insurancePerPassenger)), Percentage.ZERO, Percentage.ZERO,
                "admin@seashare.com", Instant.parse("2026-10-01T12:00:00Z"));
    }

    /** Caso con los insumos y el desglose esperado calculado fuera del sistema (CE-001). */
    private record CalculationCase(String baseRate, String insurancePerPassenger, LocalDate start, LocalDate end,
            int passengers, String rental, String insurance, String deposit, String total) {
    }
}
