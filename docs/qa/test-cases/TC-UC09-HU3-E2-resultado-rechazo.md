### Test Case Specification: UC09 Reembolsos · HU3 · E2 La Pasarela de Pago reporta el rechazo o fallo del reembolso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU3-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la Pasarela de Pago reporta el rechazo o fallo de un reembolso previamente solicitado, el sistema registra el estado externo y su detalle en la `IntenciónDeReembolso` **sin** marcar la operación como completada y **sin** crear un `RegistroDeReembolso`, dejando el resultado disponible internamente.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../docs/technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../docs/technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El sandbox no produce reembolsos rechazados, cancelados ni expirados: la Section 1 se verifica con la suite automática (`RefundWebhookHandlerTest`, `RegisterRefundResultServiceTest`). La Section 2 (firma inválida, payload incompleto) sí se ejecuta contra la aplicación; el webhook se firma a mano (runbook §3.1).
* El escenario cubre estados externos de rechazo/fallo; el fallo puramente técnico se valida en Section 2.

##### Dependencies & Assumptions
* **TC-UC09-HU2-E1 debe haber pasado**: existe una solicitud de reembolso enviada y con referencia externa.
* `finance.gateway-notification.v1` y su worker activos.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`; `refund_intent` en `PENDING_SEND`/`IN_PROCESS`, `amount = 30000.00` (100 % del depósito; ajustar a la versión vigente), `external_reference` = `id` del reembolso; cobro original con `external_reference` = `payment_id`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro de un reembolso rechazado o fallido
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la solicitud en curso | `SELECT status, amount, external_reference FROM refund_intent WHERE reservation_id = '...'` | N/A | Existe la intención enviada, pendiente de resultado y asociada a la referencia externa (RF-006, RF-007). | [ ] | |
| 2 | Simular la notificación de fallo | `POST /api/v1/webhook/gateway?data.id=<payment_id>&type=payment` | `payment_id` = `charge_intent.external_reference`; body con `action` y `type` (obligatorios); headers `x-signature` válida y `x-request-id` | `200 OK` inmediato y notificación persistida en `outbox_message` (D-UC09-11). | [ ] | |
| 3 | Verificar la consulta de reembolsos | Logs de la app (consulta `GET /v1/payments/{payment_id}/refunds`) | N/A | El worker lista los reembolsos y obtiene el estado externo del que coincide con `refund_intent.external_reference` (D-UC09-11). | [ ] | |
| 4 | Verificar la intención actualizada | `SELECT status, status_detail, external_reference FROM refund_intent` | N/A | Estado interno correspondiente al rechazo/fallo (`REJECTED`, `CANCELLED` o `EXPIRED`), con `status_detail` y referencia externa; **nunca** `COMPLETED` (RF-007, RF-009). | [ ] | |
| 5 | Verificar la ausencia de registro inmutable | `SELECT count(*) FROM refund_record WHERE refund_intent_id = '<id>'` | N/A | `0` filas: no se crea `RegistroDeReembolso` para un reembolso rechazado, cancelado o expirado (RF-009). | [ ] | |
| 6 | Verificar que no se reporta como devuelto | `SELECT * FROM v_financial_records WHERE reservation_id = '...'` y consultar UC06 | N/A | No aparece un reembolso confirmado; `released_amount` no se incrementa (RF-008, RF-009). | [ ] | |
| 7 | Verificar la disponibilidad interna del resultado | `SELECT * FROM refund_intent WHERE id = '<id>'` y logs con `reservation_id` | N/A | El estado externo y su detalle quedan consultables para revisión manual y conciliación (RNF-003; escenario E2). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Firma inválida o ausente | Enviar el webhook con `x-signature` incorrecta | `data.id` y `type` válidos | `401 UNAUTHENTICATED`; no se actualiza la intención (contrato webhook §5). | [ ] | |
| 2 | Payload incompleto | Enviar sin `action`, sin `data.id` o sin `type` | payload incompleto | `400 VALIDATION_ERROR`; no se procesa el resultado (contrato webhook §5). | [ ] | |
| 3 | Falla técnica, no rechazo de negocio | Simular timeout o `5xx` en la lista de reembolsos | N/A | La intención no se marca `REJECTED`; el mensaje se reintenta (5 intentos, *backoff* base 1 s) y, al agotarlos, se rechaza a la DLQ y se registra en `operational_failure` (RNF-003; CE-004). | [ ] | |
| 4 | La Pasarela no conoce el pago | Simular `404` en `GET .../refunds` | N/A | La intención no se cierra y el evento queda en `operational_failure` (contrato webhook §3.6). | [ ] | |
| 5 | Notificación repetida del mismo fallo | Reenviar el mismo webhook | mismo `data.id` | `200 OK`; no se altera el estado ya registrado ni se crea registro (contrato webhook §6). | [ ] | |
| 6 | Reembolso sin intención local | Notificar un pago cuyo reembolso no coincide con ninguna `refund_intent` | `refund.id` desconocido | `200 OK`; el reembolso se registra una vez en `operational_failure` y no se reintenta (contrato webhook §3.6). | [ ] | |
| 7 | Falla al persistir la notificación | Simular BD no disponible antes del acuse | N/A | `500 INTERNAL_ERROR` para que la Pasarela reintente (contrato webhook §3.7). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `rejected`, `cancelled`, `expired` e `in_process` actualizan solo la intención; ninguno crea `refund_record` (RF-009).
* **EC-002** : Una notificación repetida conserva el resultado ya registrado y no lo sobrescribe (caso extremo del UC09).
* **EC-003** : Un fallo de comunicación no es un veredicto de la Pasarela: la intención queda en estado de error y es reconciliable, sin marcarse como rechazada por negocio (RNF-003, CE-004).
* **EC-004** : `status_detail` es opcional en la respuesta de la Pasarela; si no llega, se registra el estado y el detalle queda `NULL` (contrato de reembolso §4).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
