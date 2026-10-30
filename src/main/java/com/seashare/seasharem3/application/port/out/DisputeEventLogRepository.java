package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de {@code dispute_event_log} (UC08) con deduplicacion por
 * {@code (reservation_id, dispute_id, event_key)}.
 *
 * <p>La insercion es la reclamacion idempotente del evento: si la clave ya existe no se vuelve a
 * insertar. {@code processedAt} distingue el evento reclamado del ya procesado. Fuente: UC08 plan
 * T008 y D-UC08-06.</p>
 */
public interface DisputeEventLogRepository {

    boolean insertIfAbsent(DisputeEvent event);

    Optional<DisputeEvent> findByKey(ReservationId reservationId, DisputeId disputeId, String eventKey);

    boolean markProcessed(UUID eventId, Instant processedAt);
}
