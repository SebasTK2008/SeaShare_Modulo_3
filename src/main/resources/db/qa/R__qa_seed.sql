-- =====================================================================
-- Seed base de QA — Módulo 3 (Finanzas)
-- =====================================================================
-- Se ejecuta solo cuando el perfil `qa` está activo:
--   SPRING_PROFILES_ACTIVE=dev,qa
-- y `spring.flyway.locations` incluye `classpath:db/qa`
-- (ver src/main/resources/application-qa.properties).
--
-- Contenido: únicamente datos generales compartidos por los TC.
-- NO se siembran reservas, intenciones, cobros, reembolsos, dispersiones,
-- disputas ni registros financieros: esos estados son específicos de cada
-- caso de prueba y deben prepararse en el TC correspondiente.
--
-- `financial_parameters` es append-only (trigger anti-mutación). Este seed
-- inserta las versiones 1..7 y deja la 7 como vigente. Si ya existe una
-- versión con el mismo `version_number`, no se duplica.
-- =====================================================================

INSERT INTO financial_parameters (
    id,
    version_number,
    commission_pct,
    insurance_fee_per_passenger,
    weekend_increase_pct,
    high_season_increase_pct,
    created_at,
    created_by
) VALUES
    ('00000000-0000-4000-8000-000000000001', 1, 10.0000, 10000.0000,  5.0000, 15.0000, '2026-03-01T10:00:00Z', 'qa-seed'),
    ('00000000-0000-4000-8000-000000000002', 2, 11.0000, 11000.0000,  5.0000, 18.0000, '2026-04-01T10:00:00Z', 'qa-seed'),
    ('00000000-0000-4000-8000-000000000003', 3, 12.0000, 12000.0000,  8.0000, 20.0000, '2026-05-01T10:00:00Z', 'qa-seed'),
    ('00000000-0000-4000-8000-000000000004', 4, 13.0000, 13000.0000,  8.0000, 22.0000, '2026-06-01T10:00:00Z', 'qa-seed'),
    ('00000000-0000-4000-8000-000000000005', 5, 14.0000, 14000.0000, 10.0000, 25.0000, '2026-08-01T10:00:00Z', 'qa-seed'),
    ('00000000-0000-4000-8000-000000000006', 6, 14.5000, 14500.0000, 10.0000, 25.0000, '2026-09-01T10:00:00Z', 'qa-seed'),
    ('00000000-0000-4000-8000-000000000007', 7, 15.0000, 15000.0000, 10.0000, 25.0000, '2026-10-01T10:00:00Z', 'qa-seed')
ON CONFLICT (version_number) DO NOTHING;
