package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistencia de {@code reservation_status_log} (UC07) con {@code INSERT ... ON CONFLICT DO NOTHING}.
 * Fuente: UC07 plan T001/T004 y §Reglas 7-8; D-UC07-11.
 *
 * <p>La fila reclama una operación antes de delegarla: si la clave de negocio
 * {@code (reservation_id, status, operation_type, idempotency_key)} ya existe, la operación no se
 * repite. El {@code id} lo genera el servicio para poder marcar el procesado sin releer la fila.</p>
 */
public interface ReservationStatusLogRepository {

    boolean insertIfAbsent(UUID id, ReservationId reservationId, ReservationStatus status,
            FinancialOperationType operationType, IdempotencyKey idempotencyKey, Instant statusChangedAt);

    boolean markProcessed(UUID id, Instant processedAt, String outcome);
}
