### Test Case Specification: Solicitar confirmación de pago · HU1 · E3 Consulta de un cobro aún en proceso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/006-solicitar-confirmacion-de-pago/spec.md](../../006-solicitar-confirmacion-de-pago/spec.md)  **Status** : Approved  **Execution** : 2026-11-12 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC06-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una `IntenciónDeCobro` todavía en curso, el sistema devuelve fielmente el estado "en proceso" —sin inventar ni anticipar una aprobación o un rechazo— junto con los montos y referencias disponibles, en una consulta de solo lectura que no contacta a la Pasarela.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC06 | Specification Document | [spec.md](../../006-solicitar-confirmacion-de-pago/spec.md) |
| Contrato REST UC06 | REST contract | [UC06-confirmacion-pago.md](../../../technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Feature Spec UC05 (estados internos) | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Preparación en modo B**. UC06 consulta el resultado persistido. La cobertura de estados controlados usa modo A, indicada en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000060103` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `CONT`. Sustituye `<TOKEN_CONT>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000060103","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060103';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000060103/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |
| 3 | Preparar el cobro de esta reserva | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000060103","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_CONT>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Intención en `IN_PROCESS`, sin filas en `charge_record`. Verificar con `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060103';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060103';`. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta del cobro en proceso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el estado de partida | `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060103';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060103';` | Titular `CONT` | Intención en `IN_PROCESS` y cero filas de cobro. | [X] |  |
| 2 | Consultar confirmación | `GET http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000060103/payment-confirmation` | Header `Accept: application/json` | HTTP 200, `Content-Type: application/json`, `status = IN_PROCESS`, `charged_amount = null`. `authorized_amount`, `captured_amount` y `released_amount` en `null` para este escenario sin reembolsos. | [ ] |  |
| 3 | Comparar detalle y referencia | Comparar respuesta REST con la intención de esta reserva | `detail` y `external_reference` | Coinciden con `status_detail` y `charge_intent.external_reference`; si BD muestra NULL, el JSON contiene `null`. | [X] |  |
| 4 | Comprobar lectura sin cambios | Repetir el GET y comparar las consultas anteriores | Dos consultas consecutivas | Mismos IDs, montos y número de registros. La ruta UC06 resuelve desde BD. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | UUID inválido | `GET http://localhost:8080/api/v1/reservations/R-100/payment-confirmation` | `R-100` | HTTP 400 `VALIDATION_ERROR`, `retryable: false`. | [X] |  |
| 2 | Reserva sin intención | `GET http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000999999/payment-confirmation` | UUID reservado para ausencia de cobro | HTTP 404 `CHARGE_INTENT_NOT_FOUND`, `retryable: true`. Confirmar previamente que ese UUID no tiene intención. | [X] |  |
| 3 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=GetPaymentConfirmationServiceTest,PaymentConfirmationLatestIntentIntegrationTest,UC06PaymentConfirmationContractTest,PaymentConfirmationStatusTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. Si el sandbox no devuelve `pending` o `in_process`, o el resultado cambia antes de consultar, registra el recorrido B como bloqueado o repítelo en un escenario limpio. Las pruebas A indicadas cubren el mapeo a `IN_PROCESS`.
* **EC-002**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
