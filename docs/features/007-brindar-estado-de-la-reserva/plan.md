# Implementation Plan: UC07 - Brindar el Estado de la Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

El Sistema de Reservas y Operaciones notifica por RabbitMQ el estado vigente de una reserva (uno de **10 estados**) y UC07 decide la operación financiera que corresponde: ninguna (`DISPONIBLE`, `INICIADA`, `RESERVADO`, `EN_NAVEGACION`), **cobro** (`PENDIENTE`, con el token de pago), **reembolso y/o liquidación** (las cuatro cancelaciones) o **liquidación estándar del alquiler con el depósito retenido** (`COMPLETADA`). UC07 es el orquestador: **no calcula montos ni habla con la pasarela**; invoca por `port.in` a UC05, UC09 y UC10 [SPEC HU1, HU2, HU3, RF-001…RF-010]. Es **unidireccional**: no devuelve nada a Reservas y los fallos se registran en `operational_failure` [SPEC RF-009, RF-010, RNF-003].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. Un *listener* AMQP (`finance.reservation-status.v1`, routing key `reservation.status.changed`, exchange `seashare.reservations`) deserializa el mensaje y llama a `HandleReservationStatusUseCase`. La decisión *estado → operaciones* es una política de dominio pura; el servicio toma un bloqueo asesor por reserva (general-plan D-15), valida los prerrequisitos, aplica la **identidad única de operación** (reserva + estado + tipo de operación + clave idempotente, RF-009A) mediante `reservation_status_log` y delega, todo en una transacción. Errores de lógica: `ack` + `operational_failure`; errores transitorios: `nack` con reintentos y DLQ (README §4.4). `reservation_status_log` es del bloque B. `RequestSettlementUseCase` (UC10) y `OpenDepositTrackingUseCase` (UC08) son del bloque C y se referencian por nombre.

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Brindar el estado de la reserva" únicamente con el *Sistema de Reservas y Operaciones*, sin `<<include>>`/`<<extend>>`; las operaciones que dispara provienen de los SPEC 5, 9 y 10 y de general-plan §3.5.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir estado de uno de 10 valores | `ReservationStatusListener`, `ReservationStatusMessage`, `ReservationStatus`, `ReservationStatusPolicy` | T002, T006, T008 | T003, T005, T009 |
| **RF-002** estados sin acción financiera | `ReservationStatusPolicy` (plan vacío); `INICIADA` reconocida como inicio del TTL sin administrarlo | T002, T006 | T003, T005, T011 |
| **RF-002A** `PENDIENTE` → "Procesar cobro" con el token | `HandleReservationStatusService` → `ProcessChargeUseCase` (UC05) | T006 | T005, T011 |
| **RF-003** flexible: 100 % del total | Plan: `RequestRefundUseCase(CANCELADO_FLEXIBLEMENTE)` | T013 | T012, T015 |
| **RF-004** moderada: reembolso 50 % alquiler + 100 % depósito y liquidación 50 % alquiler | Plan: `RequestRefundUseCase` + `RequestSettlementUseCase` | T013 | T012, T015 |
| **RF-005** tardía: liquidación 100 % alquiler y reembolso 100 % depósito | Plan: `RequestSettlementUseCase` + `RequestRefundUseCase` | T013 | T012, T015 |
| **RF-005A** anfitrión: 100 % del valor pagado | Plan: `RequestRefundUseCase(CANCELADO_POR_ANFITRION)` | T013 | T012, T015 |
| **RF-006** completada: liquidación estándar, depósito pendiente | Plan: `RequestSettlementUseCase` + `OpenDepositTrackingUseCase` (UC08) | T018 | T017, T020 |
| **RF-007** sin operación sobre el depósito por `COMPLETADA` | `ReservationStatusPolicy` no incluye operación sobre el depósito | T018 | T017, T019 |
| **RF-008** sin reembolso/liquidación del depósito en estados operativos, `PENDIENTE` y `COMPLETADA` | Política de dominio | T002, T006 | T003, T019 |
| **RF-009** sin respuesta a Reservas | Listener unidireccional; el caso de uso retorna `void` | T004, T008 | T009 |
| **RF-009A** identidad única de operación | `reservation_status_log` `UNIQUE(reservation_id, status, operation_type, idempotency_key)` | T001, T014 | T014, T011 |
| **RF-009B** exclusión mutua del depósito | UC07 no opera sobre el depósito en `COMPLETADA`; bloqueo por reserva; *compare-and-set* en C | T014, T018 | T017, T011 |
| **RF-010** registrar fallos, pendientes y no concluyentes | `FailureRecorderPort` (`operational_failure`) | T006, T013, T018 | T005, T012, T016 |
| **RNF-001** DTO mínimo | `ReservationStatusMessage` (HTTP/AMQP), `ReservationStatusCommand` | T004, T008 | T009 |
| **RNF-002** `BigDecimal` en lo monetario consultado | `reservation_information` leída con `Money`; UC07 no calcula | T006 | T005 |
| **RNF-003** manejo robusto de información ausente | Prerrequisitos antes de delegar | T006, T013, T018 | T005, T012, T016 |
| **CE-001** decisión correcta por estado | `ReservationStatusPolicy` + servicio | T002, T006, T013, T018 | **T010** |
| **CE-002** cancelaciones exactas | Plan por estado | T013 | **T015** |
| **CE-003** 0 operaciones sobre el depósito | Política | T018 | **T019** |
| **CE-004** fallos registrados sin ejecuciones parciales | Prerrequisitos + transacción única | T006, T013 | **T016** |
| **CE-005** liquidación al completar | Plan `COMPLETADA` | T018 | **T020** |
| **CE-006** 0 decisiones sobre daños | Política | T018 | **T021** |
| **CE-007** origen del TTL, identidad y exclusión mutua | `INICIADA`/`PENDIENTE` sin reinicio; log | T006, T014 | **T011** |
| **HU1** estados operativos, `INICIADA` y `PENDIENTE` (P1) | Fase 3 | T001–T011 | T003, T005, T009–T011 |
| **HU2** cancelaciones (P1) | Fase 4 | T012–T016 | T012–T016 |
| **HU3** completada y espera de disputa (P1) | Fase 5 | T017–T021 | T017–T021 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.1. Para UC07: Spring AMQP, Spring Data JPA, Flyway, MapStruct, ArchUnit. **Hoy no están en el `pom.xml`**; los agrega `UC11·T001` (incluye AMQP, Resilience4j, WireMock y Awaitility).
**Storage**: PostgreSQL 16+; `reservation_status_log` propia (general-plan §4); lee `reservation_information` (bloque A) [general-plan §3.5]
**Messaging**: RabbitMQ 3.13+, colas *quorum*, DLQ [general-plan]
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), Awaitility, ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: No definidos por el SPEC 7 `[NEEDS CLARIFICATION: se buscó en spec.md 007, general-plan.md y events/UC07-estado-reserva.md; ninguno fija metas de rendimiento]`
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
│   │   ├── ReservationLockPort.java             # T004  [CONV] bloqueo asesor por reserva (D-15); ver OQ-UC07-04
│   │   └── (usa) ReservationInformationRepository (A), FailureRecorderPort (UC11·T007)
│   ├── service/
│   │   └── HandleReservationStatusService.java  # T006, T013, T018
│   └── dto/
│       └── ReservationStatusCommand.java        # T004  = SolicitudEstadoReserva [SPEC]
└── infrastructure/
    ├── adapter/in/messaging/
    │   ├── ReservationStatusListener.java       # T008
    │   └── dto/ReservationStatusMessage.java    # T008  campos del contrato [SPEC RNF-001]
    ├── adapter/out/persistence/
    │   ├── ReservationStatusLogJpaEntity.java / ReservationStatusLogJpaRepository.java   # T007
    │   ├── ReservationStatusLogMapper.java      # T007  (MapStruct)
    │   ├── ReservationStatusLogPersistenceAdapter.java  # T007
    │   └── PostgresAdvisoryReservationLockAdapter.java  # T007
    └── config/
        └── ReservationStatusMessagingConfig.java  # T008  cola, binding, reintentos, DLQ [PEND OQ-UC07-06]

src/main/resources/db/migration/
└── V3x__create_reservation_status_log.sql        # T001  (rango V3x del bloque B)

src/test/java/com/seashare/seasharem3/
├── domain/service/ReservationStatusPolicyTest.java                      # T003
├── application/service/HandleReservationStatusServiceTest.java          # T005, T012, T017
├── infrastructure/adapter/in/messaging/ReservationStatusListenerTest.java  # T009
├── infrastructure/adapter/out/persistence/ReservationStatusLogPersistenceAdapterTest.java  # T007
└── acceptance/Uc07AcceptanceTest.java                                   # T010, T011, T014–T016, T019–T021
```

**Puertos consumidos** — UC07 llama solo por `port.in`:

| Puerto | Dueño | Estado de la firma |
|---|---|---|
| `ProcessChargeUseCase.process(ProcessChargeCommand)` | B (UC05) | Definida en `UC05·T006` |
| `RequestRefundUseCase.request(RefundRequestCommand)` | B (UC09) | Definida en `UC09·T006` |
| `RequestSettlementUseCase` | C (UC10) | **Pendiente**: la publica C al empezar UC10; hasta entonces UC07 usa un doble de prueba |
| `OpenDepositTrackingUseCase` | C (UC08) | **Pendiente**: la publica C al empezar UC08 |

**Puertos expuestos**: ninguno hacia otros bloques. `HandleReservationStatusUseCase` solo lo invoca el *listener* de UC07. Firma [CONV]:

```java
public interface HandleReservationStatusUseCase {
    void handle(ReservationStatusCommand command);
}
public record ReservationStatusCommand(
    UUID reservationId,
    String status,                          // texto crudo: un valor desconocido es una inconsistencia, no un error de parseo
    Instant statusChangedAt,                // fechaHoraEstado [SPEC RNF-001]
    String paymentTokenRef,                 // solo con PENDIENTE [SPEC RF-002A]
    String paymentMethodType,               // nullable
    Map<String, String> paymentMetadata     // nullable, no sensibles
) {}
```

La clave de operación (RF-009A) no viaja en el comando: el servicio la deriva de `reservationId` + `statusChangedAt` (ver §Reglas, punto 7).

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; UC07 invoca a UC05/UC09/UC10/UC08 **únicamente por su `port.in`** (general-plan §3.4, regla 4); el *listener* nunca accede a un repositorio.

## Reglas de negocio

Todas provienen del SPEC 7 y del contrato `UC07-estado-reserva.md` salvo indicación.

1. **Reconocimiento** [SPEC RF-001; contrato §3.1]: solo `DISPONIBLE`, `INICIADA`, `RESERVADO`, `EN_NAVEGACION`, `PENDIENTE`, `CANCELADO_FLEXIBLEMENTE`, `CANCELADO_MODERADAMENTE`, `CANCELADO_TARDIAMENTE`, `CANCELADO_POR_ANFITRION`, `COMPLETADA`. Cualquier otro valor: no se ejecuta ninguna operación y se registra la inconsistencia (`operational_failure`), con `ack`.
2. **Estados sin acción financiera** [SPEC RF-002, RF-008]: `DISPONIBLE`, `RESERVADO`, `EN_NAVEGACION` y `INICIADA` se reconocen sin operaciones. `INICIADA` marca el comienzo del TTL de 15 minutos; UC07 no lo administra ni lo reinicia.
3. **`PENDIENTE`** [SPEC RF-002A; HU1 escenario 3]: invoca `ProcessChargeUseCase` con el token recibido; no reinicia el TTL y no dispara reembolso ni liquidación. Si el mensaje no trae `payment_token_ref` (obligatorio con `PENDIENTE`, contrato §2) se trata como mensaje inválido: `ack` + `operational_failure`, sin llamar a UC05 **[CONV]**.
4. **Cancelaciones** [SPEC RF-003, RF-004, RF-005, RF-005A; contrato §3.4]:

| Estado | Operaciones que dispara UC07 |
|---|---|
| `CANCELADO_FLEXIBLEMENTE` | Reembolso del 100 % del valor total (alquiler, seguro, depósito); sin liquidación |
| `CANCELADO_MODERADAMENTE` | Reembolso del 50 % del alquiler + 100 % del depósito (sin seguro) **y** liquidación del 50 % del alquiler; resultados independientes |
| `CANCELADO_TARDIAMENTE` | Liquidación del 100 % del alquiler **y** reembolso del 100 % del depósito; sin reembolso de alquiler ni seguro |
| `CANCELADO_POR_ANFITRION` | Reembolso del 100 % del valor pagado; sin liquidación |

   UC07 solo elige la operación; los montos los calculan UC09 y UC10 desde registros internos.
5. **`COMPLETADA`** [SPEC RF-006, RF-007; general-plan §3.5]: liquidación estándar del alquiler (alquiler − comisión − seguro, a cargo de UC10) manteniendo el depósito asociado, y apertura del seguimiento del depósito en `OpenDepositTrackingUseCase` (UC08, con `statusChangedAt` como `completed_at`). **Ninguna operación monetaria sobre el depósito**: espera el estado de la disputa (UC08).
6. **Prerrequisitos** [SPEC RNF-003, casos extremos]: se exige `reservation_information` registrada y con los montos calculados por UC04 (alquiler, seguro, depósito y total; se escriben juntos, general-plan §4). Si faltan, no se ejecuta nada parcial: `operational_failure` y `ack`. El `PENDIENTE` exige además el valor total (lo valida UC05, RF-009).
7. **Identidad única de operación** [SPEC RF-009A; general-plan §3.5]: reserva + estado notificado + tipo de operación + clave idempotente. La clave idempotente se deriva de `reservationId` + `statusChangedAt` **[CONV]**: es igual en un reenvío de la misma transición y distinta en una nueva transición (por ejemplo, un segundo `PENDIENTE` tras un rechazo de pago permite un nuevo intento de cobro, UC05 RF-004A); no se usa `Message-Id` (general-plan D-07). Antes de cada operación se inserta una fila en `reservation_status_log` con `INSERT … ON CONFLICT DO NOTHING` **[CONV]**; si ya existía, la operación no se repite. Las notificaciones repetidas no duplican cobros (UC05 RNF-003), reembolsos ni liquidaciones. Una notificación con dos operaciones (moderada, tardía) crea **dos filas**. La misma clave se entrega como `operationKey` a UC05, UC09 y UC10.
8. **Persistencia** [SPEC Entidades Clave; general-plan D-25]: el estado operativo de la reserva **no** se persiste; `reservation_status_log` solo guarda lo necesario para idempotencia, y solo para los estados que disparan operaciones monetarias (`PENDIENTE`, las cancelaciones y `COMPLETADA`) **[CONV]**. `operation_type` toma los valores `CHARGE`, `REFUND` y `SETTLEMENT`; `outcome` es un texto corto sin valores fijados **[CONV]**.
9. **Concurrencia** [general-plan D-15]: bloqueo asesor por reserva y **una sola transacción** por mensaje (log + delegaciones), de modo que las dos operaciones de una cancelación moderada/tardía se confirman o se revierten juntas.
10. **Unidireccional y errores** [SPEC RF-009, RF-010; README §4.4]: nunca se responde a Reservas. Mensaje inválido, estado desconocido, reserva sin información o sin cálculo → `ack` + `operational_failure`. Falla transitoria (BD, broker) → `nack` con reintentos y *backoff* (3–5) y, al agotarlos, DLQ y registro en `operational_failure` **[general-plan §7.2]**. Mensaje repetido (misma identidad) → `ack` sin operación adicional.
11. **No definido por el SPEC 7** `[NEEDS CLARIFICATION]` (siguen abiertas): OQ-UC07-03, OQ-UC07-04, OQ-UC07-05 y OQ-UC07-06.

## Contratos de API

UC07 **no expone endpoints REST**. Contrato: `contracts/events/UC07-estado-reserva.md` [general-plan §6].

- **Exchange** `seashare.reservations` (`topic`, durable), **routing key** `reservation.status.changed`, **cola consumidora** `finance.reservation-status.v1` [contrato §2; README §3.5].
- **Propiedades AMQP**: `Content-Type: application/json`, `Message-Id` obligatorio (no se usa para deduplicar: general-plan D-07), `delivery_mode 2`.
- **Cuerpo**: `reservation_id` (UUID), `status`, `status_changed_at` (ISO 8601), `payment_token_ref` (obligatorio con `PENDIENTE`), `payment_method_type`, `payment_metadata` (opcionales).
- **Respuesta**: ninguna.

| Situación | Acción | Origen |
|---|---|---|
| Mensaje inválido, estado desconocido, reserva sin información o sin cálculo | `ack` + `operational_failure` | README §4.4 |
| Falla transitoria (BD, broker) | `nack` con reintentos y *backoff*; DLQ al agotarlos | README §4.4; general-plan §7.2 |
| Mensaje repetido (misma identidad) | `ack` sin operación | README §4.4; SPEC casos extremos |

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %. Hasta que C publique sus puertos, `RequestSettlementUseCase` y `OpenDepositTrackingUseCase` se sustituyen por dobles.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | 10 estados → plan de operaciones; el depósito nunca se opera | `ReservationStatusPolicyTest` |
| Unitario (`application`) | prerrequisitos, estado desconocido, identidad/duplicados, delegación por estado | `HandleReservationStatusServiceTest` (Mockito sobre los `port.in`) |
| Integración (Testcontainers PostgreSQL) | `UNIQUE` del log, bloqueo asesor, transacción única | `ReservationStatusLogPersistenceAdapterTest` |
| Integración (Testcontainers RabbitMQ) | `ack`/`nack`, reintentos, DLQ, mensaje inválido | `ReservationStatusListenerTest` |
| Arquitectura | reglas §3.4 | `ArchitectureTest` (UC11·T003, ampliado en T022) |

**Pruebas de aceptación (CE-001…CE-007)**:

- **`ce001_decision_financiera_correcta_por_estado`** (T010): los 10 estados producen exactamente la operación esperada.
- **`ce002_cancelaciones_disparan_exactamente_las_operaciones_esperadas`** (T015).
- **`ce003_cero_operaciones_sobre_el_deposito_por_completada_o_estados_operativos`** (T019).
- **`ce004_fallos_por_informacion_ausente_se_registran_sin_ejecuciones_parciales`** (T016).
- **`ce005_completada_liquida_estandar_y_conserva_el_deposito`** (T020).
- **`ce006_cero_decisiones_sobre_danos_o_destino_del_deposito`** (T021).
- **`ce007_iniciada_pendiente_sin_reinicio_ttl_identidad_y_exclusion_mutua`** (T011): `INICIADA` sin operaciones, `PENDIENTE` solo cobro, notificación repetida sin cobro adicional, un solo desenlace del depósito.

## Discrepancias y puntos abiertos

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC07-01** | `operational_failure`, `FailureRecorderPort` y las dependencias AMQP debían quedar definidos en las tareas compartidas de UC11 | Reparto de piezas compartidas vs plan UC11 | Se referencian por nombre: `UC11·T001` (dependencias), `UC11·T002` (tablas), `UC11·T007` (puertos) y `UC11·T046` (adaptadores). **Cierre:** el plan UC11 ya los incluye | **Cerrada** |
| **D-UC07-02** | RF-009A incluye una **clave idempotente** en la identidad de operación, pero RNF-001 y el contrato no la envían (solo `Message-Id`); `reservation_status_log` sí tiene `idempotency_key` | SPEC 7 RF-009A vs RNF-001 vs contrato §2 | UC07 la deriva internamente de `reservationId` + `statusChangedAt`; no se usa `Message-Id` (D-07). **Cierre:** decisión interna de UC07 | **Cerrada** |
| **D-UC07-03** | El SPEC dice que el sistema **no persiste** el estado operativo; general-plan §4 define `reservation_status_log` | SPEC 7 Entidades Clave vs general-plan §4, D-25 | Prevalece D-25: solo idempotencia, no estado operativo | Resuelta |
| **D-UC07-04** | El contrato §5 dice "Nack con requeue o DLQ tras N intentos"; README §4.4 y general-plan §7.2 definen `nack` con reintentos y *backoff* (3–5) y DLQ | `UC07-estado-reserva.md` §5 vs README §4.4 | Se sigue README §4.4 | Resuelta |
| **D-UC07-05** | `COMPLETADA` (UC07) y `COMPLETADO` (UC08) son valores distintos de dominios distintos | contratos UC07 y UC08 | Enumeraciones separadas (`ReservationStatus` y el de disputas, del bloque C) | Resuelta |
| **D-UC07-06** | El SPEC 7 no menciona la apertura del seguimiento del depósito; general-plan §3.5 la asigna a UC07 (`OpenDepositTrackingUseCase`) | SPEC 7 RF-006/RF-007 vs general-plan §3.5 | Se mantiene, sin operación monetaria (RF-007) | **Abierta** (confirmar con C) |

## Preguntas abiertas (OQ-UC07-xx)

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC07-01** | ¿De dónde sale la clave idempotente de RF-009A? Si se deriva solo de reserva + estado, un segundo `PENDIENTE` legítimo tras un rechazo de pago se confundiría con una repetición e impediría el reintento de cobro (UC05 RF-004A) | T001, T006, T014 | Derivarla de `reservationId` + `status_changed_at`: igual en un reenvío, distinta en una nueva transición **[CONV]** | **Cerrada** → **Adoptada.** |
| **OQ-UC07-02** | Valores de `operation_type` y `outcome` de `reservation_status_log`, y si los estados sin operación monetaria se registran | T001, T014 | `CHARGE`/`REFUND`/`SETTLEMENT`; solo estados monetarios; `outcome` sin valores fijados | **Cerrada** → **Adoptada.** |
| **OQ-UC07-03** | RF-009B: ¿el seguimiento del depósito (`deposit_disposition`, del bloque C) también cubre los depósitos reembolsados por cancelación? Hoy solo se abre con `COMPLETADA` | T014, T018 | No; solo `COMPLETADA`. Enlaza con OQ-UC09-03 | **Abierta** (avisar a C) |
| **OQ-UC07-04** | Dueño de `ReservationLockPort` (bloqueo asesor por reserva, D-15), que también necesitan UC08 y UC10 | T004, T007 | B (UC07) lo define y C lo referencia | **Abierta** (acordar con C) |
| **OQ-UC07-05** | Firmas de `RequestSettlementUseCase` y `OpenDepositTrackingUseCase` (las publica C) | T013, T018 | Dobles de prueba hasta entonces | **Abierta** |
| **OQ-UC07-06** | Nombres/topología de reintentos y DLQ de `finance.reservation-status.v1` (equivale a OQ-09 del plan general) | T008 | Sin nombre fijado | **Abierta** |
| **OQ-UC07-07** | `PENDIENTE` sin `payment_token_ref`: el contrato lo marca obligatorio pero el SPEC no define la reacción | T008 | `ack` + `operational_failure` sin llamar a UC05 | **Cerrada** → **Adoptada.** |

**`[NEEDS CLARIFICATION]` consolidado (abiertas):** OQ-UC07-03, OQ-UC07-04, OQ-UC07-05 y OQ-UC07-06. Discrepancia abierta: D-UC07-06.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). Las fases 1 y 2 son **Compartido** y remiten a tareas de UC11; las tareas locales empiezan en la Fase 3.

### Phase 1: Setup — **Compartido**

- [ ] `UC11·T001`–`UC11·T003`: starters (incluye AMQP, Resilience4j, WireMock y Awaitility), migración V1 y `ArchitectureTest`.

### Phase 2: Foundational — **Compartido**

- [ ] `UC11·T004`–`UC11·T009`: excepciones base, `ProblemDetailsConfig`, `SecurityConfig`.
- [ ] `UC11·T046`–`UC11·T047`: `operational_failure`, `FailureRecorderPort` y sus adaptadores.

### Phase 3: US1 — Estados operativos, `INICIADA` y `PENDIENTE` (HU1; RF-001, RF-002, RF-002A, RF-008, RF-009, RF-010; RNF-001, RNF-003; CE-001, CE-004, CE-007)

- [ ] **T001** · Migración `V3x__create_reservation_status_log.sql`: `reservation_id`, `status`, `operation_type` (`CHARGE`/`REFUND`/`SETTLEMENT`), `idempotency_key`, `status_changed_at`, `processed_at`, `outcome` (texto corto sin valores fijados); `UNIQUE(reservation_id, status, operation_type, idempotency_key)` (general-plan §4). · M: `none` · P: `pending`
- [ ] **T002** · Dominio: `ReservationStatus` (10 valores), `FinancialOperationType`, `PlannedOperation` y `ReservationStatusPolicy` (estado → operaciones: ninguna / cobro / reembolso y/o liquidación / liquidación estándar). Nunca incluye una operación sobre el depósito por `COMPLETADA`. · M: `none` · P: `pending`
- [ ] **T003** · Pruebas de `ReservationStatusPolicy`: los 10 estados con su plan exacto (cancelaciones de la tabla de §Reglas, punto 4); valor desconocido → sin plan. · M: `none` · P: `pending`
- [ ] **T004** · Puertos: `HandleReservationStatusUseCase` + `ReservationStatusCommand`, `ReservationStatusLogRepository`, `ReservationLockPort` (OQ-UC07-04). · M: `none` · P: `pending`
- [ ] **T005** · Pruebas de `HandleReservationStatusService`: estados operativos sin delegación; `PENDIENTE` → `ProcessChargeUseCase` con token; estado desconocido → `operational_failure`; reserva sin información o sin cálculo → fallo sin delegar; `INICIADA` repetida sin efectos; montos leídos como `BigDecimal` (RNF-002). · M: `none` · P: `pending`
- [ ] **T006** · `HandleReservationStatusService` (reconocimiento, prerrequisitos, `PENDIENTE`, registro de fallos con `FailureRecorderPort`; deriva la clave de operación de `reservationId` + `statusChangedAt`). Depende de `UC05·T006` y de `UC11·T007`. · M: `none` · P: `pending`
- [ ] **T007** · Persistencia: entidad, repositorio, mapper y adaptador de `reservation_status_log` (`INSERT … ON CONFLICT DO NOTHING`) y bloqueo asesor de PostgreSQL por `reservation_id`; prueba de integración (Testcontainers) del `UNIQUE` y del bloqueo. · M: `none` · P: `pending`
- [ ] **T008** · `ReservationStatusListener` + `ReservationStatusMessage` + `ReservationStatusMessagingConfig`: cola y *binding* del contrato, mensaje inválido/`PENDIENTE` sin token → `ack` + fallo, fallas transitorias → `nack` con reintentos y DLQ, sin respuesta a Reservas (RF-009). Condicionada a **OQ-UC07-06**. · M: `none` · P: `pending`
- [ ] **T009** · Pruebas del *listener* (Testcontainers RabbitMQ): `ack` en errores lógicos, `nack` y DLQ en transitorios, ninguna respuesta publicada. · M: `none` · P: `pending`
- [ ] **T010** · **`ce001_decision_financiera_correcta_por_estado`** (CE-001). · M: `none` · P: `pending`
- [ ] **T011** · **`ce007_iniciada_pendiente_sin_reinicio_ttl_identidad_y_exclusion_mutua`** (CE-007). · M: `none` · P: `pending`

### Phase 4: US2 — Cancelaciones (HU2; RF-003…RF-005A, RF-009A, RF-010; CE-002, CE-004)

- [ ] **T012** · Pruebas con dobles de UC09 y UC10: las 4 cancelaciones disparan exactamente las operaciones de §Reglas, punto 4 (moderada y tardía con dos operaciones); sin valor total calculado → fallo y sin delegar. · M: `none` · P: `pending`
- [ ] **T013** · Implementar la delegación de cancelaciones: `RequestRefundUseCase` (`UC09·T006`) y `RequestSettlementUseCase` (C; OQ-UC07-05), en una sola transacción con bloqueo por reserva. · M: `none` · P: `pending`
- [ ] **T014** · Identidad única de operación: una fila por (estado, tipo de operación) antes de delegar; reenvío con la misma identidad → `ack` sin operación; pruebas de duplicados, de dos operaciones por notificación, de un segundo `PENDIENTE` legítimo con distinto `statusChangedAt` y de la exclusión mutua del depósito (RF-009A, RF-009B). · M: `none` · P: `pending`
- [ ] **T015** · **`ce002_cancelaciones_disparan_exactamente_las_operaciones_esperadas`** (CE-002). · M: `none` · P: `pending`
- [ ] **T016** · **`ce004_fallos_por_informacion_ausente_se_registran_sin_ejecuciones_parciales`** (CE-004). · M: `none` · P: `pending`

### Phase 5: US3 — Completada y espera de la disputa (HU3; RF-006, RF-007, RF-009B; CE-003, CE-005, CE-006)

- [ ] **T017** · Pruebas: `COMPLETADA` → liquidación estándar + apertura del seguimiento del depósito; ninguna operación monetaria sobre el depósito; sin monto de alquiler o seguro → fallo sin liquidaciones parciales; repetida → no se vuelve a liquidar. · M: `none` · P: `pending`
- [ ] **T018** · Implementar `COMPLETADA`: `RequestSettlementUseCase` + `OpenDepositTrackingUseCase` (UC08; OQ-UC07-05, D-UC07-06). · M: `none` · P: `pending`
- [ ] **T019** · **`ce003_cero_operaciones_sobre_el_deposito_por_completada_o_estados_operativos`** (CE-003). · M: `none` · P: `pending`
- [ ] **T020** · **`ce005_completada_liquida_estandar_y_conserva_el_deposito`** (CE-005). · M: `none` · P: `pending`
- [ ] **T021** · **`ce006_cero_decisiones_sobre_danos_o_destino_del_deposito`** (CE-006). · M: `none` · P: `pending`

### Phase 6: Polish & Cross-Cutting Concerns

- [ ] **T022** · ArchUnit: UC07 invoca a UC05/UC08/UC09/UC10 solo por `port.in`; el *listener* no usa repositorios; `domain` sin Spring/JPA. · M: `none` · P: `pending`
- [ ] **T023** · Alinear documentos: derivación de la clave idempotente en `UC07-estado-reserva.md` (D-UC07-02) y valores de `operation_type`/`outcome` en general-plan §4. · M: `none` · P: `pending`
- [ ] **T024** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending`
- [ ] **T025** · Prueba de integración extremo a extremo con los puertos reales de UC05/UC09/UC10/UC08 cuando C los entregue, y `./mvnw clean verify` con CE-001…CE-007. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
UC11·T001–T009 + UC11·T046 + UC05·T006 + UC09·T006 ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010 ─> T011
T012 ─> T013 ─> T014 ─> T015 ─> T016 ─> T017 ─> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025
```

- **Depende de**: `UC05·T006` y `UC09·T006` (firmas), de bloque A para `reservation_information` y de bloque C para `RequestSettlementUseCase` y `OpenDepositTrackingUseCase` (T013, T018, T025).
- **No bloquea** a otros planes.
- **Riesgos de secuencia**: T008 depende de OQ-UC07-06; T013 y T018 de las firmas de C.

## Notes

- El SPEC 7 no define metas de rendimiento; la clave idempotente de RF-009A se deriva de `reservationId` + `statusChangedAt`.
- Las sucesiones imposibles de estados (por ejemplo, dos cancelaciones distintas para la misma reserva) pertenecen a Reservas y no se validan aquí [SPEC casos extremos]; como la identidad de operación incluye el estado, dos cancelaciones distintas no se deduplicarían entre sí.
- Etiquetas: `[SPEC]`, `[CONV]`, `[PEND]`/`[NEEDS CLARIFICATION]`.
- Los valores numéricos de los ejemplos son ilustrativos.

