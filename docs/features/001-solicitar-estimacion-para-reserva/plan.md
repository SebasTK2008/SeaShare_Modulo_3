# Implementation Plan: UC01 - Solicitar Estimación para Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC01 es el caso de uso **síncrono** por el que el Sistema de Reservas y Operaciones obtiene, ya calculadas, las estimaciones de precio de las embarcaciones, en dos modalidades: **lote** (HU1) y **individual** (HU2). En lote Reservas envía **únicamente** la lista de identificadores (`boat_ids`) y el sistema fija siempre 1 día, 1 pasajero y la **fecha actual** como fecha de evaluación; en individual Reservas envía `boat_id`, `start_date`, `end_date` y `passengers` y el sistema devuelve el total más una **advertencia obligatoria** que Reservas solo renderiza [SPEC RF-001, RF-002, RF-003, RF-004, RF-007, HU1, HU2]. Reservas no hace ninguna operación de precio (CE-002): toda la matemática reside en el sistema.

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. UC01 **no calcula la tarifa dinámica**: delega en UC02 vía `ProvideBaseRateUseCase`, y lee los parámetros financieros mediante el puerto publicado por su dueño. Aplica la fórmula con `PricingCalculator` de dominio puro y responde por dos adaptadores REST. Los errores siguen Problem Details y `contracts/README.md` §3.4. UC01 no persiste nada ni define migraciones propias (migración inicial única, general-plan D-31).

UC01 depende de UC02 para `ProvideBaseRateUseCase` y del núcleo compartido para `ClockPort`, `Money`, `BoatId` y los parámetros financieros. Los VOs compartidos son del núcleo compartido (general-plan §3.6) y no se redeclaran en este plan.

El tratamiento de una embarcación ausente del resultado de UC02 queda definido (D-UC01-05): un `boat_id` no reconocido por Flota se **omite** del resultado y una embarcación reconocida sin tarifa base se reporta en `unavailable[]`; nunca se inventa una tarifa.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** devolver estimaciones precisas desde la lista de IDs | `EstimateBatchUseCase`/`EstimateBatchService`, `EstimateSingleUseCase`/`EstimateSingleService`, `EstimateController` | T008–T017 | T013, T017, T019 |
| **RF-002** fórmula del lote + supuestos fijos (1 día, 1 pasajero, fecha actual), sin depósito ni penalidades | `PricingCalculator`, `EstimateBatchService`, `BatchEstimateResult` | T005, T009 | T006, T010, T013, T019 |
| **RF-003** fórmula individual, días inclusivos, tarifa de la fecha de inicio | `PricingCalculator`, `EstimateSingleService` | T005, T014, T015 | T017, T019 |
| **RF-004** exponer endpoint(s) para las solicitudes | `EstimateController` | T011, T016 | T013, T017 |
| **RF-005** consultar a Flota con la lista de IDs (una sola vez) | `ProvideBaseRateUseCase` (de UC02) | T009 | T010, T022 |
| **RF-006** límite máximo estricto del lote | `BatchEstimateRequest` + `EstimateConfiguration` (`seashare.estimates.max-batch-size`) | T012 | T013, T019 |
| **RF-007** rechazar todo atributo distinto de `boat_ids` en el lote | `BatchEstimateRequest` (DTO estricto) | T011 | T013, T021 |
| **RNF-001** DTOs mínimos en la comunicación | `BatchEstimateRequest/Response`, `SingleEstimateRequest/Response`, commands/results de aplicación | T008, T011, T014, T016 | T013, T017 |
| **RNF-002** `BigDecimal`, precisión interna de 4 decimales antes del redondeo final | `Money` (de UC02), `PricingCalculator` | T005, T006 | T019 |
| **RNF-003** manejo de errores (*timeouts*, *fallbacks*) y sin valores parciales | `GlobalExceptionHandler`, Resilience4j en el cliente de Flota (de UC02) | T007, T018 | T020 |
| **CE-001** precisión financiera en 100 pruebas | `PricingCalculator` + `EstimateAcceptanceTest` | T005, T019 | **T019 `ce001_…`** |
| **CE-002** 0 lógica de precio en Reservas (arquitectura) | ArchUnit + `EstimateController` (solo mapea) | T023, T024 | **T023 `ce002_…`** |
| **CE-003** negocio / transparencia (<2% tickets) | `warning` obligatorio de HU2 (métrica de negocio, no automatizable) | T005, T016 | **T023 `ce003_…`** |
| **CE-004** resiliencia: fallas simuladas → fallback elegante | `GlobalExceptionHandler` + `EstimateResilienceIT` | T018, T020 | **T020 `ce004_…`** |
| **CE-005** 100% de lotes con atributos extra rechazados sin cálculo | `BatchEstimateRequest` estricto | T011 | **T021 `ce005_…`** |
| **HU1** lote (P1) | Fase 3 | T008–T013 | T010, T013, T019 |
| **HU2** individual + aviso (P1) | Fase 4 | T014–T017 | T017 |
| **HU3** tarifa base de Flota (P2, sistema externo) | Consulta a Flota vía UC02; carga ligera | T018, T022 | T022 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC01: Spring Web MVC, Validation, Jackson, MapStruct, ArchUnit y WireMock/Resilience4j cuando correspondan. Las dependencias compartidas se coordinan fuera de este plan; no se modifica `pom.xml` aquí.
**Storage**: PostgreSQL 16+; UC01 **no tiene tablas propias** y no define migraciones (migración inicial única, general-plan D-31). Solo **lee la versión vigente** de `financial_parameters` (tabla versionada de UC11) para la tarifa de seguro por pasajero [SPEC general-plan §4]
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, WireMock, ArchUnit, Testcontainers (para el contexto Spring de integración) [CONV]
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: Sin meta propia: se heredan los RNF del SPEC y los límites del plan general. HU3 fija el SLA de **Flota** (< 500 ms para 50 embarcaciones, SPEC 01 HU3 / contrato Flota); para UC01 rige el timeout de lectura de 1 s del cliente de Flota [SPEC general-plan §7.2]
**Constraints**: `BigDecimal` con precisión interna de 4 decimales y redondeo final a 2 decimales en la respuesta [SPEC RNF-002; D-UC01-07]; lote definido **solo** por `boat_ids`, sin fechas ni pasajeros [SPEC RF-007]; nunca estimaciones parciales ni valores asumidos ante fallo de Flota o parámetros faltantes [SPEC casos extremos, RNF-003]; operación de solo lectura, idempotente, sin efectos secundarios
**Scale/Scope**: 2 endpoints REST (`POST /api/v1/estimates/batch`, `POST /api/v1/estimates/individual`) [contratos UC01]; 7 RF + 3 RNF + 5 CE + 3 HU del SPEC 01; 1 puerto de entrada por modalidad y puertos compartidos consumidos por nombre.

**Estado actual del repositorio (relevante para UC01)**: existen `SeashareM3Application.java`, `application.properties` (solo `spring.application.name=seashare-m3`), `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** los paquetes `domain`/`application`/`infrastructure`, ni `Money`/`BoatId`/`ClockPort`/`PricingCalculator`, ni los puertos de UC02/UC11. Los planes de UC02 y UC11 ya están redactados; UC01 consume sus piezas por nombre.

## Project Structure

### Documentation (this feature)

```text
docs/features/001-solicitar-estimacion-para-reserva/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                        # Leyenda, formatos, Problem Details, catálogo de errores §3.4, tabla de decisión §4
└── rest/
    ├── UC01-estimacion-lote.md                      # POST /api/v1/estimates/batch (HU1)
    └── UC01-estimacion-individual.md                # POST /api/v1/estimates/individual (HU2)
```

Contratos/piezas de otros planes (solo referencia entre planes; **no se re-planifican**): `external/flota-consulta-tarifas-base.md` (conexión única de Flota, vía UC02), plan de UC02 (`ProvideBaseRateUseCase`, `ClockPort`, `Money`, `BoatId`, `BaseRateException`) y plan de UC11 (`FinancialParametersRepository`, `ProblemDetailsConfig`).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── valueobject/
│   │   ├── Money.java                               # núcleo compartido — consumido por nombre
│   │   └── BoatId.java                              # núcleo compartido — consumido por nombre
│   ├── service/
│   │   └── PricingCalculator.java                   # T005 [SPEC RF-002/RF-003, RNF-002; general-plan §3.3]
│   └── exception/
│       ├── DomainException.java                     # núcleo compartido
│       └── BaseRateException.java                   # de UC02 (T014) — consumido por nombre
├── application/
│   ├── port/in/
│   │   ├── EstimateBatchUseCase.java                # T009 [general-plan §3.5]
│   │   ├── EstimateSingleUseCase.java               # T015 [general-plan §3.5]
│   │   └── ProvideBaseRateUseCase.java              # de UC02 (T016) — consumido por nombre
│   ├── port/out/
│   │   ├── FinancialParametersRepository.java       # puerto del dueño de parámetros — consumido por nombre
│   │   └── ClockPort.java                           # núcleo compartido — consumido por nombre
│   ├── service/
│   │   ├── EstimateBatchService.java                # T009 [general-plan §3.5]
│   │   └── EstimateSingleService.java               # T015 [general-plan §3.5]
│   └── dto/
│       ├── EstimateBatchCommand.java                # T008   # List<BoatId> [SPEC RNF-001; nombre [CONV]]
│       ├── BatchEstimateResult.java                 # T008   # evaluationDate, durationDays, passengers, estimates, unavailable
│       ├── BoatEstimate.java                        # T008   # boatId + estimatedTotal
│       ├── UnavailableBoat.java                     # T008   # boatId + reason (NO_BASE_RATE)
│       ├── EstimateSingleCommand.java               # T014   # boatId, startDate, endDate, passengers
│       ├── SingleEstimateResult.java                # T014   # boatId, start/end, durationDays, passengers, estimatedTotal, warning
│       └── EstimateWarning.java                     # T005   # texto literal obligatorio de HU2 [SPEC]
└── infrastructure/
    └── adapter/in/web/
        ├── EstimateController.java                  # T011 (batch), T016 (individual) [CONV]
        ├── error/GlobalExceptionHandler.java        # T007   # compartido, ya existe; mapea códigos del catálogo [CONV]
        └── dto/
            ├── BatchEstimateRequest.java            # T011   # boat_ids + rechazo de campos extra [SPEC RF-007]
            ├── BatchEstimateResponse.java           # T011   # snake_case, estimated_total string 2 dec
            ├── SingleEstimateRequest.java           # T016
            └── SingleEstimateResponse.java          # T016   # incluye warning

    └── config/
        └── EstimateConfiguration.java               # T012   # `seashare.estimates.max-batch-size` [general-plan §7.4]

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── ArchitectureTest.java                        # T002 (base) + T023, T024 (reglas de UC01)
├── domain/service/
│   └── PricingCalculatorTest.java                   # T006
├── application/service/
│   ├── EstimateBatchServiceTest.java                # T010
│   └── EstimateAcceptanceTest.java                  # T019 (ce001), T020 (ce004), T021 (ce005), T023 (ce002/ce003)
├── infrastructure/adapter/in/web/
│   ├── EstimateControllerContractTest.java          # T013, T017
│   └── EstimateControllerValidationTest.java        # T012 (límite de lote), T021 (campos extra)
└── integration/
    └── EstimateResilienceIT.java                    # T018 (WireMock sobre Flota vía UC02)
```

**Firmas referenciadas**: UC01 no expone puertos a otros casos de uso. Consume por nombre `ProvideBaseRateUseCase`, `ClockPort`, `Money`, `BoatId` y `FinancialParametersRepository` del catálogo común; no redeclara VOs ni redefine puertos del núcleo compartido (general-plan §3.6).

**Structure Decision**: servicio único hexagonal bajo `com.seashare.seasharem3`; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; adaptadores de entrada invocan `application.port.in`; controllers no acceden a repositorios ni servicios concretos; DTOs HTTP viven en infraestructura y application trabaja con commands/results. Identificadores y enums siguen los nombres vigentes del `general-plan.md`.

## Reglas de negocio

Todas las reglas provienen del SPEC 01 (`spec.md`), sus contratos o el `general-plan.md`, salvo indicación. Ejemplos con tarifa base final (ya ajustada por UC02) de `350000.00` y seguro `15000.00` por pasajero.

1. **Dos modalidades, un caso de uso** [SPEC RF-004; general-plan §3.5]: lote e individual comparten la fórmula y la delegación de la tarifa dinámica en UC02; el SPEC habla de "un *endpoint*", los contratos fijan **dos rutas** `[CONV]`, decisión **D-UC01-01**.
2. **Supuestos fijos del lote** [SPEC RF-002, HU1]: duración **1 día**, **1 pasajero** y **fecha actual** como fecha de evaluación; el sistema los fija, no son parámetros de la petición. `evaluation_date` se obtiene con `ClockPort.today()` en la zona horaria de negocio `America/Bogota` (general-plan D-12) y la respuesta devuelve `duration_days = 1` y `passengers = 1`.
3. **Días inclusivos (individual)** [SPEC RF-003, casos extremos]: `días = end_date − start_date + 1`; `start_date == end_date` es válido y equivale a 1 día; solo `end_date < start_date` es inválido.
4. **Fórmula** [SPEC RF-002, RF-003]: `estimated_total = (tarifaBaseFinal × días) + (seguroPorPasajero × pasajeros)`. La tarifa base final ya incluye la tarifa dinámica (UC02). La estimación **no incluye** el depósito de garantía ni penalidades ni ajustes posteriores. Ejemplo lote (regular, 1 día, 1 pasajero): `350000.00 + 15000.00 = 365000.00`. Ejemplo lote (sábado, FE 10 %): `385000.00 + 15000.00 = 400000.00`. Ejemplo individual (20–22-dic-2026, 3 días, 4 pasajeros, TA 25 %): `(437500.00 × 3) + (15000.00 × 4) = 1372500.00`.
5. **Tarifa dinámica de la fecha correcta** [SPEC RF-002, RF-003]: lote → fecha actual; individual → `start_date` (esa tarifa se usa para toda la duración). UC01 **no** calcula tarifa dinámica: pasa `evaluatedDate` a `ProvideBaseRateUseCase`.
6. **Deduplicación del lote** [CONV, D-UC01-08]: si `boat_ids` trae identificadores repetidos, se consulta y se estima **una vez** por embarcación; la respuesta contiene una entrada por embarcación única.
7. **Lista vacía** [SPEC casos extremos; contrato lote regla 1]: `boat_ids: []` → `200` con `estimates: []` y `unavailable: []`, **sin leer parámetros ni invocar a UC02**.
8. **Límite del lote** [SPEC RF-006; general-plan D-22]: máximo `seashare.estimates.max-batch-size` (por defecto **50**). Superarlo → `400 BATCH_SIZE_EXCEEDED` **sin procesar ningún elemento**. El límite es un supuesto configurable del sistema, no un valor que Reservas pueda ampliar por petición.
9. **Campos no aceptados en el lote** [SPEC RF-007; contrato lote regla 11; CE-005]: cualquier campo distinto de `boat_ids` (`start_date`, `end_date`, `passengers` u otros desconocidos) → `400 VALIDATION_ERROR` sin ejecutar cálculo.
10. **Validación de fechas (individual)** [SPEC casos extremos; contrato individual reglas 1–2]: formato inválido, `start_date` en el pasado (comparado con `ClockPort.today()` en la zona horaria de negocio `America/Bogota`, general-plan D-12) o `end_date` anterior a `start_date` → `400 INVALID_DATE_RANGE`, sin cálculo. Esta validación **no** aplica al lote (no recibe fechas).
11. **Validación de pasajeros (individual)** [SPEC HU2; D-UC01-06]: entero **≥ 1**, **sin máximo** (la capacidad máxima no es un insumo de UC01). Valor no entero, `null`, `0` o negativo → `400 VALIDATION_ERROR`.
12. **Embarcación sin tarifa disponible** [SPEC casos extremos; contrato lote reglas 4–5; contrato individual regla 7]: individual → `422 BASE_RATE_NOT_AVAILABLE` (sin tarifa base o no reconocida por Flota); en el lote, los identificadores no reconocidos por Flota se **omiten** del resultado y las embarcaciones reconocidas sin tarifa base se reportan en `unavailable[]` (no es error). Nunca se asume un precio.
13. **Falla de Flota** [SPEC casos extremos, RNF-003; contrato lote regla 9]: `ProvideBaseRateUseCase` lanza `BaseRateException(FLEET_UNAVAILABLE)` (caída, timeout, inalcanzable o respuesta inválida) → UC01 responde `503 FLEET_UNAVAILABLE` **sin estimaciones parciales**. UC02 ya registra el fallo en `operational_failure`; **UC01 no escribe** en `operational_failure` por ser un caso síncrono que responde (D-UC01-04).
14. **Parámetros faltantes** [SPEC casos extremos; contrato lote regla 10]: si falta la tarifa de seguro (fila ausente o valor nulo) o el porcentaje que exige la fecha evaluada (`BaseRateException(DYNAMIC_RATE_NOT_CONFIGURED)` de UC02) → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, sin estimación parcial. Si la fecha evaluada es regular, no se necesita ningún porcentaje.
15. **Orden de validación** [CONV; contratos §5 y `README` §4.3]: lote `E1 → E2 → E4 → E5`; individual `E1 → E2 → E4 → E5 → E6` (no hay filas de autenticación: seguridad fuera de alcance). La petición se valida por completo (E2) antes de leer parámetros (E4) o llamar a Flota (E5); E3 no aplica (no hay recurso en la URL) y, en el lote, E6 no produce error (se reporta en `unavailable[]`).
16. **Precisión y redondeo** [SPEC RNF-002; D-UC01-07]: todos los montos son `BigDecimal`; el cálculo interno trabaja a **escala 4**; el campo `estimated_total` de la respuesta se serializa como **string decimal redondeado HALF_UP a 2 decimales**. UC01, como llamador, realiza el redondeo final de presentación.
17. **Advertencia obligatoria (individual)** [SPEC HU2 escenario 1]: toda respuesta exitosa incluye `warning` con el texto exacto (sin punto final): `Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva`. Es la evidencia funcional de CE-003 (métrica de negocio no automatizable, D-UC01-09).
18. **Seguridad fuera de alcance** [general-plan §7.1, D-21]: el sistema **no implementa** autenticación ni autorización; los contratos ya no declaran `401 UNAUTHENTICATED` / `403 FORBIDDEN`. "Solo el Sistema de Reservas y Operaciones" describe al usuario esperado, no una validación: esta fase **no implementa** `SecurityConfig` ni pruebas de 401/403.
19. **Sin persistencia ni migración** [SPEC; general-plan §3.3/§4.2]: UC01 no crea tablas ni migraciones: rige la migración inicial única (general-plan D-31); es de solo lectura de la versión vigente de `financial_parameters` (UC11, tabla versionada append-only). Repetir la petición no produce efectos (idempotente) [contrato §6].
20. **Identificadores inexistentes** [SPEC casos extremos; contrato lote regla 4]: un `boat_id` no reconocido por Flota se **omite** del resultado (no aparece en `estimates` ni en `unavailable[]`); nunca se inventa precio.

## Contratos

Fuente: `contracts/rest/UC01-estimacion-lote.md` y `contracts/rest/UC01-estimacion-individual.md`; convenciones en `contracts/README.md` §3. UC01 no fija rutas en el SPEC: rigen los contratos [SPEC general-plan §6].

### `POST /api/v1/estimates/batch` (HU1)

Body: **solo** `boat_ids` (array de UUID; puede ser `[]`; máximo 50).

```json
{ "boat_ids": ["3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01", "9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02"] }
```

**200 OK** (ejemplo con fecha regular; `evaluation_date` lo fija el reloj):

```json
{
  "evaluation_date": "2026-10-07",
  "duration_days": 1,
  "passengers": 1,
  "estimates": [
    { "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01", "estimated_total": "365000.00" }
  ],
  "unavailable": [
    { "boat_id": "9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02", "reason": "NO_BASE_RATE" }
  ]
}
```

Lista vacía → `{ "evaluation_date": "...", "duration_days": 1, "passengers": 1, "estimates": [], "unavailable": [] }`.

| HTTP | `code` | Cuándo | `retryable` |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo no JSON, `boat_ids` ausente/no arreglo/elemento no UUID, o cualquier campo distinto de `boat_ids` | No |
| 400 | `BATCH_SIZE_EXCEEDED` | `boat_ids` supera el máximo | No |
| 503 | `FLEET_UNAVAILABLE` | Flota caída, timeout, inalcanzable o respuesta inválida [E5] | Sí |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | No existe versión vigente de parámetros o falta el valor exigido por la fecha [E4] | Sí |
| 500 | `INTERNAL_ERROR` | Error no previsto [E8] | Sí |

### `POST /api/v1/estimates/individual` (HU2)

Body: `boat_id`, `start_date`, `end_date`, `passengers` (todos obligatorios).

```json
{ "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01", "start_date": "2026-12-20", "end_date": "2026-12-22", "passengers": 4 }
```

**200 OK**:

```json
{
  "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
  "start_date": "2026-12-20",
  "end_date": "2026-12-22",
  "duration_days": 3,
  "passengers": 4,
  "estimated_total": "1372500.00",
  "warning": "Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva"
}
```

| HTTP | `code` | Cuándo | `retryable` |
|---|---|---|---|
| 400 | `INVALID_DATE_RANGE` | Formato de fecha inválido, inicio en el pasado o fin anterior al inicio [E2] | No |
| 400 | `VALIDATION_ERROR` | Cuerpo no JSON, o `boat_id`/`passengers` ausentes o con tipo/rango inválido [E2] | No |
| 422 | `BASE_RATE_NOT_AVAILABLE` | Embarcación sin tarifa base o no reconocida por Flota [E6] | No |
| 503 | `FLEET_UNAVAILABLE` | Flota caída, timeout, inalcanzable o respuesta inválida [E5] | Sí |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | No existe versión vigente de parámetros o falta el valor exigido por el inicio [E4] | Sí |
| 500 | `INTERNAL_ERROR` | Error no previsto [E8] | Sí |

**Mapeo de excepciones** (implementado por el `GlobalExceptionHandler` compartido; T007 verifica):

| Origen | `reason` | HTTP / `code` |
|---|---|---|
| `BaseRateException` de UC02 | `FLEET_UNAVAILABLE` | 503 `FLEET_UNAVAILABLE` |
| `BaseRateException` de UC02 | `DYNAMIC_RATE_NOT_CONFIGURED` | 503 `FINANCIAL_PARAMETERS_NOT_CONFIGURED` |
| Ausencia en el resultado de UC02 | — | lote: los no reconocidos por Flota se omiten y los sin tarifa van a `unavailable[]`; individual: 422 `BASE_RATE_NOT_AVAILABLE` |
| `FinancialParametersRepository.findCurrent()` sin versión vigente o seguro nulo | — | 503 `FINANCIAL_PARAMETERS_NOT_CONFIGURED` |
| Validación de petición | — | 400 (`VALIDATION_ERROR` / `BATCH_SIZE_EXCEEDED` / `INVALID_DATE_RANGE`) |

Todos los errores usan *Problem Details* (RFC 9457, `contracts/README.md` §3.3) con `code` y `retryable`. Headers comunes: `Content-Type`, `Accept`, `X-Correlation-Id` (opcional; se genera si falta y se devuelve) y `Retry-After` (opcional en los `503`, en segundos) [README §3.2].

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), `contracts/rest/UC01-*` y SPEC 01. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %. Ningún test del repositorio cubre UC01 hoy.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | fórmula y precisión escala 4 (RF-002/RF-003, RNF-002), días inclusivos | `domain/service/PricingCalculatorTest` |
| Unitario (`application`) | orquestación del lote y del individual, dedupe, lista vacía, sin tarifa, parámetros faltantes, propagación de `FLEET_UNAVAILABLE`, mapeo a `unavailable[]`/`422` | `application/service/EstimateBatchServiceTest` (Mockito sobre `ProvideBaseRateUseCase`, `FinancialParametersRepository`, `ClockPort`) |
| Contrato / Web (MockMvc) | cuerpos y códigos de los dos contratos; DTO estricto del lote; límite de lote | `EstimateControllerContractTest`, `EstimateControllerValidationTest` |
| Integración (WireMock + Spring) | fallas de Flota → 503 sin parciales; cortocircuito de parámetros | `integration/EstimateResilienceIT` |
| Aceptación | CE-001, CE-002, CE-003, CE-004, CE-005 + carga ligera HU3 | `application/service/EstimateAcceptanceTest` |
| Arquitectura | reglas §3.4; sin lógica de tarifa dinámica en UC01 | `arch/ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-005)** — nóminal `ce00X_<descripcion>`:

- **`ce001_precision_financiera_100_casos`** (CE-001, T019): prueba parametrizada (JUnit `@ParameterizedTest`) con **≥ 100 casos** que cruzan fechas (regular, fin de semana, temporada alta y coincidencia FE+TA), tarifas, días inclusivos y pasajeros en ambas modalidades; valida el total exacto con `BigDecimal` y el redondeo HALF_UP a 2 decimales, con cero discrepancias.
- **`ce002_sin_logica_de_precio_fuera_del_calculo`** (CE-002, T023): ArchUnit — UC01 no depende de `DynamicRatePolicy`/`HighSeasonCalendar` (la tarifa dinámica solo vive en UC02); la única aritmética de precio está en `PricingCalculator`; el `EstimateController` solo invoca `application.port.in` y no calcula.
- **`ce003_aviso_obligatorio_presente`** (CE-003, T023): verifica que **toda** respuesta individual exitosa incluye exactamente el texto `warning`. La métrica de negocio (<2 % de tickets de "tarifas inesperadas" en 60 días) **no es automatizable**: se traza aquí y queda como criterio de negocio verificado fuera del sistema [SPEC CE-003].
- **`ce004_fallas_simuladas_sin_estimacion_parcial`** (CE-004, T020): con WireMock (timeout > 1 s, puerto cerrado, cuerpo inválido, `500` y circuito abierto) y con parámetros ausentes, el sistema responde `503 FLEET_UNAVAILABLE` o `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` completa, **sin estimaciones parciales ni cálculos asumidos**.
- **`ce005_lote_con_atributos_extra_rechazado`** (CE-005, T021): parametrizada con `start_date`, `end_date`, `passengers` y campos desconocidos → `400 VALIDATION_ERROR` en el 100 % de los casos, sin ejecutar cálculo; y 0 estimaciones calculadas con fecha/pasajeros provistos.

Además, **carga ligera HU3** (T022): batch de 50 embarcaciones contra un stub WireMock de Flota, verificando una **única** consulta a Flota y finalización dentro del presupuesto del cliente (timeout 1 s). El SLA real (< 500 ms) es compromiso de Flota [SPEC HU3].

## Discrepancias y decisiones

Registro de contradicciones detectadas al elaborar este plan. **No se resuelven en silencio**: cada una indica la decisión adoptada. Los IDs `D-UC01-xx` son propios de este plan; los `D-xx` sin prefijo son decisiones del plan general (§9).

| ID | Descripción | Fuentes en conflicto | Decisión adoptada | Estado |
|---|---|---|---|---|
| **D-UC01-01** | El SPEC RF-004 habla de "un *endpoint*" para ambas modalidades; los contratos definen dos rutas | SPEC 01 RF-004 vs contratos UC01 (lote/individual) | Rigen los contratos: dos endpoints [CONV]; los montos y reglas son idénticos salvo fechas/pasajeros y el `warning` | Decidida |
| **D-UC01-02** | El ejemplo del contrato de lote usa `evaluation_date: 2026-10-03`, que **es sábado**, y muestra `350000.00` sin ajuste de fin de semana | `UC01-estimacion-lote.md` §4 vs calendario (SPEC 02 RF-006) | El ejemplo es ilustrativo y su tarifa mostrada ya es la final tras la dinámica; en este plan se usa `2026-10-07` (miércoles) | Decidida |
| **D-UC01-03** | El contrato de lote regla 3 dice "invoca UC02 **por cada embarcación**", pero el diseño publicado de UC02 es *batch-only* (una consulta a Flota por invocación) | `UC01-estimacion-lote.md` §3 regla 3 vs plan UC02 (T016, regla 9) | UC01 invoca `ProvideBaseRateUseCase` **una sola vez** con la lista completa y deduplicada; la redacción "por cada embarcación" del contrato queda superada por el diseño de UC02 | Decidida |
| **D-UC01-04** | `general-plan.md` §3.5 asigna `FailureRecorderPort` a UC03 y §4 define la tabla `operational_failure`, pero el plan de UC11 no incluye su implementación | `general-plan.md` §3.5/§4 vs plan UC11 | UC01 **no usa** `FailureRecorderPort` (es síncrono y responde; UC02 registra los fallos de Flota); el puerto y la tabla pertenecen al núcleo compartido y los consumen los planes que sí los necesitan | Decidida |
| **D-UC01-05** | El SPEC permite interpretar de forma distinta los IDs no reconocidos y los casos sin tarifa | SPEC 01 vs contrato Flota/UC02 | En el lote, los identificadores no reconocidos por Flota se **omiten** del resultado y las embarcaciones reconocidas sin tarifa base se reportan en `unavailable[]`; en la individual, ambos casos → `422 BASE_RATE_NOT_AVAILABLE`. Nunca se asume un precio | Decidida |
| **D-UC01-06** | El SPEC 01 no fija rango para `passengers` | SPEC 01 HU2 vs contrato individual | Entero **≥ 1, sin máximo** (la capacidad no es insumo de UC01); violación → `400 VALIDATION_ERROR` | Decidida |
| **D-UC01-07** | El SPEC RNF-002 habla de 4 decimales internos "antes de cualquier redondeo final" sin fijar el redondeo de salida | SPEC 01 RNF-002 vs ejemplos de los contratos (`"365000.00"`) | Cálculo interno a escala 4; `estimated_total` **HALF_UP a 2 decimales** (string) en la respuesta | Decidida |
| **D-UC01-08** | El `general-plan` §3.3 nombra `PricingCalculator` pero §3.5 no lo asigna a ningún UC | general-plan §3.3 vs §3.5 | UC01 asume `PricingCalculator` (es la única UC con la fórmula de estimación; UC04 usa `ReservationValueCalculator`) | Decidida |
| **D-UC01-09** | CE-003 (tickets <2 %) es una métrica de negocio no verificable por el sistema | SPEC 01 CE-003 | Se traza al `warning` obligatorio de HU2 y queda como criterio de negocio verificado fuera del sistema | Decidida |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable de forma independiente; su casilla `[ ]` es el mecanismo de seguimiento. Las dependencias externas se expresan por caso de uso, puerto o tabla; no se duplican tareas de otros planes.

### Phase 1: Setup

- [ ] **T001** Configurar propiedades de UC01 (`seashare.estimates.max-batch-size`, zona horaria mediante `ClockPort`) y base de `ArchitectureTest`, sin modificar `pom.xml`.
- [ ] **T002** Verificar disponibilidad de las dependencias compartidas y de los puertos publicados por UC02/UC11; si faltan, registrarlo como dependencia, sin duplicar su definición.

### Phase 2: Foundational

- [ ] **T003** Consumir por nombre `ProvideBaseRateUseCase`, `ClockPort`, `Money`, `BoatId`, `BaseRateException` y `FinancialParametersRepository`; UC01 no los implementa ni redeclara.
- [ ] **T005** · `PricingCalculator` (`domain/service`): `Money estimate(Money finalBaseRatePerDay, int days, Money insurancePerPassenger, int passengers)` con `BigDecimal` a escala 4 y **sin redondeo final** (RNF-002); y constante `EstimateWarning.TEXT` con el texto literal de HU2 [SPEC].
- [ ] **T006** · Pruebas unitarias de `PricingCalculator`: días inclusivos, distintos pasajeros, escala 4, casos borde (1 día / 1 pasajero) y montos con 4 decimales (RNF-002).
- [ ] **T007** Verificar que el `GlobalExceptionHandler` compartido mapea `BaseRateException`, parámetros faltantes y validación a Problem Details del catálogo; **sin reglas de seguridad** (fuera de alcance, general-plan §7.1). No se crea un *advice* por caso de uso.

### Phase 3: US1 — Estimación en lote (HU1; RF-001, RF-002, RF-004, RF-005, RF-006, RF-007; CE-001, CE-005)

- [ ] **T008** · DTOs de aplicación: `EstimateBatchCommand` (`List<BoatId>`), `BatchEstimateResult` (`evaluationDate`, `durationDays`, `passengers`, `estimates`, `unavailable`), `BoatEstimate` y `UnavailableBoat` (RNF-001).
- [ ] **T009** · `EstimateBatchUseCase` + `EstimateBatchService`: lista vacía → resultado vacío **sin** leer parámetros ni invocar UC02; dedupe de IDs; límite de lote defendido en el servicio; lectura del seguro vía `FinancialParametersRepository.findCurrent()` (sin versión vigente o valor nulo → `FINANCIAL_PARAMETERS_NOT_CONFIGURED`); **una** invocación a `ProvideBaseRateUseCase` con `evaluatedDate = ClockPort.today()`; cálculo por embarcación; los identificadores no reconocidos por Flota se omiten del resultado y las embarcaciones sin tarifa base se reportan en `unavailable[]`; frente a `FLEET_UNAVAILABLE`/`DYNAMIC_RATE_NOT_CONFIGURED` **propaga sin devolver nada**.
- [ ] **T010** · Pruebas unitarias del servicio (Mockito): 10 embarcaciones, una sin tarifa, lista vacía, IDs duplicados → una sola invocación, fecha regular sin versión vigente de parámetros, seguro faltante, Flota caída (propagación sin salida parcial).
- [ ] **T011** · `EstimateController` (`POST /api/v1/estimates/batch`) + `BatchEstimateRequest` **estricto** (rechaza cualquier campo distinto de `boat_ids`; p. ej. `@JsonAnySetter` que capture desconocidos y fuerce `VALIDATION_ERROR`) + `BatchEstimateResponse` con claves `snake_case` y `estimated_total` string de 2 decimales; `X-Correlation-Id` (genera si falta).
- [ ] **T012** · Límite de lote: `seashare.estimates.max-batch-size` en `EstimateConfiguration`; `boat_ids.size() > max` → `400 BATCH_SIZE_EXCEEDED` sin procesar ningún elemento; elementos no-UUID o `boat_ids` ausente/no-arreglo → `400 VALIDATION_ERROR`.
- [ ] **T013** · Tests de contrato MockMvc del lote: 200 feliz (ejemplo), lista vacía, `BATCH_SIZE_EXCEEDED`, campos extra, `boat_ids` inválidos y mapeo de `503`.

### Phase 4: US2 — Estimación individual (HU2; RF-001, RF-003, RF-004; CE-003)

- [ ] **T014** · `EstimateSingleCommand` + `SingleEstimateResult` y validación: formato de fecha (Jackson), `start_date` en el pasado (vs. `ClockPort.today()`), `end_date < start_date` → `400 INVALID_DATE_RANGE`; `passengers ≥ 1` → `400 VALIDATION_ERROR`. `start_date == end_date` válido (1 día).
- [ ] **T015** · `EstimateSingleUseCase` + `EstimateSingleService`: días inclusivos; lectura del seguro (E4); invocación de `ProvideBaseRateUseCase` con `evaluatedDate = start_date`; ausencia en el resultado → `422 BASE_RATE_NOT_AVAILABLE` (E6); cálculo con `PricingCalculator`; `warning` obligatorio; `FLEET_UNAVAILABLE` → `503`.
- [ ] **T016** · `EstimateController` (`POST /api/v1/estimates/individual`) + `SingleEstimateRequest`/`SingleEstimateResponse` (eco de fechas, `duration_days`, `passengers`, `estimated_total` 2 dec, `warning` exacto).
- [ ] **T017** · Tests de contrato MockMvc del individual: 200 feliz con `warning` (`1372500.00`), mismo día = 1 día, fin < inicio, inicio en el pasado, pasajeros `0`/negativos, sin tarifa → `422`, y `503`.

### Phase 5: Resiliencia y aceptación (RNF-003; CE-001, CE-002, CE-003, CE-004, CE-005; HU3)

- [ ] **T018** `EstimateResilienceIT`: timeout, `500`, cuerpo inválido y circuito abierto → `503 FLEET_UNAVAILABLE` sin estimaciones parciales; parámetros ausentes → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`.
- [ ] **T019** · **`ce001_precision_financiera_100_casos`** (CE-001): parametrizada con ≥ 100 casos (fechas regular/FE/TA/coincidencia, tarifas, días inclusivos y pasajeros, ambas modalidades) y total exacto a 2 decimales.
- [ ] **T020** · **`ce004_fallas_simuladas_sin_estimacion_parcial`** (CE-004): fallas simuladas de Flota y parámetros ausentes → respuesta controlada completa, sin salida parcial.
- [ ] **T021** · **`ce005_lote_con_atributos_extra_rechazado`** (CE-005): parametrizada con `start_date`/`end_date`/`passengers`/desconocidos → `400 VALIDATION_ERROR`, 0 cálculos.
- [ ] **T022** · HU3: carga ligera de 50 embarcaciones contra stub WireMock de Flota verificando una única consulta y finalización dentro del presupuesto del cliente (timeout 1 s). El SLA < 500 ms es de Flota.
- [ ] **T023** · **`ce002_…`** (CE-002, ArchUnit) + **`ce003_aviso_obligatorio_presente`** (CE-003, texto del `warning` en toda respuesta individual; métrica de negocio no automatizable).

### Phase 6: Polish

- [ ] **T024** · ArchUnit completo (reglas §3.4): `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `port.in`; el controller no accede a repositorios ni al servicio concreto de UC02; DTOs HTTP fuera de `application`.
- [ ] **T025** · Verificar rutas/códigos contra `contracts/README.md` y el contrato de Flota (tipos de error unificados); no editar contratos ni SPEC.
- [ ] **T026** · Compartido: revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-005.
- [ ] **T027** · Costuras: verificar el uso correcto de la firma publicada de `ProvideBaseRateUseCase` (UC02·T016) y de `ClockPort`/`Money`/`BoatId` (UC02·T005/T006); confirmar que UC01 **no expone** puertos a otros casos de uso y que **no escribe** `operational_failure`.

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─┬─> T003 ─> T005 ─> T006 ─┬─> T007 ─┐
      │         │                         │         │
      │         └─────────────────────────┴─────────┴─> T008 ─> T009 ─> T010
      │                                                                        │
      │                                              T011 ─> T012 ─> T013 <────┘
      │
      ├─────────────────────────────────────────────> T014 ─> T015 ─> T016 ─> T017
      │
      └─────────────────────────────────────────────> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027
```

- **Grupo 1 (T001–T007)**: Setup y Foundational propios + consumo de puertos y VOs compartidos.
- **US1 (T008–T013)** → **US2 (T014–T017)**: el individual reutiliza `PricingCalculator` (T005) y el `GlobalExceptionHandler` (T007); pueden desarrollarse en paralelo por archivos distintos (el controller es compartido: coordinar T011/T016 en el mismo archivo).
- **Resiliencia y aceptación (T018–T023)**: después de ambas modalidades; T018/T022 dependen de los artefactos de UC02/UC11.
- **Polish (T024–T027)**: al final.
- **Riesgo de secuencia**: escala/redondeo (D-UC01-07) e identificadores no reconocidos por Flota (D-UC01-05) quedan resueltos y no bloquean el orden; seguridad fuera de alcance (general-plan §7.1).

## Notes

- El SPEC 01 **no** define meta de rendimiento propia, zona horaria, máximo de lote ni rango de `passengers`; se fijan, respectivamente, por el timeout de 1 s del cliente de Flota, `America/Bogota` (`seashare.timezone`, general-plan D-12), el máximo de **50** (general-plan D-22) y la decisión D-UC01-06 (entero ≥ 1, sin máximo).
- **Seguridad fuera de alcance**: el sistema **no implementa autenticación ni autorización** (general-plan §7.1, D-21); los contratos de UC01 ya no declaran `401`/`403` y este plan no incluye `SecurityConfig` ni pruebas de seguridad. "Solo el Sistema de Reservas y Operaciones" es el usuario esperado.
- En el lote, un `boat_id` no reconocido por Flota se **omite** del resultado y una embarcación reconocida sin tarifa base se reporta en `unavailable[]` (D-UC01-05); nunca se inventa un precio.
- `ProvideBaseRateUseCase`, `ClockPort`, `Money`, `BoatId` y `BaseRateException` son de **UC02** (plan ya redactado): UC01 los consume por nombre, no los implementa ni los prueba.
- `FinancialParametersRepository` y la tabla `financial_parameters` son de **UC11**: UC01 los consume por nombre (versión vigente, seguro por pasajero). UC01 **no** persiste nada ni define migraciones.
- `FailureRecorderPort`/`operational_failure` **no** se usan en UC01 (D-UC01-04); UC02 registra los fallos de Flota.
- El ejemplo del contrato de lote es ilustrativo; los ejemplos de este plan usan `2026-10-07` (miércoles) como fecha de evaluación (D-UC01-02).
- Etiquetas usadas: `[SPEC]` (SPEC 01 y contratos), `[CONV]` (general-plan e inferido).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Contratos, Estrategia de testing, fases con `T0NN`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-09` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]` o `[CONV]`.
- [ ] Discrepancias `D-UC01-01` a `D-UC01-09` con decisión explícita, sin resolución silenciosa.
- [ ] Cada RF/RNF/CE/HU del SPEC 01 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce005_…` (CE-003 con nota de no automatizable).
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§11) aplicadas.
- [ ] UC01 sin tablas ni migraciones propias (migración inicial única, general-plan D-31), sin persistencia; solo lee la versión vigente de `financial_parameters` por nombre.
- [ ] «Firmas referenciadas por otros planes» declarado (UC01 no expone ninguna) y referencias por nombre a UC02/UC11.
- [ ] Seguridad fuera de alcance documentada (general-plan §7.1); sin `SecurityConfig` ni pruebas 401/403.
