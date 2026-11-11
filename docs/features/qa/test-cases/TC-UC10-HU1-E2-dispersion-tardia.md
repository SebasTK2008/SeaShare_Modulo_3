### Test Case Specification: UC10 Liquidar fondos · HU1 · E2 Dispersión por cancelación tardía (100 % alquiler)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft  **Execution** : Pendiente para esta revisión

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación tardía (<24 h), el sistema recupera el monto de alquiler registrado y registra en el **libro interno simulado** la liquidación del **100 %** como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico, y sin que el registro implique una transferencia real de fondos.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 (D-UC10-18, libro simulado) | Plan Document | [plan.md](../../010-liquidar-fondos-alquiler/plan.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato del libro interno de liquidación | Internal contract | [pasarela-comando-liquidacion.md](../../../technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Libro interno** con `GATEWAY_SETTLEMENT_MODE=simulated` para UC10. El cobro de preparación y el reembolso asociado a la cancelación usan modo B.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000100102` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsos. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000100102","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000100102';` | [ ] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000100102/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [ ] |  |
| 3 | Preparar el cobro de esta reserva | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000100102","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Intención en `CAPTURED` y una fila de cobro por `400000.00`. Verificar con `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000100102';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000100102';`. | [ ] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Dispersión tardía en el libro interno

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Notificar la cancelación | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000100102","status":"CANCELLED_STRICT","status_changed_at":"2026-12-05T15:00:00Z"}` | Se solicita la liquidación del alquiler y el reembolso de `35000.00` mediante UC09. | [ ] |  |
| 2 | Verificar la intención | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, status, gateway_capability, external_reference, status_detail, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000100102';` | Trigger `CANCELLED_STRICT` | Una intención con `scope = CANCELLATION_COMPENSATION`, `gateway_capability = STANDARD_SETTLEMENT`, `amount = 350000.00`, `commission_amount = 0` y `deposit_amount = 35000.00`. | [ ] |  |
| 3 | Verificar resultado del libro | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, status, gateway_capability, external_reference, status_detail, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000100102';` | `GATEWAY_SETTLEMENT_MODE=simulated` | `COMPLETED`, referencia `sim-settlement-<idempotency_key>` y detalle `simulated internal settlement`. | [ ] |  |
| 4 | Verificar registro y comisión | `SELECT id, settlement_intent_id, amount, rental_gross_amount, insurance_amount, deposit_amount FROM settlement_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000100102';` y `SELECT count(*) FROM commission_record c JOIN settlement_intent si ON si.id = c.settlement_intent_id WHERE si.reservation_id = 'c0a80100-0000-4000-8000-000000100102';` | Importe `350000.00` | Una fila de liquidación, alquiler bruto `350000.00`, seguro cero, depósito asociado `35000.00` y cero registros de comisión. | [ ] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Cancelación repetida | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | Mismo cuerpo y marca temporal, Message-Id diferente | Una intención, una referencia simulada y un registro de liquidación. | [ ] |  |
| 2 | Contrato y fallos controlados de pasarela (modo A) | `.\mvnw.cmd "-Dtest=SettlementCalculatorTest,RequestSettlementServiceTest,SendSettlementToGatewayServiceTest,SimulatedSettlementGatewayAdapterTest,GatewayProfilesTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas. La evidencia de contrato y errores controlados se registra por separado del recorrido funcional. | [ ] |  |

##### Edge Cases & Boundary Conditions

* **EC-001**. La comprobación de UC10 se realiza en el libro interno. El cobro de preparación y el reembolso que acompaña a la cancelación usan sandbox; sus credenciales deben estar configuradas en el mismo entorno.
* **EC-002**. La respuesta del worker es asíncrona. Consultar hasta alcanzar el resultado esperado y registrar el estado observado si no converge. PENDING_SEND puede resolverse antes de la primera consulta.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
