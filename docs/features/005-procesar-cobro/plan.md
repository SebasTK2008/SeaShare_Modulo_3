# Implementation Plan: UC05 - Procesar Cobro

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC05 envía a la Pasarela de Pago (Mercado Pago) la solicitud de autorización o cobro por el **valor total ya calculado** de una reserva (alquiler + seguro + depósito, un único monto), registra la operación operativa en curso como `ChargeIntent` y, cuando la pasarela confirma un cobro o captura exitosos, crea el `ChargeRecord` inmutable de auditoría. También registra el rechazo, la falla de comunicación y la **expiración** de autorizaciones no capturadas [SPEC HU1, HU2, RF-001…RF-015, RNF-003, RNF-005].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. UC05 **no tiene endpoint público**: lo invoca UC07 con el estado `PENDIENTE` y el token del medio de pago (UC07 RF-002A; general-plan D-09). Persiste la intención y publica el comando por *outbox* en la misma transacción; un *worker* llama a la pasarela (general-plan D-08). El resultado entra por tres vías que convergen en `RegisterChargeResultUseCase`: respuesta técnica de la llamada, webhook (que dispara una consulta activa a la API de Mercado Pago) y conciliación. Cada intento de cobro tiene **su propia clave idempotente** y una reserva puede acumular varias `ChargeIntent` [SPEC RF-004A; general-plan D-28]. `ChargeIntent` y `ChargeRecord` son del bloque B: los demás bloques las referencian por nombre.

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Procesar cobro" con los actores *Arrendatario* y *Pasarela de pago*, sin relaciones `<<include>>`/`<<extend>>`. El disparo desde "Brindar el estado de la reserva" proviene del SPEC 7 (RF-002A), no del diagrama.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud con token | `ProcessChargeUseCase`, `ProcessChargeCommand`, `ProcessChargeService` | T006, T008 | T007 |
| **RF-002** recuperar valor total registrado | `ProcessChargeService` + `ReservationInformationRepository` (de A) | T008 | T007, T026 |
| **RF-003** enviar autorización/cobro por el total | `SendChargeToGatewayService`, `MercadoPagoChargeAdapter.send` | T012, T014 | T013, T015 |
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
| **RF-014** expiración de autorizaciones | `ExpireStaleAuthorizationsService`, `AuthorizationExpiryJob` | T022 | T022 |
| **RF-015** un único monto (sin solicitudes por componente) | `ProcessChargeService`, `MercadoPagoPaymentRequest.transaction_amount` | T008, T012 | T007, T013 |
| **RNF-001** DTOs con la pasarela | `MercadoPagoPaymentRequest/Response`, `ChargeGatewayRequest/Result` | T012 | T013 |
| **RNF-002** `BigDecimal` | Campos monetarios de `ChargeIntent`/`ChargeRecord`; `NUMERIC(18,4)` | T002, T003 | T004, T013 |
| **RNF-003** errores, idempotencia, conciliación | Clave idempotente por intento; `FALLA_COMUNICACION`; `ReconcileChargeIntentsService` | T014, T023 | T015, T024, T028 |
| **RNF-004** minimización de datos de pago | Esquema sin PAN/CVV; token fuera de logs | T002, T012 | T016 |
| **RNF-005** vigencia monitoreada sin consulta manual | `AuthorizationExpiryJob` | T022 | T022 |
| **CE-001** montos sin discrepancias | `ChargeIntent`, `RegisterChargeResultService` | T018 | **T026** |
| **CE-002** trazabilidad intención → registro | FK `charge_intent_id`; varias intenciones | T002, T018 | **T027** |
| **CE-003** resiliencia ante fallas | Worker + conciliación | T014, T023 | **T028** |
| **CE-004** rechazados no son cobros | `RegisterChargeResultService` | T018 | **T029** |
| **CE-005** sin datos sensibles | Esquema + logs | T002, T012 | **T016** |
| **CE-006** trazabilidad enmascarada | `PaymentMethodReference` | T018 | **T025** |
| **CE-007** expiración registrada | `AuthorizationExpiryJob` | T022 | **T022** |
| **HU1** enviar solicitud de cobro (P1) | Fase 3 | T001–T016 | T007, T010, T013, T015, T016 |
| **HU2** registrar resultado (P1) | Fase 4 | T017–T029 | T017–T029 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.1. Para UC05: Spring Data JPA, Spring AMQP (cola interna del comando, *outbox*), Resilience4j (timeout/*circuit breaker*, §7.2), cliente HTTP, Flyway, MapStruct, ArchUnit; ShedLock opcional (§5.3). **Hoy no están en el `pom.xml`** (solo `data-jpa`, `postgresql`, `mapstruct`, `lombok-mapstruct-binding` y Testcontainers); `UC11·T001` agrega web, validation, security, flyway, test, ArchUnit, AMQP, Resilience4j, WireMock y Awaitility.
**Storage**: PostgreSQL 16+; `NUMERIC(18,4)` para dinero, `timestamptz` para instantes [general-plan §4]
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), WireMock (pasarela), Awaitility, MockMvc, ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: No definidos por el SPEC 5 `[NEEDS CLARIFICATION: se buscó en spec.md 005, general-plan.md y contratos de pasarela; solo existe el timeout de la pasarela de 10 s del general-plan §7.2 [CONV]]`
**Constraints**: `BigDecimal` con 4 decimales internos [SPEC RNF-002]; solo token, nunca PAN/CVV/vencimiento [SPEC RNF-004]; un timeout no es un fallo [SPEC RNF-003]; `RegistroDeCobro` solo con cobro/captura confirmados [SPEC RF-006, RF-008; general-plan §3.5]; la comisión y el seguro congelados no se tocan aquí
**Scale/Scope**: 2 tablas propias (`charge_intent`, `charge_record`); 0 endpoints REST propios; 1 endpoint de entrada de la pasarela (compartido, ver OQ-UC05-07); 3 jobs (expiración, conciliación, relay); 15 RF + 5 RNF + 7 CE + 2 HU

**Estado actual del repositorio (relevante)**: existen `SeashareM3Application`, `application.properties` (solo `spring.application.name`), `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** `domain`/`application`/`infrastructure`, migraciones, `Dockerfile`, `docker-compose.yml` ni reglas ArchUnit; tampoco `docs/features/005-procesar-cobro/plan.md` hasta este documento.

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
│   │   ├── ChargeIntentStatus.java              # T003  [general-plan §10]
│   │   ├── PaymentMethodReference.java          # T003  token + tipo + últimos 4 [SPEC RF-001, RF-012]
│   │   └── IdempotencyKey.java                  # T003  [general-plan §3.3]; ver OQ-UC05-09
│   └── exception/
│       ├── ReservationValueNotCalculatedException.java   # T004 [SPEC RF-009]
│       └── InvalidPaymentTokenException.java             # T004 [SPEC RF-013]
├── application/
│   ├── port/in/
│   │   ├── ProcessChargeUseCase.java            # T006  [general-plan §3.5]  (puerto expuesto)
│   │   ├── SendChargeToGatewayUseCase.java      # T006  [CONV] el worker es un adaptador y solo puede invocar port.in (general-plan §3.4)
│   │   ├── RegisterChargeResultUseCase.java     # T006  [general-plan §3.5]
│   │   ├── ExpireStaleAuthorizationsUseCase.java# T006  [general-plan §3.5]
│   │   └── ReconcileChargeIntentsUseCase.java   # T006  [CONV] job de conciliación
│   ├── port/out/
│   │   ├── ChargeIntentRepository.java          # T005  (lo consume UC06, UC09, UC10)
│   │   ├── ChargeRecordRepository.java          # T005  [CONV]
│   │   └── ChargeGatewayPort.java               # T005  [general-plan §3.5]
│   ├── service/
│   │   ├── ProcessChargeService.java            # T008
│   │   ├── SendChargeToGatewayService.java      # T014
│   │   ├── RegisterChargeResultService.java     # T018
│   │   ├── ExpireStaleAuthorizationsService.java# T022
│   │   └── ReconcileChargeIntentsService.java   # T023
│   └── dto/
│       ├── ProcessChargeCommand.java            # T006
│       ├── ChargeGatewayRequest.java            # T012  = SolicitudCobroPasarela [SPEC]
│       └── ChargeGatewayResult.java             # T012  = ResultadoCobroPasarela [SPEC]
└── infrastructure/
    ├── adapter/in/messaging/
    │   └── ChargeCommandListener.java           # T014  worker de la cola interna [general-plan D-08]
    ├── adapter/in/webhook/
    │   ├── GatewayWebhookController.java        # T019  (compartido; OQ-UC05-07)
    │   └── MercadoPagoSignatureVerifier.java    # T019
    ├── adapter/in/scheduler/
    │   ├── AuthorizationExpiryJob.java          # T022  [general-plan §5.2]
    │   └── GatewayReconciliationJob.java        # T023  (compartido con UC09/UC10; OQ-UC05-07)
    ├── adapter/out/gateway/
    │   ├── MercadoPagoChargeAdapter.java        # T012, T021
    │   └── dto/{MercadoPagoPaymentRequest, MercadoPagoPaymentResponse}.java   # T012
    └── adapter/out/persistence/
        ├── ChargeIntentJpaEntity.java / ChargeRecordJpaEntity.java            # T009
        ├── ChargeIntentJpaRepository.java / ChargeRecordJpaRepository.java    # T009
        ├── ChargePersistenceMapper.java         # T009  (MapStruct)
        └── ChargeIntentPersistenceAdapter.java / ChargeRecordPersistenceAdapter.java  # T009

src/main/resources/db/migration/
└── V3x__create_charge_intent_and_charge_record.sql   # T002  (rango V3x del bloque B)

src/test/java/com/seashare/seasharem3/
├── domain/model/{ChargeIntentTest, ChargeRecordTest}.java                    # T004
├── application/service/{ProcessChargeServiceTest, RegisterChargeResultServiceTest, ...}.java  # T007, T017
├── infrastructure/adapter/in/webhook/GatewayWebhookControllerTest.java       # T019
├── infrastructure/adapter/out/gateway/MercadoPagoChargeAdapterTest.java      # T013, T021
├── infrastructure/adapter/out/persistence/ChargePersistenceAdapterTest.java  # T010
└── acceptance/Uc05AcceptanceTest.java                                        # T016, T022, T025–T029
```

**Puertos expuestos**: `ProcessChargeUseCase` es llamado por **UC07** (mismo bloque). Firma [CONV], a publicar en el canal al terminar T006:

```java
public interface ProcessChargeUseCase {
    void process(ProcessChargeCommand command);
}
public record ProcessChargeCommand(
    UUID reservationId,
    String paymentTokenRef,                 // obligatorio con PENDIENTE [SPEC UC07 RF-002A]
    String paymentMethodType,               // nullable [SPEC RF-001]
    Map<String, String> paymentMetadata,    // nullable, solo no sensibles [SPEC RF-001, RF-012]
    String operationKey                     // identidad de operación de UC07 (RF-009A): derivada de reservationId + statusChangedAt [CONV]
) {}
```

Lanza `ReservationValueNotCalculatedException` (RF-009) o `InvalidPaymentTokenException` (RF-013); UC07 las captura y las registra en `operational_failure` (UC07 RF-010, RNF-003). Un `operationKey` repetido es idempotente: no crea otra intención (ver §Reglas, punto 3). `ChargeIntentRepository` (puerto de salida) lo consumen UC06 (B), UC09 (B) y UC10 (C): UC10 debe referenciarlo por nombre; los métodos requeridos por UC06/UC09 están en T005.

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; el webhook, el listener y los jobs solo invocan `application.port.in`; las entidades JPA no salen de `adapter/out/persistence`; los DTOs HTTP viven en `adapter.in` y `application` usa `command`/`result` [general-plan §3.4].

## Reglas de negocio

Todas provienen del SPEC 5 salvo indicación.

1. **Disparo y entrada** [SPEC RF-001; UC07 RF-002A]: UC07 invoca `ProcessChargeUseCase` con el estado `PENDIENTE`. UC05 no valida ni administra el TTL (lo administra Reservas).
2. **Valor a cobrar** [SPEC RF-002, RF-015]: un **único monto** = valor total registrado por UC04 (alquiler + seguro + depósito). Ejemplo ilustrativo (contrato UC04): alquiler `900000.00` + seguro `60000.00` + depósito `30000.00` = `990000.00`. No se generan solicitudes separadas por componente; la distinción se conserva en `charge_record` (`rental_amount`, `insurance_amount`, `deposit_amount`). Sin valor total registrado → `ReservationValueNotCalculatedException` y **no** se contacta a la pasarela [RF-009].
3. **Una intención por intento** [SPEC RF-004, RF-004A; general-plan D-28]: cada invocación con una `operationKey` nueva crea una `ChargeIntent` con su propia clave idempotente (reintento tras rechazo o expiración = intención nueva). La clave idempotente del intento se deriva de forma determinística de `reservationId` + `operationKey` **[CONV]**; la unicidad la da `UNIQUE(idempotency_key)`, no la reserva. Una invocación con una clave ya existente no crea otra intención ni cobra dos veces (UC07, caso extremo "pendiente repetido").
4. **Envío asíncrono** [general-plan D-08, §7.2]: `ProcessChargeService` persiste la intención en `PENDIENTE_ENVIO` y el mensaje *outbox* en la **misma transacción**; el *worker* envía a la pasarela (timeout 10 s [CONV]). El reintento técnico reutiliza **la misma** clave idempotente [SPEC RNF-003].
5. **Resultado y estados** [general-plan §10]: `PENDIENTE_ENVIO`, `EN_PROCESO`, `AUTORIZADO`, `CAPTURADO`, `RECHAZADO`, `CANCELADO`, `EXPIRADO`, `FALLA_COMUNICACION`. Estados de Mercado Pago del contrato: `approved`, `in_process`, `rejected` (respuesta); `approved`, `rejected`/`expired` (webhook `payment.updated`).
   - Timeout, `502`, `503` o caída → `FALLA_COMUNICACION`; **no** se interpreta como rechazo ni como éxito [SPEC RNF-003, caso extremo].
   - `400` por validación de token: no reintentable [contrato §5]; no se marca la reserva como cobrada [SPEC RF-013]. La intención queda en `RECHAZADO` con `status_detail` técnico y no se crea `ChargeRecord` **[CONV]**.
6. **Registro inmutable** [SPEC RF-006, RF-008, RF-010, RF-004B; general-plan §3.5]: `ChargeRecord` solo cuando el cobro/captura queda confirmado (`CAPTURADO`); nunca para `RECHAZADO`, `CANCELADO`, `EXPIRADO`, `EN_PROCESO` ni `FALLA_COMUNICACION`. Se crea en la **misma transacción** que la actualización de la intención, con `charge_intent_id`, y copia `owner_id` y `boat_id` de `reservation_information`. Si la integración solo autoriza (sin captura), el registro espera la captura **[PEND OQ-UC05-02]**.
7. **Datos de pago** [SPEC RF-011, RF-012, RNF-004]: solo se recibe/envía/persiste el token (`payment_token_ref`), el tipo de medio, los últimos 4 y metadatos no sensibles; el token no se escribe en logs. El `outbox_message` lleva solo el identificador de la intención (el token se lee de `charge_intent` en el *worker*) **[CONV]**.
8. **Expiración** [SPEC RF-014, RNF-005; general-plan §5.2]: `AuthorizationExpiryJob` marca `EXPIRADO` las intenciones `AUTORIZADO` con `authorization_expires_at` vencido, registra el hecho y deja la intención disponible para conciliación o un nuevo intento. No asume fondos disponibles. Origen de `authorization_expires_at` **[PEND OQ-UC05-03]**.
9. **Resultado de la pasarela y asociación** [SPEC casos extremos]: la respuesta técnica y el webhook pasan por el mismo `RegisterChargeResultService`; el segundo resultado igual al ya registrado es idempotente y no altera nada **[CONV]**. El id del pago de Mercado Pago (`data.id`) se guarda en `charge_intent.external_reference` y con él se asocia el resultado a su intención; un resultado sin intención asociada se registra en `operational_failure` sin crear `ChargeRecord` **[CONV]**. Respuesta HTTP del webhook en ese caso **[PEND OQ-UC05-07]** (propuesta del README §4.4: `200 OK`).
10. **No definido por el SPEC 5** `[NEEDS CLARIFICATION]` (siguen abiertas): OQ-UC05-01, OQ-UC05-02, OQ-UC05-03, OQ-UC05-07, OQ-UC05-08 y OQ-UC05-09.

## Contratos de API

UC05 **no expone endpoints REST propios** (README §2: "UC05 no tiene endpoint público"). Interfaces:

| Interfaz | Dirección | Contrato | Resumen |
|---|---|---|---|
| `ProcessChargeUseCase` (puerto) | UC07 → UC05 | — | Ver «Puertos expuestos» |
| Cola interna del comando | Outbox → worker | — | Nombres de exchange/cola **[PEND OQ-UC05-08]** |
| `POST /v1/payments` | Worker → Mercado Pago | `pasarela-comando-cobro.md` | `transaction_amount` único; `token`; `X-Idempotency-Key` por intento |
| `POST /api/v1/webhook/gateway` | Mercado Pago → sistema | `pasarela-webhook-resultados.md` | Verifica `x-signature`; acusa recibo; consulta activa `GET /v1/payments/{data.id}` |

**Webhook (parte de UC05, `type=payment`)** — códigos del catálogo README §3.4/§4.4:

| HTTP | `code` | Cuándo |
|---|---|---|
| 200 | — | Notificación aceptada, duplicada o `data.id` desconocido (propuesta §4.4) |
| 400 | `VALIDATION_ERROR` | Payload mal formado o sin `action`, `data.id`, `type` |
| 401 | `UNAUTHENTICATED` | `x-signature` ausente o inválida |
| 500 | `INTERNAL_ERROR` | BD no disponible al acusar recibo: la pasarela reintentará |

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | transiciones de estado, `BigDecimal`, inmutabilidad del registro | `ChargeIntentTest`, `ChargeRecordTest` |
| Unitario (`application`) | RF-002, RF-009, RF-013, RF-004A, resultados por estado | `ProcessChargeServiceTest`, `RegisterChargeResultServiceTest` |
| Integración (Testcontainers PostgreSQL) | migración, FK, `UNIQUE(idempotency_key)`, varias intenciones por reserva, trigger anti-mutación | `ChargePersistenceAdapterTest` |
| Integración (Testcontainers RabbitMQ + WireMock) | outbox → worker → pasarela, mismo `X-Idempotency-Key` en reintentos, timeouts | `ChargeCommandListenerTest`, `MercadoPagoChargeAdapterTest` |
| Web (MockMvc) | firma, `400`, `401`, `200` duplicado, `500` | `GatewayWebhookControllerTest` |
| Arquitectura | reglas §3.4 | `ArchitectureTest` (UC11·T003, ampliado en T030) |

**Pruebas de aceptación (CE-001…CE-007)**:

- **`ce001_montos_autorizados_y_confirmados_sin_discrepancias`** (T026): intención aprobada/capturada conserva montos iguales al valor calculado.
- **`ce002_trazabilidad_solicitud_resultado_e_intencion_registro`** (T027): toda solicitud enviada queda registrada, todo resultado actualiza la intención y `ChargeRecord.charge_intent_id` apunta a su intención, con varias intenciones por reserva.
- **`ce003_fallas_de_comunicacion_sin_estado_indefinido`** (T028): timeout/inalcanzable al enviar y al recibir terminan en un estado explícito.
- **`ce004_rechazados_o_fallidos_no_son_cobros_exitosos`** (T029).
- **`ce005_sin_datos_sensibles_persistidos_ni_en_logs`** (T016).
- **`ce006_metadatos_enmascarados_conservados`** (T025).
- **`ce007_autorizacion_expirada_registrada_sin_asumir_fondos`** (T022).

## Discrepancias y puntos abiertos

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC05-01** | `operational_failure`, `outbox_message`, `FailureRecorderPort`, `OutboxPort` y las dependencias AMQP, Resilience4j, WireMock y Awaitility debían quedar definidos en las tareas compartidas de UC11 | Reparto de piezas compartidas vs plan UC11 | UC05 los referencia por nombre: `UC11·T001` (dependencias), `UC11·T002` (tablas), `UC11·T007` (puertos) y `UC11·T046` (adaptadores y relay). **Cierre:** el plan UC11 ya los incluye | **Cerrada** |
| **D-UC05-02** | `charge_intent` (general-plan §4) no tiene columna para el **detalle** del estado externo (SPEC RF-006 y UC06 RF-003 lo exigen) ni para el **monto solicitado** (CE-001 compara contra el valor calculado, que UC03 puede invalidar) | general-plan §4 vs SPEC 5/6 | Se agregan `status_detail` y `requested_amount` a `charge_intent` en la migración V3x (T002). **Cierre:** `charge_intent` es una tabla propia del bloque B | **Cerrada** |
| **D-UC05-03** | general-plan §3.5 lista 3 puertos de entrada para UC05; el diseño necesita 2 más: `SendChargeToGatewayUseCase` (el *worker* es un adaptador y solo puede invocar `port.in`, §3.4) y `ReconcileChargeIntentsUseCase` (job de conciliación) | general-plan §3.5 vs §3.4 | Se agregan ambos como [CONV]. **Cierre:** son puertos propios de UC05 | **Cerrada** |
| **D-UC05-04** | El contrato de cobro usa `X-Idempotency-Key` de ejemplo `cobro-<reservation_id>`, igual para todos los intentos de una reserva; contradice RF-004A / D-28 (nuevo intento = clave nueva) | `pasarela-comando-cobro.md` §2 vs SPEC RF-004A | Rige el SPEC: clave por intento (derivada de `reservationId` + `operationKey`). T031 corrige el ejemplo del contrato. **Cierre:** el contrato de cobro es de UC05 | **Cerrada** |
| **D-UC05-05** | Webhook: la regla 2 dice responder `200` "inmediatamente sin esperar el procesamiento", la regla 7 dice responder `500` si el procesamiento interno falla | `pasarela-webhook-resultados.md` §3.2 vs §3.7 | Se acusa recibo (`200`) **después** de persistir de forma durable la notificación para su procesamiento (outbox); si eso falla, `500`. El procesamiento del paso 2 es asíncrono | **Abierta**: el webhook lo comparten UC05, UC09 y UC10 (bloque C) |
| **D-UC05-06** | RF-006 dice crear el registro "si la operación es exitosa"; la entidad dice "capturada o cobrada exitosamente"; UC06 aclara que `APROBADO` no implica captura | SPEC 5 RF-006 vs Entidades Clave vs UC06 | Se crea `ChargeRecord` solo en `CAPTURADO` (general-plan §3.5: "solo con cobro/captura confirmados") | Decidida; ver OQ-UC05-02 |
| **D-UC05-07** | RF-009/RF-013 piden "responder con un error controlado", pero UC05 no tiene canal de respuesta (lo invoca UC07, unidireccional) | SPEC 5 vs UC07 RF-009/RF-010 | `ProcessChargeUseCase` lanza excepción de dominio; UC07 la registra en `operational_failure` | Resuelta en diseño |
| **D-UC05-08** | El contrato dice que el resultado "definitivo" llega por webhook, pero la respuesta técnica puede ser `approved` | `pasarela-comando-cobro.md` §4 vs SPEC RF-005 | La respuesta técnica y el webhook pasan por el mismo `RegisterChargeResultService`; el segundo resultado igual es idempotente. **Cierre:** es el diseño del servicio de UC05 | **Cerrada** |

## Preguntas abiertas (OQ-UC05-xx)

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC05-01** | El contrato exige `payer.email`, `payment_method_id`, `installments` y `description`, pero ni el SPEC 5 ni el mensaje de UC07 (`payment_token_ref`, `payment_method_type`, `payment_metadata`) los proveen. ¿De dónde salen? | T012 | Ninguna: no se inventa. Mientras no se confirme, `MercadoPagoPaymentRequest` queda con esos campos sin origen | **Abierta** |
| **OQ-UC05-02** | ¿Se usa autorización + captura o cobro directo? El contrato no envía indicador de captura; el SPEC dice "según la capacidad configurada". Si se autoriza, ¿quién solicita la captura y cuándo se crea `ChargeRecord`? (UC10 solicita "captura y/o liquidación") | T018, T012 | Propiedad de configuración `seashare.gateway.charge-mode` **[CONV]**; `ChargeRecord` solo en `CAPTURADO` | **Abierta** (afecta a bloque C) |
| **OQ-UC05-03** | ¿De dónde sale `authorization_expires_at`? El contrato de cobro no devuelve vigencia | T022 | Sin fuente: el job queda condicionado | **Abierta** |
| **OQ-UC05-04** | Derivación y formato de la clave idempotente por intento y su relación con la identidad de operación de UC07 (RF-009A) | T006, T008 | Clave determinística a partir de `reservationId` + `operationKey`; `operationKey` = `reservationId` + `statusChangedAt` (UC07) | **Cerrada** → **Adoptada.** |
| **OQ-UC05-05** | ¿Cómo se asocia un recurso de Mercado Pago (`data.id`) a una intención? El contrato envía `external_reference` = id de la reserva, igual para todas las intenciones; si la respuesta técnica se pierde, la intención no conoce el id de MP | T019, T021 | Buscar por id externo guardado en `charge_intent.external_reference`; si no existe, `operational_failure` (sin adivinar) | **Cerrada** → **Adoptada.** |
| **OQ-UC05-06** | Estado de la intención ante `400` por token inválido: el SPEC dice que no se asume rechazo financiero y no define estado | T014 | `RECHAZADO` con `status_detail` técnico (no crea registro) | **Cerrada** → **Adoptada.** |
| **OQ-UC05-07** | El endpoint webhook y `GatewayReconciliationJob` los comparten UC05, UC09 y UC10 (bloques B y C). No hay dueño asignado. Respuesta ante `data.id` desconocido (README §4.4 `[PEND]`) | T019, T023 | UC05 define el controlador/verificador y el mapeo `type=payment`; UC09 y UC10 agregan su `type`. Respuesta `200` + `operational_failure` | **Abierta**: acordar con C |
| **OQ-UC05-08** | Nombres de exchange, cola y DLQ internos del comando (equivale a OQ-09 del plan general) | T012, T014 | Sin nombre fijado | **Abierta** |
| **OQ-UC05-09** | Dueño de los objetos de valor compartidos (`Money`, `ReservationId`) y escala/redondeo de salida hacia la pasarela (el contrato envía `990000.00`) | T003, T012 | UC05 define solo `IdempotencyKey` y `PaymentMethodReference`; el resto se referencia por nombre | **Abierta** |

**`[NEEDS CLARIFICATION]` consolidado (abiertas):** OQ-UC05-01, OQ-UC05-02, OQ-UC05-03, OQ-UC05-07, OQ-UC05-08 y OQ-UC05-09. Discrepancia abierta: D-UC05-05.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). Las fases 1 y 2 son **Compartido** y remiten a tareas de UC11; no se duplican. Las tareas locales empiezan en la Fase 3.

### Phase 1: Setup — **Compartido**

- [ ] `UC11·T001`–`UC11·T003`: starters (incluye AMQP, Resilience4j, WireMock y Awaitility), migración V1 y `ArchitectureTest`.

### Phase 2: Foundational — **Compartido**

- [ ] `UC11·T004`–`UC11·T009`: excepciones base, `ProblemDetailsConfig`, seguridad.
- [ ] `UC11·T046`–`UC11·T047`: `operational_failure`, `outbox_message`, `FailureRecorderPort`, `OutboxPort`, adaptadores y `OutboxRelayJob`.

### Phase 3: US1 — Enviar la solicitud de cobro (HU1; RF-001…RF-004B, RF-009…RF-011, RF-013, RF-015; CE-002, CE-003, CE-005)

- [ ] **T001** · Propiedades de configuración de la pasarela en `application.properties` (`seashare.gateway.*`: URL base, *access token*, secreto del webhook, timeout de 10 s y `charge-mode` de OQ-UC05-02), sin valores sensibles en el repositorio. · M: `none` · P: `pending`
- [ ] **T002** · Migración `V3x__create_charge_intent_and_charge_record.sql`: `charge_intent` (columnas de general-plan §4 + `status_detail`, `requested_amount`; `UNIQUE(idempotency_key)`; **sin** `UNIQUE(reservation_id)`; índice `(reservation_id, created_at DESC)`), `charge_record` (`charge_intent_id NOT NULL` con FK; índices `(owner_id, created_at DESC)`, `(boat_id)`, `(reservation_id)`), función y trigger que rechazan `UPDATE`/`DELETE` en `charge_record` y retiro de privilegios (D-13). **Aviso al canal**: la función de inmutabilidad la reutilizan las tablas de UC09 y del bloque C. · M: `none` · P: `pending`
- [ ] **T003** · Dominio: `ChargeIntentStatus`, `ChargeIntent` (transiciones válidas), `ChargeRecord` (inmutable, sin *setters*), `PaymentMethodReference`, `IdempotencyKey` (`BigDecimal`, RNF-002). · M: `none` · P: `pending`
- [ ] **T004** · Excepciones `ReservationValueNotCalculatedException`, `InvalidPaymentTokenException` y pruebas unitarias de dominio (transiciones, inmutabilidad, `BigDecimal`). · M: `none` · P: `pending`
- [ ] **T005** · Puertos de salida: `ChargeIntentRepository` (guardar, por id, por clave idempotente, por id externo, `findLatestByReservationId` para UC06, `findApprovedByReservationId` para UC09/UC10, intenciones vencidas, intenciones sin resultado), `ChargeRecordRepository`, `ChargeGatewayPort` (`send`, `fetchPayment`). · M: `none` · P: `pending`
- [ ] **T006** · Puertos de entrada y `ProcessChargeCommand`; **publicar en el canal la firma de `ProcessChargeUseCase`**. · M: `none` · P: `pending`
- [ ] **T007** · Pruebas de `ProcessChargeService`: valor total registrado → intención + outbox; sin valor total → `ReservationValueNotCalculatedException` sin tocar la pasarela (RF-009); token ausente → `InvalidPaymentTokenException` (RF-013); misma clave → no duplica (RF-004A); un solo monto (RF-015). · M: `none` · P: `pending`
- [ ] **T008** · `ProcessChargeService` (transacción única: intención + outbox; clave idempotente derivada de `reservationId` + `operationKey`). Depende de `UC11·T007` (`OutboxPort`). · M: `none` · P: `pending`
- [ ] **T009** · Persistencia: entidades JPA, repositorios, mapper MapStruct y adaptadores (las entidades no salen de `adapter/out/persistence`). · M: `none` · P: `pending`
- [ ] **T010** · Integración (Testcontainers): FK, `UNIQUE(idempotency_key)`, varias intenciones por reserva, trigger anti-mutación en `charge_record`. · M: `none` · P: `pending`
- [ ] **T011** · Prueba de publicación en *outbox* en la misma transacción que la intención (si falla una, falla la otra). · M: `none` · P: `pending`
- [ ] **T012** · ACL de la pasarela: `ChargeGatewayRequest/Result`, `MercadoPagoPaymentRequest/Response`, `MercadoPagoChargeAdapter.send` con `X-Idempotency-Key` por intento, sin PAN/CVV/vencimiento. Condicionada a **OQ-UC05-01** (`payer.email`, `payment_method_id`…) y **OQ-UC05-08**. · M: `none` · P: `pending`
- [ ] **T013** · Pruebas WireMock del envío: cuerpo según contrato, un solo `transaction_amount`, cabecera idempotente, `BigDecimal` sin pérdida (RNF-001, RNF-002, RF-011, RF-015). · M: `none` · P: `pending`
- [ ] **T014** · *Worker*: `ChargeCommandListener` + `SendChargeToGatewayService` con timeout 10 s y *circuit breaker*; guarda el id de pago de MP en `external_reference`; timeout/`502`/`503` → `FALLA_COMUNICACION` y reintento con la **misma** clave; `400` de token → sin reintento y la intención queda `RECHAZADO` con `status_detail` técnico. · M: `none` · P: `pending`
- [ ] **T015** · Pruebas del *worker*: reintentos con misma clave, `FALLA_COMUNICACION` no es rechazo, `400` no se reintenta y deja `RECHAZADO` técnico (RF-013, RNF-003). · M: `none` · P: `pending`
- [ ] **T016** · **`ce005_sin_datos_sensibles_persistidos_ni_en_logs`** (CE-005): el esquema no tiene columnas de PAN/CVV/vencimiento; el token no aparece en logs ni en `outbox_message`; solo tokens y metadatos no sensibles. · M: `none` · P: `pending`

### Phase 4: US2 — Registrar el resultado del cobro (HU2; RF-005…RF-008, RF-012, RF-014; RNF-003, RNF-005; CE-001, CE-003, CE-004, CE-006, CE-007)

- [ ] **T017** · Pruebas de `RegisterChargeResultService`: aprobado/capturado, en proceso, rechazado, cancelado, expirado; repetido no altera el registro; resultado sin intención → `operational_failure` y sin `ChargeRecord`. · M: `none` · P: `pending`
- [ ] **T018** · `RegisterChargeResultService`: actualiza siempre la intención; crea `ChargeRecord` solo en `CAPTURADO`, en la misma transacción, con `charge_intent_id`, propietario, embarcación y desglose (D-UC05-06). Respuesta técnica y webhook pasan por aquí (D-UC05-08). · M: `none` · P: `pending`
- [ ] **T019** · Webhook: `GatewayWebhookController` + `MercadoPagoSignatureVerifier` (HMAC-SHA256 del contrato), validación de payload, acuse tras persistencia durable (D-UC05-05) y pruebas MockMvc (401, 400, 200 duplicado, 500). Compartido, ver OQ-UC05-07. · M: `none` · P: `pending`
- [ ] **T020** · Procesamiento asíncrono de la notificación: consume el mensaje, localiza la intención por `external_reference`, consulta `fetchPayment` y llama a `RegisterChargeResultUseCase`; sin intención asociada → `operational_failure`. · M: `none` · P: `pending`
- [ ] **T021** · `MercadoPagoChargeAdapter.fetchPayment` y pruebas WireMock (`approved`, `rejected`, `expired`, `in_process`). · M: `none` · P: `pending`
- [ ] **T022** · `ExpireStaleAuthorizationsService` + `AuthorizationExpiryJob` (ShedLock opcional) y **`ce007_autorizacion_expirada_registrada_sin_asumir_fondos`** (RF-014, RNF-005, CE-007). Condicionada a **OQ-UC05-03**. · M: `none` · P: `pending`
- [ ] **T023** · `ReconcileChargeIntentsService` + registro en `GatewayReconciliationJob` (compartido): reintenta/consulta intenciones sin resultado definitivo **con la misma clave** (RNF-003). · M: `none` · P: `pending`
- [ ] **T024** · Pruebas de conciliación: intención en `FALLA_COMUNICACION` o `EN_PROCESO` termina en estado explícito. · M: `none` · P: `pending`
- [ ] **T025** · **`ce006_metadatos_enmascarados_conservados`** (CE-006, RF-012): tipo y últimos 4 se conservan en intención y registro sin exponer credenciales. · M: `none` · P: `pending`
- [ ] **T026** · **`ce001_montos_autorizados_y_confirmados_sin_discrepancias`** (CE-001). · M: `none` · P: `pending`
- [ ] **T027** · **`ce002_trazabilidad_solicitud_resultado_e_intencion_registro`** (CE-002). · M: `none` · P: `pending`
- [ ] **T028** · **`ce003_fallas_de_comunicacion_sin_estado_indefinido`** (CE-003). · M: `none` · P: `pending`
- [ ] **T029** · **`ce004_rechazados_o_fallidos_no_son_cobros_exitosos`** (CE-004, RF-008). · M: `none` · P: `pending`

### Phase 5: Polish & Cross-Cutting Concerns

- [ ] **T030** · ArchUnit: `domain` sin Spring/JPA/Jackson; `application` sin JPA/MapStruct; webhook/listener/jobs sin repositorios; `ChargeRecord` sin *setters*; ningún otro UC calcula tarifas aquí. · M: `none` · P: `pending`
- [ ] **T031** · Alinear documentos: ejemplo de `X-Idempotency-Key` en `pasarela-comando-cobro.md` (clave por intento) y columnas `status_detail`/`requested_amount` en general-plan §4. · M: `none` · P: `pending`
- [ ] **T032** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending`
- [ ] **T033** · `./mvnw clean verify` y ejecución de CE-001…CE-007. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
UC11·T001–T009 + UC11·T046 (compartido) ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010 ─> T011
                                                                                                    └──────────────> T012 ─> T013 ─> T014 ─> T015 ─> T016
T017 (pruebas) ─> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025 ─> T026…T029 ─> T030 ─> T031 ─> T032 ─> T033
```

- **Bloquea a otros planes**: T005 (métodos de `ChargeIntentRepository`) bloquea UC06 y UC09; T006 (firma de `ProcessChargeUseCase`) bloquea UC07; T002 (migración V3x y función de inmutabilidad) debe ir antes de UC09 y del bloque C (FK de `refund_intent` y `settlement_intent` a `charge_intent`).
- **Riesgos de secuencia**: T012 depende de OQ-UC05-01; T022 de OQ-UC05-03.

## Notes

- El SPEC 5 no define metas de rendimiento ni los datos del pagador/medio de pago que exige el contrato (OQ-UC05-01).
- UC05 no valida el TTL ni decide cuándo reintentar un cobro por negocio: lo decide Reservas mediante nuevas notificaciones (UC07).
- Etiquetas: `[SPEC]`, `[CONV]` (general-plan o inferido), `[PEND]`/`[NEEDS CLARIFICATION]` (sin definir).
- Los valores numéricos de los ejemplos son ilustrativos.

