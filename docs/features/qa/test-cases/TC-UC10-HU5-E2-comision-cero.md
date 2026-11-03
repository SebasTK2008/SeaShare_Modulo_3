### Test Case Specification: Liquidar fondos de alquiler · HU5 · E2 Comisión registrada en cero para dispersiones por penalidad de cancelación
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../010-liquidar-fondos-alquiler/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU5-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, en una dispersión por penalidad de cancelación (`CANCELLED_MODERATE` o `CANCELLED_STRICT`), la `IntenciónDeDispersión` conserva `commission_amount = 0` y, al confirmarse el resultado, se registra el `RegistroDeDispersión` con comisión y seguro en cero **sin** crear ningún `RegistroDeComisión`, aunque exista un porcentaje de comisión configurado.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 | Plan Document | [plan.md](../../010-liquidar-fondos-alquiler/plan.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; la dispersión se dispara publicando el evento de UC07 y la confirmación con la notificación `type=merchant_order`.

##### Dependencies & Assumptions
* **TC-UC10-HU1-E1 y TC-UC10-HU4-E1 deben haber pasado** (dispersión por cancelación y camino de confirmación operativos).
* Existe configuración financiera vigente con porcentaje de comisión mayor que cero.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-300` = `1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d` `completada` con alquiler registrado `360.00`, seguro `200.00` y depósito `12.00`; sin `settlement_intent` para el estado de cancelación. Montos de ejemplo (ajustar a la versión vigente del entorno): compensación del 50 % = `180.00`; del 100 % = `360.00`.
* **Environment** : staging local (`docker compose --profile app`), consola RabbitMQ en `http://localhost:15672`, health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Dispersión por cancelación moderada sin registro de comisión
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Disparar la dispersión por cancelación moderada | Publicar en `seashare.reservations` (`reservation.status.changed` → `finance.reservation-status.v1`) | `{"reservation_id":"1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d","status":"CANCELLED_MODERATE","status_changed_at":"2026-11-02T10:00:00Z"}` | El mensaje se confirma; UC10 registra la `IntenciónDeDispersión` con alcance `CANCELLATION_COMPENSATION` (RF-001, RF-009). | [x] | |
| 2 | Verificar el cálculo de la compensación | `SELECT` sobre `settlement_intent` | `1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d` | Monto solicitado = `180.00` (50 % del alquiler registrado); `commission_amount = 0` y seguro no aplicado (RF-002, RF-007, RF-014). | [x] | |
| 3 | Verificar el envío a la Pasarela | Request journal del simulador (`POST /v1/advanced_payments/{id}/disbursements`) | `amount = 180.00`, `collector_id` del propietario | El payload solo lleva `disbursements[].amount`, `collector_id` y `external_reference`, con `X-Idempotency-Key` de la intención (RF-008, contrato de liquidación). | [x] | |
| 4 | Simular la confirmación exitosa | `POST /api/v1/webhook/gateway?data.id=<external_reference>&type=merchant_order` con `x-signature` válida | `status = approved` | `200 OK`; la intención pasa a `COMPLETED` y se crea **un** `RegistroDeDispersión` (RF-010). | [x] | |
| 5 | Verificar los componentes del registro | `SELECT` sobre `settlement_record` | `1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d` | `rental_gross_amount = 360.00`, `insurance_amount = 0`, `deposit_amount = 12.00`, `settlement_intent_id` y monto confirmado `180.00` (RF-016, RF-009A). | [x] | |
| 6 | Verificar la ausencia de registro de comisión | `SELECT` sobre `commission_record` por la intención y la reserva | `1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d` | **Cero** filas: no se genera `RegistroDeComisión` para penalidades de cancelación (RF-007, RF-014, CE-002). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Cancelación tardía (100 % del alquiler) | Publicar el estado `CANCELLED_STRICT` para una reserva con alquiler registrado | `status = CANCELLED_STRICT` | Intención con monto `360.00`, `commission_amount = 0`; tras la confirmación, registro con seguro y comisión en cero y sin `commission_record` (RF-003, RF-007). | [x] | |
| 2 | Liquidación del depósito por disputa | Publicar el evento de UC08 con `status = COMPLETED` | `dispute_id = <uuid>`, `status = COMPLETED` | Alcance `DEPOSIT`: monto = depósito completo (`12.00`), comisión y seguro en cero, sin `commission_record` (RF-006, RF-014). | [x] | |
| 3 | Reserva sin alquiler registrado | Disparar la cancelación para una reserva sin monto de alquiler interno | reserva sin alquiler | Se registra el fallo interno sin cálculo parcial y **sin** llamada a la Pasarela (RF-013). | [x] | |
| 4 | Confirmación repetida | Reprocesar la misma notificación exitosa | mismo `data.id` | No se duplica el `settlement_record` ni aparece un `commission_record` (idempotencia, caso extremo UC10). | [x] | |
| 5 | Resultado rechazado | Simular la confirmación con estado rechazado | `status = rejected` | Solo se actualiza la intención a `REJECTED`; sin registros exitosos ni comisión (RF-012). | [x] | |
| 6 | Intento de crear comisión desde la operación de penalidad | Buscar cualquier vínculo de comisión generado por la intención | `1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d` | `commission_record` continúa vacío aunque el porcentaje global vigente sea mayor que cero (RF-007, CE-002). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El depósito se conserva asociado en el registro de la penalidad aunque no forme parte del monto dispersado (RF-016).
* **EC-002** : El seguro aplicado es `0` en cancelaciones y en la liquidación del depósito; solo la liquidación estándar registra el seguro de la reserva (RF-016).
* **EC-003** : Cambiar el porcentaje de comisión en UC11 después de la operación no altera la intención ni crea comisiones retroactivas (RF-014).
* **EC-004** : La repetición del evento de cancelación y la de la notificación de la Pasarela no producen una segunda operación monetaria (idempotencia de UC07 y del webhook).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
