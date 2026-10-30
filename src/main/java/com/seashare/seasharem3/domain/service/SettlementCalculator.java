package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.SettlementCalculation;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Politica pura de liquidacion (SPEC 10 RF-002..RF-007 y RF-014; UC10 plan regla 4).
 *
 * <p>Estandar: alquiler - comision - seguro. Cancelaciones: 50 %/100 % del alquiler sin comision
 * ni seguro. Deposito por disputa: 100 % del deposito. El seguro del registro es 0 en
 * cancelaciones y deposito, y el de la reserva en la liquidacion estandar (RF-016).</p>
 */
public final class SettlementCalculator {

    private static final BigDecimal HALF = new BigDecimal("0.50");

    public SettlementCalculation calculate(SettlementTrigger trigger, Money rentalAmount, Money insuranceAmount,
            Money depositAmount, Percentage commissionPct) {
        Objects.requireNonNull(trigger, "trigger");
        Money rental = required(rentalAmount, "rental_amount");
        Money deposit = required(depositAmount, "deposit_amount");

        return switch (trigger) {
            case COMPLETED -> standard(rental, required(insuranceAmount, "insurance_amount"), deposit, commissionPct);
            case CANCELLED_MODERATE -> new SettlementCalculation(rental.multiply(HALF), Money.ZERO, rental,
                    Money.ZERO, deposit);
            case CANCELLED_STRICT -> new SettlementCalculation(rental, Money.ZERO, rental, Money.ZERO, deposit);
            case DISPUTE_COMPLETED -> new SettlementCalculation(deposit, Money.ZERO, rental, Money.ZERO, deposit);
        };
    }

    private static SettlementCalculation standard(Money rental, Money insurance, Money deposit,
            Percentage commissionPct) {
        if (commissionPct == null) {
            throw new DomainException("The referenced financial parameters have no commission percentage");
        }
        Money commission = rental.percentageOf(commissionPct);
        Money disbursed = rental.subtract(commission).subtract(insurance);
        if (!disbursed.isPositive()) {
            throw new DomainException("The standard settlement would not disburse a positive amount: " + disbursed);
        }
        return new SettlementCalculation(disbursed, commission, rental, insurance, deposit);
    }

    private static Money required(Money value, String field) {
        if (value == null) {
            throw new DomainException("The reservation has no " + field + " to calculate the settlement");
        }
        value.requireNonNegative();
        return value;
    }
}
