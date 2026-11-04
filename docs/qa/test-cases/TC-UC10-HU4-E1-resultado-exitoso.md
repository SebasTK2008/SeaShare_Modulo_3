### Test Case Specification: Liquidar fondos de alquiler · HU4 · E1 El libro interno confirma la liquidación como exitosa
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU4-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando el **libro interno simulado** devuelve `approved` para una solicitud de liquidación previamente enviada, el sistema actualiza la `IntenciónDeDispersión`, crea **un único** `RegistroDeDispersión` inmutable con monto, referencia determinista y desglose, y lo deja disponible para "Consultar registros financieros" y "Consultar informe financiero" sin duplicarlo ante reintentos.

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
* El libro interno es determinista y **siempre** devuelve `approved`; no existe notificación externa ni consulta de detalle. La confirmación se produce en el worker que consume `finance.settlement-command.v1`.

##### Dependencies & Assumptions
* **TC-UC10-HU2-E1 debe haber pasado**: existe una `settlement_intent` con montos y desglose persistidos.
* RabbitMQ arriba y outbox activo.

##### Preconditions & Default Setup
* **Initial State** : `settlement_intent` de la reserva `R-200` = `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` (alcance `STANDARD`) en estado `PENDING_SEND`, con `idempotency_key` UUID v4 y montos de ejemplo: alquiler bruto `360.00`, seguro `200.00`, depósito `12.00`, comisión `54.00`, monto solicitado `106.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro exitoso del resultado de la liquidación
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la solicitud previa | `SELECT` sobre `settlement_intent` | `reservation_id = 0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | La intención existe en `PENDING_SEND`, con monto solicitado, desglose interno, `gateway_capability = STANDARD_SETTLEMENT` e `idempotency_key` UUID v4 (RF-009, RF-010). | [ ] | |
| 2 | Procesar el comando interno | Esperar al worker de `finance.settlement-command.v1` o republicar el comando | `settlement_intent_id` de la intención | El worker invoca el libro interno simulado; no hay petición HTTP (D-UC10-18). | [ ] | |
| 3 | Verificar el resultado del libro interno | `SELECT status, external_reference, status_detail FROM settlement_intent` | `0f1e2d3c-…` | Estado `COMPLETED`, `external_reference = sim-settlement-<operationKey>` y `status_detail = simulated internal settlement` (D-UC10-18). | [ ] | |
| 4 | Verificar el registro inmutable | `SELECT` sobre `settlement_record` filtrando por la intención | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | **Una** fila con monto confirmado `106.00`, referencia externa, `settlement_intent_id`, reserva, propietario, embarcación, alquiler bruto, seguro aplicado y depósito asociado, con `created_at` (RF-009A, RF-010, RF-015, RF-016). | [ ] | |
| 5 | Verificar la indisponibilidad de duplicados | Republicar el comando o esperar un ciclo de conciliación | misma intención | La referencia determinista y el estado terminal evitan un segundo registro; no aparece una segunda fila en `settlement_record` (D-UC10-18). | [ ] | |
| 6 | Verificar la disponibilidad para consulta | `GET /api/v1/financial-records?reservation_id=0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` (o vista `v_financial_records`) | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | El registro confirmado aparece como registro financiero de la reserva, con su monto y referencia externa (RF-011, CE-003). | [ ] | También debe verse en el informe de UC13. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Modo de liquidación inválido | Arrancar con `GATEWAY_SETTLEMENT_MODE` distinto de `simulated` | configuración inválida | La aplicación no arranca y no se procesa ninguna liquidación (D-UC10-18). | [ ] | |
| 2 | Resultado sin referencia externa | Configurar un doble del puerto que devuelva `externalId` vacío | N/A | La intención pasa a `COMMUNICATION_ERROR`, se registra en `operational_failure` y no se crea registro (RNF-003). | [ ] | Se cubre en pruebas unitarias. |
| 3 | Fallo de comunicación del libro interno | Provocar `GatewayCommunicationException` con un doble de prueba | N/A | Intención `COMMUNICATION_ERROR`, sin registro; la conciliación puede reintentar (RNF-003, CE-004). | [ ] | El adaptador simulado real no falla. |
| 4 | Intención sin confirmar | Dejar una intención en `IN_PROCESS` sin `external_reference` y ejecutar la conciliación | N/A | Se registra una sola vez en `operational_failure`; no se asume éxito ni se crea registro (D-UC10-18). | [ ] | |
| 5 | Comando duplicado | Publicar dos veces el mismo `settlement_intent_id` | mismo id | El segundo mensaje no altera el estado ni duplica el registro (idempotencia por estado terminal y referencia). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La referencia externa es determinista: `sim-settlement-<operationKey>`; un reintento produce el mismo valor (D-UC10-18).
* **EC-002** : El libro interno devuelve el monto de la intención; no se acepta un monto distinto del calculado (RF-009A, RF-016).
* **EC-003** : El depósito se conserva como componente del registro interno, aunque no exista transferencia real (RF-016; D-UC10-18).
* **EC-004** : `BigDecimal` de 4 decimales internos → monto del registro y salidas REST redondeados `HALF_UP` a 2 decimales (RNF-002, README §3.1).
* **EC-005** : El registro confirmado es inmutable: `UPDATE`/`DELETE` directos sobre `settlement_record` deben ser rechazados por la base (disparadores de la migración).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
