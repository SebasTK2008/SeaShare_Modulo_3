### Test Case Specification: Disputa de garantía · HU1 · E3 Disputa completada y liquidación al Propietario
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC08-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir `COMPLETED`, el sistema registra la resolución, aplica el desenlace `SETTLEMENT_REQUESTED` sobre el depósito y solicita a UC10 la liquidación **total** del depósito cobrado y registrado al Propietario, sin recibir montos ni instrucciones de pasarela desde el Sistema de Reservas y Operaciones.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC08 | Specification Document | [spec.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md) |
| Implementation Plan UC08 | Plan Document | [plan.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/plan.md) |
| Contrato evento de disputa | Events contract | [UC08-disputa-garantia.md](../../../docs/technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Contrato pasarela — comando de liquidación | External contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La liquidación usa un **libro interno simulado**: no hay journal de pasarela; se verifica por `settlement_intent` y `settlement_record`.
* UC10 es el ejecutor de la liquidación: aquí solo se verifica la solicitud emitida como consecuencia de UC08.

##### Dependencies & Assumptions
* **TC-UC08-HU1-E1 debe haber pasado** (ingesta de disputas operativa).
* **TC-UC10-HU3-E1 debe haber pasado** (liquidación total del depósito disponible en UC10).
* Cobro original `CAPTURED` con `charge_record`; `deposit_disposition.disposition = 'HELD'`.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con depósito cobrado de `30000.00` (ilustrativo; ajustar a la versión vigente del entorno), Propietario `owner_id` y embarcación `boat_id` registrados en `charge_record`.
* **Environment** : staging local (`docker compose --profile app`), `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: `COMPLETED` → liquidación total del depósito
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el depósito y el cobro original | `SELECT cr.deposit_amount, cr.owner_id, cr.boat_id FROM charge_record cr WHERE cr.reservation_id = 'b7d0e2a1-…';` | `30000.00`, `owner_id`, `boat_id` | Existen los datos internos que exige la liquidación; UC08 no los recibe por mensaje (RF-006, RF-007). | [ ] | |
| 2 | Publicar la disputa `COMPLETED` | Publicar en `seashare.reservations` / `reservation.dispute.updated` | `{"reservation_id":"b7d0e2a1-…","dispute_id":"d8e1f2a3-…","status":"COMPLETED","status_changed_at":"2026-12-23T10:00:00Z","event_key":"v1.0-4a5b6c"}` | `ack`; el mensaje lleva solo los cinco campos del contrato (RF-001, RF-009). | [ ] | |
| 3 | Verificar el registro de la resolución | `SELECT status, status_changed_at, processed_at FROM dispute_event_log;` | `event_key = 'v1.0-4a5b6c'` | Fila `COMPLETED` con `processed_at` no nulo (RF-002, RF-008). | [ ] | |
| 4 | Verificar el desenlace del depósito | `SELECT reservation_id, disposition, dispute_id FROM deposit_disposition;` | `dispute_id = 'd8e1f2a3-…'` | `disposition = 'SETTLEMENT_REQUESTED'` aplicado una sola vez por *compare-and-set* (RF-006, RF-008). | [ ] | |
| 5 | Verificar la intención de liquidación | `SELECT trigger, scope, amount, deposit_amount, charge_intent_id, dispute_id, status, gateway_capability, idempotency_key FROM settlement_intent;` | `trigger = 'DISPUTE_COMPLETED'` | `scope = 'DEPOSIT'`, `amount = 30000.00` (100 % del depósito), `dispute_id` y `charge_intent_id` del cobro original; `gateway_capability = 'STANDARD_SETTLEMENT'` e `idempotency_key` UUID v4 (RF-006, CE-001). | [ ] | |
| 6 | Verificar la entrega al libro interno | Inspeccionar `outbox_message` y `settlement_intent` tras el worker | `amount = 30000.00` | El comando interno referencia la intención; el libro simulado devuelve `sim-settlement-<operationKey>` y la intención pasa a `COMPLETED` (D-UC10-18). | [ ] | |
| 7 | Verificar exclusión mutua | `SELECT count(*) FROM refund_intent;` y `SELECT count(*) FROM refund_record;` | N/A | Sin intención ni registro de reembolso para la reserva: el depósito no se devuelve y se liquida (RF-008, RF-010, CE-003). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin depósito registrado | Publicar `COMPLETED` para una reserva sin depósito cobrado | reserva sin depósito | `ack` + fallo controlado en `operational_failure`; sin liquidación con monto asumido (RF-006, caso extremo del SPEC). | [ ] | |
| 2 | Cobro original no capturado | Publicar `COMPLETED` con cobro en `REJECTED`/`EXPIRED` | `charge_intent.status = 'EXPIRED'` | Fallo controlado; no se solicita liquidación (RF-006, RF-007). | [ ] | |
| 3 | `COMPLETED` repetido | Reenviar el mismo `event_key` | mismo payload | `ack` sin segunda intención ni segunda dispersión (RF-008, CE-005). | [ ] | |
| 4 | `REJECTED` después de `COMPLETED` | Publicar `REJECTED` tras la liquidación solicitada | `status = "REJECTED"` | No se reembolsa; se conserva `SETTLEMENT_REQUESTED` y se registra la inconsistencia (RF-008). | [ ] | |
| 5 | Estado desconocido o sub-resultado | Publicar `status = "COMPLETED_PARTIAL"` | estado inválido | `ack` + `operational_failure`; cero operaciones monetarias (RF-002, RF-010, CE-001, CE-006). | [ ] | |
| 6 | Mensaje con monto o instrucción de pasarela | Agregar `amount` y `gateway_instruction` al payload | campos extra | No se mapean ni persisten; la liquidación usa el depósito interno al 100 % (RF-007, CE-004). | [ ] | |
| 7 | Fallo al registrar la liquidación | Simular un fallo de persistencia al crear `settlement_record` | N/A | La intención no queda `COMPLETED`; el worker reintenta con la misma clave y, al agotar los intentos, DLQ y `operational_failure` (RNF-003). | [ ] | El libro interno no hace E/S externa (D-UC10-18). |

##### Edge Cases & Boundary Conditions
* **EC-001** : `COMPLETED` es un tratamiento total del 100 % del depósito; no admite liberación o retención parcial (RF-010, CE-006).
* **EC-002** : Un depósito tiene un único desenlace: `SETTLEMENT_REQUESTED` no puede convivir con `REFUND_REQUESTED` (RF-008).
* **EC-003** : La liquidación del depósito es independiente de la liquidación estándar del alquiler de UC10; aquí solo se verifica la parte del depósito.
* **EC-004** : UC08 no responde al productor y no expone endpoint REST para consultar la resolución (RF-009, contrato §4).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
