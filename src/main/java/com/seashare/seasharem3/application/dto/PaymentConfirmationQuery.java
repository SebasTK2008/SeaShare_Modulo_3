package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.ReservationId;

/** Consulta de confirmación de pago por reserva. Fuente: UC06 plan T003 y contrato rest/UC06-confirmacion-pago.md. */
public record PaymentConfirmationQuery(ReservationId reservationId) {
}
