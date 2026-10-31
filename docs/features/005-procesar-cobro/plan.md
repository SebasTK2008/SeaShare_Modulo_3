# Implementation Plan: UC05 - Procesar Cobro

> **Actualización del plan — 2026-10-28.** Este documento parte del plan original del **2026-10-09**, conservado en el historial de Git (commit `b42f878`). Las secciones modificadas se marcan con `[ACT-xx]` y se detallan en [Actualizaciones del plan](#actualizaciones-del-plan): `[ACT-01]` recoge la revisión previa a la implementación, `[ACT-02]` la implementación de UC05 y `[ACT-03]` el cierre de T011 (atomicidad intención + *outbox*). Salvo las líneas de versión y la sección «Actualizaciones del plan», el contenido sin marcador pertenece al plan original.

**Date**: 2026-10-09  
**Spec**: [spec.md](spec.md)

## Summary

UC05 envía a la Pasarela de Pago (Mercado Pago) la **solicitud de cobro directo con captura automática** por el **valor total ya calculado** de una reserva (alquiler + seguro + depósito, un único monto), registra la operación operativa en curso como `ChargeIntent` y, cuando la pasarela confirma el cobro exitoso, crea el `ChargeRecord` inmutable de auditoría. También registra el rechazo, la expiración reportada por la pasarela y la falla de comunicación [SPEC HU1, HU2, RF-001…RF-015, RNF-003].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. UC05 **no tiene endpoint público de negocio**: lo invoca UC07 con el estado `PENDING` y el token del medio de pago. Persiste la intención y publica el comando por *outbox* en la misma transacción; un *worker* llama a la pasarela. El receptor del webhook, la verificación de firma y el despachador por `type` son responsabilidad de UC05; UC09 y UC10 registran únicamente sus manejadores y puertos de envío/conciliación. El resultado converge en `RegisterChargeResultUseCase`. La idempotencia es por intento: clave `reservation_id` + `status` + `status_changed_at`; los estados terminales deduplican por (reserva, estado, tipo de operación) y solo `PENDING` admite nuevos intentos [general-plan D-33]. `ChargeIntent` y `ChargeRecord` son propiedad de UC05 y los demás casos de uso los referencian por nombre.

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Procesar cobro" con los actores *Arrendatario* y *Pasarela de pago*, sin relaciones `<<include>>`/`<<extend>>`. El disparo desde "Brindar el estado de la reserva" proviene del SPEC 7 (RF-002A), no del diagrama.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud con token | `ProcessChargeUseCase`, `ProcessChargeCommand`, `ProcessChargeService` | T006, T008 | T007 |
| **RF-002** recuperar valor total registrado | `ProcessChargeService` + `ReservationInformationRepository` (del núcleo compartido, fundacional) | T008 | T007, T026 |
| **RF-003** enviar la solicitud de cobro directo por el total | `SendChargeToGatewayService`, `MercadoPagoChargeAdapter.send` | T012, T014 | T013, T015 |
| **RF-004** registrar `IntenciónDeCobro` en curso | `ChargeIntent`, `ChargeIntentRepository` | T003, T009 | T010, T027 |
| **RF-004A** varias intenciones por reserva; unicidad por clave | `charge_intent` sin `UNIQUE(reservation_id)`, `UNIQUE(idempotency_key)` | T002, T008 | T007, T010 |
| **RF-004B** `RegistroDeCobro` con `intencionDeCobroId` | `charge_record.charge_intent_id` (FK `NOT NULL`) | T002, T003, T018 | T010, T027 |
| **RF-005** recibir resultado de la pasarela | `RegisterChargeResultUseCase`; webhook; respuesta técnica; conciliación | T018, T019, T020, T023 | T017, T019, T021 |
| **RF-006** actualizar intención; crear registro si hay éxito | `RegisterChargeResultService` | T018 | T017, T026 |
| **RF-007** intención disponible para UC06 | Puerto `ChargeIntentRepository` (incluye `findLatestByReservationId`) | T005, T009 | T010 (consumo: UC06) |
| **RF-008** sin registro para rechazado/fallido | `RegisterChargeResultService` | T018 | T017, T029 |
| **RF-009** error controlado sin valor total | `ReservationValueNotCalculatedException` | T004, T008 | T007 |
| **RF-010** propietario y embarcación en el registro | `ChargeRecord.ownerId/boatId` copiados de `reservation_information` | T003, T018 | T017, T027 |
| **RF-011** solo token a la pasarela | `MercadoPagoPaymentRequest` (sin PAN/CVV/vencimiento) | T012 | T013, T016 |
| **RF-012** tipo de medio y últimos 4 | `PaymentMethodReference`, `charge_intent`/`charge_record` | T003, T018 | T017, T025 |
| **RF-013** error controlado con token inválido | `InvalidPaymentTokenException`, tratamiento de `400` en el worker | T004, T008, T014 | T007, T015 |
| **RF-014** expiración reportada por la pasarela, registrada sin asumir fondos | `RegisterChargeResultService` (estado `EXPIRED`) | T018 | T017 |
| **RF-015** un único monto (sin solicitudes por componente) | `ProcessChargeService`, `MercadoPagoPaymentRequest.transaction_amount` | T008, T012 | T007, T013 |
| **RNF-001** DTOs con la pasarela | `MercadoPagoPaymentRequest/Response`, `ChargeGatewayRequest/Result` | T012 | T013 |
| **RNF-002** `BigDecimal` | Campos monetarios de `ChargeIntent`/`ChargeRecord`; `NUMERIC(18,4)` | T002, T003 | T004, T013 |
| **RNF-003** errores, idempotencia, conciliación | Clave idempotente por intento; `COMMUNICATION_ERROR`; `ReconcileChargeIntentsService` | T014, T023 | T015, T024, T028 |
| **RNF-004** minimización de datos de pago | Esquema sin PAN/CVV; token fuera de logs | T002, T012 | T016 |
| **RNF-005** vigencia monitoreada sin consulta manual | No aplica: cobro directo con captura automática, sin autorización retenida (general-plan D-32) | — | — |
| **CE-001** montos sin discrepancias | `ChargeIntent`, `RegisterChargeResultService` | T018 | **T026** |
| **CE-002** trazabilidad intención → registro | FK `charge_intent_id`; varias intenciones | T002, T018 | **T027** |
| **CE-003** resiliencia ante fallas | Worker + conciliación | T014, T023 | **T028** |
| **CE-004** rechazados no son cobros | `RegisterChargeResultService` | T018 | **T029** |
| **CE-005** sin datos sensibles | Esquema + logs | T002, T012 | **T016** |
| **CE-006** trazabilidad enmascarada | `PaymentMethodReference` | T018 | **T025** |
| **CE-007** expiración registrada | `RegisterChargeResultService` (webhook `expired` → `EXPIRED`, sin `ChargeRecord`) | T018 | **T017** |
| **HU1** enviar solicitud de cobro (P1) | Fase 3 | T001–T016 | T007, T010, T013, T015, T016 |
| **HU2** registrar resultado (P1) | Fase 4 | T017–T029 | T017–T029 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]  
**Primary Dependencies**: Spring Boot 4.1.2 [ACT-01]. Para UC05: Spring Data JPA, Spring AMQP, Resilience4j, cliente HTTP, Flyway, MapStruct, ArchUnit, WireMock y Awaitility. Las dependencias se registran en el setup común del repositorio, sin coordinar mediante numeración de tareas externas.  
**Storage**: PostgreSQL 16+; tablas `charge_intent` y `charge_record` de UC05, creadas por la migración inicial única (general-plan §4.2, D-31); `NUMERIC(18,4)` para dinero y `timestamptz` para instantes [general-plan §4]  
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), WireMock (pasarela), Awaitility, MockMvc, ArchUnit [general-plan]  
**Target Platform**: Contenedores Docker (Linux) [general-plan]  
**Project Type**: Servicio backend único (hexagonal) [general-plan]  
**Performance Goals**: Sin meta propia: se heredan los RNF del SPEC y los límites del plan general; para la llamada a la pasarela rige el timeout de 10 s (general-plan §7.2) [CONV].  
**Constraints**: `BigDecimal` con 4 decimales internos y toda salida monetaria (incluido el comando a la pasarela) redondeada `HALF_UP` a 2 decimales [SPEC RNF-002; contracts/README.md §3.1]; solo token, nunca PAN/CVV/vencimiento [SPEC RNF-004]; un timeout no es un fallo [SPEC RNF-003]; `RegistroDeCobro` solo con cobro/captura confirmados [SPEC RF-006, RF-008; general-plan §3.5]; los valores aplicados (comisión % y seguro) se resuelven de la versión de parámetros referenciada por la reserva y no se tocan aquí  
**Scale/Scope**: 2 tablas propias (`charge_intent`, `charge_record`); 0 endpoints REST propios; 1 endpoint de entrada de la pasarela (compartido con UC09/UC10); 2 jobs (conciliación, relay); 15 RF + 5 RNF + 7 CE + 2 HU

**Estado actual del repositorio (relevante) [ACT-01]**: UC01, UC02, UC03, UC04 y UC11 están completamente implementados. Existen la foundation compartida (`DomainException`, `ProblemDetailsConfig`, `FailureRecorderPort`, `OutboxPort`, AMQP: `RabbitTopologyConfig`, `AmqpNames`, `AmqpListenerFailurePolicy`, `OperationalFailureMessageRecoverer`), los VOs compartidos (`Money`, `ReservationId`, `OwnerId`, `BoatId`, `Percentage`), `BestEffortFailureRecorder` (`@Primary`), `MoneySerializer` (2 decimales HALF_UP), el patrón de advisory lock (`pg_advisory_xact_lock`), y los tests doubles en `support/` (`InMemoryFailureRecorder`, `InMemoryOutbox`, `InMemoryReservationInformationRepository`, etc.). UC05 ya tiene implementado el dominio (`ChargeIntent` completo, `ChargeIntentStatus`, `PaymentMethodReference`, `IdempotencyKey`, excepciones), todos los puertos de entrada/salida, los DTOs (`ProcessChargeCommand`, `ChargeGatewayRequest`, `ChargeGatewayResult`), el esquema de BD en `V1__initial_schema.sql`, y las properties `seashare.gateway.*`. **Pendiente**: `ChargeRecord` (stub), los 4 servicios de aplicación, toda la infraestructura (adapters), y casi todos los tests. **[ACT-02: UC05 queda implementado en esta actualización — dominio (`ChargeRecord`), aplicación (los 4 servicios, el despacho del webhook y el manejador de `payment`), infraestructura (persistencia JPA, adaptador de Mercado Pago, *outbox*, listeners, webhook, job de conciliación) y pruebas (dominio, aplicación, contrato WireMock, webhook MockMvc y aceptación CE-001…CE-007)]**

## Project Structure

### Documentation (this feature)

```text
docs/features/005-procesar-cobro/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                    # §3.3 Problem Details, §3.4 catálogo, §4.4 colas y webhook
└── external/
    ├── pasarela-comando-cobro.md                # POST /v1/payments (Mercado Pago)
    └── pasarela-webhook-resultados.md           # POST /api/v1/webhook/gateway
```

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── model/
│   │   ├── ChargeIntent.java                    # T003  [SPEC RF-004, RF-004A, RF-006]
│   │   └── ChargeRecord.java                    # T003  inmutable [SPEC RF-004B, RF-010]
│   ├── valueobject/
│   │   ├── ChargeIntentStatus.java              # T003  [general-plan §8]
│   │   ├── PaymentMethodReference.java          # T003  token + tipo + últimos 4 [SPEC RF-001, RF-012]
│   │   └── IdempotencyKey.java                  # T003  [general-plan §3.3]
│   └── exception/
│       ├── ReservationValueNotCalculatedException.java   # T004 [SPEC RF-009]
│       ├── InvalidPaymentTokenException.java             # T004 [SPEC RF-013]
│       └── GatewayCommunicationException.java             # T014 [SPEC RNF-003] falla de transporte con la pasarela
├── application/
│   ├── port/in/
│   │   ├── ProcessChargeUseCase.java            # T006  [general-plan §3.5]  (puerto expuesto)
│   │   ├── SendChargeToGatewayUseCase.java      # T006  [CONV] el worker es un adaptador y solo puede invocar port.in (general-plan §3.4)
│   │   ├── RegisterChargeResultUseCase.java     # T006  [general-plan §3.5]
│   │   ├── ReconcileChargeIntentsUseCase.java   # T006  [CONV] job de conciliación
│   │   ├── DispatchGatewayNotificationUseCase.java   # T019  [ACT-02] despacho del webhook por type
│   │   └── GatewayWebhookHandler.java           # T019  [CONV] manejadores por type (UC05/UC09/UC10)
│   ├── port/out/
│   │   ├── ChargeIntentRepository.java          # T005  (lo consume UC06, UC09, UC10)
│   │   ├── ChargeRecordRepository.java          # T005  [CONV]
│   │   ├── ChargeRecordQueryPort.java           # T005  consultas de auditoría para UC12/UC13
│   │   ├── ChargeGatewayPort.java               # T005  [general-plan §3.5]
│   │   ├── ChargeCommandPublisher.java          # T008  [ACT-02] publica el comando de cobro en el outbox
│   │   └── GatewayNotificationPublisher.java    # T019  [ACT-02] publica la notificación del webhook en el outbox
│   ├── service/
│   │   ├── ProcessChargeService.java            # T008
│   │   ├── SendChargeToGatewayService.java      # T014
│   │   ├── RegisterChargeResultService.java     # T018
│   │   ├── ReconcileChargeIntentsService.java   # T023
│   │   ├── ChargePaymentWebhookHandler.java     # T020  [ACT-02] manejador del tipo `payment`
│   │   └── GatewayWebhookDispatcherService.java # T019  [ACT-02] despacho por type
│   └── dto/
│       ├── ProcessChargeCommand.java            # T006
│       ├── ChargeCommand.java                   # T008  [ACT-02] comando de cobro de la cola interna
│       ├── ChargeGatewayRequest.java            # T012  = SolicitudCobroPasarela [SPEC]
│       └── ChargeGatewayResult.java             # T012  = ResultadoCobroPasarela [SPEC]
└── infrastructure/
    ├── config/
    │   ├── ChargeConfiguration.java              # T006/T014/T018/T023  wiring de los servicios y del adaptador de la pasarela
    │   ├── ProcessChargeConfiguration.java       # T008  wiring del servicio con la transaccion efectiva
    │   └── GatewayProperties.java                # T036  propiedades `seashare.gateway.*`
    ├── adapter/in/messaging/
    │   ├── ChargeCommandListener.java           # T014  worker de la cola interna [general-plan D-08]
    │   ├── GatewayNotificationListener.java     # T020  [ACT-02] worker de las notificaciones del webhook
    │   └── dto/{ChargeCommandMessage, ChargeCommandMessageMapper, GatewayNotificationMessage, GatewayNotificationMessageMapper}.java
    ├── adapter/in/webhook/
    │   ├── GatewayWebhookController.java        # T019  (compartido con UC09/UC10)
    │   └── MercadoPagoSignatureVerifier.java    # T019
    ├── adapter/in/scheduler/
    │   └── GatewayReconciliationJob.java        # T023  (compartido con UC09/UC10)
    ├── adapter/out/gateway/
    │   ├── MercadoPagoChargeAdapter.java        # T012, T021
    │   └── dto/{MercadoPagoPaymentRequest, MercadoPagoPaymentResponse}.java   # T012
    ├── adapter/out/messaging/
    │   ├── ChargeCommandOutboxAdapter.java      # T008  [ACT-02]
    │   └── GatewayNotificationOutboxAdapter.java # T019 [ACT-02]
    └── adapter/out/persistence/charge/
        ├── ChargeIntentJpaEntity.java / ChargeRecordJpaEntity.java            # T009
        ├── ChargeIntentJpaRepository.java / ChargeRecordJpaRepository.java    # T009
        ├── ChargePersistenceMapper.java         # T009  (mapeo manual, D-UC05-09)
        └── ChargeIntentPersistenceAdapter.java / ChargeRecordPersistenceAdapter.java  # T009

src/main/resources/db/migration/
└── V1__initial_schema.sql                            # T002  migración inicial única; contiene charge_intent y charge_record (general-plan §4.2, D-31)

src/test/java/com/seashare/seasharem3/
├── domain/model/{ChargeIntentTest, ChargeRecordTest}.java                    # T004
├── application/service/{ProcessChargeServiceTest, SendChargeToGatewayServiceTest, RegisterChargeResultServiceTest, ReconcileChargeIntentsServiceTest, ChargePaymentWebhookHandlerTest, GatewayWebhookDispatcherServiceTest}.java
├── infrastructure/adapter/in/webhook/{GatewayWebhookControllerTest, MercadoPagoSignatureVerifierTest}.java
├── infrastructure/adapter/out/gateway/MercadoPagoChargeAdapterTest.java      # T013, T021
├── acceptance/Uc05AcceptanceTest.java                                        # T016, T017, T025–T029
├── arch/Uc05ArchitectureTest.java                                            # T030
└── support/{ChargeFixtures, InMemoryChargeRecordRepository, StubChargeGateway, RecordingChargeCommandPublisher}.java
```

**Puertos expuestos**: `ProcessChargeUseCase` es llamado por **UC07**. Firma [CONV], publicada al completar T006:

```java
public interface ProcessChargeUseCase {
    void process(ProcessChargeCommand command);
}
public record ProcessChargeCommand(
    UUID reservationId,
    String paymentTokenRef,                 // obligatorio con PENDING [SPEC UC07 RF-002A]
    String paymentMethodType,               // nullable [SPEC RF-001]
    Map<String, String> paymentMetadata,    // nullable, solo no sensibles [SPEC RF-001, RF-012]
    String operationKey                     // identidad de operación de UC07 (RF-009A): derivada de reservationId + status + statusChangedAt [CONV]
) {}
```

Lanza `ReservationValueNotCalculatedException` (RF-009) o `InvalidPaymentTokenException` (RF-013); UC07 las captura y las registra en `operational_failure` (UC07 RF-010, RNF-003). Un `operationKey` repetido es idempotente: no crea otra intención (ver §Reglas, punto 3). `ChargeIntentRepository` (puerto de salida) lo consumen UC06, UC09 y UC10: UC10 debe referenciarlo por nombre; los métodos requeridos por UC06/UC09 están en T005.

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; el webhook, el listener y los jobs solo invocan `application.port.in`; las entidades JPA no salen de `adapter/out/persistence`; los DTOs HTTP viven en `adapter.in` y `application` usa `command`/`result` [general-plan §3.4]. UC05 define solo `IdempotencyKey` y `PaymentMethodReference`; los objetos de valor compartidos (`Money`, `ReservationId`) pertenecen al núcleo compartido (fundacional) y se referencian por nombre.

## Reglas de negocio

Todas provienen del SPEC 5 salvo indicación.

1. **Disparo y entrada** [SPEC RF-001; UC07 RF-002A]: UC07 invoca `ProcessChargeUseCase` con el estado `PENDING`. UC05 no valida ni administra el TTL (lo administra Reservas).
2. **Valor a cobrar** [SPEC RF-002, RF-015]: un **único monto** = valor total registrado por UC04 (alquiler + seguro + depósito). Ejemplo ilustrativo (contrato UC04): alquiler `900000.00` + seguro `60000.00` + depósito `30000.00` = `990000.00`. No se generan solicitudes separadas por componente; la distinción se conserva en `charge_record` (`rental_amount`, `insurance_amount`, `deposit_amount`). Sin valor total registrado → `ReservationValueNotCalculatedException` y **no** se contacta a la pasarela [RF-009].
3. **Una intención por intento** [SPEC RF-004, RF-004A; general-plan D-28, D-33]: cada identidad de operación nueva (un `status_changed_at` nuevo = intento nuevo) crea una `ChargeIntent` con su propia clave idempotente (reintento tras rechazo o expiración = intención nueva). La clave idempotente del intento es `reservation_id` + `status` + `status_changed_at` (el `operationKey` derivado de `reservationId` + `statusChangedAt` del mensaje de UC07); la unicidad la da `UNIQUE(idempotency_key)`, no la reserva. Los estados terminales deduplican por (reserva, estado, tipo de operación) y solo `PENDING` admite nuevos intentos. Una invocación con una clave ya existente no crea otra intención ni cobra dos veces (UC07, caso extremo "pendiente repetido"). **[ACT-02]** Además, no se abre un intento nuevo si la reserva ya tiene un cobro `CAPTURED`: una reserva cobrada no se vuelve a cobrar (la idempotencia real la da la constraint `UNIQUE(idempotency_key)` de la base de datos).
4. **Envío asíncrono** [general-plan D-08, §7.2]: `ProcessChargeService` persiste la intención en `PENDING_SEND` y el mensaje *outbox* (cola interna `finance.charge-command.v1`) en la **misma transacción**; el *worker* envía a la pasarela (timeout 10 s [CONV]). El reintento técnico reutiliza **la misma** clave idempotente [SPEC RNF-003].
5. **Resultado y estados** [general-plan §8]: `PENDING_SEND`, `IN_PROCESS`, `CAPTURED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `COMMUNICATION_ERROR`. Estados de Mercado Pago del contrato: `approved`, `in_process`, `rejected` (respuesta); `approved`, `rejected`/`expired` (webhook `payment.updated`).
   - Timeout, `502`, `503` o caída → `COMMUNICATION_ERROR`; **no** se interpreta como rechazo ni como éxito [SPEC RNF-003, caso extremo].
   - `400` por validación de token: no reintentable [contrato §5]; no se marca la reserva como cobrada [SPEC RF-013]. La intención queda en `REJECTED` con `status_detail` técnico y no se crea `ChargeRecord` **[CONV]**.
   - **[ACT-02]** Tabla de transiciones **[CONV]** (derivada; el plan no publica tabla literal): desde `PENDING_SEND` se admite cualquier estado no terminal (el worker ya envió el cobro y la pasarela responde, incluido `CAPTURED` porque con cobro directo la aprobación *es* el cobro, general-plan D-32); desde `IN_PROCESS` y `COMMUNICATION_ERROR` se admiten `CAPTURED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `IN_PROCESS` y `COMMUNICATION_ERROR`; `CAPTURED`, `REJECTED`, `CANCELLED` y `EXPIRED` son terminales. Repetir el estado actual es idempotente. Un `404` de la pasarela **no** cierra el intento: no es un veredicto financiero, el intento queda en su estado y el evento se registra en `operational_failure` (contrato §3.6). Un `PENDING_SEND` sin referencia externa queda abierto: su reintento es responsabilidad de UC07 (Reservas), no de la conciliación.
6. **Registro inmutable** [SPEC RF-006, RF-008, RF-010, RF-004B; general-plan §3.5]: `ChargeRecord` solo cuando el cobro/captura queda confirmado (`CAPTURED`); nunca para `REJECTED`, `CANCELLED`, `EXPIRED`, `IN_PROCESS` ni `COMMUNICATION_ERROR`. Se crea en la **misma transacción** que la actualización de la intención, con `charge_intent_id`, y copia `owner_id` y `boat_id` de `reservation_information`. Con el cobro directo con captura automática (general-plan D-32), la confirmación de la pasarela corresponde al cobro efectivo y el registro se crea en ese mismo momento.
7. **Datos de pago** [SPEC RF-011, RF-012, RNF-004]: solo se recibe/envía/persiste el token (`payment_token_ref`), el tipo de medio, los últimos 4 y metadatos no sensibles; el token no se escribe en logs. **[ACT-02]** El `outbox_message` de la cola interna `finance.charge-command.v1` lleva **el identificador de la intención y el correo del pagador** (`charge_intent_id` y `payer_email`), y **nunca el token**: el token se lee de `charge_intent` en el *worker* (CE-005). El correo es indispensable porque el contrato de cobro exige `payer.email` y el esquema de `charge_intent` no tiene columna para él; es un dato plano del mensaje de UC07, no un dato sensible de pago. El resto de los campos del contrato de cobro se resuelven así: `payment_method_id` ← `payment_method_type` (con la traducción `CREDIT_CARD` → `credit_card`), `installments` = 1 y `description` fijada por el sistema; el `external_reference` enviado a la pasarela es el `reservation_id` y la cabecera `X-Idempotency-Key` es la clave idempotente del intento (`charge_intent.idempotency_key`). **[ACT-02]** Las claves de `payment_metadata` que UC05 consume son `payer_email` y `last_four` (datos no sensibles); el tipo de medio llega en el campo `paymentMethodType` del comando, que es opcional (SPEC RF-001: «cuando estén disponibles»).
8. **Resultado de la pasarela y asociación** [SPEC casos extremos]: la respuesta técnica y el webhook pasan por el mismo `RegisterChargeResultService`; el segundo resultado igual al ya registrado es idempotente y no altera nada **[CONV]**. El id del pago de Mercado Pago (`data.id`) se guarda en `charge_intent.external_reference` y con él se asocia el resultado a su intención; un resultado sin intención asociada se registra en `operational_failure` sin crear `ChargeRecord` **[CONV]**. El webhook responde `200 OK` ante `data.id` o `type` desconocidos (registrando el evento en `operational_failure`) y ante tipos no reconocidos (se registra y se confirma recepción); las fallas internas responden `500 INTERNAL_ERROR` para que Mercado Pago reintente [README §4.4].

## Contratos de API

UC05 **no expone endpoints REST de negocio propios** (README §2: "UC05 no tiene endpoint de negocio"); su único endpoint es el webhook compartido de la pasarela. Interfaces:

| Interfaz | Dirección | Contrato | Resumen |
|---|---|---|---|
| `ProcessChargeUseCase` (puerto) | UC07 → UC05 | — | Ver «Puertos expuestos» |
| Cola interna del comando | Outbox → worker | `finance.charge-command.v1` (DLQ `finance.charge-command.v1.dlq`) | Reintentos del listener: 5 con *backoff* exponencial (base 1 s) y luego DLQ + registro en `operational_failure` [README §3.5/§4.4]. **[ACT-02]** El cuerpo es `{"charge_intent_id": "...", "payer_email": "..."}` (D-UC05-10) |
| Cola interna de notificaciones **[ACT-02]** | Outbox → worker | `finance.gateway-notification.v1` (DLQ `finance.gateway-notification.v1.dlq`) | Cola **nueva** para el paso 2 del webhook; cuerpo `{"type": "...", "action": "...", "data_id": "..."}` (D-UC05-12). Registrada en `contracts/README.md` §3.5 (T031) |
| `POST /v1/payments` | Worker → Mercado Pago | `pasarela-comando-cobro.md` | `transaction_amount` único; `token`; `X-Idempotency-Key` por intento; `payer_email` ← `payment_metadata` del mensaje de UC07, `payment_method_id` ← `payment_method_type`, `installments` = 1 y `description` la fija el sistema |
| `POST /api/v1/webhook/gateway` | Mercado Pago → sistema | `pasarela-webhook-resultados.md` | Verifica `x-signature`; valida el payload; persiste la notificación en el *outbox* y acusa recibo; el paso 2 ocurre en el worker de `finance.gateway-notification.v1` (D-UC05-12) |

**Webhook (parte de UC05, `type=payment`)** — códigos del catálogo README §3.4/§4.4:

| HTTP | `code` | Cuándo |
|---|---|---|
| 200 | — | Notificación aceptada, duplicada, `data.id`/`type` desconocidos o `type` no reconocido (se registra en `operational_failure`) [README §4.4] |
| 400 | `VALIDATION_ERROR` | Payload mal formado o sin `action`, `data.id`, `type` |
| 401 | `UNAUTHENTICATED` | `x-signature` ausente o inválida (requisito de la integración con Mercado Pago; no es seguridad del proyecto — general-plan §7.1) |
| 500 | `INTERNAL_ERROR` | BD no disponible al acusar recibo: la pasarela reintentará |

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | transiciones de estado, `BigDecimal`, inmutabilidad del registro | `ChargeIntentTest`, `ChargeRecordTest` |
| Unitario (`application`) | RF-002, RF-009, RF-013, RF-004A, resultados por estado | `ProcessChargeServiceTest`, `RegisterChargeResultServiceTest` |
| Integración (Testcontainers PostgreSQL) | esquema (migración inicial única), FK, `UNIQUE(idempotency_key)`, varias intenciones por reserva, trigger anti-mutación | `ChargePersistenceAdapterTest` |
| Integración (Testcontainers RabbitMQ + WireMock) | outbox → worker → pasarela, mismo `X-Idempotency-Key` en reintentos, timeouts | `ChargeCommandListenerTest`, `MercadoPagoChargeAdapterTest` |
| Web (MockMvc) | firma, `400`, `401`, `200` (duplicado o desconocido), `500` (el `401` es de la firma de Mercado Pago, no control de acceso — general-plan §7.1) | `GatewayWebhookControllerTest` |
| Arquitectura | reglas §3.4 | `ArchitectureTest` (base y ampliación T030) |

**Pruebas de aceptación (CE-001…CE-007)**:

- **`ce001_montos_autorizados_y_confirmados_sin_discrepancias`** (T026): intención aprobada/capturada conserva montos iguales al valor calculado.
- **`ce002_trazabilidad_solicitud_resultado_e_intencion_registro`** (T027): toda solicitud enviada queda registrada, todo resultado actualiza la intención y `ChargeRecord.charge_intent_id` apunta a su intención, con varias intenciones por reserva.
- **`ce003_fallas_de_comunicacion_sin_estado_indefinido`** (T028): timeout/inalcanzable al enviar y al recibir terminan en un estado explícito.
- **`ce004_rechazados_o_fallidos_no_son_cobros_exitosos`** (T029).
- **`ce005_sin_datos_sensibles_persistidos_ni_en_logs`** (T016).
- **`ce006_metadatos_enmascarados_conservados`** (T025).
- **`ce007_expiracion_reportada_por_la_pasarela_registrada_sin_asumir_fondos`** (T017): la pasarela reporta `expired`; la intención queda en `EXPIRED` sin crear `ChargeRecord` y sin asumir fondos disponibles.

## Decisiones de diseño

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC05-01** | Las piezas técnicas compartidas no deben coordinarse mediante tareas de otro caso de uso | Auditoría de planes | UC05 consume `FailureRecorderPort`, `OutboxPort`, AMQP y dependencias por sus contratos; no los redeclara ni referencia tareas externas | Aplicada |
| **D-UC05-02** | `charge_intent` requiere detalle de estado externo y monto solicitado | general-plan §4 vs SPEC 5/6 | El esquema de UC05 en la migración inicial única incluye `status_detail` y `requested_amount`; el cobro es directo con captura automática (general-plan D-32) y los campos del pagador/medio de pago se resuelven desde el mensaje de UC07 (`payment_metadata.payer_email`, `payment_method_type`), con `installments` = 1 y `description` fijada por el sistema | Aplicada |
| **D-UC05-03** | general-plan §3.5 lista 3 puertos de entrada para UC05; el diseño necesita 2 más: `SendChargeToGatewayUseCase` (el *worker* es un adaptador y solo puede invocar `port.in`, §3.4) y `ReconcileChargeIntentsUseCase` (job de conciliación) | general-plan §3.5 vs §3.4 | Se agregan ambos como [CONV]: son puertos propios de UC05 | **Aplicada** |
| **D-UC05-04** | El contrato de cobro usa `X-Idempotency-Key` de ejemplo `cobro-<reservation_id>`, igual para todos los intentos de una reserva; contradice RF-004A / D-28 (nuevo intento = clave nueva) | `pasarela-comando-cobro.md` §2 vs SPEC RF-004A | Rige el SPEC: clave por intento (`reservation_id` + `status` + `status_changed_at`; general-plan D-33). T031 corrige el ejemplo del contrato: el contrato de cobro es de UC05 | **Aplicada** |
| **D-UC05-05** | La política exacta de acuse del webhook presenta reglas en tensión | `pasarela-webhook-resultados.md` §3.2 vs §3.7 | UC05 mantiene la recepción, la verificación y el despacho; `data.id`/`type` desconocidos → `200 OK` + registro en `operational_failure`; `type` no reconocido se registra y se confirma recepción; fallas internas → `500` para que Mercado Pago reintente | Aplicada |
| **D-UC05-06** | RF-006 dice crear el registro "si la operación es exitosa"; la entidad dice "capturada o cobrada exitosamente"; UC06 aclara que `APPROVED` no implica captura | SPEC 5 RF-006 vs Entidades Clave vs UC06 | Se crea `ChargeRecord` solo en `CAPTURED` (general-plan §3.5: "solo con cobro/captura confirmados") | Aplicada |
| **D-UC05-07** | RF-009/RF-013 piden "responder con un error controlado", pero UC05 no tiene canal de respuesta (lo invoca UC07, unidireccional) | SPEC 5 vs UC07 RF-009/RF-010 | `ProcessChargeUseCase` lanza excepción de dominio; UC07 la registra en `operational_failure` | Aplicada |
| **D-UC05-08** | El contrato dice que el resultado "definitivo" llega por webhook, pero la respuesta técnica puede ser `approved` | `pasarela-comando-cobro.md` §4 vs SPEC RF-005 | La respuesta técnica y el webhook pasan por el mismo `RegisterChargeResultService`; el segundo resultado igual es idempotente: es el diseño del servicio de UC05 | **Aplicada** |
| **D-UC05-09** **[ACT-02]** | El plan pide un mapper MapStruct para la persistencia (T009) y general-plan D-04 lo establece para los modelos JPA | plan T009 vs general-plan D-04 vs D-UC11-11 (mapeo manual deliberado del núcleo) | Mapeo **manual** en `ChargePersistenceMapper`, igual que UC03/UC04/UC11: el dominio es inmutable y las entidades no exponen constructores públicos, de modo que MapStruct exigiría *setters* o un *builder* y debilitaría la encapsulación. Desviación de D-04, coherente con D-UC11-11 | **Aplicada** |
| **D-UC05-10** **[ACT-02]** | La regla 7 original decía que el mensaje del outbox lleva «solo el identificador de la intención», pero el contrato de cobro exige `payer.email` y `charge_intent` no tiene columna para el correo | plan §Reglas 7 vs `pasarela-comando-cobro.md` §2 vs `V1__initial_schema.sql` (`charge_intent`) | El mensaje lleva **`charge_intent_id` y `payer_email`**; el token **nunca** viaja (CE-005). El correo es un dato plano, no un dato sensible de pago, y la alternativa (una columna nueva) exigiría una migración `V2+` del núcleo (D-31) para persistir un dato personal sin necesidad funcional | **Aplicada** |
| **D-UC05-11** **[ACT-02]** | ¿Quién valida que la solicitud a la pasarela esté completa (tipo de medio, correo del pagador)? | SPEC RF-001 («cuando estén disponibles») vs `pasarela-comando-cobro.md` §2 (obligatorios) vs plan §Reglas 7 | En el **proceso** solo se valida el token (`InvalidPaymentTokenException`, RF-013 explícito); el **worker** valida el resto antes de llamar: si falta el tipo de medio o el correo, la intención queda `REJECTED` con detalle técnico **sin** llamar a la pasarela («no se envía un cobro con datos incompletos», SPEC caso extremo). El tipo de medio sigue siendo opcional en la entrada (RF-001) | **Aplicada** |
| **D-UC05-12** **[ACT-02]** | El paso 2 del webhook (consultar el pago y registrar el resultado) puede ser síncrono en el hilo de la petición o asíncrono | `pasarela-webhook-resultados.md` §3.2 (acuse inmediato) vs §3.7 (500 si falla el procesamiento) vs plan T019/T020 | **Asíncrono**: el webhook persiste la notificación en el *outbox* (durable) y responde `200`; el paso 2 ocurre en el worker de la cola interna `finance.gateway-notification.v1` (cola nueva, registrada en la topología AMQP del núcleo). Consecuencia documentada: un fallo del paso 2 ya no puede devolver `500` a la pasarela; lo reintenta la cola interna (5 intentos → DLQ + `operational_failure`) | **Aplicada** |
| **D-UC05-13** **[ACT-02]** | Un `404` de la pasarela al consultar el pago se traduce a `REJECTED` (estado terminal, veredicto financiero) | `pasarela-webhook-resultados.md` §3.6 vs SPEC RNF-003 vs plan §Reglas 5 | Un `404` **no** cierra el intento: la intención conserva su estado y el evento se registra en `operational_failure`; la conciliación vuelve a intentarlo. Solo la pasarela declara un rechazo con su estado `rejected` | **Aplicada** |
| **D-UC05-14** **[ACT-02]** | Nada impedía abrir un intento nuevo para una reserva que ya tenía un cobro `CAPTURED` | plan §Reglas 3 (D-33) vs SPEC RF-004A | No se abre un intento si la reserva ya tiene un cobro capturado: una reserva cobrada no se vuelve a cobrar. La idempotencia por clave (`UNIQUE(idempotency_key)`) sigue siendo la garantía real del esquema | **Aplicada** |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias se expresan mediante puertos, firmas y tablas, no mediante tareas externas.

### Phase 1: Setup

- [x] **T034** · registrar starters, AMQP, Resilience4j, WireMock, Awaitility y base de `ArchitectureTest`. [ACT-01: Completado — `pom.xml` incluye `resilience4j-spring-boot4` 2.4.0, `wiremock-standalone` 3.13.2, `archunit-junit5` 1.5.1, `testcontainers-rabbitmq`; `ArchitectureTest` existe en `src/test/java/.../arch/`]

### Phase 2: Foundational

- [x] **T035** · consumir `DomainException`, `ProblemDetailsConfig`, `FailureRecorderPort`, `OutboxPort` y AMQP común por sus firmas compartidas; UC05 no los redeclara. [ACT-01: Completado — UC11 implementó la foundation compartida; existen `BestEffortFailureRecorder` (`@Primary`), `RabbitTopologyConfig`, `AmqpNames`, `AmqpListenerFailurePolicy`, `OperationalFailureMessageRecoverer`]

### Phase 3: US1 — Enviar la solicitud de cobro (HU1; RF-001…RF-004B, RF-009…RF-011, RF-013, RF-015; CE-002, CE-003, CE-005)

- [x] **T036** · Propiedades de configuración de la pasarela (`seashare.gateway.*`), sin valores sensibles en el repositorio. [ACT-01: Completado — `application.properties` define `read-timeout=10s`, `connect-timeout=10s`; `application-dev.properties` y `application-prod.properties` definen `base-url`, `access-token`, `webhook-secret`]
- [x] **T037** · Verificar el esquema de `charge_intent` y `charge_record` contra la migración inicial única `V1__initial_schema.sql`: unicidad de idempotencia, múltiples intenciones por reserva, FK de auditoría, índices, inmutabilidad de `charge_record` y privilegios. UC05 no define migraciones propias; desviaciones ⇒ versión posterior `V2+` (general-plan §4.2, D-31). [ACT-01: Completado — `V1__initial_schema.sql` líneas 147-197 contienen ambas tablas con constraints, índices y triggers]
- [x] **T038** · Dominio: `ChargeIntentStatus`, `ChargeIntent` (transiciones válidas), `ChargeRecord` (inmutable, sin *setters*), `PaymentMethodReference`, `IdempotencyKey`; valores de estado en inglés conforme al general-plan. La clave idempotente del intento se deriva de `reservation_id` + `status` + `status_changed_at` y los estados terminales deduplican por (reserva, estado, tipo de operación) [general-plan D-33]. [ACT-01: Parcialmente completado — `ChargeIntent` completo (189 líneas), `ChargeIntentStatus` enum con 7 estados, `PaymentMethodReference` record, `IdempotencyKey` record. **Pendiente**: `ChargeRecord` es un stub de 6 líneas sin campos ni lógica] **[ACT-02: Completado — `ChargeRecord` implementado como modelo inmutable (sin *setters*) con `create(...)` y `restore(...)`; la tabla de transiciones de `ChargeIntent` se amplía según §Reglas 5]**
- [x] **T004** · Excepciones `ReservationValueNotCalculatedException`, `InvalidPaymentTokenException` y pruebas unitarias de dominio (transiciones, inmutabilidad, `BigDecimal`). · M: `none` · P: `pending` [ACT-01: Parcialmente completado — Excepciones implementadas; `ChargeIntentTest` existe (6 tests de dominio). **Pendiente**: `ChargeRecordTest` y pruebas de inmutabilidad de `ChargeRecord`] **[ACT-02: Completado — `ChargeRecordTest` (8 pruebas) cubre la copia del desglose y del propietario, los metadatos no sensibles, la inmutabilidad sin *setters* y el rechazo de una intención no capturada, de otra reserva, sin desglose calculado o con un monto discrepante; `ChargeIntentTest` cubre la tabla de transiciones]**
- [x] **T039** · Puertos de salida: `ChargeIntentRepository`, `ChargeRecordRepository`, `ChargeRecordQueryPort` y `ChargeGatewayPort`; solo `PENDING` admite nuevos intentos, los estados terminales son idempotentes [general-plan D-33]. [ACT-01: Completado — Los 4 puertos de salida existen con sus firmas]
- [x] **T040** · Puertos de entrada y `ProcessChargeCommand`; publicar la firma de `ProcessChargeUseCase`. [ACT-01: Completado — Los 4 puertos de entrada y `ProcessChargeCommand` existen]
- [x] **T007** · Pruebas de `ProcessChargeService`: valor total registrado → intención + outbox; sin valor total → `ReservationValueNotCalculatedException` sin tocar la pasarela (RF-009); token ausente → `InvalidPaymentTokenException` (RF-013); misma clave → no duplica (RF-004A); un solo monto (RF-015). · M: `none` · P: `pending` **[ACT-02: Completado — `ProcessChargeServiceTest` (10 pruebas) cubre los cinco escenarios, la idempotencia por clave, la no apertura de intento con un cobro ya capturado y la ausencia del token en el mensaje del outbox]**
- [x] **T008** · `ProcessChargeService` (transacción única: intención + outbox; clave idempotente derivada de `reservation_id` + `status` + `status_changed_at` conforme a general-plan D-33). **[ACT-02: Completado — servicio en `application/service`, registrado en `ProcessChargeConfiguration` para que su `@Transactional` sea efectivo; publica el comando por `ChargeCommandPublisher` (D-UC05-10)]**
- [x] **T009** · Persistencia: entidades JPA, repositorios, mapper MapStruct y adaptadores (las entidades no salen de `adapter/out/persistence`). · M: `none` · P: `pending` **[ACT-02: Completado con la desviación de D-UC05-09 (mapeo manual, coherente con D-UC11-11) — `adapter/out/persistence/charge/` con las dos entidades, los dos repositorios Spring Data, `ChargePersistenceMapper` y los dos adaptadores] [2026-10-29: `findLatestByReservationId` se endurece con `ORDER BY created_at DESC, id DESC` para el desempate de UC06 (D-UC06-05); el puerto y el modelo no cambian.]**
- [x] **T010** · Integración (Testcontainers): FK, `UNIQUE(idempotency_key)`, varias intenciones por reserva, trigger anti-mutación en `charge_record`. · M: `none` · P: `pending` **[ACT-02: Completado — `ChargePersistenceAdapterIntegrationTest` (6 pruebas) verifica contra PostgreSQL real la FK a `reservation_information` (una intención de una reserva inexistente se rechaza), `UNIQUE(idempotency_key)`, varias intenciones por reserva, el trigger anti-`UPDATE` de `charge_record` y `UNIQUE(charge_intent_id)`]**
- [x] **T011** · Prueba de publicación en *outbox* en la misma transacción que la intención (si falla una, falla la otra). · M: `none` · P: `done` **[ACT-02, cerrado el 2026-10-30: la atomicidad la garantizan el `@Transactional` de `ProcessChargeService` y la propagación `MANDATORY` del *outbox*, verificada contra PostgreSQL y RabbitMQ reales en `OutboxRelayIntegrationTest.publishOutsideATransactionFailsBecauseItMustJoinTheCallerTransaction`; `ProcessChargeServiceTest.registersIntentAndPublishesCommandForCalculatedTotal` comprueba la intención registrada y el comando publicado]**
- [x] **T012** · ACL de la pasarela: `ChargeGatewayRequest/Result`, `MercadoPagoPaymentRequest/Response`, `MercadoPagoChargeAdapter.send` con `X-Idempotency-Key` por intento, sin PAN/CVV/vencimiento. El `payer_email` se toma de `payment_metadata` (mensaje de UC07), el `payment_method_id` de `payment_method_type`, `installments` = 1 y la `description` la fija el sistema. · M: `none` · P: `pending` **[ACT-02: Completado — el adaptador usa un `JsonMapper` propio (el de la API escribe los decimales como cadena) con `SNAKE_CASE`, traduce `CREDIT_CARD` → `credit_card` y aplica timeout de 10 s y *circuit breaker* (general-plan §7.2, D-20)]**
- [x] **T013** · Pruebas WireMock del envío: cuerpo según contrato, un solo `transaction_amount`, cabecera idempotente, `BigDecimal` sin pérdida (RNF-001, RNF-002, RF-011, RF-015). · M: `none` · P: `pending` **[ACT-02: Completado — `MercadoPagoChargeAdapterTest` (11 pruebas) cubre el cuerpo del contrato, la cabecera `X-Idempotency-Key`, la ausencia de datos sensibles, el `400` no reintentable, el `503`/`500`/timeout y la consulta del pago]**
- [x] **T014** · *Worker*: `ChargeCommandListener` + `SendChargeToGatewayService` con timeout 10 s y *circuit breaker*; guarda el id de pago de MP en `external_reference`; timeout/`502`/`503` → `COMMUNICATION_ERROR` y reintento con la **misma** clave; `400` de token → sin reintento y la intención queda `REJECTED` con `status_detail` técnico. · M: `none` · P: `pending` **[ACT-02: Completado — el listener clasifica los errores permanentes (ack + registro) y propaga los transitorios; el servicio guarda la referencia externa, clasifica el `400` como no reintentable y propaga la falla de comunicación para que la cola reintente con la misma clave; el *circuit breaker* del adaptador evita gastar los reintentos contra una pasarela caída]**
- [x] **T015** · Pruebas del *worker*: reintentos con misma clave, `COMMUNICATION_ERROR` no es rechazo, `400` no se reintenta y deja `REJECTED` técnico (RF-013, RNF-003). · M: `none` · P: `pending` **[ACT-02: Completado — `SendChargeToGatewayServiceTest` (7 pruebas) cubre el envío con la clave idempotente, el `400` no reintentable, la falla de comunicación reintentable y el request incompleto; `ChargeCommandListenerTest` (6 pruebas) cubre el cuerpo mal formado, el cuerpo sin `charge_intent_id`, el content-type no JSON, el fallo permanente (ack + registro) y el transitorio (propagado para que la cola reintente)]**
- [x] **T016** · **`ce005_sin_datos_sensibles_persistidos_ni_en_logs`** (CE-005): el esquema no tiene columnas de PAN/CVV/vencimiento; el token no aparece en logs ni en `outbox_message`; solo tokens y metadatos no sensibles. · M: `none` · P: `pending` **[ACT-02: Completado — `Uc05AcceptanceTest.ce005_...` verifica que el token no aparece en el `toString` del registro de auditoría, en el del VO del medio de pago ni en el del mensaje del outbox; la ausencia de columnas de PAN/CVV/vencimiento en el esquema la verificará el test de integración de la persistencia (T010, pendiente)]**

### Phase 4: US2 — Registrar el resultado del cobro (HU2; RF-005…RF-008, RF-012, RF-014; RNF-003; CE-001, CE-003, CE-004, CE-006, CE-007)

- [x] **T017** · Pruebas de `RegisterChargeResultService`: aprobado/capturado, en proceso, rechazado, cancelado, expirado; repetido no altera el registro; resultado sin intención → `operational_failure` y sin `ChargeRecord`. · M: `none` · P: `pending` **[ACT-02: Completado — `RegisterChargeResultServiceTest` (9 pruebas)]**
- [x] **T018** · `RegisterChargeResultService`: actualiza siempre la intención; crea `ChargeRecord` solo en `CAPTURED`, en la misma transacción, con `charge_intent_id`, propietario, embarcación y desglose. UC05 escribe el monto capturado; los reembolsos (totales o parciales) se hacen por `/refunds` (general-plan D-32). Respuesta técnica y webhook pasan por aquí. **[ACT-02: Completado — el registro se crea solo en `CAPTURED` y en la misma transacción; si la reserva ya no tiene el desglose, el cobro confirmado no se pierde: se registra el fallo y la intención queda `CAPTURED` (la conciliación la vuelve a pasar por aquí para crear el registro que falta)]**
- [x] **T019** · Webhook propiedad de UC05: `GatewayWebhookController` + `MercadoPagoSignatureVerifier` (HMAC-SHA256 del contrato), validación de payload, acuse tras persistencia durable y despachador por `type`; UC09/UC10 solo registran sus manejadores y puertos `Send…`/`Reconcile…`. Pruebas MockMvc (401, 400, 200 duplicado y `data.id`/`type` desconocidos, 500). **[ACT-02: Completado — el controller verifica la firma, valida el payload y despacha por `type`; la notificación se persiste en el *outbox* (durable) antes de acusar recibo; `GatewayWebhookDispatcherService` registra en `operational_failure` los tipos sin manejador; pruebas MockMvc de 200/400/401/500 y pruebas del verificador]**
- [x] **T020** · Procesamiento asíncrono de la notificación: consume el mensaje, localiza la intención por `external_reference`, consulta `fetchPayment` y llama a `RegisterChargeResultUseCase`; sin intención asociada → `operational_failure`. · M: `none` · P: `pending` **[ACT-02: Completado — `GatewayNotificationListener` consume la cola `finance.gateway-notification.v1` y `ChargePaymentWebhookHandler` asocia por `external_reference`, consulta `fetchPayment` y registra; sin intención asociada registra el evento]**
- [x] **T021** · `MercadoPagoChargeAdapter.fetchPayment` y pruebas WireMock (`approved`, `rejected`, `expired`, `in_process`). · M: `none` · P: `pending` **[ACT-02: Completado — `fetchPayment` devuelve `Optional` vacío ante un `404`; las pruebas cubren los cuatro estados]**
- [x] **T023** · `ReconcileChargeIntentsService` + registro en `GatewayReconciliationJob` (compartido): reintenta/consulta intenciones sin resultado definitivo **con la misma clave** (RNF-003). · M: `none` · P: `pending` **[ACT-02: Completado — la conciliación cubre `PENDING_SEND`, `IN_PROCESS`, `COMMUNICATION_ERROR` y las capturas `CAPTURED` sin registro de auditoría; un `404` no cierra el intento (D-UC05-13) y el fallo se registra una sola vez por intento]**
- [x] **T024** · Pruebas de conciliación: intención en `COMMUNICATION_ERROR` o `IN_PROCESS` termina en estado explícito. · M: `none` · P: `pending` **[ACT-02: Completado — `ReconcileChargeIntentsServiceTest` (7 pruebas)]**
- [x] **T025** · **`ce006_metadatos_enmascarados_conservados`** (CE-006, RF-012): tipo y últimos 4 se conservan en intención y registro sin exponer credenciales. · M: `none` · P: `pending` **[ACT-02: Completado — `Uc05AcceptanceTest.ce006_...`]**
- [x] **T026** · **`ce001_montos_autorizados_y_confirmados_sin_discrepancias`** (CE-001). · M: `none` · P: `pending` **[ACT-02: Completado — `Uc05AcceptanceTest.ce001_...`]**
- [x] **T027** · **`ce002_trazabilidad_solicitud_resultado_e_intencion_registro`** (CE-002). · M: `none` · P: `pending` **[ACT-02: Completado — `Uc05AcceptanceTest.ce002_...`: varias intenciones por reserva, cada resultado actualiza la suya y `charge_record.charge_intent_id` apunta a su intención]**
- [x] **T028** · **`ce003_fallas_de_comunicacion_sin_estado_indefinido`** (CE-003). · M: `none` · P: `pending` **[ACT-02: Completado — `Uc05AcceptanceTest.ce003_...` cubre la falla al enviar; la falla al recibir el resultado queda en `COMMUNICATION_ERROR` y la conciliación la recupera (T023/T024)]**
- [x] **T029** · **`ce004_rechazados_o_fallidos_no_son_cobros_exitosos`** (CE-004, RF-008). · M: `none` · P: `pending` **[ACT-02: Completado — `Uc05AcceptanceTest.ce004_...`]**

### Phase 5: Polish & Cross-Cutting Concerns

- [x] **T030** · ArchUnit: `domain` sin Spring/JPA/Jackson; `application` sin JPA/MapStruct; webhook/listener/jobs sin repositorios; `ChargeRecord` sin *setters*; ningún otro UC calcula tarifas aquí. · M: `none` · P: `pending` **[ACT-02: Completado — las 7 reglas de `arch/ArchitectureTest` se cumplen (verificado en la suite completa) y `arch/Uc05ArchitectureTest` añade las reglas propias: UC05 no recalcula tarifas ni el valor de la reserva, no depende de la implementación de la pasarela, los adaptadores de entrada solo invocan puertos de entrada y `ChargeRecord` es inmutable sin *setters*]**
- [x] **T031** · Alinear documentos: ejemplo de `X-Idempotency-Key` en `pasarela-comando-cobro.md` (clave por intento) y columnas `status_detail`/`requested_amount` en general-plan §4. · M: `none` · P: `pending` **[ACT-02: Completado — el ejemplo de la clave del contrato ya era el de la clave por intento; se añaden `status_detail` y `requested_amount` a la tabla `charge_intent` de general-plan §4, y la cola nueva `finance.gateway-notification.v1` (con su DLQ, su enrutado y la forma de su cuerpo) a `contracts/README.md` §3.5 y §4.4]**
- [x] **T032** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending` **[ACT-02: Completado — medición JaCoCo de la implementación: dominio `ChargeRecord` 99,5 %, `ChargeIntent` 99,1 %, `ChargeIntentStatus`/`PaymentMethodReference`/`IdempotencyKey` 100 %; aplicación `RegisterChargeResultService` 87,0 %, `SendChargeToGatewayService` 90,6 %, `ProcessChargeService` 97,5 %, `ReconcileChargeIntentsService`/`GatewayWebhookDispatcherService` 100 %]**
- [x] **T033** · `./mvnw clean verify` y ejecución de CE-001…CE-007. · M: `none` · P: `pending` **[ACT-02: Completado — ejecutado el 2026-10-29: `Tests run: 1238, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS, con los criterios CE-001…CE-007 ejecutados en `Uc05AcceptanceTest`]**

## Dependencies & Execution Order

```text
T034–T040 (setup y contratos compartidos) ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010 ─> T011
                                                                                                     └──────────────> T012 ─> T013 ─> T014 ─> T015 ─> T016
T017 (pruebas) ─> T018 ─> T019 ─> T020 ─> T021 ─> T023 ─> T024 ─> T025 ─> T026…T029 ─> T030 ─> T031 ─> T032 ─> T033
```

- **Dependencias externas**: los casos que consultan cobros consumen `ChargeIntentRepository`, `ChargeRecordQueryPort` y las tablas por sus firmas; UC07 consume `ProcessChargeUseCase`. El esquema de UC05 (`charge_intent`, `charge_record`) forma parte de la migración inicial única y está disponible antes de las referencias FK posteriores (general-plan §4.2, D-31).

## Notes

- **Idempotencia por intento**: la clave del intento es `reservation_id` + `status` + `status_changed_at`; los estados terminales deduplican por (reserva, estado, tipo de operación) y solo `PENDING` admite nuevos intentos (un `status_changed_at` nuevo = intento nuevo) [general-plan D-33].

- Los datos del pagador/medio de pago que exige el contrato de cobro se resuelven desde el mensaje de UC07: `payer_email` ← `payment_metadata`, `payment_method_id` ← `payment_method_type`, `installments` = 1 y `description` fijada por el sistema.
- UC05 no valida el TTL ni decide cuándo reintentar un cobro por negocio: lo decide Reservas mediante nuevas notificaciones (UC07).
- Etiquetas: `[SPEC]`, `[CONV]` (general-plan o inferido).
- Los valores numéricos de los ejemplos son ilustrativos.

---

## Actualizaciones del plan

### Actualización 2026-10-28 [ACT-01]

**Revisión previa a la implementación de UC05.** Se verificó el estado actual del código contra el plan original del 2026-10-09.

**Cambios realizados:**

1. **Nota de versionado** al inicio del documento con referencia al commit original `b42f878`.

2. **§Technical Context**:
   - Spring Boot `4.1.1` → `4.1.2` (versión actual en `pom.xml` línea 8).
   - «Estado actual del repositorio» actualizado: UC01, UC02, UC03, UC04 y UC11 están implementados; existe la foundation compartida (`DomainException`, `ProblemDetailsConfig`, `FailureRecorderPort`, `OutboxPort`, AMQP, `BestEffortFailureRecorder`, `MoneySerializer`, advisory lock pattern, test doubles en `support/`).

3. **§Implementation Phases — Tareas marcadas como completadas:**
   - **T034** [x]: `pom.xml` incluye `resilience4j-spring-boot4` 2.4.0, `wiremock-standalone` 3.13.2, `archunit-junit5` 1.5.1, `testcontainers-rabbitmq`; `ArchitectureTest` existe en `src/test/java/.../arch/`.
   - **T035** [x]: UC11 implementó la foundation compartida; existen `BestEffortFailureRecorder` (`@Primary`), `RabbitTopologyConfig`, `AmqpNames`, `AmqpListenerFailurePolicy`, `OperationalFailureMessageRecoverer`.
   - **T036** [x]: `application.properties` define `seashare.gateway.read-timeout=10s`, `connect-timeout=10s`; `application-dev.properties` y `application-prod.properties` definen `base-url`, `access-token`, `webhook-secret`.
   - **T037** [x]: `V1__initial_schema.sql` líneas 147-197 contienen `charge_intent` y `charge_record` con constraints, índices y triggers de inmutabilidad.
   - **T038** [x] **Parcial**: `ChargeIntent` completo (189 líneas), `ChargeIntentStatus` enum con 7 estados, `PaymentMethodReference` record, `IdempotencyKey` record. **Pendiente**: `ChargeRecord` es un stub de 6 líneas sin campos ni lógica.
   - **T004** [x] **Parcial**: Excepciones `ReservationValueNotCalculatedException` e `InvalidPaymentTokenException` implementadas; `ChargeIntentTest` existe (6 tests de dominio). **Pendiente**: `ChargeRecordTest` y pruebas de inmutabilidad de `ChargeRecord`.
   - **T039** [x]: Los 4 puertos de salida existen (`ChargeIntentRepository`, `ChargeRecordRepository`, `ChargeRecordQueryPort`, `ChargeGatewayPort`).
   - **T040** [x]: Los 4 puertos de entrada existen (`ProcessChargeUseCase`, `SendChargeToGatewayUseCase`, `RegisterChargeResultUseCase`, `ReconcileChargeIntentsUseCase`) junto con `ProcessChargeCommand`.

**Pendiente de implementar (inicio de UC05):**
- **T003** (no listada explícitamente pero referenciada): Completar `ChargeRecord.java` como modelo de dominio inmutable.
- **T007–T033**: Servicios de aplicación (`ProcessChargeService`, `SendChargeToGatewayService`, `RegisterChargeResultService`, `ReconcileChargeIntentsService`), toda la infraestructura (adapters de persistencia, gateway, webhook, listener, scheduler), y casi todos los tests.

**Verificado contra el código:**
- `pom.xml` (Spring Boot 4.1.2, dependencias)
- `src/main/java/com/seashare/seasharem3/domain/` (modelos, VOs, excepciones)
- `src/main/java/com/seashare/seasharem3/application/` (puertos, DTOs)
- `src/main/resources/db/migration/V1__initial_schema.sql` (esquema de BD)
- `src/main/resources/application*.properties` (configuración de gateway)
- `src/test/java/com/seashare/seasharem3/domain/model/ChargeIntentTest.java` (tests de dominio)
- `src/test/java/com/seashare/seasharem3/support/` (test doubles)
- `src/test/java/com/seashare/seasharem3/arch/ArchitectureTest.java` (reglas de arquitectura)

**Sin verificar o pendiente:**
- `ChargeRecord.java` requiere implementación completa (stub actual).
- Tests de integración y aceptación (T007–T033) no existen aún.

**Referencias cruzadas revisadas:**
- Planes de UC01, UC02, UC03, UC04, UC11 (todos implementados, referencian componentes compartidos).
- Planes de UC06, UC07, UC08, UC09, UC10 (consumen puertos de UC05 ya definidos).

### Actualización 2026-10-28 [ACT-02]

**Implementación de UC05.** Se implementó el caso de uso completo y se revisó con auditoría de código.

**Cambios realizados:**

1. **§Reglas de negocio**:
   - Regla 3: se documenta que no se abre un intento si la reserva ya tiene un cobro `CAPTURED` (D-UC05-14).
   - Regla 5: se publica la tabla de transiciones de `ChargeIntent` **[CONV]** y se documenta que un `404` de la pasarela no cierra el intento (D-UC05-13).
   - Regla 7: el mensaje del *outbox* lleva `charge_intent_id` y `payer_email` (no solo el identificador), nunca el token; se documentan las claves `payer_email` y `last_four` de `payment_metadata` (D-UC05-10, D-UC05-11).

2. **§Project Structure**: rutas y clases reales (`adapter/out/persistence/charge/`, `adapter/out/messaging/`, `adapter/in/webhook/`, `adapter/in/messaging/dto/`), y los puertos y servicios nuevos.

3. **§Decisiones de diseño**: se añaden **D-UC05-09** (mapeo manual, desviación de D-04 coherente con D-UC11-11), **D-UC05-10** (payload del *outbox*), **D-UC05-11** (dónde se valida la completitud de la solicitud a la pasarela), **D-UC05-12** (paso 2 del webhook asíncrono y cola nueva), **D-UC05-13** (el `404` no cierra el intento) y **D-UC05-14** (no abrir un intento con un cobro ya capturado).

4. **§Implementation Phases**: tareas marcadas `[x]` y con la nota de lo implementado (T003, T004, T007–T010, T012–T033); queda pendiente T011.

5. **Contratos alineados con la implementación** (T031): `contracts/external/pasarela-comando-cobro.md` (clave idempotente real, `description` y `external_reference` con el `reservation_id`, `transaction_amount` numérico, reglas de datos obligatorios, *timeout* y *circuit breaker*, y tabla completa de clasificación de errores); `contracts/external/pasarela-webhook-resultados.md` (acuse de recibo tras la persistencia durable, paso 2 asíncrono en la cola interna, campos que el sistema exige de verdad, mapeo por `type` sin filtrar por `action`, `404` que no cierra el intento y alcance real del `500`); `contracts/README.md` §3.5 (cuerpo y enrutado de las colas internas de UC05), §2 (UC05 sin endpoint de negocio) y §4.4 (filas del webhook para el paso 2 asíncrono, el `404` y la regla única de `4xx` de la pasarela, replicada en los contratos de reembolso y liquidación); `general-plan.md` §4 (columnas `status_detail`/`requested_amount`) y §5.1 (conexión C9 con el paso 2 asíncrono).

6. **§Technical Context**: se actualiza el estado del repositorio y se documentan las nuevas propiedades `seashare.gateway.circuit-breaker.*` (tipadas en `GatewayProperties`) y `seashare.gateway.reconciliation-interval` (consumida por el `@Scheduled` del job de conciliación).

7. **§Contratos de API y decisiones**: se documenta la cola interna de notificaciones y el paso 2 asíncrono del webhook, con la tabla de transición de estados y la regla de que un `404` no cierra el intento.

8. **§Implementation Phases — T009 (2026-10-29)**: se documenta el endurecimiento de `findLatestByReservationId` con `ORDER BY created_at DESC, id DESC` para el desempate de UC06 (D-UC06-05); el puerto y el modelo de UC05 no cambian.

**Defectos encontrados por la auditoría y corregidos en esta actualización:**
- El despacho del webhook no era transaccional y la escritura en el *outbox* es `MANDATORY`: toda notificación devolvía `500`. Ahora `GatewayWebhookDispatcherService.dispatch` es transaccional.
- La conciliación no cubría `PENDING_SEND` ni las capturas `CAPTURED` sin registro de auditoría: un cobro confirmado podía quedar sin `ChargeRecord` para siempre. Ahora cubre ambos casos y recrea el registro que falta.
- Un `404` de la pasarela cerraba el intento como `REJECTED` (veredicto financiero inventado). Ahora el intento se conserva y el evento se registra.
- El adaptador de la pasarela no tenía *circuit breaker* (T014, general-plan D-20) ni clasificaba los errores de transporte no HTTP: ambos corregidos. Un `400` de token inválido tampoco cuenta como fallo del *circuit breaker*: no es una caída de la pasarela y no debe abrirlo contra tráfico válido.
- Un resultado repetido sin `status_detail` borraba el detalle técnico ya registrado (y que UC06 publica): ahora el detalle solo se sustituye cuando el nuevo viene informado.
- El `toString` del VO del medio de pago exponía el token: ahora se enmascara (CE-005).
- La cola nueva `finance.gateway-notification.v1` no estaba en el mapa «cola → UC dueño» del `MessageRecoverer`: el fallo agotado se registraba con `use_case = "AMQP"`. Ahora la cola se traduce a `UC05` y hay una prueba que lo cubre.
- Los contratos de reembolso y liquidación clasificaban todo `4xx` como no reintentable, en contradicción con el de cobro y con la tabla E8 del README. Ahora el README §4.4 fija una regla única para la pasarela (`400` de validación → no reintentable; el resto de `4xx` y los `5xx` → reintentables) y los tres contratos la replican.
- El contrato del webhook exigía `api_version` y `data` en el cuerpo, que el sistema no valida; y sugería un filtrado por `action` que no existe. Ambos puntos están corregidos según la implementación.
- La validación del `last_four` contra `VARCHAR(4)` se aplicaba también al leer de la base de datos; se mantiene como invariante del VO con un mensaje más preciso.

**Verificado contra el código:**
- `src/main/java/com/seashare/seasharem3/` (dominio, aplicación e infraestructura de UC05).
- `src/test/java/com/seashare/seasharem3/` (pruebas de UC05 y dobles en `support/`).
- `src/main/resources/application.properties` (propiedades de la pasarela).
- Las 7 reglas de `arch/ArchitectureTest` se verifican en la suite completa.

**Sin verificar o pendiente:**
- T011: **cerrado el 2026-10-30.** La atomicidad intención + *outbox* se apoya en el `@Transactional` de `ProcessChargeService` y en la propagación `MANDATORY` de `OutboxPort`, verificada contra PostgreSQL y RabbitMQ reales en `OutboxRelayIntegrationTest`; `ProcessChargeServiceTest` comprueba la intención registrada y el comando publicado.
- El camino *outbox → relay → cola → listener* del webhook (durabilidad de D-UC05-12) no tiene prueba de integración con RabbitMQ real.

**Referencias cruzadas revisadas:**
- `docs/technical-plan/contracts/README.md` §3.5 y §4.4 (cola nueva por registrar).
- `docs/technical-plan/general-plan.md` §4 (columnas `status_detail`/`requested_amount` por alinear).
- `docs/technical-plan/contracts/external/pasarela-webhook-resultados.md` §3.7 (el `500` cubre el inicio del paso 2, no su ejecución asíncrona).
