### Test Case Specification: Estado de la reserva · HU2 · E3 Cancelación tardía (<24 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 · release/0.1.0 · PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verificar que `CANCELLED_STRICT` delega la liquidación del **100 % del alquiler** en el libro interno de UC10 y el reembolso **real de prueba del 100 % del depósito** en UC09/Mercado Pago. No se reembolsa alquiler ni seguro ni se responde a Reservas. UC07 no asume éxito por delegar; la liquidación interna no acredita transferencia real al propietario.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC09 | Plan Document | [plan.md](../../009-reembolsar-dinero-arrendatario/plan.md) |
| Plan UC10 | Plan Document | [plan.md](../../010-liquidar-fondos-alquiler/plan.md) |
| Reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Webhook | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Política A/B | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. UC10 usa el libro interno (`GATEWAY_SETTLEMENT_MODE=simulated`), sin HTTP a Mercado Pago. Modo A separado para fallos controlados.
* UC07 no calcula el umbral de 24 h: comprueba la consecuencia del estado elegido por Reservas.

##### Dependencies & Assumptions
* Seed QA v7 vigente, seguro `15000.0000` por pasajero, comisión `15.0000`; Flota con tarifa regular `350000.00`.
* Credenciales de prueba MCO, Public Key y secreto de webhook de la misma aplicación; callback HTTPS público registrado para `payment`, terminado en `/api/v1/webhook/gateway`.
* RabbitMQ, relay del outbox y workers activos; no detener consumidores para forzar estados intermedios.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070203` y UUID auxiliares ausentes. Preparación propia; no reutilizar pagos reembolsados ni borrar datos para repetir sin autorización.
* **Environment**. Compose `dev,qa`; app `http://localhost:8080`, health `/actuator/health`, RabbitMQ `http://localhost:15672`, Flota `http://localhost:8081`; BD `host=localhost port=5433 dbname=seashare user=seashare`; pasarela `https://api.mercadopago.com` con credenciales de prueba.
* **Test Accounts**. Comprador/vendedor MCO de prueba, vendedor con saldo para devolver `35000.00 COP`. Tokenizar desde el frontend con titular `APRO`; reemplazar `<TOKEN_APRO>` por token nuevo y `<EMAIL_COMPRADOR>` por email de prueba. Metadata `VISA / 6260`; el reembolso usa el pago original, no otro token.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único por entrega. Correlacionar consumo y webhook; no guardar tokens, credenciales ni datos completos de tarjeta.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar reserva | RabbitMQ → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070203","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` → una fila, tarifa `350000.0000`. | [X] | |
| 2 | Calcular valor | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070203/calculated-value` | Sin cuerpo | HTTP 200; alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] | |
| 3 | Cobrar en Mercado Pago | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070203","status":"PENDING","status_changed_at":"2026-12-07T17:00:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | `SELECT id, status, amount, external_reference FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` → `CAPTURED`. `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` → una fila con desglose. Proveedor `approved`, `400000.00`, `live_mode=false`, sin reembolsos previos. | [X] | |

##### Preparación de variantes negativas (sección 2)

Comprobar ausencia de los UUID auxiliares. Reservar `c0a80100-0000-4000-8000-000000070226` sin registrarlo: `SELECT count(*) FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070226';` = 0.

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin cálculo | RabbitMQ → `reservation.info.provided`; no calcular ni cobrar | `{"reservation_id":"c0a80100-0000-4000-8000-000000070223","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT total_amount FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070223';` → una fila con NULL. | [X] | |
| 2 | Reserva calculada sin cobro | Publicar `reservation.info.provided`, esperar persistencia y ejecutar `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070224/calculated-value` sin cuerpo; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070224","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | HTTP 200, total `400000.00`; `SELECT count(*) FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070224';` = 0. | [X] | |
| 3 | Reserva para estado inválido | RabbitMQ → `reservation.info.provided`; sin cálculo ni cobro | `{"reservation_id":"c0a80100-0000-4000-8000-000000070225","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Información persistida, sin operaciones. | [X] | |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Liquidación interna del alquiler + reembolso real del depósito

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar estado de partida | Consultar `refund_intent`, `settlement_intent` y `deposit_disposition` por `reservation_id = 'c0a80100-0000-4000-8000-000000070203'`; `SELECT count(*) FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203' AND status = 'CANCELLED_STRICT';` | Cobro CAPTURED de la preparación | Sin reembolsos, liquidaciones, depósito ni log de cancelación; conservar IDs del cobro. | [X] | |
| 2 | Cancelar | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070203","status":"CANCELLED_STRICT","status_changed_at":"2026-12-07T18:20:00Z"}` | Evento consumido, correlacionado por Message-Id, sin ese mensaje en DLQ. | [X] | |
| 3 | Verificar log | `SELECT status, operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203' AND status = 'CANCELLED_STRICT';` | Clave `c0a80100-0000-4000-8000-000000070203:CANCELLED_STRICT` | Dos filas (`SETTLEMENT`, `REFUND`), misma clave y marca `18:20:00Z`, procesadas con `outcome = delegated`. | [X] | |
| 4 | Verificar liquidación | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, charge_intent_id, operation_key, idempotency_key, status FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` | `CANCELLED_STRICT / CANCELLATION_COMPENSATION` | Una intención por `350000.00`, comisión `0.00`, depósito asociado `35000.00` (no liquidado); cobro original, clave de negocio del log y UUID v4 propio. | [X] | |
| 5 | Verificar reembolso y depósito | `SELECT id, trigger, scope, amount, status, charge_intent_id, operation_key, idempotency_key, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';`; `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` | `CANCELLED_STRICT / DEPOSIT_ONLY` | Una intención de `35000.00`, mismo cobro y clave de negocio; UUID v4 distinto del de liquidación. Depósito `REFUND_REQUESTED`, sin disputa. Reembolso excluye alquiler `350000.00` y seguro `15000.00`: `35000 + 350000 + 15000 = 400000`. | [X] | |
| 6 | Verificar comandos | Consultar `outbox_message` por los IDs de intención de pasos 4–5 | `settlement.command` y `refund.command` | Un comando lógico publicado por tipo, con `settlement_intent_id` o `refund_intent_id` correspondiente; sin token ni datos de tarjeta. | [X] | |
| 7 | Verificar reembolso real | Panel o `GET https://api.mercadopago.com/v1/payments/{payment_id}` y arreglo `refunds`; `SELECT id, status, amount, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';`; `SELECT id, refund_intent_id, amount, released_amount, transaction_cost, external_reference FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` | Pago = referencia externa del cobro | Reembolso `approved` por `35000.00 COP`, mismo ID local/externo; intención `COMPLETED` y un registro ligado a ella. Neto = monto − costo; costo NULL implica neto igual al monto. No exigir estado `refunded` en el pago parcial. | [X] | |
| 8 | Verificar libro interno | `SELECT id, status, status_detail, external_reference, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';`; `SELECT id, settlement_intent_id, amount, insurance_amount, deposit_amount, external_reference FROM settlement_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';`; `SELECT count(*) FROM commission_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070203';` | Resultado UC10 | `COMPLETED`, referencia `sim-settlement-<idempotency_key>`, detalle `simulated internal settlement`; un registro de `350000.00`, seguro `0.00`, depósito asociado `35000.00`; cero registros de comisión (solo STANDARD los genera). No hay transferencia real. | [X] | |
| 9 | Webhook posterior al reembolso | Historial real/túnel, logs del worker y `SELECT routing_key, payload, published_at FROM outbox_message WHERE routing_key = 'gateway.notification' AND payload::jsonb->>'data_id' = '<PAYMENT_ID>';` | Sustituir `<PAYMENT_ID>` por el pago propio | Entrega real `payment` firmada posterior al reembolso, HTTP 200, notificación publicada; correlacionar por pago y `x-request-id` con UC09, mismo `refund_id`, sin duplicados. HTTP 200 no acredita por sí solo procesamiento del worker. | [X] | |
| 10 | Unidireccionalidad | Trazas del evento principal por Message-Id/reserva | Sin `reply_to` | Sin respuesta de negocio de UC07 a M2; ack AMQP, HTTP de publicación y webhook no son respuestas a Reservas. | [X] | |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sin cálculo | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070223","status":"CANCELLED_STRICT","status_changed_at":"2026-12-07T18:20:00Z"}` | `RESERVATION_INFO_INCOMPLETE`, sin operaciones ni cancelación a medias. | [X] | |
| 2 | Sin información | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070226","status":"CANCELLED_STRICT","status_changed_at":"2026-12-07T18:20:00Z"}` | `RESERVATION_INFO_NOT_FOUND`, sin delegación. | [X] | |
| 3 | Sin cobro original | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070224","status":"CANCELLED_STRICT","status_changed_at":"2026-12-07T18:20:00Z"}` | UC10, primera delegación, devuelve `FAILED` por falta de cobro; UC07 revierte y no llega a solicitar el reembolso. Fallo registrado por la política del listener; sin logs/intenciones/comandos financieros persistidos ni reembolso real. | [X] | |
| 4 | Reenvío terminal | Republicar paso 2 principal con Message-Id nuevo y después este cuerpo con otro Message-Id | `{"reservation_id":"c0a80100-0000-4000-8000-000000070203","status":"CANCELLED_STRICT","status_changed_at":"2026-12-07T18:25:00Z"}` | Mismas consultas: dos logs terminales con marca original `18:20:00Z`, una intención/comando/registro de cada tipo, mismos IDs/claves y depósito sin cambios. | [X] | |
| 5 | Estado desconocido | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070225","status":"CANCELLED_LATE","status_changed_at":"2026-12-07T18:20:00Z"}` | Inconsistencia en `operational_failure`, sin operaciones. | [X] | |
| 6 | Contrato y resultados controlados (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,RefundCalculatorTest,SettlementCalculatorTest,RequestRefundServiceTest,RequestSettlementServiceTest,SendRefundToGatewayServiceTest,RegisterRefundResultServiceTest,MercadoPagoRefundAdapterTest,SendSettlementToGatewayServiceTest,RegisterSettlementResultServiceTest,SimulatedSettlementGatewayAdapterTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas de delegación, FAILED, cálculos, contrato y registros; evidencia separada del recorrido real. | [X] | |
| 7 | Reintentos (modo A) | `.\mvnw.cmd "-Dtest=AmqpListenerRetryIntegrationTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures controlados | Política aprobada: 5 intentos, backoff, DLQ y registro del fallo; no detener la BD del recorrido real. | [X] | |

##### Edge Cases & Boundary Conditions
* **EC-001**. En negativos 1, 2, 3 y 5, comprobar el fallo en `operational_failure` y ausencia de logs, intenciones y depósito por el UUID de su fila. Revisar publicaciones durante la entrega para descartar comandos inesperados.
* **EC-002**. Se delega liquidación primero y reembolso después en una transacción local; los resultados posteriores son independientes. No preparar COMPLETED ni liquidación previa.
* **EC-003**. Los workers pueden completar antes de consultar. Esperar el resultado esperado; si no converge o falta webhook/saldo/sandbox, registrar el bloqueo, no PASS. Las pruebas A omitidas tampoco acreditan aprobación.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
