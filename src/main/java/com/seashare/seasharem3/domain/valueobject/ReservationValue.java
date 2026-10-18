package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;

/**
 * Desglose del valor calculado de la reserva (alquiler + seguro + depósito = total).
 * Fuente: UC04 plan (DesgloseValorReserva → ReservationValue) y contrato rest/UC04-valor-calculado-reserva.md §4.
 */
public record ReservationValue(Money rentalAmount, Money insuranceAmount, Money guaranteeDeposit, Money totalAmount) {

    public ReservationValue {
        Objects.requireNonNull(rentalAmount, "rentalAmount");
        Objects.requireNonNull(insuranceAmount, "insuranceAmount");
        Objects.requireNonNull(guaranteeDeposit, "guaranteeDeposit");
        Objects.requireNonNull(totalAmount, "totalAmount");
    }
}
