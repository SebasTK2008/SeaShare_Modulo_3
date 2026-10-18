package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.ReservationId;

/** Solicitud de cálculo: reserva. Fuente: UC04 plan T010. */
public record CalculateReservationValueCommand(ReservationId reservationId) {
}
