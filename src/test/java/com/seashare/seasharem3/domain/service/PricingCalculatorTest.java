package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @Test
    void calculatesBasicEstimate() {
        Money result = calculator.estimate(
                new Money(new BigDecimal("350000.00")), 1, new Money(new BigDecimal("15000.00")), 1);
        assertThat(result.toOutputAmount()).isEqualByComparingTo(new BigDecimal("365000.00"));
    }

    @Test
    void calculatesInclusiveDays() {
        Money result = calculator.estimate(
                new Money(new BigDecimal("350000.00")), 3, new Money(new BigDecimal("15000.00")), 4);
        assertThat(result.toOutputAmount()).isEqualByComparingTo(new BigDecimal("1110000.00"));
    }

    @Test
    void keepsInternalScale4() {
        Money result = calculator.estimate(
                new Money(new BigDecimal("350000.0000")), 1, new Money(new BigDecimal("15000.0000")), 1);
        assertThat(result.amount().scale()).isEqualTo(Money.INTERNAL_SCALE);
    }

    @Test
    void rejectsZeroOrNegativeDays() {
        Money base = new Money(new BigDecimal("350000.00"));
        Money insurance = new Money(new BigDecimal("15000.00"));

        assertThatThrownBy(() -> calculator.estimate(base, 0, insurance, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("durationDays");
        assertThatThrownBy(() -> calculator.estimate(base, -1, insurance, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("durationDays");
    }

    @Test
    void rejectsZeroOrNegativePassengers() {
        Money base = new Money(new BigDecimal("350000.00"));
        Money insurance = new Money(new BigDecimal("15000.00"));

        assertThatThrownBy(() -> calculator.estimate(base, 1, insurance, 0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("passengers");
        assertThatThrownBy(() -> calculator.estimate(base, 1, insurance, -3))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("passengers");
    }

    @Test
    void rejectsNullArguments() {
        Money money = new Money(new BigDecimal("350000.00"));

        assertThatNullPointerException().isThrownBy(
                () -> calculator.estimate(null, 1, money, 1));
        assertThatNullPointerException().isThrownBy(
                () -> calculator.estimate(money, 1, null, 1));
    }

    @Test
    void zeroInsuranceOnlyChargesRental() {
        Money result = calculator.estimate(
                new Money(new BigDecimal("100000.00")), 2, Money.ZERO, 5);
        assertThat(result.toOutputAmount()).isEqualByComparingTo(new BigDecimal("200000.00"));
    }
}
