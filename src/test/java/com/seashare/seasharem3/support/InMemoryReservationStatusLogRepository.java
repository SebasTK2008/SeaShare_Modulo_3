package com.seashare.seasharem3.support;

import com.seashare.seasharem3.application.port.out.ReservationStatusLogRepository;
import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fake en memoria de {@link ReservationStatusLogRepository} con la semántica del adaptador JPA:
 * la clave de negocio reclama la operación una sola vez y {@code markProcessed} la confirma.
 * [CONV] núcleo compartido para las pruebas de UC07.
 */
public class InMemoryReservationStatusLogRepository implements ReservationStatusLogRepository {

    private final Map<String, LogRow> rows = new LinkedHashMap<>();

    @Override
    public boolean insertIfAbsent(UUID id, ReservationId reservationId, ReservationStatus status,
            FinancialOperationType operationType, IdempotencyKey idempotencyKey, Instant statusChangedAt) {
        String key = key(reservationId, status, operationType, idempotencyKey);
        return rows.putIfAbsent(key, new LogRow(id, reservationId, status, operationType, idempotencyKey,
                statusChangedAt, null, null)) == null;
    }

    @Override
    public boolean markProcessed(UUID id, Instant processedAt, String outcome) {
        for (Map.Entry<String, LogRow> entry : rows.entrySet()) {
            if (entry.getValue().id().equals(id)) {
                LogRow row = entry.getValue();
                rows.put(entry.getKey(), new LogRow(row.id(), row.reservationId(), row.status(),
                        row.operationType(), row.idempotencyKey(), row.statusChangedAt(), processedAt, outcome));
                return true;
            }
        }
        return false;
    }

    public List<LogRow> rows() {
        return List.copyOf(new ArrayList<>(rows.values()));
    }

    public void clear() {
        rows.clear();
    }

    private static String key(ReservationId reservationId, ReservationStatus status,
            FinancialOperationType operationType, IdempotencyKey idempotencyKey) {
        return reservationId.value() + "|" + status + "|" + operationType + "|" + idempotencyKey.value();
    }

    /** Fila de {@code reservation_status_log} tal como la ve la aplicación. */
    public record LogRow(UUID id, ReservationId reservationId, ReservationStatus status,
            FinancialOperationType operationType, IdempotencyKey idempotencyKey, Instant statusChangedAt,
            Instant processedAt, String outcome) {
    }
}
