### Test Case Specification: Estimaciones para reserva · HU2 · E1 Estimación delegada para una sola embarcación y aviso legal
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/001-solicitar-estimacion-para-reserva/spec.md](../../001-solicitar-estimacion-para-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC01-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir el identificador de una embarcación, un rango de fechas y el número de pasajeros, el sistema calcula delegadamente la estimación (días inclusivos × tarifa de la fecha de inicio + seguro por pasajero) y devuelve junto al valor total una advertencia obligatoria, sin que el llamador calcule precios.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC01 | Specification Document | [spec.md](../../001-solicitar-estimacion-para-reserva/spec.md) |
| Contrato estimación individual | REST contract | [UC01-estimacion-individual.md](../../../technical-plan/contracts/rest/UC01-estimacion-individual.md) |
| Contrato Flota — tarifas base | External contract | [flota-consulta-tarifas-base.md](../../../technical-plan/contracts/external/flota-consulta-tarifas-base.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Los montos y porcentajes de esta ficha son **ilustrativos** (ejemplos de los contratos y stub de Flota); **ajustar a la versión vigente del entorno**.
* El aviso legal se valida carácter a carácter contra el texto fijado por el contrato.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`** (automático con el perfil `qa`, ver `matriz-qa.md` → *Seed inicial de QA*): existe versión vigente con tarifa de seguro y ambos porcentajes de tarifa dinámica (no depende de otro TC).
* Flota (stub o real) con tarifa base para la embarcación de prueba.

##### Preconditions & Default Setup
* **Initial State** : `insurance_fee_per_passenger = 15000.0000`, `weekend_increase_pct = 10.0000`, `high_season_increase_pct = 25.0000`; embarcación `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` con `base_rate = 350000.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, stub de Flota en `http://localhost:8081`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Estimación individual con advertencia obligatoria
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la tarifa base de la embarcación | `POST http://localhost:8081/api/v1/fleet/base-rates` | `boat_id = 3f2c…1d01` | Flota devuelve `base_rate = "350000.00"`; es la única fuente (RF-005, UC02 RF-004). | [x] | |
| 2 | Solicitar la estimación individual | `POST http://localhost:8080/api/v1/estimates/individual` | `boat_id`, `start_date = 2026-12-20`, `end_date = 2026-12-22`, `passengers = 4` | `200 OK` con eco de `boat_id`, fechas y pasajeros (RF-003, RF-004). | [x] | |
| 3 | Verificar los días inclusivos informados | Revisar `duration_days` | inicio 20, fin 22 | `duration_days = 3` (20, 21 y 22) (RF-003). | [x] | |
| 4 | Verificar el aviso legal obligatorio | Comparar el campo `warning` con el texto del contrato | N/A | Texto exacto: `Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva` (HU2 esc. 1). | [x] | |
| 5 | Verificar la tarifa aplicada y el total | Comparar `estimated_total` con `(tarifa final del 2026-12-20 × 3) + (15000.00 × 4)` | Temporada alta 25 % > fin de semana 10 % ⇒ `437500.00` | `1312500.00 + 60000.00 = 1372500.00` (RF-003, RF-002). | [x] | |
| 6 | Verificar que la tarifa de inicio rige toda la duración | Recalcular los 3 días con la misma tarifa (no día a día) | `437500.00` diarios | El total no varía por el 21/22-dic: se usa la tarifa de la fecha de inicio (CE-002, UC02 RF-001). | [x] | |
| 7 | Verificar que el estimado excluye el depósito | Comprobar que el total no suma el 10 % de la tarifa diaria | N/A | El aviso y el monto confirman que no se incluye depósito de garantía (RF-002, HU2 esc. 1). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fecha de fin anterior al inicio | `POST /api/v1/estimates/individual` con fin < inicio | `2026-12-22` → `2026-12-20` | `400 INVALID_DATE_RANGE`, sin cálculo (casos extremos). | [x] | |
| 2 | Fecha de inicio en el pasado | Enviar un día anterior a hoy | `2026-10-01` | `400 INVALID_DATE_RANGE`; la comparación usa `America/Bogota` (contrato §3 regla 1). | [x] | |
| 3 | Formato de fecha inválido | Enviar `start_date = "20/12/2026"` | formato no ISO | `400 INVALID_DATE_RANGE`, sin cálculo (casos extremos). | [x] | |
| 4 | Embarcación sin tarifa o no reconocida | Usar un `boat_id` que Flota no devuelve | UUID desconocido | `422 BASE_RATE_NOT_AVAILABLE`; no se asume tarifa (casos extremos, contrato §3 regla 7). | [x] | |
| 5 | Falta un campo obligatorio o pasajeros inválidos | Enviar sin `boat_id`/`passengers`, o `passengers = 0` | payload incompleto | `400 VALIDATION_ERROR` (contrato §5; pasajeros entero ≥ 1). | [x] | |
| 6 | Flota caída o timeout | Detener el stub o simular demora > 1 s | N/A | `503 FLEET_UNAVAILABLE`, sin estimación parcial (RNF-003, CE-004). | [x] | |
| 7 | Sin parámetros financieros exigidos | Ejecutar sobre BD sin versión vigente o sin el porcentaje de la fecha de inicio | N/A | `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, sin total parcial (casos extremos). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `start_date == end_date` es válido y equivale a 1 día; solo es inválida una fecha de fin anterior (casos extremos).
* **EC-002** : El aviso `warning` es obligatorio incluso en una estimación de 1 día o de 1 pasajero (HU2 esc. 1).
* **EC-003** : UC01 no limita `passengers` por capacidad; la capacidad máxima se valida al registrar la reserva en UC03 (contrato §2).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
