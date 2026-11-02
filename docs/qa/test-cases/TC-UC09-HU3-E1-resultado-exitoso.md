### Test Case Specification: UC09 Reembolsos · HU3 · E1 La Pasarela de Pago reporta el reembolso como exitoso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la Pasarela de Pago reporta el éxito de un reembolso previamente solicitado, el sistema actualiza la `IntenciónDeReembolso` y crea el `RegistroDeReembolso` inmutable con el monto confirmado, el detalle, la referencia externa y el vínculo obligatorio a la intención, dejándolo disponible para "Consultar registros financieros".

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
* La Pasarela se **simula**; el webhook se dispara con una firma HMAC-SHA256 válida del simulador.
* La consulta de detalle (`GET /v1/payments/{payment_id}/refunds/{refund_id}`) también se sirve desde el simulador.

##### Dependencies & Assumptions
* **TC-UC09-HU2-E1 debe haber pasado**: existe una solicitud de reembolso ya enviada y asociada a una referencia externa.
* `finance.gateway-notification.v1` y su worker activos.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`; `refund_intent` en `PENDING_SEND`/`IN_PROCESS`, `amount = 30000.00` (100 % del depósito; ajustar a la versión vigente), `trigger = DISPUTE_REJECTED`, `external_reference` = `id` del reembolso devuelto por la Pasarela. `charge_intent.external_reference` = `payment_id` original.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro de un reembolso confirmado como exitoso
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la intención en curso | `SELECT status, amount, external_reference FROM refund_intent WHERE reservation_id = '...'` | N/A | La solicitud previa existe y está pendiente, con su `external_reference` ya persistida (RF-006, RF-007). | [ ] | |
| 2 | Simular la notificación de la Pasarela | `POST /api/v1/webhook/gateway?data.id=<refund_id>&type=refund` | Body con `action` y `type` (obligatorios, no discriminan el resultado); headers `Content-Type: application/json`, `x-signature` válida, `x-request-id` | `200 OK` inmediato; la notificación queda persistida en `outbox_message` antes del acuse (contrato webhook §3.2). | [ ] | |
| 3 | Verificar la cola interna | Inspeccionar `finance.gateway-notification.v1` | `{"type":"refund","action":"<el enviado>","data_id":"<refund_id>"}` | El mensaje se publica con routing `gateway.notification` para el procesamiento asíncrono (contrato webhook §3.3). | [ ] | |
| 4 | Verificar la consulta de detalle | Request journal: `GET /v1/payments/{charge_intent.external_reference}/refunds/{data.id}` | N/A | El worker consulta el recurso y obtiene el estado definitivo; el cuerpo del webhook no se usa como veredicto (contrato webhook §3.4). | [ ] | |
| 5 | Verificar la intención actualizada | `SELECT status, status_detail, amount, external_reference FROM refund_intent` | N/A | Estado `COMPLETED`, con `status_detail`, monto confirmado y referencia externa (RF-007). | [ ] | |
| 6 | Verificar el registro inmutable | `SELECT * FROM refund_record WHERE refund_intent_id = '<id>'` | N/A | Un único `refund_record` con `refund_intent_id NOT NULL`, `amount`, `released_amount`, `detail`, `external_reference`, reserva, propietario, embarcación, `dispute_id` cuando aplique y `created_at` (RF-007, RF-006A). | [ ] | Creación atómica con la intención. |
| 7 | Verificar el monto neto liberado | Comparar `amount`, `transaction_cost` y `released_amount` | costo reportado por el simulador | `released_amount = amount − transaction_cost` cuando la Pasarela informa costo; si no lo informa, `released_amount = amount` y `transaction_cost` queda `NULL` (RF-011). | [ ] | |
| 8 | Verificar disponibilidad para consulta | `SELECT * FROM v_financial_records WHERE record_id = '<refund_record.id>'` | N/A | El registro aparece para "Consultar registros financieros" (RF-008, CE-003). | [ ] | |
| 9 | Verificar inmutabilidad y no duplicación | `SELECT count(*) FROM refund_record WHERE refund_intent_id = '<id>'` | N/A | Exactamente 1 fila; el registro no admite `UPDATE`/`DELETE` (RF-007; caso extremo del UC09). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Firma inválida o ausente | `POST /api/v1/webhook/gateway` con `x-signature` incorrecta | `data.id` y `type` válidos | `401 UNAUTHENTICATED`; no se procesa nada (contrato webhook §3.1 y §5). | [ ] | |
| 2 | Payload incompleto | Enviar sin `action`, sin `data.id` o sin `type` | payload incompleto | `400 VALIDATION_ERROR`; no se encola ni se registra el resultado (contrato webhook §5). | [ ] | |
| 3 | Notificación duplicada del mismo resultado | Reenviar el mismo webhook | mismo `data.id` | `200 OK` y el resultado ya registrado no se altera ni crea otro `refund_record` (contrato webhook §3.5 y §6). | [ ] | |
| 4 | La Pasarela no conoce el reembolso | Simular `404` en la consulta de detalle | N/A | La intención **no** se cierra; el evento se registra en `operational_failure` (contrato webhook §3.6). | [ ] | |
| 5 | `data.id` sin intención asociada | Notificar un `refund_id` desconocido | `data.id` inexistente | `200 OK` y registro en `operational_failure`, sin creación de registros (contrato webhook §3.6). | [ ] | |
| 6 | Falla al persistir la notificación | Simular BD no disponible antes del acuse | N/A | `500 INTERNAL_ERROR` para que la Pasarela reintente (contrato webhook §3.7). | [ ] | |
| 7 | Falla en el paso asíncrono | Simular error de la consulta o del registro tras el `200 OK` | N/A | Reintentos de la cola interna (5 intentos, *backoff* base 1 s); al agotarlos, DLQ y `operational_failure` (README §4.4). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Solo el estado exitoso crea `refund_record`; `rejected`, `cancelled`, `expired` o `in_process` actualizan únicamente la intención (RF-009).
* **EC-002** : El monto confirmado por la Pasarela prevalece sobre el solicitado; `released_amount` se calcula sobre el confirmado (RF-007, RF-011).
* **EC-003** : La repetición de la notificación no altera el registro existente; el sistema conserva el resultado ya registrado (caso extremo del UC09).
* **EC-004** : `BigDecimal` con 4 decimales internos → `HALF_UP` a 2 decimales en el registro y en la vista de consulta (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
