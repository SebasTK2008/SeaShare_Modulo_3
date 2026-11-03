### Test Case Specification: Tarifa base · HU1 · E1 Tarifa regular (sin condición dinámica vigente)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/002-brindar-tarifa-base/spec.md](../../002-brindar-tarifa-base/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC02-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la fecha evaluada no es fin de semana ni cae en una ventana de temporada alta, el sistema devuelve la tarifa base provista por Flota **sin ningún ajuste dinámico** y la registra como tarifa vigente de la reserva, sin depender del tipo ni la categoría de la embarcación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC02 | Specification Document | [spec.md](../../002-brindar-tarifa-base/spec.md) |
| Contrato Flota — tarifas base | External contract | [flota-consulta-tarifas-base.md](../../../technical-plan/contracts/external/flota-consulta-tarifas-base.md) |
| Contrato evento UC03 (consumidor de UC02) | Events contract | [UC03-informacion-reserva.md](../../../technical-plan/contracts/events/UC03-informacion-reserva.md) |
| Contrato valor calculado (UC04) | REST contract | [UC04-valor-calculado-reserva.md](../../../technical-plan/contracts/rest/UC04-valor-calculado-reserva.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* UC02 es **interno** y no expone endpoint (RF-003): su resultado se observa en la tarifa que UC03 persiste (`reservation_information.base_rate`) y, de forma indirecta, en el alquiler que calcula UC04.
* Los montos y porcentajes son **ilustrativos**; **ajustar a la versión vigente del entorno**.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`** (automático con el perfil `qa`, ver `matriz-qa.md` → *Seed inicial de QA*): versión vigente con seguro y porcentajes (no depende de otro TC).
* Flota (stub o real) arriba y RabbitMQ con la cola `finance.reservation-info.v1` declarada.

##### Preconditions & Default Setup
* **Initial State** : `insurance_fee_per_passenger = 15000.0000`, `weekend_increase_pct = 10.0000`, `high_season_increase_pct = 25.0000`; embarcación `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` → `350000.00` y `9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02` → `480000.00`; fecha evaluada de ejemplo **2027-02-10 (miércoles, fuera de toda ventana)**.
* **Environment** : staging local (`docker compose --profile app up --build -d`), RabbitMQ en `http://localhost:15672`, stub de Flota en `http://localhost:8081`, BD con `psql "host=localhost port=5433 dbname=seashare user=seashare"`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Tarifa regular sin ajuste dinámico
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la tarifa provista por Flota | `POST http://localhost:8081/api/v1/fleet/base-rates` | `boat_id = 3f2c…1d01` | `base_rate = "350000.00"`; Flota es la única fuente autoritativa (RF-004). | [x] | |
| 2 | Registrar una reserva con fecha regular | Publicar en RabbitMQ (`seashare.reservations` → `reservation.info.provided`) el payload de UC03 | `reservation_id = c0a80101-1111-4a2b-9c3d-00000000e101`, `boat_id`, `owner_id`, `start_date = 2027-02-10`, `end_date = 2027-02-12`, `passengers = 3`, `max_capacity = 6` | El mensaje se confirma (ack) y UC03 incluye UC02 con `boat_id` y `start_date` (UC03 RF-002). | [x] | |
| 3 | Verificar la tarifa final registrada | `SELECT base_rate, base_rate_parameters_id FROM reservation_information WHERE reservation_id = 'c0a80101-1111-4a2b-9c3d-00000000e101'` | N/A | `base_rate = 350000.0000`, idéntica a la de Flota (RF-001, RF-002 condición regular). | [x] | |
| 4 | Verificar que no se aplicó ninguna versión de parámetros | Revisar `base_rate_parameters_id` de la misma fila | N/A | `NULL`: una fecha regular no requiere porcentajes y no se lee versión (regla 14 y D-UC02-08). | [x] | |
| 5 | Verificar el efecto en el cálculo del valor | `POST http://localhost:8080/api/v1/reservations/c0a80101-1111-4a2b-9c3d-00000000e101/calculated-value` | misma reserva | `rental_amount = 1050000.00` (`350000.00 × 3`), lo que confirma la tarifa regular (UC04 RF-003). | [x] | |
| 6 | Verificar independencia del tipo/categoría | Repetir los pasos 2–3 con la segunda embarcación | `9a1b7c33-…-0c9e5f3a2b02`, misma fecha | `base_rate = 480000.0000`: solo cambia la tarifa de Flota, no el ajuste (RF-007, CE-002). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fecha regular sin configuración de parámetros | BD recreada sin versión de `financial_parameters` y evento con fecha regular | `start_date = 2027-02-10` | La tarifa se obtiene igual (`350000.0000`) y `base_rate_parameters_id` queda `NULL` (regla 13: el día regular no exige porcentaje). | [x] | `docker compose --profile app down -v` antes de la prueba. |
| 2 | Flota caída o timeout | Detener el stub y publicar el evento | N/A | UC03 reintenta y envía a `finance.reservation-info.v1.dlq`, registrando el fallo; no se persiste información incompleta (RNF-003, CE-003). | [x] | En UC01: `503 FLEET_UNAVAILABLE`. |
| 3 | Embarcación sin tarifa base | Publicar el evento con un `boat_id` que Flota no devuelve | UUID desconocido | No se registra la reserva y se anota el fallo; no se asume tarifa (casos extremos). | [x] | |
| 4 | Evento con fechas inválidas | Publicar con `end_date` anterior a `start_date` | `2027-02-12` → `2027-02-10` | Se confirma (ack) y se registra el fallo; no se consulta Flota ni se persiste (UC03 RF-005). | [x] | |
| 5 | Reenvío con otra fecha de inicio | Republicar el mismo `reservation_id` con `start_date` de fin de semana | `2026-11-07` | El *upsert* vuelve a incluir UC02 y la tarifa registrada cambia al valor ajustado (casos extremos de UC03). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un viernes fuera de ventana (p. ej. `2026-11-13`) es regular: el ajuste de fin de semana es solo sábado y domingo (D-UC02-05).
* **EC-002** : La clasificación se hace en la zona horaria de negocio (`America/Bogota`, `seashare.timezone`), no en la del servidor.
* **EC-003** : La tarifa se conserva con 4 decimales internos aunque la fecha sea regular; el redondeo a 2 decimales ocurre en las salidas (RNF-002, convención §3.1).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
