### Test Case Specification: Procesar cobro · HU1 · E2 Uso de un medio de pago tokenizado sin almacenar datos sensibles
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../005-procesar-cobro/spec.md)  **Status** : Approved  **Execution** : 2026-11-12 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el sistema utiliza **únicamente** el token o referencia segura del medio de pago para iniciar la operación, y que no recibe ni persiste el número completo de la tarjeta, el código de seguridad ni otros datos sensibles.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. Las comprobaciones controladas de modo A se identifican en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000050102` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000050102","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050102';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000050102/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Uso del medio tokenizado

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Solicitar el cobro | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000050102","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | UC07 delega a UC05. Intención en `CAPTURED` y una fila de cobro por `400000.00`. | [X] |  |
| 2 | Verificar el almacenamiento permitido | `SELECT payment_token_ref IS NOT NULL AS tiene_token, payment_method_type, last_four FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050102';` y `SELECT payment_method_type, last_four FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000050102';` | `VISA`, `6260` | Token presente en la intención, marca y últimos cuatro disponibles. El registro de cobro conserva marca y últimos cuatro. | [X] |  |
| 3 | Verificar el comando interno | `SELECT o.routing_key, o.payload, o.published_at FROM outbox_message o JOIN charge_intent ci ON o.payload::jsonb->>'charge_intent_id' = ci.id::text WHERE ci.reservation_id = 'c0a80100-0000-4000-8000-000000050102' AND o.routing_key = 'charge.command';` | Comando de esta intención | Incluye `charge_intent_id` y `payer_email`; excluye el token y los datos completos de la tarjeta. | [X] |  |
| 4 | Revisar logs y contrato saliente | Logs de la aplicación durante este cobro y cobertura A de este TC | Token utilizado y datos de la tarjeta de prueba | Los logs excluyen el token, PAN, CVV y vencimiento. El adaptador envía el token y la metadata permitida. Guardar evidencia redactada. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | PENDING sin token | Repetir en reserva limpia, omitiendo `payment_token_ref` | Mismo cuerpo PENDING, sin el campo | Se registra el fallo de validación y no se crea intención ni cobro. | [X] |  |
| 2 | PENDING repetido | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | Mismo cuerpo y marca temporal, Message-Id diferente | Un solo cobro y una sola intención. | [X] |  |
| 3 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=MercadoPagoChargeAdapterTest,ReservationStatusMessageMapperTest,ProcessChargeServiceTest,ChargeIntentTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
