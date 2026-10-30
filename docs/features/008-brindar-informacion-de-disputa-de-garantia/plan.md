# Implementation Plan: UC08 - Brindar Información de Disputa de Garantía

**Date**: 2026-10-09 (plan original)  
**Updated**: 2026-10-30 (ver [Actualizaciones del plan](#actualizaciones-del-plan))  
**Spec**: [spec.md](spec.md)

> **Actualización del plan — 2026-10-30.** Este documento parte del plan original del **2026-10-09** (commit `e2b3970`), con la revisión de firmas del **2026-10-29** (commit `b92b0da`) ya incorporada y marcada como `[ACT-2026-10-29]`. Las secciones modificadas en esta revisión están marcadas con `[ACT-xx]` y se detallan en [Actualizaciones del plan](#actualizaciones-del-plan). Salvo las líneas de versión y la sección «Actualizaciones del plan», el contenido sin marcador pertenece al plan original.

## Summary

UC08 es el consumidor asíncrono y unidireccional que recibe desde Reservas el estado de una disputa de garantía. El mensaje solo contiene `reservation_id`, `dispute_id`, `status`, `status_changed_at` y `event_key`; nunca recibe motivos, orígenes, montos ni instrucciones de pasarela [SPEC RF-001, RF-003, RF-007, RNF-001].

`PENDING` registra el evento sin mover dinero. `REJECTED` invoca a UC09 mediante `RequestRefundUseCase` con el trigger `DISPUTE_REJECTED`; `COMPLETED` invoca a UC10 mediante `RequestSettlementUseCase` con el trigger `DISPUTE_COMPLETED` y `scope = DEPOSIT`. UC09/UC10 recuperan internamente el depósito y el cobro, calculan el 100 % y aplican el desenlace con un único *compare-and-set* en `deposit_disposition`, del que UC08 es dueño (general-plan D-34); UC08 no consulta montos ni los envía. Los eventos se procesan con idempotencia, exclusión mutua y control de concurrencia. UC08 no calcula la ventana de 24 horas ni ejecuta jobs [SPEC RF-004..RF-010]. **[ACT-06]**

Enfoque técnico: Spring Boot único con arquitectura hexagonal (`domain` / `application` / `infrastructure`), listener AMQP, PostgreSQL, `deposit_disposition`, `dispute_event_log`, `FailureRecorderPort`, y puertos de entrada de UC09/UC10. Los errores siguen `contracts/README.md` §3.5 y §4.4: `ack` para mensajes inválidos, desconocidos, duplicados o sin datos financieros; `nack`/backoff/DLQ para fallas transitorias.

## Actualizaciones del plan

Registro de los cambios introducidos el **2026-10-30** respecto a la versión del **2026-10-29** (commit `b92b0da`), que conserva el plan original del **2026-10-09** (commit `e2b3970`). No se presenta el contenido actualizado como si hubiera formado parte del plan original: cada sección afectada lleva su marcador `[ACT-xx]` y aquí se documenta el valor anterior y el vigente. Los marcadores `[ACT-2026-10-29]` corresponden a la revisión previa ya registrada en el historial y se conservan como referencia.

La versión anterior puede recuperarse con `git show b92b0da:docs/features/008-brindar-informacion-de-disputa-de-garantia/plan.md`; las diferencias de esta actualización se consultan con `git diff b92b0da -- docs/features/008-brindar-informacion-de-disputa-de-garantia/plan.md`. El plan original está en `git show e2b3970:docs/features/008-brindar-informacion-de-disputa-de-garantia/plan.md`.

| ID | Sección(es) | Original (2026-10-29) → vigente (2026-10-30) | Motivo |
|---|---|---|---|
| **ACT-01** | Cabecera y esta sección | Sin nota de versión → `Date`/`Updated`, blockquote, sección y tabla `[ACT-xx]` | Versionar la revisión y dejar rastro del plan original y de la revisión del 2026-10-29. |
| **ACT-02** | `Technical Context`, `Scale/Scope` | Spring Boot 4.1.1; sin JaCoCo; 10 RF → Spring Boot 4.1.2; JaCoCo 0.8.15 sin puerta de UC08; 10 RF + RF-009A | Alinear con `pom.xml` y el SPEC. |
| **ACT-03** | `Technical Context` (nuevo párrafo «Estado actual de UC08») | Sin estado → UC08 mínimo implementado y consumido por UC09/UC10; ingesta de disputas pendiente | Registrar el avance real y evitar declarar implementado lo que no existe. |
| **ACT-04** | `Project Structure` | Rutas y clases previstas (`persistence/dispute`, `DisputeInformationService`, `DisputeNotificationListener`, `AmqpConfig`, `ObservabilityConfig`, `DisputeStatus` en `domain/model`) → árbol real con `persistence/deposit`, `config/amqp`, `DisputeStatus` en `domain/valueobject` y clases pendientes marcadas | Alinear el plan con el código. |
| **ACT-05** | `Firmas referenciadas`, `Dependencies & Execution Order` | Solo UC07; sin comandos de UC09/UC10 → UC07/UC09/UC10; `RefundRequestCommand`/`SettlementCommand`, triggers, scope y `operationKey`; `completedAt` con fallback al `Clock` | Completar el contrato que UC08 debe implementar. |
| **ACT-06** | `Summary`, `Storage`, reglas 5-8 y 12, trazabilidad y T009 | UC08 consulta depósito/cobro internos, pasa «el total interno» y usa `/refunds` → UC08 delega en UC09/UC10; ellos consultan, calculan y aplican el CAS; UC08 solo aporta trigger, scope, `disputeId` y `operationKey` | Reflejar el reparto cerrado en UC09/UC10 y `general-plan` D-34. |
| **ACT-07** | `Implementation Phases` y `D-UC08-01..05` | Todas las tareas en `[ ]`; D-UC08-02 «Decidida», D-UC08-03 «Aplicada» → T001-T003, T007 y T008a hechas; T004-T006 parciales; T008-T018 pendientes; estados corregidos | Registrar el avance real sin declarar cerrada la implementación. |
| **ACT-08** | `Estrategia de testing`, `Notes` y `Checklist de auto-revisión` | Nombres de pruebas que no existen y cobertura ≥90/80 sin puerta → pruebas existentes y pendientes; ausencia de puerta JaCoCo de UC08; checklist documental revisado | Cerrar la revisión documental. |
| **ACT-09** | `Estado actual`, `Firmas referenciadas`, regla 8, trazabilidad, `D-UC08-06`, T008/T009/T016 y `ce005` | `dispute_status` pendiente de decidir → no se pobla; `dispute_event_log` es la fuente única; `deposit_disposition` conserva `dispute_id`; guarda de disposición y lock antes de delegar | Cerrar la decisión de T008/T009 y dejar definida la idempotencia del desenlace. |
| **ACT-10** | `Estado actual`, `Project Structure`, `Estrategia de testing`, `D-UC08-01/03/06`, `Implementation Phases`, `Notes` y `Checklist de auto-revisión` | Ingesta pendiente y pruebas planificadas → ingesta implementada y probada; T004-T018 cerradas; nombres reales de pruebas; suite completa con 1395 tests, 0 fallos y 0 errores | Cerrar el plan tras la implementación de UC08. |

### Trazabilidad RF/RNF/CE/HU → componente / tarea **[ACT-06] [ACT-09]**

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001, RF-007 | `DisputeNotificationMessage`, `DisputeNotificationCommand`, listener | T006-T010 | T011, T015 |
| RF-002, RF-003, RF-010 | `DisputeStatus`, `DisputeInformationService` | T004, T008 | T012, T017 |
| RF-004 | Rama `PENDING` | T008 | **T013 `ce002_…`** |
| RF-005, RF-009A | `RequestRefundUseCase` vía `port.in`; UC09 consulta y aplica el CAS | T005, T009 | **T014 `ce003_…`** |
| RF-006 | `RequestSettlementUseCase` vía `port.in`; UC10 consulta y aplica el CAS | T005, T009 | **T014 `ce003_…`** |
| RF-008 | clave idempotente, `dispute_event_log`, lock y guarda de `deposit_disposition` | T004, T008, T009 | **T016 `ce005_…`** |
| RF-009 | listener AMQP, sin scheduler | T003, T010 | T011, T018 |
| RNF-001 | DTO AMQP + command/result | T006, T007 | T011 |
| RNF-002 | `BigDecimal`/`Money` en UC09/UC10; UC08 no recibe ni calcula montos | T005, T009 | T014, T015 |
| RNF-003 | ack/nack, DLQ, `FailureRecorderPort` | T003, T010 | T016, T018 |
| CE-001 | estados canónicos | T004, T008 | **T012 `ce001_…`** |
| CE-002 | rama `PENDING` | T008 | **T013 `ce002_…`** |
| CE-003 | consecuencias finales | T009 | **T014 `ce003_…`** |
| CE-004 | DTO estricto y ausencia de montos externos | T005, T006 | **T015 `ce004_…`** |
| CE-005 | deduplicación, `event_key` distinto y concurrencia | T008, T009 | **T016 `ce005_…`** |
| CE-006 | rechazo de sub-resultados | T004, T008 | **T017 `ce006_…`** |
| HU1 | Fase 3 | T006-T010 | T013-T017 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.2 (parent del `pom.xml`); Web MVC, Validation, Data JPA, AMQP, Actuator, Flyway, MapStruct, Resilience4j, Micrometer/Prometheus, ArchUnit y ShedLock opcional. UC08 usa especialmente AMQP, JPA, Validation, PostgreSQL, RabbitMQ, Awaitility y Testcontainers. Las dependencias pertenecen al núcleo compartido; este plan no modifica `pom.xml`. (Sin *starter* de seguridad: fuera de alcance, general-plan §7.1.) **[ACT-02]**  
**Storage**: PostgreSQL 16+, `NUMERIC(18,4)` y `timestamptz`. UC08 es dueño de `deposit_disposition` y `dispute_event_log`; no lee `reservation_information` ni `charge_record`: UC09/UC10 los consultan por sus propios puertos y aplican el desenlace del depósito [general-plan §4, D-34]. `deposit_disposition.dispute_status` queda reservada; el estado canónico se lee de `dispute_event_log`. **[ACT-06] [ACT-09]**  
**Messaging**: RabbitMQ 3.13+, exchange topic durable `seashare.reservations`, routing key `reservation.dispute.updated`, cola `finance.guarantee-dispute.v1`, mensajes persistentes y DLQ `finance.guarantee-dispute.v1.dlq` [contrato UC08].  
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers PostgreSQL/RabbitMQ, Awaitility, ArchUnit, pruebas de contrato AMQP y JaCoCo 0.8.15; la puerta `check` del `pom.xml` todavía no incluye clases de UC08 [CONV]. **[ACT-02]**  
**Target Platform**: contenedores Docker sobre Linux; desarrollo con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: Sin meta propia: el SPEC 08 no define SLA numérico; rigen idempotencia, reintentos con backoff (5 intentos, base 1 s) y ausencia de bloqueo permanente [CONV].  
**Constraints**: `BigDecimal` a 4 decimales; importes solo desde registros internos; `PENDING` no mueve dinero; finales son tratamientos del 100 %; un depósito tiene un solo desenlace; sin respuesta al productor; sin sub-resultados parciales ni cron de 24 horas [SPEC RF-004..RF-010].  
**Scale/Scope**: un consumidor AMQP, dos tablas propietarias, dos puertos consumidos de UC09/UC10, 10 RF + RF-009A + 3 RNF + 6 CE + 1 HU [SPEC 08]. **[ACT-02]**

**Estado actual de UC08**: UC08 está implementado y verificado. El desenlace del depósito está en uso: `OpenDepositTrackingService` (bajo `ReservationLockPort`, creación idempotente en `HELD` y *compare-and-set* a `REFUND_REQUESTED`/`SETTLEMENT_REQUESTED`), `DepositDisposition`, su persistencia JPA en `infrastructure/adapter/out/persistence/deposit/` y el wiring `DepositConfiguration`. UC09 invoca el puerto para cualquier reembolso y UC10 solo para `DISPUTE_COMPLETED`; UC07 aún no lo invoca. Las tablas `deposit_disposition` y `dispute_event_log`, la cola `finance.guarantee-dispute.v1` con su DLQ y la política compartida de ack/nack/reintentos existen. La ingesta de disputas está implementada: `DisputeEvent` con reglas canónicas; `DisputeInformationService` con inserción idempotente en `dispute_event_log`, `processed_at`, guarda de `deposit_disposition` bajo `ReservationLockPort` y delegación en UC09/UC10; `DisputeNotificationListener` con DTO/mapper; persistencia JPA de `dispute_event_log`; y pruebas de dominio, servicio, contrato, integración, aceptación y arquitectura. `.\mvnw.cmd verify` ejecutó 1395 tests con 0 fallos y 0 errores; la puerta JaCoCo pasó. La columna `deposit_disposition.dispute_status` permanece nullable y reservada: no se pobla desde el evento; el estado canónico se lee de `dispute_event_log` (D-UC08-06). **[ACT-03] [ACT-09] [ACT-10]**

## Project Structure

### Documentation

```text
docs/features/008-brindar-informacion-de-disputa-de-garantia/
├── plan.md
└── spec.md
docs/technical-plan/contracts/events/UC08-disputa-garantia.md
```

### Source Code (repository root) **[ACT-04] [ACT-10]**

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java
├── domain/
│   ├── model/
│   │   ├── DepositDisposition.java                  # existe
│   │   └── DisputeEvent.java                        # existe
│   ├── valueobject/
│   │   ├── DisputeId.java                           # existe
│   │   ├── DisputeStatus.java                       # existe (valueobject, no model)
│   │   ├── DepositDispositionStatus.java            # existe
│   │   ├── IdempotencyKey.java                      # existe (núcleo compartido)
│   │   └── ReservationId.java                       # existe (núcleo compartido)
│   └── exception/
│       ├── DomainException.java                     # existe (núcleo compartido)
│       └── DisputeNotificationValidationException.java # existe
├── application/
│   ├── port/in/
│   │   ├── HandleDisputeNotificationUseCase.java    # existe; implementado
│   │   └── OpenDepositTrackingUseCase.java          # existe; implementado
│   ├── port/out/
│   │   ├── DepositDispositionRepository.java        # existe; implementado
│   │   ├── DisputeEventLogRepository.java           # existe; implementado
│   │   ├── ReservationInformationRepository.java    # dueño UC03, solo referencia
│   │   ├── ChargeRecordQueryPort.java               # dueño UC05, solo referencia
│   │   ├── FailureRecorderPort.java                 # puerto compartido
│   │   ├── ReservationLockPort.java                 # lock único por reserva
│   │   └── OutboxPort.java                          # puerto compartido
│   ├── service/
│   │   ├── OpenDepositTrackingService.java          # existe; UC08 mínimo
│   │   └── DisputeInformationService.java           # existe; ingesta de disputas
│   └── dto/
│       ├── DisputeNotificationCommand.java          # existe
│       ├── DepositTrackingResult.java               # existe
│       └── OpenDepositTrackingCommand.java          # existe
└── infrastructure/
    ├── adapter/in/messaging/
    │   ├── DisputeNotificationListener.java         # existe
    │   └── dto/
    │       ├── DisputeNotificationMessage.java      # existe
    │       └── DisputeNotificationMessageMapper.java # existe
    ├── adapter/out/persistence/deposit/
    │   ├── DepositDispositionJpaEntity.java          # existe
    │   ├── DepositDispositionJpaRepository.java      # existe
    │   └── DepositDispositionPersistenceAdapter.java # existe
    ├── adapter/out/persistence/dispute/
    │   ├── DisputeEventLogJpaEntity.java             # existe
    │   ├── DisputeEventLogJpaRepository.java         # existe
    │   └── DisputeEventLogPersistenceAdapter.java    # existe
    └── config/
        ├── DepositConfiguration.java                 # existe
        └── amqp/{AmqpNames,RabbitTopologyConfig,AmqpListenerFailurePolicy,OperationalFailureMessageRecoverer}.java # núcleo compartido

src/main/resources/db/migration/
└── V1__initial_schema.sql # migración inicial única; contiene deposit_disposition y dispute_event_log (general-plan §4.2, D-31)

src/test/java/com/seashare/seasharem3/
├── acceptance/Uc08AcceptanceTest.java                # existe (ce001-ce006)
├── arch/ArchitectureTest.java                        # existe (reglas §3.4)
├── arch/Uc08ArchitectureTest.java                    # existe (reglas UC08)
├── application/service/OpenDepositTrackingServiceTest.java  # existe (UC08 mínimo)
├── application/service/DisputeInformationServiceTest.java   # existe (T008/T009)
├── application/service/RequestRefundServiceTest.java        # existe (UC09 → UC08)
├── application/service/RequestSettlementServiceTest.java    # existe (UC10 → UC08)
├── domain/model/DisputeEventTest.java                # existe (T004)
├── infrastructure/adapter/in/messaging/DisputeNotificationListenerContractTest.java # existe (T011)
├── infrastructure/adapter/in/messaging/DisputeNotificationListenerIntegrationTest.java # existe (T018)
├── infrastructure/adapter/out/persistence/dispute/DisputeEventLogPersistenceAdapterIntegrationTest.java # existe (T018)
├── infrastructure/config/amqp/RabbitTopologyConfigTest.java   # existe (cola/DLQ/binding)
├── infrastructure/config/amqp/RabbitTopologyIntegrationTest.java # existe
├── integration/RefundSettlementPersistenceIntegrationTest.java # existe (CAS de deposit_disposition)
├── migration/FlywaySchemaTest.java                   # existe (tablas)
├── support/InMemoryDepositDispositionRepository.java # existe (fake)
└── support/InMemoryDisputeEventLogRepository.java    # existe (fake)
```

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4: `domain` no depende de Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; adaptadores de entrada invocan `application.port.in`; adaptadores de salida implementan `application.port.out`; ningún listener accede directamente a repositorios; DTOs AMQP viven en infraestructura y aplicación trabaja con commands/results.

**Firmas referenciadas [ACT-05] [ACT-09]**: UC08 publica `OpenDepositTrackingUseCase.open(OpenDepositTrackingCommand)` para UC07, UC09 y UC10, y es dueño de `deposit_disposition` (desenlace con un único *compare-and-set*; general-plan D-34). [CONV núcleo, congelado] el comando es `OpenDepositTrackingCommand(ReservationId reservationId, String operationKey, DepositDispositionStatus target, UUID disputeId, DisputeStatus disputeStatus, Instant completedAt)`: `target` expresa el desenlace solicitado (`HELD` al completar la reserva, `REFUND_REQUESTED` para UC09, `SETTLEMENT_REQUESTED` para UC10); `disputeId` es nullable; `disputeStatus` se conserva nullable por compatibilidad y **no se pobla** (el estado canónico vive en `dispute_event_log`, D-UC08-06); si `completedAt` es `null` al crear la fila, el servicio usa el `Clock` inyectado. El retorno es `DepositTrackingResult(ReservationId reservationId, DepositDispositionStatus status, boolean created)`: `created` distingue la apertura nueva del desenlace ya existente para que el llamador no repita la operación.

UC08 consume `RequestRefundUseCase` de UC09 y `RequestSettlementUseCase` de UC10 por sus puertos publicados; no redefine ni implementa esos casos. Mapeo de la notificación:

| Estado de la disputa | Puerto invocado | Comando | `operationKey` |
|---|---|---|---|
| `PENDING` | ninguno | — | — |
| `REJECTED` | `RequestRefundUseCase.request` | `RefundRequestCommand(UUID reservationId, RefundTrigger.DISPUTE_REJECTED, UUID disputeId, String operationKey)` | `event_key` |
| `COMPLETED` | `RequestSettlementUseCase.request` | `SettlementCommand(ReservationId reservationId, SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, UUID disputeId, String operationKey)` | `event_key` |

UC09 y UC10 recuperan el depósito/cobro internos, calculan el 100 % y aplican el CAS mediante `OpenDepositTrackingUseCase`; UC08 no consulta ni envía montos. UC09/UC10 invocan el puerto con `disputeStatus = null`; `dispute_event_log` es la fuente única del estado de la disputa y `deposit_disposition.dispute_status` queda reservada, sin poblar (D-UC08-06). No expone REST ni responde a Reservas. **[ACT-05] [ACT-09]**

## Reglas de negocio

1. **Mensaje mínimo** [SPEC RF-001, RF-007]: aceptar solo los cinco campos del contrato; no mapear motivos, origen, montos ni instrucciones.
2. **Estados canónicos** [SPEC RF-002, RF-010]: solo `PENDING`, `REJECTED`, `COMPLETED`; desconocidos/parciales → fallo registrado, `ack`, cero operaciones.
3. **Idempotencia** [SPEC RF-008; contrato §3.2]: clave `(reservation_id, dispute_id, event_key)`; repetidos no llaman UC09/UC10.
4. **Pendiente** [SPEC RF-004]: registra y no reembolsa/liquida.
5. **Rechazado** [SPEC RF-003, RF-005, RF-009A]: invoca `RequestRefundUseCase` con `RefundTrigger.DISPUTE_REJECTED`, el `disputeId` y `operationKey = event_key`; UC09 recupera el depósito y el cobro internos y solicita el reembolso total (100 %) del depósito, sin motivo ni monto en el mensaje (general-plan D-32, D-34). **[ACT-06]**
6. **Completado** [SPEC RF-006]: invoca `RequestSettlementUseCase` con `SettlementTrigger.DISPUTE_COMPLETED`, `SettlementScope.DEPOSIT`, el `disputeId` y `operationKey = event_key`; UC10 recupera el depósito interno y solicita la liquidación total (100 %) (general-plan D-34). **[ACT-06]**
7. **Sin montos asumidos** [SPEC RF-007]: UC08 no recibe ni envía montos; si UC09/UC10 no encuentran depósito o cobro confirmado, registran el fallo controlado y no solicitan la operación financiera. **[ACT-06]**
8. **Exclusión mutua** [SPEC RF-008; general-plan §3.2]: un depósito solo termina en reembolso o liquidación; UC09/UC10 aplican el desenlace con el único *compare-and-set* de `OpenDepositTrackingUseCase` (D-34). UC08 comprueba su propia `deposit_disposition` antes de delegar: si el desenlace ya está aplicado, confirma sin repetir la llamada; si es el opuesto, registra la inconsistencia y confirma. **[ACT-06] [ACT-09]**
9. **Ventana de 24 horas** [SPEC RF-009, RF-009A]: Reservas envía `REJECTED`; UC08 no calcula vencimientos ni ejecuta jobs.
10. **Ack/nack** [CONV README §4.4]: inválidos/desconocidos/ausencia/duplicado → `ack` + fallo; BD/broker transitorio → `nack`, backoff exponencial (5 intentos, base 1 s) y, al agotarlos, DLQ + registro en `operational_failure`.
11. **Unidireccional** [SPEC RF-009; contrato §4]: no se publica respuesta de negocio.
12. **Precisión** [SPEC RNF-002; README §3.1]: UC08 no recibe ni calcula montos; la precisión `BigDecimal`/`NUMERIC(18,4)` y la ausencia de redondeo prematuro las aplican UC09/UC10 sobre los registros internos. **[ACT-06]**

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

## Estrategia de testing **[ACT-08] [ACT-10]**

Cobertura objetivo [CONV]: dominio ≥90 %, aplicación ≥80 %. La puerta JaCoCo del `pom.xml` (0.8.15) todavía no incluye clases de UC08; hasta que se añadan, la cobertura de UC08 no se exige automáticamente. La suite completa ejecutó 1395 tests con 0 fallos y 0 errores; la puerta JaCoCo pasó.

| Nivel | Cobertura | Ubicación | Estado |
|---|---|---|---|
| Unitario aplicación (UC08 mínimo) | apertura, idempotencia, transición y conflicto de desenlace | `application/service/OpenDepositTrackingServiceTest` | Existe |
| Integración persistencia (depósito) | CAS real de `deposit_disposition` | `integration/RefundSettlementPersistenceIntegrationTest` | Existe |
| Integración UC09/UC10 | UC09 no reembolsa si el depósito ya fue liquidado; UC10 marca `SETTLEMENT_REQUESTED` en `DISPUTE_COMPLETED` | `application/service/RequestRefundServiceTest`, `RequestSettlementServiceTest` | Existe |
| Topología AMQP | cola, DLQ y binding de disputa | `infrastructure/config/amqp/RabbitTopologyConfigTest`, `RabbitTopologyIntegrationTest` | Existe |
| Migración | existencia de `deposit_disposition` y `dispute_event_log` | `migration/FlywaySchemaTest` | Existe |
| Unitario dominio | estados, validaciones, `samePayload` y `processedAt` | `domain/model/DisputeEventTest` | Existe |
| Unitario aplicación | PENDING, finales, guarda, duplicados, conflictos y transitorios | `application/service/DisputeInformationServiceTest` | Existe |
| Contrato y listener | headers, payload mínimo, estados, ack/nack y errores permanentes/transitorios | `infrastructure/adapter/in/messaging/DisputeNotificationListenerContractTest` | Existe |
| Integración AMQP/PostgreSQL | cola, DLQ, rollback, reserva no registrada, deduplicación y concurrencia | `infrastructure/adapter/in/messaging/DisputeNotificationListenerIntegrationTest`, `infrastructure/adapter/out/persistence/dispute/DisputeEventLogPersistenceAdapterIntegrationTest` | Existe |
| Aceptación | `ce001`…`ce006` | `acceptance/Uc08AcceptanceTest` | Existe |
| Arquitectura | reglas §3.4, inmutabilidad y ausencia de jobs/endpoints | `arch/Uc08ArchitectureTest` | Existe |

- **`ce001_solo_estados_canónicos`**: estados válidos aceptados; desconocidos no mueven dinero.
- **`ce002_pendiente_no_mueve_dinero`**: múltiples pendientes producen cero solicitudes financieras.
- **`ce003_finales_generan_una_sola_consecuencia_total`**: rechazado llama una vez a UC09 y completado una vez a UC10, por 100 %.
- **`ce004_montos_solo_desde_registros_internos`**: ningún monto del mensaje llega a la operación financiera.
- **`ce005_concurrencia_idempotente`**: el mismo `event_key` no repite (y un payload distinto para la misma clave registra conflicto); un `event_key` distinto para el mismo desenlace no duplica reembolso/liquidación; finales opuestos no se aplican dos veces; un `PENDING` tardío no revierte el desenlace. **[ACT-09]**
- **`ce006_sin_subresultados_parciales`**: reembolso o liquidación parcial no se procesa.

Las pruebas de aceptación están implementadas en `Uc08AcceptanceTest` con los nombres `ce001_…`…`ce006_…` y se ejecutan en la suite completa. **[ACT-08] [ACT-10]**

## Decisiones de diseño (D-UC08-xx) **[ACT-07] [ACT-09] [ACT-10]**

Registro de las decisiones de diseño de este plan; los `D-xx` sin prefijo `UC08` son decisiones del plan general (§9).

| ID | Descripción | Decisión | Estado |
|---|---|---|---|
| **D-UC08-01** | UC08 es dueño de `deposit_disposition` y `dispute_event_log`; UC09/UC10 publican sus firmas | El esquema de ambas tablas se crea en la migración inicial única (general-plan §4.2, D-31); UC08 consume los `port.in` de UC09/UC10 y no redefine sus firmas | Aplicada |
| **D-UC08-02** | Migraciones de UC08 | Migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31); UC08 no define ni ordena migraciones propias | Aplicada |
| **D-UC08-03** | Tratamiento de errores en cola | `ack` + fallo para lógica; `nack`/DLQ para transitorios | Aplicada |
| **D-UC08-04** | Carrera entre creación de seguimiento y actualización de depósito | Reintento acotado o creación idempotente de la fila bajo `ReservationLockPort` (general-plan D-15) | Aplicada |
| **D-UC08-05** | Seguridad transversal para AMQP | Seguridad fuera de alcance: sin autenticación ni autorización para AMQP y sin `SecurityConfig` propio o compartido (general-plan §7.1; D-21) | Aplicada |
| **D-UC08-06** | ¿Se puebla `deposit_disposition.dispute_status` desde el evento? | No. `dispute_event_log` es la fuente única del estado; `deposit_disposition` conserva `dispute_id` como vínculo causal y `dispute_status` queda nullable/reservada (ningún llamador la envía). Se evita duplicar estado, regresiones por eventos fuera de orden y una segunda escritura sobre la disposición | Aplicada |

## Implementation Phases **[ACT-07] [ACT-09] [ACT-10]**

> **Actualización 2026-10-30 [ACT-07]**: se marcan como hechas T001, T002, T003, T007 y T008a; T004, T005 y T006 quedan parciales; T008–T018 siguen pendientes. T009 se reescribe para reflejar que UC09/UC10 consultan los registros internos y aplican el CAS.

> **Actualización 2026-10-30 [ACT-09]**: T008/T009 incorporan `dispute_event_log` como fuente única, `processed_at`, la guarda de `deposit_disposition` y el lock por reserva; T016/`ce005` cubren la idempotencia del desenlace.

> **Actualización 2026-10-30 [ACT-10]**: implementación cerrada; T004–T018 completadas y verificadas con `.\mvnw.cmd verify` (1395 tests, 0 fallos, 0 errores; JaCoCo correcto).

### Phase 1: Setup — Compartido

- [x] **T001** Consumir la configuración AMQP común, Problem Details, `FailureRecorderPort`, `ReservationLockPort`, `OutboxPort` y observabilidad compartida; los fallos se registran con `REQUIRES_NEW`, `reservation_id` nullable, `payload_ref` referencial y procedimiento de reproceso. Núcleo compartido disponible; `OpenDepositTrackingService` consume `FailureRecorderPort` y `ReservationLockPort`; `OutboxPort` no aplica porque UC08 no publica. **[ACT-07]**
- [x] **T002** Configurar exchange, binding, cola quorum y DLQ `<cola>.dlq` según contrato. Declarado en `RabbitTopologyConfig` y verificado por `RabbitTopologyConfigTest`/`RabbitTopologyIntegrationTest`. **[ACT-07]**
- [x] **T003** Definir ack/nack, backoff y correlación; sin cron/scheduler propio. Definido en `AmqpListenerFailurePolicy` y `application.properties`; `OperationalFailureMessageRecoverer` mapea la cola a UC08 y `DisputeNotificationListener` aplica la política. **[ACT-07] [ACT-10]**

### Phase 2: Foundational

- [x] **T004** Crear `DisputeStatus`, value objects, `DisputeEvent` y reglas canónicas. Implementado: `DisputeStatus`, `DisputeId`, `IdempotencyKey`, `DepositDisposition` y `DisputeEvent` con validaciones y `markProcessed`. **[ACT-07] [ACT-10]**
- [x] **T005** Consumir por nombre los puertos de UC09/UC10, `FailureRecorderPort`, `ReservationLockPort`, `DepositDispositionRepository` y `DisputeEventLogRepository`; no implementarlos. Implementado: `DisputeInformationService` consume `RequestRefundUseCase`, `RequestSettlementUseCase`, `DepositDispositionRepository`, `DisputeEventLogRepository`, `FailureRecorderPort` y `ReservationLockPort`; UC09/UC10 consultan reserva/cobro. **[ACT-07] [ACT-10]**
- [x] **T006** Crear DTO AMQP estricto y mapearlo a `DisputeNotificationCommand`. Implementado: `DisputeNotificationMessage`, `DisputeNotificationMessageMapper` y `DisputeNotificationCommand` con `DisputeStatus`. **[ACT-07] [ACT-10]**
- [x] **T007** Crear `HandleDisputeNotificationUseCase`, `OpenDepositTrackingUseCase` y el result interno `DepositTrackingResult(ReservationId reservationId, DepositDispositionStatus status, boolean created)` [CONV núcleo, congelado]. `OpenDepositTrackingCommand` incluye `target`, `disputeId`, `disputeStatus` y `completedAt`; UC07 lo usa con `HELD`, UC09 con `REFUND_REQUESTED` y UC10 con `SETTLEMENT_REQUESTED`. **[ACT-2026-10-29]** Implementado `OpenDepositTrackingUseCase`/`DepositTrackingResult`; `HandleDisputeNotificationUseCase` está implementado por `DisputeInformationService`. **[ACT-07] [ACT-10]**

### Phase 3: US1 — Recibir y procesar disputa (P1)

- [x] **T008** Implementar `DisputeInformationService`: validar el mensaje, insertar el evento en `dispute_event_log` con `processed_at = null`, deduplicar por `(reservation_id, dispute_id, event_key)`, reanudar eventos incompletos y marcar `processed_at` al terminar; `PENDING` no toca `deposit_disposition`. Todo en una transacción; un `event_key` repetido con `processed_at` no nulo se confirma sin repetir la operación, y si el repetido trae `status` o `status_changed_at` distintos se registra el conflicto y se confirma [D-UC08-06]. Implementado y cubierto por `DisputeInformationServiceTest`. **[ACT-09] [ACT-10]**
- [x] **T008a** Persistir `deposit_disposition` con CAS explícito: estados finales, rechazo del reembolso cuando corresponda y transición única bajo `ReservationLockPort`. Implementado en `OpenDepositTrackingService` y `DepositDispositionPersistenceAdapter`; cubierto por `OpenDepositTrackingServiceTest` y `RefundSettlementPersistenceIntegrationTest`. **[ACT-07]**
- [x] **T009** Invocar exactamente una vez a UC09/UC10 en los estados finales con el trigger, `scope`, `disputeId` y `operationKey = event_key`; no consultar ni enviar montos. Bajo `ReservationLockPort`, leer `deposit_disposition`: si ya tiene el desenlace solicitado, confirmar sin repetir la llamada; si tiene el opuesto, registrar la inconsistencia y confirmar; si está `HELD` o ausente, delegar. UC09/UC10 recuperan los registros internos y aplican el CAS. Implementado en `DisputeInformationService` y cubierto por `Uc08AcceptanceTest`. **[ACT-06] [ACT-09] [ACT-10]**
- [x] **T010** Implementar listener con ack para errores no reintentables y nack/DLQ para transitorios. Implementado en `DisputeNotificationListener`; la violación de la FK de reserva desconocida se confirma y registra. **[ACT-10]**
- [x] **T011** Tests de contrato AMQP, listener y ausencia de respuesta. Implementado en `DisputeNotificationListenerContractTest` y `DisputeNotificationListenerIntegrationTest`. **[ACT-10]**

### Phase 4: Aceptación y robustez

- [x] **T012** `ce001_solo_estados_canónicos`. Implementado en `Uc08AcceptanceTest`. **[ACT-10]**
- [x] **T013** `ce002_pendiente_no_mueve_dinero`. Implementado en `Uc08AcceptanceTest`. **[ACT-10]**
- [x] **T014** `ce003_finales_generan_una_sola_consecuencia_total`. Implementado en `Uc08AcceptanceTest`. **[ACT-10]**
- [x] **T015** `ce004_montos_solo_desde_registros_internos`. Implementado en `Uc08AcceptanceTest`. **[ACT-10]**
- [x] **T016** `ce005_concurrencia_idempotente`: mismo `event_key`, `event_key` distinto para el mismo desenlace, finales opuestos y `PENDING` tardío. Implementado en `Uc08AcceptanceTest` y en la prueba concurrente de `DisputeEventLogPersistenceAdapterIntegrationTest`. **[ACT-09] [ACT-10]**
- [x] **T017** `ce006_sin_subresultados_parciales`. Implementado en `Uc08AcceptanceTest`. **[ACT-10]**
- [x] **T018** ArchUnit e integración PostgreSQL/RabbitMQ; verificar que UC08 no introduce jobs ni endpoints. Implementado en `Uc08ArchitectureTest`, `DisputeEventLogPersistenceAdapterIntegrationTest` y `DisputeNotificationListenerIntegrationTest`. **[ACT-10]**

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012..T018`.

UC08 consume las firmas publicadas por UC09/UC10 y publica `OpenDepositTrackingUseCase` para UC07, UC09 y UC10. UC09 y UC10 ya están implementados e invocan ese puerto; UC07 aún no. Cualquier cambio a esos planes queda fuera y solo se documenta. `deposit_disposition` y `dispute_event_log` forman parte de la migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31); UC08 no define ni ordena migraciones propias. **[ACT-05]**

## Notes **[ACT-08] [ACT-10]**

No modificar `pom.xml`, `general-plan.md`, SPEC, contratos ni planes de UC07/UC09/UC10/UC11. No implementar montos desde Reservas, operaciones parciales, cron de 24 horas ni respuesta de negocio al productor. La actualización del 2026-10-30 se registra en [Actualizaciones del plan](#actualizaciones-del-plan); el plan original del 2026-10-09 y la revisión del 2026-10-29 permanecen en el historial de Git (commits `e2b3970` y `b92b0da`). La implementación se cerró el 2026-10-30 con 1395 tests, 0 fallos y 0 errores. **[ACT-08] [ACT-10]**

## Checklist de auto-revisión **[ACT-08] [ACT-10]**

> Revisión documental y cierre de implementación del 2026-10-30.

- [x] Alineado con `general-plan.md`, contrato UC08 y `contracts/README.md`.
- [x] Cada RF/RNF/CE/HU tiene componente, tarea y prueba.
- [x] Ack/nack, DLQ, idempotencia y exclusión mutua documentados.
- [x] Sin REST, jobs, montos externos ni migraciones propias (migración inicial única, general-plan D-31).
- [x] Implementación cerrada con 1395 tests, 0 fallos y 0 errores (ver ACT-10).
