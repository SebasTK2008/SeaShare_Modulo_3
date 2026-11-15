### Test Case Specification: Disputa de garantía · HU1 · E4 Ausencia de disputa al vencer la ventana de 24 h
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md](../../008-brindar-informacion-de-disputa-de-garantia/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC08-HU1-E4
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al vencer la ventana de 24 h sin reclamo, el sistema **no** actúa por iniciativa propia y solo reembolsa el depósito cuando el Sistema de Reservas y Operaciones notifica `REJECTED`; el tratamiento es idéntico al de una disputa rechazada y no exige atributo de origen.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC08 | Specification Document | [spec.md](../../008-brindar-informacion-de-disputa-de-garantia/spec.md) |
| Implementation Plan UC08 | Plan Document | [plan.md](../../008-brindar-informacion-de-disputa-de-garantia/plan.md) |
| Contrato evento de disputa | Events contract | [UC08-disputa-garantia.md](../../../technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No se esperan 24 h reales: la antigüedad se siembra en el entorno de QA modificando `deposit_disposition.completed_at`; el paso del tiempo lo gobierna el Sistema de Reservas y Operaciones.

##### Dependencies & Assumptions
* **TC-UC07-HU3-E1 debe haber pasado**: reserva `COMPLETED` con depósito en seguimiento `HELD`.
* **TC-UC08-HU1-E2 debe haber pasado**: se compara el resultado financiero con el de una disputa rechazada.
* Cobro original `CAPTURED` con `charge_record`.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` completada hace más de 24 h, depósito cobrado de `30000.00` (ilustrativo; ajustar a la versión vigente del entorno), `deposit_disposition.disposition = 'HELD'` y **sin** eventos en `dispute_event_log`.
* **Environment** : staging local (`docker compose --profile app`), `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Silencio del sistema y `REJECTED` por vencimiento
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Preparar la ventana vencida | `UPDATE deposit_disposition SET completed_at = now() - interval '25 hours' WHERE reservation_id = 'b7d0e2a1-…';` | 25 h de antigüedad | El depósito sigue en `HELD` y no existe notificación de disputa (punto de partida del escenario). | [x] | Solo preparación de datos. |
| 2 | Verificar que no hay acción autónoma | Consultar `SELECT count(*) FROM refund_intent;`, `SELECT count(*) FROM settlement_intent;` y `SELECT disposition FROM deposit_disposition;` | N/A | Sin reembolso, sin liquidación y `disposition` sin cambios: UC08 no ejecuta cron, temporizador ni tarea de fondo (RF-009). | [x] | |
| 3 | Verificar la ausencia de planificador | Revisar `Uc08ArchitectureTest` y la configuración de la aplicación (sin `@Scheduled` de UC08, sin fila de `shedlock` para este caso de uso) | N/A | UC08 solo actúa como consumidor de notificaciones (RF-009). | [x] | |
| 4 | Publicar el `REJECTED` de vencimiento | Publicar en `seashare.reservations` / `reservation.dispute.updated` | `{"reservation_id":"b7d0e2a1-…","dispute_id":"d8e1f2a3-…","status":"REJECTED","status_changed_at":"2026-12-24T10:00:00Z","event_key":"v1.0-cierre24h"}` | `ack`; el mensaje no incluye motivo, origen ni monto (RF-009A, RF-001). | [x] | |
| 5 | Verificar el registro del cierre | `SELECT status, status_changed_at, processed_at FROM dispute_event_log WHERE reservation_id = 'b7d0e2a1-…';` | `event_key = 'v1.0-cierre24h'` | Fila `REJECTED` con `processed_at` no nulo y sin atributo de origen persistido (RF-009A, RF-003). | [x] | |
| 6 | Verificar el reembolso total del depósito | `SELECT disposition FROM deposit_disposition;` y `SELECT trigger, scope, amount, status FROM refund_intent;` | `30000.00` | `disposition = 'REFUND_REQUESTED'`; `trigger = 'DISPUTE_REJECTED'`, `scope = 'DEPOSIT_ONLY'`, `amount = 30000.00` (RF-009A, RF-005, CE-001). | [x] | |
| 7 | Comparar con la disputa rechazada | Contrastar montos, `scope`, `trigger` y desenlace con los obtenidos en TC-UC08-HU1-E2 | misma reserva/importes | Resultado financiero idéntico: no existe diferencia de tratamiento entre ambos orígenes (CE-005). | [x] | |
| 8 | Verificar que no hay liquidación | `SELECT count(*) FROM settlement_intent;` y `SELECT count(*) FROM refund_record;` | N/A | Sin liquidación y sin registro definitivo, porque la Pasarela aún no confirmó (RF-005, RF-009). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Notificación sin `event_key` | Publicar el cierre de 24 h sin clave idempotente | payload incompleto | `ack` + fallo registrado; sin reembolso (RNF-001, README §4.4). | [x] | |
| 2 | `REJECTED` repetido por reenvío | Reenviar el mismo `event_key` | mismo payload | `ack` sin segunda intención ni segundo reembolso (RF-008, CE-005). | [x] | |
| 3 | Nuevo `event_key` para la misma disputa | Publicar otro `REJECTED` con `event_key` distinto | `event_key = "v1.0-cierre24h-b"` | Sin segunda operación; se registra la inconsistencia para conciliación (RF-008). | [x] | |
| 4 | `COMPLETED` posterior al cierre | Publicar `COMPLETED` después de solicitado el reembolso | `status = "COMPLETED"` | No se liquida el depósito; se conserva el desenlace y se registra la inconsistencia (RF-008, RF-010). | [x] | |
| 5 | Reserva sin depósito registrado | Publicar el `REJECTED` de vencimiento sin depósito cobrado | reserva sin depósito | `ack` + `operational_failure`; sin reembolso ni monto asumido (caso extremo del SPEC). | [x] | |
| 6 | Estado desconocido | Publicar `status = "EXPIRED_DISPUTE"` | estado inválido | `ack` + `operational_failure`; cero operaciones monetarias (RF-002, CE-001). | [x] | Equivale a E2 en canal de cola. |

##### Edge Cases & Boundary Conditions
* **EC-001** : La ventana de 24 h la calcula el Sistema de Reservas y Operaciones; Finanzas no la infiere ni la vigila (RF-009).
* **EC-002** : El cierre por ausencia de reclamo no requiere un campo de origen que lo distinga de un reclamo rechazado (RF-009A, CE-005).
* **EC-003** : Un `PENDING` recibido después del cierre no revierte el reembolso ya solicitado (RF-008).
* **EC-004** : El depósito se reembolsa al 100 %; no hay retención parcial por "falta de reclamo" (RF-010, CE-006).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
