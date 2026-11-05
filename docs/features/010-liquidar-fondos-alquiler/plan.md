# Implementation Plan: UC10 - Liquidar fondos de alquiler

**Date**: 2026-10-09 (plan original)
**Updated**: 2026-11-04 (ver [Actualizaciones del plan](#actualizaciones-del-plan))
**Spec**: [spec.md](spec.md)

> **Actualización del plan — 2026-10-30.** Este documento parte del plan original del **2026-10-09**, conservado en el historial de Git (commit `b26bcbb`). La revisión previa a la implementación (2026-10-29) se conserva en `[ACT-01]`…`[ACT-08]`; el cierre oficial posterior a la implementación se registra en `[ACT-09]`, el cierre de las pruebas de aceptación, en `[ACT-10]`, el libro interno simulado, en `[ACT-11]`, y la deduplicación por identidad de negocio (general-plan D-37), en `[ACT-12]`. Las secciones afectadas están marcadas con `[ACT-xx]` y se detallan en [Actualizaciones del plan](#actualizaciones-del-plan). Salvo las líneas de versión y la sección «Actualizaciones del plan», el contenido sin marcador pertenece al plan original.

## Summary

UC10 es el caso de uso interno que solicita dispersiones al propietario por tres motivos: cancelación moderada (50 % del alquiler), cancelación tardía (100 % del alquiler), liquidación estándar de una reserva `COMPLETED` y liquidación total del depósito cuando una disputa termina en `COMPLETED`. No recibe montos desde Reservas: recupera los valores de `reservation_information` y conserva la comisión calculada en la intención de dispersión [SPEC RF-001..RF-009, RF-016].

El sistema registra una `SettlementIntent` antes de invocar el libro interno de liquidación. El resultado actualiza siempre la intención; solo un resultado externo exitoso crea un `SettlementRecord` inmutable y, únicamente para liquidación estándar, un `CommissionRecord` inmutable en la misma transacción. Operaciones rechazadas, canceladas, expiradas o pendientes no crean registros exitosos [SPEC RF-010..RF-014].

Enfoque técnico: servicio Spring Boot único con arquitectura hexagonal (`domain` / `application` / `infrastructure`), puertos internos consumidos por UC07/UC08, libro interno simulado de liquidaciones y outbox/worker para reintentos. UC10 es dueño de `settlement_intent`, `settlement_record` y `commission_record`; UC12/UC13 solo los consultan. Las piezas de otros planes se referencian por nombre y no se replanifican.

## Actualizaciones del plan

Registro de los cambios introducidos el **2026-10-29** respecto al plan original del **2026-10-09**, cuya versión completa permanece en el historial de Git (commit `b26bcbb`). No se presenta el contenido actualizado como si hubiera formado parte del plan original: cada sección afectada lleva su marcador `[ACT-xx]` y aquí se documenta el valor anterior y el vigente.

La versión original puede recuperarse con `git show b26bcbb:docs/features/010-liquidar-fondos-alquiler/plan.md`; las diferencias de esta actualización se consultan con `git diff b26bcbb -- docs/features/010-liquidar-fondos-alquiler/plan.md`.

| ID | Sección(es) | Original (2026-10-09) → vigente (2026-10-29) | Motivo |
|---|---|---|---|
| **ACT-01** | `Technical Context`, `Project Structure`, `Scale/Scope` y `Estrategia de testing` | Spring Boot 4.1.1; `SettlementService`, `SettlementWorker`, `OutboxMessagingAdapter`, `GatewayWebhookConfig`, `PaymentGatewayAdapter`, `ObservabilityConfig`; «dos puertos de entrada internos» → Spring Boot 4.1.2; cuatro puertos de entrada; `SettlementCommandListener`, `SettlementCommandOutboxAdapter`, `SettlementConfiguration`, `MercadoPagoSettlementAdapter` (sustituido por `SimulatedSettlementGatewayAdapter` en ACT-11); se elimina la recepción/verificación del webhook de UC10 | Alinear el plan con la estructura real de UC05 y con el andamiaje existente. |
| **ACT-02** | Puertos y DTOs | `SettlementCommand` con `IdempotencyKey`; sin publisher de outbox; `GatewaySettlementResult` sin monto confirmado; cuenta destino sin configuración → `SettlementCommand.operationKey: String`; `SettlementCommandPublisher`/`SettlementCommandOutboxAdapter`; `GatewaySettlementResult` con `amount`; `findByIdForUpdate`; `seashare.gateway.settlement-mode` | Congelar firmas implementables y consistentes con UC05/UC07. |
| **ACT-03** | Regla 4, D-UC10-07 y `general-plan` D-30 | Liquidación estándar = alquiler − comisión (el seguro no interviene) → **alquiler − comisión − seguro**, conforme al SPEC 10; D-UC10-12 reemplaza a D-UC10-07 y D-35 reemplaza a D-30 | Respetar la fuente de verdad del SPEC 10. |
| **ACT-04** | Regla 5, T005a y catálogo de estados | `DEPOSIT_DISPUTE` (valor inexistente); `commission_amount`/componentes del registro sin regla por `scope` → `DEPOSIT`; catálogo publicado de `SettlementTrigger`; `commission_amount = 0` en cancelación/depósito; `insurance_amount = 0` en cancelación/depósito y valor de reserva en estándar | Corregir el mapeo de alcances y los valores persistidos. |
| **ACT-05** | Regla 9, T009 y manejo de fallos | Comisión/parámetros ausentes → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` (UC10 no tiene endpoint REST) → registrar el fallo con `FailureRecorderPort` y no enviar; `SettlementStatus.FAILED` como resultado de la solicitud | UC10 es interno y el SPEC pide registro interno, no HTTP. |
| **ACT-06** | Regla 5, T015 y depósito | UC10 aplicaba el CAS del depósito sin UC08 implementado y el comando no expresaba la disposición → UC08 es dueño del CAS; UC10 invoca `OpenDepositTrackingUseCase` con `SETTLEMENT_REQUESTED` solo en `DISPUTE_COMPLETED` | Resolver la exclusión mutua del depósito. |
| **ACT-07** | Reglas 2-5, T010, T011 y contrato de liquidación | Importes desde `reservation_information`; sin persistir `external_reference` de la respuesta; resultado sin referencia definida → `charge_record` con respaldo en `reservation_information`; persistir la referencia externa; libro interno simulado con referencia determinista y conciliación interna (D-UC10-18) | Cerrar la trazabilidad y la conciliación sin depender de una dispersión inexistente. |
| **ACT-08** | `Implementation Phases`, T021, `Notes` y checklist | Tareas y nombres desactualizados; cobertura/ArchUnit sin test propio → T001/T002/T005a/T012a marcadas como hechas y T005 como parcial; T010/T011/T012/T021 reescritas; `Uc10ArchitectureTest`; no se editan contratos ni planes externos desde UC10 | Dejar el plan ejecutable y evitar dependencias entre planes. |

### Actualización de cierre — 2026-10-30

Registro del cierre oficial posterior a la implementación. La revisión previa del 2026-10-29 se conserva en `[ACT-01]`…`[ACT-08]`; esta actualización no reescribe ese historial. La versión anterior del plan puede recuperarse con `git show b26bcbb:docs/features/010-liquidar-fondos-alquiler/plan.md`.

| ID | Sección(es) | Original (2026-10-29) → vigente (2026-10-30) | Motivo |
|---|---|---|---|
| **ACT-09** | `Estado actual del repositorio`, `Implementation Phases`, `Estrategia de testing`, `Notes` y checklist | El estado describía los modelos como stubs y no reconocía servicios, adaptadores ni pruebas; las tareas seguían pendientes → UC10 implementado y verificado; tareas marcadas según evidencia; pendientes de aceptación CE, listener y cobertura específica documentados; se registran las columnas `version` de bloqueo optimista, el uso de `ReservationLockPort` en UC08 y las pruebas adicionales | Cerrar el plan como documentación de cierre sin declarar hechas las pruebas que no existen con el nombre del plan. |
| **ACT-10** | `Implementation Phases` (T013–T020) | Tareas abiertas con nombres de prueba planificados (`ce00X_…`) o con piezas marcadas como «falta» → cada tarea se reescribe con la prueba real que cubre el comportamiento (`SettlementCalculatorTest`, `RequestSettlementServiceTest`, `RegisterSettlementResultServiceTest`, `RefundSettlementPersistenceIntegrationTest`, `SendSettlementToGatewayServiceTest`, `ReconcileSettlementIntentsServiceTest`, `FlywaySchemaTest`) y se marca `[x]`; se retira de T019 la referencia a estados fuera de orden y concurrencia, que no tienen prueba dedicada | Adaptar el seguimiento del plan a la implementación entregada, en lugar de renombrar pruebas ya escritas. |
| **ACT-11** | `Technical Context`, `Estado actual del repositorio`, `Estrategia de testing`, D-UC10-02, D-UC10-18 y T010/T011 | `MercadoPagoSettlementAdapter` y `SettlementWebhookHandler` como integración real con la pasarela → libro interno simulado `SimulatedSettlementGatewayAdapter`, sin manejador propio de webhook, con `IdempotencyKey` UUID v4 por envío, conciliación interna y `STANDARD_SETTLEMENT` como única capacidad; `V1__initial_schema.sql` retira `ADVANCED_PAYMENTS_DISBURSEMENT` (aún no desplegada, sin migración adicional) | La Payments API real no expone dispersión ni marketplace; se elimina la integración ficticia y se conserva el contrato interno de UC10. |

Desviaciones de implementación registradas en este cierre: `refund_intent.version` y `settlement_intent.version` (bloqueo optimista), `ReservationLockPort` en el UC08 mínimo, libro interno simulado con referencia determinista y pruebas adicionales de liquidación, conciliación y persistencia. Las pruebas de aceptación `ce00X_…` de UC10 no existen con ese nombre; su cobertura funcional equivalente está en las pruebas unitarias e integración citadas en la estrategia de testing.

### Actualización de deduplicación — 2026-11-04

| ID | Sección(es) | Original (2026-10-30) → vigente (2026-11-04) | Motivo |
|---|---|---|---|
| **ACT-12** | Esquema (`settlement_intent`), `SettlementIntentRepository`, `RequestSettlementService` y `GatewaySettlementRequest` | `UNIQUE(idempotency_key)` como único índice único y `findByIdempotencyKey` → `settlement_intent.operation_key` + `UNIQUE(reservation_id, operation_key)`; `findByReservationIdAndOperationKey` y `findLatestByReservationIdAndScope`; transición del depósito como condición para crear la intención de `DISPUTE_COMPLETED`; el campo `operationKey` del DTO de pasarela pasa a `idempotencyKey` (D-UC10-19) | Un UUID v4 aleatorio por intento no deduplica y UC07/UC08 usan claves de operación distintas (general-plan D-37). |

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001 | `RequestSettlementUseCase`, commands internos | T005-T008 | T011, T012 |
| RF-002, RF-003, RF-007 | `SettlementCalculator`, `SettlementScope` | T004, T009 | T013 |
| RF-004, RF-005 | `SettlementIntent`, `FrozenParameters` (comisión de la versión referenciada por la reserva) | T004, T009 | T014 |
| RF-006 | rama `DEPOSIT` (`DISPUTE_COMPLETED`) | T009 | T015 |
| RF-008, RF-009 | `SettlementGatewayPort`, `SettlementIntentRepository` | T005, T008, T010 | T016 |
| RF-009A, RF-015, RF-016 | `SettlementRecord`, mapper y persistencia | T010, T017 | T018 |
| RF-010, RF-012, RF-014 | `RegisterSettlementResultUseCase`, transacción de confirmación | T006, T010, T017 | T019, T020 |
| RF-011 | registros publicados en vista financiera | T017 | T018 |
| RF-013 | validación de información interna + `FailureRecorderPort` | T009 | **T017a `rf013_…`** |
| RNF-001 | DTOs de comando y resultado de pasarela | T006, T007 | T016, T019 |
| RNF-002 | `BigDecimal`, `Money`, `SettlementCalculator` | T004, T009 | **T013 `ce001_…`** |
| RNF-003 | outbox, timeout, retry y conciliación (sin webhook: el libro interno no notifica) | T003, T006, T010 | **T020 `ce004_…`** |
| CE-001 | calculadora por alcance | T004, T009 | **T013 `ce001_…`** |
| CE-002 | regla de comisión por alcance | T009, T017 | **T014 `ce002_…`** |
| CE-003 | intención → registro + vista | T008, T017 | **T016 `ce003_…`** |
| CE-004 | ACL/outbox | T003, T006, T010 | **T020 `ce004_…`** |
| CE-005 | estados externos no exitosos | T010, T017 | **T017 `ce005_…`** |
| CE-006 | creación atómica de comisión | T017 | **T017 `ce006_…`** |
| HU1 | compensaciones por cancelación | T009 | T013, T014 |
| HU2 | liquidación estándar | T009, T017 | T013, T014, T020 |
| HU3 | depósito por disputa | T009 | T015 |
| HU4 | resultado de pasarela | T006, T010 | T019, T021 |
| HU5 | comisión independiente | T017 | T020 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.2 (parent del `pom.xml`); Spring Web MVC, Validation, Data JPA (Hibernate), AMQP, Actuator, Flyway, Resilience4j, Micrometer/Prometheus, ArchUnit y ShedLock ya declaradas. UC10 usa especialmente JPA, AMQP, Testcontainers y Awaitility; el cliente HTTP de pasarela, WireMock y el webhook compartido pertenecen a UC05/UC09. El mapeo de persistencia es manual, siguiendo la convención de UC05/UC11. Este plan no modifica `pom.xml`. (Sin *starter* de seguridad: fuera de alcance, general-plan §7.1.) **[ACT-01] [ACT-11]**
**Storage**: PostgreSQL 16+, `NUMERIC(18,4)` para importes y `timestamptz` para instantes. UC10 es dueño de `settlement_intent`, `settlement_record` y `commission_record`, creadas por la migración inicial única (general-plan §4.2, D-31); lee `charge_record` como fuente autoritativa del desglose (con respaldo en `reservation_information`) y `charge_intent` por puertos de sus dueños [general-plan §4]. **[ACT-07]**
**Messaging**: RabbitMQ 3.13+ con outbox, colas quorum, confirmaciones de publicador y DLQ; los comandos de liquidación se envían desde el worker al libro interno simulado; el webhook compartido pertenece a UC05/UC09 [general-plan §5; contratos externos].
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers PostgreSQL/RabbitMQ, WireMock (solo UC05/UC09; UC10 no usa HTTP), Awaitility y ArchUnit [CONV].
**Target Platform**: contenedores Docker sobre Linux; desarrollo con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: Sin meta propia: el SPEC 10 no fija SLA numérico; rigen el timeout de pasarela (10 s, general-plan §7.2), los reintentos del worker y el procesamiento idempotente; un timeout nunca se interpreta como éxito [CONV].  
**Constraints**: `BigDecimal` a 4 decimales; la comisión aplicada proviene de la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`) y se conserva en la intención; cancelaciones y depósito no crean comisión; registros financieros inmutables; solo confirmación exitosa crea `SettlementRecord`; sin datos asumidos ni registros exitosos parciales; sin datos sensibles de pago [SPEC RNF-002/003; general-plan §2].  
**Scale/Scope**: cuatro puertos de entrada internos (`RequestSettlementUseCase`, `SendSettlementToGatewayUseCase`, `RegisterSettlementResultUseCase`, `ReconcileSettlementIntentsUseCase`), un adaptador de libro interno, sin webhook propio, tres tablas propietarias, 16 RF + 3 RNF + 6 CE + 5 HU [SPEC 10]. **[ACT-01] [ACT-11]**

**Estado actual del repositorio**: existen las capas `domain`/`application`/`infrastructure`, la migración inicial única `V1__initial_schema.sql`, las reglas `ArchitectureTest`/`UcNNArchitectureTest` y los casos de uso UC05/UC06. UC10 está implementado: `SettlementIntent`, `SettlementRecord`, `CommissionRecord`, `SettlementCalculator` y `SettlementCalculation`; los cuatro puertos de entrada y los puertos de salida (`SettlementIntentRepository`, `SettlementRecordRepository`, `CommissionRecordRepository`, `SettlementGatewayPort`, `SettlementCommandPublisher`); `RequestSettlementService`, `SendSettlementToGatewayService`, `RegisterSettlementResultService` y `ReconcileSettlementIntentsService`; persistencia JPA con bloqueo optimista y `findByIdForUpdate`; `SimulatedSettlementGatewayAdapter` (libro interno con referencia determinista `sim-settlement-<idempotency_key>`), `SettlementCommandOutboxAdapter`, `SettlementCommandListener` y `SettlementConfiguration`. El webhook `payment` es compartido y no tiene manejador propio de UC10, porque la liquidación simulada no reporta resultados externos. `V1__initial_schema.sql` contiene `version` en `settlement_intent`, las tablas de registro/comisión y deja `STANDARD_SETTLEMENT` como única capacidad. Las pruebas incluyen calculadora, solicitud, envío, registro, conciliación, persistencia Testcontainers, UC08 mínimo y `Uc10ArchitectureTest`; `.\mvnw.cmd verify` terminó en verde. Quedan pendientes las pruebas de aceptación `ce00X_…` con esos nombres, `SettlementCommandListenerTest`, las pruebas de concurrencia/duplicados y una puerta JaCoCo específica de UC10. **[ACT-01] [ACT-02] [ACT-09] [ACT-11]**

## Project Structure

### Documentation

```text
docs/features/010-liquidar-fondos-alquiler/
├── plan.md
└── spec.md
docs/technical-plan/contracts/external/
├── pasarela-comando-liquidacion.md
```

### Source Code

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java
├── domain/
│   ├── model/{SettlementIntent,SettlementRecord,CommissionRecord}.java
│   ├── valueobject/{SettlementScope,SettlementStatus,SettlementTrigger}.java  # Money/IdempotencyKey son compartidos
│   ├── service/SettlementCalculator.java
│   └── exception/DomainException.java (las excepciones de dominio de UC10 son las compartidas)
├── application/
│   ├── port/in/
│   │   ├── RequestSettlementUseCase.java          # UC07/UC08 lo consumen
│   │   ├── RegisterSettlementResultUseCase.java   # webhook/worker
│   │   ├── SendSettlementToGatewayUseCase.java    # worker de envío (núcleo compartido)
│   │   └── ReconcileSettlementIntentsUseCase.java # job de conciliación (núcleo compartido)
│   ├── port/out/
│   │   ├── SettlementGatewayPort.java             # puerto del libro interno de liquidaciones
│   │   ├── SettlementIntentRepository.java        # incluye findByIdForUpdate
│   │   ├── SettlementRecordRepository.java        # incluye findBySettlementIntentId
│   │   ├── CommissionRecordRepository.java        # incluye findBySettlementRecordId
│   │   ├── SettlementCommandPublisher.java        # outbox [ACT-02]
│   │   ├── ChargeIntentRepository.java            # dueño UC05
│   │   ├── ChargeRecordQueryPort.java             # dueño UC05
│   │   ├── ReservationInformationRepository.java  # dueño UC03
│   │   ├── FinancialParametersRepository.java     # dueño UC11
│   │   ├── FailureRecorderPort.java               # puerto compartido
│   │   ├── ReservationLockPort.java               # puerto compartido
│   │   └── OutboxPort.java                        # puerto compartido
│   ├── service/
│   │   ├── RequestSettlementService.java
│   │   ├── SendSettlementToGatewayService.java
│   │   ├── RegisterSettlementResultService.java
│   │   └── ReconcileSettlementIntentsService.java
│   └── dto/
│       ├── SettlementCommand.java
│       ├── SettlementRequestResult.java           # firma congelada [CONV núcleo]
│       ├── GatewaySettlementRequest.java
│       └── GatewaySettlementResult.java           # status, externalId, statusDetail, amount [ACT-02]
└── infrastructure/
    ├── adapter/in/messaging/
    │   ├── SettlementCommandListener.java         # consume finance.settlement-command.v1
    │   └── dto/SettlementCommandMessage.java      # {settlement_intent_id}
    ├── adapter/out/gateway/
    │   └── SimulatedSettlementGatewayAdapter.java  # libro interno simulado
    ├── adapter/out/messaging/
    │   └── SettlementCommandOutboxAdapter.java    # [ACT-02]
    ├── adapter/out/persistence/settlement/
    │   ├── {SettlementIntentJpaEntity,SettlementRecordJpaEntity,CommissionRecordJpaEntity}.java
    │   ├── {SettlementIntentJpaRepository,SettlementRecordJpaRepository,CommissionRecordJpaRepository}.java
    │   ├── SettlementPersistenceMapper.java       # mapeo manual
    │   └── SettlementPersistenceAdapter.java
    └── config/
        └── SettlementConfiguration.java           # wiring de servicios y ACL

src/main/resources/db/migration/
└── V1__initial_schema.sql # migración inicial única; contiene settlement_intent, settlement_record y commission_record (general-plan §4.2, D-31)

src/test/java/com/seashare/seasharem3/
├── domain/model/{SettlementIntentTest,SettlementRecordTest,CommissionRecordTest}.java
├── domain/service/SettlementCalculatorTest.java
├── application/service/{RequestSettlementServiceTest,SendSettlementToGatewayServiceTest,RegisterSettlementResultServiceTest,ReconcileSettlementIntentsServiceTest}.java
├── infrastructure/adapter/in/messaging/SettlementCommandListenerTest.java
├── infrastructure/adapter/out/gateway/SimulatedSettlementGatewayAdapterTest.java   # pendiente de implementar
├── integration/RefundSettlementPersistenceIntegrationTest.java
├── arch/Uc10ArchitectureTest.java
└── acceptance/Uc10AcceptanceTest.java
``` **[ACT-01] [ACT-02]**

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4. UC10 no implementa receptor de webhook: su liquidación es un libro interno simulado y la conciliación es interna. Define sus puertos `SendSettlementToGatewayUseCase`/`ReconcileSettlementIntentsUseCase` (núcleo compartido) y `RegisterSettlementResultUseCase` para el resultado. `domain` no depende de Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; adaptadores de entrada invocan `application.port.in`; adaptadores de salida implementan `application.port.out`.

**Firmas referenciadas**: UC10 publica `RequestSettlementUseCase` para UC07 y UC08, y define `SendSettlementToGatewayUseCase`/`ReconcileSettlementIntentsUseCase` (núcleo compartido) y `RegisterSettlementResultUseCase` para el resultado interno. `SettlementCommand` incluye `trigger`, `scope`, `dispute_id` cuando aplica y `operationKey: String`, homogéneo con UC05/UC09. Consume `ChargeIntentRepository`, `ChargeRecordQueryPort`, `ReservationInformationRepository`, `FinancialParametersRepository`, `FailureRecorderPort`, `ReservationLockPort` y `OutboxPort` por nombre. Publica `SettlementCommandPublisher` para el outbox y `GatewaySettlementResult(status, externalId, statusDetail, amount)` como DTO de entrada del resultado. **[ACT-02]**

## Reglas de negocio

1. **Entrada interna sin montos externos** [SPEC RF-001]: UC07/UC08 entregan reserva y desencadenante; los montos se recuperan internamente.
2. **Cancelación moderada** [SPEC RF-002, RF-007]: `0.50 × rental_amount`, sin comisión ni seguro; el depósito de la reserva se conserva asociado en el registro. **[ACT-04]**
3. **Cancelación tardía** [SPEC RF-003, RF-007]: `1.00 × rental_amount`, sin comisión ni seguro; el depósito de la reserva se conserva asociado en el registro. **[ACT-04]**
4. **Estándar** [SPEC RF-005, RF-014]: liquidación = **alquiler bruto − comisión − seguro náutico**; la comisión se calcula con el % de la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`) y el seguro es el monto registrado en la reserva. La decisión **D-35** reemplaza a D-30 y **D-UC10-12** reemplaza a D-UC10-07. **[ACT-03]**
5. **Depósito por disputa** [SPEC RF-006]: el `scope` es `DEPOSIT` (no `DEPOSIT_DISPUTE`) y `dispute_id` identifica la liquidación del 100 % del depósito interno, sin comisión ni seguro. Solo el trigger `DISPUTE_COMPLETED` marca el depósito como `SETTLEMENT_REQUESTED`; la liquidación estándar mantiene el depósito asociado sin disponerlo. **[ACT-04] [ACT-06]**
6. **Intención antes de liquidar** [SPEC RF-009]: registrar desencadenante, alcance, monto, comisión si aplica, depósito, cobro original, capacidad y `idempotency_key` (UUID v4) antes de enviar al libro interno. La identidad de negocio que decide si hay un intento nuevo se conserva como `reservation_id` + `status` + tipo de operación + `operationKey` (D-UC10-10). **[ACT-02]**
7. **Libro interno simulado** [SPEC RF-008; contrato de liquidación]: UC10 no dispersa fondos en Mercado Pago. `SettlementGatewayPort.send(GatewaySettlementRequest)` recibe reserva, monto neto y clave idempotente; `SimulatedSettlementGatewayAdapter` devuelve una referencia determinista `sim-settlement-<idempotency_key>` y el estado `approved`. La capacidad persistida es `STANDARD_SETTLEMENT`; no existen cuentas destino, comisiones de pasarela ni credenciales de marketplace. El split real queda como fase futura no implementada (D-UC10-18). **[ACT-04] [ACT-11]**
8. **Confirmación** [SPEC RF-010, RF-012]: el resultado aprobado del libro interno crea `SettlementRecord` inmutable; `pending`, `rejected`, `cancelled`, `expired` solo actualizan la intención. El mapeo de estados externos se conserva para una futura implementación real: `approved → COMPLETED`, `in_process/pending → IN_PROCESS`, `rejected → REJECTED`, `cancelled → CANCELLED`, `expired → EXPIRED`; un estado desconocido no altera la intención y se registra. **[ACT-04]**
9. **Comisión** [SPEC RF-004, RF-014]: comisión = % de la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`, resuelta vía `ReservationInformationRepository.findByReservationId`) × alquiler bruto; se conserva en la intención. Solo aplica a `scope = STANDARD`; cancelaciones y depósito guardan `commission_amount = 0`. Si faltan la comisión o los parámetros en una liquidación estándar, UC10 **registra el fallo con `FailureRecorderPort` y no crea intención ni envía al libro interno** (no hay `503`: UC10 no tiene endpoint REST). 4 decimales internos y salidas `HALF_UP` a 2 decimales (`contracts/README.md` §3.1). **[ACT-03] [ACT-05]**
10. **Trazabilidad** [SPEC RF-009A, RF-015, RF-016]: registros incluyen intención, reserva, propietario, embarcación, bruto, seguro, depósito, monto confirmado, detalle, referencia externa y fecha de creación. `settlement_record.insurance_amount` es 0 en cancelación/depósito y el seguro de la reserva en la liquidación estándar; `deposit_amount` conserva siempre el depósito asociado. **[ACT-04]**
11. **Faltantes** [SPEC RF-013]: alquiler/seguro/depósito requerido ausente → registrar fallo, no calcular parcialmente ni enviar al libro interno.
12. **Fallo del libro interno** [SPEC RNF-003; contrato de liquidación]: el libro simulado no produce rechazos ni timeouts en esta entrega. El worker conserva la clasificación para una implementación futura: un `GatewayCommunicationException` deja la intención en `COMMUNICATION_ERROR` y relanza para que la cola reintente con la misma clave UUID v4; un `GatewayBusinessRejectionException` la deja en `REJECTED` sin reintento; una respuesta sin referencia externa se trata como fallo transitorio. **[ACT-02] [ACT-04] [ACT-11]**
13. **Conciliación interna** [D-UC10-18]: UC10 no registra un webhook de liquidación. `ReconcileSettlementIntentsUseCase` republica las intenciones `PENDING_SEND` sin referencia, repara con la referencia determinista las que ya la tienen y registra una sola vez en `operational_failure` las que avanzaron sin referencia. **[ACT-07] [ACT-11]**
14. **Inmutabilidad** [SPEC RF-010..RF-014; general-plan §2]: registros confirmados nunca se actualizan ni eliminan.
15. **Origen del monto y referencia externa** [SPEC RF-001; UC03 D-UC03-02]: la fuente autoritativa del desglose es `charge_record`; si el cobro no tiene desglose se usa `reservation_information` exigiendo `isCalculated()`. Antes de registrar el resultado se persiste en `settlement_intent.external_reference` la referencia devuelta por el libro interno (`sim-settlement-<idempotency_key>`), que la conciliación usa para reparar un registro incompleto. **[ACT-07] [ACT-11]**

## Contratos

Fuentes: `external/pasarela-comando-liquidacion.md`; convenciones en `contracts/README.md` §3.2–§4.4.

### Puerto interno

`RequestSettlementUseCase` es llamado por UC07 y UC08. Su firma final se publica en este plan y se copia en los planes consumidores:

```java
SettlementRequestResult request(SettlementCommand command);

public record SettlementCommand(
    ReservationId reservationId,
    SettlementTrigger trigger,   // COMPLETED | CANCELLED_MODERATE | CANCELLED_STRICT | DISPUTE_COMPLETED
    SettlementScope scope,       // STANDARD | CANCELLATION_COMPENSATION | DEPOSIT
    UUID disputeId,              // nullable; solo con DISPUTE_COMPLETED
    String operationKey          // identidad de operación de UC07/UC08
) {}

// [CONV núcleo, congelado] composición mínima: intención registrada + estado
public record SettlementRequestResult(UUID settlementIntentId, SettlementStatus status) {}
```

El comando debe expresar reserva, desencadenante/alcance, idempotency key y referencias internas; no acepta un monto arbitrario de Reservas. El adaptador recupera y calcula los importes desde registros internos. Si la solicitud no puede registrarse por datos faltantes, UC10 registra el fallo y devuelve `SettlementRequestResult(null, SettlementStatus.FAILED)`; `FAILED` es un estado de resultado y no se persiste como intención. **[ACT-02] [ACT-05]**

El resultado del libro interno se registra con `RegisterSettlementResultUseCase.register(GatewaySettlementResult)`, siendo `GatewaySettlementResult(String status, String externalId, String statusDetail, Money amount)` el único DTO de entrada [CONV núcleo, congelado]. `statusDetail` y `amount` son nullable; en ese caso el registro usa el monto de la intención y deja el detalle nulo. `SendSettlementToGatewayUseCase.send(UUID settlementIntentId)` y `ReconcileSettlementIntentsUseCase.reconcile()` son los puertos propios del worker y del job de conciliación. **No existe** `SettlementResultCommand`. **[ACT-02]**

### Comando al libro interno simulado

```java
GatewaySettlementResult send(GatewaySettlementRequest request);

public record GatewaySettlementRequest(
    UUID reservationId,
    Money amount,
    String idempotencyKey    // UUID v4 persistido en settlement_intent.idempotency_key (D-UC10-19)
) {}
```

No hay endpoint HTTP ni credenciales: `SimulatedSettlementGatewayAdapter` devuelve
`status = approved`, `externalId = sim-settlement-<idempotency_key>` y
`statusDetail = simulated internal settlement`. El monto neto se calcula antes, en el dominio, y se
conserva en la intención y en el registro. La capacidad es siempre `STANDARD_SETTLEMENT`.

### Conciliación interna

No existe una consulta remota ni un webhook de liquidación que UC10 deba procesar. La conciliación
republica una intención `PENDING_SEND` sin referencia, repara con el resultado determinista una
intención que ya tiene referencia y registra una sola vez las intenciones que avanzaron sin
referencia. Una intención `COMPLETED` con su `settlement_record` se omite. **[ACT-11]**

## Estrategia de testing

Cobertura objetivo [CONV]: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Cobertura | Ubicación |
|---|---|---|
| Unitario dominio | porcentajes, alcances, comisión, seguro, depósito, escala 4 | `domain/service/SettlementCalculatorTest` |
| Unitario aplicación | intención, faltantes, idempotencia, puertos, envío al libro simulado, estados y conciliación interna | `application/service/{RequestSettlementServiceTest,SendSettlementToGatewayServiceTest,RegisterSettlementResultServiceTest,ReconcileSettlementIntentsServiceTest}` |
| Contrato interno | comando de liquidación, `IdempotencyKey` UUID v4 y referencia determinista del libro | `SendSettlementToGatewayServiceTest` |
| Integración | PostgreSQL, persistencia, comisión y CAS del depósito | `RefundSettlementPersistenceIntegrationTest`, `FlywaySchemaTest` y `Uc10AcceptanceTest` |
| Resiliencia | fallo del libro simulado, reintento y conciliación interna | `SendSettlementToGatewayServiceTest`, `ReconcileSettlementIntentsServiceTest` |
| Arquitectura | reglas §3.4 e inmutabilidad; UC10 no depende de `RefundIntent`/`RefundRecord` | `arch/Uc10ArchitectureTest` **[ACT-08]** |

**Estado de las pruebas**: implementadas las pruebas unitarias, de persistencia (`RefundSettlementPersistenceIntegrationTest`), del listener (`SettlementCommandListenerTest`) y de Arquitectura. También existen `SimulatedSettlementGatewayAdapterTest` (referencia determinista y estado `approved`) y `Uc10AcceptanceTest` (`ce001_…` … `ce006_…`). **[ACT-09] [ACT-11]**

- **`ce001_precision_financiera_100_casos`**: ≥100 casos para cancelaciones, estándar y depósito; exactitud `BigDecimal` y escala 4.
- **`ce002_comision_por_alcance`**: cero comisión en cancelación/depósito; estándar usa la comisión de la versión referenciada por la reserva.
- **`ce003_intencion_a_registro`**: toda solicitud queda en intención y todo éxito genera registro trazable.
- **`ce004_fallas_del_libro_interno_sin_estado_indefinido`**: fallo del libro interno y respuesta pendiente dejan la intención conciliable, nunca un éxito falso.
- **`ce005_fallo_no_es_exito`**: rejected/cancelled/expired/pending no crean `SettlementRecord`.
- **`ce006_una_comision_atomica`**: estándar confirmado crea exactamente un `SettlementRecord` y un `CommissionRecord` relacionados.

Ninguna de estas seis pruebas de aceptación existe con ese nombre en el repositorio; su cobertura funcional equivalente está en `SettlementCalculatorTest`, `RequestSettlementServiceTest`, `SendSettlementToGatewayServiceTest`, `RegisterSettlementResultServiceTest`, `ReconcileSettlementIntentsServiceTest` y `RefundSettlementPersistenceIntegrationTest`. **[ACT-09] [ACT-11]**

## Decisiones de diseño (D-UC10-xx)

Registro de las decisiones de diseño de este plan, con su contexto y estado. Los IDs `D-UC10-xx` son propios de este plan; los `D-xx` sin prefijo UC10 son decisiones del plan general (§9).

| ID | Descripción | Contexto | Decisión | Estado |
|---|---|---|---|---|
| **D-UC10-01** | Firmas del puerto publicado por UC10 para UC07/UC08 | Auditoría de planes | UC10 publica `RequestSettlementUseCase` aquí y copia su firma en UC07/UC08; cambios externos no se aplican | Aplicada |
| **D-UC10-02** | La liquidación podía requerir una operación externa que unificara el depósito | contrato de liquidación vs integración real | El componente de depósito se conserva siempre en la intención y en el registro; el libro interno no dispersa fondos ni necesita una operación relacionada. La composición del monto se mantiene como dato auditable | Aplicada **[ACT-11]** |
| **D-UC10-03** | El webhook es compartido por UC05/UC09/UC10 | contrato webhook compartido | UC10 **no** consume webhook: su liquidación es interna y se concilia con `ReconcileSettlementIntentsUseCase`. El topic `payment` queda para UC05/UC09 | Aplicada **[ACT-11]** |
| **D-UC10-04** | Migraciones de UC10 | general-plan §4.2 y D-31 | Migración inicial única `V1__initial_schema.sql` (general-plan D-31); UC10 no define ni ordena migraciones propias | Aplicada |
| **D-UC10-05** | Mecanismo transversal de autenticación/autorización | general-plan §7.1 y D-21 | Seguridad fuera de alcance: sin autenticación ni autorización; UC10 no define ni hereda configuración de seguridad | Aplicada |
| **D-UC10-06** | Campos del pagador y metadatos exigidos por la pasarela | contrato de liquidación vs contrato de cobro | UC10 **no** envía `payer_email`, `payment_method_id`, `installments` ni `description`: son campos del contrato de cobro (UC05). El comando al libro interno solo lleva reserva, monto neto y clave idempotente | Aplicada **[ACT-04] [ACT-11]** |
| **D-UC10-07** | Tratamiento del seguro en la base de liquidación | SPEC 10 RF-004/RF-014 | **Reemplazada por D-UC10-12**: el seguro sí se descuenta, conforme al SPEC 10 | Reemplazada |
| **D-UC10-08** | Cálculo, base y ausencia de comisión | SPEC 10 RF-004/RF-014 | Comisión = % de la versión referenciada × alquiler bruto, solo para `scope = STANDARD`; en cancelación/depósito `commission_amount = 0`. Si faltan comisión o parámetros, se registra el fallo con `FailureRecorderPort` y no se crea intención ni se invoca el libro interno (sin `503`) | Aplicada **[ACT-05]** |
| **D-UC10-09** | Escala y redondeo | `contracts/README.md` §3.1 | 4 decimales internos; salidas `HALF_UP` a 2 decimales | Aplicada |
| **D-UC10-10** | Idempotencia de la operación | general-plan D-33 | La identidad de negocio es `reservation_id` + `status` + tipo de operación + `operationKey`; la clave persistida en `settlement_intent.idempotency_key` es un **UUID v4** generado una vez al crear la intención. Los reintentos técnicos reutilizan la clave y un `status_changed_at` nuevo es un intento nuevo. La deduplicación no busca por `findByIdempotencyKey` | Aplicada **[ACT-02]** |
| **D-UC10-11** | Parámetros congelados frente a globales | general-plan D-24/D-27 | UC10 usa la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`) como fuente autoritativa para reservas ya calculadas (los valores aplicados —comisión %— se resuelven de ella, sin copiarse en la reserva); una nueva versión de `financial_parameters` nunca altera cálculos ya realizados | Aplicada |
| **D-UC10-12** | Fórmula de la liquidación estándar | SPEC 10 RF-005, HU2 y caso extremo vs general-plan D-30 | Liquidación estándar = **alquiler bruto − comisión − seguro náutico**. Reemplaza a D-UC10-07 y a D-30 (D-35 en general-plan) | Aplicada **[ACT-03]** |
| **D-UC10-13** | Valor de la comisión y de los componentes del registro por alcance | V1 `commission_amount NUMERIC(18,4) NOT NULL`; `settlement_record` con componentes `NOT NULL` | `commission_amount = 0` en cancelación/depósito y `> 0` solo en estándar; `insurance_amount = 0` en cancelación/depósito y el seguro de la reserva en estándar; `rental_gross_amount` = alquiler de la reserva; `deposit_amount` = depósito asociado siempre | Aplicada **[ACT-04]** |
| **D-UC10-14** | Fallo por datos internos incompletos | SPEC 10 RF-013 y caso extremo; contrato de cola | Se registra en `operational_failure` y se devuelve `SettlementRequestResult(null, FAILED)`; no se crea intención ni se invoca el libro interno. No se usa `503` porque UC10 no tiene endpoint REST | Aplicada **[ACT-05]** |
| **D-UC10-15** | Origen del desglose de importes | SPEC 10 RF-001 vs UC03 D-UC03-02 | `charge_record` es la fuente autoritativa; `reservation_information` es solo respaldo si el cobro no tiene desglose y exige `isCalculated()` | Aplicada **[ACT-07]** |
| **D-UC10-16** | DTOs y publicación del comando | Andamiaje real de UC10 | `SettlementCommand.operationKey: String`; `GatewaySettlementResult` incluye `amount`; `SettlementCommandPublisher`/`SettlementCommandOutboxAdapter` publican en `finance.settlement-command.v1`; `findByIdForUpdate` evita carreras entre webhook y conciliación | Aplicada **[ACT-02]** |
| **D-UC10-17** | Desenlace del depósito | general-plan D-34 vs UC08 no implementado | UC08 es dueño del CAS. UC10 invoca `OpenDepositTrackingUseCase` solo con `DISPUTE_COMPLETED` y `SETTLEMENT_REQUESTED`; la liquidación estándar y las cancelaciones no disponen el depósito | Aplicada **[ACT-06]** |
| **D-UC10-18** | La integración real no expone una dispersión en la Payments API | SPEC RF-008/RF-010 vs integración real de Mercado Pago | UC10 usa un **libro interno simulado**: `SettlementGatewayPort.send` devuelve una referencia determinista `sim-settlement-<idempotency_key>` y estado `approved`, sin mover fondos ni usar cuentas de marketplace. La capacidad es `STANDARD_SETTLEMENT`; `seashare.gateway.settlement-mode` solo admite `simulated` y cualquier otro valor impide arrancar. La conciliación es interna. El marketplace real queda como fase futura no implementada | **Aplicada [ACT-11]** |
| **D-UC10-19** **[ACT-12]** | Una reentrega del comando o el mismo desenlace del depósito reclamado por UC07 y por UC08 podía generar una segunda liquidación | general-plan D-33/D-34 vs D-37; el esquema solo tenía `UNIQUE(idempotency_key)` (UUID v4 aleatorio) | `settlement_intent.operation_key` con `UNIQUE(reservation_id, operation_key)`: si ya existe una intención de la misma operación, `RequestSettlementService` devuelve `SettlementRequestResult(existing.id(), existing.status())`. Para `DISPUTE_COMPLETED` (alcance `DEPOSIT`) solo se crea la intención si esta solicitud aplicó la transición del depósito (`DepositTrackingResult.transitioned`); si otra operación ya la aplicó, se devuelve la intención `DEPOSIT` más reciente (no `FAILED`) y se registra `duplicate_deposit_outcome=SETTLEMENT_REQUESTED` en `operational_failure`. La referencia `sim-settlement-<idempotency_key>` se deriva del UUID v4 del intento, no de `operation_key`; el campo del DTO `GatewaySettlementRequest` se renombra a `idempotencyKey` | **Aplicada** |

## Implementation Phases

### Phase 1: Setup

- [x] **T001** Consumir JPA, AMQP, errores, outbox, observabilidad y transacciones del núcleo compartido. **[ACT-01: ya disponible.]**
- [x] **T002** Publicar la firma de `RequestSettlementUseCase` y documentar consumidores UC07/UC08, sin editar sus planes. **[ACT-01: firma ya publicada.]**
- [x] **T003** Configurar ACL, `SettlementCommandPublisher`/`SettlementCommandOutboxAdapter`, listener y la conciliación interna. **No** existe manejador de webhook propio: UC10 no consume notificaciones de la pasarela. Implementado. **[ACT-01] [ACT-09] [ACT-11]**

### Phase 2: Foundational

- [x] **T004** Crear `SettlementScope`, `SettlementTrigger`, estados y `SettlementCalculator`; `SettlementScope`, `SettlementStatus` y `SettlementTrigger` ya existen; se añade `SettlementStatus.FAILED` para el resultado de una solicitud no registrada. `Money`, `ReservationId`, `OwnerId`, `IdempotencyKey` y el bean `Clock` se consumen del núcleo compartido y no se redeclaran. Implementado. **[ACT-01] [ACT-05] [ACT-09]**
- [x] **T005** Crear puertos, `SettlementCommand`/results y adaptadores de consulta para reserva/cobro; el command incluye `trigger`, `scope`, `dispute_id` cuando aplica y `operationKey: String`. Consume piezas de UC03/UC05 por nombre. Implementado, incluyendo `SettlementCommandPublisher` y `findByIdForUpdate`. **[ACT-02] [ACT-09]**
- [x] **T005a** Congelar la firma pública `SettlementRequestResult request(SettlementCommand)`; `SettlementRequestResult(UUID settlementIntentId, SettlementStatus status)` [CONV núcleo] y `scope` usa el valor vigente del catálogo: `DEPOSIT` cuando existe `dispute_id`, nunca `DEPOSIT_DISPUTE`. No se usa `void` ni se inventa otro valor. **[ACT-04]**
- [x] **T006** Crear `SettlementGatewayPort`, DTOs del comando/resultado e implementar `SimulatedSettlementGatewayAdapter` con referencia determinista; la validación de `settlement-mode` vive en `SettlementConfiguration`. **[ACT-02] [ACT-09] [ACT-11]**
- [x] **T007** Crear modelos de intención/registro/comisión y repositorios; definir restricciones de idempotencia e inmutabilidad. Implementado. **[ACT-01] [ACT-09]**
- [x] **T008** Implementar persistencia de intención antes del envío y consulta de intención para webhook/worker, con `findByIdForUpdate` y `@Version`. **[ACT-02] [ACT-09]**

### Phase 3: User Stories

- [x] **T009** [US1/US2/US3] Implementar cálculo por cancelación, estándar y depósito con validación de datos internos (la comisión estándar usa el % de la **versión de parámetros referenciada** por la reserva y el seguro se descuenta), sin montos externos. Implementado. **[ACT-03] [ACT-09]**
- [x] **T010** [US1/US2/US3] Implementar `RequestSettlementService`, outbox y `SettlementCommandListener`; el envío al libro interno y los reintentos viven en `SendSettlementToGatewayService`. Persistir `external_reference` con la referencia determinista antes de registrar el resultado. Implementado. **[ACT-07] [ACT-09] [ACT-11]**
- [x] **T011** [US4] Implementar la conciliación interna: `SendSettlementToGatewayService` registra el resultado del libro interno y `ReconcileSettlementIntentsUseCase` repara las intenciones con referencia determinista. UC10 no implementa receptor de webhook ni verificador de firma. Implementado. **[ACT-07] [ACT-09] [ACT-11]**
- [x] **T012** [US4/US5] Implementar transacción de confirmación: actualizar intención; en éxito crear `SettlementRecord`; la comisión solo se crea en la liquidación estándar con el % de la versión de parámetros referenciada por la reserva, incluso si el porcentaje es 0. Implementado. **[ACT-04] [ACT-09]**
- [x] **T012a** Verificar `settlement_intent`, `settlement_record` y `commission_record` contra la migración inicial única `V1__initial_schema.sql`, incluyendo claves idempotentes, FKs, `dispute_id`, estados, la capacidad `STANDARD_SETTLEMENT` y las restricciones de inmutabilidad (general-plan §4.2, D-31). **[ACT-04: verificado; `commission_amount` es NOT NULL y se guarda 0 en cancelación/depósito; `V1` fija `STANDARD_SETTLEMENT` como única capacidad, sin migración adicional.]**
- [x] **T013** **CE-001 — precisión financiera**: cubierto por `SettlementCalculatorTest` (casos principales, comisión y escala de 4 decimales); no se añadieron 100 casos parametrizados. **[ACT-09] [ACT-10]**
- [x] **T014** **CE-002 — comisión por alcance**: cubierto por `SettlementCalculatorTest` y `RegisterSettlementResultServiceTest` (comisión solo en `STANDARD`, incluso con 0 %). **[ACT-09] [ACT-10]**
- [x] **T015** Depósito total y trazabilidad con la disputa de UC08 (`DEPOSIT`, `OpenDepositTrackingUseCase` con `SETTLEMENT_REQUESTED`): cubierto por `RequestSettlementServiceTest` y `RefundSettlementPersistenceIntegrationTest`. **[ACT-04] [ACT-06] [ACT-09] [ACT-10]**
- [x] **T016** **CE-003 — intención a registro**: cubierto por `RefundSettlementPersistenceIntegrationTest` (registro enlazado a la intención) y `RegisterSettlementResultServiceTest`. **[ACT-09] [ACT-10]**
- [x] **T017** **CE-005 — fallo no es éxito** y **CE-006 — una comisión atómica**: cubiertos por `RegisterSettlementResultServiceTest` (rechazo sin registro y comisión creada junto al registro). **[ACT-09] [ACT-10]**
- [x] **T017a** **RF-013 — información interna incompleta registra fallo sin llamada**: cubierto por `RequestSettlementServiceTest` (sin cobro aprobado → `SettlementRequestResult(null, FAILED)` y fallo registrado, sin cálculo parcial). **[ACT-05] [ACT-09] [ACT-10]**

### Phase 4: Resiliencia y Polish

- [x] **T018** Integración con PostgreSQL/RabbitMQ y disponibilidad para UC12/UC13 mediante la vista financiera: cubierto por `RefundSettlementPersistenceIntegrationTest` y `FlywaySchemaTest` (`v_financial_records`: columnas, tipos y consulta por `reservation_id`). **[ACT-09] [ACT-10]**
- [x] **T019** Pruebas de duplicados y registros inmutables: cubierto por `RegisterSettlementResultServiceTest` (confirmación repetida sin duplicar la comisión), `ReconcileSettlementIntentsServiceTest` (referencia determinista y reparación única) y `FlywaySchemaTest` (inmutabilidad). **[ACT-09] [ACT-10] [ACT-11]**
- [x] **T020** **CE-004 — fallos sin estado indefinido**: cubierto por `SendSettlementToGatewayServiceTest` (rechazo y fallo de comunicación), `ReconcileSettlementIntentsServiceTest` y la política AMQP compartida verificada en `AmqpListenerRetryIntegrationTest`. **[ACT-09] [ACT-10] [ACT-11]**
- [x] **T021** `Uc10ArchitectureTest`, cobertura y revisión de contratos; no modificar contratos ni planes externos desde la implementación (la alineación documental se hizo en la Fase 0). `Uc10ArchitectureTest` existe y `.\mvnw.cmd verify` cumple las puertas; desde el 2026-11-01 la puerta JaCoCo de `pom.xml` incluye las clases de UC10 (dominio y aplicación). **[ACT-08] [ACT-09]**

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012 → T013..T021`.

UC10 consume datos/puertos publicados por UC03 y UC05, y expone `RequestSettlementUseCase` para UC07/UC08. UC12/UC13 solo consumen sus registros confirmados. Las tablas de UC10 forman parte de la migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31); UC10 no define migraciones propias.

## Notes

No modificar `pom.xml`, SPEC ni planes de UC03/UC05/UC07/UC08/UC09/UC11/UC12/UC13. La alineación documental de la Fase 0 ya actualizó `general-plan` y los contratos de liquidación/webhook. No implementar transferencias fuera del ACL, estados de éxito para timeouts, comisiones en penalidades/depósitos ni registros mutables. **[ACT-08]**

- **Estado de cierre (2026-11-01)**: UC10 implementado y verificado con `.\mvnw.cmd verify` (**1600 tests, 0 fallos; las 84 pruebas que requieren Docker se omiten solo en entornos sin Docker**). Las pruebas que estaban pendientes ya existen: `SettlementIntentTest`, `SettlementRecordTest`, `CommissionRecordTest`, `SettlementCommandListenerTest` y `Uc10AcceptanceTest` (CE-001…CE-006). La capacidad de liquidación (RF-009) se persiste en `settlement_intent.gateway_capability`. **[ACT-09]**

## Checklist de auto-revisión

- [x] Alineado con SPEC 10, contratos de liquidación/webhook y `contracts/README.md`. **[ACT-03] [ACT-04]**
- [x] Cada RF/RNF/CE/HU tiene componente, tarea y prueba. La trazabilidad existe y `Uc10AcceptanceTest` implementa CE-001…CE-006. **[ACT-09]**
- [x] Intención, registro, comisión, idempotencia e inmutabilidad están trazados. **[ACT-09]**
- [x] Solo éxitos externos crean registros financieros. **[ACT-09]**
- [x] Requisitos de esquema de UC10 verificados contra la migración inicial única (general-plan §4.2, D-31). **[ACT-04]**
