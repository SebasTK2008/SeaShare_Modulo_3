### Test Case Specification: Procesar cobro · HU1 · E1 Envío exitoso de la solicitud de cobro a la Pasarela de pago
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../005-procesar-cobro/spec.md)  **Status** : Draft  **Execution** : Pendiente para esta revisión

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verifica que el evento de cobro crea una intención por el total calculado, envía el token a Mercado Pago y registra el resultado del proveedor.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. Las comprobaciones controladas de modo A se identifican en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000050101` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000050101","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050101';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000050101/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Envío de la solicitud de cobro

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Solicitar el cobro | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000050101","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | UC07 delega a UC05. Intención en `CAPTURED` y una fila de cobro por `400000.00`. | [X] |  |
| 2 | Verificar intención y comando interno | `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050101';` y `SELECT o.routing_key, o.payload, o.published_at FROM outbox_message o JOIN charge_intent ci ON o.payload::jsonb->>'charge_intent_id' = ci.id::text WHERE ci.reservation_id = 'c0a80100-0000-4000-8000-000000050101' AND o.routing_key = 'charge.command';` | Total `400000.00` | Una intención con UUID v4 en `idempotency_key`, un comando `charge.command` publicado con `charge_intent_id` y `payer_email`, sin token. | [X] |  |
| 3 | Verificar el cobro del proveedor | Panel de Mercado Pago o `GET https://api.mercadopago.com/v1/payments/{payment_id}` | `payment_id = charge_intent.external_reference` | Un pago `approved` de `400000.00 COP`, `external_reference = c0a80100-0000-4000-8000-000000050101` y descripción `Reserva c0a80100-0000-4000-8000-000000050101`. | [X] |  |
| 4 | Verificar el registro del resultado | `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050101';` | Misma reserva | exactamente un registro de cobro con el desglose calculado. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Evento de negocio repetido | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | Publicar dos veces el mismo PENDING, con idénticos cuerpo y `status_changed_at`, y Message-Id diferente | Una intención, una operación monetaria y un registro de cobro. Comparar los IDs y el recuento en las consultas de este TC. | [X] |  |
| 2 | Tipo genérico de tarjeta (B-09) | Recrear el escenario completo con reserva limpia y token nuevo | Cambiar únicamente `payment_method_type` a `CREDIT_CARD` | El adaptador omite `payment_method_id`. Guardar el estado y error reales del proveedor. Si devuelve `4001`, registrar defecto sobre la exigencia de marca. | [X] |  |
| 3 | Repetición del POST en el proveedor (B-07) | Reenviar `POST https://api.mercadopago.com/v1/payments` inmediatamente después del cobro | Mismos token, email, reserva y `X-Idempotency-Key` de la intención; cuerpo indicado debajo | El proveedor devuelve el mismo `payment_id`. BD conserva una intención y un registro. | [X] |  |
| 4 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=ProcessChargeServiceTest,SendChargeToGatewayServiceTest,MercadoPagoChargeAdapterTest,ChargeCommandListenerTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. Para la repetición HTTP de B-07, el cuerpo original de este caso es `{"transaction_amount":400000,"token":"<TOKEN_APRO>","description":"Reserva c0a80100-0000-4000-8000-000000050101","installments":1,"payment_method_id":"visa","payer":{"email":"<EMAIL_COMPRADOR>"},"external_reference":"c0a80100-0000-4000-8000-000000050101"}`. Usa la credencial de prueba en `Authorization: Bearer <ACCESS_TOKEN_TEST>` y la clave persistida en `X-Idempotency-Key`.
* **EC-002**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
