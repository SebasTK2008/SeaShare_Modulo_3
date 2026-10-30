package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundCalculation;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas de la politica de montos de reembolso (SPEC 9 RF-002..RF-005). */
class RefundCalculatorTest {

    private static final Money RENTAL = new Money(new BigDecimal("900000.00"));
    private static final Money INSURANCE = new Money(new BigDecimal("60000.00"));
    private static final Money DEPOSIT = new Money(new BigDecimal("30000.00"));
    private static final Money TOTAL = new Money(new BigDecimal("990000.00"));

    private final RefundCalculator calculator = new RefundCalculator();

    @Test
    void flexibleAndHostRefundTheWholePaidValue() {
        assertThat(calculator.calculate(RefundTrigger.CANCELLED_FLEXIBLE, RENTAL, INSURANCE, DEPOSIT, TOTAL))
                .isEqualTo(new RefundCalculation(TOTAL, RefundScope.FULL));
        assertThat(calculator.calculate(RefundTrigger.CANCELLED_BY_HOST, RENTAL, INSURANCE, DEPOSIT, TOTAL))
                .isEqualTo(new RefundCalculation(TOTAL, RefundScope.FULL));
    }

    @Test
    void moderateRefundsHalfRentalPlusDepositWithoutInsurance() {
        assertThat(calculator.calculate(RefundTrigger.CANCELLED_MODERATE, RENTAL, INSURANCE, DEPOSIT, TOTAL))
                .isEqualTo(new RefundCalculation(new Money(new BigDecimal("480000.00")),
                        RefundScope.HALF_RENTAL_PLUS_DEPOSIT));
    }

    @Test
    void strictAndRejectedDisputeRefundOnlyTheDeposit() {
        assertThat(calculator.calculate(RefundTrigger.CANCELLED_STRICT, RENTAL, INSURANCE, DEPOSIT, TOTAL))
                .isEqualTo(new RefundCalculation(DEPOSIT, RefundScope.DEPOSIT_ONLY));
        assertThat(calculator.calculate(RefundTrigger.DISPUTE_REJECTED, RENTAL, INSURANCE, DEPOSIT, TOTAL))
                .isEqualTo(new RefundCalculation(DEPOSIT, RefundScope.DEPOSIT_ONLY));
    }

    @Test
    void rejectsMissingOrNonPositiveRequiredAmounts() {
        assertThatThrownBy(() -> calculator.calculate(RefundTrigger.CANCELLED_STRICT, RENTAL, INSURANCE, null,
                TOTAL)).isInstanceOf(DomainException.class).hasMessageContaining("deposit_amount");
        assertThatThrownBy(() -> calculator.calculate(RefundTrigger.CANCELLED_MODERATE, Money.ZERO, INSURANCE,
                DEPOSIT, TOTAL)).isInstanceOf(DomainException.class).hasMessageContaining("rental_amount");
    }
}
