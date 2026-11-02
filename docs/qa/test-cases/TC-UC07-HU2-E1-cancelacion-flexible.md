### Test Case Specification: Estado de la reserva · HU2 · E1 Cancelación flexible (>72 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `CANCELLED_FLEXIBLE`, el sistema solicita al reembolso el **100 % del valor total pagado** (alquiler, seguro y depósito) y **no** ejecuta "Liquidar fondos de alquiler", dejando el resultado pendiente de confirmación externa.

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
* La Pasarela se **simula**; este TC verifica la delegación y la intención de reembolso, no el envío externo (cubierto en UC09).

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1** y **TC-UC04-HU1-E1** deben haber pasado (información y valor total calculados).
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro original en estado `CAPTURED` con `charge_record` y desglose (alquiler, seguro, depósito).
* RabbitMQ arriba; colas `finance.reservation-status.v1` y `finance.refund-command.v1` disponibles.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos ilustrativos (ajustar al entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; `charge_intent` original en `CAPTURED`; sin `refund_intent`, `settlement_intent` ni filas de `reservation_status_log` para el estado.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso total sin liquidación
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql` sobre `refund_intent`, `settlement_intent` y `reservation_status_log` | misma reserva | Sin reembolsos, liquidaciones ni log para `CANCELLED_FLEXIBLE`. | [ ] | |
| 2 | Notificar la cancelación flexible | Publicar `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-01T10:15:00Z"}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001). | [ ] | |
| 3 | Verificar la identidad de la operación | `SELECT status, operation_type, idempotency_key, processed_at FROM reservation_status_log` | misma reserva | **Una** fila `CANCELLED_FLEXIBLE` / `REFUND` con clave `<reservation_id>:CANCELLED_FLEXIBLE` y `processed_at` no nulo (RF-009A). | [ ] | |
| 4 | Verificar la intención de reembolso | `SELECT trigger, scope, amount, status, charge_intent_id, idempotency_key FROM refund_intent` | misma reserva | Una fila `CANCELLED_FLEXIBLE` / `FULL`, monto `990000.00`, estado `PENDING_SEND` (o `IN_PROCESS` si el worker ya la tomó), asociada al `charge_intent` original y clave `refund:CANCELLED_FLEXIBLE:<operationKey>` (RF-003). | [ ] | |
| 5 | Verificar que **no** se liquida | `SELECT count(*) FROM settlement_intent WHERE reservation_id = ...` | misma reserva | Cero filas: la cancelación flexible no ejecuta "Liquidar fondos de alquiler" (RF-003). | [ ] | |
| 6 | Verificar el comando interno | Consola RabbitMQ u `outbox_message` | enrutado `refund.command` | Un único mensaje en `finance.refund-command.v1`; ningún `settlement.command` para esta reserva (plan §Reglas 4). | [ ] | |
| 7 | Verificar el resultado pendiente | `SELECT status FROM refund_intent` y `SELECT count(*) FROM refund_record` | misma reserva | La solicitud queda registrada en curso y UC07 no asume el éxito: el `refund_record` solo existe cuando la Pasarela confirma (spec HU2 E1). | [ ] | |
| 8 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | Sin respuesta al Sistema de Reservas y Operaciones (RF-009). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin valor total calculado | Publicar `CANCELLED_FLEXIBLE` para una reserva sin cálculo | sin montos | `ack` y `operational_failure`; sin reembolso ni liquidación, sin montos asumidos (RNF-003, CE-004; caso extremo del spec). | [ ] | |
| 2 | Reserva sin registro financiero | Publicar para un UUID no registrado por UC03 | UUID nuevo | `ack` y `operational_failure` (`RESERVATION_INFO_NOT_FOUND`); sin delegación (equivale a E3/E6). | [ ] | |
| 3 | Sin cobro original aprobado | Publicar la cancelación sin un `charge_intent` en `CAPTURED` | sin cobro | UC09 devuelve `FAILED`: la transacción completa se revierte, no queda log ni intención y se registra el fallo (UC07 plan §Reglas 8). | [ ] | |
| 4 | Reenvío del mismo estado terminal | Publicar dos veces `CANCELLED_FLEXIBLE` con marcas distintas | `10:15:00Z` y `10:20:00Z` | Una sola fila de log y un solo `refund_intent`: en estados terminales la clave es estable (RF-009A, D-UC07-11). | [ ] | |
| 5 | Estado de cancelación en minúsculas | Publicar `status=cancelled_flexible` | valor inválido | `ack` e inconsistencia en `operational_failure`; sin operación (RF-001). | [ ] | |
| 6 | Falla transitoria de base de datos | Detener PostgreSQL y publicar la cancelación | N/A | `nack`; 5 intentos/backoff base 1 s; DLQ y `operational_failure` al agotarlos (equivale a E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El reembolso es un único monto que incluye alquiler, seguro y depósito; el seguro **sí** se devuelve en la cancelación flexible (RF-003).
* **EC-002** : El desenlace del depósito (`REFUND_REQUESTED`) lo aplica UC09/UC08 con su *compare-and-set*; UC07 no escribe `deposit_disposition` en cancelaciones.
* **EC-003** : La solicitud de reembolso no implica que los fondos ya se hayan recibido; el estado final lo confirma la Pasarela (spec HU2 E1).
* **EC-004** : Montos con 4 decimales internos → redondeo `HALF_UP` a 2 decimales hacia la Pasarela (contratos/README §3.1).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
