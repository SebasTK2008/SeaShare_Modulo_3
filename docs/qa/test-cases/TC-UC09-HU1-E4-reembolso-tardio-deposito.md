### Test Case Specification: Reembolsos · HU1 · E4 Reembolso del depósito por cancelación tardía (<24 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU1-E4
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación tardía, el sistema solicita únicamente el reembolso del 100 % del depósito registrado, excluyendo alquiler y seguro, y sin aplicar descuentos por costos transaccionales.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md) |
| Implementation Plan UC09 | Plan Document | [plan.md](../../../docs/features/009-reembolsar-dinero-arrendatario/plan.md) |
| Contrato evento de estado de reserva (disparador) | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../docs/technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; el monto se verifica en el request journal y en `refund_intent`.
* El resultado externo del reembolso se cubre en UC09 HU3; aquí la operación queda pendiente.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 y TC-UC05-HU2-E1 deben haber pasado** (cobro capturado con desglose).
* **TC-UC09-HU1-E2 debe haber pasado** (cálculo parcial sobre el depósito operativo).
* Worker de reembolso activo sobre `finance.refund-command.v1`.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con `rental_amount = 900000.00`, `insurance_amount = 60000.00`, `deposit_amount = 30000.00`, `amount = 990000.00` y depósito en `HELD` (ilustrativos; ajustar a la versión vigente del entorno).
* **Environment** : staging local (`docker compose --profile app`), `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Devolución exclusiva del depósito
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el depósito registrado | `SELECT deposit_amount FROM charge_record WHERE reservation_id = 'b7d0e2a1-…';` y `SELECT disposition FROM deposit_disposition;` | `30000.00`, `HELD` | El depósito está cobrado y registrado, único importe reembolsable del escenario (RF-003A). | [ ] | |
| 2 | Publicar la cancelación tardía | Publicar en `seashare.reservations` / `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"CANCELLED_STRICT","status_changed_at":"2026-12-23T09:00:00Z"}` | `ack`; UC07 delega el reembolso del depósito en UC09 (RF-001). | [ ] | |
| 3 | Verificar la intención de reembolso | `SELECT trigger, scope, amount, status, idempotency_key FROM refund_intent;` | `trigger = 'CANCELLED_STRICT'` | `scope = 'DEPOSIT_ONLY'`, `amount = 30000.00`, clave `refund:CANCELLED_STRICT:<reservation_id>:CANCELLED_STRICT` (RF-003A, RF-006). | [ ] | |
| 4 | Verificar que alquiler y seguro quedan fuera | Contrastar el `amount` con el valor total y con el alquiler | `30000.00` ≠ `990000.00` ≠ `900000.00` | Solo se solicita el depósito; el alquiler y el seguro no forman parte del reembolso (RF-003A, CE-002). | [ ] | |
| 5 | Verificar el desenlace del depósito | `SELECT disposition FROM deposit_disposition;` | N/A | `disposition = 'REFUND_REQUESTED'`; el depósito se devuelve al 100 % (RF-003A, RF-005). | [ ] | |
| 6 | Verificar la llamada a la Pasarela | Journal del simulador (`POST /v1/payments/{id}/refunds`) | `amount = 30000.00` | Reembolso parcial explícito sobre el cobro original, con `X-Idempotency-Key` de la intención (contrato §2). | [ ] | |
| 7 | Verificar estado pendiente | `SELECT status FROM refund_intent;` y `SELECT count(*) FROM refund_record;` | N/A | Intención en curso, sin `refund_record` hasta la confirmación externa (RF-006, RF-007). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin depósito registrado | Publicar `CANCELLED_STRICT` sin depósito cobrado | reserva sin depósito | `operational_failure` y `FAILED`; sin llamada a la Pasarela ni monto asumido (RF-010, caso extremo del SPEC). | [ ] | |
| 2 | Cobro original no aprobado | Publicar la cancelación con cobro en `REJECTED`/`EXPIRED` | `charge_intent.status = 'REJECTED'` | No se solicita reembolso del depósito (RF-006 "según el estado del cobro original"). | [ ] | |
| 3 | Monto mayor al depósito en la intención | Inspeccionar que no se sume alquiler o seguro | N/A | El `amount` es exactamente el depósito; no se admite retención ni devolución parcial (RF-005, CE-002). | [ ] | |
| 4 | Cancelación repetida | Publicar dos veces el mismo estado | mismo `status_changed_at` | Una sola intención y un solo comando (idempotencia, RF-006). | [ ] | |
| 5 | `400` de validación de la Pasarela | Simular `400` no reintentable | N/A | Intención en `REJECTED` para revisión manual, sin `refund_record` (contrato §5). | [ ] | |
| 6 | `503` o timeout | Simular `503`/timeout en el worker | N/A | `COMMUNICATION_ERROR`, reintentos con la misma clave y DLQ tras 5 intentos (RNF-003). | [ ] | |
| 7 | Evento con monto explícito | Agregar `amount` al mensaje de UC07 | `amount = 990000.00` | Se ignora; rige el depósito registrado (`30000.00`) (RF-001, CE-002). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El mismo importe de depósito puede devolverse por `CANCELLED_STRICT` o por `DISPUTE_REJECTED`, pero con `trigger` y clave idempotente distintos (RF-006).
* **EC-002** : La liquidación del 100 % del alquiler al Propietario corresponde a UC10; UC09 solo devuelve el depósito (RF-003A, CE-002).
* **EC-003** : El depósito se reembolsa al 100 %; no existe retención parcial por cancelación tardía (RF-005).
* **EC-004** : El monto enviado es un reembolso parcial explícito; no se omite el `amount` para no convertir la operación en un reembolso total (contrato §2).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
