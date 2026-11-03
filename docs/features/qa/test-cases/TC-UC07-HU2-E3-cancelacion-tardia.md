### Test Case Specification: Estado de la reserva · HU2 · E3 Cancelación tardía (<24 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `CANCELLED_STRICT`, el sistema solicita la liquidación del **100 % del alquiler** al propietario y el reembolso del **100 % del depósito** al arrendatario, **sin** reembolsar el alquiler ni el seguro, y sin asumir que los fondos ya fueron recibidos.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC09 | Plan Document | [plan.md](../../009-reembolsar-dinero-arrendatario/plan.md) |
| Plan UC10 | Plan Document | [plan.md](../../010-liquidar-fondos-alquiler/plan.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; se verifica la delegación y las intenciones, no la confirmación externa de cada operación.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1** y **TC-UC04-HU1-E1** deben haber pasado (información y valor total calculados).
* **TC-UC05-HU2-E1 debe haber pasado**: cobro original en `CAPTURED` con `charge_record` y desglose.
* RabbitMQ arriba; colas `finance.reservation-status.v1`, `finance.refund-command.v1` y `finance.settlement-command.v1` disponibles.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos ilustrativos (ajustar al entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; cobro original en `CAPTURED`; sin intenciones ni log del estado.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Liquidación del alquiler + reembolso del depósito
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql` sobre `refund_intent`, `settlement_intent` y `reservation_status_log` | misma reserva | Sin reembolsos, liquidaciones ni log para `CANCELLED_STRICT`. | [x] | |
| 2 | Notificar la cancelación tardía | Publicar `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"CANCELLED_STRICT","status_changed_at":"2026-12-03T18:20:00Z"}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001). | [x] | |
| 3 | Verificar las dos identidades de operación | `SELECT operation_type, idempotency_key, processed_at FROM reservation_status_log WHERE reservation_id = ...` | misma reserva | **Dos** filas (`SETTLEMENT` y `REFUND`) con la misma clave `<reservation_id>:CANCELLED_STRICT`, ambas procesadas (RF-009A). | [x] | |
| 4 | Verificar la liquidación al propietario | `SELECT trigger, scope, amount, commission_amount, deposit_amount, status FROM settlement_intent` | misma reserva | `CANCELLED_STRICT` / `CANCELLATION_COMPENSATION`, monto `900000.00` (100 % del alquiler), comisión `0`, depósito asociado `30000.00`, estado `PENDING_SEND` (o `IN_PROCESS`) (RF-005). | [x] | |
| 5 | Verificar el reembolso del depósito | `SELECT trigger, scope, amount, status, idempotency_key FROM refund_intent` | misma reserva | `CANCELLED_STRICT` / `DEPOSIT_ONLY`, monto `30000.00`, estado `PENDING_SEND` (o `IN_PROCESS`), clave `refund:CANCELLED_STRICT:<operationKey>` (RF-005). | [x] | |
| 6 | Verificar que no se reembolsa alquiler ni seguro | Comparar el monto del reembolso con el alquiler y el seguro | `900000.00`, `60000.00` | El reembolso es exactamente el depósito: **no** incluye `900000.00` de alquiler ni `60000.00` de seguro (RF-005). | [x] | |
| 7 | Verificar los comandos internos | Consola RabbitMQ u `outbox_message` | `settlement.command`, `refund.command` | Un mensaje en cada cola para la reserva; sin comandos adicionales por concepto. | [x] | |
| 8 | Verificar la solicitud sin recepción asumida | Revisar el estado de las intenciones y la ausencia de registros inmutables | misma reserva | Ambas intenciones quedan en curso; UC07 no asume éxito ni recepción de fondos (spec HU2 E3). | [x] | |
| 9 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | Sin respuesta al Sistema de Reservas y Operaciones (RF-009). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Falta el depósito registrado | Publicar la cancelación sobre una reserva sin depósito | sin depósito | `ack` y `operational_failure`; **no** se ejecuta solo la liquidación: la cancelación no queda a medias (RNF-003, CE-004). | [x] | |
| 2 | Falta el monto de alquiler | Publicar sobre una reserva sin alquiler calculado | sin alquiler | `ack` y `operational_failure`; cero intenciones y cero registros (RNF-003). | [x] | |
| 3 | Sin cobro original aprobado | Publicar sin un `charge_intent` en `CAPTURED` | sin cobro | UC09 devuelve `FAILED` y se revierte la transacción completa: tampoco queda la liquidación (CE-004). | [x] | |
| 4 | Reenvío del mismo estado terminal | Publicar dos veces `CANCELLED_STRICT` con marcas distintas | `18:20:00Z` y `18:25:00Z` | Siguen existiendo dos filas de log y una intención de cada tipo; no se duplican operaciones (RF-009A). | [x] | |
| 5 | Estado desconocido | Publicar `status=CANCELLED_LATE` | valor inválido | `ack` e inconsistencia en `operational_failure`; sin operación (RF-001). | [x] | |
| 6 | Falla transitoria de base de datos | Detener PostgreSQL y publicar la cancelación | N/A | `nack`; 5 intentos/backoff base 1 s; DLQ y `operational_failure` al agotarlos (equivale a E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La liquidación se solicita primero y el reembolso después; ambas se confirman o revierten juntas (plan §Reglas 9).
* **EC-002** : El seguro no se reembolsa ni se descuenta de la compensación: la liquidación es el 100 % del alquiler bruto (RF-005).
* **EC-003** : El desenlace del depósito (`REFUND_REQUESTED`) lo aplica UC09/UC08; UC07 solo elige las operaciones.
* **EC-004** : Una cancelación tardía no genera una segunda liquidación del depósito (CE-003, CE-006).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
