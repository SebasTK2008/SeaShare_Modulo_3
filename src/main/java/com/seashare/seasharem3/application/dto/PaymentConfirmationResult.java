package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.math.BigDecimal;

/** Confirmación de pago con montos BigDecimal anulables. Fuente: UC06 plan T003 y §Reglas 5; contrato rest/UC06-confirmacion-pago.md §4. */
public record PaymentConfirmationResult(ReservationId reservationId, PaymentConfirmationStatus status, String detail,
                                        BigDecimal authorizedAmount, BigDecimal capturedAmount,
                                        BigDecimal releasedAmount, BigDecimal chargedAmount,
                                        String externalReference) {
}
