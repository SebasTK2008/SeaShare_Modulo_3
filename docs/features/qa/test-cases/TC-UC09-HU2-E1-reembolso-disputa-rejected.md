### Test Case Specification: UC09 Reembolsos · HU2 · E1 Reembolso por disputa `REJECTED` o ausencia de reclamo
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Approved  **Execution** : 2026-11-14 · release/0.1.0 · PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibirse el estado `REJECTED` de una disputa de garantía, el sistema recupera el depósito cobrado y registrado internamente y solicita a la Pasarela su liberación o reembolso íntegro (100 %), sin retención parcial y sin recibir montos desde el Sistema de Reservas y Operaciones. El resultado se registra cuando lo confirma la pasarela.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../009-reembolsar-dinero-arrendatario/spec.md) |
| Contrato evento UC08 (disparador) | Events contract | [UC08-disputa-garantia.md](../../../technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. Las comprobaciones controladas de modo A se identifican en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000090201` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000090201","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000090201/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |
| 3 | Preparar el cobro de esta reserva | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000090201","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Intención en `CAPTURED` y una fila de cobro por `400000.00`. Verificar con `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';`. | [X] |  |
| 4 | Abrir seguimiento del depósito | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000090201","status":"COMPLETED","status_changed_at":"2026-12-05T15:00:00Z"}` | Una liquidación estándar de UC10 y depósito en `HELD`. `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso por disputa rechazada o ausencia de reclamo

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Notificar la resolución REJECTED | RabbitMQ → `seashare.reservations` → `reservation.dispute.updated` | `{"reservation_id":"c0a80100-0000-4000-8000-000000090201","dispute_id":"d0a80100-0000-4000-8000-000000090201","status":"REJECTED","status_changed_at":"2026-12-05T16:00:00Z","event_key":"qa-UC09-HU2-E1-1"}` | Evento procesado y delegado a UC09. | [X] |  |
| 2 | Verificar la disputa registrada | `SELECT dispute_id, event_key, status, processed_at FROM dispute_event_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | Disputa `d0a80100-0000-4000-8000-000000090201`, event_key `qa-UC09-HU2-E1-1` | Una fila `REJECTED` con `processed_at` no nulo. | [X] |  |
| 3 | Verificar intención y depósito | `SELECT id, trigger, scope, status, status_detail, amount, charge_intent_id, dispute_id, external_reference, idempotency_key FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` y `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | Trigger `DISPUTE_REJECTED`, scope `DEPOSIT_ONLY` | Una intención de `35000.00`, clave UUID v4, referencia al cobro original y depósito en `REFUND_REQUESTED`. El dispute_id coincide con el evento. | [X] |  |
| 4 | Verificar el comando interno | `SELECT o.payload, o.published_at FROM outbox_message o JOIN refund_intent ri ON o.payload::jsonb->>'refund_intent_id' = ri.id::text WHERE ri.reservation_id = 'c0a80100-0000-4000-8000-000000090201' AND o.routing_key = 'refund.command';` | ID de la intención de reembolso | Un comando `refund.command` publicado con `refund_intent_id`. El monto se obtiene de los registros internos. | [X] |  |
| 5 | Verificar el reembolso del proveedor | Panel de Mercado Pago o `GET https://api.mercadopago.com/v1/payments/{payment_id}/refunds` | `payment_id = charge_intent.external_reference`; `refund_id = refund_intent.external_reference` | Un reembolso de `35000.00 COP` cuyo ID coincide con la intención. El POST saliente usa `{"amount":35000.00}` y la clave de la intención. | [X] |  |
| 6 | Verificar el resultado y el registro | `SELECT id, trigger, scope, status, status_detail, amount, charge_intent_id, dispute_id, external_reference, idempotency_key FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` y `SELECT id, refund_intent_id, amount, released_amount, transaction_cost, external_reference FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | Importe `35000.00` | Tras `approved` del proveedor, intención `COMPLETED` y exactamente un `refund_record`. Si la respuesta inicial sigue en curso, esperar el resultado externo y volver a consultar. | [X] |  |
| 7 | Verificar el neto liberado | `SELECT id, refund_intent_id, amount, released_amount, transaction_cost, external_reference FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | `amount`, `transaction_cost`, `released_amount` | `released_amount = amount - transaction_cost`. Si el costo es NULL, el neto coincide con amount. | [X] |  |
| 8 | Verificar liquidaciones | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, status, gateway_capability, external_reference, status_detail, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000090201';` | Preparación con COMPLETED | Se conserva la liquidación estándar del alquiler. Hay cero liquidaciones de depósito por la resolución REJECTED. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Evento repetido | RabbitMQ → `seashare.reservations` → `reservation.dispute.updated` | Republicar el mismo cuerpo, misma marca y misma event_key cuando aplique; Message-Id diferente | Misma intención, mismo refund_id, un registro de reembolso y desenlace único del depósito. | [X] |  |
| 2 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=RefundCalculatorTest,RequestRefundServiceTest,SendRefundToGatewayServiceTest,MercadoPagoRefundAdapterTest,DisputeInformationServiceTest,DisputeNotificationListenerContractTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. Para ausencia de reclamo se recibe el mismo `REJECTED`, sin campo de motivo. La preparación COMPLETED abre el seguimiento y genera la liquidación estándar; la resolución solo dispone del depósito.
* **EC-002**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
