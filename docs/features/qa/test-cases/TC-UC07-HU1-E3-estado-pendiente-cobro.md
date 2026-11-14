### Test Case Specification: Estado de la reserva · HU1 · E3 Estado "pendiente": continúa TTL y dispara el cobro
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `PENDING` con el token del medio de pago, el sistema lo reconoce como continuación del TTL ya iniciado (sin reiniciarlo), ejecuta "Procesar cobro" para la reserva, registra la identidad de la operación y **no** dispara reembolso ni liquidación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Feature Spec UC05 | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. Las comprobaciones controladas de modo A se identifican en la segunda sección.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070103` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070103","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070103';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070103/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Delegación de PENDING a cobro

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Notificar el estado inicial | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070103","status":"INITIATED","status_changed_at":"2026-12-05T14:15:00Z"}` | El evento se consume y no genera operaciones financieras ni filas en `reservation_status_log`. | [X] |  |
| 2 | Solicitar el cobro | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070103","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | UC07 delega a UC05. Intención en `CAPTURED` y una fila de cobro por `400000.00`. | [X] |  |
| 3 | Verificar identidad y delegación | `SELECT status, operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070103';` y `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070103';` | Marca PENDING `2026-12-05T14:30:00Z` | Una fila `PENDING / CHARGE` procesada y una intención por `400000.00`. La marca recibida se conserva. | [X] |  |
| 4 | Verificar el comando | `SELECT o.routing_key, o.payload, o.published_at FROM outbox_message o JOIN charge_intent ci ON o.payload::jsonb->>'charge_intent_id' = ci.id::text WHERE ci.reservation_id = 'c0a80100-0000-4000-8000-000000070103' AND o.routing_key = 'charge.command';` | Intención de esta reserva | Un comando publicado con ID y email; sin token. | [X] |  |
| 5 | Verificar ausencia de efectos ajenos | `SELECT count(*) FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070103';`; `SELECT count(*) FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070103';`; `SELECT count(*) FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070103';` | Misma reserva | Los tres recuentos son cero. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | PENDING repetido | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | Mismo cuerpo y marca, Message-Id diferente | Una fila PENDING en el log y una intención. | [X] |  |
| 2 | Datos incompletos | En escenarios limpios, omitir token y luego omitir el cálculo de UC04 | Variantes independientes del PENDING | Fallo controlado en `operational_failure` para cada reserva, sin intención ni cobro. | [X] |  |
| 3 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,ReservationStatusEndToEndIntegrationTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [X] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. UC07 no persiste `INITIATED` porque no planifica acciones financieras. El TTL pertenece a Reservas y Operaciones; aquí se comprueban la marca temporal y los efectos de `PENDING`.
* **EC-002**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
