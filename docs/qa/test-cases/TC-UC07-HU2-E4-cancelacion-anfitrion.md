### Test Case Specification: Estado de la reserva · HU2 · E4 Cancelación por anfitrión
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E4
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `CANCELLED_BY_HOST`, el sistema solicita la liberación o el reembolso del **100 % del valor pagado** al arrendatario y **no** ejecuta ninguna dispersión de fondos al propietario.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../../docs/features/007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC09 | Plan Document | [plan.md](../../../docs/features/009-reembolsar-dinero-arrendatario/plan.md) |
| Contrato pasarela — reembolso | External contract | [pasarela-comando-reembolso.md](../../../docs/technical-plan/contracts/external/pasarela-comando-reembolso.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; el TC verifica la delegación y la intención de reembolso, no el envío externo.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1** y **TC-UC04-HU1-E1** deben haber pasado (información y valor total calculados).
* **TC-UC05-HU2-E1 debe haber pasado**: cobro original en `CAPTURED` con `charge_record` y desglose.
* RabbitMQ arriba; colas `finance.reservation-status.v1` y `finance.refund-command.v1` disponibles.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos ilustrativos (ajustar al entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; cobro original en `CAPTURED`; sin intenciones ni log del estado.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso total sin dispersión al propietario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql` sobre `refund_intent`, `settlement_intent` y `reservation_status_log` | misma reserva | Sin reembolsos, liquidaciones ni log para `CANCELLED_BY_HOST`. | [ ] | |
| 2 | Notificar la cancelación por anfitrión | Publicar `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-04T11:05:00Z"}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001). | [ ] | |
| 3 | Verificar la identidad de la operación | `SELECT status, operation_type, idempotency_key, processed_at FROM reservation_status_log` | misma reserva | **Una** fila `CANCELLED_BY_HOST` / `REFUND` con clave `<reservation_id>:CANCELLED_BY_HOST` y `processed_at` no nulo (RF-009A). | [ ] | |
| 4 | Verificar la intención de reembolso | `SELECT trigger, scope, amount, status, charge_intent_id FROM refund_intent` | misma reserva | `CANCELLED_BY_HOST` / `FULL`, monto `990000.00`, estado `PENDING_SEND`, asociada al cobro original (RF-005A). | [ ] | |
| 5 | Verificar que **no** hay dispersión | `SELECT count(*) FROM settlement_intent WHERE reservation_id = ...` | misma reserva | Cero filas: la cancelación por anfitrión no liquida fondos al propietario (RF-005A). | [ ] | |
| 6 | Verificar el comando interno | Consola RabbitMQ u `outbox_message` | enrutado `refund.command` | Un único mensaje en `finance.refund-command.v1`; ningún `settlement.command`. | [ ] | |
| 7 | Verificar el resultado pendiente | `SELECT status FROM refund_intent` y `SELECT count(*) FROM refund_record` | misma reserva | La intención queda en curso y UC07 no asume el éxito: el `refund_record` solo existe tras la confirmación de la Pasarela (spec HU2 E4). | [ ] | |
| 8 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | Sin respuesta al Sistema de Reservas y Operaciones (RF-009). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin valor total calculado | Publicar `CANCELLED_BY_HOST` sobre una reserva sin cálculo | sin montos | `ack` y `operational_failure`; sin reembolso ni dispersión (RNF-003, CE-004). | [ ] | |
| 2 | Reserva sin registro financiero | Publicar para un UUID no registrado por UC03 | UUID nuevo | `ack` y `operational_failure` (`RESERVATION_INFO_NOT_FOUND`); sin delegación. | [ ] | |
| 3 | Sin cobro original aprobado | Publicar sin un `charge_intent` en `CAPTURED` | sin cobro | UC09 devuelve `FAILED`; se revierte la transacción completa, no queda log ni intención y se registra el fallo (plan §Reglas 8). | [ ] | |
| 4 | Reenvío del mismo estado terminal | Publicar dos veces `CANCELLED_BY_HOST` con marcas distintas | `11:05:00Z` y `11:10:00Z` | Una sola fila de log y un solo `refund_intent` (clave estable, RF-009A). | [ ] | |
| 5 | Estado con guion o minúsculas | Publicar `status=cancelled-by-host` | valor inválido | `ack` e inconsistencia en `operational_failure`; sin operación (RF-001). | [ ] | |
| 6 | Falla transitoria de base de datos | Detener PostgreSQL y publicar la cancelación | N/A | `nack`; 5 intentos/backoff base 1 s; DLQ y `operational_failure` al agotarlos (equivale a E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La cancelación por anfitrión reembolsa el 100 % sin penalidad, independientemente del aviso; el propietario no recibe compensación (RF-005A).
* **EC-002** : El reembolso incluye alquiler, seguro y depósito en un único monto (`990000.00`).
* **EC-003** : El desenlace del depósito (`REFUND_REQUESTED`) lo aplica UC09/UC08; UC07 no escribe `deposit_disposition` en cancelaciones.
* **EC-004** : Si la Pasarela no confirma, el resultado queda registrado como no concluyente; UC07 no asume éxito (RF-010).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
