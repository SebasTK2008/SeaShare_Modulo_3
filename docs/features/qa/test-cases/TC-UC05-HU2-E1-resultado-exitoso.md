### Test Case Specification: Procesar cobro · HU2 · E1 La Pasarela reporta el cobro como exitoso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../005-procesar-cobro/spec.md)  **Status** : Approved  **Execution** : 2026-11-12 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una solicitud de cobro previamente enviada a la Pasarela, cuando la Pasarela reporta que la transacción fue exitosa el sistema registra el estado, el monto cobrado, el detalle y la referencia externa, crea el `RegistroDeCobro` inmutable y deja la operación disponible para "Solicitar confirmación de pago" (UC06).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Contrato UC06 (consulta del resultado) | REST contract | [UC06-confirmacion-pago.md](../../../technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. Las comprobaciones controladas de modo A se identifican en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000050201` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000050201","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050201';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000050201/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro del cobro aprobado y webhook real

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Solicitar el cobro | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000050201","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | UC07 delega a UC05. Intención en `CAPTURED` y una fila de cobro por `400000.00`. | [X] |  |
| 2 | Verificar el resultado financiero | `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050201';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050201';` | Cobro de `400000.00` | `CAPTURED`, un registro y desglose `350000.00 / 15000.00 / 35000.00`. La respuesta del POST puede registrar el resultado antes del webhook. | [X] |  |
| 3 | Verificar la entrega real del webhook | Historial de webhooks de Mercado Pago y `SELECT o.created_at, o.payload, o.published_at FROM outbox_message o JOIN charge_intent ci ON o.payload::jsonb->>'data_id' = ci.external_reference WHERE ci.reservation_id = 'c0a80100-0000-4000-8000-000000050201' AND o.routing_key = 'gateway.notification' ORDER BY o.created_at;` | `payment_id = charge_intent.external_reference` | Entrega real HTTP 200 y notificación `gateway.notification` con el mismo ID. Correlacionar el GET del worker al pago en la telemetría HTTP disponible. | [X] |  |
| 4 | Consultar el resultado publicado | `GET http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000050201/payment-confirmation` | Sin cuerpo | HTTP 200, `status = APPROVED`, `charged_amount = "400000.00"`, referencia y detalle iguales a BD. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Entrega repetida | Reenviar la entrega del proveedor desde su panel | Mismo pago | Mismo estado, mismo `charge_record.id` y exactamente una fila para esta reserva. | [X] |  |
| 2 | Webhook sin firma | `POST http://localhost:8080/api/v1/webhook/gateway?data.id=<payment_id>&type=payment` | Body `{"action":"payment.updated","type":"payment"}`, Content-Type JSON, sin `x-signature` | HTTP 401 `UNAUTHENTICATED`, sin cambios en la intención. | [X] |  |
| 3 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=GatewayWebhookControllerTest,ChargePaymentWebhookHandlerTest,RegisterChargeResultServiceTest,MercadoPagoChargeAdapterTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. La evidencia del webhook incluye ID del pago, hora de entrega, HTTP 200 y outbox. Si el GET del worker no es observable, deja esa comprobación bloqueada. El estado financiero por sí solo no acredita el recorrido del webhook.
* **EC-002**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
