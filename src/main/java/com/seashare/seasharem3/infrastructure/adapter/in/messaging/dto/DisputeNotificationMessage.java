package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo del evento {@code reservation.dispute.updated} (Reservas → sistema).
 *
 * <p>Los componentes usan {@code camelCase} y el {@code JsonMapper} de Spring Boot aplica la
 * estrategia {@code SNAKE_CASE} del contrato. El estado se deja como {@link String} para poder
 * rechazar valores no canonicos con un mensaje de validacion. Fuente: contrato
 * {@code events/UC08-disputa-garantia.md} §2 y UC08 plan T006.</p>
 */
public record DisputeNotificationMessage(
        UUID reservationId,
        UUID disputeId,
        String status,
        Instant statusChangedAt,
        String eventKey) {
}
