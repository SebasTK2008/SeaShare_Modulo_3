### Test Case Specification: Reembolsos · HU1 · E2 Reembolso por cancelación moderada (72 h – 24 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación moderada, el sistema calcula el 50 % del alquiler registrado, le suma el 100 % del depósito y solicita ese monto a la Pasarela, dejando el seguro fuera del reembolso y sin aplicar descuentos por costos transaccionales.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../009-reembolsar-dinero-arrendatario/spec.md) |
| Implementation Plan UC09 | Plan Document | [plan.md](../../009-reembolsar-dinero-arrendatario/plan.md) |
| Contrato evento de estado de reserva (disparador) | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; el monto se verifica en el request journal y en `refund_intent`.
* El resultado externo del reembolso corresponde a UC09 HU3; aquí la operación queda pendiente.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 y TC-UC05-HU2-E1 deben haber pasado** (cobro capturado con desglose).
* **TC-UC09-HU1-E1 debe haber pasado** (flujo de solicitud de reembolso operativo).
* Worker de reembolso activo sobre `finance.refund-command.v1`.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con `rental_amount = 900000.00`, `insurance_amount = 60000.00`, `deposit_amount = 30000.00`, `amount = 990000.00` y depósito en `HELD` (ilustrativos; ajustar a la versión vigente del entorno).
* **Environment** : staging local (`docker compose --profile app`), `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: 50 % del alquiler + 100 % del depósito
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar los montos que entran al cálculo | `SELECT rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'b7d0e2a1-…';` | `900000.00 / 60000.00 / 30000.00` | Alquiler y depósito registrados; el seguro existe pero no participa del reembolso (RF-003). | [x] | |
| 2 | Publicar la cancelación moderada | Publicar en `seashare.reservations` / `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-22T09:00:00Z"}` | `ack`; UC07 delega el reembolso en UC09 (RF-001). | [x] | |
| 3 | Verificar el monto calculado | `SELECT trigger, scope, amount, status FROM refund_intent;` | `450000.00 + 30000.00 = 480000.00` | `trigger = 'CANCELLED_MODERATE'`, `scope = 'HALF_RENTAL_PLUS_DEPOSIT'`, `amount = 480000.00` (RF-003). | [x] | |
| 4 | Verificar que el seguro no se reembolsa | Contrastar el `amount` con un cálculo que incluyera el seguro | `450000.00 + 60000.00 + 30000.00 = 540000.00` | El monto solicitado **no** es `540000.00`: el seguro queda excluido (RF-003, CE-002). | [x] | |
| 5 | Verificar el desenlace del depósito | `SELECT disposition FROM deposit_disposition;` | N/A | `disposition = 'REFUND_REQUESTED'`; el depósito se devuelve al 100 % (RF-003, RF-005). | [x] | |
| 6 | Verificar la llamada a la Pasarela | Journal del simulador (`POST /v1/payments/{id}/refunds`) | `amount = 480000.00` | Se reembolsa el cobro original por el monto calculado, con `X-Idempotency-Key` de la intención (contrato §2). | [x] | |
| 7 | Verificar estado pendiente y trazabilidad | `SELECT status FROM refund_intent;` y `SELECT count(*) FROM refund_record;` | N/A | Intención en curso, sin `refund_record` hasta la confirmación externa; el resultado se registra en UC09 HU3 (RF-006, RF-007). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin monto de alquiler | Publicar `CANCELLED_MODERATE` sin `rental_amount` registrado | reserva sin alquiler | Fallo en `operational_failure` y `FAILED`; no se envía un 50 % asumido ni se contacta a la Pasarela (RF-010, caso extremo del SPEC). | [x] | |
| 2 | Reserva sin depósito registrado | Publicar la cancelación sin depósito cobrado | reserva sin depósito | Fallo controlado; no se calcula el monto ni se solicita el reembolso (RF-010). | [x] | |
| 3 | Alquiler con centavos | Repetir con `rental_amount = 900001.00` | `450000.50 + 30000.00 = 480000.50` | El 50 % se calcula con precisión interna de 4 decimales y se serializa `HALF_UP` a 2 decimales (RNF-002, README §3.1). | [x] | |
| 4 | Misma cancelación repetida | Publicar dos veces el mismo estado | mismo `status_changed_at` | Una sola intención y un solo comando (idempotencia de UC07/UC09, RF-006). | [x] | |
| 5 | `400` de validación de la Pasarela | Simular `400` no reintentable | N/A | Intención en `REJECTED` para revisión manual, sin `refund_record` (contrato §5). | [x] | |
| 6 | `503` o timeout | Simular `503`/timeout en el worker | N/A | `COMMUNICATION_ERROR`, reintentos con la misma clave y DLQ al agotar los 5 intentos (RNF-003, README §4.4). | [x] | |
| 7 | Evento con monto explícito | Agregar `amount` al mensaje de UC07 | `amount = 100.00` | El monto recibido se ignora; rige el cálculo interno (RF-001, CE-002). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El 50 % se aplica al alquiler registrado, nunca al valor total de la reserva (RF-003).
* **EC-002** : El depósito se reembolsa al 100 %; no se admite retención parcial (RF-005).
* **EC-003** : La liquidación del 50 % restante del alquiler corresponde a UC10; UC09 no la ejecuta (exclusión de responsabilidades, CE-002).
* **EC-004** : Un costo transaccional reportado por la Pasarela no modifica el monto solicitado; solo se registra en el resultado (RF-011, RF-012).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
