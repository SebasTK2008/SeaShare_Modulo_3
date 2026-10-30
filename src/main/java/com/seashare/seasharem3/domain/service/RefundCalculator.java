package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundCalculation;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Politica pura de montos de reembolso por estado (SPEC 9 RF-002..RF-005; UC09 plan regla 1).
 * No conoce la pasarela, la base de datos ni los costos transaccionales.
 */
public final class RefundCalculator {

    private static final BigDecimal HALF = new BigDecimal("0.50");

    public RefundCalculation calculate(RefundTrigger trigger, Money rentalAmount, Money insuranceAmount,
            Money depositAmount, Money totalAmount) {
        Objects.requireNonNull(trigger, "trigger");
        return switch (trigger) {
            case CANCELLED_FLEXIBLE, CANCELLED_BY_HOST -> new RefundCalculation(
                    required(totalAmount, "total_amount"), RefundScope.FULL);
            case CANCELLED_MODERATE -> new RefundCalculation(
                    required(rentalAmount, "rental_amount").multiply(HALF)
                            .add(required(depositAmount, "deposit_amount")),
                    RefundScope.HALF_RENTAL_PLUS_DEPOSIT);
            case CANCELLED_STRICT, DISPUTE_REJECTED -> new RefundCalculation(
                    required(depositAmount, "deposit_amount"), RefundScope.DEPOSIT_ONLY);
        };
    }

    private static Money required(Money value, String field) {
        if (value == null) {
            throw new DomainException("The reservation has no " + field + " to calculate the refund");
        }
        if (!value.isPositive()) {
            throw new DomainException(
                    "The reservation's " + field + " must be greater than zero to calculate the refund: "
                            + value.amount());
        }
        return value;
    }
}
