### Test Case Specification: Información de reserva · HU1 · E1 Registro exitoso de la información de una reserva
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/003-brindar-informacion-de-reserva/spec.md](../../003-brindar-informacion-de-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC03-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir por cola los datos de una reserva (identificador, embarcación, fechas, pasajeros, propietario y capacidad máxima), el sistema incluye "Brindar tarifa base" con la fecha de inicio, registra internamente toda la información —incluida la tarifa vigente y el propietario recibido— sin consultar directamente a Flota por propietario o capacidad, no calcula montos y no devuelve respuesta a Reservas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC03 | Specification Document | [spec.md](../../003-brindar-informacion-de-reserva/spec.md) |
| Contrato evento información de reserva | Events contract | [UC03-informacion-reserva.md](../../../technical-plan/contracts/events/UC03-informacion-reserva.md) |
| Contrato Flota — tarifas base | External contract | [flota-consulta-tarifas-base.md](../../../technical-plan/contracts/external/flota-consulta-tarifas-base.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El caso de uso es **unidireccional**: no hay respuesta a Reservas; el éxito se verifica en la fila persistida y en la ausencia de fallos registrados.
* Los montos y porcentajes son **ilustrativos**; **ajustar a la versión vigente del entorno**.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`** (automático con el perfil `qa`, ver `matriz-qa.md` → *Seed inicial de QA*): versión vigente con seguro y porcentajes (no depende de otro TC).
* **TC-UC02-HU1-E3** ejecutado: la tarifa de una fecha de temporada alta se ajusta correctamente.
* RabbitMQ arriba con exchange `seashare.reservations` y cola `finance.reservation-info.v1`.

##### Preconditions & Default Setup
* **Initial State** : `high_season_increase_pct = 25.0000`, `weekend_increase_pct = 10.0000`, `insurance_fee_per_passenger = 15000.0000`; embarcación `3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` con `base_rate = 350000.00`; no existe la reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), consola RabbitMQ en `http://localhost:15672`, stub de Flota en `http://localhost:8081`, BD con `psql "host=localhost port=5433 dbname=seashare user=seashare"`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro interno de la información de la reserva
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Publicar la información de la reserva | Consola RabbitMQ → `seashare.reservations` → routing key `reservation.info.provided` con el payload de [UC03-informacion-reserva.md](../../../technical-plan/contracts/events/UC03-informacion-reserva.md) | `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`, `boat_id = 3f2c…1d01`, `start_date = 2026-12-20`, `end_date = 2026-12-22`, `passengers = 4`, `owner_id = a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d`, `max_capacity = 6` | El mensaje entra en `finance.reservation-info.v1` y se confirma (`ack`) al persistir. | [x] | `Message-Id` obligatorio. |
| 2 | Verificar la naturaleza unidireccional | Revisar que el productor no recibe respuesta ni desglose | N/A | No se emite respuesta a Reservas; solo se confirma el mensaje (RF-006, CE-002). | [x] | |
| 3 | Verificar la información registrada | `SELECT reservation_id, boat_id, start_date, end_date, passengers, owner_id, max_capacity FROM reservation_information WHERE reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10'` | N/A | Una fila con todos los datos recibidos, sin alteraciones (RF-001, RF-004, CE-001). | [x] | |
| 4 | Verificar la tarifa base registrada | `SELECT base_rate, base_rate_parameters_id FROM reservation_information WHERE reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10'` | Fecha de inicio `2026-12-20` (temporada alta 25 %) | `base_rate = 437500.0000` y `base_rate_parameters_id` no nulo: UC02 se incluyó con la fecha de inicio (RF-002). | [x] | Con otro porcentaje, recalcular. |
| 5 | Verificar el origen del propietario y la capacidad | Journal del stub en `http://localhost:8081/__admin/requests` | N/A | Solo hay `POST /api/v1/fleet/base-rates` con `boat_ids`; ninguna consulta para obtener `owner_id` o `max_capacity` (RF-003, CE-004). | [x] | |
| 6 | Verificar que no se calculan montos | `SELECT rental_amount, insurance_amount, deposit_amount, total_amount, calculated_at FROM reservation_information WHERE reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10'` | N/A | Todos `NULL`: este caso de uso no calcula alquiler, seguro ni depósito (RF-007). | [x] | |
| 7 | Verificar que no se registró ningún fallo | `SELECT use_case, reason FROM operational_failure WHERE reservation_id = 'b7d0e2a1-…-3e5f7a1c9b10'` | N/A | Sin filas: el registro se completó sin incidentes (RNF-003). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fecha de fin anterior al inicio | Publicar el evento con `end_date < start_date` | `2026-12-22` → `2026-12-20` | `ack` y registro del fallo; no se persiste la reserva (RF-005, política §4.4). | [x] | |
| 2 | Pasajeros por encima de la capacidad | Publicar con `passengers > max_capacity` | `passengers = 7`, `max_capacity = 6` | `ack` y fallo registrado; no se persiste ni se consulta Flota (RF-005). | [x] | |
| 3 | Pasajeros cero o negativos | Publicar con `passengers = 0` y con `-1` | `0`, `-1` | `ack` y fallo registrado; no se persiste (RF-005, CHECK de la tabla). | [x] | |
| 4 | Falta propietario o capacidad máxima | Publicar sin `owner_id` y, en otra prueba, sin `max_capacity` | payload incompleto | No se consulta Flota como respaldo, no se persiste información incompleta y se conserva la versión válida previa si existía (RF-003, RNF-003). | [x] | |
| 5 | Flota caída o timeout | Detener el stub y publicar el evento | N/A | 5 intentos con *backoff* base 1 s, luego DLQ `finance.reservation-info.v1.dlq` y registro en `operational_failure`; sin tarifa asumida (RNF-003, CE-003). | [x] | |
| 6 | Embarcación sin tarifa base | Publicar con un `boat_id` que Flota no devuelve | UUID desconocido | Fallo permanente: `ack` y registro, sin persistir ni inventar tarifa (casos extremos, D-UC03-04). | [x] | |
| 7 | Reenvío con datos modificados (*upsert*) | Republicar la misma reserva con nueva fecha de fin y pasajeros | `end_date = 2026-12-23`, `passengers = 5` | Se sobrescribe la información, se re-incluye UC02 y se invalidan montos y `frozen_parameters_id` si ya se habían calculado (casos extremos, RF-008 de UC04). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `start_date == end_date` es válido y representa 1 día; solo se rechaza un fin anterior al inicio (RF-005).
* **EC-002** : Un reenvío idéntico es un *upsert* sin duplicar filas (la reserva tiene `reservation_id` como PK).
* **EC-003** : Sobre una reserva ya calculada, el *upsert* deja `rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`, `frozen_parameters_id` y `calculated_at` en `NULL`; UC04 debe recalcular antes de un nuevo cobro (RF-008 de UC04).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
