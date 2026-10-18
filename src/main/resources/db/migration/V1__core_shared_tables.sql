-- Fase 1 (núcleo compartido) del historial Flyway único ordenado por fase (general-plan D-31).
-- GAP: los planes no publican DDL literal para estas tablas técnicas; se derivan de general-plan §4.

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
