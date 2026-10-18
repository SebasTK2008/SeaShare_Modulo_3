-- Fase 2 (UC11) del historial Flyway único ordenado por fase (general-plan D-31).
-- DDL de financial_parameters versionada append-only; fuente: UC11 plan T002 y general-plan §4.

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
