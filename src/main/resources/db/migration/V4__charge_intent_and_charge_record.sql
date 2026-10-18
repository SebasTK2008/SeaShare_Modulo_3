-- Fase 4 (UC05) del historial Flyway único ordenado por fase (general-plan D-31).
-- Fuente: UC05 plan T001/T002 y general-plan §4 (líneas 225-226, 250, 252).

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

-- Función reutilizable de inmutabilidad para las tablas *_record (general-plan §4; reutilizada en V5/V6).
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
