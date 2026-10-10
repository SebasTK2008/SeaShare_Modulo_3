# Implementation Plan: UC08 - Brindar Información de Disputa de Garantía

**Date**: 2026-10-09  
**Spec**: [spec.md](spec.md)

## Summary

UC08 es el consumidor asíncrono y unidireccional que recibe desde Reservas el estado de una disputa de garantía. El mensaje solo contiene `reservation_id`, `dispute_id`, `status`, `status_changed_at` y `event_key`; nunca recibe motivos, orígenes, montos ni instrucciones de pasarela [SPEC RF-001, RF-003, RF-007, RNF-001].

`PENDING` registra el evento sin mover dinero. `REJECTED` consulta el depósito y cobro internos y solicita a UC09 el reembolso/liberación total; `COMPLETED` solicita a UC10 la liquidación total. Los eventos se procesan con idempotencia, exclusión mutua y control de concurrencia. UC08 no calcula la ventana de 24 horas ni ejecuta jobs [SPEC RF-004..RF-010].

Enfoque técnico: Spring Boot único con arquitectura hexagonal (`domain` / `application` / `infrastructure`), listener AMQP, PostgreSQL, `deposit_disposition`, `dispute_event_log`, `FailureRecorderPort`, y puertos de entrada de UC09/UC10. Los errores siguen `contracts/README.md` §3.5 y §4.4: `ack` para mensajes inválidos, desconocidos, duplicados o sin datos financieros; `nack`/backoff/DLQ para fallas transitorias.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001, RF-007 | `DisputeNotificationMessage`, `DisputeNotificationCommand`, listener | T006-T010 | T011, T015 |
| RF-002, RF-003, RF-010 | `DisputeStatus`, `DisputeInformationService` | T004, T008 | T012, T017 |
| RF-004 | Rama `PENDING` | T008 | **T013 `ce002_…`** |
| RF-005, RF-009A | `RequestRefundUseCase` vía `port.in` | T005, T009 | **T014 `ce003_…`** |
| RF-006 | `RequestSettlementUseCase` vía `port.in` | T005, T009 | **T014 `ce003_…`** |
| RF-008 | clave idempotente, lock y restricción única | T004, T008 | **T016 `ce005_…`** |
| RF-009 | listener AMQP, sin scheduler | T003, T010 | T011, T018 |
| RNF-001 | DTO AMQP + command/result | T006, T007 | T011 |
| RNF-002 | `BigDecimal`/`Money` de registros internos | T005, T009 | T014, T015 |
| RNF-003 | ack/nack, DLQ, `FailureRecorderPort` | T003, T010 | T016, T018 |
| CE-001 | estados canónicos | T004, T008 | **T012 `ce001_…`** |
| CE-002 | rama `PENDING` | T008 | **T013 `ce002_…`** |
| CE-003 | consecuencias finales | T009 | **T014 `ce003_…`** |
| CE-004 | DTO estricto y consultas internas | T005, T006 | **T015 `ce004_…`** |
| CE-005 | deduplicación/concurrencia | T008 | **T016 `ce005_…`** |
| CE-006 | rechazo de sub-resultados | T004, T008 | **T017 `ce006_…`** |
| HU1 | Fase 3 | T006-T010 | T013-T017 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.1; Web MVC, Validation, Data JPA, AMQP, Security OAuth2 Resource Server, Actuator, Flyway, MapStruct, Resilience4j, Micrometer/Prometheus, ArchUnit y ShedLock opcional. UC08 usa especialmente AMQP, JPA, Validation, PostgreSQL, RabbitMQ, Awaitility y Testcontainers. Dependencias compartidas: `UC11·T001`–`UC11·T009`; este plan no modifica `pom.xml`.  
**Storage**: PostgreSQL 16+, `NUMERIC(18,4)` y `timestamptz`. UC08 es dueño de `deposit_disposition` y `dispute_event_log`; lee `reservation_information` y `charge_record` mediante puertos de sus dueños [general-plan §4].  
**Messaging**: RabbitMQ 3.13+, exchange topic durable `seashare.reservations`, routing key `reservation.dispute.updated`, cola `finance.guarantee-dispute.v1`, mensajes persistentes y DLQ [CONV contrato UC08].  
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers PostgreSQL/RabbitMQ, Awaitility, ArchUnit y pruebas de contrato AMQP [CONV].  
**Target Platform**: contenedores Docker sobre Linux; desarrollo con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: el SPEC no define SLA numérico [NEEDS CLARIFICATION OQ-UC08-01]; se exige idempotencia, backoff y ausencia de bloqueo permanente.  
**Constraints**: `BigDecimal` a 4 decimales; importes solo desde registros internos; `PENDING` no mueve dinero; finales son tratamientos del 100 %; un depósito tiene un solo desenlace; sin respuesta al productor; sin sub-resultados parciales ni cron de 24 horas [SPEC RF-004..RF-010].  
**Scale/Scope**: un consumidor AMQP, dos tablas propietarias, dos puertos consumidos de UC09/UC10, 10 RF + 3 RNF + 6 CE + 1 HU [SPEC 08].

## Project Structure

### Documentation

```text
docs/features/008-brindar-informacion-de-disputa-de-garantia/
├── plan.md
└── spec.md
docs/technical-plan/contracts/events/UC08-disputa-garantia.md
```

### Source Code

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java
├── domain/
│   ├── model/{DepositDisposition,DisputeEvent,DisputeStatus}.java
│   ├── valueobject/{ReservationId,DisputeId,IdempotencyKey}.java
│   └── exception/DomainException.java
├── application/
│   ├── port/in/{HandleDisputeNotificationUseCase,OpenDepositTrackingUseCase}.java
│   ├── port/out/
│   │   ├── {DepositDispositionRepository,DisputeEventLogRepository}.java
│   │   ├── ReservationInformationRepository.java     # dueño UC03, solo referencia
│   │   ├── ChargeRecordQueryPort.java                # dueño UC05, solo referencia
│   │   ├── FailureRecorderPort.java                  # UC11·T007
│   │   └── OutboxPort.java                           # UC11·T009
│   ├── service/DisputeInformationService.java
│   └── dto/{DisputeNotificationCommand,DepositTrackingResult}.java
└── infrastructure/
    ├── adapter/in/messaging/dispute/
    │   ├── DisputeNotificationListener.java
    │   └── dto/DisputeNotificationMessage.java
    ├── adapter/out/persistence/dispute/
    │   ├── {DepositDispositionJpaEntity,DisputeEventLogJpaEntity}.java
    │   ├── DisputePersistenceAdapter.java
    │   └── DisputePersistenceMapper.java
    ├── adapter/out/messaging/OutboxMessagingAdapter.java
    └── config/{AmqpConfig,ProblemDetailsConfig,ObservabilityConfig}.java

src/main/resources/db/migration/
└── V1__create_dispute_tracking_tables.sql # solo documentado; consolidación asigna definitivo

src/test/java/com/seashare/seasharem3/
├── arch/ArchitectureTest.java
├── contract/UC08DisputeNotificationContractTest.java
├── domain/model/DisputeInformationTest.java
├── application/service/DisputeInformationServiceTest.java
├── infrastructure/adapter/in/messaging/DisputeNotificationListenerTest.java
└── infrastructure/adapter/out/persistence/DisputePersistenceAdapterTest.java
```

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4 y la referencia UC01/UC11: `domain` no depende de Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; adaptadores de entrada invocan `application.port.in`; adaptadores de salida implementan `application.port.out`; ningún listener accede directamente a repositorios; DTOs AMQP viven en infraestructura y aplicación trabaja con commands/results.

**Firmas referenciadas**: UC08 publica `OpenDepositTrackingUseCase` para UC07. Consume `RequestRefundUseCase` de UC09 y `RequestSettlementUseCase` de UC10 por sus puertos publicados; no redefine ni implementa esos casos. No expone REST ni responde a Reservas.

## Reglas de negocio

1. **Mensaje mínimo** [SPEC RF-001, RF-007]: aceptar solo los cinco campos del contrato; no mapear motivos, origen, montos ni instrucciones.
2. **Estados canónicos** [SPEC RF-002, RF-010]: solo `PENDING`, `REJECTED`, `COMPLETED`; desconocidos/parciales → fallo registrado, `ack`, cero operaciones.
3. **Idempotencia** [SPEC RF-008; contrato §3.2]: clave `(reservation_id, dispute_id, event_key)`; repetidos no llaman UC09/UC10.
4. **Pendiente** [SPEC RF-004]: registra y no reembolsa/liquida.
5. **Rechazado** [SPEC RF-003, RF-005, RF-009A]: recupera depósito/cobro internos y solicita 100 % a UC09; el estado del cobro determina liberación o reembolso.
6. **Completado** [SPEC RF-006]: recupera depósito interno y solicita 100 % a UC10.
7. **Sin montos asumidos** [SPEC RF-007]: ausencia de depósito/cobro confirmado → fallo controlado sin llamada financiera.
8. **Exclusión mutua** [SPEC RF-008; general-plan §3.2]: un depósito solo termina en reembolso o liquidación.
9. **Ventana de 24 horas** [SPEC RF-009, RF-009A]: Reservas envía `REJECTED`; UC08 no calcula vencimientos ni ejecuta jobs.
10. **Ack/nack** [CONV README §4.4]: inválidos/desconocidos/ausencia/duplicado → `ack` + fallo; BD/broker transitorio → `nack`, backoff y DLQ tras 3–5 reintentos.
11. **Unidireccional** [SPEC RF-009; contrato §4]: no se publica respuesta de negocio.
12. **Precisión** [SPEC RNF-002; README §3.1]: montos `BigDecimal`/`NUMERIC(18,4)`, sin redondeo prematuro.

## Contrato AMQP

Fuente: `events/UC08-disputa-garantia.md`; convenciones en `contracts/README.md` §3.5 y §4.4.

```text
Exchange: seashare.reservations (topic, durable)
Routing key: reservation.dispute.updated
Queue: finance.guarantee-dispute.v1
Content-Type: application/json; Message-Id obligatorio
Respuesta: ninguna; ack para errores no reintentables, nack/DLQ para fallas transitorias
```

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "dispute_id": "d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a",
  "status": "REJECTED",
  "status_changed_at": "2026-12-23T10:00:00Z",
  "event_key": "v1.0-4a5b6c"
}
```

## Estrategia de testing

Cobertura objetivo [CONV]: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Cobertura | Ubicación |
|---|---|---|
| Unitario dominio | estados, 100 %, exclusión mutua, clave | `domain/model/DisputeInformationTest` |
| Unitario aplicación | ramas, datos faltantes, duplicados, puertos | `application/service/DisputeInformationServiceTest` |
| Contrato AMQP | headers, routing key, payload mínimo, ack | `contract/UC08DisputeNotificationContractTest` |
| Integración | RabbitMQ ack/nack/DLQ, PostgreSQL y concurrencia | `integration/UC08DisputeProcessingIT` |
| Arquitectura | reglas §3.4 y ausencia de jobs | `arch/ArchitectureTest` |

- **`ce001_solo_estados_canónicos`**: estados válidos aceptados; desconocidos no mueven dinero.
- **`ce002_pendiente_no_mueve_dinero`**: múltiples pendientes producen cero solicitudes financieras.
- **`ce003_finales_generan_una_sola_consecuencia_total`**: rechazado llama una vez a UC09 y completado una vez a UC10, por 100 %.
- **`ce004_montos_solo_desde_registros_internos`**: ningún monto del mensaje llega a la operación financiera.
- **`ce005_concurrencia_idempotente`**: eventos concurrentes no duplican desenlace.
- **`ce006_sin_subresultados_parciales`**: liberación/retención parcial no se procesa.

## Discrepancias y preguntas abiertas

| ID | Descripción | Propuesta por defecto | Estado |
|---|---|---|---|
| **OQ-UC08-01** | El SPEC no define SLA ni número exacto de reintentos | heredar backoff general de 3–5 reintentos y medir lag | Abierta |
| **D-UC08-01** | UC09/UC10 deben publicar firmas | consumir sus `port.in`; copiar firmas de los planes dueños | Decidida |
| **D-UC08-02** | Orden definitivo de migraciones | documentar **V1**; consolidación asigna números finales | Decidida |
| **D-UC08-03** | Tratamiento de errores en cola | ack + fallo para lógica; nack/DLQ para transitorios | Decidida por contrato |
| **OQ-UC08-02** | Seguridad transversal para AMQP | heredar configuración compartida; no crear `SecurityConfig` propio | Diferida |

## Implementation Phases

### Phase 1: Setup — Compartido

- [ ] **T001** Referenciar `UC11·T001`–`UC11·T009` para AMQP, persistencia, errores y observabilidad.
- [ ] **T002** Configurar exchange, binding, cola quorum y DLQ según contrato.
- [ ] **T003** Definir ack/nack, backoff y correlación; sin cron/scheduler propio.

### Phase 2: Foundational

- [ ] **T004** Crear `DisputeStatus`, value objects, `DisputeEvent` y reglas canónicas.
- [ ] **T005** Consumir por nombre puertos de UC09/UC10, repositorios de reserva/cobro, `FailureRecorderPort` y `OutboxPort`; no implementarlos.
- [ ] **T006** Crear DTO AMQP estricto y mapearlo a `DisputeNotificationCommand`.
- [ ] **T007** Crear `HandleDisputeNotificationUseCase`, `OpenDepositTrackingUseCase` y result interno.

### Phase 3: US1 — Recibir y procesar disputa (P1)

- [ ] **T008** Implementar servicio: registrar, deduplicar, resolver pendientes y persistir disposición.
- [ ] **T009** Consultar depósito/cobro internos y llamar exactamente una vez a UC09/UC10 con el total interno.
- [ ] **T010** Implementar listener con ack para errores no reintentables y nack/DLQ para transitorios.
- [ ] **T011** Tests de contrato AMQP, listener y ausencia de respuesta.

### Phase 4: Aceptación y robustez

- [ ] **T012** `ce001_solo_estados_canónicos`.
- [ ] **T013** `ce002_pendiente_no_mueve_dinero`.
- [ ] **T014** `ce003_finales_generan_una_sola_consecuencia_total`.
- [ ] **T015** `ce004_montos_solo_desde_registros_internos`.
- [ ] **T016** `ce005_concurrencia_idempotente`.
- [ ] **T017** `ce006_sin_subresultados_parciales`.
- [ ] **T018** ArchUnit e integración PostgreSQL/RabbitMQ; verificar que UC08 no introduce jobs ni endpoints.

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012..T018`.

UC08 consume las firmas publicadas por UC09/UC10 y publica `OpenDepositTrackingUseCase` para UC07. Cualquier cambio a esos planes queda fuera y solo se documenta. Migración: **V1**, únicamente como referencia documental; la numeración final se consolida después.

## Notes

No modificar `pom.xml`, `general-plan.md`, SPEC, contratos ni planes de UC07/UC09/UC10/UC11. No implementar montos desde Reservas, operaciones parciales, cron de 24 horas ni respuesta de negocio al productor.

## Checklist de auto-revisión

- [ ] Alineado con `general-plan.md`, contrato UC08 y `contracts/README.md`.
- [ ] Cada RF/RNF/CE/HU tiene componente, tarea y prueba.
- [ ] Ack/nack, DLQ, idempotencia y exclusión mutua documentados.
- [ ] Sin REST, jobs, montos externos ni migración aplicada.
