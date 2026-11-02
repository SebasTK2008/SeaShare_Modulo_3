package com.seashare.seasharem3.domain.valueobject;

import com.seashare.seasharem3.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void normalizesInternalScaleTo4Decimals() {
        Money money = new Money(new BigDecimal("350000"));

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("350000.0000"));
        assertThat(money.amount().scale()).isEqualTo(4);
    }

    @Test
    void rejectsExcessPrecision() {
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new Money(new BigDecimal("350000.12345")));
    }

    @Test
    void addsSubtractsAndMultipliesKeepingScale4() {
        Money rate = new Money(new BigDecimal("350000.00"));
        Money insurance = new Money(new BigDecimal("15000.00"));

        assertThat(rate.add(insurance).amount()).isEqualByComparingTo(new BigDecimal("365000.0000"));
        assertThat(rate.subtract(insurance).amount()).isEqualByComparingTo(new BigDecimal("335000.0000"));
        assertThat(rate.multiply(new BigDecimal("3")).amount()).isEqualByComparingTo(new BigDecimal("1050000.0000"));
        assertThat(insurance.multiply(new BigDecimal("4")).amount()).isEqualByComparingTo(new BigDecimal("60000.0000"));
    }

    @Test
    void monetaryOutputRoundsHalfUpTo2Decimals() {
        Money withFraction = new Money(new BigDecimal("437500.1250"));

        assertThat(withFraction.toOutputAmount()).isEqualByComparingTo(new BigDecimal("437500.13"));
        assertThat(withFraction.toOutputAmount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new Money(null));
    }

    @Test
    void rejectsNullMultiplier() {
        assertThatNullPointerException().isThrownBy(() -> new Money(BigDecimal.TEN).multiply(null));
    }

    @Test
    void rejectsNegativeAmountWhenNonNegativeRequired() {
        Money negative = new Money(new BigDecimal("-0.01"));

        assertThatExceptionOfType(DomainException.class).isThrownBy(negative::requireNonNegative);
    }

    @Test
    void rejectsNonPositiveAmountWhenPositiveRequired() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(Money.ZERO::requirePositive);
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> new Money(new BigDecimal("-1")).requirePositive());
    }

    @Test
    void appliesPercentagesWithInternalScale4() {
        Money base = new Money(new BigDecimal("100000.00"));

        assertThat(base.percentageOf(new Percentage(new BigDecimal("10"))).amount())
                .isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(base.increaseBy(new Percentage(new BigDecimal("15"))).amount())
                .isEqualByComparingTo(new BigDecimal("115000.0000"));
        assertThat(base.percentageOf(Percentage.ZERO).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void dividesWithHalfUpRoundingAndRejectsZeroDivisor() {
        Money total = new Money(new BigDecimal("989999.99"));

        assertThat(total.divide(new BigDecimal("3")).amount()).isEqualByComparingTo(new BigDecimal("329999.9967"));
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> total.divide(BigDecimal.ZERO));
    }

    @Test
    void comparesAmounts() {
        Money lower = new Money(new BigDecimal("10.00"));
        Money higher = new Money(new BigDecimal("20.00"));

        assertThat(lower.isLessThan(higher)).isTrue();
        assertThat(lower.isLessThanOrEqualTo(higher)).isTrue();
        assertThat(higher.isGreaterThan(lower)).isTrue();
        assertThat(higher.isGreaterThanOrEqualTo(lower)).isTrue();
        assertThat(lower.isPositive()).isTrue();
        assertThat(lower.isNegative()).isFalse();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(lower.min(higher)).isEqualTo(lower);
        assertThat(lower.max(higher)).isEqualTo(higher);
        assertThat(lower.compareTo(higher)).isNegative();
    }

    @Test
    void negatesAndAppliesAbsoluteValue() {
        Money negative = new Money(new BigDecimal("-15.50"));

        assertThat(negative.negate().amount()).isEqualByComparingTo(new BigDecimal("15.5000"));
        assertThat(negative.abs().amount()).isEqualByComparingTo(new BigDecimal("15.5000"));
    }
}
