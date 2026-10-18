-- Fase 3 (UC03) del historial Flyway único ordenado por fase (general-plan D-31).
-- DDL exacta publicada por UC03 plan §Migración (copia literal).

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
