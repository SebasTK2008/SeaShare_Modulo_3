package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DynamicRatePolicyTest {

    private final DynamicRatePolicy policy = new DynamicRatePolicy(new HighSeasonCalendar());
    private final BaseRate baseRate = new BaseRate(new Money(new BigDecimal("350000.00")));

    @Test
    void leavesRegularDatesUnchangedWithoutReadingParameters() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2026-09-30"), null);

        assertThat(result).isEqualTo(baseRate);
        assertThat(policy.requiresAdjustment(LocalDate.parse("2026-09-30"))).isFalse();
    }

    @Test
    void appliesWeekendIncrease() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2026-10-03"), parameters("10.00", "25.00"));

        assertThat(result.amount().amount()).isEqualByComparingTo("385000.0000");
    }

    @Test
    void treatsRecessWeekAsHighSeason() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2026-10-07"), parameters("10.00", "25.00"));

        assertThat(result.amount().amount()).isEqualByComparingTo("437500.0000");
    }

    @Test
    void appliesHighSeasonIncrease() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2027-01-15"), parameters("10.00", "25.00"));

        assertThat(result.amount().amount()).isEqualByComparingTo("437500.0000");
    }

    @Test
    void appliesTheHigherAdjustmentWhenConditionsOverlap() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2026-12-20"), parameters("10.00", "25.00"));

        assertThat(result.amount().amount()).isEqualByComparingTo("437500.0000");
    }

    @Test
    void appliesWeekendWhenItsPercentageIsHigherThanHighSeason() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2026-12-20"), parameters("30.00", "25.00"));

        assertThat(result.amount().amount()).isEqualByComparingTo("455000.0000");
    }

    @Test
    void returnsScaleFourWithoutFinalRounding() {
        BaseRate result = policy.apply(baseRate, LocalDate.parse("2026-10-03"), parameters("10.50", "25.00"));

        assertThat(result.amount().amount()).isEqualByComparingTo("386750.0000");
        assertThat(result.amount().amount().scale()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-03", "2026-10-07", "2026-12-20"})
    void rejectsMissingParametersAsAProgrammingErrorWhenAdjustmentIsRequired(String date) {
        assertThatNullPointerException()
                .isThrownBy(() -> policy.apply(baseRate, LocalDate.parse(date), null))
                .withMessage("parameters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-10", "2026-10-11", "2026-10-05", "2026-12-20"})
    void detectsDatesThatRequireAdjustment(String date) {
        assertThat(policy.requiresAdjustment(LocalDate.parse(date))).isTrue();
    }

    private static FinancialParameters parameters(String weekendPercentage, String highSeasonPercentage) {
        return FinancialParameters.create(
                UUID.randomUUID(),
                1,
                new Percentage(new BigDecimal("10.00")),
                new Money(new BigDecimal("15000.00")),
                new Percentage(new BigDecimal(weekendPercentage)),
                new Percentage(new BigDecimal(highSeasonPercentage)),
                "admin@seashare.com",
                Instant.parse("2026-09-20T15:04:05Z"));
    }
}
