### Test Case Specification: Estado de la reserva · HU3 · E1 Finalización única de la reserva
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `COMPLETED`, el sistema solicita **una única** liquidación estándar del alquiler al propietario (alquiler bruto − comisión − seguro), abre el seguimiento del depósito en `HELD` y **no** ejecuta ninguna operación monetaria ni decisión sobre la garantía.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../../docs/features/007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC10 | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Plan UC08 | Plan Document | [plan.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/plan.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; se verifica la delegación, la intención de dispersión y el seguimiento del depósito, no la confirmación externa.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1** y **TC-UC04-HU1-E1** deben haber pasado (información y valor total calculados, con versión de parámetros referenciada).
* **TC-UC05-HU2-E1 debe haber pasado**: cobro original en `CAPTURED` con `charge_record` y desglose.
* **TC-UC11-HU4-E1 debe haber pasado**: existe una versión de parámetros financieros con el porcentaje de comisión vigente.
* RabbitMQ arriba; colas `finance.reservation-status.v1` y `finance.settlement-command.v1` disponibles.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos ilustrativos (ajustar a la versión vigente del entorno): alquiler bruto `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; comisión de ejemplo `15.00 %` = `135000.00`; sin `settlement_intent`, `refund_intent` ni `deposit_disposition`.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Liquidación estándar y depósito retenido
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql` sobre `settlement_intent`, `refund_intent`, `deposit_disposition` y `reservation_status_log` | misma reserva | Sin liquidaciones, reembolsos ni desenlace del depósito. | [ ] | |
| 2 | Notificar la finalización | Publicar `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"COMPLETED","status_changed_at":"2026-12-10T09:00:00Z"}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001). | [ ] | |
| 3 | Verificar la identidad de la operación | `SELECT status, operation_type, idempotency_key, processed_at FROM reservation_status_log` | misma reserva | **Una** fila `COMPLETED` / `SETTLEMENT` con clave `<reservation_id>:COMPLETED` y `processed_at` no nulo (RF-009A). | [ ] | |
| 4 | Verificar la liquidación estándar | `SELECT trigger, scope, amount, commission_amount, deposit_amount, status FROM settlement_intent` | misma reserva | `COMPLETED` / `STANDARD`; monto = `900000.00` − comisión − `60000.00` (con 15 %: `705000.00`), comisión `135000.00`, depósito asociado `30000.00`, estado `PENDING_SEND` (o `IN_PROCESS`) (RF-006; UC10 plan §Reglas 4). | [ ] | |
| 5 | Verificar la apertura del depósito | `SELECT disposition, completed_at, dispute_id, dispute_status FROM deposit_disposition` | misma reserva | **Una** fila con `HELD`, `completed_at` = `2026-12-10T09:00:00Z` (la marca del evento, no la de recepción), `dispute_id` y `dispute_status` nulos (RF-006, RF-007). | [ ] | |
| 6 | Verificar que no hay operación monetaria sobre el depósito | `SELECT count(*) FROM refund_intent` y `SELECT count(*) FROM settlement_intent WHERE scope = 'DEPOSIT'` | misma reserva | Cero reembolsos y cero liquidaciones de depósito: la apertura en `HELD` es solo seguimiento (RF-007, CE-003, CE-006). | [ ] | |
| 7 | Verificar el comando interno | Consola RabbitMQ u `outbox_message` | enrutado `settlement.command` | Un único mensaje en `finance.settlement-command.v1`; ningún `refund.command` para la reserva. | [ ] | |
| 8 | Verificar que no se decide sobre la garantía | Revisar `deposit_disposition` y `dispute_event_log` | misma reserva | El depósito permanece asociado y pendiente de resolución; no hay decisión de daño ni destino final (CE-006). | [ ] | |
| 9 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | Sin respuesta al Sistema de Reservas y Operaciones (RF-009). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Falta el monto de alquiler o el seguro | Publicar `COMPLETED` sobre una reserva con cálculo incompleto | sin alquiler o sin seguro | `ack` y `operational_failure`; sin liquidación ni apertura del depósito, sin montos asumidos (RNF-003, CE-004; caso extremo del spec). | [ ] | |
| 2 | Faltan la comisión o los parámetros referenciados | Publicar sobre una reserva sin versión de parámetros utilizable | sin comisión | UC10 devuelve `FAILED`; se revierte la transacción completa: ni liquidación ni `deposit_disposition`, y se registra el fallo (UC10 plan §Reglas 9). | [ ] | |
| 3 | Reenvío de `COMPLETED` | Publicar dos veces el estado con marcas distintas | `09:00:00Z` y `09:30:00Z` | Una sola fila de log, una sola liquidación y el depósito sigue en `HELD`: no se liquida dos veces (RF-009A, CE-005). | [ ] | |
| 4 | El depósito ya tenía otro desenlace | Publicar `COMPLETED` cuando `deposit_disposition.disposition` ya es `REFUND_REQUESTED` | depósito ya resuelto | La liquidación estándar se registra; `deposit_disposition` conserva `REFUND_REQUESTED` y la inconsistencia queda en `operational_failure` (RF-009B; contrato §3.8). | [ ] | |
| 5 | No llega la resolución de la disputa | Esperar o simular el paso de la ventana de 24 h sin notificación de UC08 | N/A | El depósito sigue `HELD`; Finanzas no ejecuta temporizadores ni cron jobs (CE-006; caso extremo del spec). | [ ] | |
| 6 | Falla transitoria de base de datos | Detener PostgreSQL y publicar `COMPLETED` | N/A | `nack`; 5 intentos/backoff base 1 s; DLQ y `operational_failure` al agotarlos (equivale a E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La liquidación estándar descuenta el seguro, además de la comisión: `alquiler bruto − comisión − seguro` (RF-006; UC10 p. 4).
* **EC-002** : Una comisión configurada en `0.00 %` es válida y produce una comisión `0.00`, no la omisión del registro (UC10).
* **EC-003** : El depósito se conserva asociado a la reserva y no se reembolsa ni se liquida por `COMPLETED` (RF-007, CE-003).
* **EC-004** : Montos en `BigDecimal` con 4 decimales internos → salida redondeada `HALF_UP` a 2 decimales (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
