# Implementation Plan: UC06 - Solicitar Confirmación de Pago

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

El Sistema de Reservas y Operaciones consulta el estado vigente del cobro de una reserva para decidir si la avanza a `RESERVADO` o revierte el bloqueo temporal. UC06 es una **consulta de solo lectura** sobre la `ChargeIntent` creada por UC05: devuelve el estado (`EN_PROCESO`, `APROBADO`, `RECHAZADO`, `CANCELADO`, `EXPIRADO` o `DESCONOCIDO`), el detalle y los montos y la referencia externa cuando existan. **No contacta a la pasarela** [SPEC HU1, RF-001…RF-006, CE-002].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. Adaptador de entrada REST (`GET /api/v1/reservations/{reservation_id}/payment-confirmation`) que invoca únicamente `application.port.in`; el servicio lee la intención por el puerto de salida `ChargeIntentRepository` (dueño: UC05) y traduce los estados internos al vocabulario del SPEC con el mapeo de general-plan §10. Errores en *Problem Details* (RFC 9457). El diagrama de casos de uso asocia "Solicitar confirmación de pago" únicamente con el *Sistema de Reservas y Operaciones* (`MODULO 2`), sin `<<include>>`/`<<extend>>`.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud por `reservation_id` | `PaymentConfirmationController`, `PaymentConfirmationQuery` | T003, T006 | T009 |
| **RF-002** consultar la `IntenciónDeCobro` | `GetPaymentConfirmationService` + `ChargeIntentRepository.findLatestByReservationId` (UC05) | T005 | T004, T010 |
| **RF-003** devolver estado vigente y detalle | `PaymentConfirmationStatus` (mapeo §10), `PaymentConfirmationResult` | T001, T005 | T002, T009 |
| **RF-004** montos y referencias si existen | `PaymentConfirmationResult`, `PaymentConfirmationResponse` (nulos permitidos) | T003, T006 | T009 |
| **RF-005** error controlado sin intención | `ChargeIntentNotFoundException` → `404 CHARGE_INTENT_NOT_FOUND` | T005, T007 | T004, T009, T013 |
| **RF-006** no contactar a la pasarela | `GetPaymentConfirmationService` sin dependencia de `ChargeGatewayPort` | T005, T014 | T012, T014 |
| **RNF-001** DTOs con Reservas | `PaymentConfirmationQuery/Result` (aplicación), `PaymentConfirmationResponse` (HTTP) | T003, T006 | T009 |
| **RNF-002** `BigDecimal` | Montos del resultado; JSON como *string* decimal (README §3.1) | T003, T006 | T009 |
| **RNF-003** manejo de errores robusto | `ProblemDetailsConfig` (mapeos de UC06), validación de UUID | T007 | T009, T013 |
| **CE-001** estado coincide con la intención | Mapeo + lectura de la intención | T001, T005 | **T011** |
| **CE-002** 0 llamadas a la pasarela | Sin dependencia hacia la pasarela + ArchUnit | T005, T014 | **T012** |
| **CE-003** reservas sin intención → error controlado | `ChargeIntentNotFoundException` | T005, T007 | **T013** |
| **HU1** consultar el resultado de un cobro (P1) | Fase 3 | T001–T013 | T009 (escenarios 1, 2 y 3) |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.1. Para UC06: Spring Web MVC, Validation, Security, MapStruct (solo si se mapea con MapStruct), ArchUnit. **Hoy no están en el `pom.xml`** (se agregan en `UC11·T001`).
**Storage**: PostgreSQL 16+; UC06 **solo lee** `charge_intent` (tabla de UC05) [general-plan §3.5]; no tiene migraciones propias
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (PostgreSQL), WireMock (solo para demostrar CE-002), ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: No definidos por el SPEC 6 `[NEEDS CLARIFICATION: se buscó en spec.md 006, general-plan.md y contracts/rest/UC06-confirmacion-pago.md; ninguno fija metas de rendimiento]`
**Constraints**: solo lectura; sin llamadas a la pasarela [SPEC RF-006]; `BigDecimal` en los montos [SPEC RNF-002]; invocable únicamente por el Sistema de Reservas y Operaciones [SPEC RF-001; contrato]
**Scale/Scope**: 1 endpoint REST; 0 tablas propias; 6 RF + 3 RNF + 3 CE + 1 HU

**Estado actual del repositorio**: existen `SeashareM3Application`, `application.properties`, `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. No existen `domain`/`application`/`infrastructure`, migraciones ni reglas ArchUnit.

## Project Structure

### Documentation (this feature)

```text
docs/features/006-solicitar-confirmacion-de-pago/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                # §3.3 Problem Details, §3.4 catálogo, §4 tabla de decisión de errores
└── rest/
    └── UC06-confirmacion-pago.md            # GET /api/v1/reservations/{reservation_id}/payment-confirmation
```

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── valueobject/
│   │   └── PaymentConfirmationStatus.java       # T001  mapeo desde ChargeIntentStatus [general-plan §10]
│   └── exception/
│       └── ChargeIntentNotFoundException.java   # T001  [SPEC RF-005; nombre [CONV]]
├── application/
│   ├── port/in/
│   │   └── GetPaymentConfirmationUseCase.java   # T003  [general-plan §3.5]
│   ├── service/
│   │   └── GetPaymentConfirmationService.java   # T005
│   └── dto/
│       ├── PaymentConfirmationQuery.java        # T003  = SolicitudConfirmacionPago [SPEC]
│       └── PaymentConfirmationResult.java       # T003  = ConfirmacionPagoResultado [SPEC]
└── infrastructure/
    └── adapter/in/web/
        ├── PaymentConfirmationController.java   # T006
        └── dto/PaymentConfirmationResponse.java # T006  claves del contrato [CONV]

src/test/java/com/seashare/seasharem3/
├── domain/valueobject/PaymentConfirmationStatusTest.java               # T002
├── application/service/GetPaymentConfirmationServiceTest.java          # T004
├── contract/UC06PaymentConfirmationContractTest.java                   # T009, T013
├── infrastructure/adapter/in/web/PaymentConfirmationSecurityTest.java  # T008
├── infrastructure/adapter/out/persistence/PaymentConfirmationLatestIntentIT.java  # T010
└── acceptance/Uc06AcceptanceTest.java                                  # T011, T012, T013
```

El puerto de salida `ChargeIntentRepository` **no** se crea aquí: es del bloque B (UC05, `UC05·T005`) y UC06 lo referencia por nombre. UC06 solo requiere el método `findLatestByReservationId` (ver D-UC06-02). No hay «Puertos expuestos»: ningún otro bloque llama a `GetPaymentConfirmationUseCase`.

**Structure Decision**: servicio único hexagonal; `application` solo depende de `domain`; el controller invoca únicamente `application.port.in` y nunca un repositorio; ningún componente de UC06 depende de `ChargeGatewayPort` [general-plan §3.4; SPEC RF-006].

## Reglas de negocio

Todas provienen del SPEC 6 y del contrato `UC06-confirmacion-pago.md`, salvo indicación.

1. **Quién puede llamarlo** [SPEC RF-001; contrato]: solo el Sistema de Reservas y Operaciones. Mecanismo de autenticación `[PEND OQ-01 del plan general; ver OQ-UC06-01]`.
2. **Búsqueda** [SPEC RF-002]: se consulta la `ChargeIntent` de la reserva. Una reserva puede tener varias (UC05 RF-004A); se devuelve **la más reciente por `created_at`** **[CONV]** (el SPEC 6 habla de "la" intención, en singular).
3. **Estados** [SPEC RF-003; general-plan §10]:

| Estado interno (`ChargeIntent`) | `status` devuelto |
|---|---|
| `PENDIENTE_ENVIO`, `EN_PROCESO` | `EN_PROCESO` |
| `AUTORIZADO`, `CAPTURADO` | `APROBADO` |
| `RECHAZADO` | `RECHAZADO` |
| `CANCELADO` | `CANCELADO` |
| `EXPIRADO` | `EXPIRADO` |
| `FALLA_COMUNICACION` | `DESCONOCIDO` (**OQ-12 del plan general**) |

4. **Fidelidad** [SPEC RF-003, caso extremo "en proceso"]: se devuelve el estado registrado sin anticipar un resultado. Un cobro `RECHAZADO`, `CANCELADO` o `EXPIRADO` jamás se reporta como aprobado. `APROBADO` **no implica** que la captura o la liquidación ya se ejecutaron [SPEC escenario 1].
5. **Montos y referencia** [SPEC RF-004]: `authorized_amount`, `captured_amount`, `released_amount`, `charged_amount`, `external_reference` y `detail` salen de la intención; los no disponibles van como `null`. Detalle: la columna `status_detail` la agrega UC05 (D-UC05-02). Escala de los montos en JSON `[PEND OQ-UC06-02]`.
6. **Solo lectura** [SPEC RF-002, caso extremo "solicitud repetida"]: cada consulta devuelve el estado vigente, sin crear ni modificar registros.
7. **Sin intención** [SPEC RF-005, RNF-003]: `404 CHARGE_INTENT_NOT_FOUND`, `retryable: true` (UC05 la crea de forma asíncrona tras `PENDIENTE`) [contrato; README E5].
8. **Orden de validación** [README §4.3]: E1 (`401`) → E2 (`403`) → E3 → E4 (`400`, `reservation_id` con formato inválido) → E5 (`404`). No se consulta la base antes de validar el formato.
9. **No definido por el SPEC 6** `[NEEDS CLARIFICATION]` (siguen abiertas): OQ-UC06-01 y OQ-UC06-02.

## Contratos de API

Fuente: `contracts/rest/UC06-confirmacion-pago.md` [general-plan §6]. El SPEC no fija la ruta; rige el contrato.

### `GET /api/v1/reservations/{reservation_id}/payment-confirmation`

- **200 OK** — campos: `reservation_id`, `status`, `detail`, `authorized_amount`, `captured_amount`, `released_amount`, `charged_amount`, `external_reference` (los montos como *string* decimal o `null`):

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "status": "APROBADO",
  "detail": null,
  "authorized_amount": "990000.00",
  "captured_amount": null,
  "released_amount": null,
  "charged_amount": null,
  "external_reference": "pg-8841"
}
```

| HTTP | `code` | Cuándo | `retryable` | En catálogo §3.4 |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reservation_id` no es un UUID | No | sí |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | sí |
| 403 | `FORBIDDEN` | El llamador no es el Sistema de Reservas y Operaciones | No | sí |
| 404 | `CHARGE_INTENT_NOT_FOUND` | No existe `ChargeIntent` para la reserva | Sí | sí |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | sí |

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | mapeo de los 8 estados internos a los 6 devueltos | `PaymentConfirmationStatusTest` |
| Unitario (`application`) | intención presente/ausente, nulos, la más reciente | `GetPaymentConfirmationServiceTest` (Mockito sobre `ChargeIntentRepository`) |
| Contrato (MockMvc) | cuerpos y códigos del contrato; los tres escenarios de aceptación | `UC06PaymentConfirmationContractTest` |
| Seguridad | `401`/`403` | `PaymentConfirmationSecurityTest` |
| Integración (Testcontainers) | varias intenciones por reserva → la más reciente | `PaymentConfirmationLatestIntentIT` |
| Arquitectura | sin dependencia hacia la pasarela | `ArchitectureTest` (UC11·T003, ampliado en T014) |

**Pruebas de aceptación (CE-001…CE-003)**:

- **`ce001_estado_devuelto_coincide_con_la_intencion`** (T011): para cada estado interno, la respuesta coincide con la intención de UC05.
- **`ce002_no_se_realizan_solicitudes_a_la_pasarela`** (T012): con WireMock de la pasarela, 0 solicitudes tras cualquier consulta; ArchUnit confirma la ausencia de dependencia.
- **`ce003_reserva_sin_intencion_responde_error_controlado`** (T013): `404 CHARGE_INTENT_NOT_FOUND`, `retryable: true`, sin respuestas ambiguas.

## Discrepancias y puntos abiertos

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC06-01** | El SPEC 6 consulta "la `IntenciónDeCobro`" (singular); el SPEC 5 RF-004A permite varias por reserva | SPEC 6 RF-002 vs SPEC 5 RF-004A | Se devuelve la más reciente por `created_at` (índice `(reservation_id, created_at DESC)`, general-plan §4). **Cierre:** UC05 y UC06 son del mismo bloque | **Cerrada** |
| **D-UC06-02** | `findLatestByReservationId` y `status_detail` no existen todavía: dependen de `UC05·T005` y `UC05·T002` | UC06 vs plan UC05 | Se pidieron en el plan UC05 (mismo bloque); UC06 queda bloqueada hasta que existan | Resuelta en diseño |
| **D-UC06-03** | Los estados `EN_PROCESO` y `DESCONOCIDO` del SPEC 6 no equivalen uno a uno a los 8 estados internos de general-plan §10 | SPEC 6 RF-003 vs general-plan §10 | Mapeo de la tabla de §Reglas, punto 3; sin estado nuevo | Resuelta; `FALLA_COMUNICACION`→`DESCONOCIDO` pendiente de OQ-12 |

## Preguntas abiertas (OQ-UC06-xx)

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC06-01** | ¿Cómo se identifica y autoriza al "Sistema de Reservas y Operaciones" (credencial de servicio)? Equivale a OQ-01 del plan general; el SPEC fija quién, no cómo | T008 | OAuth2 Resource Server (JWT) con credencial de servicio de Reservas (general-plan §7.1) **[CONV]** | **Abierta** |
| **OQ-UC06-02** | Escala y modo de redondeo de los montos en el JSON (los ejemplos muestran 2 decimales; internamente son 4). Afecta a todos los bloques | T006 | Sin regla fijada; se aplica la que se acuerde para todos los contratos | **Abierta** |
| **OQ-UC06-03** | Si hay varias intenciones, ¿se devuelve siempre la más reciente? | T005, T010 | La más reciente por `created_at` | **Cerrada** → **Adoptada.** |

**`[NEEDS CLARIFICATION]` consolidado (abiertas):** OQ-UC06-01 y OQ-UC06-02.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). Las fases 1 y 2 son **Compartido** y remiten a tareas de UC11; las tareas locales empiezan en la Fase 3.

### Phase 1: Setup — **Compartido**

- [ ] `UC11·T001`–`UC11·T003`: starters, migración V1 y `ArchitectureTest`.

### Phase 2: Foundational — **Compartido**

- [ ] `UC11·T004`–`UC11·T009`: excepciones base, `ProblemDetailsConfig`, `SecurityConfig`.

### Phase 3: US1 — Consultar el resultado de un cobro (HU1; RF-001…RF-006; RNF-001…RNF-003; CE-001…CE-003)

- [ ] **T001** · Dominio: `PaymentConfirmationStatus` con el mapeo de §10 y `ChargeIntentNotFoundException`. Depende de `UC05·T003` (`ChargeIntentStatus`). · M: `none` · P: `pending`
- [ ] **T002** · Pruebas del mapeo: los 8 estados internos → los 6 estados devueltos (incluye `FALLA_COMUNICACION`→`DESCONOCIDO`). · M: `none` · P: `pending`
- [ ] **T003** · `GetPaymentConfirmationUseCase`, `PaymentConfirmationQuery`, `PaymentConfirmationResult` (montos `BigDecimal` anulables). · M: `none` · P: `pending`
- [ ] **T004** · Pruebas de `GetPaymentConfirmationService` (Mockito): intención aprobada (escenario 1), rechazada (2), en proceso (3), sin intención (RF-005), montos nulos, la más reciente. · M: `none` · P: `pending`
- [ ] **T005** · `GetPaymentConfirmationService`: lee por `ChargeIntentRepository.findLatestByReservationId`, traduce el estado, lanza `ChargeIntentNotFoundException`. Sin dependencia de la pasarela. Depende de `UC05·T005`. · M: `none` · P: `pending`
- [ ] **T006** · `PaymentConfirmationController` (`GET`) y `PaymentConfirmationResponse` con las claves del contrato (montos como *string* decimal). Escala condicionada a **OQ-UC06-02**. · M: `none` · P: `pending`
- [ ] **T007** · Extender `ProblemDetailsConfig` (`UC11·T008`): `CHARGE_INTENT_NOT_FOUND` 404 `retryable: true`, y `VALIDATION_ERROR` 400 por `reservation_id` no UUID. · M: `none` · P: `pending`
- [ ] **T008** · Regla de seguridad del path: solo la credencial del Sistema de Reservas; `401`/`403`. Condicionada a **OQ-UC06-01**; prueba `PaymentConfirmationSecurityTest`. · M: `none` · P: `pending`
- [ ] **T009** · Pruebas de contrato MockMvc con los ejemplos del contrato: `200` por cada estado, `400`, `404`, y que repetir la consulta no modifica datos. · M: `none` · P: `pending`
- [ ] **T010** · Integración (Testcontainers): varias `ChargeIntent` de una reserva → se devuelve la más reciente. Depende de `UC05·T010`. · M: `none` · P: `pending`
- [ ] **T011** · **`ce001_estado_devuelto_coincide_con_la_intencion`** (CE-001). · M: `none` · P: `pending`
- [ ] **T012** · **`ce002_no_se_realizan_solicitudes_a_la_pasarela`** (CE-002). · M: `none` · P: `pending`
- [ ] **T013** · **`ce003_reserva_sin_intencion_responde_error_controlado`** (CE-003). · M: `none` · P: `pending`

### Phase 4: Polish & Cross-Cutting Concerns

- [ ] **T014** · ArchUnit: `GetPaymentConfirmationService` y el controller no dependen de `ChargeGatewayPort` ni de repositorios; entidades JPA fuera de `adapter/out/persistence` (RF-006). · M: `none` · P: `pending`
- [ ] **T015** · Verificar el contrato `UC06-confirmacion-pago.md` frente a la implementación (rutas, códigos, `retryable`) y registrar cualquier diferencia como D-UC06-xx. · M: `none` · P: `pending`
- [ ] **T016** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]) y `./mvnw clean verify`. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
UC11·T001–T009 (compartido) ─> [UC05·T003, T005, T010] ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010 ─> T011 ─> T012 ─> T013 ─> T014 ─> T015 ─> T016
```

- **Depende de**: `UC05·T003` (estados), `UC05·T005` (`findLatestByReservationId`), `UC05·T002` (`status_detail`) y `UC05·T010` (adaptador de persistencia para la prueba de integración).
- **No bloquea** a otros planes: nadie llama a UC06 desde dentro del sistema.

## Notes

- El SPEC 6 no define metas de rendimiento (Technical Context).
- `DESCONOCIDO` para fallas de comunicación está sujeto a OQ-12 del plan general (no definida en el documento entregado: general-plan salta de §7 a §10).
- Etiquetas: `[SPEC]`, `[CONV]`, `[PEND]`/`[NEEDS CLARIFICATION]`.
- Los valores numéricos de los ejemplos son ilustrativos.
