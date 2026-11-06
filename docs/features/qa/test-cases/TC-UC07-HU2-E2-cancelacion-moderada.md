### Test Case Specification: Estado de la reserva · HU2 · E2 Cancelación moderada (72 h – 24 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `CANCELLED_MODERATE`, el sistema solicita el reembolso del **50 % del alquiler + 100 % del depósito** (sin seguro) **y** la liquidación del **50 % del alquiler** como compensación al propietario, manteniendo cada operación con su propio estado y resultado.

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
* La Pasarela se **simula**; se verifica la creación de las dos intenciones y sus comandos, no el envío externo.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1** y **TC-UC04-HU1-E1** deben haber pasado (información y valor total calculados).
* **TC-UC05-HU2-E1 debe haber pasado**: cobro original en `CAPTURED` con `charge_record` y desglose.
* RabbitMQ arriba; colas `finance.reservation-status.v1`, `finance.refund-command.v1` y `finance.settlement-command.v1` disponibles.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos ilustrativos (ajustar al entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; cobro original en `CAPTURED`; sin intenciones de reembolso/liquidación ni log del estado.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso parcial + liquidación compensatoria
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql` sobre `refund_intent`, `settlement_intent` y `reservation_status_log` | misma reserva | Sin reembolsos, liquidaciones ni log para `CANCELLED_MODERATE`. | [ ] | |
| 2 | Notificar la cancelación moderada | Publicar `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-02T09:40:00Z"}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001). | [ ] | |
| 3 | Verificar las dos identidades de operación | `SELECT operation_type, idempotency_key, processed_at FROM reservation_status_log WHERE reservation_id = ... ORDER BY operation_type` | misma reserva | **Dos** filas (`REFUND` y `SETTLEMENT`) con la misma clave `<reservation_id>:CANCELLED_MODERATE`, ambas con `processed_at` no nulo (RF-009A, plan §Reglas 7). | [ ] | |
| 4 | Verificar el reembolso | `SELECT trigger, scope, amount, status, idempotency_key FROM refund_intent` | misma reserva | `CANCELLED_MODERATE` / `HALF_RENTAL_PLUS_DEPOSIT`, monto `480000.00` (`450000.00` + `30000.00`), estado `PENDING_SEND` (o `IN_PROCESS`), clave UUID v4 (D-UC09-12) (RF-004). | [ ] | |
| 5 | Verificar la liquidación compensatoria | `SELECT trigger, scope, amount, commission_amount, deposit_amount, status FROM settlement_intent` | misma reserva | `CANCELLED_MODERATE` / `CANCELLATION_COMPENSATION`, monto `450000.00` (50 % del alquiler), comisión `0`, depósito asociado `30000.00`, estado `PENDING_SEND` (o `IN_PROCESS`) (RF-004; UC10 plan §Reglas 2). | [ ] | |
| 6 | Verificar que el seguro no se reembolsa | Comparar `refund_intent.amount` con alquiler/seguro/depósito | `900000.00`, `60000.00`, `30000.00` | El monto `480000.00` **no** incluye el seguro: solo 50 % del alquiler + depósito (RF-004). | [ ] | |
| 7 | Verificar los comandos internos | Consola RabbitMQ u `outbox_message` | `refund.command`, `settlement.command` | Un mensaje en cada cola (`finance.refund-command.v1` y `finance.settlement-command.v1`) para la reserva. | [ ] | |
| 8 | Verificar resultados independientes y atomicidad | Revisar el estado de ambas intenciones y que el `ack` ocurrió tras delegar las dos | misma reserva | Cada intención conserva su estado; el mensaje se confirma solo si ambas delegaciones se registraron en la única transacción (plan §Reglas 9, CE-002). | [ ] | |
| 9 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | Sin respuesta al Sistema de Reservas y Operaciones (RF-009). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Falta el monto de alquiler o el depósito | Publicar la cancelación sobre una reserva con cálculo incompleto | sin alquiler o sin depósito | `ack` y `operational_failure`; **no** queda una cancelación a medias: cero reembolsos y cero liquidaciones (RNF-003, CE-004). | [x] | |
| 2 | Sin cobro original aprobado | Publicar sin un `charge_intent` en `CAPTURED` | sin cobro | UC09 devuelve `FAILED`; se revierte la transacción completa: ni log ni intenciones, y el fallo se registra (UC07 plan §Reglas 8). | [x] | |
| 3 | La liquidación delegada devuelve `FAILED` | Forzar el `FAILED` de UC10 con el doble de aceptación (no existe una entrada manual para este fallo) | `SettlementRequestResult.status = FAILED` | Se revierte también el reembolso ya delegado: la cancelación no queda ejecutada a medias (CE-004; plan UC07 ACT-11). | [x] | |
| 4 | Reenvío del mismo estado terminal | Publicar dos veces `CANCELLED_MODERATE` con marcas distintas | `09:40:00Z` y `09:45:00Z` | Siguen existiendo **dos** filas de log y **una** intención de cada tipo; no se duplican operaciones (RF-009A). | [x] | |
| 5 | Estado en minúsculas | Publicar `status=cancelled_moderate` | valor inválido | `ack` e inconsistencia; sin operación (RF-001). | [x] | |
| 6 | Falla transitoria de base de datos | Detener PostgreSQL y publicar la cancelación | N/A | `nack`; 5 intentos/backoff base 1 s; DLQ y `operational_failure` al agotarlos (equivale a E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El plan de UC07 ejecuta primero el reembolso y luego la liquidación, pero ambas viven en la misma transacción: no hay ejecución parcial visible.
* **EC-002** : El depósito se reembolsa al 100 % y el alquiler al 50 %; el propietario recibe el otro 50 % del alquiler como compensación (RF-004).
* **EC-003** : El 50 % se calcula con 4 decimales internos y se redondea `HALF_UP` a 2 decimales hacia la Pasarela (RNF-002, README §3.1).
* **EC-004** : Se esperan exactamente dos filas de log (una por tipo de operación); una sola fila indicaría que la cancelación quedó incompleta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
