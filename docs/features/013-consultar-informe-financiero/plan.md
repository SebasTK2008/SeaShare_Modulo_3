# Implementation Plan: UC13 - Consultar informe financiero

**Date**: 2026-10-10
**Spec**: [spec.md](spec.md)

## Summary

UC13 consulta y exporta informes agregados de registros financieros confirmados para `ADMIN_FINANCIERO` o `OWNER`. Usa períodos quincenales, mensuales y trimestrales, el período anterior equivalente y el mismo agregador para JSON y CSV [SPEC RF-001..RF-015, HU1..HU3].

La fuente es `v_financial_records`, creada por UC12 con `record_id` y `NULL::text AS external_reference` para comisiones. El alcance se obtiene de la identidad autenticada; UC13 no consulta Flota, no devuelve el detalle paginado de UC12 y no modifica registros. `q` y las métricas adicionales del contrato permanecen como OQ-CROSS-14/D-26; el CSV incluye únicamente los campos exigidos por el SPEC.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001..RF-004 | `ReportPeriodResolver` | T004,T007 | T013 |
| RF-005..RF-006 | `FinancialReportQueryPort`, scope resolver | T005,T008 | T012 |
| RF-007..RF-009 | `FinancialAggregator` | T005,T008 | T013 |
| RF-010 | `PeriodComparison` | T005,T008 | T014 |
| RF-011 | `FinancialReportResult` | T006,T009 | T011 |
| RF-012..RF-013 | `ExportFinancialReportUseCase`, CSV adapter | T009,T010 | T015 |
| RF-014..RF-015 | confirmed-record predicate, no FleetPort | T005,T010 | T016 |
| RNF-001..RNF-004 | DTOs, ClockPort, common aggregator | T003,T006,T008,T010 | T011,T015 |
| CE-001 | scope resolver | T008 | **T012 `ce001_…`** |
| CE-002 | net/earnings/commission metrics | T008 | **T013 `ce002_…`** |
| CE-003 | fixed period resolver | T004,T007 | **T014 `ce003_…`** |
| CE-004 | previous equivalent period | T005 | **T014 `ce004_…`** |
| CE-005 | confirmed records only | T005 | **T016 `ce005_…`** |
| CE-006 | shared report/CSV result | T009,T010 | **T015 `ce006_…`** |
| HU1/HU2/HU3 | platform, owner, export | T008-T010 | T012-T016 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.1; Web MVC, Validation, Data JPA, Security OAuth2 Resource Server, Actuator, MapStruct, Micrometer/Prometheus, ArchUnit y librería CSV. Cambios de dependencias pertenecen al núcleo compartido y no se aplican desde este plan.
**Storage**: PostgreSQL 16+; lectura de `v_financial_records` (`UNION ALL`, orden `created_at DESC, record_id DESC`), `NUMERIC(18,4)` y `timestamptz`. UC13 no crea tablas.
**Messaging**: N/A; ambos endpoints son REST síncronos y de solo lectura.
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Spring Security Test, Testcontainers PostgreSQL y ArchUnit.
**Target Platform**: contenedores Docker sobre Linux.
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: agregación indexada; el SPEC no fija SLA [NEEDS CLARIFICATION OQ-UC13-01].
**Constraints**: `BigDecimal` a 4 decimales, no rangos libres, comisiones informativas sin doble conteo, depósitos pendientes excluidos, consulta/CSV con idéntico cálculo y alcance.
**Scale/Scope**: dos endpoints REST, tres periodicidades, dos alcances, cuatro tipos de registro y 15 RF + 4 RNF + 6 CE + 3 HU.

## Project Structure

```text
docs/features/013-consultar-informe-financiero/{spec.md,plan.md}
docs/technical-plan/contracts/rest/{UC13-consultar-informe-financiero.md,UC13-exportar-informe-financiero.md}

src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── model/{ReportPeriod,FinancialReport,ReportMetrics,PeriodComparison}.java
│   ├── valueobject/{Periodicity,ReportScope}.java # VOs compartidos no se redefinen
│   └── service/{ReportPeriodResolver,FinancialAggregator,VariationCalculator}.java
├── application/
│   ├── port/in/{GetFinancialReportUseCase,ExportFinancialReportUseCase}.java
│   ├── port/out/FinancialReportQueryPort.java
│   ├── service/FinancialReportService.java
│   └── dto/{GetFinancialReportCommand,FinancialReportResult,FinancialReportExportResult}.java
└── infrastructure/
    ├── adapter/in/web/financialreport/
    │   ├── FinancialReportController.java
    │   ├── FinancialReportExceptionHandler.java
    │   └── dto/{FinancialReportResponse,FinancialReportExportResponse}.java
    ├── adapter/out/persistence/financialreport/FinancialReportQueryAdapter.java
    ├── adapter/out/export/csv/CsvReportExporter.java
    └── config/{ProblemDetailsConfig,ObservabilityConfig}.java

src/test/java/com/seashare/seasharem3/
├── contract/{FinancialReportControllerContractTest,FinancialReportExportContractTest}.java
├── domain/service/{ReportPeriodResolverTest,FinancialAggregatorTest}.java
├── application/service/FinancialReportServiceTest.java
├── integration/FinancialReportConsistencyIT.java
└── arch/ArchitectureTest.java
```

**Structure Decision**: `domain` no depende de Spring/JPA/Jackson; `application` solo depende de `domain`; adaptadores de entrada invocan `port.in`; adaptadores de salida implementan `port.out`; el controller no accede a repositorios. `ClockPort` y los VOs `Money`, `ReservationId`, `OwnerId` se consumen del núcleo compartido y no se redeclaran. Zona horaria explícita: `seashare.timezone=America/Bogota` mediante `ClockPort` [D-23].

## Reglas de negocio

1. Quincenas: días 1–15 y 16–fin; meses calendario; trimestres Q1–Q4 [SPEC RF-002].
2. Consulta sin período: último período cerrado y comparación anterior equivalente [SPEC RF-003].
3. No se aceptan rangos libres [SPEC RF-004].
4. `ADMIN_FINANCIERO`: plataforma; `OWNER`: registros propios [SPEC RF-005/006].
5. Neto plataforma = cobros − reembolsos − dispersiones; ganancias del propietario = dispersiones confirmadas [SPEC RF-008].
6. Comisiones son métrica informativa y no se contabilizan dos veces [SPEC RF-009].
7. Variación porcentual con período anterior cero = no calculable [SPEC RF-010].
8. Inclusión por `created_at`; pendientes/intenciones quedan fuera [SPEC RF-014, RNF-003].
9. El CSV reutiliza exactamente `FinancialReportResult`; incluye solo datos agregados exigidos por el SPEC, incluidos conteos por tipo y la comparación con el período anterior cuando correspondan a RF-009, RF-010 y RF-013. Las métricas extra del contrato (`retained_funds`, `disputed_amount`, etc.) permanecen condicionadas a OQ-CROSS-14/D-CROSS-26 [SPEC RF-013].
10. `q` y métricas contractuales adicionales quedan abiertas bajo OQ-CROSS-14/D-26; no se presentan como aprobadas.

## Contratos REST

`GET /api/v1/financial-report`: `periodicity` obligatorio y `period` opcional. `GET /api/v1/financial-report/export`: `periodicity` y `period` obligatorios, respuesta `text/csv`. Ambos usan Problem Details para `INVALID_PERIOD`, `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN` e `INTERNAL_ERROR` según los contratos existentes.

## Estrategia de testing

| Nivel | Cobertura | Ubicación |
|---|---|---|
| Unitario dominio | períodos, cierres, fórmulas y variaciones | `domain/service/*Test` |
| Unitario aplicación | alcance y agregación única | `FinancialReportServiceTest` |
| Contrato/Web | JSON, CSV, headers y errores | `contract/*Test` |
| Integración | vista, fechas y equivalencia JSON/CSV | `FinancialReportConsistencyIT` |
| Arquitectura | capas, sin Flota ni mutaciones | `ArchitectureTest` |

- `ce001_scope_platform_and_owner`
- `ce002_net_without_double_commission`
- `ce003_fixed_period_boundaries`
- `ce004_previous_equivalent_comparison`
- `ce005_pending_deposits_excluded`
- `ce006_csv_matches_json_report`

## Discrepancias y preguntas abiertas

| ID | Situación | Estado |
|---|---|---|
| OQ-UC13-01 | SLA/volumen no definidos por SPEC | Abierta |
| OQ-CROSS-14 / D-26 | `q`, métricas extra y alcance exacto de campos adicionales del CSV | Abierta; no se asume solución |
| D-UC13-01 | La vista pertenece a UC12; UC13 la consume mediante puerto | Aplicada; UC13 no crea ni redefine la vista |
| D-23 | ClockPort y zona horaria explícita | Aplicada |
| D-20 | VOs compartidos no se redeclaran | Aplicada |
| OQ-CROSS-07 / D-08 | Política y orden de migraciones | Abierta; no se asigna `V1` aprobada |
| OQ-CROSS-13 / D-16 | Seguridad, claims y AMQP | Abierta |

## Implementation Phases

### Phase 1: Setup

- [ ] T001 Configurar propiedades de zona horaria, correlación y serialización decimal; consumir infraestructura compartida existente.
- [ ] T002 Verificar la firma de `FinancialReportQueryPort` y el contrato de `v_financial_records` de UC12: `record_id`, `created_at DESC, record_id DESC`, y `NULL::text AS external_reference` para comisiones.

### Phase 2: Foundational

- [ ] T003 Consumir `ClockPort`, `Money`, `ReservationId` y `OwnerId` del núcleo compartido; no redeclarar VOs.
- [ ] T004 Implementar `ReportPeriodResolver` con zona horaria explícita y período anterior equivalente.
- [ ] T005 Implementar consulta/agregación sobre registros confirmados y filtro por scope.
- [ ] T006 Crear DTOs de aplicación, métricas y comparación.
- [ ] T007 Implementar validación y Problem Details conforme a contratos.
- [ ] T008 Implementar `FinancialReportService` como fuente única de cálculo.

### Phase 3: User Stories

- [ ] T009 [US1] Exponer informe de plataforma para `ADMIN_FINANCIERO`.
- [ ] T010 [US2] Exponer informe propio para `OWNER`.
- [ ] T011 [US3] Exportar CSV desde el resultado común, solo con campos definidos por SPEC; `q`/métricas extra quedan condicionados a OQ-CROSS-14.
- [ ] T012 `ce001_scope_platform_and_owner`.
- [ ] T013 `ce002_net_without_double_commission`.
- [ ] T014 `ce003_fixed_period_boundaries` y `ce004_previous_equivalent_comparison`.
- [ ] T015 `ce006_csv_matches_json_report`.
- [ ] T016 `ce005_pending_deposits_excluded`.

### Phase 4: Polish

- [ ] T017 Probar período abierto, zona horaria, informe vacío, cero anterior y seguridad diferida.
- [ ] T018 ArchUnit, cobertura y validación final de que no se consulta Flota ni se mutan registros.

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009/T010 → T011 → T012..T018`.

UC13 depende de la vista y registros confirmados publicados por UC12/UC10/UC09/UC05 y del núcleo compartido. No se aplican migraciones desde este plan; la política queda en OQ-CROSS-07/D-08.

## Notes

No modificar SPEC, contratos, plantilla, `general-plan.md` ni otros planes. Las decisiones B permanecen abiertas y no se presentan como aprobadas.
