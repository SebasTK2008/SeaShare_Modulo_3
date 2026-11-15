package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo del evento {@code reservation.info.provided} (Reservas → sistema).
 *
 * <p>Los componentes usan {@code camelCase} y el {@code JsonMapper} de Spring Boot aplica la
 * estrategia {@code SNAKE_CASE} del contrato. Los enteros son {@link Integer} (no {@code int}) para
 * distinguir un campo ausente o {@code null} de un valor {@code 0}. Fuente: contrato
 * {@code events/UC03-informacion-reserva.md} §2 y UC03 plan T007.</p>
 */
public record ReservationInfoMessage(
        UUID reservationId,
        UUID boatId,
        LocalDate startDate,
        LocalDate endDate,
        Integer passengers,
        UUID ownerId,
        Integer maxCapacity) {
}
