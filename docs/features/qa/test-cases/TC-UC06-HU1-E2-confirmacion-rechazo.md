### Test Case Specification: Solicitar confirmación de pago · HU1 · E2 Confirmación de un cobro rechazado
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/006-solicitar-confirmacion-de-pago/spec.md](../../006-solicitar-confirmacion-de-pago/spec.md)  **Status** : Approved  **Execution** : 2026-11-12 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC06-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una `IntenciónDeCobro` marcada como rechazada por "Procesar cobro", el sistema devuelve el estado externo correspondiente y su detalle, **sin** reportar un cobro aprobado y **sin** exponer montos cobrados, de modo que Reservas pueda revertir el bloqueo temporal del activo.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC06 | Specification Document | [spec.md](../../006-solicitar-confirmacion-de-pago/spec.md) |
| Contrato REST UC06 | REST contract | [UC06-confirmacion-pago.md](../../../technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Feature Spec UC05 (resultado del cobro) | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Preparación en modo B**. UC06 consulta el resultado persistido. La cobertura de estados controlados usa modo A, indicada en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000060102` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `OTHE`. Sustituye `<TOKEN_OTHE>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Para la variante de nuevo intento, tokeniza otra tarjeta con titular `APRO`. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000060102","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060102';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000060102/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |
| 3 | Preparar el cobro de esta reserva | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000060102","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_OTHE>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Intención en `REJECTED`, sin filas en `charge_record`. Verificar con `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060102';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060102';`. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta del cobro rechazado

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el estado de partida | `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060102';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000060102';` | Titular `OTHE` | Intención en `REJECTED` y cero filas de cobro. | [X] |  |
| 2 | Consultar confirmación | `GET http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000060102/payment-confirmation` | Header `Accept: application/json` | HTTP 200, `Content-Type: application/json`, `status = REJECTED`, `charged_amount = null`. `authorized_amount`, `captured_amount` y `released_amount` en `null` para este escenario sin reembolsos. | [X] |  |
| 3 | Comparar detalle y referencia | Comparar respuesta REST con la intención de esta reserva | `detail` y `external_reference` | Coinciden con `status_detail` y `charge_intent.external_reference`; si BD muestra NULL, el JSON contiene `null`. | [X] |  |
| 4 | Comprobar lectura sin cambios | Repetir el GET y comparar las consultas anteriores | Dos consultas consecutivas | Mismos IDs, montos y número de registros. La ruta UC06 resuelve desde BD. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | UUID inválido | `GET http://localhost:8080/api/v1/reservations/R-100/payment-confirmation` | `R-100` | HTTP 400 `VALIDATION_ERROR`, `retryable: false`. | [X] |  |
| 2 | Reserva sin intención | `GET http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000999999/payment-confirmation` | UUID reservado para ausencia de cobro | HTTP 404 `CHARGE_INTENT_NOT_FOUND`, `retryable: true`. Confirmar previamente que ese UUID no tiene intención. | [X] |  |
| 3 | Consultar después de un nuevo intento | RabbitMQ → `seashare.reservations` → `reservation.status.changed` y repetir el GET | `{"reservation_id":"c0a80100-0000-4000-8000-000000060102","status":"PENDING","status_changed_at":"2026-12-05T14:45:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Al capturarse el nuevo cobro, devuelve `APPROVED` y la referencia nueva. La intención rechazada sigue en BD. | [X] |  |
| 4 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=GetPaymentConfirmationServiceTest,PaymentConfirmationLatestIntentIntegrationTest,UC06PaymentConfirmationContractTest,PaymentConfirmationStatusTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
