### Test Case Specification: Valor calculado · HU1 · E1 Cálculo exitoso del desglose completo
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/004-solicitar-el-valor-calculado-de-la-reserva/spec.md](../../../docs/features/004-solicitar-el-valor-calculado-de-la-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC04-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, para una reserva con información previamente registrada, el sistema calcula el monto de alquiler (tarifa registrada × días inclusivos), el seguro náutico (tarifa de seguro × pasajeros) y el depósito de garantía (10 % de la tarifa diaria registrada), devuelve el desglose completo con el valor total y deja los montos y la referencia a la versión de parámetros disponibles para "Procesar cobro".

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC04 | Specification Document | [spec.md](../../../docs/features/004-solicitar-el-valor-calculado-de-la-reserva/spec.md) |
| Contrato valor calculado | REST contract | [UC04-valor-calculado-reserva.md](../../../docs/technical-plan/contracts/rest/UC04-valor-calculado-reserva.md) |
| Contrato evento UC03 (origen de la información) | Events contract | [UC03-informacion-reserva.md](../../../docs/technical-plan/contracts/events/UC03-informacion-reserva.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Los montos y porcentajes son **ilustrativos** (ejemplos de los contratos); **ajustar a la versión vigente del entorno**.
* El caso de uso responde de forma síncrona; la inspección de la BD es necesaria para verificar el congelamiento de parámetros.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1 debe haber pasado**: la reserva está registrada con tarifa base, fechas, pasajeros, propietario y capacidad.
* **TC-UC11-HU4-E1 debe haber pasado**: existe versión vigente de `financial_parameters`.
* **TC-UC02-HU1-E1** ejecutado: la tarifa registrada para una fecha regular no lleva ajuste.

##### Preconditions & Default Setup
* **Initial State** : reserva `c0a80102-2222-4b3c-8d4e-00000000e201` registrada vía UC03 con `boat_id = 3f2c…1d01`, `start_date = 2027-02-10`, `end_date = 2027-02-12`, `passengers = 4`, `max_capacity = 6`, `base_rate = 350000.0000`; `insurance_fee_per_passenger = 15000.0000`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, BD con `psql "host=localhost port=5433 dbname=seashare user=seashare"`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Cálculo y devolución del desglose completo
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la información registrada | `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80102-2222-4b3c-8d4e-00000000e201'` | N/A | `base_rate = 350000.0000`, del 10 al 12 de feb, 4 pasajeros: insumos completos (RF-002). | [ ] | |
| 2 | Solicitar el valor calculado | `POST http://localhost:8080/api/v1/reservations/c0a80102-2222-4b3c-8d4e-00000000e201/calculated-value` sin cuerpo | N/A | `200 OK` con `reservation_id`, `rental_amount`, `insurance_amount`, `guarantee_deposit_amount` y `total_amount` (RF-001, RF-007). | [ ] | |
| 3 | Verificar el monto de alquiler | Comparar `rental_amount` con `350000.00 × 3 días inclusivos` | `2027-02-10` → `2027-02-12` | `1050000.00`; el alquiler es bruto y no incluye seguro, depósito ni comisión (RF-003). | [ ] | |
| 4 | Verificar el seguro náutico | Comparar `insurance_amount` con `15000.00 × 4` | 4 pasajeros | `60000.00` (RF-004). | [ ] | |
| 5 | Verificar el depósito de garantía | Comparar `guarantee_deposit_amount` con el 10 % de la tarifa diaria registrada | `350000.00 × 10 %` | `35000.00`; no se multiplica por días, pasajeros ni daño (RF-005). | [ ] | |
| 6 | Verificar el valor total | Sumar los tres conceptos y comparar con `total_amount` | N/A | `1145000.00` = `1050000.00 + 60000.00 + 35000.00` (RF-006, CE-001). | [ ] | |
| 7 | Verificar la persistencia y el congelamiento | `SELECT rental_amount, insurance_amount, deposit_amount, total_amount, frozen_parameters_id, calculated_at FROM reservation_information WHERE reservation_id = 'c0a80102-2222-4b3c-8d4e-00000000e201'` | N/A | Los cuatro montos escritos juntos, `frozen_parameters_id` no nulo y `calculated_at` con el instante del cálculo (RF-008). | [ ] | |
| 8 | Verificar que no se recalcula la tarifa | Journal del stub de Flota y logs de la app | N/A | No hay nueva consulta a Flota ni re-evaluación de la tarifa dinámica; se usa la tarifa registrada (CE-002). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin información registrada | Solicitar el valor de un UUID que no pasó por UC03 | `00000000-0000-4000-8000-000000000000` | `404 RESERVATION_INFO_NOT_FOUND` con `retryable: true`, sin montos parciales (RF-009). | [ ] | |
| 2 | Información registrada incompleta | Dejar la reserva sin tarifa base (fallo previo de UC03) y solicitar el valor | fila con `base_rate` nula | `422 RESERVATION_INFO_INCOMPLETE`, sin cálculo parcial (RNF-003). | [ ] | |
| 3 | Sin versión vigente de parámetros (primer cálculo) | BD sin `financial_parameters` y reserva sin `frozen_parameters_id` | N/A | `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`; no se calcula ni el seguro ni un total parcial (casos extremos). | [ ] | |
| 4 | Identificador de reserva inválido | `POST /api/v1/reservations/no-es-uuid/calculated-value` | texto libre | `400 VALIDATION_ERROR` (contrato §5). | [ ] | |
| 5 | Solicitud repetida | Ejecutar dos veces el paso 2 de la Section 1 | misma reserva | Mismo desglose y actualización idempotente, sin duplicar montos (casos extremos). | [ ] | |
| 6 | Nueva versión de parámetros publicada | Guardar nuevos parámetros en UC11 y repetir la solicitud | `insurance_fee_per_passenger` distinto | El desglose **no cambia**: el recálculo resuelve la versión referenciada en `frozen_parameters_id` (RF-008). | [ ] | |
| 7 | Reenvío de la información de la reserva | Republicar el evento de UC03 con otra fecha de fin y volver a pedir el valor | `end_date = 2027-02-13` | UC03 invalida montos y `frozen_parameters_id`; el nuevo cálculo se hace con la versión vigente (RF-008, casos extremos de UC03). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `start_date == end_date` comprende 1 día de alquiler; del 10 al 12 comprenden 3 días (RF-003).
* **EC-002** : El depósito se calcula sobre la tarifa diaria registrada **con la tarifa dinámica ya resuelta** (D-29) y nunca se multiplica por la duración.
* **EC-003** : Los montos internos usan `BigDecimal` con 4 decimales; la respuesta y los registros redondean `HALF_UP` a 2 decimales (RNF-002, convención §3.1).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
