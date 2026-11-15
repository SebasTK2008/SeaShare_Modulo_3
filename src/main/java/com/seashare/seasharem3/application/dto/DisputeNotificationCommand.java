package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;

/**
 * Notificacion de disputa mapeada desde el mensaje AMQP. El estado llega ya tipado: el mapper aplica
 * la regla de estados canonicos antes de construir el comando.
 * Fuente: UC08 plan T006; contrato events/UC08-disputa-garantia.md §Body (5 campos).
 */
public record DisputeNotificationCommand(ReservationId reservationId, DisputeId disputeId, DisputeStatus status,
                                         Instant statusChangedAt, String eventKey) {
}
