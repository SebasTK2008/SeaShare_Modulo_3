### Test Case Specification: Disputa de garantía · HU1 · E3 Disputa completada y liquidación al Propietario
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md](../../008-brindar-informacion-de-disputa-de-garantia/spec.md)  **Status** : Approved  **Execution** : 2026-11-14 · release/0.1.0 · PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC08-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir `COMPLETED`, el sistema registra la resolución, aplica el desenlace `SETTLEMENT_REQUESTED` sobre el depósito y solicita a UC10 la liquidación **total** del depósito cobrado y registrado al Propietario, sin recibir montos ni instrucciones de pasarela desde el Sistema de Reservas y Operaciones.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC08 | Specification Document | [spec.md](../../008-brindar-informacion-de-disputa-de-garantia/spec.md) |
| Implementation Plan UC08 | Plan Document | [plan.md](../../008-brindar-informacion-de-disputa-de-garantia/plan.md) |
| Contrato evento de disputa | Events contract | [UC08-disputa-garantia.md](../../../technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Contrato del libro interno de liquidación | Internal contract | [pasarela-comando-liquidacion.md](../../../technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La liquidación de UC10 es un **libro interno simulado** (`GATEWAY_SETTLEMENT_MODE=simulated`): no hay petición HTTP a Mercado Pago, journal de pasarela ni transferencia real de fondos. La evidencia son `settlement_intent`, `settlement_record` y `outbox_message`.
* **UC08 no se integra con Mercado Pago**: la disputa la envía el Sistema de Reservas y Operaciones como `reservation.dispute.updated`. La preparación del cobro sí usa la API real de Mercado Pago (modo B); no se requiere un contracargo real.
* UC08 no expone REST ni responde a Reservas: se verifica por la cola `finance.guarantee-dispute.v1`, la base de datos y las colas internas de comando.
* UC10 es el ejecutor de la liquidación: aquí se verifica la solicitud emitida por UC08 y su anotación en el libro interno.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS registrado para `payment` cuando se comprueben entregas reales.
* `GATEWAY_SETTLEMENT_MODE=simulated` (valor por defecto).
* RabbitMQ arriba, cola `finance.guarantee-dispute.v1` y su DLQ declaradas.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000080103` ausente. Este caso prepara todos sus datos. Puede ejecutarse sobre volúmenes de QA recreados antes de repetirlo (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. Comprador y vendedor de prueba MCO. Para este caso tokeniza la Visa crédito de prueba con titular `APRO`. Sustituye `<TOKEN_APRO>` por ese token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega. Los cuerpos completos figuran en las tablas.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000080103","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103';` | [X] | Fila creada con `base_rate = 350000.0000`, fechas y 1 pasajero. |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000080103/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] | HTTP 200; `350000.00 / 15000.00 / 35000.00 / 400000.00`. |
| 3 | Preparar el cobro real | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000080103","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Intención en `CAPTURED` y una fila de cobro por `400000.00` con depósito `35000.00`. `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103';` | [X] | Pago real `1355374685` `approved`; intención `6bc343bb-be42-4976-80f8-8871617a0d56` `CAPTURED`; registro `400000.00` con depósito `35000.00`. |
| 4 | Abrir el seguimiento del depósito | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000080103","status":"COMPLETED","status_changed_at":"2026-12-05T15:00:00Z"}` | `deposit_disposition = HELD` para la reserva. La preparación genera además la liquidación estándar del alquiler (`282500.00`, comisión `52500.00`), que no es objeto de este caso y se distingue por `trigger`. `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103';` | [X] | `HELD`; liquidación estándar `282500.00` + comisión `52500.00` en `COMPLETED`. |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Disputa `COMPLETED` → liquidación total del depósito

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Notificar la resolución `COMPLETED` | RabbitMQ → `seashare.reservations` → `reservation.dispute.updated` | `{"reservation_id":"c0a80100-0000-4000-8000-000000080103","dispute_id":"d0a80100-0000-4000-8000-000000080103","status":"COMPLETED","status_changed_at":"2026-12-05T16:00:00Z","event_key":"qa-UC08-HU1-E3-1"}` | Evento procesado y delegado a UC10; `ack`; sin respuesta de negocio al productor. | [X] | Log `Received`/`Parsed`; cola de disputa y DLQ en `0` tras el `ack`; sin `reply_to`. |
| 2 | Verificar el registro de la resolución | `SELECT dispute_id, event_key, status, status_changed_at, processed_at FROM dispute_event_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103' AND event_key = 'qa-UC08-HU1-E3-1';` | Disputa `d0a80100-0000-4000-8000-000000080103`, `event_key = 'qa-UC08-HU1-E3-1'` | Una fila `COMPLETED` con la marca original `status_changed_at` y `processed_at` no nulo. | [X] | `status_changed_at = 2026-12-05T16:00:00Z`; `processed_at = 2026-11-14T05:50:16Z`. |
| 3 | Verificar el desenlace del depósito | `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103';` | Disputa `d0a80100-0000-4000-8000-000000080103` | `disposition = 'SETTLEMENT_REQUESTED'` aplicado una sola vez por *compare-and-set*; `dispute_id` coincide con el evento. | [X] | `SETTLEMENT_REQUESTED` con `dispute_id = d0a80100-0000-4000-8000-000000080103`; fila única. |
| 4 | Verificar la intención de liquidación | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, charge_intent_id, dispute_id, status, gateway_capability, external_reference, status_detail, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103' AND trigger = 'DISPUTE_COMPLETED';` | `trigger = 'DISPUTE_COMPLETED'` | Una fila con `scope = 'DEPOSIT'`, `amount = 35000.00`, `commission_amount = 0.00`, `deposit_amount = 35000.00`, `charge_intent_id` del cobro original, `dispute_id` del evento, `gateway_capability = 'STANDARD_SETTLEMENT'` e `idempotency_key` UUID v4. El estado puede ser `PENDING_SEND`, `IN_PROCESS` o `COMPLETED`; no se exige observar el intermedio. | [X] | Intención `f081ae90-34c9-49a7-952a-bd84aab0df2a`; `COMPLETED`; `charge_intent_id = 6bc343bb-be42-4976-80f8-8871617a0d56`; `idempotency_key = 0e5e171b-2db8-402f-8b48-61a5585b6900` (UUID v4). |
| 5 | Verificar el comando interno | `SELECT routing_key, payload, published_at FROM outbox_message WHERE routing_key = 'settlement.command' AND payload::jsonb->>'settlement_intent_id' = '<INTENT_ID>';` | ID de la intención del paso 4 | Un comando `settlement.command` publicado con solo `settlement_intent_id`; sin montos ni datos de la disputa. | [X] | 1 comando con `{"settlement_intent_id":"f081ae90-34c9-49a7-952a-bd84aab0df2a"}`. |
| 6 | Verificar la entrega al libro interno | `SELECT status, external_reference, status_detail FROM settlement_intent WHERE id = '<INTENT_ID>';` | `amount = 35000.00` | `COMPLETED`, `external_reference = sim-settlement-<idempotency_key>` y `status_detail = simulated internal settlement`. No hay petición HTTP a Mercado Pago. | [X] | `COMPLETED`; `sim-settlement-0e5e171b-2db8-402f-8b48-61a5585b6900`; `simulated internal settlement`. |
| 7 | Verificar el registro inmutable | `SELECT id, settlement_intent_id, amount, rental_gross_amount, insurance_amount, deposit_amount, external_reference FROM settlement_record WHERE settlement_intent_id = '<INTENT_ID>';` | N/A | Exactamente una fila con `amount = 35000.00`, `rental_gross_amount = 350000.00`, `insurance_amount = 0.00`, `deposit_amount = 35000.00` y la misma referencia externa. | [X] | 1 fila: `35000.00 / 350000.00 / 0.00 / 35000.00`, misma referencia externa. |
| 8 | Verificar que no se genera comisión | `SELECT count(*) FROM commission_record WHERE settlement_intent_id = '<INTENT_ID>';` | N/A | `0` filas: la liquidación del depósito no lleva comisión (RF-014, CE-002). | [X] | `0` filas. |
| 9 | Verificar exclusión mutua | `SELECT (SELECT count(*) FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103') AS refunds, (SELECT count(*) FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000080103') AS refund_records;` | N/A | `0` y `0`: el depósito se liquida, no se reembolsa (RF-008, RF-010, CE-003). | [X] | `0` y `0`. |
| 10 | Verificar unidireccionalidad | Revisar las propiedades del mensaje y el outbox de la reserva | N/A | Sin `reply_to` y sin respuesta de negocio hacia Reservas; el `ack` AMQP y el comando interno no son respuestas de UC08 (RF-009). | [X] | Outbox: `charge.command`, `gateway.notification` y 2 `settlement.command`; sin respuesta a Reservas; colas en `0`. |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Disputa `COMPLETED` sin cobro capturado | Preparar solo `reservation.info.provided` para la reserva `c0a80100-0000-4000-8000-000000080104` y publicar `reservation.dispute.updated` | `{"reservation_id":"c0a80100-0000-4000-8000-000000080104","dispute_id":"d0a80100-0000-4000-8000-000000080104","status":"COMPLETED","status_changed_at":"2026-12-05T16:10:00Z","event_key":"qa-UC08-HU1-E3-2"}` | `ack`; evento registrado y procesado en `dispute_event_log`; `operational_failure` de UC10 con `reason = 'CHARGE_INTENT_NOT_FOUND'`; cero intenciones y registros de liquidación o reembolso. | [X] | Evento procesado; UC10 `CHARGE_INTENT_NOT_FOUND`; `0` intenciones, registros, reembolsos y disposiciones. |
| 2 | `COMPLETED` repetido | Republicar el cuerpo del paso 1 principal con `Message-Id` nuevo | Mismo `event_key = 'qa-UC08-HU1-E3-1'` | Sigue una sola fila en `dispute_event_log`, una sola intención `DISPUTE_COMPLETED`, un solo `settlement_record` y un solo comando; mismos IDs y claves. | [X] | `1/1/1/1` antes y después; mismo `settlement_intent_id` e `idempotency_key`. |
| 3 | `REJECTED` después de `COMPLETED` | Publicar con la misma reserva y disputa, `event_key` nuevo | `{"reservation_id":"c0a80100-0000-4000-8000-000000080103","dispute_id":"d0a80100-0000-4000-8000-000000080103","status":"REJECTED","status_changed_at":"2026-12-05T16:05:00Z","event_key":"qa-UC08-HU1-E3-3"}` | `ack`; se conserva `SETTLEMENT_REQUESTED`; `operational_failure` con `deposit_disposition=SETTLEMENT_REQUESTED;requested=REFUND_REQUESTED`; sin reembolso ni segunda liquidación. | [X] | `SETTLEMENT_REQUESTED` conservado; UC08 `INTERNAL_ERROR` con el detalle esperado; `0` reembolsos; `1` liquidación de depósito. |
| 4 | Estado desconocido o sub-resultado | Publicar `status = "COMPLETED_PARTIAL"` | `{"reservation_id":"c0a80100-0000-4000-8000-000000080103","dispute_id":"d0a80100-0000-4000-8000-000000080103","status":"COMPLETED_PARTIAL","status_changed_at":"2026-12-05T16:20:00Z","event_key":"qa-UC08-HU1-E3-4"}` | `ack` + `operational_failure`; cero operaciones monetarias y ninguna fila nueva en `dispute_event_log` (el contrato se rechaza antes de persistir). | [X] | Sin fila `E3-4`; UC08 registró el rechazo del contrato (causa raíz `No enum constant ... COMPLETED_PARTIAL`); `0` operaciones; cola y DLQ en `0`. |
| 5 | Contrato y fallos controlados (modo A) | `.\mvnw.cmd "-Dtest=DisputeInformationServiceTest,DisputeNotificationListenerContractTest,DisputeEventLogPersistenceAdapterIntegrationTest,RequestSettlementServiceTest,SendSettlementToGatewayServiceTest,RegisterSettlementResultServiceTest,SimulatedSettlementGatewayAdapterTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas de validación, idempotencia, delegación, cálculo, fallos y libro simulado; evidencia separada del recorrido real. | [X] | `59` pruebas, `0` fallos, `0` errores; `BUILD SUCCESS`. |

##### Edge Cases & Boundary Conditions
* **EC-001** : `COMPLETED` es un tratamiento total del 100 % del depósito; no admite liberación o retención parcial (RF-010, CE-006).
* **EC-002** : Un depósito tiene un único desenlace: `SETTLEMENT_REQUESTED` no puede convivir con `REFUND_REQUESTED` (RF-008).
* **EC-003** : La liquidación del depósito es independiente de la liquidación estándar del alquiler de UC10. La preparación con `COMPLETED` genera la estándar; la disputa genera la de `scope = 'DEPOSIT'`. Las consultas deben filtrar por `trigger`.
* **EC-004** : UC08 no responde al productor y no expone endpoint REST para consultar la resolución (RF-009, contrato §4).
* **EC-005** : `status_changed_at` es el instante de la transición, no el de recepción; una entrega tardía conserva la marca original.
* **EC-006** : Los importes ejecutados provienen de `charge_record` y de los registros internos, nunca del evento de Reservas. El DTO solo acepta los cinco campos del contrato; campos extra no se mapean (RF-001, RF-007, CE-004).
* **EC-007** : La respuesta del worker es asíncrona. La intención puede estar `COMPLETED` antes de la primera consulta; no se exige observar `PENDING_SEND`.

--------------------------------------------------------------------------------

#### Defect Severity Reference
| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
