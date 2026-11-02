### Test Case Specification: Estimaciones para reserva · HU1 · E1 Cálculo delegado exitoso de múltiples estimaciones en lote
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/001-solicitar-estimacion-para-reserva/spec.md](../../../docs/features/001-solicitar-estimacion-para-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC01-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir **únicamente** la lista de identificadores (`boat_ids`), el sistema calcula cada estimación con 1 día de duración, 1 pasajero y la fecha actual como fecha de evaluación, consulta la tarifa base a Flota en una sola llamada y devuelve el arreglo de estimaciones sin que el llamador ejecute ninguna operación de precio.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC01 | Specification Document | [spec.md](../../../docs/features/001-solicitar-estimacion-para-reserva/spec.md) |
| Contrato estimación en lote | REST contract | [UC01-estimacion-lote.md](../../../docs/technical-plan/contracts/rest/UC01-estimacion-lote.md) |
| Contrato Flota — tarifas base | External contract | [flota-consulta-tarifas-base.md](../../../docs/technical-plan/contracts/external/flota-consulta-tarifas-base.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El stub local de Flota ([fleet-base-rates.json](../../../wiremock/mappings/fleet-base-rates.json)) solo devuelve **2** tarifas: para el lote de 10 hay que cargar un mapping con 10 tarifas vía la API de administración de WireMock (`POST /__admin/mappings`) o usar la Flota del entorno de `release/0.1.0`.
* Los montos de esta ficha son **ilustrativos** (stub y ejemplos de los contratos); **ajustar a la versión vigente del entorno**.

##### Dependencies & Assumptions
* **TC-UC11-HU4-E1 debe haber pasado**: existe versión vigente de `financial_parameters` con tarifa de seguro y el porcentaje que exige la fecha evaluada.
* Flota (stub o real) con tarifa base para las 10 embarcaciones de prueba.

##### Preconditions & Default Setup
* **Initial State** : parámetros de ejemplo `insurance_fee_per_passenger = 15000.0000`, `weekend_increase_pct = 15.0000`, `high_season_increase_pct = 25.0000`, `commission_pct = 15.0000`; 10 embarcaciones con `base_rate` conocida (una de ellas `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` → `350000.00`).
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, stub de Flota en `http://localhost:8081`, BD con `psql "host=localhost port=5433 dbname=seashare user=seashare"` (puerto publicado `POSTGRES_HOST_PORT`).
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Cálculo delegado exitoso de las 10 estimaciones
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar las tarifas que provee Flota | `POST http://localhost:8081/api/v1/fleet/base-rates` con los 10 IDs (o revisar el mapping cargado) | 10 UUID + `base_rate` por embarcación | Flota devuelve las 10 tarifas; es la única fuente del valor (RF-005, RF-004 de UC02). | [ ] | |
| 2 | Enviar la solicitud en lote | `POST http://localhost:8080/api/v1/estimates/batch` con cuerpo que solo lleva `boat_ids` | `{"boat_ids":[10 UUID]}` | `200 OK`; `evaluation_date` = fecha actual en `America/Bogota`, `duration_days = 1`, `passengers = 1` (RF-002, regla 6 del contrato). | [ ] | |
| 3 | Verificar la cobertura del lote | Revisar el cuerpo de la respuesta | N/A | `estimates` con 10 entradas y `unavailable` vacío; la unión de ambos es el conjunto de IDs solicitados (RF-001, invariante §3 regla 5). | [ ] | |
| 4 | Verificar la fórmula en una muestra | Comparar `estimated_total` con `(tarifa base final del día × 1) + (15000.00 × 1)` | `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01`, tarifa `350000.00` | Coincide exacto; si el día es regular, `365000.00` (RF-002). | [ ] | |
| 5 | Verificar que no se cobra depósito ni penalidades | Recalcular sin el 10 % de depósito ni ajustes posteriores | N/A | El total no incorpora depósito de garantía, penalidades ni ajustes de reserva (RF-002). | [ ] | |
| 6 | Verificar la delegación del cálculo | Revisar que cada entrada solo trae `boat_id` y `estimated_total` (sin tarifa ni desglose) | N/A | Reservas solo renderiza el valor devuelto; no hay operaciones de precio en el llamador (RNF-001, CE-002). | [ ] | |
| 7 | Verificar la consulta única a Flota | Journal del stub en `http://localhost:8081/__admin/requests` (o logs de la app) | N/A | Una sola `POST /api/v1/fleet/base-rates` con la lista completa de IDs (RF-005). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Cuerpo con fechas o pasajeros | `POST /api/v1/estimates/batch` incluyendo `start_date`, `end_date` o `passengers` | payload con campos extra | `400 VALIDATION_ERROR`, sin ejecutar ningún cálculo (RF-007, CE-005). | [ ] | |
| 2 | Lote sobre el máximo | Enviar 51 identificadores (máximo 50) | 51 UUID | `400 BATCH_SIZE_EXCEEDED`, sin procesar ningún elemento (RF-006). | [ ] | |
| 3 | Lista vacía | Enviar `boat_ids: []` | `[]` | `200` con `estimates: []` y `unavailable: []`, sin error (casos extremos). | [ ] | |
| 4 | Identificador inexistente o sin tarifa | Incluir un ID que Flota no devuelve | 9 IDs válidos + 1 desconocido | Los 9 se estiman; el desconocido aparece en `unavailable[]` con `reason = "NO_BASE_RATE"` (casos extremos + desviación A4). | [ ] | |
| 5 | Flota caída o timeout | Detener el stub o simular demora superior a 1 s | N/A | `503 FLEET_UNAVAILABLE`, sin estimaciones parciales ni inventadas (RNF-003, CE-004). | [ ] | |
| 6 | Sin parámetros o sin el porcentaje exigido | Ejecutar con BD sin versión vigente o sin el porcentaje de la fecha evaluada | N/A | `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, sin estimación parcial (casos extremos). | [ ] | |
| 7 | Elemento que no es UUID | Enviar `boat_ids: ["no-es-uuid"]` | texto libre | `400 VALIDATION_ERROR` (contrato §5). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un lote de exactamente 50 identificadores se procesa; uno de 51 se rechaza (RF-006).
* **EC-002** : Un `boat_id` repetido no duplica la estimación: el resultado se calcula por identificador único (invariante del contrato §3 regla 5).
* **EC-003** : El cálculo interno usa `BigDecimal` con 4 decimales y la salida se redondea `HALF_UP` a 2 decimales (RNF-002, convención §3.1).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
