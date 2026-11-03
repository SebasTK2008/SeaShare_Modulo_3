### Test Case Specification: Tarifa base · HU1 · E2 Tarifa ajustada por fin de semana
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/002-brindar-tarifa-base/spec.md](../../002-brindar-tarifa-base/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC02-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la fecha evaluada es sábado o domingo, el sistema aplica sobre la tarifa de Flota el incremento de fin de semana configurado (`tarifa × (1 + % / 100)`) y registra la tarifa resultante como tarifa vigente de la reserva, junto con la referencia a la versión de parámetros usada.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC02 | Specification Document | [spec.md](../../002-brindar-tarifa-base/spec.md) |
| Contrato Flota — tarifas base | External contract | [flota-consulta-tarifas-base.md](../../../technical-plan/contracts/external/flota-consulta-tarifas-base.md) |
| Contrato evento UC03 (consumidor de UC02) | Events contract | [UC03-informacion-reserva.md](../../../technical-plan/contracts/events/UC03-informacion-reserva.md) |
| Contrato valor calculado (UC04) | REST contract | [UC04-valor-calculado-reserva.md](../../../technical-plan/contracts/rest/UC04-valor-calculado-reserva.md) |

--------------------------------------------------------------------------------

#### Test Case Limitations
* UC02 es interno (RF-003): el ajuste se observa en `reservation_information.base_rate` (escrito por UC03) y en el alquiler de UC04.
* Los montos y porcentajes son **ilustrativos**; **ajustar a la versión vigente del entorno**.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`** (automático con el perfil `qa`, ver `matriz-qa.md` → *Seed inicial de QA*): versión vigente con ambos porcentajes y seguro (no depende de otro TC).
* Flota (stub o real) arriba y cola `finance.reservation-info.v1` operativa.

##### Preconditions & Default Setup
* **Initial State** : `weekend_increase_pct = 10.0000`, `high_season_increase_pct = 25.0000`, `insurance_fee_per_passenger = 15000.0000`; embarcación `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` → `350000.00`; fecha evaluada de ejemplo **2026-11-07 (sábado), fuera de las ventanas de temporada alta**.
* **Environment** : staging local (`docker compose --profile app up --build -d`), RabbitMQ en `http://localhost:15672`, stub de Flota en `http://localhost:8081`, BD con `psql "host=localhost port=5433 dbname=seashare user=seashare"`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Ajuste de fin de semana aplicado a la tarifa de Flota
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la tarifa provista por Flota | `POST http://localhost:8081/api/v1/fleet/base-rates` | `boat_id = 3f2c…1d01` | `base_rate = "350000.00"` (RF-004). | [x] | |
| 2 | Confirmar que la fecha evaluada es fin de semana | Verificar el día de la semana en `America/Bogota` | `2026-11-07` | Sábado: aplica la condición de fin de semana (D-UC02-05, RF-002). | [x] | |
| 3 | Registrar la reserva con fecha de sábado | Publicar en `seashare.reservations` → `reservation.info.provided` | `reservation_id = c0a80101-1111-4a2b-9c3d-00000000e102`, `boat_id`, `owner_id`, `start_date = 2026-11-07`, `end_date = 2026-11-08`, `passengers = 2`, `max_capacity = 6` | Mensaje confirmado; UC03 incluye UC02 con esa fecha de inicio (UC03 RF-002). | [x] | |
| 4 | Verificar la tarifa ajustada | `SELECT base_rate, base_rate_parameters_id FROM reservation_information WHERE reservation_id = 'c0a80101-1111-4a2b-9c3d-00000000e102'` | N/A | `base_rate = 385000.0000` = `350000.00 × 1.10` (RF-002). | [x] | Con otro porcentaje, recalcular con la fórmula. |
| 5 | Verificar la referencia a la versión usada | Consultar `base_rate_parameters_id` y su `version_number` | N/A | No es `NULL` y apunta a la versión vigente que aportó el porcentaje (RF-006 de UC11, D-UC02-08). | [x] | |
| 6 | Verificar el efecto en el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80101-1111-4a2b-9c3d-00000000e102/calculated-value` | misma reserva | `rental_amount = 770000.00` (`385000.00 × 2`), coherente con la tarifa ajustada (UC04 RF-003). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sábado y domingo reciben el mismo ajuste | Repetir con `start_date` domingo | `2026-11-08` | `base_rate = 385000.0000`; ambos días son fin de semana (D-UC02-05). | [x] | |
| 2 | Coincidencia de fin de semana y temporada alta | Reserva con fecha en ventana y en domingo | `2026-12-20` (domingo, ventana de fin de año) | Se aplica el ajuste **mayor**: `437500.0000` (25 %), no `385000.00` (casos extremos, RF-002). | [x] | |
| 3 | Sin versión vigente de parámetros | BD recreada sin `financial_parameters` y evento en sábado | `start_date = 2026-11-07` | No se calcula la tarifa: UC03 registra el fallo y envía a la DLQ; UC01 responde `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` (casos extremos). | [x] | |
| 4 | Flota caída o timeout | Detener el stub y publicar el evento | N/A | Reintentos y DLQ con registro del fallo; no se persiste tarifa asumida (RNF-003, CE-003). | [x] | |
| 5 | Embarcación sin tarifa base | Evento con un `boat_id` no devuelto por Flota | UUID desconocido | No se registra la reserva; se anota el fallo y no se inventa tarifa (casos extremos). | [x] | |
| 6 | Víspera y días hábiles | Reserva con `start_date` viernes y con lunes | `2026-11-06`, `2026-11-09` | Tarifa regular `350000.0000`: el ajuste no se extiende a días hábiles (D-UC02-05). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un festivo que cae en sábado o domingo se tarifa con el porcentaje de fin de semana, sin porcentaje adicional (RF-006).
* **EC-002** : El primer día de una ventana de temporada alta (`2026-11-15`) ya es temporada alta; la víspera (`2026-11-14`, sábado) solo tiene ajuste de fin de semana.
* **EC-003** : El porcentaje se aplica con `BigDecimal` y escala 4; el resultado se redondea a 2 decimales solo en las salidas (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
