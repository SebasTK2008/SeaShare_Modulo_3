-- Fase 8 (UC07) del historial Flyway único ordenado por fase (general-plan D-31).
-- Fuente: UC07 plan T001 (DDL exacta propiedad de UC07) y general-plan §4 (línea 234).

CREATE TABLE reservation_status_log (
    id                UUID         PRIMARY KEY,
    reservation_id    UUID         NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    operation_type    VARCHAR(32)  NOT NULL,  -- CHARGE | REFUND | SETTLEMENT (UC07 §Reglas 8)
    idempotency_key   VARCHAR(255) NOT NULL,  -- derivada de reservationId + status + statusChangedAt (D-UC07-02)
    status_changed_at TIMESTAMPTZ  NOT NULL,
    processed_at      TIMESTAMPTZ,
    outcome           TEXT,                   -- texto corto sin valores fijados (UC07 §Reglas 8)
    CONSTRAINT uq_reservation_status_log
        UNIQUE (reservation_id, status, operation_type, idempotency_key),
    CONSTRAINT fk_reservation_status_log_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation_information (reservation_id)
);

-- Inserción idempotente con INSERT ... ON CONFLICT DO NOTHING (UC07 §Reglas 7);
-- solo para los estados que disparan operaciones monetarias (PENDING, cancelaciones y COMPLETED).
