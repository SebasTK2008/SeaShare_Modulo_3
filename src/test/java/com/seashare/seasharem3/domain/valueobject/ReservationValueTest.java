package com.seashare.seasharem3.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ReservationValueTest {

    private static final ReservationValue VALUE = new ReservationValue(
            new Money(new BigDecimal("900000.00")),
            new Money(new BigDecimal("60000.00")),
            new Money(new BigDecimal("30000.00")),
            new Money(new BigDecimal("990000.00")));

    @Test
    void holdsTheFourAmountsOfTheBreakdown() {
        assertThat(VALUE.rentalAmount()).isEqualTo(new Money(new BigDecimal("900000.00")));
        assertThat(VALUE.insuranceAmount()).isEqualTo(new Money(new BigDecimal("60000.00")));
        assertThat(VALUE.guaranteeDeposit()).isEqualTo(new Money(new BigDecimal("30000.00")));
        assertThat(VALUE.totalAmount()).isEqualTo(new Money(new BigDecimal("990000.00")));
    }

    @Test
    void rejectsAnyMissingAmount() {
        Money rental = VALUE.rentalAmount();
        Money insurance = VALUE.insuranceAmount();
        Money deposit = VALUE.guaranteeDeposit();

        assertThatNullPointerException().isThrownBy(() -> new ReservationValue(null, insurance, deposit, rental));
        assertThatNullPointerException().isThrownBy(() -> new ReservationValue(rental, null, deposit, rental));
        assertThatNullPointerException().isThrownBy(() -> new ReservationValue(rental, insurance, null, rental));
        assertThatNullPointerException().isThrownBy(() -> new ReservationValue(rental, insurance, deposit, null));
    }
}
