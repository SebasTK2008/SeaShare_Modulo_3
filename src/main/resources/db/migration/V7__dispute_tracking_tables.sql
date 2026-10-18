-- Fase 7 (UC08) del historial Flyway único ordenado por fase (general-plan D-31).
-- Fuente: UC08 plan D-UC08-01/T008a (UC08 conserva la DDL exacta de ambas tablas) y general-plan §4 (líneas 232-233).

CREATE TABLE deposit_disposition (
    reservation_id UUID         PRIMARY KEY,
    completed_at   TIMESTAMPTZ  NOT NULL,  -- fechaHoraEstado del estado COMPLETED recibido por UC07
    dispute_id     UUID,
    dispute_status VARCHAR(32),
    disposition    VARCHAR(32)  NOT NULL,
    CONSTRAINT chk_deposit_disposition_disposition
        CHECK (disposition IN ('HELD', 'REFUND_REQUESTED', 'SETTLEMENT_REQUESTED')),
    CONSTRAINT fk_deposit_disposition_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation_information (reservation_id)
);

-- El desenlace se aplica con un único compare-and-set sobre disposition (general-plan D-34).

-- CHOQUE (general-plan §4.1): el ER declara deposit_disposition.dispute_id como string; aquí se tipa
-- UUID (DisputeId) de forma consistente con refund_record/refund_intent. Registrado en core-gaps.md.

CREATE TABLE dispute_event_log (
    id                UUID         PRIMARY KEY,
    reservation_id    UUID         NOT NULL,
    dispute_id        UUID         NOT NULL,
    event_key         VARCHAR(128) NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    status_changed_at TIMESTAMPTZ  NOT NULL,
    processed_at      TIMESTAMPTZ,
    CONSTRAINT uq_dispute_event_log UNIQUE (reservation_id, dispute_id, event_key),
    CONSTRAINT fk_dispute_event_log_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation_information (reservation_id)
);
