package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @Test
    void calculatesBasicEstimate() {
        BigDecimal result = calculator.calculate(
                new BaseRate(new Money(new BigDecimal("350000.00"))),
                1, 1, new Money(new BigDecimal("15000.00")));
        assertThat(result).isEqualByComparingTo(new BigDecimal("365000.00"));
    }

    @Test
    void calculatesInclusiveDays() {
        BigDecimal result = calculator.calculate(
                new BaseRate(new Money(new BigDecimal("350000.00"))),
                3, 4, new Money(new BigDecimal("15000.00")));
        assertThat(result).isEqualByComparingTo(new BigDecimal("1110000.00"));
    }

    @Test
    void calculatesSingleDaySinglePassenger() {
        BigDecimal result = calculator.calculate(
                new BaseRate(new Money(new BigDecimal("350000.00"))),
                1, 1, new Money(new BigDecimal("15000.00")));
        assertThat(result).isEqualByComparingTo(new BigDecimal("365000.00"));
    }

    @Test
    void calculatesWithScale4() {
        BigDecimal result = calculator.calculate(
                new BaseRate(new Money(new BigDecimal("350000.0000"))),
                1, 1, new Money(new BigDecimal("15000.0000")));
        assertThat(result.scale()).isEqualTo(4);
    }
}
