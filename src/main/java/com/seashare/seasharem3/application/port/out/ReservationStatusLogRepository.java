package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;

/**
 * Persistencia de reservation_status_log (UC07) con INSERT … ON CONFLICT DO NOTHING.
 * Fuente: UC07 plan T001/T004 y §Reglas 7–8.
 * GAP: firma no publicada; se deriva el registro idempotente por (reserva, estado, tipo de operación, clave).
 */
public interface ReservationStatusLogRepository {

    boolean insertIfAbsent(ReservationId reservationId, String status, FinancialOperationType operationType,
                           IdempotencyKey idempotencyKey, Instant statusChangedAt);
}
