# Implementation Plan: UC06 - Solicitar Confirmación de Pago

**Date**: 2026-10-09 (plan original)
**Updated**: 2026-10-29 (ver [Actualizaciones del plan](#actualizaciones-del-plan))
**Spec**: [spec.md](spec.md)

> **Actualización del plan — 2026-10-29.** Este documento parte del plan original del **2026-10-09**, conservado en el historial de Git (commit `978c7e5`). Las secciones modificadas durante la implementación de UC06 están marcadas con `[ACT-xx]` y se detallan en [Actualizaciones del plan](#actualizaciones-del-plan). Salvo las líneas de versión y la sección «Actualizaciones del plan», el contenido sin marcador pertenece al plan original.

## Summary

El Sistema de Reservas y Operaciones consulta el estado vigente del cobro de una reserva para decidir si la avanza a `RESERVED` o revierte el bloqueo temporal. UC06 es una **consulta de solo lectura** sobre la `ChargeIntent` creada por UC05: devuelve el estado (`IN_PROCESS`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED` o `UNKNOWN`), el detalle y los montos y la referencia externa cuando existan. **No contacta a la pasarela ni escribe montos** [SPEC HU1, RF-001…RF-006, CE-002].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. Adaptador de entrada REST (`GET /api/v1/reservations/{reservation_id}/payment-confirmation`) que invoca únicamente `application.port.in`; el servicio lee la intención por el puerto de salida `ChargeIntentRepository` (dueño: UC05) y traduce los estados internos al vocabulario del SPEC con el mapeo de general-plan §8. Errores en *Problem Details* (RFC 9457). UC06 es de solo lectura y no escribe `captured_amount`, `released_amount` ni estados terminales; esas escrituras pertenecen a los casos de uso que ejecutan el cobro, el reembolso y la liquidación (UC05, UC09 y UC10).

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud por `reservation_id` | `PaymentConfirmationController`, `PaymentConfirmationQuery` | T003, T006 | T009 |
| **RF-002** consultar la `IntenciónDeCobro` | `GetPaymentConfirmationService` + `ChargeIntentRepository.findLatestByReservationId` (UC05) | T005 | T004, T010 |
| **RF-003** devolver estado vigente y detalle | `PaymentConfirmationStatus` (mapeo §8), `PaymentConfirmationResult` | T001, T005 | T002, T009 |
| **RF-004** montos y referencias si existen | `PaymentConfirmationResult`, `PaymentConfirmationResponse` (nulos permitidos) | T003, T006 | T009 |
| **RF-005** error controlado sin intención | `ChargeIntentNotFoundException` → `404 CHARGE_INTENT_NOT_FOUND` | T005, T007 | T004, T009, T013 |
| **RF-006** no contactar a la pasarela | `GetPaymentConfirmationService` sin dependencia de `ChargeGatewayPort` | T005, T014 | T012, T014 |
| **RNF-001** DTOs con Reservas | `PaymentConfirmationQuery/Result` (aplicación), `PaymentConfirmationResponse` (HTTP) | T003, T006 | T009 |
| **RNF-002** `BigDecimal` | Montos del resultado; JSON como *string* decimal (README §3.1) | T003, T006 | T009 |
| **RNF-003** manejo de errores robusto | `GlobalExceptionHandler` + `ErrorCode` (mapeos compartidos), validación de UUID | T007 | T009, T013 **[ACT-07]** |
| **CE-001** estado coincide con la intención | Mapeo + lectura de la intención | T001, T005 | **T011** |
| **CE-002** 0 llamadas a la pasarela | Sin dependencia hacia la pasarela + ArchUnit | T005, T014 | **T012** |
| **CE-003** reservas sin intención → error controlado | `ChargeIntentNotFoundException` | T005, T007 | **T013** |
| **HU1** consultar el resultado de un cobro (P1) | Fase 3 | T001–T007, T009–T013 | T009 (escenarios 1, 2 y 3) |

## Actualizaciones del plan

Registro de los cambios introducidos el **2026-10-29** respecto al plan original del **2026-10-09**, cuya versión completa permanece en el historial de Git (commit `978c7e5`). No se presenta el contenido actualizado como si hubiera formado parte del plan original: cada sección afectada lleva su marcador `[ACT-xx]` y aquí se documenta el valor anterior y el vigente.

La versión original puede recuperarse con `git show 978c7e5:docs/features/006-solicitar-confirmacion-de-pago/plan.md`; las diferencias de esta actualización se consultan con `git diff 978c7e5 -- docs/features/006-solicitar-confirmacion-de-pago/plan.md`.

| ID | Sección(es) | Original (2026-10-09) → vigente (2026-10-29) | Motivo |
|---|---|---|---|
| **ACT-01** | Technical Context: `Primary Dependencies`, `Testing` | `Spring Boot 4.1.1`; MapStruct condicional; testing sin versiones → `Spring Boot 4.1.2`; mapeo manual (sin MapStruct); JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers, ArchUnit 1.5.1, WireMock 3.13.2 y JaCoCo 0.8.15 | Alinear el plan con el `pom.xml` y con los mapeos manuales realmente usados. |
| **ACT-02** | `Estado actual del repositorio` | «No existen `domain`/`application`/`infrastructure`, migraciones ni reglas ArchUnit» → UC01–UC05 y UC11 implementados; UC06 ya tiene `PaymentConfirmationStatus`, `ChargeIntentNotFoundException`, `GetPaymentConfirmationUseCase`, `PaymentConfirmationQuery` y `PaymentConfirmationResult`; `V1__initial_schema.sql` y `ArchitectureTest`/`UcNNArchitectureTest` existentes | Registrar el estado real del repositorio antes de implementar UC06. |
| **ACT-03** | `Project Structure`, T003 y T008 | Estructura sin configuración de wiring; IT `*IT`; `ArchitectureTest` base; `ProblemDetailsConfig` para los mapeos; puerto sin firma publicada → `PaymentConfirmationConfiguration`; `PaymentConfirmationLatestIntentIntegrationTest`; `Uc06ArchitectureTest`; mapeos en `GlobalExceptionHandler`/`ErrorCode`; firma `PaymentConfirmationResult get(PaymentConfirmationQuery)` publicada; `PaymentConfirmationStatus.from(ChargeIntentStatus)` | Reflejar las rutas, convenciones de prueba y piezas compartidas reales, y publicar la firma del puerto de entrada. |
| **ACT-04** | Reglas de negocio 4–5 y ejemplo de `Contratos de API` | «Los montos salen de la intención» y ejemplo con `authorized_amount: "990000.00"`/`charged_amount: null` → `charged_amount = amount` solo en `APPROVED`; `authorized_amount`, `captured_amount` y `released_amount` siempre `null`; `detail ← status_detail`; ejemplo alineado con el contrato (`charged_amount: "990000.00"`) | `charge_intent` solo guarda `amount`; el cobro es directo con captura automática (D-32) y los reembolsos pertenecen a UC09. |
| **ACT-05** | `Estrategia de testing`, CE-001…CE-003 y T009–T016 | WireMock obligatorio para CE-002; IT `*IT`; cobertura sin puerta de UC06 → CE-002 por ArchUnit + reflexión; IT con Testcontainers y `createdAt` distintos; JaCoCo con `PaymentConfirmationStatus` y `GetPaymentConfirmationService` | Ajustar las pruebas a la arquitectura y a las herramientas reales del repositorio. |
| **ACT-06** | `Implementation Phases` | Todas las tareas en `[ ]` y sin T008; T003/T017/T018 descritas como trabajo pendiente → T003, T017 y T018 en `[x]`; T001 anotada (enum y excepción ya existen, falta el mapeo); T007 reducida a verificación; T008 = `PaymentConfirmationConfiguration`; T014 = `Uc06ArchitectureTest`; T016 = JaCoCo + `clean verify` | Reflejar lo ya implementado y cerrar los huecos de wiring, arquitectura y cobertura. |
| **ACT-07** | `Trazabilidad`, `Decisiones de diseño (D-UC06-xx)` y `Dependencies & Execution Order` | RNF-003 apuntaba a `ProblemDetailsConfig`; sin decisiones sobre montos ni desempate; grafo sin T008/T017/T018 → RNF-003 apunta a `GlobalExceptionHandler`/`ErrorCode`; nuevas `D-UC06-04` (montos), `D-UC06-05` (desempate por `id`) y `D-UC06-06` (APPROVED/captura); `D-UC06-01` menciona el desempate; grafo actualizado | Alinear la trazabilidad con los componentes reales y documentar las decisiones adoptadas. |
| **ACT-08** | `Notes` | Sin mención a montos nulos ni al desempate → se aclaran `UNKNOWN`, los montos nulos, el desempate por `id` y el commit base del versionado | Dejar constancia de las reglas que el implementador necesita. |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.2 (`pom.xml`). Para UC06: Spring Web MVC, Validation, ArchUnit 1.5.1 y JaCoCo 0.8.15. El mapeo entre capas es manual (sin MapStruct: `ChargePersistenceMapper` y los controllers lo hacen así). Las dependencias se registran en el setup común, sin referencias a tareas externas. (Sin *starter* de seguridad: fuera de alcance, general-plan §7.1.) **[ACT-01]**
**Storage**: PostgreSQL 16+; UC06 **solo lee** `charge_intent` (tabla de UC05) [general-plan §3.5]; no define migraciones propias (migración inicial única, general-plan D-31)
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (PostgreSQL), WireMock 3.13.2 (disponible; CE-002 se resuelve con ArchUnit + reflexión, sin WireMock) y ArchUnit 1.5.1 [general-plan]. JaCoCo 0.8.15 ejecuta la puerta de cobertura de UC06 en `verify`. **[ACT-01] [ACT-05]**
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: Sin meta propia: el SPEC 06 no define metas de rendimiento [CONV].
**Constraints**: solo lectura; sin llamadas a la pasarela [SPEC RF-006]; `BigDecimal` en los montos [SPEC RNF-002]; invocable únicamente por el Sistema de Reservas y Operaciones [SPEC RF-001; contrato]
**Scale/Scope**: 1 endpoint REST; 0 tablas propias; 6 RF + 3 RNF + 3 CE + 1 HU

**Estado actual del repositorio**: UC01–UC05 y UC11 están implementados; existen `domain`/`application`/`infrastructure`, `V1__initial_schema.sql` y las reglas `ArchitectureTest`/`UcNNArchitectureTest`. UC06 ya cuenta con `PaymentConfirmationStatus`, `ChargeIntentNotFoundException`, `GetPaymentConfirmationUseCase`, `PaymentConfirmationQuery` y `PaymentConfirmationResult`; faltan el mapeo de estados, el servicio, el controller, el wiring y las pruebas. Las piezas compartidas `DomainException`, `ErrorCode`, `GlobalExceptionHandler`, `ApiProblemDetailsFactory` y los serializadores de `JacksonConfig` ya están cableadas. **[ACT-02]**

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

### Source Code (repository root) **[ACT-03]**

```text
src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── valueobject/
│   │   └── PaymentConfirmationStatus.java       # T001  enum + from(ChargeIntentStatus) [general-plan §8]
│   └── exception/
│       └── ChargeIntentNotFoundException.java   # T001  ya existe en el núcleo compartido [SPEC RF-005]
├── application/
│   ├── port/in/
│   │   └── GetPaymentConfirmationUseCase.java   # T003  PaymentConfirmationResult get(PaymentConfirmationQuery)
│   ├── service/
│   │   └── GetPaymentConfirmationService.java   # T005
│   └── dto/
│       ├── PaymentConfirmationQuery.java        # T003  ya existe = SolicitudConfirmacionPago [SPEC]
│       └── PaymentConfirmationResult.java       # T003  ya existe = ConfirmacionPagoResultado [SPEC]
└── infrastructure/
    ├── adapter/in/web/
    │   ├── PaymentConfirmationController.java   # T006
    │   └── dto/PaymentConfirmationResponse.java # T006  claves del contrato [CONV]
    └── config/
        └── PaymentConfirmationConfiguration.java # T008  wiring del servicio (application sin Spring)

src/test/java/com/seashare/seasharem3/
├── arch/Uc06ArchitectureTest.java                                      # T014
├── domain/valueobject/PaymentConfirmationStatusTest.java               # T002
├── application/service/GetPaymentConfirmationServiceTest.java          # T004
├── contract/UC06PaymentConfirmationContractTest.java                   # T009, T013
├── integration/PaymentConfirmationLatestIntentIntegrationTest.java     # T010 (Testcontainers)
└── acceptance/Uc06AcceptanceTest.java                                  # T011, T012, T013
```

El puerto de salida `ChargeIntentRepository` **no** se crea aquí: es de UC05 y UC06 lo referencia por nombre. UC06 solo requiere el método `findLatestByReservationId` (ver D-UC06-02). No hay puertos expuestos: ningún otro caso de uso llama a `GetPaymentConfirmationUseCase`. `GetPaymentConfirmationUseCase` publica la firma `PaymentConfirmationResult get(PaymentConfirmationQuery)`; al implementar se retira del javadoc la nota «GAP: firma no publicada por el plan». **[ACT-03]**

**Structure Decision**: servicio único hexagonal; `application` solo depende de `domain`; el controller invoca únicamente `application.port.in` y nunca un repositorio; ningún componente de UC06 depende de `ChargeGatewayPort` [general-plan §3.4; SPEC RF-006]. El servicio se registra como bean en `PaymentConfirmationConfiguration` (patrón de `ChargeConfiguration`), porque `application` no puede depender de Spring; el mapeo es manual y no usa MapStruct. **[ACT-03]**

## Reglas de negocio

Todas provienen del SPEC 6 y del contrato `UC06-confirmacion-pago.md`, salvo indicación.

1. **Quién puede llamarlo** [SPEC RF-001; contrato]: el Sistema de Reservas y Operaciones es el usuario esperado; el sistema **no valida** el llamador (seguridad fuera de alcance; general-plan §7.1, D-21).
2. **Búsqueda** [SPEC RF-002]: se consulta la `ChargeIntent` de la reserva. Una reserva puede tener varias (UC05 RF-004A); se devuelve **la más reciente por `created_at`**, desempatando por `id` descendente para que el resultado sea determinista **[CONV; D-UC06-05]** (el SPEC 6 habla de "la" intención, en singular). **[ACT-07]**
3. **Estados** [SPEC RF-003; general-plan §8]:

| Estado interno (`ChargeIntent`) | `status` devuelto |
|---|---|
| `PENDING_SEND`, `IN_PROCESS` | `IN_PROCESS` |
| `CAPTURED` | `APPROVED` |
| `REJECTED` | `REJECTED` |
| `CANCELLED` | `CANCELLED` |
| `EXPIRED` | `EXPIRED` |
| `COMMUNICATION_ERROR` | `UNKNOWN` (valor vigente del enum) |

4. **Fidelidad** [SPEC RF-003, caso extremo "en proceso"]: se devuelve el estado registrado sin anticipar un resultado. Un cobro `REJECTED`, `CANCELLED` o `EXPIRED` jamás se reporta como aprobado. En este repositorio `APPROVED` se deriva de `CAPTURED` (captura confirmada por la pasarela); la respuesta **no afirma** que la captura o la liquidación se hayan ejecutado porque `captured_amount` y `released_amount` quedan en `null` [SPEC escenario 1; D-UC06-06]. **[ACT-04]**
5. **Montos y referencia** [SPEC RF-004; D-UC06-04]: la única fuente es la `ChargeIntent`, que guarda `amount`, `status_detail` y `external_reference`. Se devuelve `charged_amount = amount` **solo cuando el estado es `APPROVED`**; `authorized_amount`, `captured_amount` y `released_amount` van siempre en `null` (el cobro es directo con captura automática, D-32, y los reembolsos pertenecen a UC09). `detail ← status_detail` y `external_reference ← external_reference`; los no disponibles van como `null`. Escala y redondeo de los montos en JSON: 4 decimales internos y salida redondeada `HALF_UP` a 2 decimales [CONV; `contracts/README.md` §3.1]. **[ACT-04]**
6. **Solo lectura** [SPEC RF-002, caso extremo "solicitud repetida"]: cada consulta devuelve el estado vigente, sin crear ni modificar registros.
7. **Sin intención** [SPEC RF-005, RNF-003]: `404 CHARGE_INTENT_NOT_FOUND`, `retryable: true` (UC05 la crea de forma asíncrona tras `PENDING`) [contrato; README E3].
8. **Orden de validación** [README §4.3]: E1 → E2 (`400`, `reservation_id` con formato inválido) → E3 (`404`). No se consulta la base antes de validar el formato; no hay filas de autenticación (seguridad fuera de alcance).
9. **Estado externo desconocido y cobro directo** [general-plan §8, D-32; contrato]: el estado externo desconocido se devuelve como `UNKNOWN`; la falla de comunicación interna (`COMMUNICATION_ERROR`) se mapea a `UNKNOWN`; la consulta no cambia estados. El cobro es directo con captura automática: sin fase de autorización (no hay estado `AUTHORIZED` operativo ni expiración de autorización); `EXPIRED` significa que la operación expiró sin cobro (valor externo de la pasarela).

## Contratos de API

Fuente: `contracts/rest/UC06-confirmacion-pago.md` [general-plan §6]. El SPEC no fija la ruta; rige el contrato.

### `GET /api/v1/reservations/{reservation_id}/payment-confirmation`

- **200 OK** — campos: `reservation_id`, `status`, `detail`, `authorized_amount`, `captured_amount`, `released_amount`, `charged_amount`, `external_reference` (los montos como *string* decimal o `null`):

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "status": "APPROVED",
  "detail": null,
  "authorized_amount": null,
  "captured_amount": null,
  "released_amount": null,
  "charged_amount": "990000.00",
  "external_reference": "pg-8841"
}
```

En `APPROVED` solo `charged_amount` tiene valor; los demás montos son `null` mientras UC05/UC09 no publiquen esas columnas [D-UC06-04]. El `detail` del 200 es el `status_detail` de la intención y no debe confundirse con el `detail` de Problem Details. **[ACT-04]**

| HTTP | `code` | Cuándo | `retryable` | En catálogo §3.4 |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reservation_id` no es un UUID | No | sí |
| 404 | `CHARGE_INTENT_NOT_FOUND` | No existe `ChargeIntent` para la reserva | Sí | sí |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | sí |

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %. JaCoCo 0.8.15 ejecuta la puerta en `verify`; se incluyen `PaymentConfirmationStatus` en el bloque de dominio y `GetPaymentConfirmationService` en el de aplicación, porque sin esas inclusiones la cobertura de UC06 no se mide. **[ACT-05]**

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | mapeo de los 7 estados internos a los 6 devueltos | `PaymentConfirmationStatusTest` |
| Unitario (`application`) | intención presente/ausente, nulos, la más reciente | `GetPaymentConfirmationServiceTest` (Mockito sobre `ChargeIntentRepository`) |
| Contrato (MockMvc) | cuerpos y códigos del contrato; los tres escenarios de aceptación; `standaloneSetup` con `GlobalExceptionHandler` real y `JacksonConfig` | `UC06PaymentConfirmationContractTest` |
| Integración (Testcontainers) | varias intenciones por reserva → la más reciente (desempate por `id`) | `PaymentConfirmationLatestIntentIntegrationTest` |
| Arquitectura | sin dependencia hacia la pasarela ni hacia repositorios desde el servicio/controller | `Uc06ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-003)**:

- **`ce001_estado_devuelto_coincide_con_la_intencion`** (T011): para cada estado interno, la respuesta coincide con la intención de UC05; `charged_amount` solo aparece en `APPROVED` y los demás montos son `null`. **[ACT-04]**
- **`ce002_no_se_realizan_solicitudes_a_la_pasarela`** (T012): `Uc06ArchitectureTest` confirma que ni `GetPaymentConfirmationService` ni `PaymentConfirmationController` dependen de `ChargeGatewayPort`; la prueba de aceptación inspecciona el constructor del servicio (solo `ChargeIntentRepository`) y verifica que una consulta no produce ninguna interacción con la pasarela. No requiere WireMock. **[ACT-05]**
- **`ce003_reserva_sin_intencion_responde_error_controlado`** (T013): `404 CHARGE_INTENT_NOT_FOUND`, `retryable: true`, sin respuestas ambiguas.

## Decisiones de diseño (D-UC06-xx)

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC06-01** | El SPEC 6 consulta "la `IntenciónDeCobro`" (singular); el SPEC 5 RF-004A permite varias por reserva | SPEC 6 RF-002 vs SPEC 5 RF-004A | Se devuelve la más reciente por `created_at` (índice `(reservation_id, created_at DESC)`, general-plan §4), con desempate por `id` descendente (D-UC06-05); UC05 y UC06 se desarrollan en la misma fase | **Aplicada** |
| **D-UC06-02** | `findLatestByReservationId` y `status_detail` los publica UC05; UC06 los consume por nombre | UC06 vs plan UC05 | UC06 consume las firmas publicadas por UC05 (misma fase); no redeclara el puerto ni la columna | **Aplicada** |
| **D-UC06-03** | Los estados `IN_PROCESS` y `UNKNOWN` del SPEC 6 no equivalen uno a uno a los estados internos | SPEC 6 RF-003 vs general-plan §8 | Mapeo de la tabla de Reglas de negocio, sin estado nuevo; `COMMUNICATION_ERROR` se expone como `UNKNOWN` | **Aplicada** |
| **D-UC06-04** | El contrato publica cuatro montos, pero `charge_intent` solo guarda `amount`; `authorized_amount` choca con el cobro directo (D-32) y `released_amount` pertenece a UC09 | SPEC 6 RF-004 vs contrato vs modelo/tabla de UC05 | `charged_amount = amount` solo en `APPROVED`; `authorized_amount`, `captured_amount` y `released_amount` siempre `null`; no se amplía el esquema ni se toca UC05 | **Aplicada** |
| **D-UC06-05** | `findLatestByReservationId` ordena solo por `created_at DESC`; dos intenciones con el mismo instante dan un resultado no determinista | UC06 plan regla 2 vs implementación de UC05 | Desempate por `id DESC` en la consulta JPA de UC05; el puerto no cambia y la operación sigue siendo de solo lectura | **Aplicada** |
| **D-UC06-06** | El SPEC dice que `APPROVED` no implica captura, pero el mapeo lo deriva de `CAPTURED` (captura confirmada) | SPEC 6 escenario 1 vs general-plan §8 y semántica de UC05 | Se mantiene el mapeo `CAPTURED → APPROVED`; la respuesta no afirma captura ni liquidación porque `captured_amount` y `released_amount` quedan en `null` | **Aplicada** |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad granular y verificable; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias se expresan mediante puertos, firmas y tablas, no mediante tareas externas.
>
> **Actualización 2026-10-29 [ACT-06]**: `T003`, `T017` y `T018` ya están satisfechas por el núcleo compartido y se marcan `[x]`; `T001` sigue abierta porque falta el mapeo `ChargeIntentStatus → PaymentConfirmationStatus`; `T008` se incorpora para el wiring. El resto de tareas permanece `[ ]` hasta implementar UC06.

### Phase 1: Setup

- [x] **T017** · registrar starters requeridos y base de `ArchitectureTest`. **[ACT-06: satisfecha por el setup de UC01/UC02; `pom.xml` y `ArchitectureTest` ya existen.]**

### Phase 2: Foundational

- [x] **T018** · consumir `DomainException` y `ProblemDetailsConfig` por sus contratos compartidos; UC06 no los redeclara (sin configuración de seguridad: fuera de alcance, general-plan §7.1). **[ACT-06: satisfecha; los mapeos de UC06 viven en `GlobalExceptionHandler`/`ErrorCode`.]**

### Phase 3: US1 — Consultar el resultado de un cobro (HU1; RF-001…RF-006; RNF-001…RNF-003; CE-001…CE-003)

- [ ] **T001** · Dominio: completar `PaymentConfirmationStatus` con `from(ChargeIntentStatus)` y el mapeo de §8 (incluye `UNKNOWN`); `ChargeIntentNotFoundException` ya existe en el núcleo compartido. Consume `ChargeIntentStatus` por nombre desde UC05. **[ACT-06: el enum y la excepción ya existen; falta el mapeo.]**
- [ ] **T002** · Pruebas del mapeo: estados internos → estados devueltos, incluyendo `COMMUNICATION_ERROR`→`UNKNOWN`.
- [x] **T003** · `GetPaymentConfirmationUseCase`, `PaymentConfirmationQuery`, `PaymentConfirmationResult` (montos `BigDecimal` anulables). · M: `none` · P: `pending` **[ACT-06: ya existen; la firma publicada es `PaymentConfirmationResult get(PaymentConfirmationQuery)`.]**
- [ ] **T004** · Pruebas de `GetPaymentConfirmationService` (Mockito): intención aprobada (escenario 1), rechazada (2), en proceso (3), sin intención (RF-005), montos nulos, la más reciente. · M: `none` · P: `pending`
- [ ] **T005** · `GetPaymentConfirmationService` (`@Transactional(readOnly = true)`): lee por `ChargeIntentRepository.findLatestByReservationId`, traduce el estado, lanza `ChargeIntentNotFoundException` con el mensaje del contrato. Sin dependencia de la pasarela; consume la firma de UC05 por nombre.
- [ ] **T006** · `PaymentConfirmationController` (`GET`) y `PaymentConfirmationResponse` con las claves del contrato (`@JsonProperty`, montos como *string* decimal, redondeo `HALF_UP` a 2 decimales). · M: `none` · P: `pending`
- [ ] **T007** · verificar que `GlobalExceptionHandler`/`ErrorCode` ya traducen `CHARGE_INTENT_NOT_FOUND` a 404 `retryable: true` y `VALIDATION_ERROR` a 400 por `reservation_id` no UUID; no se reconfigura el handler compartido. **[ACT-06: el mapeo ya existe; esta tarea es de verificación.]**
- [ ] **T008** · `PaymentConfirmationConfiguration`: registrar `GetPaymentConfirmationService` como bean (patrón de `ChargeConfiguration`), porque `application` no puede depender de Spring. · M: `none` · P: `pending` **[ACT-06]**
- [ ] **T009** · Pruebas de contrato MockMvc con los ejemplos del contrato: `200` por cada estado, `400`, `404`, y que repetir la consulta no modifica datos; `standaloneSetup` con `GlobalExceptionHandler` real y `JacksonConfig`. · M: `none` · P: `pending`
- [ ] **T010** · Integración (Testcontainers): varias `ChargeIntent` de una reserva con `createdAt` distintos → se devuelve la más reciente usando el adaptador de persistencia publicado por UC05; se cubre el desempate por `id` (D-UC06-05).
- [ ] **T011** · **`ce001_estado_devuelto_coincide_con_la_intencion`** (CE-001). · M: `none` · P: `pending`
- [ ] **T012** · **`ce002_no_se_realizan_solicitudes_a_la_pasarela`** (CE-002). · M: `none` · P: `pending`
- [ ] **T013** · **`ce003_reserva_sin_intencion_responde_error_controlado`** (CE-003). · M: `none` · P: `pending`

### Phase 4: Polish & Cross-Cutting Concerns

- [ ] **T014** · `Uc06ArchitectureTest`: `GetPaymentConfirmationService` y el controller no dependen de `ChargeGatewayPort` ni de repositorios; entidades JPA fuera de `adapter/out/persistence` (RF-006). · M: `none` · P: `pending` **[ACT-06]**
- [ ] **T015** · Verificar el contrato `UC06-confirmacion-pago.md` frente a la implementación (rutas, códigos, `retryable`) y registrar cualquier diferencia como D-UC06-xx. · M: `none` · P: `pending`
- [ ] **T016** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]) con `PaymentConfirmationStatus` y `GetPaymentConfirmationService` en los `includes` de JaCoCo, y `./mvnw clean verify`. · M: `none` · P: `pending` **[ACT-05]**

## Dependencies & Execution Order

```text
Núcleo compartido + contratos publicados por UC05 ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010 ─> T011 ─> T012 ─> T013 ─> T014 ─> T015 ─> T016
```

- **Depende de**: `ChargeIntentStatus`, `ChargeIntentRepository.findLatestByReservationId`, `amount`, `status_detail`, `external_reference` y el adaptador de persistencia publicados por UC05. La consulta de la más reciente se endurece con `id DESC` en ese adaptador (D-UC06-05).
- **Ya satisfecho**: T003, T017 y T018; el orden efectivo de implementación empieza en T001/T002. **[ACT-06]**
- **No bloquea** a otros planes: nadie llama a UC06 desde dentro del sistema.

## Notes

- El SPEC 6 no define metas de rendimiento (Technical Context).
- `UNKNOWN` para fallas de comunicación es el valor vigente del enum.
- Etiquetas usadas: `[SPEC]`/`[CONV]`.
- `charged_amount` solo se informa en `APPROVED`; `authorized_amount`, `captured_amount` y `released_amount` son `null` mientras UC05/UC09 no publiquen esas columnas (D-UC06-04). **[ACT-04]**
- La más reciente se desempata por `id` descendente (D-UC06-05). **[ACT-07]**
- El `detail` del 200 es el `status_detail` de la intención; no debe confundirse con el `detail` de Problem Details. **[ACT-04]**
- Versión original del plan: commit `978c7e5` (2026-10-18); actualización: 2026-10-29. **[ACT-08]**
- Los valores numéricos de los ejemplos son ilustrativos. La escala y el redondeo siguen la regla común de los contratos (4 decimales internos; salida `HALF_UP` a 2 decimales, `contracts/README.md` §3.1); UC06 no modifica montos.
