### Test Case Specification: Liquidar fondos de alquiler · HU5 · E1 Registro de la comisión aplicada en una liquidación estándar
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU5-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al confirmarse exitosamente la liquidación estándar de una reserva `completada`, el sistema crea de forma **atómica** un `RegistroDeDispersión` y un `RegistroDeComisión` inmutables, con el monto de comisión conservado en la `IntenciónDeDispersión`, relacionados con la reserva, el propietario, la embarcación, la intención y la operación de dispersión, sin recalcularlo ni duplicarlo.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../docs/technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; la confirmación se provoca con la notificación `type=merchant_order` del contrato de webhook.

##### Dependencies & Assumptions
* **TC-UC10-HU4-E1 debe haber pasado** (camino de confirmación exitosa operativo).
* La reserva referencia una versión de parámetros (`frozen_parameters_id`) con porcentaje de comisión configurado.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-200` = `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` `completada`; `settlement_intent` de alcance `STANDARD` en `IN_PROCESS` con `commission_amount = 54.00`, `external_reference = mp_disp_QA_03` y desglose interno. Montos de ejemplo (ajustar a la versión vigente del entorno): alquiler bruto `360.00`, seguro `200.00`, depósito `12.00`, monto solicitado `106.00`, comisión `15 %`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Creación atómica del registro de comisión
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la comisión calculada en la intención | `SELECT` sobre `settlement_intent` | `reservation_id = 0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | `commission_amount` conservado como dato operativo no inmutable, resuelto desde `frozen_parameters_id` (RF-004, RF-014). | [ ] | |
| 2 | Confirmar que aún no existe registro de comisión | `SELECT` sobre `commission_record` por la intención | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Cero filas antes de la confirmación externa (RF-014). | [ ] | |
| 3 | Simular la confirmación exitosa de la Pasarela | `POST /api/v1/webhook/gateway?data.id=mp_disp_QA_03&type=merchant_order` con `x-signature` válida, seguido de la consulta de detalle en el simulador | `status = approved` | `200 OK` al webhook y consulta `GET /v1/advanced_payments/{id}` con el estado exitoso (contract §3.4). | [ ] | |
| 4 | Verificar la intención confirmada | `SELECT` sobre `settlement_intent` | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Estado `COMPLETED` con el detalle y la referencia externa (RF-010). | [ ] | |
| 5 | Verificar el registro de dispersión | `SELECT` sobre `settlement_record` por la intención | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Exactamente **una** fila con monto confirmado, `settlement_intent_id`, reserva, propietario, embarcación, alquiler bruto, seguro y depósito (RF-009A, RF-010, RF-015, RF-016). | [ ] | |
| 6 | Verificar el registro de comisión | `SELECT` sobre `commission_record` por la intención y el registro | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Exactamente **una** fila, inmutable, con el monto igual al `commission_amount` de la intención, fecha y hora de creación, reserva, propietario, embarcación, `settlement_intent_id` y vínculo con el `settlement_record` (RF-010, RF-014, CE-006). | [ ] | |
| 7 | Verificar la atomicidad | Forzar un fallo de persistencia del `commission_record` (restricción temporal en el entorno de prueba) y repetir la confirmación | N/A | No queda ni el `commission_record` ni un `settlement_record` huérfano: ambos se crean o ninguno; la operación se reintenta y no reporta éxito (RF-010, CE-006). | [ ] | |
| 8 | Verificar la ausencia de recálculo | Crear una nueva versión de parámetros con otro porcentaje de comisión y volver a consultar el registro | nueva versión vigente | El `commission_record` conserva el monto calculado con la versión referenciada por la reserva; no se recalcula ni se altera (RF-014, RF-007 del UC11). | [ ] | |
| 9 | Verificar la disponibilidad para consulta | `GET /api/v1/financial-records?reservation_id=0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | El registro de comisión aparece como métrica financiera independiente del pago al propietario (RF-011). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmación repetida | Reprocesar la misma notificación exitosa | mismo `data.id` | No se crea un segundo `commission_record` ni un segundo `settlement_record`; la repetición se registra para conciliación (caso extremo UC10, CE-006). | [ ] | |
| 2 | Dos entregas concurrentes del mismo resultado | Publicar dos mensajes del mismo `data_id` en `finance.gateway-notification.v1` | mismo payload | La transacción y el bloqueo de la intención garantizan un único registro de comisión (CE-006). | [ ] | |
| 3 | Comisión o parámetros ausentes en una liquidación estándar | Invocar la dispersión con `frozen_parameters_id` inexistente o sin comisión | reserva sin parámetros resolubles | Se registra el fallo interno con `FailureRecorderPort`, no se crea intención ni se llama a la Pasarela; sin cálculo parcial (RF-013). | [ ] | |
| 4 | Resultado rechazado | Simular confirmación con estado rechazado | `status = rejected` | No se crea `commission_record` ni `settlement_record`; solo se actualiza la intención (RF-012). | [ ] | |
| 5 | Intento de modificar el registro | `UPDATE`/`DELETE` directo sobre `commission_record` | N/A | La base rechaza la operación (inmutabilidad, disparadores de la migración). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Con comisión configurada en `0 %` en una liquidación estándar confirmada, se crea igualmente **un** `commission_record` con monto `0.00` (RF-014; el registro solo omite en cancelaciones y depósito).
* **EC-002** : El monto de la comisión no es atributo del `RegistroDeDispersión`; se consulta solo en `commission_record` (RF-014).
* **EC-003** : `BigDecimal` de 4 decimales internos → monto del registro y salidas REST redondeados `HALF_UP` a 2 decimales (RNF-002).
* **EC-004** : El seguro se descuenta del monto liquidado al propietario, pero no interviene en el monto de la comisión (RF-005, RF-014).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
