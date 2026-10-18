package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/** Los cuatro montos del desglose calculado. Fuente: UC04 plan T010 y contrato rest/UC04-valor-calculado-reserva.md §4. */
public record ReservationValueResult(Money rentalAmount, Money insuranceAmount, Money guaranteeDeposit,
                                     Money totalAmount) {
}
