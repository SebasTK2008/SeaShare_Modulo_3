package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;

/**
 * Notificación de disputa mapeada desde el mensaje AMQP.
 * Fuente: UC08 plan T006; contrato events/UC08-disputa-garantia.md §Body (5 campos).
 * GAP: composición exacta del record no enumerada en el plan; campos tomados del contrato.
 */
public record DisputeNotificationCommand(ReservationId reservationId, DisputeId disputeId, String status,
                                         Instant statusChangedAt, String eventKey) {
}
