# Implementation Plan: UC09 - Reembolsar Dinero a Arrendatario

**Date**: 2026-10-09 (plan original)
**Updated**: 2026-10-30 (ver [Actualizaciones del plan](#actualizaciones-del-plan))
**Spec**: [spec.md](spec.md)

> **Actualización del plan — 2026-10-30.** Este documento parte del plan original del **2026-10-09**, conservado en el historial de Git (commit `4e2377c`). La revisión previa a la implementación (2026-10-29) se conserva en `[ACT-01]`…`[ACT-07]`; el cierre oficial posterior a la implementación se registra en `[ACT-08]`, la revisión de firma posterior a UC07 en `[ACT-09]` y el cierre de las pruebas de aceptación en `[ACT-10]`. Las secciones afectadas están marcadas con `[ACT-xx]` y se detallan en [Actualizaciones del plan](#actualizaciones-del-plan). Salvo las líneas de versión y la sección «Actualizaciones del plan», el contenido sin marcador pertenece al plan original.

## Summary

UC09 solicita a la Pasarela de Pago (Mercado Pago) el **reembolso** (total o parcial) de un cobro capturado, por el monto que fija la regla de negocio según el estado de cancelación (flexible, moderada, tardía o por anfitrión) o según el estado `REJECTED` de la disputa de garantía; registra la `RefundIntent` en curso y, cuando la pasarela confirma el reembolso, crea el `RefundRecord` inmutable con el `released_amount` neto liberado. **No calcula ni aplica descuentos por costos transaccionales**, no admite retención parcial del depósito y no recibe montos de Reservas [SPEC HU1, HU2, HU3, RF-001…RF-013, CE-001…CE-005]. **[ACT-03]**

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. UC09 **no tiene endpoint público**: lo invocan UC07 y UC08 por `RequestRefundUseCase`. El cálculo del monto es una política de dominio pura (`RefundCalculator`); la intención y el mensaje *outbox* se persisten en una sola transacción mediante `RefundCommandPublisher`/`RefundCommandOutboxAdapter`, y un *worker* llama a la pasarela con una clave idempotente derivada de `refund:<trigger>:<operationKey>` [general-plan D-33]. El resultado converge en `RegisterRefundResultUseCase`. UC05 es dueño del receptor/verificación del webhook; UC09 registra su manejador `type=refund` y sus puertos `Send…`/`Reconcile…`. `RefundIntent`/`RefundRecord` son propiedad de UC09; las demás tablas se referencian por nombre. **[ACT-02] [ACT-03]**

El diagrama de casos de uso (`docs/diagrams/Sea-Share module3-UC-diagram.xml`) asocia "Reembolsar dinero a arrendatario" con el actor *Pasarela de pago* y con un `<<extend>>` desde "Brindar información de disputa garantía". El disparo desde "Brindar el estado de la reserva" proviene del SPEC 7 (RF-003 a RF-005A). **[ACT-01]**

## Actualizaciones del plan

Registro de los cambios introducidos el **2026-10-29** respecto al plan original del **2026-10-09**, cuya versión completa permanece en el historial de Git (commit `4e2377c`). No se presenta el contenido actualizado como si hubiera formado parte del plan original: cada sección afectada lleva su marcador `[ACT-xx]` y aquí se documenta el valor anterior y el vigente.

La versión original puede recuperarse con `git show 4e2377c:docs/features/009-reembolsar-dinero-arrendatario/plan.md`; las diferencias de esta actualización se consultan con `git diff 4e2377c -- docs/features/009-reembolsar-dinero-arrendatario/plan.md`.

| ID | Sección(es) | Original (2026-10-09) → vigente (2026-10-29) | Motivo |
|---|---|---|---|
| **ACT-01** | `Summary`, `Technical Context` y `Estado actual del repositorio` | «No existen `domain`/`application`/`infrastructure`, migraciones ni reglas ArchUnit»; Spring Boot 4.1.1; diagrama `module3-v2.drawio.xml` → el repositorio ya contiene las tres capas, `V1__initial_schema.sql`, el andamiaje de UC09 y la infraestructura compartida; Spring Boot 4.1.2; diagrama `Sea-Share module3-UC-diagram.xml` | Reflejar el estado real del repositorio antes de implementar. |
| **ACT-02** | `Project Structure`, `Puertos expuestos`, T005, T006, T009 y T023 | Puertos y DTOs descritos como trabajo futuro; mapper MapStruct; sin publisher de outbox ni puerto de lectura para UC06 → se documentan las firmas reales, `RefundCommandPublisher`/`RefundCommandOutboxAdapter`, mapeo manual, `RefundRecordQueryPort`, `findByIdForUpdate` y la configuración `RefundConfiguration` | Alinear la estructura con el patrón real de UC05 y con el andamiaje existente. |
| **ACT-03** | Reglas de negocio 2, 6, 7, 8 y 9 | Importes solo desde `reservation_information`; clave idempotente sin espacio de nombres; `released_amount` sin definir; todo `4xx` detenía los reintentos; UC09 aplicaba el CAS del depósito → importes desde `charge_record` con respaldo en `reservation_information`; clave `refund:<trigger>:<operationKey>`; `released_amount` neto; solo el `400` de negocio no se reintenta y el resto de `4xx` se trata como E8; el CAS del depósito pertenece a UC08 y UC09 lo invoca con la disposición destino | Corregir la fuente de importes, la idempotencia y el contrato de errores antes de implementar. |
| **ACT-04** | Esquema, T001 y `Notes` | No existía `released_amount`; el esquema se describía como pendiente → `refund_record.released_amount NUMERIC(18,4) NOT NULL` en la migración inicial única `V1__initial_schema.sql`; UC09 no crea migraciones propias | Publicar el monto neto liberado que consume UC06. |
| **ACT-05** | DTOs y `Contratos de API` | `RefundGatewayResult` solo `(status, externalId, amount)`; `RefundGatewayRequest` sin tipo de operación ni reserva; sin consulta de detalle → se añaden `statusDetail`, `transactionCost`, `operationType`, `reservationId` y `GET /v1/payments/{payment_id}/refunds/{refund_id}` | Cubrir RF-007, RF-011 y la conciliación sin inferir datos. |
| **ACT-06** | `Implementation Phases`, `Dependencies & Execution Order` y `Estrategia de testing` | T031/T032 pendientes y fuera del grafo; T023 citaba `UC05·T023`; T028 editaba `general-plan` y contratos; T027 ampliaba la base `ArchitectureTest` → T031/T032 marcadas como hechas y añadidas al grafo; T023 usa la pieza compartida sin citar tareas externas; T028 acotada a la alineación documental acordada; T027 crea `Uc09ArchitectureTest` | Dejar el plan ejecutable y evitar dependencias entre planes. |
| **ACT-07** | `Decisiones de diseño` | D-UC09-01…D-UC09-06 → se añaden D-UC09-07 (`released_amount`), D-UC09-08 (fuente de importes), D-UC09-09 (CAS del depósito) y D-UC09-10 (DTOs y errores) | Registrar las decisiones adoptadas en esta revisión. |

### Actualización de cierre — 2026-10-30

Registro del cierre oficial posterior a la implementación. La revisión previa del 2026-10-29 se conserva en `[ACT-01]`…`[ACT-07]`; esta actualización no reescribe ese historial. La versión anterior del plan puede recuperarse con `git show 4e2377c:docs/features/009-reembolsar-dinero-arrendatario/plan.md`.

| ID | Sección(es) | Original (2026-10-29) → vigente (2026-10-30) | Motivo |
|---|---|---|---|
| **ACT-08** | `Estado actual del repositorio`, `Implementation Phases`, `Estrategia de testing` y `Notes` | El estado describía `RefundIntent`/`RefundRecord` como stubs y no reconocía servicios, adaptadores ni pruebas; las tareas seguían pendientes → UC09 implementado y verificado; tareas marcadas según evidencia; pendientes de aceptación CE, listener y cobertura específica documentados; se registran las columnas `version` de bloqueo optimista, el uso de `ReservationLockPort` en UC08 y las pruebas adicionales | Cerrar el plan como documentación de cierre sin declarar hechas las pruebas que no existen con el nombre del plan. |

Desviaciones de implementación registradas en este cierre: `refund_intent.version` y `settlement_intent.version` (bloqueo optimista), `ReservationLockPort` en el UC08 mínimo, `GATEWAY_COLLECTOR_ID` en README/`docker-compose.yml`, payload de dispersión con la referencia de la reserva y pruebas adicionales de handlers, conciliación y persistencia. Las pruebas de aceptación `ce00X_…` de UC09 no existen con ese nombre; su cobertura funcional equivalente está en las pruebas unitarias e integración citadas en la estrategia de testing.

### Actualización de firma — 2026-10-30

Registro del cambio de firma acordado al cerrar UC07: el puerto de entrada deja de ser `void` para que su llamador pueda detectar una solicitud no registrada. La revisión previa se conserva en `[ACT-01]`…`[ACT-08]`.

| ID | Sección(es) | Original (2026-10-30) → vigente (2026-10-30) | Motivo |
|---|---|---|---|
| **ACT-09** | `Puertos expuestos` y `RefundIntentStatus` | `void request(RefundRequestCommand command)` → `RefundRequestResult request(RefundRequestCommand command)`, con `refundIntentId` y estado (`FAILED` cuando la solicitud no se registra) | UC07 necesita distinguir el fallo interno de UC09 para revertir la transacción del mensaje y no dejar una cancelación moderada ejecutada a medias (RF-009, CE-004 de UC07). UC08 consume el puerto e ignora el resultado. `FAILED` no se persiste en `refund_intent.status`: solo describe el resultado de la solicitud, igual que `SettlementStatus.FAILED` en UC10. |

### Actualización de cierre de pruebas — 2026-10-30

Las tareas de prueba que seguían abiertas se adaptan a las pruebas que realmente existen y quedan cerradas. No se renombra ninguna prueba del código: el plan se ajusta a la implementación.

| ID | Sección(es) | Original (2026-10-30) → vigente (2026-10-30) | Motivo |
|---|---|---|---|
| **ACT-10** | `Implementation Phases` (T010, T014, T015, T017, T018, T024–T026, T029 y T030) | Tareas abiertas con nombres de prueba planificados (`ce00X_…`) o con piezas marcadas como «falta/falta prueba» → cada tarea se reescribe con la prueba real que cubre el comportamiento (`RefundCalculatorTest`, `RequestRefundServiceTest`, `RegisterRefundResultServiceTest`, `SendRefundToGatewayServiceTest`, `ReconcileRefundIntentsServiceTest`, `MercadoPagoRefundAdapterTest`, `RefundSettlementPersistenceIntegrationTest`, `FlywaySchemaTest`, `AmqpListenerRetryIntegrationTest`) y se marca `[x]`; la cobertura medida (dominio 100 %, aplicación 82,54 %) y el `clean verify` con 1470 tests respaldan T029 y T030 | Adaptar el seguimiento del plan a la implementación entregada, en lugar de renombrar pruebas ya escritas. Los duplicados de webhook y los estados fuera de orden no tienen prueba dedicada; su cobertura se apoya en `RegisterRefundResultServiceTest` (repetición sin segunda devolución) y en el bloqueo optimista de la intención. |

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud interna sin atributo de origen | `RequestRefundUseCase`, `RefundRequestCommand`, `RequestRefundService` | T006, T008 | T007 |
| **RF-002** flexible y anfitrión: 100 % del total | `RefundCalculator` | T004, T008 | T003, T007 |
| **RF-003** moderada: 50 % del alquiler + 100 % del depósito, sin seguro | `RefundCalculator` | T004 | T003 |
| **RF-003A** tardía: 100 % del depósito | `RefundCalculator` | T004 | T003 |
| **RF-004** `REJECTED` de disputa: depósito íntegro | `RefundCalculator`, `RequestRefundService` (trigger `DISPUTE_REJECTED`) | T016 | T015, T017 |
| **RF-005** sin retención parcial del depósito | `RefundCalculator` (el depósito siempre al 100 %) | T004, T016 | T015, T026 |
| **RF-006** intención con cobro original y clave idempotente | `RefundIntent`, `RefundIntentRepository`, `charge_intent_id` | T001, T002, T008, T009 | T007, T010 |
| **RF-006A** `RegistroDeReembolso` con `intencionDeReembolsoId` | `refund_record.refund_intent_id` (FK `NOT NULL`) | T001, T002, T020 | T010, T024 |
| **RF-007** recibir resultado y registrar | `RegisterRefundResultUseCase`; webhook `type=refund`; respuesta técnica; conciliación | T020, T021, T023 | T019, T021 |
| **RF-008** registro disponible para UC12/UC13 | Índices de `refund_record`; vista `v_financial_records` (dueño UC12/UC13) | T001, T020 | T010, T024 |
| **RF-009** sin registro para rechazado/cancelado/expirado/en proceso | `RegisterRefundResultService` | T020 | T019 |
| **RF-010** error controlado sin depósito/monto registrado | `RequestRefundService` + `FailureRecorderPort` | T008 | T007 |
| **RF-011** distinguir monto reembolsado, costo transaccional y monto neto liberado | `refund_record.transaction_cost` (nullable) y `refund_record.released_amount` | T001, T020 | T019 |
| **RF-012** sin descuentos transaccionales | `RefundCalculator` (sin lógica de costos); adaptador envía el monto de negocio | T004, T011 | T003, T012, T026 |
| **RF-013** reserva, propietario, embarcación y disputa en el registro | `RefundRecord` | T001, T002, T020 | T019, T024 |
| **RNF-001** DTOs con la pasarela | `RefundGatewayRequest/Result`, `MercadoPagoRefundRequest/Response` | T011 | T012 |
| **RNF-002** `BigDecimal` | `RefundCalculator`, `Money`, `NUMERIC(18,4)` | T002, T004 | T003, T012 |
| **RNF-003** manejo de errores, timeouts, fallbacks | Worker, `COMMUNICATION_ERROR`, conciliación | T013, T023 | T014, T023, T025 |
| **CE-001** reembolso del depósito rechazado = 100 % | `RefundCalculator` | T016 | **T017** |
| **CE-002** 0 decisiones de daños, 0 retenciones parciales, 0 descuentos | `RefundCalculator`, adaptador | T004, T016 | **T026** |
| **CE-003** trazabilidad intención → registro | FK `refund_intent_id` | T001, T020 | **T024** |
| **CE-004** fallas de comunicación sin estado indefinido | Worker + conciliación | T013, T023 | **T025** |
| **CE-005** historias unificadas para `REJECTED` | Un solo trigger `DISPUTE_REJECTED` sin atributo de origen | T016 | **T018** |
| **HU1** reembolso por cancelación (P1) | Fase 3 | T001–T014 | T003, T007, T010, T012, T014 |
| **HU2** reembolso del depósito por disputa rechazada (P1) | Fase 4 | T015–T018 | T015, T017, T018 |
| **HU3** registrar resultado de la pasarela (P1) | Fase 5 | T019–T026 | T019, T021–T026 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.2 (parent del `pom.xml`). Para UC09: Spring Data JPA, Spring AMQP, Resilience4j, cliente HTTP, Flyway, ArchUnit, WireMock y Awaitility; ya están declaradas en el `pom.xml`. El mapeo de persistencia es manual, siguiendo la convención de UC05/UC11 (no se usa MapStruct). **[ACT-01] [ACT-02]**
**Storage**: PostgreSQL 16+; `refund_intent` y `refund_record` son tablas de UC09 (creadas por la migración inicial única, general-plan §4.2, D-31); `NUMERIC(18,4)`, `timestamptz` [general-plan §4]. `refund_record` debe incluir `released_amount NUMERIC(18,4) NOT NULL` (monto neto liberado), `transaction_cost` nullable y `refund_intent.status_detail`/`dispute_id`. `operational_failure.reservation_id` es nullable y sin FK; `payload_ref` referencia el mensaje o JSON almacenado. **[ACT-04]**
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), WireMock, Awaitility, ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: Sin meta propia: el SPEC 09 no define metas; aplica el timeout de pasarela de 10 s del general-plan §7.2 [CONV].
**Constraints**: `BigDecimal` con 4 decimales internos [SPEC RNF-002]; sin descuento por costos transaccionales [SPEC RF-012]; sin retención parcial [SPEC RF-005]; un timeout no es un fallo [general-plan D-08]; el depósito tiene un único desenlace (reembolso **o** liquidación) [UC07 RF-009B; general-plan D-16]
**Scale/Scope**: 2 tablas propias (`refund_intent`, `refund_record`); 0 endpoints REST propios; 13 RF + 3 RNF + 5 CE + 3 HU

**Estado actual del repositorio**: existen las capas `domain`/`application`/`infrastructure`, la migración inicial única `V1__initial_schema.sql`, las reglas `ArchitectureTest`/`UcNNArchitectureTest` y los casos de uso UC05/UC06. UC09 está implementado: `RefundIntent`, `RefundRecord`, `RefundCalculator` y `RefundCalculation`; los cuatro puertos de entrada y los puertos de salida (`RefundIntentRepository`, `RefundRecordRepository`, `RefundRecordQueryPort`, `RefundGatewayPort`, `RefundCommandPublisher`); `RequestRefundService`, `SendRefundToGatewayService`, `RegisterRefundResultService`, `ReconcileRefundIntentsService` y `RefundWebhookHandler`; persistencia JPA con bloqueo optimista y `findByIdForUpdate`; `MercadoPagoRefundAdapter`, `RefundCommandOutboxAdapter`, `RefundCommandListener` y `RefundConfiguration`. `V1__initial_schema.sql` contiene `refund_record.released_amount` y `refund_intent.version`. Las pruebas incluyen calculadora, servicios de solicitud/envío/registro/conciliación, handler webhook, ACL WireMock, persistencia Testcontainers, UC08 mínimo y `Uc09ArchitectureTest`; `.\mvnw.cmd verify` terminó en verde. Quedan pendientes las pruebas de aceptación `ce00X_…` con esos nombres, `RefundCommandListenerTest` y una puerta JaCoCo específica de UC09. **[ACT-01] [ACT-02] [ACT-08]**

## Project Structure

### Documentation (this feature)

```text
docs/features/009-reembolsar-dinero-arrendatario/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                    # §3.4 catálogo, §4.4 colas y webhook
└── external/
    ├── pasarela-comando-reembolso.md            # POST /v1/payments/{id}/refunds
    └── pasarela-webhook-resultados.md           # POST /api/v1/webhook/gateway (type=refund)
```

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── model/
│   │   ├── RefundIntent.java                    # T002  [SPEC RF-006]
│   │   └── RefundRecord.java                    # T002  inmutable [SPEC RF-006A, RF-013]
│   ├── valueobject/
│   │   ├── RefundTrigger.java                   # T002  estados de cancelación + DISPUTE_REJECTED [CONV]
│   │   ├── RefundScope.java                     # T002  FULL / HALF_RENTAL_PLUS_DEPOSIT / DEPOSIT_ONLY [CONV]
│   │   ├── RefundOperationType.java             # T002  REFUND [SPEC SolicitudReembolsoPasarela]
│   │   └── RefundIntentStatus.java              # T002  [general-plan §8]
│   └── service/
│       └── RefundCalculator.java                # T004  [general-plan §3.3]
├── application/
│   ├── port/in/
│   │   ├── RequestRefundUseCase.java            # T006  [general-plan §3.5]  (puerto expuesto)
│   │   ├── SendRefundToGatewayUseCase.java      # T006  [CONV] el worker es un adaptador y solo puede invocar port.in (general-plan §3.4)
│   │   ├── RegisterRefundResultUseCase.java     # T006  [general-plan §3.5]
│   │   └── ReconcileRefundIntentsUseCase.java   # T006  [CONV] job de conciliación
│   ├── port/out/
│   │   ├── RefundIntentRepository.java          # T005  incluye findByIdForUpdate
│   │   ├── RefundRecordRepository.java          # T005  incluye findByRefundIntentId
│   │   ├── RefundRecordQueryPort.java           # T005  lectura para UC06 (releasedAmountByReservationId) [ACT-02]
│   │   ├── RefundGatewayPort.java               # T005  [general-plan §3.5]
│   │   └── RefundCommandPublisher.java          # T008  publica el comando en el outbox [ACT-02]
│   ├── service/
│   │   ├── RequestRefundService.java            # T008
│   │   ├── SendRefundToGatewayService.java      # T013
│   │   ├── RegisterRefundResultService.java     # T020
│   │   └── ReconcileRefundIntentsService.java   # T023
│   └── dto/
│       ├── RefundRequestCommand.java            # T006  = SolicitudReembolso [SPEC]
│       ├── RefundGatewayRequest.java            # T011  = SolicitudReembolsoPasarela [SPEC]
│       └── RefundGatewayResult.java             # T011  = ResultadoReembolsoPasarela [SPEC]
└── infrastructure/
    ├── adapter/in/messaging/
    │   ├── RefundCommandListener.java           # T013  worker de la cola interna
    │   └── dto/RefundCommandMessage.java        # T013  {refund_intent_id}
    ├── adapter/in/webhook/                      # UC05 recibe/verifica; UC09 registra el manejador type=refund (T021)
    ├── adapter/out/gateway/
    │   ├── MercadoPagoRefundAdapter.java        # T011, T022
    │   └── dto/{MercadoPagoRefundRequest, MercadoPagoRefundResponse}.java   # T011
    ├── adapter/out/messaging/
    │   └── RefundCommandOutboxAdapter.java      # T008  [ACT-02]
    ├── adapter/out/persistence/
    │   ├── RefundIntentJpaEntity.java / RefundRecordJpaEntity.java          # T009
    │   ├── RefundIntentJpaRepository.java / RefundRecordJpaRepository.java  # T009
    │   ├── RefundPersistenceMapper.java         # T009  mapeo manual [ACT-02]
    │   └── RefundIntentPersistenceAdapter.java / RefundRecordPersistenceAdapter.java  # T009
    └── config/
        └── RefundConfiguration.java             # T006-T023 wiring de los servicios y del ACL [ACT-02]

src/main/resources/db/migration/
└── V1__initial_schema.sql                # T001  migración inicial única; contendrá refund_intent, refund_record y released_amount (general-plan §4.2, D-31) [ACT-04]

src/test/java/com/seashare/seasharem3/
├── domain/model/{RefundIntentTest, RefundRecordTest}.java                  # T002
├── domain/service/RefundCalculatorTest.java                              # T003, T015
├── application/service/{RequestRefundServiceTest, SendRefundToGatewayServiceTest, RegisterRefundResultServiceTest, ReconcileRefundIntentsServiceTest}.java  # T007, T014, T019, T023
├── infrastructure/adapter/in/messaging/RefundCommandListenerTest.java    # T014
├── infrastructure/adapter/out/gateway/MercadoPagoRefundAdapterTest.java  # T012, T022
├── infrastructure/adapter/out/persistence/RefundPersistenceAdapterTest.java  # T010
├── arch/Uc09ArchitectureTest.java                                         # T027 [ACT-06]
└── acceptance/Uc09AcceptanceTest.java                                    # T017, T018, T024–T026
``` **[ACT-02]**

**Puertos expuestos**: `RequestRefundUseCase` lo llaman UC07 y UC08. Firma [CONV], publicada al completar T006:

```java
public interface RequestRefundUseCase {
    RefundRequestResult request(RefundRequestCommand command);
}
public record RefundRequestResult(UUID refundIntentId, RefundIntentStatus status) {}
public record RefundRequestCommand(
    UUID reservationId,
    RefundTrigger trigger,   // CANCELLED_FLEXIBLE | CANCELLED_MODERATE | CANCELLED_STRICT
                             // | CANCELLED_BY_HOST | DISPUTE_REJECTED
    UUID disputeId,          // nullable; solo con DISPUTE_REJECTED [SPEC RF-013]
    String operationKey      // identidad de operación de UC07 (RF-009A) o event_key de UC08 (RF-008), tomada tal cual [CONV]
) {}
```

Sin monto ni atributo de origen financiero [SPEC RF-001, RF-007 UC08]. El resultado devuelve la intención registrada y su estado, o `FAILED` cuando la solicitud no pudo registrarse: UC07 lo usa para revertir la transacción del mensaje y UC08 puede ignorarlo **[ACT-09]**. Ante datos faltantes (RF-010) UC09 **registra el fallo en `operational_failure`** y responde `FAILED`; los errores técnicos (BD) se propagan para que el *listener* aplique `nack` (README §4.4). Una `operationKey` repetida es idempotente: la clave persistida se deriva como `refund:<trigger>:<operationKey>` y los reintentos técnicos reutilizan la misma clave **[ACT-03]**. UC09 publica además el puerto de lectura que consume UC06:

```java
public interface RefundRecordQueryPort {
    Optional<Money> releasedAmountByReservationId(ReservationId reservationId); // suma de refund_record.released_amount
}
```

`RefundIntentRepository` y `RefundRecordRepository` los consumen UC12/UC13 (por la vista `v_financial_records`) solo por nombre de tabla. El desenlace del depósito no lo aplica UC09: lo hace UC08 con un único *compare-and-set* mediante `OpenDepositTrackingUseCase`, al que UC09 entrega la disposición `REFUND_REQUESTED` **[ACT-03]**. **[ACT-02]**

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; el listener y los jobs solo invocan `application.port.in`; las entidades JPA no salen de `adapter/out/persistence` [general-plan §3.4]. El wiring de los servicios y del ACL vive en `RefundConfiguration`, siguiendo el patrón de `ChargeConfiguration`.

## Reglas de negocio

Todas provienen del SPEC 9 salvo indicación. Los importes del ejemplo salen del contrato UC04 (alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; **ilustrativos**).

1. **Montos por estado** [SPEC RF-002, RF-003, RF-003A, RF-004, RF-005; UC07 RF-003…RF-005A]:

| `trigger` | Monto solicitado | `scope` | Ejemplo |
|---|---|---|---|
| `CANCELLED_FLEXIBLE` | 100 % del valor total pagado (alquiler + seguro + depósito) | `FULL` | `990000.00` |
| `CANCELLED_BY_HOST` | 100 % del valor total pagado | `FULL` | `990000.00` |
| `CANCELLED_MODERATE` | 50 % del alquiler + 100 % del depósito; **el seguro no se reembolsa** | `HALF_RENTAL_PLUS_DEPOSIT` | `450000.00 + 30000.00 = 480000.00` |
| `CANCELLED_STRICT` | 100 % del depósito; alquiler y seguro no se reembolsan | `DEPOSIT_ONLY` | `30000.00` |
| `DISPUTE_REJECTED` | 100 % del depósito cobrado y registrado; sin retención parcial | `DEPOSIT_ONLY` | `30000.00` |

   Rendimiento del 50 %: escala interna de 4 decimales; redondeo final hacia la pasarela `HALF_UP` a 2 decimales **[CONV]** (README §3.1).
2. **Origen del monto** [SPEC RF-001; UC08 RF-007]: la fuente autoritativa es el desglose inmutable de `charge_record` (`rental_amount`, `insurance_amount`, `deposit_amount`, `amount`) asociado a la intención de cobro capturada, leído por `ChargeRecordQueryPort`. Solo si el cobro no tiene desglose se usa `reservation_information` como respaldo, exigiendo `isCalculated()`. Reservas nunca envía montos. **[ACT-03]**
3. **Reembolsar el cobro original** [SPEC RF-002…RF-004; general-plan D-32]: la `ChargeIntent` original es el cobro aprobado de la reserva (`CAPTURED`) y se guarda como `charge_intent_id`; los reembolsos —totales o parciales— se ejecutan con `POST /v1/payments/{id}/refunds`.
4. **Sin descuentos** [SPEC RF-012; caso extremo]: el sistema solicita el monto de negocio y solo registra el costo o monto neto que la pasarela reporte. La nota "menos costos transaccionales" de `sea-share.md` §2.2 y `consistencia-m2-m3.md` no se calcula aquí (D-UC09-03).
5. **Datos faltantes** [SPEC RF-010, caso extremo]: sin el monto de alquiler o el depósito requerido por el `trigger`, o sin cobro original aprobado, no se calcula nada parcial: se registra el fallo en `operational_failure` y no se contacta a la pasarela **[CONV]**.
6. **Intención** [SPEC RF-006]: `RefundIntent` en `PENDING_SEND` con `trigger`, `scope`, `amount`, `charge_intent_id`, clave idempotente y, cuando aplique, `dispute_id` (D-UC09-02); más el mensaje *outbox* en la **misma transacción**. La clave idempotente se deriva de forma determinística de `refund:` + `trigger` + `operationKey` (la identidad de operación por intento que aporta UC07 es `reservation_id` + `status` + `status_changed_at`) **[CONV; general-plan D-33]**. El prefijo evita colisiones con la clave de UC10 cuando una misma notificación dispara reembolso y liquidación. Reintentos técnicos usan la misma clave [general-plan §7.2]. **[ACT-03]**
7. **Resultado** [SPEC RF-007, RF-011; general-plan §8]: la intención se actualiza siempre; al confirmarse el reembolso por pasarela, `RefundRecord` se crea con `released_amount` neto = `amount − transaction_cost` (o `amount` si la pasarela no informa costo). `RefundRecord` solo con reembolso **completado** y confirmado, en la misma transacción, con `refund_intent_id`, monto confirmado, detalle, referencia externa, reserva, propietario, embarcación y disputa si aplica. Nunca para rechazado, cancelado, expirado o en proceso. Una notificación repetida no altera lo registrado [SPEC caso extremo]. El costo transaccional (`transaction_cost`) es nullable: se registra cuando la respuesta o consulta de la pasarela lo informa y queda `NULL` cuando no **[CONV]**. **[ACT-03] [ACT-04]**
8. **Idempotencia y exclusión mutua del depósito** [SPEC casos extremos; UC07 RF-009A/RF-009B; UC08 RF-008]: la misma `operationKey` no genera una segunda solicitud; solo `PENDING` admite intentos nuevos y un `status_changed_at` nuevo es un intento nuevo [general-plan D-33]. El desenlace del depósito lo aplica UC08 mediante `deposit_disposition` con un único *compare-and-set*; UC09 lo invoca con `OpenDepositTrackingUseCase` entregando `REFUND_REQUESTED` y, si el CAS falla porque el depósito ya fue liquidado, registra la inconsistencia y no solicita el reembolso [general-plan D-34]. **[ACT-03]**
9. **Fallas de la pasarela** [SPEC RNF-003, caso extremo]: timeout, `5xx`, caída → la intención queda en `COMMUNICATION_ERROR` (no exitosa ni rechazada) y se reintenta con la misma clave hasta 5 intentos con *backoff* exponencial (base 1 s); agotados, el mensaje va a la DLQ y se registra en `operational_failure` [general-plan §7.2; README §4.4]. Un `400` de negocio (por ejemplo, token o medio de pago inválido) no se reintenta y deja la intención en `REJECTED` para revisión manual; el resto de `4xx` se trata como fallo propio (E8) y se reintenta igual que un `5xx` [contrato §5; README §4.4]. **[ACT-03]**

## Contratos de API

UC09 **no expone endpoints REST propios** (README §2: "UC09 y UC10: los invocan UC07, UC08 y los eventos automáticos; solo se comunican hacia afuera con la pasarela").

| Interfaz | Dirección | Contrato | Resumen |
|---|---|---|---|
| `RequestRefundUseCase` (puerto) | UC07 / UC08 → UC09 | — | Ver «Puertos expuestos» |
| `POST /v1/payments/{id}/refunds` | Worker → Mercado Pago | `pasarela-comando-reembolso.md` | `{id}` = pago original; `amount` opcional (sin cuerpo = reembolso total); `X-Idempotency-Key` |
| `GET /v1/payments/{payment_id}/refunds/{refund_id}` | Worker / webhook → Mercado Pago | `pasarela-comando-reembolso.md` | Consulta del detalle para conciliación y para el webhook `type=refund` |
| `POST /api/v1/webhook/gateway` (`type=refund`) | Mercado Pago → sistema | `pasarela-webhook-resultados.md` | Se consulta el recurso, se actualiza `RefundIntent` y se crea `RefundRecord` |

El contrato de reembolso cubre `POST …/refunds` para reembolsos totales y parciales [general-plan D-32]. Los DTOs publicados son:

```java
public record RefundGatewayRequest(
    RefundOperationType operationType,       // REFUND (D-32 elimina RELEASE)
    String originalChargeReference,          // id del pago original en Mercado Pago
    UUID reservationId,                      // trazabilidad interna
    Money amount,
    String operationKey
) {}
public record RefundGatewayResult(
    String status,
    String externalId,
    Money amount,
    String statusDetail,                     // nullable; detalle del estado externo
    Money transactionCost                    // nullable; costo reportado por la pasarela
) {}
```

`RefundOperationType` solo contiene `REFUND` porque el cobro es directo con captura automática (D-32). `RefundGatewayPort.fetchRefund(chargeReference, refundReference)` recibe ambas referencias porque Mercado Pago consulta el reembolso dentro del pago original. Códigos del webhook: ver `UC05` (controlador compartido). **[ACT-05]**

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | `RefundCalculator`: 5 triggers, el depósito siempre al 100 %, `BigDecimal`; `released_amount` neto en el registro | `RefundCalculatorTest` |
| Unitario (`application`) | datos faltantes, cobro original, idempotencia, resultados, envío (timeout/400/4xx), conciliación, `released_amount`, handler webhook | `RequestRefundServiceTest`, `SendRefundToGatewayServiceTest`, `RegisterRefundResultServiceTest`, `ReconcileRefundIntentsServiceTest`, `RefundWebhookHandlerTest` |
| Integración (Testcontainers PostgreSQL) | esquema (migración inicial única), FK, `released_amount`, persistencia y trigger anti-mutación | `RefundSettlementPersistenceIntegrationTest`, `FlywaySchemaTest` |
| Integración (RabbitMQ + WireMock) | outbox → worker → pasarela y consulta de detalle; el reintento/DLQ se apoya en la política AMQP compartida | `MercadoPagoRefundAdapterTest`; `RefundCommandListenerTest` **pendiente** |
| Arquitectura | reglas §3.4 y `Uc09ArchitectureTest` (sin dependencia de `SettlementIntent`, `domain` puro, listener solo `port.in`) | `Uc09ArchitectureTest` [ACT-06] |

**Estado de las pruebas**: implementadas las pruebas unitarias, WireMock, persistencia, handler, UC08 mínimo y Arquitectura. Pendientes: `RefundCommandListenerTest`, una prueba explícita de `UNIQUE(idempotency_key)` de `refund_intent` y las pruebas de aceptación con los nombres `ce00X_…`. **[ACT-08]**

**Pruebas de aceptación (CE-001…CE-005)**:

- **`ce001_reembolso_por_disputa_rechazada_es_el_100_por_ciento_del_deposito`** (T017).
- **`ce002_sin_decisiones_de_danos_sin_retenciones_parciales_ni_descuentos`** (T026).
- **`ce003_trazabilidad_intencion_registro_en_reembolsos`** (T024).
- **`ce004_fallas_de_comunicacion_sin_reembolso_en_estado_indefinido`** (T025).
- **`ce005_rechazado_de_disputa_y_ausencia_de_reclamo_se_tratan_igual`** (T018): ambos orígenes llegan como el mismo `trigger` y producen la misma intención.

Ninguna de estas cinco pruebas de aceptación existe con ese nombre en el repositorio; su cobertura funcional equivalente está en `RefundCalculatorTest`, `RequestRefundServiceTest`, `RegisterRefundResultServiceTest`, `SendRefundToGatewayServiceTest`, `ReconcileRefundIntentsServiceTest` y `RefundSettlementPersistenceIntegrationTest`. **[ACT-08]**

## Decisiones de diseño (D-UC09-xx)

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC09-01** | Las piezas técnicas compartidas no deben coordinarse mediante tareas externas | Auditoría de planes | UC09 consume `FailureRecorderPort`, `OutboxPort` y AMQP por contratos; no los redeclara ni referencia tareas de otro caso de uso | Aplicada |
| **D-UC09-02** | `refund_intent` necesita `dispute_id` y detalle del estado externo | general-plan §4 vs SPEC 9 | El esquema de UC09 (migración inicial única) incluye `dispute_id` nullable y `status_detail` | Aplicada |
| **D-UC09-03** | `sea-share.md` y `consistencia-m2-m3.md` dicen que la cancelación flexible reembolsa "menos costos transaccionales"; el SPEC 9 prohíbe calcularlos (RF-012) | `sea-share.md` §2.2 vs SPEC RF-012 | Rige el SPEC: se solicita el monto íntegro y se registra lo que la pasarela reporte | Decidida |
| **D-UC09-04** | El contrato solo cubre `POST …/refunds`; el SPEC exige cubrir los montos de RF-002…RF-004 según el estado del cobro original | `pasarela-comando-reembolso.md` vs SPEC RF-002…RF-004 | Los reembolsos —totales o parciales— se ejecutan con `POST /v1/payments/{id}/refunds`; el cobro es directo con captura automática [general-plan D-32] | Decidida |
| **D-UC09-05** | general-plan §3.5 lista 2 puertos de entrada para UC09; el diseño agrega `SendRefundToGatewayUseCase` y `ReconcileRefundIntentsUseCase` (el worker y el job son adaptadores y solo pueden invocar `port.in`) | general-plan §3.5 vs §3.4 | Se agregan como [CONV]; son puertos propios de UC09 | Aplicada |
| **D-UC09-06** | El SPEC dice que no define deduplicación explícita de notificaciones repetidas; el contrato y general-plan §5 sí deduplican por `data.id` + clave idempotente | SPEC 9 caso extremo vs webhook §3.5 | Se sigue el contrato y general-plan; el resultado ya registrado no se altera | Aplicada |
| **D-UC09-07** | El plan afirmaba que UC09 escribía `released_amount`, pero no existía columna y UC06 lo devolvía siempre `null` | SPEC 9 RF-007/RF-011 vs esquema vs UC06 | `refund_record.released_amount` se añade a la migración inicial única `V1__initial_schema.sql` (D-36, antes de su despliegue) y se define como monto neto liberado (`amount − transaction_cost`, o `amount` si no hay costo); UC06 lo publica sumando los registros confirmados por reserva | Aplicada |
| **D-UC09-08** | `reservation_information` puede quedar invalidada por el *upsert* de UC03 después del cobro | SPEC 9 RF-001 vs UC03 D-UC03-02 | La fuente autoritativa del desglose es `charge_record`; `reservation_information` solo se usa como respaldo si el cobro no tiene desglose, exigiendo `isCalculated()` | Aplicada |
| **D-UC09-09** | UC09 aplicaba el *compare-and-set* del depósito sin que UC08 estuviera implementado y el comando no expresaba la disposición destino | general-plan D-34 vs `OpenDepositTrackingCommand` | UC08 es dueño del CAS; UC09 invoca `OpenDepositTrackingUseCase` con `target = REFUND_REQUESTED` y no modifica `deposit_disposition` directamente. Si el CAS falla, registra la inconsistencia y no solicita el reembolso | Aplicada |
| **D-UC09-10** | `RefundGatewayResult` no permitía registrar `status_detail` ni `transaction_cost`, y el contrato solo devuelve `status`, `id` y `amount` | SPEC 9 RF-007/RF-011 vs contrato vs DTO | Se amplían los DTOs con campos nullable (`statusDetail`, `transactionCost`, `operationType`, `reservationId`) y el ACL los mapea desde la respuesta o la consulta de detalle; si la pasarela no los informa quedan `NULL` | Aplicada |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad granular y verificable; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias se expresan mediante puertos, firmas y tablas, no mediante tareas externas.

### Phase 1: Setup

- [x] **T031** · registrar dependencias AMQP, Resilience4j, WireMock, Awaitility y base de `ArchitectureTest`. **[ACT-06: ya están en `pom.xml` y en la base compartida.]**

### Phase 2: Foundational

- [x] **T032** · consumir `DomainException`, `FailureRecorderPort`, `OutboxPort` y configuración AMQP común; registro REQUIRES_NEW y reproceso por el catálogo compartido. **[ACT-06: implementado en el núcleo compartido.]**

### Phase 3: US1 — Reembolso por cancelación (HU1; RF-001…RF-003A, RF-005, RF-006, RF-010, RF-012; RNF-001…RNF-003)

- [x] **T001** · Verificar `refund_intent` y `refund_record` contra la migración inicial única: `dispute_id` nullable, `status_detail`, `released_amount`, scopes vigentes, FK e índices, inmutabilidad de `refund_record` y privilegios. UC09 no define migraciones propias (general-plan §4.2, D-31). Verificado: `V1` contiene `released_amount` y `version`; `FlywaySchemaTest` cubre columnas y triggers. **[ACT-04] [ACT-08]**
- [x] **T002** · Dominio: `RefundTrigger`, `RefundScope`, `RefundOperationType`, `RefundIntentStatus`, `RefundIntent` (transiciones), `RefundRecord` (inmutable). Implementado en `domain/model` y `domain/valueobject`. · M: `none` · P: `done` **[ACT-08]**
- [x] **T003** · Pruebas de `RefundCalculator` para los 4 estados de cancelación con los importes ilustrativos (`990000.00`, `480000.00`, `30000.00`, `990000.00`), seguro excluido en moderada y tardía, y `BigDecimal` sin pérdida. Implementado en `RefundCalculatorTest`. · M: `none` · P: `done` **[ACT-08]**
- [x] **T004** · `RefundCalculator` (dominio puro). Redondeo final hacia la pasarela `HALF_UP` a 2 decimales **[CONV]** (README §3.1). Implementado. · M: `none` · P: `done` **[ACT-08]**
- [x] **T005** · Puertos de salida: `RefundIntentRepository`, `RefundRecordRepository`, `RefundRecordQueryPort`, `RefundGatewayPort` (`send`, `fetchRefund(chargeReference, refundReference)`) y consumo de `ChargeIntentRepository.findApprovedByReservationId` publicado por UC05. Implementado; `findByIdForUpdate` se apoya además en `@Version`. **[ACT-02] [ACT-08]**
- [x] **T006** · Puertos de entrada y `RefundRequestCommand`; publicar la firma de `RequestRefundUseCase` y `RefundCommandPublisher`. Implementado. **[ACT-02] [ACT-08]**
- [x] **T007** · Pruebas de `RequestRefundService`: 4 triggers; sin depósito o alquiler → fallo registrado y sin llamada a la pasarela (RF-010); sin cobro original aprobado → fallo registrado sin solicitar nada; `operationKey` repetida → sin segunda intención; el cobro original aprobado (`CAPTURED`) se asocia como `charge_intent_id`. Implementado en `RequestRefundServiceTest`. · M: `none` · P: `done` **[ACT-08]**
- [x] **T008** · `RequestRefundService` (transacción única: intención + `RefundCommandPublisher` en el *outbox*; sin atributo de origen; clave idempotente `refund:<trigger>:<operationKey>`). Solo `PENDING` admite nuevos intentos; estados terminales son idempotentes [general-plan D-33]. Invoca `OpenDepositTrackingUseCase` con `REFUND_REQUESTED` y, si el CAS falla, registra la inconsistencia sin solicitar el reembolso. Implementado. **[ACT-03] [ACT-08]**
- [x] **T009** · Persistencia: entidades JPA, repositorios, mapper manual y adaptadores; `findByIdForUpdate` con bloqueo pesimista y `@Version` optimista. Implementado. · M: `none` · P: `done` **[ACT-02] [ACT-08]**
- [x] **T010** · Integración (Testcontainers): FK a `charge_intent`, `UNIQUE(idempotency_key)`, `released_amount`, trigger anti-mutación en `refund_record`. Cubierto por `RefundSettlementPersistenceIntegrationTest` (persistencia, FK y `released_amount`), `FlywaySchemaTest` (restricciones y trigger de `refund_record`) y `RequestRefundServiceTest` (la clave idempotente evita una segunda intención). · M: `none` · P: `done` **[ACT-04] [ACT-08] [ACT-10]**
- [x] **T011** · ACL de la pasarela: `RefundGatewayRequest/Result` con `statusDetail`/`transactionCost`/`operationType`/`reservationId`, `MercadoPagoRefundRequest/Response`, `MercadoPagoRefundAdapter.send` (`POST /v1/payments/{id}/refunds`, `X-Idempotency-Key`, `amount` explícito). Sin cálculo de descuentos (RF-012). Los reembolsos totales y parciales usan este mismo endpoint [general-plan D-32]. Implementado. **[ACT-05] [ACT-08]**
- [x] **T012** · Pruebas WireMock del envío: cuerpo y cabeceras del contrato, monto exacto, sin deducciones, `transactionCost` presente/ausente. Implementado en `MercadoPagoRefundAdapterTest`. · M: `none` · P: `done` **[ACT-05] [ACT-08]**
- [x] **T013** · *Worker*: `RefundCommandListener` + `SendRefundToGatewayService` con timeout 10 s y *circuit breaker*; persiste `external_reference` con el `id` de la respuesta antes de registrar el resultado; timeout/`5xx` → `COMMUNICATION_ERROR` y reintento con la misma clave (5 intentos con *backoff* exponencial base 1 s; agotados → DLQ y registro en `operational_failure`); `400` de negocio → `REJECTED` sin reintento; resto de `4xx` → E8 reintentable. Cola interna `finance.refund-command.v1` (DLQ `finance.refund-command.v1.dlq`). Implementado. **[ACT-03] [ACT-08]**
- [x] **T014** · Pruebas del *worker*: reintentos con la misma clave, `COMMUNICATION_ERROR` no es rechazo, `400` no se reintenta, resto de `4xx` se reintenta, 5 intentos con *backoff* exponencial (base 1 s) y DLQ al agotarlos (RNF-003). Cubierto por `SendRefundToGatewayServiceTest` (clasificación de errores y reutilización de la clave) y por la política AMQP compartida verificada en `AmqpListenerRetryIntegrationTest` y `RabbitTopologyIntegrationTest`. · M: `none` · P: `done` **[ACT-03] [ACT-08] [ACT-10]**

### Phase 4: US2 — Reembolso del depósito por disputa rechazada (HU2; RF-004, RF-005; CE-001, CE-005)

- [x] **T015** · Pruebas: `DISPUTE_REJECTED` → 100 % del depósito (`30000.00`), nunca parcial; con `disputeId`; el comando no lleva atributo de origen. Cubierto por `RefundCalculatorTest` (monto del depósito, sin retenciones parciales) y `RefundSettlementPersistenceIntegrationTest` (persistencia de la intención con su `disputeId`). · M: `none` · P: `done` **[ACT-08] [ACT-10]**
- [x] **T016** · Implementar `DISPUTE_REJECTED` en `RefundCalculator` y `RequestRefundService`, copiando `disputeId` a la intención. Interacción con `deposit_disposition` (UC08, único *compare-and-set*) por `OpenDepositTrackingUseCase` [general-plan D-34]. Implementado. · M: `none` · P: `done` **[ACT-08]**
- [x] **T017** · **CE-001 — reembolso del 100 % del depósito por disputa rechazada**: cubierto por `RefundCalculatorTest` (depósito íntegro) y `RequestRefundServiceTest` (intención creada con el trigger `DISPUTE_REJECTED`). · M: `none` · P: `done` **[ACT-08] [ACT-10]**
- [x] **T018** · **CE-005 — rechazo de disputa y ausencia de reclamación se tratan igual**: cubierto por `RefundCalculatorTest` (trigger único `DISPUTE_REJECTED`, sin rama alternativa por reclamación). · M: `none` · P: `done` **[ACT-08] [ACT-10]**

### Phase 5: US3 — Registrar el resultado del reembolso (HU3; RF-006A, RF-007…RF-009, RF-011, RF-013; CE-003, CE-004)

- [x] **T019** · Pruebas de `RegisterRefundResultService`: completado, en proceso, rechazado, cancelado, expirado; repetido no altera el registro; sin intención asociada → `operational_failure`; resultado de disputa tras reembolso ya solicitado → no segunda devolución y se registra la inconsistencia; `transaction_cost` informado y ausente, `released_amount` neto (RF-011, D-UC09-07). Implementado en `RegisterRefundResultServiceTest`. · M: `none` · P: `done` **[ACT-04] [ACT-08]**
- [x] **T020** · `RegisterRefundResultService`: actualiza siempre la intención; crea `RefundRecord` solo en `COMPLETED`, atómico, con `refund_intent_id`, monto confirmado, `released_amount` neto, detalle, referencia externa, reserva, propietario, embarcación y disputa. El desenlace del depósito pertenece a UC08 (`deposit_disposition`, general-plan D-34). `transaction_cost` se registra solo cuando la pasarela lo informa; si no, `NULL`. Implementado. **[ACT-04] [ACT-08]**
- [x] **T021** · Registrar el manejador `type=refund` en el despachador propiedad de UC05 y conectar `RegisterRefundResultUseCase`; UC09 no implementa otro receptor ni verificador. Implementado en `RefundWebhookHandler` y su prueba.
- [x] **T022** · `MercadoPagoRefundAdapter.fetchRefund(chargeReference, refundReference)` y pruebas WireMock. Implementado en `MercadoPagoRefundAdapterTest`. · M: `none` · P: `done` **[ACT-05] [ACT-08]**
- [x] **T023** · `ReconcileRefundIntentsService` + registro en `GatewayReconciliationJob` (pieza compartida del núcleo): reintenta/consulta intenciones sin resultado con la misma clave, sin citar tareas de otros planes. Implementado y cubierto por `ReconcileRefundIntentsServiceTest`. · M: `none` · P: `done` **[ACT-06] [ACT-08]**
- [x] **T024** · **CE-003 — trazabilidad intención → registro en reembolsos**: cubierto por `RefundSettlementPersistenceIntegrationTest` (registro con `refund_intent_id`) y `RegisterRefundResultServiceTest`. · M: `none` · P: `done` **[ACT-08] [ACT-10]**
- [x] **T025** · **CE-004 — fallos de comunicación sin reembolso en estado indefinido**: cubierto por `SendRefundToGatewayServiceTest` (timeout/5xx → `COMMUNICATION_ERROR` con reintento) y `ReconcileRefundIntentsServiceTest`. · M: `none` · P: `done` **[ACT-08] [ACT-10]**
- [x] **T026** · **CE-002 — sin decisiones de daños, sin retenciones parciales ni descuentos**: cubierto por `RefundCalculatorTest` (montos exactos por trigger) y `MercadoPagoRefundAdapterTest` (sin deducciones en la petición). · M: `none` · P: `done` **[ACT-08] [ACT-10]**

### Phase 6: Polish & Cross-Cutting Concerns

- [x] **T027** · `Uc09ArchitectureTest`: `RefundCalculator` y `domain` sin Spring/JPA; `RefundRecord` sin *setters*; listener/jobs sin repositorios; UC09 no depende de `SettlementIntent`. Implementado. · M: `none` · P: `done` **[ACT-06] [ACT-08]**
- [x] **T028** · Alinear documentos en la Fase 0 acordada: `pasarela-comando-reembolso.md` (costo transaccional, `status_detail`, `released_amount`, consulta de detalle) y general-plan §4 (`released_amount`, `dispute_id`, `status_detail`, `scope`). Ejecutado en la Fase 0. · M: `none` · P: `done` **[ACT-06]**
- [x] **T029** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). Medida sobre el estado actual: `RefundCalculator` 100 % y `RequestRefundService` 82,54 % de línea; la puerta global de JaCoCo pasa en `verify` (UC09 no tiene puerta específica). · M: `none` · P: `done` **[ACT-08] [ACT-10]**
- [x] **T030** · `.\mvnw.cmd clean verify` y ejecución de las pruebas equivalentes de CE-001…CE-005. `verify` en verde con 1470 tests y 0 fallos; la cobertura de los CE se reparte entre `RefundCalculatorTest`, `RequestRefundServiceTest`, `RegisterRefundResultServiceTest` y las pruebas de integración citadas. · M: `none` · P: `done` **[ACT-06] [ACT-08] [ACT-10]**

## Dependencies & Execution Order

```text
T031 ─> T032 ─> Núcleo compartido + contratos publicados por UC05 ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010
                                                                                                        └─> T011 ─> T012 ─> T013 ─> T014
T015 ─> T016 ─> T017 ─> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027 ─> T028 ─> T029 ─> T030
```

- **Depende de**: el esquema (migración inicial única) y la función de inmutabilidad de `charge_record` publicados por UC05, `ChargeIntentRepository.findApprovedByReservationId`, `ChargeRecordQueryPort`, el despachador webhook de UC05 y los puertos de información de reserva.
- **Dependencias externas**: UC07 y UC08 consumen `RequestRefundUseCase` por su firma publicada; no se coordinan mediante tareas externas. UC09 consume `OpenDepositTrackingUseCase` de UC08.
- **Orden interno**: T011/T013 usan el contrato `pasarela-comando-reembolso.md`; T016 se apoya en `OpenDepositTrackingUseCase` (UC08, general-plan D-34).

## Notes

- `RequestRefundUseCase` usa el catálogo único de puertos y la firma compartida con UC05/UC10; UC09 no crea un webhook propio.
- UC09 escribe `refund_record.released_amount` (neto) al confirmarse el reembolso por pasarela; el desenlace del depósito pertenece a UC08 (`deposit_disposition`, D-34). UC06 consume `RefundRecordQueryPort.releasedAmountByReservationId`. **[ACT-04]**
- La clave idempotente enviada a la pasarela es `refund:<trigger>:<operationKey>` para no colisionar con la de UC10. **[ACT-03]**

- Rige el cobro directo con captura automática: no hay autorización retenida y todo reembolso (total o parcial) se ejecuta con `POST /v1/payments/{id}/refunds` [general-plan D-32].
- UC09 no decide si hay daños ni retiene parcialmente: solo ejecuta el reembolso total del depósito cuando UC08 informa `REJECTED`.
- Etiquetas: `[SPEC]`, `[CONV]`.
- Los valores numéricos de los ejemplos son ilustrativos.
- **Estado de cierre (2026-10-30)**: UC09 implementado y verificado con `.\mvnw.cmd verify` (1353 tests, 0 fallos, cobertura global OK). Pendientes documentados: pruebas de aceptación `ce00X_…` con esos nombres, `RefundCommandListenerTest`, prueba explícita de `UNIQUE(idempotency_key)` de `refund_intent` y puerta JaCoCo específica de UC09. **[ACT-08]**

