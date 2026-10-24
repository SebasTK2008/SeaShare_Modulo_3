package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

/**
 * Desglose calculado de una reserva: la reserva y los cuatro montos.
 * Fuente: UC04 plan T010 y contrato rest/UC04-valor-calculado-reserva.md §4
 * (campos {@code reservation_id} y {@code guarantee_deposit_amount}).
 */
public record ReservationValueResult(ReservationId reservationId, Money rentalAmount, Money insuranceAmount,
                                     Money guaranteeDepositAmount, Money totalAmount) {
}
