# Implementation Plan: UC12 - Consultar registros financieros

**Date**: 2026-10-09  
**Spec**: [spec.md](spec.md)

## Summary

UC12 es el caso de uso **REST, síncrono y de solo lectura** que permite al Propietario consultar los registros financieros de sus reservas y al Administrador Financiero consultar el alcance global. Devuelve exclusivamente registros inmutables y confirmados de cobro, reembolso, dispersión y comisión; nunca expone intenciones en curso o fallidas [SPEC RF-003, RF-004, RF-010, RF-011].

La consulta se realiza sobre `v_financial_records`, aplicando primero el alcance derivado de la identidad autenticada y después los filtros. Todas las respuestas son paginadas, con tamaño por defecto 10, orden estable y metadatos completos. UC12 no consulta Flota, no muta registros y no exporta archivos; la exportación pertenece a UC13 [SPEC RF-007..RF-013].

Enfoque técnico: servicio Spring Boot único con arquitectura hexagonal (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3`, controller REST que invoca `QueryFinancialRecordsUseCase`, puerto de consulta de solo lectura sobre la vista consolidada y DTOs HTTP separados de commands/results. La vista y los registros son propiedad de otros planes; UC12 los consume por nombre y no redefine sus tablas.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001 | `FinancialRecordsController`, request/query DTO | T006, T007 | T012 |
| RF-002 | `RecordFilter`, query adapter | T004, T008 | T013 |
| RF-003, RF-011 | `FinancialRecordScopeResolver`, `SecurityContext` | T005, T008 | **T014 `ce001_…`**, T015 |
| RF-004 | `FinancialRecordQueryPort` sobre `v_financial_records` | T004 | T013, T016 |
| RF-005, RF-006 | `FinancialRecordResult`, mapper | T006, T009 | **T017 `ce004_…`** |
| RF-007, RF-008 | `PageResult`, `PageRequestPolicy` | T003, T008 | **T018 `ce003_…`** |
| RF-009 | validación + `PAGE_OUT_OF_RANGE` | T003, T007 | T012, T018 |
| RF-010 | puerto/query de solo lectura | T004, T010 | **T019 `ce005_…`** |
| RF-012 | ausencia de `FleetRatePort`/cliente Flota | T004, T010 | T019 |
| RF-013 | ausencia de export adapter | T010 | T019 |
| RNF-001 | DTOs HTTP y application result | T006, T009 | T012 |
| RNF-002 | `Money`/`BigDecimal` y serialización decimal | T009 | T017 |
| RNF-003 | orden estable y cursor lógico por `(created_at, id)` | T004, T008 | **T016 `ce002_…`** |
| CE-001 | alcance Propietario | T005, T008 | **T014 `ce001_…`** |
| CE-002 | alcance Administrador | T005, T008 | **T015 `ce002_…`** |
| CE-003 | paginación/default 10 | T003, T008 | **T018 `ce003_…`** |
| CE-004 | detalle y referencia externa | T009 | **T017 `ce004_…`** |
| CE-005 | read-only/no export | T004, T010 | **T019 `ce005_…`** |
| HU1 | consulta propia | Fase 3 | T014, T016 |
| HU2 | consulta global | Fase 3 | T015, T016 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.1; Spring Web MVC, Validation, Data JPA (Hibernate), Security OAuth2 Resource Server, Actuator, Flyway, MapStruct, Micrometer/Prometheus y ArchUnit. UC12 usa especialmente Web MVC, JPA/read-only query, seguridad, MockMvc, Testcontainers y pruebas de arquitectura. Las dependencias pertenecen al núcleo compartido; este plan no modifica `pom.xml`.
**Storage**: PostgreSQL 16+; UC12 crea `v_financial_records` como `UNION ALL` de `charge_record`, `refund_record`, `settlement_record` y `commission_record`, con `record_id`, orden estable `(created_at DESC, record_id DESC)` y `NULL::text AS external_reference` para comisiones. Usa `NUMERIC(18,4)` y `timestamptz` [D-07].
**Messaging**: N/A para la entrada de UC12; no consume ni publica eventos en este caso de uso.  
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Spring Security Test, Testcontainers PostgreSQL y ArchUnit [CONV].  
**Target Platform**: contenedores Docker sobre Linux; desarrollo local con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: consulta paginada indexada por `owner_id`, `reservation_id`, `boat_id` y `created_at`; el SPEC no fija latencia numérica [NEEDS CLARIFICATION OQ-UC12-01]. El diseño evita cargar el histórico completo en memoria.  
**Constraints**: solo lectura; alcance del Propietario se aplica antes de filtros; tamaño por defecto 10; orden estable sin omisiones/duplicados; `BigDecimal` a 4 decimales; sin Flota, mutaciones ni exportación [SPEC RF-003, RF-007, RF-010, RF-012, RF-013, RNF-002/003].  
**Scale/Scope**: un endpoint REST, cuatro tipos de registro, dos roles, cinco filtros del contrato (`transaction_type`, `owner_id`, `boat_id`, `reservation_id`, fechas/q) y 13 RF + 3 RNF + 5 CE + 2 HU [SPEC 12].

## Project Structure

### Documentation

```text
docs/features/012-consultar-registros-financieros/
├── plan.md
└── spec.md
docs/technical-plan/contracts/rest/UC12-consultar-registros-financieros.md
```

### Source Code

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java
├── domain/
│   ├── model/{FinancialRecord,RecordType,RecordFilter,PageResult}.java
│   ├── valueobject/{Money,ReservationId,OwnerId,BoatId}.java
│   └── exception/{DomainException,InvalidPageException}.java
├── application/
│   ├── port/in/QueryFinancialRecordsUseCase.java
│   ├── port/out/FinancialRecordQueryPort.java
│   ├── service/FinancialRecordsQueryService.java
│   └── dto/
│       ├── QueryFinancialRecordsCommand.java
│       ├── FinancialRecordResult.java
│       └── FinancialRecordsPageResult.java
└── infrastructure/
    ├── adapter/in/web/financialrecords/
    │   ├── FinancialRecordsController.java
    │   ├── FinancialRecordsExceptionHandler.java
    │   └── dto/{FinancialRecordsRequest,FinancialRecordsResponse}.java
    ├── adapter/out/persistence/financialrecords/
    │   ├── FinancialRecordQueryAdapter.java
    │   ├── FinancialRecordProjection.java
    │   └── FinancialRecordQueryMapper.java
    └── config/{SecurityConfig,ProblemDetailsConfig,ObservabilityConfig}.java

src/main/resources/db/migration/
└── V*__create_financial_records_view.sql # versión sujeta a OQ-CROSS-07/D-08

src/test/java/com/seashare/seasharem3/
├── arch/ArchitectureTest.java
├── contract/FinancialRecordsControllerContractTest.java
├── domain/model/FinancialRecordQueryTest.java
├── application/service/FinancialRecordsQueryServiceTest.java
├── infrastructure/adapter/in/web/FinancialRecordsSecurityTest.java
├── infrastructure/adapter/out/persistence/FinancialRecordQueryAdapterTest.java
└── integration/FinancialRecordsPaginationIT.java
```

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4. Los VOs compartidos (`Money`, `ReservationId`, `OwnerId`, `ClockPort`) se consumen del núcleo compartido y no se redeclaran. `domain` no depende de Spring/JPA/Jackson; `application` solo depende de `domain`; controller invoca solo `application.port.in`; query adapter implementa `FinancialRecordQueryPort`; no se expone entidad JPA ni se accede a repositorios desde el controller.

**Firmas referenciadas**: UC12 publica `QueryFinancialRecordsUseCase` para su adaptador REST y define la vista que UC13 consume. Consume `FinancialRecordQueryPort`/registros publicados por UC05, UC09 y UC10; no modifica sus planes. Zona horaria: `ClockPort` con `seashare.timezone` [D-23].

## Reglas de negocio

1. **Alcance** [SPEC RF-003, RF-011; contrato §3.1]: Propietario filtra siempre por su identidad y reservas asociadas; Administrador Financiero puede consultar plataforma completa y filtrar por propietario.
2. **Filtros** [SPEC RF-002; contrato §2]: `CHARGE`, `REFUND`, `SETTLEMENT`, `COMMISSION`, propietario y embarcación; contrato añade reserva, fechas y `q` como filtros técnicos. Todos se combinan con AND.
3. **Comisión** [SPEC RF-002]: `COMMISSION` devuelve únicamente `CommissionRecord`; nunca se compara numéricamente la comisión para decidir el tipo.
4. **Fuente** [SPEC RF-004]: solo cuatro registros inmutables confirmados; intenciones, pendientes y fallidos quedan fuera.
5. **Detalle** [SPEC RF-005, RF-006]: cada resultado contiene tipo, reserva, propietario, embarcación, monto, `created_at` y referencia externa opcional.
6. **Paginación** [SPEC RF-007, RF-008, RNF-003]: `page` es 1-indexed, `size` por defecto 10, orden determinista `(created_at DESC, record_id DESC)`, sin omisiones ni duplicados.
7. **Página fuera de rango** [SPEC RF-009; contrato §5]: `PAGE_OUT_OF_RANGE` 404; filtros sin resultados producen página vacía, no error.
8. **Validación** [SPEC RF-009; contrato §5]: `page < 1`, `size` inválido o enumeración inválida → `VALIDATION_ERROR` 400 sin ejecutar query.
9. **Seguridad** [SPEC RF-011; contrato §5]: credencial ausente → `UNAUTHENTICATED`; rol no autorizado → `FORBIDDEN`; mecanismo se hereda de la decisión transversal [PEND OQ-01].
10. **Sin Flota** [SPEC RF-012]: alcance y filtros se resuelven con identidad y datos financieros internos, nunca con Gestión de Flota.
11. **Solo lectura** [SPEC RF-010, RF-013]: no crear/modificar/eliminar registros, no actualizar intenciones y no generar CSV.
12. **Serialización monetaria** [SPEC RNF-002; README §3.1]: `amount` se serializa como string decimal; cálculo/proyección mantiene escala interna 4.

## Contrato REST

Fuente: `rest/UC12-consultar-registros-financieros.md`; convenciones `contracts/README.md` §3.1–§4.

```text
GET /api/v1/financial-records
Authorization: Bearer <token>
Accept: application/json
X-Correlation-Id: opcional
```

Query parameters: `page` obligatorio; `size` opcional (10); `transaction_type`, `owner_id`, `boat_id`, `reservation_id`, `q`, `date_from`, `date_to` opcionales. El filtro `owner_id` nunca amplía el alcance de un Propietario.

Respuesta `200 OK`:

```json
{
  "content": [{
    "record_type": "CHARGE",
    "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
    "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
    "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
    "amount": "990000.00",
    "created_at": "2026-12-05T14:30:00Z",
    "external_reference": "TXN-987654321"
  }],
  "page_number": 1,
  "page_size": 10,
  "total_elements": 1,
  "total_pages": 1
}
```

Errores: `400 VALIDATION_ERROR`, `404 PAGE_OUT_OF_RANGE`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `500 INTERNAL_ERROR`. Todos siguen Problem Details, sin detalles internos en 5xx. La consulta es segura para reintentar.

## Estrategia de testing

Cobertura objetivo [CONV]: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Cobertura | Ubicación |
|---|---|---|
| Unitario dominio | filtros, tipos, paginación y moneda | `domain/model/FinancialRecordQueryTest` |
| Unitario aplicación | alcance, filtros, default, páginas y errores | `application/service/FinancialRecordsQueryServiceTest` |
| Contrato/Web | query params, JSON, códigos y metadatos | `FinancialRecordsControllerContractTest` |
| Seguridad | 401/403 y aislamiento por rol | `FinancialRecordsSecurityTest` |
| Integración | vista, orden estable, páginas y solo lectura | `FinancialRecordsPaginationIT` |
| Arquitectura | capas, ausencia de Flota y ausencia de export | `arch/ArchitectureTest` |

- **`ce001_alcance_propietario`**: ningún registro de otro propietario aparece aunque se envíen `owner_id`/`boat_id` ajenos.
- **`ce002_cobertura_global_sin_duplicados`**: Administrador obtiene conjunto global filtrado, recorriendo todas las páginas sin omisiones/duplicados.
- **`ce003_paginacion_default_y_metadatos`**: default 10, `page_number`, `page_size`, `total_elements`, `total_pages`; página vacía válida.
- **`ce004_detalle_completo_y_comision`**: cuatro tipos, campos obligatorios y referencia externa opcional; comisión solo por tipo.
- **`ce005_solo_lectura_sin_exportacion`**: ninguna operación de escritura ni archivo generado; no se invoca Flota.

## Discrepancias y preguntas abiertas

| ID | Descripción | Propuesta por defecto | Estado |
|---|---|---|---|
| **OQ-UC12-01** | El SPEC no fija latencia ni volumen | usar consulta paginada indexada y no cargar histórico completo; medir p95 en integración | Abierta |
| **D-UC12-01** | El contrato añade `reservation_id`, `q`, fechas y filtro de propietario | aceptarlos como filtros técnicos sin ampliar el alcance del SPEC; validar tipos y combinarlos con AND | Aplicada |
| **D-07** | Dueño y forma de `v_financial_records` | UC12 crea la vista con `record_id`, orden estable y `NULL::text` para comisiones | Aplicada |
| **D-UC12-03** | Seguridad transversal no está cerrada | documentar 401/403 y heredar `SecurityConfig`; no inventar mecanismo local | Diferida |
| **OQ-CROSS-07 / D-08** | Política y orden de migraciones | mantener abierta y respetar dependencias | Abierta |
| **OQ-CROSS-14 / D-26** | Semántica de `q` y métricas adicionales | UC12 implementa solo la búsqueda y métricas exigidas por el SPEC; no agrega métricas ni columnas | Abierta; requiere decisión externa |

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Consumir web, validación, seguridad, errores y observabilidad del núcleo compartido.
- [ ] **T002** Crear DDL de `v_financial_records` con `record_id`, `NULL::text AS external_reference` para comisiones y orden estable; incluir pruebas de esquema.
- [ ] **T003** Configurar parámetros de paginación y correlación; no agregar exportación ni cliente de Flota.

### Phase 2: Foundational

- [ ] **T004** Crear modelos, tipos, filtros, query port y adapter read-only sobre la vista.
- [ ] **T005** Resolver alcance por identidad/rol antes de aplicar filtros; no consultar Flota.
- [ ] **T006** Crear commands/results y DTOs HTTP del contrato.
- [ ] **T007** Implementar validación, `Problem Details`, `PAGE_OUT_OF_RANGE` y serialización monetaria.
- [ ] **T008** Implementar paginación estable `(created_at DESC, record_id DESC)` y default 10.

### Phase 3: User Stories

- [ ] **T009** [US1/P1] Implementar consulta paginada propia, filtros y aislamiento de propietario.
- [ ] **T010** [US2/P1] Implementar consulta global de Administrador y filtros combinados.
- [ ] **T011** Exponer `GET /api/v1/financial-records` y mapear respuesta completa sin exponer entidades.
- [ ] **T012** Tests de contrato: 200, vacío, filtros, validaciones, página fuera de rango, 401/403.
- [ ] **T013** `ce001_alcance_propietario`.
- [ ] **T014** `ce002_cobertura_global_sin_duplicados`.
- [ ] **T015** `ce003_paginacion_default_y_metadatos`.
- [ ] **T016** Integración de orden estable y consistencia entre páginas.
- [ ] **T017** `ce004_detalle_completo_y_comision`.

### Phase 4: Polish

- [ ] **T018** Pruebas read-only, ausencia de Flota y ausencia de exportación.
- [ ] **T019** `ce005_solo_lectura_sin_exportacion` y ArchUnit.
- [ ] **T020** Revisar cobertura, contratos y `./mvnw clean verify`; sin modificar archivos externos.

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009/T010 → T011 → T012..T020`.

UC12 depende de los registros confirmados publicados por UC05, UC09 y UC10 y define la vista consumida por UC13. La política de migraciones queda abierta en OQ-CROSS-07/D-08.

## Notes

No modificar `pom.xml`, `general-plan.md`, SPEC, contrato ni planes de UC05/UC09/UC10/UC11/UC13. No crear/modificar/eliminar registros, no consultar Flota y no generar CSV.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 12, contrato REST UC12 y `contracts/README.md`.
- [ ] RF/RNF/CE/HU trazados a componente, tarea y prueba.
- [ ] Alcance, filtros, paginación estable y errores documentados.
- [ ] Solo lectura, sin Flota y sin exportación.
- [ ] DDL exacta de `v_financial_records` documentada; política de versionado global permanece abierta.
