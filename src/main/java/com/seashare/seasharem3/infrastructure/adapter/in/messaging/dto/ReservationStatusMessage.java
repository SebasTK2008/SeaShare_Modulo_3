package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Cuerpo del evento {@code reservation.status.changed} (Reservas -> sistema).
 *
 * <p>Los componentes usan {@code camelCase} y el {@code JsonMapper} de Spring Boot aplica la
 * estrategia {@code SNAKE_CASE} del contrato. El estado se deja como {@link String} para poder
 * rechazar valores no canónicos con un mensaje de validación. Fuente: contrato
 * {@code events/UC07-estado-reserva.md} §2 y UC07 plan T008.</p>
 */
public record ReservationStatusMessage(
        UUID reservationId,
        String status,
        Instant statusChangedAt,
        String paymentTokenRef,
        String paymentMethodType,
        Map<String, String> paymentMetadata) {
}
