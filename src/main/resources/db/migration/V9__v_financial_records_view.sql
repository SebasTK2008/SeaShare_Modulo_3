-- Fase 9 (UC12/UC13) del historial Flyway único ordenado por fase (general-plan D-31).
-- Fuente: general-plan §4 "Vista de lectura (UC12/UC13)" y core-gaps.md (V9 propuesta).
-- UNION ALL de las cuatro tablas inmutables con columnas comunes.

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
