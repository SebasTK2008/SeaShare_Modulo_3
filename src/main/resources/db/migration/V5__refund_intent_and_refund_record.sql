-- Fase 5 (UC09) del historial Flyway único ordenado por fase (general-plan D-31).
-- Fuente: UC09 plan T001 (DDL exacta de UC09) y general-plan §4 (líneas 227-228, 250).

CREATE TABLE refund_intent (
    id                 UUID          PRIMARY KEY,
    reservation_id     UUID          NOT NULL,
    trigger            VARCHAR(64)   NOT NULL,
    scope              VARCHAR(64)   NOT NULL,
    amount             NUMERIC(18,4) NOT NULL,
    charge_intent_id   UUID          NOT NULL,
    idempotency_key    VARCHAR(255)  NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    status_detail      TEXT,         -- [D-UC09-02] detalle del estado externo
    dispute_id         UUID,         -- [D-UC09-02] nullable (solo trigger DISPUTA_RECHAZADO)
    external_reference TEXT,
    CONSTRAINT uq_refund_intent_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_refund_intent_charge FOREIGN KEY (charge_intent_id) REFERENCES charge_intent (id)
);

CREATE INDEX idx_refund_intent_reservation ON refund_intent (reservation_id);

-- CHOQUE (general-plan §4.1): el ER declara refund_intent.dispute_id como string; aquí se tipa UUID
-- (DisputeId) siguiendo D-UC09-02 y la entrada de disputas del sistema. Registrado en core-gaps.md.

CREATE TABLE refund_record (
    id                 UUID          PRIMARY KEY,
    refund_intent_id   UUID          NOT NULL,
    reservation_id     UUID          NOT NULL,
    owner_id           UUID          NOT NULL,
    boat_id            UUID          NOT NULL,
    dispute_id         UUID,
    amount             NUMERIC(18,4) NOT NULL,
    detail             TEXT,
    transaction_cost   NUMERIC(18,4),
    external_reference TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_refund_record_intent FOREIGN KEY (refund_intent_id) REFERENCES refund_intent (id)
);

CREATE INDEX idx_refund_record_owner_created ON refund_record (owner_id, created_at DESC);
CREATE INDEX idx_refund_record_boat          ON refund_record (boat_id);
CREATE INDEX idx_refund_record_reservation   ON refund_record (reservation_id);

CREATE TRIGGER trg_refund_record_immutable
    BEFORE UPDATE OR DELETE ON refund_record
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
