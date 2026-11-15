package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.SettlementCalculation;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas de la formula de liquidacion (SPEC 10 RF-002..RF-007, D-35). */
class SettlementCalculatorTest {

    private static final Money RENTAL = new Money(new BigDecimal("900000.00"));
    private static final Money INSURANCE = new Money(new BigDecimal("60000.00"));
    private static final Money DEPOSIT = new Money(new BigDecimal("30000.00"));
    private static final Percentage COMMISSION = new Percentage(new BigDecimal("20"));

    private final SettlementCalculator calculator = new SettlementCalculator();

    @Test
    void standardSubtractsCommissionAndInsurance() {
        SettlementCalculation result = calculator.calculate(SettlementTrigger.COMPLETED, RENTAL, INSURANCE,
                DEPOSIT, COMMISSION);

        assertThat(result.disbursedAmount()).isEqualTo(new Money(new BigDecimal("660000.00")));
        assertThat(result.commissionAmount()).isEqualTo(new Money(new BigDecimal("180000.00")));
        assertThat(result.rentalGrossAmount()).isEqualTo(RENTAL);
        assertThat(result.insuranceAmount()).isEqualTo(INSURANCE);
        assertThat(result.depositAmount()).isEqualTo(DEPOSIT);
    }

    @Test
    void cancellationCompensationsDoNotApplyCommissionOrInsurance() {
        SettlementCalculation moderate = calculator.calculate(SettlementTrigger.CANCELLED_MODERATE, RENTAL,
                INSURANCE, DEPOSIT, null);
        SettlementCalculation strict = calculator.calculate(SettlementTrigger.CANCELLED_STRICT, RENTAL,
                INSURANCE, DEPOSIT, null);

        assertThat(moderate.disbursedAmount()).isEqualTo(new Money(new BigDecimal("450000.00")));
        assertThat(moderate.commissionAmount()).isEqualTo(Money.ZERO);
        assertThat(moderate.insuranceAmount()).isEqualTo(Money.ZERO);
        assertThat(strict.disbursedAmount()).isEqualTo(RENTAL);
        assertThat(strict.commissionAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    void disputeSettlesTheWholeDeposit() {
        SettlementCalculation result = calculator.calculate(SettlementTrigger.DISPUTE_COMPLETED, RENTAL,
                INSURANCE, DEPOSIT, null);

        assertThat(result.disbursedAmount()).isEqualTo(DEPOSIT);
        assertThat(result.commissionAmount()).isEqualTo(Money.ZERO);
        assertThat(result.insuranceAmount()).isEqualTo(Money.ZERO);
    }

    @Test
    void standardRequiresFrozenCommission() {
        assertThatThrownBy(() -> calculator.calculate(SettlementTrigger.COMPLETED, RENTAL, INSURANCE, DEPOSIT,
                null)).isInstanceOf(DomainException.class).hasMessageContaining("commission");
    }

    @Test
    void standardRejectsANonPositiveDisbursement() {
        Percentage tooHigh = new Percentage(new BigDecimal("100"));
        assertThatThrownBy(() -> calculator.calculate(SettlementTrigger.COMPLETED, RENTAL, INSURANCE, DEPOSIT,
                tooHigh)).isInstanceOf(DomainException.class).hasMessageContaining("positive");
    }
}
