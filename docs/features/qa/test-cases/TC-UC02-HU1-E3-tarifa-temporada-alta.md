### Test Case Specification: Tarifa base · HU1 · E3 Tarifa ajustada por temporada alta
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/002-brindar-tarifa-base/spec.md](../../002-brindar-tarifa-base/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC02-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la fecha evaluada cae dentro de una de las ventanas de temporada alta (fin de año, mitad de año, Semana Santa o semana de receso), el sistema aplica el porcentaje de incremento configurado sobre la tarifa de Flota y registra esa tarifa como vigente, sin que las ventanas se configuren manualmente.

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
* Los montos y porcentajes son **ilustrativos**; **ajustar a la versión vigente del entorno**. Las fechas de las pruebas de frontera deben corresponder al mismo tipo de día (ventana / fuera de ventana) en el calendario del año de ejecución.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`**: versión vigente con ambos porcentajes y seguro (no depende de otro TC).
* Flota (stub o real) arriba y cola `finance.reservation-info.v1` operativa.

##### Preconditions & Default Setup
* **Initial State** : `high_season_increase_pct = 25.0000`, `weekend_increase_pct = 15.0000`, `insurance_fee_per_passenger = 15000.0000`; embarcación `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` → `350000.00`; fecha evaluada de ejemplo **2026-11-19 (jueves, ventana de fin de año)**.
* **Environment** : staging local (`docker compose --profile app up --build -d`), RabbitMQ en `http://localhost:15672`, stub de Flota en `http://localhost:8081`, BD con `psql "host=localhost port=5433 dbname=seashare user=seashare"`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Ajuste de temporada alta aplicado a la tarifa de Flota
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la tarifa provista por Flota | `POST http://localhost:8081/api/v1/fleet/base-rates` | `boat_id = 3f2c…1d01` | `base_rate = "350000.00"` (RF-004). | [x] | |
| 2 | Confirmar que la fecha cae en temporada alta | Contrastar la fecha con las ventanas de RF-006 | `2026-11-19` | Jueves dentro de fin de año (15-nov → 15-ene): condición de temporada alta. | [x] | |
| 3 | Registrar la reserva con fecha de temporada alta | Publicar en `seashare.reservations` → `reservation.info.provided` | `reservation_id = c0a80101-1111-4a2b-9c3d-00000000e103`, `boat_id`, `owner_id`, `start_date = 2026-11-19`, `end_date = 2026-11-21`, `passengers = 2`, `max_capacity = 6` | Mensaje confirmado; UC03 incluye UC02 con esa fecha de inicio (UC03 RF-002). | [x] | |
| 4 | Verificar la tarifa ajustada | `SELECT base_rate, base_rate_parameters_id FROM reservation_information WHERE reservation_id = 'c0a80101-1111-4a2b-9c3d-00000000e103'` | N/A | `base_rate = 437500.0000` = `350000.00 × 1.25` (RF-002, RF-006). | [x] | Con otro porcentaje, recalcular con la fórmula. |
| 5 | Verificar la referencia a la versión usada | Consultar `base_rate_parameters_id` y su `version_number` | N/A | No es `NULL` y apunta a la versión vigente del porcentaje de temporada alta (D-UC02-08). | [x] | |
| 6 | Verificar el efecto en el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80101-1111-4a2b-9c3d-00000000e103/calculated-value` | misma reserva | `rental_amount = 1312500.00` (`437500.00 × 3` días inclusivos), coherente con la tarifa ajustada (UC04 RF-003). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Frontera de fin de año | Registrar reservas con las fechas de frontera | `2026-11-14` (sábado), `2026-11-15` (domingo), `2027-01-15` (viernes), `2027-01-16` (sábado) | `11-14` y `01-16`: solo fin de semana (`402500.0000`); `11-15` y `01-15`: temporada alta (`437500.0000`) (RF-006). | [x] | |
| 2 | Ventana de mitad de año | Repetir con la frontera de junio/julio | `2027-07-30` (viernes) y `2027-07-31` (sábado) | `07-30`: temporada alta (`437500.0000`); `07-31`: solo fin de semana (`402500.0000`) (RF-006). | [x] | |
| 3 | Semana Santa calculada por calendario | Repetir con Jueves y Viernes Santo y con el día anterior | `2027-03-24`, `2027-03-25`, `2027-03-26` | `03-24`: regular (`350000.0000`); `03-25` y `03-26`: temporada alta (`437500.0000`) (RF-006, RNF-004). | [x] | |
| 4 | Ventana de receso | Repetir en los límites de la ventana | `2027-10-05` (martes) y `2027-10-13` (miércoles) | `10-05`: temporada alta; `10-13`: regular (RF-006). | [x] | |
| 5 | Sin versión vigente de parámetros | BD recreada sin `financial_parameters` y evento en temporada alta | `start_date = 2026-11-19` | No se calcula la tarifa: UC03 registra el fallo y envía a la DLQ; UC01 responde `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` (casos extremos). | [x] | |
| 6 | Flota caída o timeout | Detener el stub y publicar el evento | N/A | Reintentos y DLQ con registro del fallo; no se persiste tarifa asumida (RNF-003, CE-003). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Las ventanas se recalculan cada año sin configuración manual; la de Semana Santa depende del Domingo de Resurrección (Meeus/Jones/Butcher) y no de una tabla cargada (RNF-004).
* **EC-002** : Los puentes y festivos entre semana **no** activan temporada alta: se tarifan como regulares salvo que caigan en una ventana declarada (RF-006).
* **EC-003** : Si la fecha es fin de semana y temporada alta a la vez, prevalece el ajuste que produzca la tarifa más alta (casos extremos de UC02).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
