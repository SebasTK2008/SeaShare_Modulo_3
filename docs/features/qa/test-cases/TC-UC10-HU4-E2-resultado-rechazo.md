### Test Case Specification: Liquidar fondos de alquiler · HU4 · E2 El libro interno no confirma la liquidación
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU4-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando el libro interno no confirma una liquidación (rechazo de negocio, fallo de comunicación o ausencia de referencia externa), el sistema actualiza la `IntenciónDeDispersión` sin marcarla como completada, **no** crea `RegistroDeDispersión` ni `RegistroDeComisión`, y deja el resultado disponible internamente para conciliación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 (D-UC10-10, D-UC10-18) | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato del libro interno de liquidación | Internal contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El adaptador simulado real **siempre** devuelve `approved`; los caminos de rechazo y error de comunicación se ejercitan con dobles de `SettlementGatewayPort` en pruebas unitarias, no en el entorno QA. Este caso documenta el comportamiento esperado y su cobertura automatizada.

##### Dependencies & Assumptions
* Existe una `settlement_intent` con montos y desglose persistidos, sin registro confirmado previo.
* El puerto `SettlementGatewayPort` puede sustituirse por un doble que lance `GatewayBusinessRejectionException` o `GatewayCommunicationException`.

##### Preconditions & Default Setup
* **Initial State** : `settlement_intent` de la reserva `R-200` = `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` (alcance `STANDARD`) en estado `PENDING_SEND`, `idempotency_key` UUID v4. Montos de ejemplo: alquiler bruto `360.00`, seguro `200.00`, depósito `12.00`, comisión `54.00`, monto solicitado `106.00`.
* **Environment** : pruebas unitarias de `SendSettlementToGatewayService` y `ReconcileSettlementIntentsService`; staging local para verificar la conciliación.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Resultado no exitoso sin registro financiero
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la solicitud previa | `SELECT` sobre `settlement_intent` | `reservation_id = 0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Intención en `PENDING_SEND`, con monto solicitado y sin filas en `settlement_record` para esa intención (RF-009). | [ ] | |
| 2 | Simular un rechazo de negocio | Doble de `SettlementGatewayPort` que lanza `GatewayBusinessRejectionException` | N/A | Intención `REJECTED` con detalle; sin `settlement_record` ni `commission_record` (RF-012, CE-005). | [ ] | Prueba unitaria. |
| 3 | Simular un fallo de comunicación | Doble que lanza `GatewayCommunicationException` | N/A | Intención `COMMUNICATION_ERROR` con detalle; el servicio propaga la excepción para que la cola reintente; sin registros exitosos (RNF-003). | [ ] | Prueba unitaria. |
| 4 | Simular un resultado sin referencia | Doble que devuelve `externalId` vacío | N/A | Intención `COMMUNICATION_ERROR`, fallo en `operational_failure`; sin registros exitosos (RNF-003). | [ ] | Prueba unitaria. |
| 5 | Verificar que no se creó un registro exitoso | `SELECT count(*)` sobre `settlement_record` y `commission_record` por la intención | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | **Cero** filas en ambas tablas: un fallo del libro interno nunca se registra como liquidación exitosa (RF-012, CE-005). | [ ] | |
| 6 | Verificar que no se reporta como liquidado | `GET /api/v1/financial-records?reservation_id=0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | La reserva no muestra un registro de liquidación confirmado; el resultado fallido queda disponible internamente para conciliación (RF-012). | [ ] | |
| 7 | Verificar la conciliación interna | Dejar la intención en `IN_PROCESS` sin referencia y ejecutar el ciclo de conciliación | N/A | Se registra el fallo una sola vez; no se asume éxito ni se crea registro (D-UC10-10). | [ ] | `GatewayReconciliationJob`. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Intención aún en proceso | Dejar la intención en `IN_PROCESS` con referencia | N/A | La conciliación reenvía el resultado simulado determinista y repara el registro si falta; no se asume un éxito sin referencia (D-UC10-10). | [ ] | |
| 2 | Intención en `PENDING_SEND` | Ejecutar la conciliación sin que el worker haya procesado la intención | N/A | Se republica el comando; no se crea ningún registro por la mera conciliación (D-UC10-10). | [ ] | |
| 3 | Estado terminal repetido | Reprocesar una intención `COMPLETED` con su registro | N/A | La conciliación omite la intención; no se duplica nada (D-UC10-10). | [ ] | |
| 4 | Modo de liquidación inválido | Arrancar con `GATEWAY_SETTLEMENT_MODE` distinto de `simulated` | configuración inválida | La aplicación no arranca (D-UC10-18). | [ ] | |
| 5 | Fallo del libro interno en producción | N/A | N/A | No reproducible: el adaptador simulado real no lanza excepciones ni devuelve referencias vacías; su cobertura está en pruebas unitarias (D-UC10-18). | [ ] | Limitación declarada. |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un rechazo de negocio deja la intención en `REJECTED` y no es reintentable; no crea registros (contrato de liquidación §5).
* **EC-002** : Un fallo de comunicación deja la intención en `COMMUNICATION_ERROR` y se reintenta; no se confunde con rechazo de negocio (RNF-003, CE-004).
* **EC-003** : Un resultado no exitoso no borra ni modifica un `RegistroDeDispersión` ya confirmado de la misma reserva por otra operación (inmutabilidad, RF-010).
* **EC-004** : El detalle técnico expuesto en `4xx` es accionable; en `5xx` es genérico, sin trazas ni SQL (README §4.3).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
