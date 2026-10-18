-- Fase 6 (UC10) del historial Flyway único ordenado por fase (general-plan D-31).
-- Fuente: UC10 plan T002/T013 y general-plan §4 (líneas 229-231, 246, 250).

CREATE TABLE settlement_intent (
    id                UUID          PRIMARY KEY,
    reservation_id    UUID          NOT NULL,
    trigger           VARCHAR(64)   NOT NULL,
    scope             VARCHAR(64)   NOT NULL,
    amount            NUMERIC(18,4) NOT NULL,
    commission_amount NUMERIC(18,4) NOT NULL,
    deposit_amount    NUMERIC(18,4),
    charge_intent_id  UUID          NOT NULL,
    idempotency_key   VARCHAR(255)  NOT NULL,
    status            VARCHAR(32)   NOT NULL,
    status_detail     TEXT,         -- [CONV] detalle del estado externo (simétrico a charge/refund_intent)
    external_reference TEXT,        -- [CONV] referencia externa de la pasarela (UC10 plan T012a); omitida en general-plan §4
    dispute_id        UUID,         -- [CONV] traza de la disputa adjudicada (UC10 plan T012a; scope DEPOSIT); omitida en general-plan §4
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),  -- [CONV] antigüedad para conciliación (general-plan §7.3); omitida en general-plan §4
    CONSTRAINT uq_settlement_intent_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_settlement_intent_charge FOREIGN KEY (charge_intent_id) REFERENCES charge_intent (id)
);

-- scope vigente: STANDARD | CANCELLATION_COMPENSATION | DEPOSIT (general-plan §4; sin DEPOSIT_DISPUTE).
CREATE INDEX idx_settlement_intent_reservation ON settlement_intent (reservation_id);

-- [CONV] índice para asociar el webhook de liquidación (data.id) con la intención (UC10 §Reglas).
CREATE INDEX idx_settlement_intent_external_reference
    ON settlement_intent (external_reference)
    WHERE external_reference IS NOT NULL;

CREATE TABLE settlement_record (
    id                   UUID          PRIMARY KEY,
    settlement_intent_id UUID          NOT NULL,
    reservation_id       UUID          NOT NULL,
    owner_id             UUID          NOT NULL,
    boat_id              UUID          NOT NULL,
    amount               NUMERIC(18,4) NOT NULL,
    detail               TEXT,
    rental_gross_amount  NUMERIC(18,4) NOT NULL,
    insurance_amount     NUMERIC(18,4) NOT NULL,
    deposit_amount       NUMERIC(18,4) NOT NULL,
    external_reference   TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_settlement_record_intent FOREIGN KEY (settlement_intent_id) REFERENCES settlement_intent (id),
    -- [CONV] general-plan §4.1 declara settlement_intent ||--o| settlement_record (0..1).
    CONSTRAINT uq_settlement_record_settlement_intent UNIQUE (settlement_intent_id)
);

CREATE INDEX idx_settlement_record_owner_created ON settlement_record (owner_id, created_at DESC);
CREATE INDEX idx_settlement_record_boat          ON settlement_record (boat_id);
CREATE INDEX idx_settlement_record_reservation   ON settlement_record (reservation_id);

CREATE TRIGGER trg_settlement_record_immutable
    BEFORE UPDATE OR DELETE ON settlement_record
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();

CREATE TABLE commission_record (
    id                   UUID          PRIMARY KEY,
    settlement_record_id UUID          NOT NULL,
    settlement_intent_id UUID          NOT NULL,
    reservation_id       UUID          NOT NULL,
    owner_id             UUID          NOT NULL,
    boat_id              UUID          NOT NULL,
    amount               NUMERIC(18,4) NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_commission_record_settlement_record UNIQUE (settlement_record_id),
    CONSTRAINT fk_commission_record_settlement_record FOREIGN KEY (settlement_record_id) REFERENCES settlement_record (id),
    CONSTRAINT fk_commission_record_settlement_intent FOREIGN KEY (settlement_intent_id) REFERENCES settlement_intent (id)
);

CREATE INDEX idx_commission_record_owner_created ON commission_record (owner_id, created_at DESC);
CREATE INDEX idx_commission_record_boat          ON commission_record (boat_id);
CREATE INDEX idx_commission_record_reservation   ON commission_record (reservation_id);

CREATE TRIGGER trg_commission_record_immutable
    BEFORE UPDATE OR DELETE ON commission_record
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();
