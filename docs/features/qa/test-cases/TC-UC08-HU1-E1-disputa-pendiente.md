### Test Case Specification: Disputa de garantía · HU1 · E1 Disputa pendiente
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md](../../008-brindar-informacion-de-disputa-de-garantia/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC08-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con un depósito cobrado y registrado, la notificación `PENDING` de una disputa de garantía se registra de forma idempotente y **no** dispara reembolso ni liquidación del depósito.

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
* UC08 no expone REST: se verifica por la cola consumidora, la base de datos y las colas internas de comando. No se espera respuesta al productor.

##### Dependencies & Assumptions
* **TC-UC07-HU3-E1 debe haber pasado**: la reserva está `COMPLETED` y UC07 abrió el seguimiento del depósito en `HELD`.
* **TC-UC05-HU1-E1 debe haber pasado**: existe `charge_record` del cobro capturado con el depósito.
* RabbitMQ arriba, cola `finance.guarantee-dispute.v1` y su DLQ declaradas.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con depósito cobrado de `30000.00` (valores ilustrativos del contrato UC04; ajustar a la versión vigente del entorno); `deposit_disposition.disposition = 'HELD'`; sin eventos de disputa previos.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro de una disputa `PENDING` sin movimiento de dinero
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el depósito cobrado y su seguimiento | `SELECT ci.status, cr.deposit_amount FROM charge_intent ci JOIN charge_record cr ON cr.charge_intent_id = ci.id WHERE ci.reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10';` y `SELECT reservation_id, disposition, completed_at FROM deposit_disposition;` | depósito `30000.00`, cobro `CAPTURED` | Existe el depósito cobrado y registrado, y el seguimiento en `HELD` (RF-005, RF-006). | [x] | |
| 2 | Publicar la disputa `PENDING` | Publicar en `seashare.reservations` con routing key `reservation.dispute.updated` (consola RabbitMQ o `rabbitmqadmin publish`) | `{"reservation_id":"b7d0e2a1-…","dispute_id":"d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a","status":"PENDING","status_changed_at":"2026-12-23T10:00:00Z","event_key":"v1.0-4a5b6c"}` | El mensaje llega a `finance.guarantee-dispute.v1` y se confirma (`ack`); no hay respuesta al productor (RF-009). | [x] | `Content-Type: application/json`, `Message-Id` obligatorio. |
| 3 | Verificar el registro del evento | `SELECT reservation_id, dispute_id, event_key, status, status_changed_at, processed_at FROM dispute_event_log;` | `event_key = 'v1.0-4a5b6c'` | Fila con `status = 'PENDING'`, la marca `status_changed_at` recibida y `processed_at` no nulo (RF-001, RF-008). | [x] | |
| 4 | Verificar que no se movió el depósito | `SELECT reservation_id, disposition, dispute_id FROM deposit_disposition;` y `SELECT count(*) FROM refund_intent;` / `SELECT count(*) FROM settlement_intent;` | misma reserva | `disposition` sigue en `HELD` y no aparece intención de reembolso ni de liquidación (RF-004, CE-002). | [x] | |
| 5 | Verificar que no se emitió comando interno | Inspeccionar `finance.refund-command.v1` y `finance.settlement-command.v1` (consola RabbitMQ o `outbox_message`) | N/A | Sin mensajes nuevos para la reserva; `PENDING` no genera operación monetaria (RF-004, CE-002). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Estado de disputa desconocido | Publicar `status = "PARTIALLY_RELEASED"` (sub-resultado de retención parcial) | mismo payload, estado inválido | `ack` + registro en `operational_failure`; cero operaciones monetarias (RF-002, RF-010, CE-001, CE-006). | [x] | Equivale a E2 del catálogo, en canal de cola. |
| 2 | Mensaje sin campo obligatorio | Publicar sin `event_key` o sin `status_changed_at` | payload incompleto | `ack` + fallo registrado; no se persiste el evento ni se delega (RNF-001, README §4.4). | [x] | |
| 3 | Mensaje con datos no permitidos | Publicar el payload agregando `amount`, `retained_amount` y `reason` | campos extra | Los campos extra no se mapean ni persisten; ningún monto entra a la operación (RF-001, RF-007, CE-004). | [x] | El DTO de UC08 es estricto. |
| 4 | `PENDING` repetido | Reenviar el mismo mensaje (mismo `event_key`) | mismo payload | `ack` sin operación adicional y sin duplicar la fila de `dispute_event_log` (RF-008, CE-005). | [x] | |
| 5 | `PENDING` tardío tras una resolución | Publicar `PENDING` después de un `REJECTED`/`COMPLETED` ya procesado | `event_key` nuevo, estado `PENDING` | Se registra el evento sin revertir el desenlace aplicado ni mover dinero (RF-004, RF-008, CE-005). | [x] | |
| 6 | Falla transitoria de base de datos | Detener PostgreSQL y publicar el mensaje | N/A | `nack` con 5 intentos y backoff exponencial base 1 s; al agotarlos, DLQ y registro en `operational_failure` (RNF-003, README §4.4). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Varias notificaciones `PENDING` seguidas producen cero reembolsos y cero liquidaciones del depósito (CE-002).
* **EC-002** : El sistema no calcula ni espera la ventana de 24 h: no hay cron, temporizador ni tarea en segundo plano (RF-009).
* **EC-003** : `status_changed_at` es el instante de la transición, no el de recepción; una entrega tardía conserva la marca original.
* **EC-004** : El canal es unidireccional: no se publica respuesta de negocio al Sistema de Reservas y Operaciones (contrato §4, RF-009).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
