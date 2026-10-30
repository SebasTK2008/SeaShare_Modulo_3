# Implementation Plan: UC07 - Brindar el Estado de la Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

El Sistema de Reservas y Operaciones notifica por RabbitMQ el estado vigente de una reserva (uno de **10 estados**) y UC07 decide la operación financiera que corresponde: ninguna (`AVAILABLE`, `INITIATED`, `RESERVED`, `IN_NAVIGATION`), **cobro** (`PENDING`, con el token de pago), **reembolso y/o liquidación** (las cuatro cancelaciones) o **liquidación estándar del alquiler con el depósito retenido** (`COMPLETED`). UC07 es el orquestador: **no calcula montos ni habla con la pasarela**; invoca por `port.in` a UC05, UC09, UC10 y UC08 [SPEC HU1, HU2, HU3, RF-001…RF-010]. Es **unidireccional**: no devuelve nada a Reservas y los fallos se registran en `operational_failure` [SPEC RF-009, RF-010, RNF-003].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. Un *listener* AMQP (`finance.reservation-status.v1`, routing key `reservation.status.changed`, exchange `seashare.reservations`) deserializa el mensaje y llama a `HandleReservationStatusUseCase`. La decisión *estado → operaciones* es una política de dominio pura; el servicio usa el `ReservationLockPort` único compartido (núcleo compartido, general-plan §3.6), valida prerrequisitos y aplica la identidad única de operación (reserva + estado + tipo de operación + clave idempotente), todo en una transacción. La clave idempotente es `reservation_id` + `status` + `status_changed_at`; los estados terminales deduplican por (reserva, estado, tipo de operación) y solo `PENDING` admite nuevos intentos [D-33]. Errores de lógica: `ack` + `operational_failure`; errores transitorios: 5 intentos con *backoff* exponencial (base 1 s), y al agotarlos DLQ `<cola>.dlq` y `operational_failure` [README §4.4; general-plan §7.2]. `reservation_status_log` es propiedad de UC07; `RequestSettlementUseCase` y `OpenDepositTrackingUseCase` se referencian por sus puertos publicados.

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Brindar el estado de la reserva" únicamente con el *Sistema de Reservas y Operaciones*, sin `<<include>>`/`<<extend>>`; las operaciones que dispara provienen de los SPEC 5, 9 y 10 y de general-plan §3.5.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir estado de uno de 10 valores | `ReservationStatusListener`, `ReservationStatusMessage`, `ReservationStatus`, `ReservationStatusPolicy` | T002, T006, T008 | T003, T005, T009 |
| **RF-002** estados sin acción financiera | `ReservationStatusPolicy` (plan vacío); `INITIATED` reconocida como inicio del TTL sin administrarlo | T002, T006 | T003, T005, T011 |
| **RF-002A** `PENDING` → "Procesar cobro" con el token | `HandleReservationStatusService` → `ProcessChargeUseCase` (UC05) | T006 | T005, T011 |
| **RF-003** flexible: 100 % del total | Plan: `RequestRefundUseCase(CANCELLED_FLEXIBLE)` | T013 | T012, T015 |
| **RF-004** moderada: reembolso 50 % alquiler + 100 % depósito y liquidación 50 % alquiler | Plan: `RequestRefundUseCase` + `RequestSettlementUseCase` | T013 | T012, T015 |
| **RF-005** tardía: liquidación 100 % alquiler y reembolso 100 % depósito | Plan: `RequestSettlementUseCase` + `RequestRefundUseCase` | T013 | T012, T015 |
| **RF-005A** anfitrión: 100 % del valor pagado | Plan: `RequestRefundUseCase(CANCELLED_BY_HOST)` | T013 | T012, T015 |
| **RF-006** completada: liquidación estándar, depósito pendiente | Plan: `RequestSettlementUseCase` + `OpenDepositTrackingUseCase` (UC08) | T018 | T017, T020 |
| **RF-007** sin operación sobre el depósito por `COMPLETED` | `ReservationStatusPolicy` no incluye operación sobre el depósito | T018 | T017, T019 |
| **RF-008** sin reembolso/liquidación del depósito en estados operativos, `PENDING` y `COMPLETED` | Política de dominio | T002, T006 | T003, T019 |
| **RF-009** sin respuesta a Reservas | Listener unidireccional; el caso de uso retorna `void` | T004, T008 | T009 |
| **RF-009A** identidad única de operación | `reservation_status_log` `UNIQUE(reservation_id, status, operation_type, idempotency_key)` | T001, T014 | T014, T011 |
| **RF-009B** exclusión mutua del depósito | UC07 no opera sobre el depósito en `COMPLETED`; bloqueo por reserva; *compare-and-set* en `deposit_disposition` (UC08) | T014, T018 | T017, T011 |
| **RF-010** registrar fallos, pendientes y no concluyentes | `FailureRecorderPort` (`operational_failure`) | T006, T013, T018 | T005, T012, T016 |
| **RNF-001** DTO mínimo | `ReservationStatusMessage` (HTTP/AMQP), `ReservationStatusCommand` | T004, T008 | T009 |
| **RNF-002** `BigDecimal` en lo monetario consultado | `reservation_information` leída con `Money`; UC07 no calcula | T006 | T005 |
| **RNF-003** manejo robusto de información ausente | Prerrequisitos antes de delegar | T006, T013, T018 | T005, T012, T016 |
| **CE-001** decisión correcta por estado | `ReservationStatusPolicy` + servicio | T002, T006, T013, T018 | **T010** |
| **CE-002** cancelaciones exactas | Plan por estado | T013 | **T015** |
| **CE-003** 0 operaciones sobre el depósito | Política | T018 | **T019** |
| **CE-004** fallos registrados sin ejecuciones parciales | Prerrequisitos + transacción única | T006, T013 | **T016** |
| **CE-005** liquidación al completar | Plan `COMPLETED` | T018 | **T020** |
| **CE-006** 0 decisiones sobre daños | Política | T018 | **T021** |
| **CE-007** origen del TTL, identidad y exclusión mutua | `INITIATED`/`PENDING` sin reinicio; log | T006, T014 | **T011** |
| **HU1** estados operativos, `INITIATED` y `PENDING` (P1) | Fase 3 | T001–T011 | T003, T005, T009–T011 |
| **HU2** cancelaciones (P1) | Fase 4 | T012–T016 | T012–T016 |
| **HU3** completada y espera de disputa (P1) | Fase 5 | T017–T021 | T017–T021 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.1. Para UC07: Spring AMQP, Spring Data JPA, Flyway, MapStruct, ArchUnit y pruebas RabbitMQ. Se registran en el setup común sin referencias a tareas externas.
**Storage**: PostgreSQL 16+; `reservation_status_log` propia (general-plan §4); lee `reservation_information` por su puerto publicado [general-plan §3.5]. `operational_failure.reservation_id` es nullable y sin FK; `payload_ref` referencia el mensaje o JSON almacenado.
**Messaging**: RabbitMQ 3.13+, colas *quorum*; entrante `finance.reservation-status.v1`; internas `finance.charge-command.v1`, `finance.refund-command.v1`, `finance.settlement-command.v1` y `finance.gateway-notification.v1` (esta última, añadida por UC05 para el paso 2 del webhook de la pasarela; README §3.5); DLQ `<cola>.dlq` [general-plan]
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), Awaitility, ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: Sin meta propia: el SPEC 07 no define metas de rendimiento [CONV].
**Constraints**: unidireccional [SPEC RF-009]; no persiste el estado operativo de la reserva [SPEC Entidades Clave]; identidad única de operación [SPEC RF-009A]; sin cálculos parciales ante información ausente [SPEC RNF-003]; UC07 no administra TTL ni disputas [general-plan §2.6]
**Scale/Scope**: 1 cola consumida; 1 tabla propia; 0 endpoints REST; 10 estados; 10 RF (+ RF-002A, RF-005A, RF-009A, RF-009B) + 3 RNF + 7 CE + 3 HU

**Estado actual del repositorio**: existen `SeashareM3Application`, `application.properties`, `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. No existen `domain`/`application`/`infrastructure`, migraciones ni reglas ArchUnit.

## Project Structure

### Documentation (this feature)

```text
docs/features/007-brindar-estado-de-la-reserva/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                 # §3.5 propiedades AMQP, §4.4 colas
└── events/
    └── UC07-estado-reserva.md                # reservation.status.changed → finance.reservation-status.v1
```

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── valueobject/
│   │   ├── ReservationStatus.java               # T002  10 valores en MAYÚSCULAS [contrato §3.1; README §3.1]
│   │   ├── FinancialOperationType.java          # T002  CHARGE / REFUND / SETTLEMENT [CONV]
│   │   └── PlannedOperation.java                # T002  operación + trigger [CONV]
│   └── service/
│       └── ReservationStatusPolicy.java         # T002  estado → operaciones (pura)
├── application/
│   ├── port/in/
│   │   └── HandleReservationStatusUseCase.java  # T004  [general-plan §3.5]
│   ├── port/out/
│   │   ├── ReservationStatusLogRepository.java  # T004
│   │   ├── ReservationLockPort.java             # T004  [CONV] bloqueo asesor por reserva (D-15); núcleo compartido (general-plan §3.6)
│   │   └── (usa) ReservationInformationRepository, FailureRecorderPort y ReservationLockPort compartidos
│   ├── service/
│   │   └── HandleReservationStatusService.java  # T006, T013, T018
│   └── dto/
│       └── ReservationStatusCommand.java        # T004  = SolicitudEstadoReserva [SPEC]
└── infrastructure/
    ├── adapter/in/messaging/
    │   ├── ReservationStatusListener.java       # T008
    │   ├── ReservationStatusMessageMapper.java  # T008  texto del contrato → ReservationStatus; desconocido = fallo lógico
    │   └── dto/ReservationStatusMessage.java    # T008  campos del contrato [SPEC RNF-001]
    ├── adapter/out/persistence/
    │   ├── ReservationStatusLogJpaEntity.java / ReservationStatusLogJpaRepository.java   # T007
    │   ├── ReservationStatusLogMapper.java      # T007  (MapStruct)
    │   ├── ReservationStatusLogPersistenceAdapter.java  # T007
    │   └── PostgresAdvisoryReservationLockAdapter.java  # T007
    └── config/
        └── ReservationStatusMessagingConfig.java  # T008  cola `finance.reservation-status.v1`, *binding*, 5 intentos con *backoff* (base 1 s), DLQ `<cola>.dlq` [CONV]

src/main/resources/db/migration/
└── V1__initial_schema.sql                    # T001  migración inicial única; contiene reservation_status_log (general-plan §4.2, D-31)

src/test/java/com/seashare/seasharem3/
├── domain/service/ReservationStatusPolicyTest.java                      # T003
├── application/service/HandleReservationStatusServiceTest.java          # T005, T012, T017
├── infrastructure/adapter/in/messaging/ReservationStatusMessageMapperTest.java  # T008
├── infrastructure/adapter/in/messaging/ReservationStatusListenerTest.java  # T009
├── infrastructure/adapter/out/persistence/ReservationStatusLogPersistenceAdapterTest.java  # T007
└── acceptance/Uc07AcceptanceTest.java                                   # T010, T011, T014–T016, T019–T021
```

**Puertos consumidos** — UC07 llama solo por `port.in`:

| Puerto | Dueño | Estado de la firma |
|---|---|---|
| `ProcessChargeUseCase.process(ProcessChargeCommand)` | UC05 | Definida en `UC05·T006` |
| `RequestRefundUseCase.request(RefundRequestCommand)` | UC09 | Definida en `UC09·T006` |
| `RequestSettlementUseCase.request(SettlementCommand)` | UC10 | Firma publicada por UC10 |
| `OpenDepositTrackingUseCase.open(OpenDepositTrackingCommand)` | UC08 | Firma publicada por UC08; UC07 no implementa el puerto |

**Puertos expuestos**: ninguno hacia otros casos de uso. `HandleReservationStatusUseCase` solo lo invoca el *listener* de UC07. Firma [CONV]:

```java
public interface HandleReservationStatusUseCase {
    void handle(ReservationStatusCommand command);
}
public record ReservationStatusCommand(
    UUID reservationId,
    ReservationStatus status,               // solo valores conocidos; el listener rechaza desconocidos antes de construir el command
    Instant statusChangedAt,                // fechaHoraEstado [SPEC RNF-001]
    String paymentTokenRef,                 // solo con PENDING [SPEC RF-002A]
    String paymentMethodType,               // nullable
    Map<String, String> paymentMetadata     // nullable, no sensibles
) {}
```

El mensaje AMQP conserva `status` como texto (contrato §2). `ReservationStatusMessageMapper` lo convierte al enum; si el valor no es uno de los 10 estados, lanza una excepción lógica que el listener registra con `AmqpListenerFailurePolicy.recordAndAcknowledge(...)` y confirma el mensaje. El command solo transporta estados conocidos.

La clave de operación (RF-009A) no viaja en el comando: el servicio la deriva de `reservationId` + `status` + `statusChangedAt` (ver §Reglas, punto 7).

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; UC07 invoca a UC05/UC09/UC10/UC08 **únicamente por su `port.in`** (general-plan §3.4, regla 4); el *listener* nunca accede a un repositorio.

## Reglas de negocio

Todas provienen del SPEC 7 y del contrato `UC07-estado-reserva.md` salvo indicación.

1. **Reconocimiento** [SPEC RF-001; contrato §3.1]: solo `AVAILABLE`, `INITIATED`, `RESERVED`, `IN_NAVIGATION`, `PENDING`, `CANCELLED_FLEXIBLE`, `CANCELLED_MODERATE`, `CANCELLED_STRICT`, `CANCELLED_BY_HOST`, `COMPLETED`. El listener convierte el texto del contrato al enum; cualquier otro valor se rechaza antes de construir el command, no ejecuta ninguna operación y se registra la inconsistencia (`operational_failure`), con `ack`.
2. **Estados sin acción financiera** [SPEC RF-002, RF-008]: `AVAILABLE`, `RESERVED`, `IN_NAVIGATION` y `INITIATED` se reconocen sin operaciones. `INITIATED` marca el comienzo del TTL de 15 minutos; UC07 no lo administra ni lo reinicia.
3. **`PENDING`** [SPEC RF-002A; HU1 escenario 3]: invoca `ProcessChargeUseCase` con el token recibido; no reinicia el TTL y no dispara reembolso ni liquidación. Si el mensaje no trae `payment_token_ref` (obligatorio con `PENDING`, contrato §2) se trata como mensaje inválido: `ack` + `operational_failure`, sin llamar a UC05 **[CONV]**.
4. **Cancelaciones** [SPEC RF-003, RF-004, RF-005, RF-005A; contrato §3.4]:

| Estado | Operaciones que dispara UC07 |
|---|---|
| `CANCELLED_FLEXIBLE` | Reembolso del 100 % del valor total (alquiler, seguro, depósito); sin liquidación |
| `CANCELLED_MODERATE` | Reembolso del 50 % del alquiler + 100 % del depósito (sin seguro) **y** liquidación del 50 % del alquiler; resultados independientes |
| `CANCELLED_STRICT` | Liquidación del 100 % del alquiler **y** reembolso del 100 % del depósito; sin reembolso de alquiler ni seguro |
| `CANCELLED_BY_HOST` | Reembolso del 100 % del valor pagado; sin liquidación |

   UC07 solo elige la operación; los montos los calculan UC09 y UC10 desde registros internos.
5. **`COMPLETED`** [SPEC RF-006, RF-007; general-plan §3.5, D-35, D-34]: liquidación estándar del alquiler (**alquiler bruto − comisión − seguro náutico**; a cargo de UC10) manteniendo el depósito asociado, y apertura del seguimiento del depósito en `OpenDepositTrackingUseCase` (UC08). UC07 envía `OpenDepositTrackingCommand(reservationId, operationKey, target = HELD, disputeId = null, disputeStatus = null, completedAt = statusChangedAt)`. `deposit_disposition` —propiedad de UC08— registra todos los desenlaces del depósito (reembolso por cancelación, `REJECTED` = reembolso y `COMPLETED` = liquidación) mediante un único *compare-and-set*; UC07 lo invoca por `OpenDepositTrackingUseCase`. **Ninguna operación monetaria sobre el depósito**: espera el estado de la disputa (UC08). **[ACT-2026-10-29]**
6. **Prerrequisitos** [SPEC RNF-003, casos extremos]: se exige `reservation_information` registrada y con los montos calculados por UC04 (alquiler, seguro, depósito y total; se escriben juntos, general-plan §4). Si faltan, no se ejecuta nada parcial: `operational_failure` y `ack`. El `PENDING` exige además el valor total (lo valida UC05, RF-009).
7. **Identidad única de operación** [SPEC RF-009A; general-plan §3.5, D-33]: reserva + estado notificado + tipo de operación + clave idempotente. La clave idempotente por intento es `reservationId` + `status` + `statusChangedAt` **[CONV]**: es igual en un reenvío de la misma transición y distinta en una nueva transición. Los estados terminales deduplican por (reserva, estado, tipo de operación) y solo `PENDING` admite intentos nuevos (un `statusChangedAt` nuevo tras un rechazo de pago habilita un nuevo intento de cobro, UC05 RF-004A); no se usa `Message-Id` (general-plan D-07). Antes de cada operación se inserta una fila en `reservation_status_log` con `INSERT … ON CONFLICT DO NOTHING` **[CONV]**; si ya existía, la operación no se repite. Las notificaciones repetidas no duplican cobros (UC05 RNF-003), reembolsos ni liquidaciones. Una notificación con dos operaciones (moderada, tardía) crea **dos filas**. La misma clave se entrega como `operationKey` a UC05, UC09 y UC10.
8. **Persistencia** [SPEC Entidades Clave; general-plan D-25]: el estado operativo de la reserva **no** se persiste; `reservation_status_log` solo guarda lo necesario para idempotencia, y solo para los estados que disparan operaciones monetarias (`PENDING`, las cancelaciones y `COMPLETED`) **[CONV]**. `operation_type` toma los valores `CHARGE`, `REFUND` y `SETTLEMENT`; `outcome` es un texto corto sin valores fijados **[CONV]**.
9. **Concurrencia** [general-plan D-15, §3.6]: bloqueo asesor por reserva —`ReservationLockPort` del núcleo compartido, consumido por UC07/UC08/UC09/UC10— y **una sola transacción** por mensaje (log + delegaciones), de modo que las dos operaciones de una cancelación moderada/tardía se confirman o se revierten juntas.
10. **Unidireccional y errores** [SPEC RF-009, RF-010; README §4.4]: nunca se responde a Reservas. Mensaje inválido, estado desconocido (detectado por el mapper del listener), reserva sin información o sin cálculo → `ack` + `operational_failure`. Falla transitoria (BD, broker) → `nack` con 5 intentos y *backoff* exponencial (base 1 s) y, al agotarlos, DLQ `<cola>.dlq` y registro en `operational_failure` **[general-plan §7.2]**. Mensaje repetido (misma identidad) → `ack` sin operación adicional.

## Contratos de API

UC07 **no expone endpoints REST**. Contrato: `contracts/events/UC07-estado-reserva.md` [general-plan §6].

- **Exchange** `seashare.reservations` (`topic`, durable), **routing key** `reservation.status.changed`, **cola consumidora** `finance.reservation-status.v1` [contrato §2; README §3.5].
- **Propiedades AMQP**: `Content-Type: application/json`, `Message-Id` obligatorio (no se usa para deduplicar: general-plan D-07), `delivery_mode 2`.
- **Cuerpo**: `reservation_id` (UUID), `status`, `status_changed_at` (ISO 8601), `payment_token_ref` (obligatorio con `PENDING`), `payment_method_type`, `payment_metadata` (opcionales).
- **Respuesta**: ninguna.

| Situación | Acción | Origen |
|---|---|---|
| Mensaje inválido, estado desconocido, reserva sin información o sin cálculo | `ack` + `operational_failure` | README §4.4 |
| Falla transitoria (BD, broker) | `nack` con 5 intentos y *backoff* exponencial (base 1 s); DLQ `<cola>.dlq` y `operational_failure` al agotarlos | README §4.4; general-plan §7.2 |
| Mensaje repetido (misma identidad) | `ack` sin operación | README §4.4; SPEC casos extremos |

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %. `RequestSettlementUseCase` (UC10) y `OpenDepositTrackingUseCase` (UC08) se sustituyen por dobles en las pruebas unitarias; sus firmas publicadas son `request(SettlementCommand)` y `open(OpenDepositTrackingCommand)`.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | 10 estados → plan de operaciones; el depósito nunca se opera | `ReservationStatusPolicyTest` |
| Unitario (`application`) | prerrequisitos, identidad/duplicados, delegación por estado (solo estados conocidos) | `HandleReservationStatusServiceTest` (Mockito sobre los `port.in`) |
| Unitario (`infrastructure`) | mapeo texto → enum; valor desconocido → fallo lógico | `ReservationStatusMessageMapperTest` |
| Integración (Testcontainers PostgreSQL) | `UNIQUE` del log, bloqueo asesor, transacción única | `ReservationStatusLogPersistenceAdapterTest` |
| Integración (Testcontainers RabbitMQ) | `ack`/`nack`, reintentos, DLQ, mensaje inválido y estado desconocido | `ReservationStatusListenerTest` |
| Arquitectura | reglas §3.4 | `ArchitectureTest` (base y ampliación T022) |

**Pruebas de aceptación (CE-001…CE-007)**:

- **`ce001_decision_financiera_correcta_por_estado`** (T010): los 10 estados producen exactamente la operación esperada.
- **`ce002_cancelaciones_disparan_exactamente_las_operaciones_esperadas`** (T015).
- **`ce003_cero_operaciones_sobre_el_deposito_por_completada_o_estados_operativos`** (T019).
- **`ce004_fallos_por_informacion_ausente_se_registran_sin_ejecuciones_parciales`** (T016).
- **`ce005_completada_liquida_estandar_y_conserva_el_deposito`** (T020).
- **`ce006_cero_decisiones_sobre_danos_o_destino_del_deposito`** (T021).
- **`ce007_iniciada_pendiente_sin_reinicio_ttl_identidad_y_exclusion_mutua`** (T011): `INITIATED` sin operaciones, `PENDING` solo cobro, notificación repetida sin cobro adicional, un solo desenlace del depósito.

## Decisiones de diseño (D-UC07-xx)

Registro de las decisiones de diseño de este plan, con su contexto y estado. Los IDs `D-UC07-xx` son propios de este plan; los `D-xx` sin prefijo UC07 son decisiones del plan general (§9).

| ID | Descripción | Contexto | Decisión | Estado |
|---|---|---|---|---|
| **D-UC07-01** | Las piezas técnicas compartidas no deben coordinarse mediante tareas de otro caso de uso | Auditoría de planes | UC07 consume `FailureRecorderPort`, AMQP y puertos de bloqueo por sus contratos; no los redeclara ni referencia tareas externas | Aplicada |
| **D-UC07-02** | RF-009A incluye una **clave idempotente** en la identidad de operación, pero RNF-001 y el contrato no la envían (solo `Message-Id`); `reservation_status_log` sí tiene `idempotency_key` | SPEC 7 RF-009A vs RNF-001 vs contrato §2 | UC07 la deriva internamente de `reservationId` + `status` + `statusChangedAt`; los estados terminales deduplican por (reserva, estado, tipo de operación) y solo `PENDING` admite intentos nuevos; no se usa `Message-Id` (general-plan D-33, D-07) | Aplicada |
| **D-UC07-03** | El SPEC dice que el sistema **no persiste** el estado operativo; general-plan §4 define `reservation_status_log` | SPEC 7 Entidades Clave vs general-plan §4, D-25 | Prevalece D-25: solo idempotencia, no estado operativo | Aplicada |
| **D-UC07-04** | El contrato §5 dice "Nack con requeue o DLQ tras N intentos"; README §4.4 y general-plan §7.2 definen `nack` con reintentos y *backoff* y DLQ | `UC07-estado-reserva.md` §5 vs README §4.4 | Se sigue README §4.4: 5 intentos con *backoff* exponencial (base 1 s) y DLQ `<cola>.dlq` al agotarlos, con registro en `operational_failure` | Aplicada |
| **D-UC07-05** | `COMPLETED` (UC07) y `COMPLETED` (UC08) son valores distintos de dominios distintos | contratos UC07 y UC08 | Enumeraciones separadas: `ReservationStatus` (UC07) y `DisputeStatus` (UC08) | Aplicada |
| **D-UC07-06** | El SPEC 7 no menciona la apertura del seguimiento del depósito; general-plan §3.5 la asigna a UC07 (`OpenDepositTrackingUseCase`) | SPEC 7 RF-006/RF-007 vs general-plan §3.5 | Se mantiene, sin operación monetaria (RF-007) | Aplicada |
| **D-UC07-07** | Propiedad de `ReservationLockPort` (bloqueo asesor por reserva, D-15), que también necesitan UC08, UC09 y UC10 | general-plan §3.6 | `ReservationLockPort` pertenece al núcleo compartido (fundacional) y lo consumen UC07/UC08/UC09/UC10; UC07 no lo redeclara | Aplicada |
| **D-UC07-08** | Firmas publicadas de los puertos que UC07 consume de UC10 y UC08 | general-plan §3.5/§3.6 | `RequestSettlementUseCase.request(SettlementCommand)` (dueño UC10) y `OpenDepositTrackingUseCase.open(OpenDepositTrackingCommand)` (dueño UC08) | Aplicada |
| **D-UC07-09** | Nombres y topología de colas y DLQ | contrato §2; README §3.5 | Entrante `finance.reservation-status.v1`; internas `finance.charge-command.v1`, `finance.refund-command.v1`, `finance.settlement-command.v1` y `finance.gateway-notification.v1` (añadida por UC05; la fuente canónica es el README §3.5); DLQ `<cola>.dlq` | Aplicada |
| **D-UC07-10** | Alcance de `deposit_disposition` respecto de los depósitos reembolsados por cancelación | SPEC 7 RF-009B; general-plan D-34 | `deposit_disposition` (propiedad de UC08) registra todos los desenlaces del depósito (reembolso por cancelación, `REJECTED` = reembolso y `COMPLETED` = liquidación) con un único *compare-and-set*; UC07 lo invoca por `OpenDepositTrackingUseCase` | Aplicada |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad granular y verificable; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias se expresan mediante puertos, firmas y tablas, no mediante tareas externas.

### Phase 1: Setup

- [ ] **T026** · registrar dependencias AMQP y base de `ArchitectureTest`, con DLQ `<cola>.dlq`.

### Phase 2: Foundational

- [ ] **T027** · consumir `DomainException`, `FailureRecorderPort`, `ReservationLockPort` y configuración compartida por sus contratos; registro de fallos con `REQUIRES_NEW` y procedimiento de reproceso.

### Phase 3: US1 — Estados operativos, `INITIATED` y `PENDING` (HU1; RF-001, RF-002, RF-002A, RF-008, RF-009, RF-010; RNF-001, RNF-003; CE-001, CE-004, CE-007)

- [ ] **T001** · Verificar `reservation_status_log` contra la migración inicial única: `reservation_id`, `status`, `operation_type`, `idempotency_key`, `status_changed_at`, `processed_at`, `outcome`, unicidad por `(reservation_id, status, operation_type, idempotency_key)` (general-plan §4); UC07 no define migraciones propias (general-plan §4.2, D-31).
- [ ] **T002** · Dominio: `ReservationStatus` (10 valores), `FinancialOperationType`, `PlannedOperation` y `ReservationStatusPolicy` (estado → operaciones: ninguna / cobro / reembolso y/o liquidación / liquidación estándar). Nunca incluye una operación sobre el depósito por `COMPLETED`. · M: `none` · P: `pending`
- [ ] **T003** · Pruebas de `ReservationStatusPolicy`: los 10 estados con su plan exacto (cancelaciones de la tabla de §Reglas, punto 4); valor desconocido → sin plan. · M: `none` · P: `pending`
- [ ] **T004** · Puertos: `HandleReservationStatusUseCase` + `ReservationStatusCommand` (`status: ReservationStatus`), `ReservationStatusLogRepository` y consumo del único `ReservationLockPort` compartido.
- [ ] **T005** · Pruebas de `HandleReservationStatusService`: estados operativos sin delegación; `PENDING` → `ProcessChargeUseCase` con token; reserva sin información o sin cálculo → fallo sin delegar; `INITIATED` repetida sin efectos; montos leídos como `BigDecimal` (RNF-002). El servicio solo recibe estados conocidos; el desconocido se prueba en T008/T009.
- [ ] **T006** · `HandleReservationStatusService` (reconocimiento, prerrequisitos, `PENDING`, registro de fallos con `FailureRecorderPort`; deriva identidad de `(reservationId, status, operationType)`). Consume los puertos de UC05, UC09, UC10 y UC08 por nombre.
- [ ] **T007** · Persistencia: entidad, repositorio, mapper y adaptador de `reservation_status_log` (`INSERT … ON CONFLICT DO NOTHING`) y bloqueo asesor de PostgreSQL por `reservation_id`; prueba de integración (Testcontainers) del `UNIQUE` y del bloqueo. · M: `none` · P: `pending`
- [ ] **T008** · `ReservationStatusListener` + `ReservationStatusMessage` + `ReservationStatusMessageMapper` + `ReservationStatusMessagingConfig`: cola `finance.reservation-status.v1` y *binding* del contrato; el mapper convierte `status` al enum y lanza un fallo lógico si es desconocido; mensaje inválido/estado desconocido/`PENDING` sin token → `ack` + fallo, fallas transitorias → `nack` con 5 intentos y *backoff* exponencial (base 1 s) y DLQ `<cola>.dlq`, sin respuesta a Reservas (RF-009). · M: `none` · P: `pending`
- [ ] **T009** · Pruebas del *listener* (Testcontainers RabbitMQ): `ack` en errores lógicos, estado desconocido → `ack` + `operational_failure`, `nack` y DLQ en transitorios, ninguna respuesta publicada. · M: `none` · P: `pending`
- [ ] **T010** · **`ce001_decision_financiera_correcta_por_estado`** (CE-001). · M: `none` · P: `pending`
- [ ] **T011** · **`ce007_iniciada_pendiente_sin_reinicio_ttl_identidad_y_exclusion_mutua`** (CE-007). · M: `none` · P: `pending`

### Phase 4: US2 — Cancelaciones (HU2; RF-003…RF-005A, RF-009A, RF-010; CE-002, CE-004)

- [ ] **T012** · Pruebas con dobles de UC09 y UC10: las 4 cancelaciones disparan exactamente las operaciones de §Reglas, punto 4 (moderada y tardía con dos operaciones); sin valor total calculado → fallo y sin delegar. · M: `none` · P: `pending`
- [ ] **T013** · Implementar la delegación de cancelaciones: `RequestRefundUseCase` y `RequestSettlementUseCase.request(SettlementCommand)`, en una sola transacción con el `ReservationLockPort` del núcleo compartido.
- [ ] **T014** · Identidad única de operación: una fila por (estado, tipo de operación) antes de delegar; reenvío con la misma identidad → `ack` sin operación; pruebas de duplicados, de dos operaciones por notificación, de un segundo `PENDING` legítimo con distinto `statusChangedAt` y de la exclusión mutua del depósito (RF-009A, RF-009B). · M: `none` · P: `pending`
- [ ] **T015** · **`ce002_cancelaciones_disparan_exactamente_las_operaciones_esperadas`** (CE-002). · M: `none` · P: `pending`
- [ ] **T016** · **`ce004_fallos_por_informacion_ausente_se_registran_sin_ejecuciones_parciales`** (CE-004). · M: `none` · P: `pending`

### Phase 5: US3 — Completada y espera de la disputa (HU3; RF-006, RF-007, RF-009B; CE-003, CE-005, CE-006)

- [ ] **T017** · Pruebas: `COMPLETED` → liquidación estándar + apertura del seguimiento del depósito; ninguna operación monetaria sobre el depósito; sin monto de alquiler o seguro → fallo sin liquidaciones parciales; repetida → no se vuelve a liquidar. · M: `none` · P: `pending`
- [ ] **T018** · Implementar `COMPLETED`: `RequestSettlementUseCase.request(SettlementCommand)` + `OpenDepositTrackingUseCase.open(OpenDepositTrackingCommand)`; UC08 es dueño de `deposit_disposition` y `dispute_event_log` y registra el desenlace con un único *compare-and-set* [D-34].
- [ ] **T019** · **`ce003_cero_operaciones_sobre_el_deposito_por_completada_o_estados_operativos`** (CE-003). · M: `none` · P: `pending`
- [ ] **T020** · **`ce005_completada_liquida_estandar_y_conserva_el_deposito`** (CE-005). · M: `none` · P: `pending`
- [ ] **T021** · **`ce006_cero_decisiones_sobre_danos_o_destino_del_deposito`** (CE-006). · M: `none` · P: `pending`

### Phase 6: Polish & Cross-Cutting Concerns

- [ ] **T022** · ArchUnit: UC07 invoca a UC05/UC08/UC09/UC10 solo por `port.in`; el *listener* no usa repositorios; `domain` sin Spring/JPA. · M: `none` · P: `pending`
- [ ] **T023** · Alinear documentos: derivación de la clave idempotente en `UC07-estado-reserva.md` (D-UC07-02) y valores de `operation_type`/`outcome` en general-plan §4. · M: `none` · P: `pending`
- [ ] **T024** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending`
- [ ] **T025** · Prueba de integración extremo a extremo con los puertos reales de UC05/UC09/UC10/UC08 cuando sus adaptadores estén disponibles, y `./mvnw clean verify` con CE-001…CE-007. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
Núcleo compartido + puertos publicados por UC05/UC09/UC10/UC08 ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010 ─> T011
T012 ─> T013 ─> T014 ─> T015 ─> T016 ─> T017 ─> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025
```

- **Depende de**: las firmas publicadas de `ProcessChargeUseCase`, `RequestRefundUseCase`, `RequestSettlementUseCase`, `OpenDepositTrackingUseCase`, `ReservationInformationRepository` y del núcleo compartido.
- **No bloquea** a otros planes.
- **Riesgos de secuencia**: T008 depende de la topología AMQP común; T013 y T018 consumen las firmas publicadas de los puertos de UC09/UC10/UC08.

## Notes

- El SPEC 7 no define metas de rendimiento; la clave idempotente de RF-009A se deriva de `reservationId` + `status` + `statusChangedAt` (general-plan D-33).
- Las sucesiones imposibles de estados pertenecen a Reservas y no se validan aquí [SPEC casos extremos]. Los estados terminales son idempotentes por `(reserva, estado, tipo de operación)`; solo `PENDING` admite nuevos intentos [D-33].
- Etiquetas: `[SPEC]`, `[CONV]`.
- Los valores numéricos de los ejemplos son ilustrativos.

