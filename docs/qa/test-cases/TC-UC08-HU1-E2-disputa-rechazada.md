### Test Case Specification: Disputa de garantía · HU1 · E2 Disputa rechazada y devolución al Arrendatario
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC08-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir `REJECTED`, el sistema registra la resolución, aplica el desenlace `REFUND_REQUESTED` sobre el depósito y solicita a UC09 el reembolso **total** del depósito cobrado y registrado, sin recibir ni asumir montos desde el Sistema de Reservas y Operaciones.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC08 | Specification Document | [spec.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md) |
| Implementation Plan UC08 | Plan Document | [plan.md](../../../docs/features/008-brindar-informacion-de-disputa-de-garantia/plan.md) |
| Contrato evento de disputa | Events contract | [UC08-disputa-garantia.md](../../../docs/technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../docs/technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo A (suite automática):** el cuerpo y las cabeceras que el sistema envía a la Pasarela los verifican `MercadoPagoChargeAdapterTest` y `MercadoPagoRefundAdapterTest` (WireMock embebido); este TC verifica el efecto en la BD, las colas y la consulta real del reembolso.
* **Modo B (release):** el reembolso del depósito debe repetirse contra la API real de Mercado Pago sandbox, incluido el webhook `payment`, según [`pagos-api-real.md`](../pagos-api-real.md). Sin esa evidencia el TC no se aprueba.

##### Dependencies & Assumptions
* **TC-UC08-HU1-E1 debe haber pasado**: el mismo depósito ya está en seguimiento y la ingesta de disputas funciona.
* **TC-UC05-HU1-E1 y TC-UC05-HU2-E1 deben haber pasado**: existe cobro `CAPTURED` con `charge_record` y `charge_intent.external_reference`.
* Worker de reembolso activo sobre `finance.refund-command.v1`.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`, cobro `CAPTURED` con `rental_amount = 900000.00`, `insurance_amount = 60000.00`, `deposit_amount = 30000.00`, `amount = 990000.00` (ilustrativos; ajustar a la versión vigente del entorno); `deposit_disposition.disposition = 'HELD'`.
* **Environment** : staging local (`docker compose --profile app`), `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: `REJECTED` → reembolso total del depósito
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el depósito interno | `SELECT cr.deposit_amount FROM charge_record cr WHERE cr.reservation_id = 'b7d0e2a1-…';` y `SELECT disposition FROM deposit_disposition;` | `30000.00`, `HELD` | El depósito está cobrado y registrado; UC08 no lo recalcula desde la notificación (RF-005, RF-007). | [ ] | |
| 2 | Publicar la disputa `REJECTED` | Publicar en `seashare.reservations` / `reservation.dispute.updated` | `{"reservation_id":"b7d0e2a1-…","dispute_id":"d8e1f2a3-…","status":"REJECTED","status_changed_at":"2026-12-23T10:00:00Z","event_key":"v1.0-4a5b6c"}` | `ack`; la resolución se procesa sin motivo ni monto en el mensaje (RF-001, RF-003, RF-009). | [ ] | Sin respuesta al productor. |
| 3 | Verificar el registro de la resolución | `SELECT status, status_changed_at, processed_at FROM dispute_event_log WHERE reservation_id = 'b7d0e2a1-…';` | `event_key = 'v1.0-4a5b6c'` | Fila `REJECTED` con `processed_at` no nulo y sin columna de motivo/origen (RF-003, RF-008). | [ ] | |
| 4 | Verificar el desenlace del depósito | `SELECT reservation_id, disposition, dispute_id FROM deposit_disposition;` | `dispute_id = 'd8e1f2a3-…'` | `disposition = 'REFUND_REQUESTED'` aplicado una sola vez por *compare-and-set* (RF-005, RF-008). | [ ] | |
| 5 | Verificar la intención de reembolso | `SELECT trigger, scope, amount, charge_intent_id, dispute_id, status, idempotency_key FROM refund_intent;` | `trigger = 'DISPUTE_REJECTED'` | `scope = 'DEPOSIT_ONLY'`, `amount = 30000.00` (100 % del depósito), `dispute_id` presente, `charge_intent_id` del cobro capturado y estado en curso (RF-005, CE-001). | [ ] | |
| 6 | Verificar la entrega a la Pasarela | Inspeccionar `outbox_message` y consultar el reembolso real: `GET /v1/payments/{id}/refunds` en Mercado Pago | `amount = 30000.00` | `{id}` = `charge_intent.external_reference`; body con `amount` del depósito; `X-Idempotency-Key` = clave de la intención (contrato de reembolso §2, RF-012 de UC09). | [ ] | Sin descuento por costos transaccionales. El header `X-Idempotency-Key` lo verifica `MercadoPagoRefundAdapterTest`; en la BD, `refund_intent.idempotency_key` es el UUID v4 que se envía. |
| 7 | Verificar que no hay liquidación ni registro definitivo | `SELECT count(*) FROM settlement_intent;` y `SELECT count(*) FROM refund_record;` | N/A | Sin `settlement_intent`; sin `refund_record`, porque el resultado de la Pasarela aún no se confirmó (RF-005, RF-009). | [ ] | El resultado externo se cubre en UC09 HU3. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin depósito registrado | Publicar `REJECTED` para una reserva sin `deposit_disposition` ni depósito cobrado | reserva sin depósito | `ack` + fallo controlado en `operational_failure`; sin reembolso ni monto asumido (RF-005, caso extremo del SPEC). | [ ] | |
| 2 | Cobro original no capturado | Publicar `REJECTED` con cobro en `REJECTED`/`EXPIRED` | `charge_intent.status = 'REJECTED'` | Fallo controlado; no se solicita reembolso (RF-005 "según el estado del cobro original"). | [ ] | |
| 3 | `REJECTED` repetido | Reenviar el mismo `event_key` | mismo payload | `ack` sin segunda intención ni segundo reembolso (RF-008, CE-005). | [ ] | |
| 4 | Mismo `dispute_id` con `event_key` distinto | Publicar un `REJECTED` nuevo para una disputa ya resuelta | `event_key = "v1.0-4a5b6d"` | Sin segunda operación monetaria; se registra la inconsistencia para conciliación (RF-008, CE-003). | [ ] | |
| 5 | `COMPLETED` después de `REJECTED` | Publicar `COMPLETED` tras el reembolso ya solicitado | `status = "COMPLETED"` | No se liquida el depósito; se conserva el desenlace existente y se registra la inconsistencia (RF-008, RF-010). | [ ] | |
| 6 | Mensaje con monto o motivo | Agregar `amount = 15.00` y `reason` al payload | campos extra | Se ignoran; el monto ejecutado sale del `charge_record` interno (RF-003, RF-007, CE-004). | [ ] | |
| 7 | Falla de la Pasarela al solicitar el reembolso | Simular `503`/timeout en el worker | N/A | `refund_intent` en `COMMUNICATION_ERROR`; no se asume resultado; reintentos con la misma clave (RNF-003, contrato §5). | [ ] | Detalle de reintentos en UC09. |

##### Edge Cases & Boundary Conditions
* **EC-001** : El depósito se reembolsa siempre al 100 %; no existe retención parcial ni sub-resultado (RF-010, CE-006).
* **EC-002** : `REJECTED` no requiere ni persiste motivo u origen operativo (RF-003).
* **EC-003** : `deposit_disposition` admite un único desenlace: tras `REFUND_REQUESTED` no puede pasar a `SETTLEMENT_REQUESTED` (RF-008).
* **EC-004** : La clave de la intención se deriva del `trigger` y del `event_key` recibido; un reenvío reutiliza la misma clave (plan UC08, regla 3).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
