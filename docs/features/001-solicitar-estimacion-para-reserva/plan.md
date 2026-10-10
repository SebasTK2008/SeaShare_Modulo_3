# Implementation Plan: UC01 - Solicitar Estimación para Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC01 es el caso de uso **síncrono** por el que el Sistema de Reservas y Operaciones obtiene, ya calculadas, las estimaciones de precio de las embarcaciones, en dos modalidades: **lote** (HU1) y **individual** (HU2). En lote Reservas envía **únicamente** la lista de identificadores (`boat_ids`) y el sistema fija siempre 1 día, 1 pasajero y la **fecha actual** como fecha de evaluación; en individual Reservas envía `boat_id`, `start_date`, `end_date` y `passengers` y el sistema devuelve el total más una **advertencia obligatoria** que Reservas solo renderiza [SPEC RF-001, RF-002, RF-003, RF-004, RF-007, HU1, HU2]. Reservas no hace ninguna operación de precio (CE-002): toda la matemática reside en el sistema.

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. UC01 **no calcula la tarifa dinámica**: delega en UC02 vía `ProvideBaseRateUseCase` (firma publicada en el plan de UC02), y para el **seguro por pasajero** lee `financial_parameters` a través de `FinancialParametersRepository` (puerto de salida de UC11, bloque B). Aplica la fórmula con un `PricingCalculator` de dominio puro [SPEC RNF-002; general-plan §3.3] y responde por dos adaptadores REST `web` [SPEC RF-004; general-plan §3.5]. Los errores siguen *Problem Details* (RFC 9457) y el catálogo de `contracts/README.md` §3.4, con el orden de decisión E1→E2→E3→E4→E6→E7 (lote) y E1→…→E4→E6→E7→E8 (individual). UC01 **no persiste nada y no tiene migración Flyway propia**.

UC01 es el **orden 2 del bloque A** de la hoja de ruta: depende del plan de UC02 (mismo bloque, ya escrito) para `ProvideBaseRateUseCase`, `ClockPort` y los value objects `Money`/`BoatId`, y del plan de UC11 (bloque B) para `FinancialParametersRepository` [guía §3.1, §3.2; general-plan §12].

**Decisión sobre embarcaciones sin tarifa (resuelve la ambigüedad SPEC 01 vs. puerto de UC02)**: dado que el contrato de Flota trata "sin tarifa" y "no existe" de forma equivalente y el puerto publicado de UC02 solo devuelve embarcaciones **con tarifa**, UC01 no puede distinguir "reconocida sin tarifa" de "inexistente". Se adopta la **alternativa A**: toda embarcación solicitada que no aparezca en el resultado de UC02 se reporta en `unavailable[]` con `reason = SIN_TARIFA_BASE`, sin inventar precio. Esta es una desviación registrada del texto "omite inexistentes" del SPEC 01 (ver **D-UC01-06**); se descartó modificar el puerto de UC02 porque exigiría endurecer el contrato externo con Flota y no es un cambio "solo de plan".

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
| **RNF-003** manejo de errores (*timeouts*, *fallbacks*) y sin valores parciales | `EstimateExceptionHandler`, Resilience4j en el cliente de Flota (de UC02) | T007, T018 | T020 |
| **CE-001** precisión financiera en 100 pruebas | `PricingCalculator` + `EstimateAcceptanceTest` | T005, T019 | **T019 `ce001_…`** |
| **CE-002** 0 lógica de precio en Reservas (arquitectura) | ArchUnit + `EstimateController` (solo mapea) | T023, T024 | **T023 `ce002_…`** |
| **CE-003** negocio / transparencia (<2% tickets) | `warning` obligatorio de HU2 (métrica de negocio, no automatizable) | T005, T016 | **T023 `ce003_…`** |
| **CE-004** resiliencia: fallas simuladas → fallback elegante | `EstimateExceptionHandler` + `EstimateResilienceIT` | T018, T020 | **T020 `ce004_…`** |
| **CE-005** 100% de lotes con atributos extra rechazados sin cálculo | `BatchEstimateRequest` estricto | T011 | **T021 `ce005_…`** |
| **HU1** lote (P1) | Fase 3 | T008–T013 | T010, T013, T019 |
| **HU2** individual + aviso (P1) | Fase 4 | T014–T017 | T017 |
| **HU3** tarifa base de Flota (P2, sistema externo) | Consulta a Flota vía UC02; carga ligera | T018, T022 | T022 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC01: Spring Web MVC, Validation, Jackson, MapStruct (sin uso obligatorio), ArchUnit y WireMock (integración con Flota). **Ninguna dependencia nueva fuera de las ya previstas en `UC11·T001`** (que incluye web, validation, test, security-test, ArchUnit) más `resilience4j-*` y `wiremock` que amplía `UC02·T019`; el `pom.xml` se edita solo en la fase Setup Compartida, sin cambios en silencio [guía §6]
**Storage**: PostgreSQL 16+; UC01 **no tiene tablas propias ni migración**. Solo **lee** `financial_parameters` (tabla de UC11, dueño B) para la tarifa de seguro por pasajero [SPEC general-plan §4; guía §3.1]
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, WireMock, ArchUnit, Testcontainers (para el contexto Spring de integración) [CONV]
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: El SPEC 01 no define metas propias; HU3 fija el SLA de **Flota** (< 500 ms para 50 embarcaciones, SPEC 01 HU3 / contrato Flota). Para UC01 rige el timeout de lectura de 1 s del cliente de Flota [SPEC general-plan §7.2; OQ-UC01-02]
**Constraints**: `BigDecimal` con precisión interna de 4 decimales y redondeo final a 2 decimales en la respuesta [SPEC RNF-002; D-UC01-08]; lote definido **solo** por `boat_ids`, sin fechas ni pasajeros [SPEC RF-007]; nunca estimaciones parciales ni valores asumidos ante fallo de Flota o parámetros faltantes [SPEC casos extremos, RNF-003]; operación de solo lectura, idempotente, sin efectos secundarios
**Scale/Scope**: 2 endpoints REST (`POST /api/v1/estimates/batch`, `POST /api/v1/estimates/individual`) [contratos UC01]; 7 RF + 3 RNF + 5 CE + 3 HU del SPEC 01; 1 puerto de entrada por modalidad; 1 puerto consumido (UC02) + 1 repositorio consumido (UC11)

**Estado actual del repositorio (relevante para UC01)**: existen `SeashareM3Application.java`, `application.properties` (solo `spring.application.name=seashare-m3`), `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** los paquetes `domain`/`application`/`infrastructure`, ni `Money`/`BoatId`/`ClockPort`/`PricingCalculator`, ni los puertos de UC02/UC11. Los planes de UC02 (bloque A) y UC11 (bloque B) ya están redactados; UC01 consume sus piezas por nombre.

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

Contratos/piezas de otros planes (solo referencia de costura; **no se re-planifican**): `external/flota-consulta-tarifas-base.md` (conexión única de Flota, vía UC02), plan de UC02 (`ProvideBaseRateUseCase`, `ClockPort`, `Money`, `BoatId`, `BaseRateException`) y plan de UC11 (`FinancialParametersRepository`, `ProblemDetailsConfig`).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── valueobject/
│   │   ├── Money.java                               # de UC02 (T006) — consumido por nombre [D-UC01-04]
│   │   └── BoatId.java                              # de UC02 (T006) — consumido por nombre [D-UC01-04]
│   ├── service/
│   │   └── PricingCalculator.java                   # T005 [SPEC RF-002/RF-003, RNF-002; general-plan §3.3]
│   └── exception/
│       ├── DomainException.java                     # T003 [Compartido UC11·T004]
│       └── BaseRateException.java                   # de UC02 (T014) — consumido por nombre
├── application/
│   ├── port/in/
│   │   ├── EstimateBatchUseCase.java                # T009 [general-plan §3.5]
│   │   ├── EstimateSingleUseCase.java               # T015 [general-plan §3.5]
│   │   └── ProvideBaseRateUseCase.java              # de UC02 (T016) — consumido por nombre [guía §3.2]
│   ├── port/out/
│   │   ├── FinancialParametersRepository.java       # de UC11 (T007/T013) — consumido por nombre [guía §3.1]
│   │   └── ClockPort.java                           # de UC02 (T005) — consumido por nombre (intra-bloque A)
│   ├── service/
│   │   ├── EstimateBatchService.java                # T009 [general-plan §3.5]
│   │   └── EstimateSingleService.java               # T015 [general-plan §3.5]
│   └── dto/
│       ├── EstimateBatchCommand.java                # T008   # List<BoatId> [SPEC RNF-001; nombre [CONV]]
│       ├── BatchEstimateResult.java                 # T008   # evaluationDate, durationDays, passengers, estimates, unavailable
│       ├── BoatEstimate.java                        # T008   # boatId + estimatedTotal
│       ├── UnavailableBoat.java                     # T008   # boatId + reason (SIN_TARIFA_BASE)
│       ├── EstimateSingleCommand.java               # T014   # boatId, startDate, endDate, passengers
│       ├── SingleEstimateResult.java                # T014   # boatId, start/end, durationDays, passengers, estimatedTotal, warning
│       └── EstimateWarning.java                     # T005   # texto literal obligatorio de HU2 [SPEC]
└── infrastructure/
    └── adapter/in/web/
        ├── EstimateController.java                  # T011 (batch), T016 (individual) [CONV]
        ├── EstimateExceptionHandler.java            # T007   # @RestControllerAdvice → códigos del catálogo [CONV]
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

**Puertos expuestos**: UC01 **no expone ningún puerto que otro bloque llame**. Sus puertos de entrada (`EstimateBatchUseCase`, `EstimateSingleUseCase`) son alcanzados únicamente por sus adaptadores REST y no se invocan entre casos de uso [general-plan §3.4]. UC01 **consume** por nombre: `ProvideBaseRateUseCase` (port.in de UC02, firma publicada en `UC02·T016`), `ClockPort` (UC02·T005, intra-bloque A), `Money`/`BoatId` (UC02·T006) y `FinancialParametersRepository` (UC11·T007/T013). El consumo del seguro sigue la firma publicada por UC11: `Optional<FinancialParameters> find()`.

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) en un solo módulo Maven, con subpaquetes temáticos sin reglas entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas del §3.4 aplicadas aquí: `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `application.port.in`; el controller nunca accede a un repositorio ni a UC02 concretamente (solo a `port.in`); los DTOs HTTP viven en `infrastructure.adapter.in` y `application` trabaja con `command`/`result` (RNF-001). Identificadores en inglés (D-23) y lenguaje ubicuo §13; `SolicitudEstimacionLote`/`EmbarcacionInfo` del SPEC no están en §13 → `BatchEstimateRequest` (infraestructura) y `EstimateBatchCommand`/`BoatEstimate` (aplicación) **[CONV]**.

## Reglas de negocio

Todas las reglas provienen del SPEC 01 (`spec.md`), sus contratos o el `general-plan.md`, salvo indicación. Ejemplos con tarifa base final (ya ajustada por UC02) de `350000.00` y seguro `15000.00` por pasajero.

1. **Dos modalidades, un caso de uso** [SPEC RF-004; general-plan §3.5]: lote e individual comparten la fórmula y la delegación de la tarifa dinámica en UC02; el SPEC habla de "un *endpoint*", los contratos fijan **dos rutas** `[CONV]` (OQ-02), resuelto en **D-UC01-01**.
2. **Supuestos fijos del lote** [SPEC RF-002, HU1]: duración **1 día**, **1 pasajero** y **fecha actual** como fecha de evaluación; el sistema los fija, no son parámetros de la petición. `evaluation_date` se obtiene con `ClockPort.today()` en la zona de negocio (general-plan D-12/OQ-04) y la respuesta devuelve `duration_days = 1` y `passengers = 1`.
3. **Días inclusivos (individual)** [SPEC RF-003, casos extremos]: `días = end_date − start_date + 1`; `start_date == end_date` es válido y equivale a 1 día; solo `end_date < start_date` es inválido.
4. **Fórmula** [SPEC RF-002, RF-003]: `estimated_total = (tarifaBaseFinal × días) + (seguroPorPasajero × pasajeros)`. La tarifa base final ya incluye la tarifa dinámica (UC02). La estimación **no incluye** el depósito de garantía ni penalidades ni ajustes posteriores. Ejemplo lote (regular, 1 día, 1 pasajero): `350000.00 + 15000.00 = 365000.00`. Ejemplo lote (sábado, FE 10 %): `385000.00 + 15000.00 = 400000.00`. Ejemplo individual (20–22-dic-2026, 3 días, 4 pasajeros, TA 25 %): `(437500.00 × 3) + (15000.00 × 4) = 1372500.00`.
5. **Tarifa dinámica de la fecha correcta** [SPEC RF-002, RF-003]: lote → fecha actual; individual → `start_date` (esa tarifa se usa para toda la duración). UC01 **no** calcula tarifa dinámica: pasa `evaluatedDate` a `ProvideBaseRateUseCase`.
6. **Deduplicación del lote** [CONV, D-UC01-09]: si `boat_ids` trae identificadores repetidos, se consulta y se estima **una vez** por embarcación; la respuesta contiene una entrada por embarcación única.
7. **Lista vacía** [SPEC casos extremos; contrato lote regla 1]: `boat_ids: []` → `200` con `estimates: []` y `unavailable: []`, **sin leer parámetros ni invocar a UC02**.
8. **Límite del lote** [SPEC RF-006; general-plan D-22]: máximo `seashare.estimates.max-batch-size` (por defecto **50**). Superarlo → `400 BATCH_SIZE_EXCEEDED` **sin procesar ningún elemento**. El límite es un supuesto configurable del sistema, no un valor que Reservas pueda ampliar por petición.
9. **Campos no aceptados en el lote** [SPEC RF-007; contrato lote regla 11; CE-005]: cualquier campo distinto de `boat_ids` (`start_date`, `end_date`, `passengers` u otros desconocidos) → `400 VALIDATION_ERROR` sin ejecutar cálculo.
10. **Validación de fechas (individual)** [SPEC casos extremos; contrato individual reglas 1–2]: formato inválido, `start_date` en el pasado (comparado con `ClockPort.today()` en la zona de negocio) o `end_date` anterior a `start_date` → `400 INVALID_DATE_RANGE`, sin cálculo. Esta validación **no** aplica al lote (no recibe fechas).
11. **Validación de pasajeros (individual)** [SPEC HU2; D-UC01-07]: entero **≥ 1**, **sin máximo** (la capacidad máxima no es un insumo de UC01). Valor no entero, `null`, `0` o negativo → `400 VALIDATION_ERROR`.
12. **Embarcación sin tarifa disponible** [SPEC casos extremos; contrato lote regla 5; D-UC01-06]: individual → `422 BASE_RATE_NOT_AVAILABLE`; lote → la embarcación se reporta en `unavailable[]` con `reason = SIN_TARIFA_BASE` y el resto del lote se devuelve correctamente. Nunca se asume un precio.
13. **Falla de Flota** [SPEC casos extremos, RNF-003; contrato lote regla 9]: `ProvideBaseRateUseCase` lanza `BaseRateException(FLEET_UNAVAILABLE)` (caída, timeout, inalcanzable o respuesta inválida) → UC01 responde `503 FLEET_UNAVAILABLE` **sin estimaciones parciales**. UC02 ya registra el fallo en `operational_failure`; **UC01 no escribe** en `operational_failure` por ser un caso síncrono que responde (D-UC01-05, confirmado).
14. **Parámetros faltantes** [SPEC casos extremos; contrato lote regla 10]: si falta la tarifa de seguro (fila ausente o valor nulo) o el porcentaje que exige la fecha evaluada (`BaseRateException(DYNAMIC_RATE_NOT_CONFIGURED)` de UC02) → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, sin estimación parcial. Si la fecha evaluada es regular, no se necesita ningún porcentaje.
15. **Orden de validación** [CONV; contratos §5 y `README` §4.3]: lote `E1 → E2 → E3 → E4 → E6 → E7`; individual `E1 → E2 → E3 → E4 → E6 → E7 → E8`. La petición se valida por completo (E4) antes de leer parámetros (E6) o llamar a Flota (E7); E5 no aplica (no hay recurso en la URL) y, en el lote, E8 no produce error (se reporta en `unavailable[]`).
16. **Precisión y redondeo** [SPEC RNF-002; D-UC02-04; D-UC01-08]: todos los montos son `BigDecimal`; el cálculo interno trabaja a **escala 4**; el campo `estimated_total` de la respuesta se serializa como **string decimal redondeado HALF_UP a 2 decimales**. UC01, como llamador, realiza el redondeo final de presentación.
17. **Advertencia obligatoria (individual)** [SPEC HU2 escenario 1]: toda respuesta exitosa incluye `warning` con el texto exacto (sin punto final): `Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva`. Es la evidencia funcional de CE-003 (métrica de negocio no automatizable, D-UC01-10).
18. **Seguridad** [PEND OQ-01; OQ-UC01-01]: los contratos declaran `401 UNAUTHENTICATED` / `403 FORBIDDEN` y que solo el Sistema de Reservas y Operaciones puede llamar. El **mecanismo y la configuración** quedan **diferidos** a la revisión conjunta de los bloques: esta fase **no implementa** `SecurityConfig` ni pruebas de 401/403 para no introducir incoherencias con UC11 (ver Notes).
19. **Sin persistencia ni migración** [SPEC; general-plan §3.3]: UC01 no crea tablas ni migraciones Flyway; es de solo lectura sobre `financial_parameters` (UC11). Repetir la petición no produce efectos (idempotente) [contrato §6].
20. **Identificadores inexistentes** [SPEC casos extremos; D-UC01-06]: se tratan igual que "sin tarifa disponible" por indisponibilidad del dato; quedan en `unavailable[]` (alternativa A). Registrado como desviación explícita.

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
    { "boat_id": "9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02", "reason": "SIN_TARIFA_BASE" }
  ]
}
```

Lista vacía → `{ "evaluation_date": "...", "duration_days": 1, "passengers": 1, "estimates": [], "unavailable": [] }`.

| HTTP | `code` | Cuándo | `retryable` |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo no JSON, `boat_ids` ausente/no arreglo/elemento no UUID, o cualquier campo distinto de `boat_ids` | No |
| 400 | `BATCH_SIZE_EXCEEDED` | `boat_ids` supera el máximo | No |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida [PEND OQ-01] | No |
| 403 | `FORBIDDEN` | El llamador no es Reservas [PEND OQ-01] | No |
| 503 | `FLEET_UNAVAILABLE` | Flota caída, timeout, inalcanzable o respuesta inválida [E7] | Sí |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Falta el seguro o el porcentaje exigido por la fecha [E6] | Sí |
| 500 | `INTERNAL_ERROR` | Error no previsto [E10] | Sí |

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
| 400 | `INVALID_DATE_RANGE` | Formato de fecha inválido, inicio en el pasado o fin anterior al inicio [E4] | No |
| 400 | `VALIDATION_ERROR` | Cuerpo no JSON, o `boat_id`/`passengers` ausentes o con tipo/rango inválido [E4] | No |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida [PEND OQ-01] | No |
| 403 | `FORBIDDEN` | El llamador no es Reservas [PEND OQ-01] | No |
| 422 | `BASE_RATE_NOT_AVAILABLE` | Embarcación sin tarifa base o no reconocida por Flota [E8] | No |
| 503 | `FLEET_UNAVAILABLE` | Flota caída, timeout, inalcanzable o respuesta inválida [E7] | Sí |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Falta el seguro o el porcentaje exigido por el inicio [E6] | Sí |
| 500 | `INTERNAL_ERROR` | Error no previsto [E10] | Sí |

**Mapeo de excepciones** (implementado por `EstimateExceptionHandler`, T007):

| Origen | `reason` | HTTP / `code` |
|---|---|---|
| `BaseRateException` de UC02 | `FLEET_UNAVAILABLE` | 503 `FLEET_UNAVAILABLE` |
| `BaseRateException` de UC02 | `DYNAMIC_RATE_NOT_CONFIGURED` | 503 `FINANCIAL_PARAMETERS_NOT_CONFIGURED` |
| Ausencia en el resultado de UC02 | — | lote: `unavailable[]`; individual: 422 `BASE_RATE_NOT_AVAILABLE` |
| `FinancialParametersRepository.find()` vacío o seguro nulo | — | 503 `FINANCIAL_PARAMETERS_NOT_CONFIGURED` |
| Validación de petición | — | 400 (`VALIDATION_ERROR` / `BATCH_SIZE_EXCEEDED` / `INVALID_DATE_RANGE`) |

Todos los errores usan *Problem Details* (RFC 9457, `contracts/README.md` §3.3) con `code` y `retryable`. Headers comunes: `Authorization`, `Content-Type`, `Accept`, `X-Correlation-Id` (opcional; se genera si falta y se devuelve) [README §3.2].

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

Además, **carga ligera HU3** (T022): batch de 50 embarcaciones contra un stub WireMock de Flota, verificando una **única** consulta a Flota y finalización dentro del presupuesto del cliente (timeout 1 s). El SLA real (< 500 ms) es compromiso de Flota [SPEC HU3; OQ-UC01-02].

## Discrepancias y puntos abiertos

Registro de contradicciones detectadas al elaborar este plan. **No se resuelven en silencio**: cada una indica la decisión tomada para poder avanzar y lo que requiere confirmación. Los IDs `D-UC01-xx` son propios de este plan; los `D-xx` sin prefijo son decisiones del plan general (§11).

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC01-01** | El SPEC RF-004 habla de "un *endpoint*" para ambas modalidades; los contratos definen dos rutas | SPEC 01 RF-004 vs contratos UC01 (lote/individual) | Rigen los contratos: dos endpoints [CONV] (OQ-02); los montos y reglas son idénticos salvo fechas/pasajeros y el `warning` | Decidida |
| **D-UC01-02** | El ejemplo del contrato de lote usa `evaluation_date: 2026-10-03`, que **es sábado**, y muestra `350000.00` sin ajuste de fin de semana | `UC01-estimacion-lote.md` §4 vs calendario (SPEC 02 RF-006) | El ejemplo es ilustrativo e inconsistente; en este plan se usa `2026-10-07` (miércoles). T025 **propone corregir el ejemplo del contrato** (requiere acuerdo; no se edita en silencio) | Abierta (corrección propuesta) |
| **D-UC01-03** | El contrato de lote regla 3 dice "invoca UC02 **por cada embarcación**", pero el diseño publicado de UC02 es *batch-only* (una consulta a Flota por invocación) | `UC01-estimacion-lote.md` §3 regla 3 vs plan UC02 (T016, regla 9) | UC01 invoca `ProvideBaseRateUseCase` **una sola vez** con la lista completa y deduplicada; la redacción "por cada embarcación" del contrato queda superada por el diseño de UC02 | Decidida |
| **D-UC01-04** | `Money`, `BoatId`, `ClockPort` y `FleetRatePort` se comparten intra-bloque A pero no figuran en la guía §3.1 | general-plan §3.3/§3.5 vs guía §3.1 | Hereda **D-UC02-05**: se propone añadirlos a §3.1; UC01 los referencia por nombre en tanto se edite | Abierta (heredada) |
| **D-UC01-05** | La guía §3.1 asigna `FailureRecorderPort`/`operational_failure` a `UC11·T001`–`UC11·T009`, pero el plan de UC11 no las contiene | guía §3.1 vs plan UC11 | UC01 **no usa** `FailureRecorderPort` (es síncrono y responde; UC02 registra los fallos de Flota). Hereda **D-UC02-11** para los bloques que sí lo necesitan | Decidida para UC01 |
| **D-UC01-06** | El SPEC 01 pide "omitir inexistentes" y "marcar sin tarifa", pero el puerto de UC02 y el contrato de Flota tratan ambos casos igual | SPEC 01 casos extremos vs plan UC02 (T016) y `flota-consulta-tarifas-base.md` §3 regla 3 | **Alternativa A**: toda embarcación solicitada ausente del resultado de UC02 se reporta en `unavailable[]` con `SIN_TARIFA_BASE`; se registra la desviación de "omitir inexistentes". Se descartó la alternativa B (modificar el puerto de UC02) porque exigiría además endurecer el contrato externo con Flota | Decidida (desviación registrada) |
| **D-UC01-07** | El SPEC 01 no fija rango para `passengers` | SPEC 01 HU2 vs contrato individual (rango `[PEND]`) | Entero **≥ 1, sin máximo** (la capacidad no es insumo de UC01); violación → `400 VALIDATION_ERROR` | Decidida (confirmada) |
| **D-UC01-08** | El SPEC RNF-002 habla de 4 decimales internos "antes de cualquier redondeo final" sin fijar el redondeo de salida | SPEC 01 RNF-002 vs ejemplos de los contratos (`"365000.00"`) | Cálculo interno a escala 4; `estimated_total` **HALF_UP a 2 decimales** (string) en la respuesta | Decidida (confirmada) |
| **D-UC01-09** | El `general-plan` §3.3 nombra `PricingCalculator` pero §3.5 no lo asigna a ningún UC | general-plan §3.3 vs §3.5 | UC01 asume `PricingCalculator` (es la única UC con la fórmula de estimación; UC04 usa `ReservationValueCalculator`) | Decidida |
| **D-UC01-10** | CE-003 (tickets <2 %) es una métrica de negocio no verificable por el sistema | SPEC 01 CE-003 | Se traza al `warning` obligatorio de HU2 y queda como criterio de negocio verificado fuera del sistema | Decidida (confirmada) |

## Preguntas abiertas (OQ-UC01-xx)

Preguntas que este plan no puede resolver con el SPEC 01, el plan general ni los contratos. Hasta que se confirmen rige la propuesta por defecto.

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC01-01** | ¿Qué mecanismo de autenticación/autorización usa el sistema y quién es dueño del `SecurityConfig` del servicio? El SPEC fija *quién* llama (solo Reservas) pero no *cómo*; es un asunto transversal a todos los bloques | Reglas 401/403 de ambos contratos; pruebas de seguridad | **Diferido** a la revisión conjunta de los bloques; en esta fase no se implementa `SecurityConfig` ni pruebas 401/403 (general-plan §7.1, OQ-01). Evita incoherencias con UC11 | Abierta (diferida) |
| **OQ-UC01-02** | ¿Existen metas de rendimiento propias de UC01? El SPEC 01 solo fija el SLA de Flota (HU3) | Technical Context; T022 | Sin meta propia; se hereda el timeout de 1 s del cliente de Flota y el SLA < 500 ms/50 embarcaciones es de Flota **[CONV]** | Abierta |
| **OQ-UC01-03** | ¿Cuál es la zona horaria de negocio para "fecha actual" y "fecha pasada"? (general-plan OQ-04) | T009, T014; reglas 2, 10 | `America/Bogota` en `seashare.timezone` **[CONV]** | Abierta (general) |
| **OQ-UC01-04** | ¿El máximo de lote definitivo es 50 o 100? El SPEC RF-006 deja "50 o 100" | T012 | **50** (general-plan D-22; coincide con la prueba de carga de Flota) | Abierta (general) |
| **OQ-UC01-05** | ¿Se envía `Retry-After` en los `503` y se documentan reintentos del cliente Reservas? | T007 | `Retry-After` opcional (README §3.2); `retryable: true` en `503` | Abierta |

**`[NEEDS CLARIFICATION]` consolidado:** OQ-UC01-01 a OQ-UC01-05.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). **«Compartido»** = misma tarea que la fase de la hoja de ruta general (`general-plan.md` §12); las fases Setup y Foundational remiten a `UC11·T001`–`UC11·T009` (guía §3.4). Los bloques vecinos referencian estas tareas como `UC11·T0NN`.

### Phase 1: Setup — **Compartido** (con la Fase 1 general; se referencia como `UC11·T001`–`UC11·T003`)

- [ ] **T001** · Compartido: `pom.xml` — remite a `UC11·T001`. UC01 **no requiere artefactos nuevos** (usa web, validation, Jackson, test y, para T018/T022, los `wiremock`/`resilience4j` que ya amplía `UC02·T019`); se coordina en el mismo archivo sin duplicar ediciones [guía §6]. · M: `none` · P: `pending`
- [ ] **T002** · Compartido: `application.properties` (`seashare.estimates.max-batch-size=50`, `seashare.timezone`) y base de `ArchitectureTest` (reglas §3.4) — remite a `UC11·T003`. · M: `none` · P: `pending`

### Phase 2: Foundational — **Compartido** (con la Fase 2 general; se referencia como `UC11·T004`–`UC11·T009`)

- [ ] **T003** · Compartido: `DomainException` (`domain/exception`), base del árbol de excepciones — remite a `UC11·T004`. · M: `none` · P: `pending`
- [ ] **T004** · Consumir por nombre las piezas ya definidas en otros planes: `ProvideBaseRateUseCase`/`ProvideBaseRateCommand`/`BaseRateResult` (UC02·T016), `ClockPort` (UC02·T005), `Money`/`BoatId` (UC02·T006), `BaseRateException` (UC02·T014) y `FinancialParametersRepository` (UC11·T007/T013, con `Optional<FinancialParameters> find()`). UC01 **no** las implementa ni las prueba. Notas D-UC01-04 y D-UC01-05. · M: `none` · P: `pending`
- [ ] **T005** · `PricingCalculator` (`domain/service`): `Money estimate(Money finalBaseRatePerDay, int days, Money insurancePerPassenger, int passengers)` con `BigDecimal` a escala 4 y **sin redondeo final** (RNF-002, D-UC02-04); y constante `EstimateWarning.TEXT` con el texto literal de HU2 [SPEC]. · M: `none` · P: `pending`
- [ ] **T006** · Pruebas unitarias de `PricingCalculator`: días inclusivos, distintos pasajeros, escala 4, casos borde (1 día / 1 pasajero) y montos con 4 decimales (RNF-002). · M: `none` · P: `pending`
- [ ] **T007** · `EstimateExceptionHandler` (`@RestControllerAdvice`): mapea `BaseRateException` (según `reason`), parámetros faltantes (`find()` vacío / seguro nulo) y errores de validación a `Problem Details` con los `code` del catálogo (`README` §3.4); reutiliza `ProblemDetailsConfig` (UC11·T008). **Sin reglas de seguridad** (diferidas, OQ-UC01-01). · M: `none` · P: `pending`

### Phase 3: US1 — Estimación en lote (HU1; RF-001, RF-002, RF-004, RF-005, RF-006, RF-007; CE-001, CE-005)

- [ ] **T008** · DTOs de aplicación: `EstimateBatchCommand` (`List<BoatId>`), `BatchEstimateResult` (`evaluationDate`, `durationDays`, `passengers`, `estimates`, `unavailable`), `BoatEstimate` y `UnavailableBoat` (RNF-001). · M: `none` · P: `pending`
- [ ] **T009** · `EstimateBatchUseCase` + `EstimateBatchService`: lista vacía → resultado vacío **sin** leer parámetros ni invocar UC02; dedupe de IDs; límite de lote defendido en el servicio; lectura del seguro vía `FinancialParametersRepository.find()` (ausente/nulo → `FINANCIAL_PARAMETERS_NOT_CONFIGURED`); **una** invocación a `ProvideBaseRateUseCase` con `evaluatedDate = ClockPort.today()`; cálculo por embarcación; ausencias → `unavailable[]` (alternativa A); frente a `FLEET_UNAVAILABLE`/`DYNAMIC_RATE_NOT_CONFIGURED` **propaga sin devolver nada**. · M: `none` · P: `pending`
- [ ] **T010** · Pruebas unitarias del servicio (Mockito): 10 embarcaciones, una sin tarifa, lista vacía, IDs duplicados → una sola invocación, fecha regular sin fila de parámetros, seguro faltante, Flota caída (propagación sin salida parcial). · M: `none` · P: `pending`
- [ ] **T011** · `EstimateController` (`POST /api/v1/estimates/batch`) + `BatchEstimateRequest` **estricto** (rechaza cualquier campo distinto de `boat_ids`; p. ej. `@JsonAnySetter` que capture desconocidos y fuerce `VALIDATION_ERROR`) + `BatchEstimateResponse` con claves `snake_case` y `estimated_total` string de 2 decimales; `X-Correlation-Id` (genera si falta). · M: `none` · P: `pending`
- [ ] **T012** · Límite de lote: `seashare.estimates.max-batch-size` en `EstimateConfiguration`; `boat_ids.size() > max` → `400 BATCH_SIZE_EXCEEDED` sin procesar ningún elemento; elementos no-UUID o `boat_ids` ausente/no-arreglo → `400 VALIDATION_ERROR`. · M: `none` · P: `pending`
- [ ] **T013** · Tests de contrato MockMvc del lote: 200 feliz (ejemplo), lista vacía, `BATCH_SIZE_EXCEEDED`, campos extra, `boat_ids` inválidos y mapeo de `503`. · M: `none` · P: `pending`

### Phase 4: US2 — Estimación individual (HU2; RF-001, RF-003, RF-004; CE-003)

- [ ] **T014** · `EstimateSingleCommand` + `SingleEstimateResult` y validación: formato de fecha (Jackson), `start_date` en el pasado (vs. `ClockPort.today()`), `end_date < start_date` → `400 INVALID_DATE_RANGE`; `passengers ≥ 1` → `400 VALIDATION_ERROR`. `start_date == end_date` válido (1 día). · M: `none` · P: `pending`
- [ ] **T015** · `EstimateSingleUseCase` + `EstimateSingleService`: días inclusivos; lectura del seguro (E6); invocación de `ProvideBaseRateUseCase` con `evaluatedDate = start_date`; ausencia en el resultado → `422 BASE_RATE_NOT_AVAILABLE` (E8); cálculo con `PricingCalculator`; `warning` obligatorio; `FLEET_UNAVAILABLE` → `503`. · M: `none` · P: `pending`
- [ ] **T016** · `EstimateController` (`POST /api/v1/estimates/individual`) + `SingleEstimateRequest`/`SingleEstimateResponse` (eco de fechas, `duration_days`, `passengers`, `estimated_total` 2 dec, `warning` exacto). · M: `none` · P: `pending`
- [ ] **T017** · Tests de contrato MockMvc del individual: 200 feliz con `warning` (`1372500.00`), mismo día = 1 día, fin < inicio, inicio en el pasado, pasajeros `0`/negativos, sin tarifa → `422`, y `503`. · M: `none` · P: `pending`

### Phase 5: Resiliencia y aceptación (RNF-003; CE-001, CE-002, CE-003, CE-004, CE-005; HU3)

- [ ] **T018** · `EstimateResilienceIT` (integración + WireMock sobre Flota, a través del adaptador de UC02): timeout, `500`, cuerpo inválido y circuito abierto → `503 FLEET_UNAVAILABLE` **sin estimaciones parciales**; parámetros ausentes → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`. Depende de los artefactos de `UC11·T001`/`UC02·T019`. · M: `none` · P: `pending`
- [ ] **T019** · **`ce001_precision_financiera_100_casos`** (CE-001): parametrizada con ≥ 100 casos (fechas regular/FE/TA/coincidencia, tarifas, días inclusivos y pasajeros, ambas modalidades) y total exacto a 2 decimales. · M: `none` · P: `pending`
- [ ] **T020** · **`ce004_fallas_simuladas_sin_estimacion_parcial`** (CE-004): fallas simuladas de Flota y parámetros ausentes → respuesta controlada completa, sin salida parcial. · M: `none` · P: `pending`
- [ ] **T021** · **`ce005_lote_con_atributos_extra_rechazado`** (CE-005): parametrizada con `start_date`/`end_date`/`passengers`/desconocidos → `400 VALIDATION_ERROR`, 0 cálculos. · M: `none` · P: `pending`
- [ ] **T022** · HU3: carga ligera de 50 embarcaciones contra stub WireMock de Flota verificando una única consulta y finalización dentro del presupuesto del cliente (timeout 1 s). El SLA < 500 ms es de Flota. · M: `none` · P: `pending`
- [ ] **T023** · **`ce002_…`** (CE-002, ArchUnit) + **`ce003_aviso_obligatorio_presente`** (CE-003, texto del `warning` en toda respuesta individual; métrica de negocio no automatizable). · M: `none` · P: `pending`

### Phase 6: Polish

- [ ] **T024** · ArchUnit completo (reglas §3.4): `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `port.in`; el controller no accede a repositorios ni al servicio concreto de UC02; DTOs HTTP fuera de `application`. · M: `none` · P: `pending`
- [ ] **T025** · Documentación/contratos: proponer la corrección del ejemplo del contrato de lote (D-UC01-02: `2026-10-03` es sábado) y registrar las discrepancias de costura (D-UC01-03, D-UC01-04, D-UC01-05, D-UC01-06); verificar que las rutas y códigos coinciden con `contracts/README.md`. No se edita ningún contrato ni SPEC sin acuerdo. · M: `none` · P: `pending`
- [ ] **T026** · Compartido: revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-005. · M: `none` · P: `pending`
- [ ] **T027** · Costuras: verificar el uso correcto de la firma publicada de `ProvideBaseRateUseCase` (UC02·T016) y de `ClockPort`/`Money`/`BoatId` (UC02·T005/T006); confirmar que UC01 **no expone** puertos a otros bloques y que **no escribe** `operational_failure`. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─┬─> T003 ─> T004 ─> T005 ─> T006 ─┬─> T007 ─┐
      │         │                                   │        │
      │         └───────────────────────────────────┴────────┴─> T008 ─> T009 ─> T010
      │                                                                        │
      │                                              T011 ─> T012 ─> T013 <────┘
      │
      ├─────────────────────────────────────────────> T014 ─> T015 ─> T016 ─> T017
      │
      └─────────────────────────────────────────────> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027
```

- **Bloque 1 (T001–T007)**: Setup y Foundational compartidos (`UC11·T001`–`UC11·T009`) + el `PricingCalculator`, el `warning` y el mapeo de errores propios de UC01. Sin T004 (piezas de UC02/UC11) no compila el servicio.
- **US1 (T008–T013)** → **US2 (T014–T017)**: el individual reutiliza `PricingCalculator` (T005) y el `EstimateExceptionHandler` (T007); pueden desarrollarse en paralelo por archivos distintos (el controller es compartido: coordinar T011/T016 en el mismo archivo).
- **Resiliencia y aceptación (T018–T023)**: después de ambas modalidades; T018/T022 dependen de los artefactos de UC02/UC11.
- **Polish (T024–T027)**: al final.
- **Riesgo de secuencia**: la seguridad (401/403) está **diferida** (OQ-UC01-01); los contratos la documentan pero no se implementa ni se prueba en esta fase, evitando incoherencias con UC11 hasta la consolidación.

## Notes

- El SPEC 01 **no** define meta de rendimiento propia (OQ-UC01-02), zona horaria (OQ-UC01-03), máximo definitivo de lote (OQ-UC01-04) ni rango de `passengers` (resuelto como D-UC01-07) → `[NEEDS CLARIFICATION]`/`[PEND]` con propuesta por defecto registrada.
- **Seguridad diferida**: por acuerdo de esta revisión, la autenticación/autorización de UC01 (y el `SecurityConfig` compartido del sistema) **no se implementa ni se prueba** en este plan; queda como **OQ-UC01-01** para la revisión conjunta de los bloques (general-plan §7.1, OQ-01). Los contratos siguen documentando `401`/`403`.
- **D-UC01-06 (alternativa A)**: toda embarcación solicitada ausente del resultado de UC02 se reporta en `unavailable[]`; no se distingue "inexistente" de "sin tarifa". Se descartó modificar el puerto de UC02 porque exigía endurecer el contrato externo con Flota (no era un cambio "solo de plan").
- `ProvideBaseRateUseCase`, `ClockPort`, `Money`, `BoatId` y `BaseRateException` son de **UC02** (bloque A, plan ya redactado): UC01 los consume por nombre, no los implementa ni los prueba [guía §3.1/§3.2].
- `FinancialParametersRepository` y la tabla `financial_parameters` son de **UC11** (bloque B): UC01 los consume por nombre (seguro por pasajero). UC01 **no** persiste ni migra nada.
- `FailureRecorderPort`/`operational_failure` **no** se usan en UC01 (D-UC01-05); UC02 registra los fallos de Flota.
- Ejemplo de lote del contrato con fecha inconsistente → **D-UC01-02** (corrección propuesta, no aplicada sin acuerdo).
- Etiquetas usadas: `[SPEC]` (SPEC 01 y contratos), `[CONV]` (general-plan e inferido), `[PEND]`/`[NEEDS CLARIFICATION]` (sin definir).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Contratos, Estrategia de testing, fases con `T0NN`/`M`/`P`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-09` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]`, `[CONV]`, `[PEND]` o `[NEEDS CLARIFICATION]`.
- [ ] Discrepancias `D-UC01-01` a `D-UC01-10` con decisión explícita, sin resolución silenciosa.
- [ ] Preguntas abiertas definidas en el propio plan (OQ-UC01-01 a OQ-UC01-05), con remisión al general cuando corresponden.
- [ ] Cada RF/RNF/CE/HU del SPEC 01 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce005_…` (CE-003 con nota de no automatizable).
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§13) aplicadas.
- [ ] UC01 sin tablas, sin migración, sin persistencia; solo lee `financial_parameters` por nombre.
- [ ] «Puertos expuestos» declarado (UC01 no expone ninguno) y costuras con UC02/UC11 por nombre.
- [ ] Seguridad diferida registrada como OQ-UC01-01, sin `SecurityConfig` ni pruebas 401/403.
