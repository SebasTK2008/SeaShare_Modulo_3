### Test Case Specification: Liquidar fondos de alquiler · HU4 · E1 La Pasarela reporta la dispersión como exitosa
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU4-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una solicitud de dispersión previamente enviada a la Pasarela de Pago y una notificación `type=merchant_order` que reporta la transferencia como exitosa, el sistema actualiza la `IntenciónDeDispersión`, crea **un único** `RegistroDeDispersión` inmutable con monto confirmado, detalle y referencia externa, y lo deja disponible para "Consultar registros financieros" y "Consultar informe financiero" sin duplicarlo ante repeticiones.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../docs/technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de liquidación | External contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula** (WireMock / simulador); la notificación se genera a mano con la firma `x-signature` calculada con el secret del webhook del entorno.

##### Dependencies & Assumptions
* **TC-UC10-HU2-E1 debe haber pasado**: existe una `settlement_intent` enviada a la Pasarela y con `external_reference` persistida.
* RabbitMQ arriba y outbox activo; cola `finance.gateway-notification.v1` y su DLQ disponibles.

##### Preconditions & Default Setup
* **Initial State** : `settlement_intent` de la reserva `R-200` (alcance `STANDARD`) en estado `IN_PROCESS`, `external_reference = mp_disp_QA_01`, `idempotency_key = settlement:COMPLETED:<operationKey>`. Montos de ejemplo (ajustar a la versión vigente de parámetros del entorno): alquiler bruto `360.00`, seguro `200.00`, depósito `12.00`, comisión `54.00`, monto solicitado `106.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro exitoso del resultado de la dispersión
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la solicitud previa | `SELECT` sobre `settlement_intent` | `reservation_id = R-200` | La intención existe en estado en curso, con monto solicitado, desglose interno y `external_reference` persistida (RF-009, RF-010). | [ ] | |
| 2 | Simular la notificación de la Pasarela | `POST /api/v1/webhook/gateway?data.id=mp_disp_QA_01&type=merchant_order` con `Content-Type: application/json`, `x-signature: ts=...,v1=...`, `x-request-id: <uuid>` | Body `{"action":"payment.updated","api_version":"v1","data":{"id":"mp_disp_QA_01"},"type":"merchant_order"}` | `200 OK` (`{"status":"received"}`) de acuse inmediato; la notificación queda persistida de forma durable (contract §3.2). | [ ] | La firma se calcula con el secret del entorno. |
| 3 | Verificar la publicación interna | Consola RabbitMQ u `outbox_message` | N/A | Mensaje en `finance.gateway-notification.v1` con enrutado `gateway.notification` y cuerpo `{"type":"merchant_order","action":"payment.updated","data_id":"mp_disp_QA_01"}`. | [ ] | |
| 4 | Verificar la consulta a la Pasarela | Request journal del simulador (`GET /v1/advanced_payments/{id}`) | `{id} = mp_disp_QA_01` | Se consulta el detalle con `Authorization: Bearer <ACCESS_TOKEN>`; el estado se toma de la consulta, no del cuerpo del webhook (contract §3.4). | [ ] | |
| 5 | Verificar la actualización de la intención | `SELECT` sobre `settlement_intent` | `R-200` | Estado `COMPLETED` y `status_detail` del estado externo registrados; la intención sigue siendo la fuente de verdad del resultado (RF-010). | [ ] | |
| 6 | Verificar el registro inmutable | `SELECT` sobre `settlement_record` filtrando por la intención | `R-200` | **Una** fila con monto confirmado, detalle, referencia externa, `settlement_intent_id` (`intencionDeDispersiónId`), reserva, propietario, embarcación, alquiler bruto, seguro aplicado y depósito asociado, con `fecha_hora_creacion` (RF-009A, RF-010, RF-015, RF-016). | [ ] | |
| 7 | Verificar la indisponibilidad de duplicados | Repetir el `POST` del paso 2 con la misma firma y `data.id` | mismo payload | `200 OK`; la intención y el registro **no** cambian y no aparece una segunda fila en `settlement_record` (idempotencia por `external_reference`). | [ ] | |
| 8 | Verificar la disponibilidad para consulta | `GET /api/v1/financial-records?reservation_id=R-200` (o vista `v_financial_records`) | `R-200` | El registro confirmado aparece como registro financiero de la reserva, con su monto y referencia externa (RF-011, CE-003). | [ ] | También debe verse en el informe de UC13. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Firma ausente o inválida | Enviar el webhook sin `x-signature` o con `v1` alterado | firma inválida | `401 UNAUTHENTICATED`; no se procesa nada y la intención no cambia (contract §3.1, catálogo §3.4). | [ ] | |
| 2 | Payload incompleto | Omitir `action`, `data.id` o `type` | payload incompleto | `400 VALIDATION_ERROR`; la intención y los registros no se alteran. | [ ] | |
| 3 | Base de datos no disponible al acusar recibo | Detener PostgreSQL y enviar la notificación | N/A | `500 INTERNAL_ERROR`; nada queda persistido y la Pasarela reintenta (contract §3.7). | [ ] | |
| 4 | La Pasarela responde `404` al consultar el detalle | Simular `404` en `GET /v1/advanced_payments/{id}` | N/A | `200 OK` al webhook; la intención **no** se cierra en `REJECTED` y el evento queda en `operational_failure` (un `404` no es veredicto financiero). | [ ] | |
| 5 | `data.id` o `type` sin intención asociada | Enviar `data.id` inexistente con `type=merchant_order` | `mp_disp_desconocido` | `200 OK` y registro en `operational_failure`; no se crea ningún `RegistroDeDispersión`. | [ ] | |
| 6 | Falla en el paso 2 asíncrono | Simular `503`/timeout en la consulta a la Pasarela | N/A | La respuesta HTTP ya fue `200 OK`; la cola reintenta hasta 5 intentos con backoff exponencial base 1 s y al agotarlos envía a la DLQ y registra en `operational_failure` (README §4.4). | [ ] | |
| 7 | Registro duplicado | Reprocesar el mismo `data_id` desde la cola | mismo mensaje | El duplicado se confirma sin crear otro registro ni alterar el ya confirmado (contract §6). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Si la respuesta de la Pasarela no informa `amount`, el registro usa el monto de la intención (contrato de liquidación).
* **EC-002** : Si no viene `status_detail`, el registro queda con detalle nulo y el resultado sigue siendo válido (plan UC10, contrato de liquidación).
* **EC-003** : El depósito se unifica en el monto transferido, pero el desglose (`deposit_amount`) se conserva en el registro interno (RF-016).
* **EC-004** : `BigDecimal` de 4 decimales internos → monto del registro y salidas REST redondeados `HALF_UP` a 2 decimales (RNF-002, README §3.1).
* **EC-005** : El registro confirmado es inmutable: `UPDATE`/`DELETE` directos sobre `settlement_record` deben ser rechazados por la base (disparadores de la migración).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
