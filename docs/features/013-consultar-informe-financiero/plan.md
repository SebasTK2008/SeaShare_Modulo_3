# Implementation Plan: UC13 - Consultar informe financiero

**Date**: 2026-10-09  
**Spec**: [spec.md](spec.md)

## Summary

UC13 es el caso de uso REST que agrega los registros financieros confirmados para el Administrador Financiero o el Propietario en períodos quincenales, mensuales o trimestrales. La consulta devuelve el último período cerrado por defecto, o el período explícitamente seleccionado, junto con la comparación con el período anterior equivalente [SPEC RF-001..RF-011].

También expone la exportación CSV. La consulta JSON y el CSV deben usar el mismo resolvedor de período, alcance, agregador y DTO interno; el archivo incluye las mismas métricas más periodicidad, período, alcance, solicitante y fecha de generación [SPEC RF-012, RF-013, RNF-001, RNF-004]. UC13 solo lee registros inmutables, no consulta Flota, no incluye depósitos pendientes y no devuelve el detalle paginado de UC12.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001, RF-002 | `Periodicity`, `ReportPeriodResolver` | T004, T007 | T013 |
| RF-003, RF-004 | `ReportPeriodResolver` | T004, T007 | **T014 `ce003_…`** |
| RF-005, RF-006 | `FinancialReportQueryPort`, `FinancialReportService` | T005, T008 | **T012 `ce001_…`** |
| RF-007, RF-008 | `FinancialAggregator` | T005, T008 | **T013 `ce002_…`** |
| RF-009 | `CommissionMetrics`, `FinancialAggregator` | T005, T008 | T013 |
| RF-010 | `PeriodComparison`, variation policy | T005, T008 | **T015 `ce004_…`** |
| RF-011 | `FinancialReportResult` | T006, T009 | T011 |
| RF-012, RF-013 | `ExportFinancialReportUseCase`, `CsvReportExporter` | T009, T010 | **T016 `ce006_…`** |
| RF-014 | query predicate on confirmed records | T005 | **T017 `ce005_…`** |
| RF-015 | ausencia de `FleetPort` | T005, T010 | T017 |
| RNF-001 | request/result/report DTOs | T006, T009 | T011, T016 |
| RNF-002 | `BigDecimal`/`Money` | T005, T008 | **T012 `ce001_…`** |
| RNF-003 | `created_at` del registro inmutable | T005 | T012, T015 |
| RNF-004 | agregador compartido por JSON/CSV | T008, T010 | **T016 `ce006_…`** |
| CE-001 | scope resolver | T006, T008 | **T012 `ce001_…`** |
| CE-002 | net/ganancia y comisión | T008 | **T013 `ce002_…`** |
| CE-003 | period resolver | T004, T007 | **T014 `ce003_…`** |
| CE-004 | previous period/comparison | T005 | **T015 `ce004_…`** |
| CE-005 | confirmed-record predicate | T005 | **T017 `ce005_…`** |
| CE-006 | common report + CSV pipeline | T009, T010 | **T016 `ce006_…`** |
| HU1 | informe de plataforma | Fase 3 | T012-T015 |
| HU2 | informe propio | Fase 3 | T012-T015 |
| HU3 | exportación | Fase 3 | T016 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.1; Spring Web MVC, Validation, Data JPA (Hibernate), Security OAuth2 Resource Server, Actuator, Flyway, MapStruct, Micrometer/Prometheus y ArchUnit. UC13 usa además el escritor CSV elegido por el proyecto, MockMvc, Spring Security Test y Testcontainers. Dependencias compartidas se agregan mediante `UC11·T001`–`UC11·T009`; este plan no modifica `pom.xml`.  
**Storage**: PostgreSQL 16+; lectura de `v_financial_records`/registros inmutables confirmados, `NUMERIC(18,4)` y `timestamptz`. UC13 no crea tablas ni modifica registros [general-plan §4].  
**Messaging**: N/A para consulta y exportación; ambos endpoints son síncronos y de solo lectura.  
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Spring Security Test, Testcontainers PostgreSQL, pruebas de CSV y ArchUnit [CONV].  
**Target Platform**: contenedores Docker sobre Linux; desarrollo con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: agregación indexada por alcance y `created_at`; el SPEC no fija latencia ni volumen [NEEDS CLARIFICATION OQ-UC13-01]. Consulta y exportación no cargan detalle paginado completo ni consultan Flota.  
**Constraints**: `BigDecimal` a 4 decimales; períodos fijos, sin rangos libres; comisiones informativas sin doble conteo; depósitos pendientes excluidos; mismo cálculo para JSON/CSV; sin mutaciones ni datos externos a los registros definidos [SPEC RNF-002..RNF-004].  
**Scale/Scope**: dos endpoints REST, tres periodicidades, dos alcances, cuatro tipos de registro, 15 RF + 4 RNF + 6 CE + 3 HU [SPEC 13].

## Project Structure

### Documentation

```text
docs/features/013-consultar-informe-financiero/
├── plan.md
└── spec.md
docs/technical-plan/contracts/rest/
├── UC13-consultar-informe-financiero.md
└── UC13-exportar-informe-financiero.md
```

### Source Code

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java
├── domain/
│   ├── model/{ReportPeriod,FinancialReport,ReportMetrics,PeriodComparison}.java
│   ├── valueobject/{Periodicity,ReportScope,Money}.java
│   ├── service/{ReportPeriodResolver,FinancialAggregator,VariationCalculator}.java
│   └── exception/{DomainException,InvalidPeriodException}.java
├── application/
│   ├── port/in/
│   │   ├── GetFinancialReportUseCase.java
│   │   └── ExportFinancialReportUseCase.java
│   ├── port/out/FinancialReportQueryPort.java
│   ├── service/FinancialReportService.java
│   └── dto/
│       ├── GetFinancialReportCommand.java
│       ├── FinancialReportResult.java
│       └── FinancialReportExportResult.java
└── infrastructure/
    ├── adapter/in/web/financialreport/
    │   ├── FinancialReportController.java
    │   ├── FinancialReportExceptionHandler.java
    │   └── dto/{FinancialReportResponse,FinancialReportExportResponse}.java
    ├── adapter/out/persistence/financialreport/
    │   ├── FinancialReportQueryAdapter.java
    │   ├── FinancialRecordProjection.java
    │   └── FinancialReportQueryMapper.java
    ├── adapter/out/export/csv/CsvReportExporter.java
    └── config/{SecurityConfig,ProblemDetailsConfig,ObservabilityConfig}.java

src/main/resources/db/migration/
└── V1__create_financial_report_support.sql # solo documentado; sin migración propia aplicada

src/test/java/com/seashare/seasharem3/
├── arch/ArchitectureTest.java
├── contract/{FinancialReportControllerContractTest,FinancialReportExportContractTest}.java
├── domain/service/{ReportPeriodResolverTest,FinancialAggregatorTest}.java
├── application/service/FinancialReportServiceTest.java
├── infrastructure/adapter/in/web/FinancialReportSecurityTest.java
├── infrastructure/adapter/out/export/CsvReportExporterTest.java
└── integration/FinancialReportConsistencyIT.java
```

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4 y el patrón de UC01/UC08/UC12. `domain` no depende de Spring/JPA/Jackson; `application` solo depende de `domain`; controllers invocan únicamente `application.port.in`; query adapter implementa `FinancialReportQueryPort`; CSV es un adaptador de salida; ningún controller accede a repositorios; DTOs HTTP/CSV viven en infraestructura.

**Firmas referenciadas**: UC13 publica `GetFinancialReportUseCase` y `ExportFinancialReportUseCase` solo para sus adaptadores REST. Consume registros/vista mediante `FinancialReportQueryPort`; no reimplementa la paginación de UC12, aunque reutiliza su fuente de registros. UC12, UC10 y la seguridad transversal son dependencias externas y no se modifican.

## Reglas de negocio

1. **Periodicidades** [SPEC RF-001, RF-002]: `QUINCENAL` = día 1–15 / 16–fin; `MENSUAL` = mes calendario; `TRIMESTRAL` = Q1 enero-marzo, Q2 abril-junio, Q3 julio-septiembre, Q4 octubre-diciembre.
2. **Período por defecto** [SPEC RF-003; contrato consulta §2]: sin `period`, usar último período cerrado y comparar con el anterior equivalente.
3. **Selección** [SPEC RF-004]: aceptar solo identificadores canónicos (`YYYY-MM-H1/H2`, `YYYY-MM`, `YYYY-Qn` según periodicidad); no aceptar rangos libres.
4. **Período abierto** [SPEC casos extremos; contrato §3]: el período actual puede devolverse parcial con `is_closed=false` y corte de consulta.
5. **Alcance** [SPEC RF-005, RF-006]: Administrador agrega plataforma; Propietario agrega solo registros de sus reservas/propietario autenticado.
6. **Neto plataforma** [SPEC RF-008]: `cobros − reembolsos − dispersiones`; no resta comisiones nuevamente.
7. **Ganancia propietario** [SPEC RF-008]: total de dispersiones confirmadas a su favor, incluyendo alquiler, cancelaciones y depósitos liquidados.
8. **Comisiones** [SPEC RF-009]: total informativo independiente y conteo por tipo; no entra de nuevo en neto/ganancia.
9. **Comparación** [SPEC RF-010]: comparar mismo tipo de período inmediatamente anterior; variación absoluta y porcentual; si anterior es cero, porcentaje `null`/no calculable.
10. **Inclusión temporal** [SPEC RF-007, RNF-003]: usar `created_at` del registro inmutable confirmado; no incluir intenciones ni depósitos pendientes.
11. **Sin Flota** [SPEC RF-015]: alcance y agregación se resuelven desde identidad y registros internos.
12. **Mismo resultado en CSV** [SPEC RNF-004]: exportar el mismo `FinancialReportResult`; solo agregar columnas de solicitante y generación requeridas por contrato.
13. **Solo lectura** [SPEC RF-013]: ninguna consulta/exportación modifica registros.

## Contratos REST

Fuentes: `rest/UC13-consultar-informe-financiero.md`, `rest/UC13-exportar-informe-financiero.md`; convenciones `contracts/README.md` §3.1–§4.

### Consulta

```text
GET /api/v1/financial-report
Authorization: Bearer <token>
Accept: application/json
```

Query: `periodicity` obligatorio; `period` opcional. Respuesta `200` incluye `periodicity`, `period`, `is_closed`, `scope`, `net_amount`, totales de cobros/reembolsos/dispersiones/comisiones, counts y comparison. Los campos operativos adicionales del contrato (`total_gross_volume`, `retained_funds`, `disputed_amount`, `open_disputes_count`) deben derivarse únicamente de datos definidos en el modelo; si no existe fuente contractual, se registran como `[PEND]` y no se inventan.

### Exportación

```text
GET /api/v1/financial-report/export
Authorization: Bearer <token>
Accept: text/csv
```

`periodicity` y `period` son obligatorios. Respuesta `200 text/csv` con `Content-Disposition`; contiene métricas, periodicidad, período, alcance, solicitante y fecha de generación. Ambos endpoints usan el mismo servicio/agregador. Errores: `400 INVALID_PERIOD`/`VALIDATION_ERROR`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `500 INTERNAL_ERROR`; errores preferiblemente `application/problem+json` incluso en descarga.

## Estrategia de testing

Cobertura objetivo [CONV]: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Cobertura | Ubicación |
|---|---|---|
| Unitario dominio | períodos, cierres, fórmulas, variaciones y BigDecimal | `domain/service/*Test` |
| Unitario aplicación | alcance, agregación, comparación y reutilización CSV | `FinancialReportServiceTest` |
| Contrato/Web | query params, JSON, CSV, headers y errores | `contract/*ContractTest` |
| Seguridad | 401/403 y alcance por identidad | `FinancialReportSecurityTest` |
| Integración | fuente confirmada, fechas, consulta/exportación idénticas | `FinancialReportConsistencyIT` |
| Arquitectura | capas, sin Flota y sin mutaciones | `arch/ArchitectureTest` |

- **`ce001_scope_platform_and_owner`**: admin ve plataforma; propietario solo sus reservas, en JSON y CSV.
- **`ce002_net_without_double_commission`**: fórmulas correctas y comisión informativa sin doble conteo.
- **`ce003_fixed_period_boundaries`**: quincenas, meses, trimestres; rangos libres rechazados.
- **`ce004_previous_equivalent_comparison`**: anterior equivalente, variación absoluta/porcentual y cero anterior no calculable.
- **`ce005_pending_deposits_excluded`**: depósitos sin registro confirmado no afectan métricas.
- **`ce006_csv_matches_json_report`**: CSV y consulta usan los mismos agregados, alcance, período y periodicidad.

## Discrepancias y preguntas abiertas

| ID | Descripción | Propuesta por defecto | Estado |
|---|---|---|---|
| **OQ-UC13-01** | El SPEC no fija SLA ni volumen | agregación indexada, sin cargar detalle completo; medir p95 | Abierta |
| **D-UC13-01** | Contrato añade métricas operativas no explicitadas en RF (`retained_funds`, etc.) | no inventar datos; derivar solo si existe fuente en registros/modelo y marcar formato como [PEND] | Abierta |
| **D-UC13-02** | Consulta permite `period` omitido pero exportación lo exige | consulta usa último cerrado; exportación exige período explícito, según contratos | Decidida por contrato |
| **D-UC13-03** | UC12 y UC13 consultan registros, pero UC13 necesita agregación común | consumir puerto de informe sobre la misma fuente; no reimplementar paginación | Decidida |
| **D-UC13-04** | Seguridad transversal no está cerrada | heredar `SecurityConfig`; documentar 401/403 sin definir mecanismo local | Diferida |
| **D-UC13-05** | Numeración de migraciones del bloque C | documentar **V1**; consolidación asigna definitiva | Decidida |

## Implementation Phases

### Phase 1: Setup — Compartido

- [ ] **T001** Referenciar `UC11·T001`–`UC11·T009` para web, validación, seguridad, errores y observabilidad.
- [ ] **T002** Acordar la firma del `FinancialReportQueryPort` con UC12/los dueños de registros sin modificar sus planes.
- [ ] **T003** Configurar serialización decimal, `Content-Disposition`, correlación y generación de timestamp.

### Phase 2: Foundational

- [ ] **T004** Implementar `Periodicity`, `ReportPeriod` y `ReportPeriodResolver`, incluidos cierre y anterior equivalente.
- [ ] **T005** Implementar query/agregación por alcance sobre registros confirmados y `created_at`; excluir pendientes e intenciones.
- [ ] **T006** Crear commands/results, métricas, conteos y comparación temporal.
- [ ] **T007** Crear validación REST y `Problem Details` para periodicidad/período.
- [ ] **T008** Implementar `FinancialReportService` como única fuente de cálculo para consulta y exportación.

### Phase 3: User Stories

- [ ] **T009** [US1/P1] Exponer consulta global para `ADMIN_FINANCIERO`.
- [ ] **T010** [US2/P1] Exponer consulta propia para `PROPIETARIO`.
- [ ] **T011** [US3/P2] Implementar `CsvReportExporter` y endpoint de exportación usando el mismo resultado agregado.
- [ ] **T012** `ce001_scope_platform_and_owner`.
- [ ] **T013** `ce002_net_without_double_commission`.
- [ ] **T014** `ce003_fixed_period_boundaries`.
- [ ] **T015** `ce004_previous_equivalent_comparison`.
- [ ] **T016** `ce006_csv_matches_json_report`.
- [ ] **T017** `ce005_pending_deposits_excluded`.

### Phase 4: Polish

- [ ] **T018** Pruebas de período abierto, cambio de año, febrero, cero anterior, informe vacío y métricas opcionales [PEND].
- [ ] **T019** Verificar solo lectura, ausencia de Flota, seguridad, headers CSV y errores JSON.
- [ ] **T020** ArchUnit, cobertura y verificación final; no editar contratos ni planes externos.

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009/T010 → T011 → T012..T020`.

UC13 depende de registros confirmados y de la vista/puertos publicados por UC05, UC09, UC10 y UC12. Publica dos puertos internos propios únicamente para sus adaptadores REST. Migración: **V1**, solo referencia documental; numeración definitiva y cualquier cambio de vista se consolidan después.

## Notes

No modificar `pom.xml`, `general-plan.md`, SPEC, contratos ni planes de UC05/UC09/UC10/UC11/UC12. No consultar Flota, no devolver detalle paginado de UC12, no duplicar comisiones y no generar CSV mediante un cálculo separado.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 13, ambos contratos REST y `contracts/README.md`.
- [ ] RF/RNF/CE/HU trazados a componente, tarea y prueba.
- [ ] Consulta y exportación reutilizan exactamente el mismo agregador.
- [ ] Períodos, alcance, comparación, comisiones y depósitos pendientes documentados.
- [ ] Migración indicada como V1 y ningún cambio externo aplicado.
