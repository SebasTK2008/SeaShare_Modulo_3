-- Migración inicial única del Sistema Financiero de SEA-SHARE (Módulo 3).
-- Fuente de autoría: general-plan §4 y §4.1 (docs/technical-plan/general-plan.md).
-- Contiene TODO el esquema en una sola migración: las 15 tablas, sus CHECK/UNIQUE/FK,
-- índices, la vista v_financial_records y los triggers anti-mutación.
-- Política de migraciones: general-plan §4.2 y D-31 — única migración inicial; los cambios
-- posteriores se añaden como V2__… en orden cronológico y append-only. No existen migraciones
-- por caso de uso ni por fase.

-- =====================================================================
-- UC11 · financial_parameters (antes V2__financial_parameters.sql)
-- DDL de financial_parameters versionada append-only; fuente: UC11 plan T002 y general-plan §4.
-- =====================================================================

CREATE TABLE financial_parameters (
    id                          UUID          PRIMARY KEY,
    version_number              BIGINT        NOT NULL,
    commission_pct              NUMERIC(18,4) NOT NULL,
    insurance_fee_per_passenger NUMERIC(18,4) NOT NULL,
    weekend_increase_pct        NUMERIC(18,4) NOT NULL,
    high_season_increase_pct    NUMERIC(18,4) NOT NULL,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                  TEXT,
    CONSTRAINT uq_financial_parameters_version_number UNIQUE (version_number),
    CONSTRAINT chk_financial_parameters_commission_pct
        CHECK (commission_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_financial_parameters_weekend_increase_pct
        CHECK (weekend_increase_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_financial_parameters_high_season_increase_pct
        CHECK (high_season_increase_pct BETWEEN 0 AND 100),
    CONSTRAINT chk_financial_parameters_insurance_fee
        CHECK (insurance_fee_per_passenger >= 0)
);

-- Refuerzo append-only recomendado por UC11 §Reglas 4 (denegar UPDATE/DELETE en BD).
CREATE OR REPLACE FUNCTION reject_financial_parameters_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'financial_parameters es append-only: se prohíben UPDATE y DELETE';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_financial_parameters_immutable
    BEFORE UPDATE OR DELETE ON financial_parameters
    FOR EACH ROW EXECUTE FUNCTION reject_financial_parameters_mutation();

-- GAP: la primera versión (version_number = 1) la crea el PUT de UC11, no esta migración.

-- =====================================================================
-- UC03 · reservation_information (antes V3__reservation_information.sql)
-- DDL exacta publicada por UC03 plan §Migración (copia literal).
-- =====================================================================

CREATE TABLE reservation_information (
    reservation_id                  UUID         PRIMARY KEY,             -- [SPEC RF-001; general-plan §4]
    boat_id                         UUID         NOT NULL,                -- [SPEC RF-001]
    base_rate                       NUMERIC(18,4) NOT NULL,               -- [SPEC RNF-002; escala 4, sin redondeo]
    base_rate_parameters_id         UUID,                                 -- [CONV; D-24/D-27] versión de financial_parameters usada por la tarifa dinámica (UC02); NULL si la fecha era regular
    start_date                      DATE         NOT NULL,                -- [SPEC RF-001]
    end_date                        DATE         NOT NULL,                -- [SPEC RF-001]
    passengers                      INTEGER      NOT NULL,                -- [SPEC RF-001]
    owner_id                        UUID         NOT NULL,                -- [SPEC RF-001/RF-003: recibido en el mensaje, nunca de Flota]
    max_capacity                    INTEGER      NOT NULL,                -- [SPEC RF-001/RF-003]
    -- Columnas calculadas por UC04; NULL hasta entonces y anuladas por el upsert de UC03 (D-UC03-02):
    rental_amount                   NUMERIC(18,4),
    insurance_amount                NUMERIC(18,4),
    deposit_amount                  NUMERIC(18,4),
    total_amount                    NUMERIC(18,4),
    frozen_parameters_id            UUID,                                 -- [CONV; D-27] referencia a la versión usada por UC04 (única fuente de los valores aplicados: comisión % y seguro por pasajero); NULL hasta entonces
    calculated_at                   TIMESTAMPTZ,
    CONSTRAINT chk_reservation_dates     CHECK (end_date >= start_date),                -- [SPEC RF-005]
    CONSTRAINT chk_reservation_passengers CHECK (passengers BETWEEN 1 AND max_capacity), -- [SPEC RF-005]
    CONSTRAINT fk_reservation_base_rate_parameters FOREIGN KEY (base_rate_parameters_id) REFERENCES financial_parameters (id), -- [CONV; D-24]
    CONSTRAINT fk_reservation_frozen_parameters    FOREIGN KEY (frozen_parameters_id)    REFERENCES financial_parameters (id)  -- [CONV; D-27]
);

-- =====================================================================
-- Núcleo técnico compartido (antes V1__core_shared_tables.sql)
-- GAP: los planes no publican DDL literal para estas tablas técnicas; se derivan de general-plan §4.
-- =====================================================================

CREATE TABLE operational_failure (
    id              UUID         PRIMARY KEY,
    use_case        VARCHAR(100) NOT NULL,
    reservation_id  UUID,
    reason          TEXT         NOT NULL,
    payload_ref     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved        BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Decisión (UC11 T046): reservation_id nullable y SIN FK a reservation_information.
-- [CONV] general-plan §4 no documenta índices para operational_failure; se crean para las dos
-- consultas previsibles (fallos no resueltos en orden de antigüedad y fallos por reserva).
CREATE INDEX idx_operational_failure_unresolved
    ON operational_failure (created_at)
    WHERE resolved = FALSE;

CREATE INDEX idx_operational_failure_reservation
    ON operational_failure (reservation_id)
    WHERE reservation_id IS NOT NULL;

CREATE TABLE outbox_message (
    id            UUID         PRIMARY KEY,
    exchange      VARCHAR(255) NOT NULL,
    routing_key   VARCHAR(255) NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_message_unpublished
    ON outbox_message (created_at)
    WHERE published_at IS NULL;

-- GAP: general-plan §4 solo exige "índice parcial published_at IS NULL" sin fijar columnas;
-- se indexa created_at (orden de publicación) hasta que un plan lo refine.

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

-- GAP: general-plan §4 solo lista la columna name; se usan las 4 columnas canónicas de ShedLock.
-- GAP: los privilegios por rol no están documentados en ningún plan.

-- =====================================================================
-- UC05 · charge_intent y charge_record (antes V4__charge_intent_and_charge_record.sql)
-- Fuente: UC05 plan T001/T002 y general-plan §4 (tabla charge_intent y charge_record).
-- =====================================================================

CREATE TABLE charge_intent (
    id                  UUID          PRIMARY KEY,
    reservation_id      UUID          NOT NULL,
    idempotency_key     VARCHAR(255)  NOT NULL,
    status              VARCHAR(32)   NOT NULL,
    amount              NUMERIC(18,4) NOT NULL,
    payment_token_ref   TEXT          NOT NULL,
    payment_method_type VARCHAR(64),
    last_four           VARCHAR(4),
    external_reference  TEXT,
    status_detail       TEXT,         -- [D-UC05-02; D-UC06-02] detalle del estado externo publicado por UC05 (lo consume UC06)
    requested_amount    NUMERIC(18,4), -- [D-UC05-02] monto solicitado; el núcleo no lo modela aun (GAP)
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT uq_charge_intent_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_charge_intent_reservation FOREIGN KEY (reservation_id) REFERENCES reservation_information (reservation_id)
);

-- Sin UNIQUE(reservation_id): se permiten múltiples intenciones por reserva (UC05 RF-004A).
CREATE INDEX idx_charge_intent_reservation_created
    ON charge_intent (reservation_id, created_at DESC);

-- [CONV] general-plan §4 no define índice para external_reference; necesario para asociar el
-- webhook de la pasarela (data.id) con la intención registrada (UC05 §Reglas 5).
CREATE INDEX idx_charge_intent_external_reference
    ON charge_intent (external_reference)
    WHERE external_reference IS NOT NULL;

CREATE TABLE charge_record (
    id                  UUID          PRIMARY KEY,
    charge_intent_id    UUID          NOT NULL,
    reservation_id      UUID          NOT NULL,
    owner_id            UUID          NOT NULL,
    boat_id             UUID          NOT NULL,
    amount              NUMERIC(18,4) NOT NULL,
    rental_amount       NUMERIC(18,4) NOT NULL,
    insurance_amount    NUMERIC(18,4) NOT NULL,
    deposit_amount      NUMERIC(18,4) NOT NULL,
    payment_method_type VARCHAR(64),
    last_four           VARCHAR(4),
    external_reference  TEXT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_charge_record_intent FOREIGN KEY (charge_intent_id) REFERENCES charge_intent (id),
    -- [CONV] general-plan §4.1 declara charge_intent ||--o| charge_record (0..1): una intención
    -- exitosa genera a lo sumo un registro inmutable; evita duplicados por webhook concurrente.
    CONSTRAINT uq_charge_record_charge_intent UNIQUE (charge_intent_id)
);

CREATE INDEX idx_charge_record_owner_created ON charge_record (owner_id, created_at DESC);
CREATE INDEX idx_charge_record_boat          ON charge_record (boat_id);
CREATE INDEX idx_charge_record_reservation   ON charge_record (reservation_id);

-- Función reutilizable de inmutabilidad para las tablas *_record (general-plan §4; reutilizada por UC09 y UC10).
CREATE OR REPLACE FUNCTION reject_record_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Las tablas *_record son inmutables: se prohíben UPDATE y DELETE';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_charge_record_immutable
    BEFORE UPDATE OR DELETE ON charge_record
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();

-- GAP: privilegios del usuario de aplicación sobre las tablas *_record no documentados
-- (además del trigger, denegar UPDATE/DELETE por GRANT/REVOKE según entorno).

-- =====================================================================
-- UC09 · refund_intent y refund_record (antes V5__refund_intent_and_refund_record.sql)
-- Fuente: UC09 plan T001 (DDL exacta de UC09) y general-plan §4 (refund_intent y refund_record).
-- =====================================================================

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
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),  -- [CONV] antigüedad para conciliación (general-plan §7.3); omitido en general-plan §4
    CONSTRAINT uq_refund_intent_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_refund_intent_charge FOREIGN KEY (charge_intent_id) REFERENCES charge_intent (id)
);

CREATE INDEX idx_refund_intent_reservation ON refund_intent (reservation_id);

-- [CONV] índice para asociar el webhook de reembolso (data.id) con la intención (UC09 §Reglas).
CREATE INDEX idx_refund_intent_external_reference
    ON refund_intent (external_reference)
    WHERE external_reference IS NOT NULL;

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
    CONSTRAINT fk_refund_record_intent FOREIGN KEY (refund_intent_id) REFERENCES refund_intent (id),
    -- [CONV] general-plan §4.1 declara refund_intent ||--o| refund_record (0..1).
    CONSTRAINT uq_refund_record_refund_intent UNIQUE (refund_intent_id)
);

CREATE INDEX idx_refund_record_owner_created ON refund_record (owner_id, created_at DESC);
CREATE INDEX idx_refund_record_boat          ON refund_record (boat_id);
CREATE INDEX idx_refund_record_reservation   ON refund_record (reservation_id);

CREATE TRIGGER trg_refund_record_immutable
    BEFORE UPDATE OR DELETE ON refund_record
    FOR EACH ROW EXECUTE FUNCTION reject_record_mutation();

-- =====================================================================
-- UC10 · settlement_intent, settlement_record y commission_record (antes V6__settlement_tables.sql)
-- Fuente: UC10 plan T002/T013 y general-plan §4 (settlement_intent, settlement_record y commission_record).
-- =====================================================================

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

-- =====================================================================
-- UC08 · deposit_disposition y dispute_event_log (antes V7__dispute_tracking_tables.sql)
-- Fuente: UC08 plan D-UC08-01/T008a (UC08 conserva la DDL exacta de ambas tablas) y general-plan §4.
-- =====================================================================

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

-- =====================================================================
-- UC07 · reservation_status_log (antes V8__reservation_status_log.sql)
-- Fuente: UC07 plan T001 (DDL exacta propiedad de UC07) y general-plan §4.
-- =====================================================================

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

-- =====================================================================
-- UC12/UC13 · vista v_financial_records (antes V9__v_financial_records_view.sql)
-- Fuente: general-plan §4 "Vista de lectura (UC12/UC13)" y core-gaps.md (V9 propuesta).
-- UNION ALL de las cuatro tablas inmutables con columnas comunes.
-- =====================================================================

CREATE VIEW v_financial_records AS
SELECT
    'COBRO'::text        AS record_type,
    id                   AS record_id,
    reservation_id       AS reservation_id,
    owner_id             AS owner_id,
    boat_id              AS boat_id,
    amount               AS amount,
    created_at           AS created_at,
    external_reference   AS external_reference
FROM charge_record
UNION ALL
SELECT
    'REEMBOLSO'::text,
    id,
    reservation_id,
    owner_id,
    boat_id,
    amount,
    created_at,
    external_reference
FROM refund_record
UNION ALL
SELECT
    'DISPERSION'::text,
    id,
    reservation_id,
    owner_id,
    boat_id,
    amount,
    created_at,
    external_reference
FROM settlement_record
UNION ALL
SELECT
    'COMISION'::text,
    id,
    reservation_id,
    owner_id,
    boat_id,
    amount,
    created_at,
    NULL::text
FROM commission_record;
