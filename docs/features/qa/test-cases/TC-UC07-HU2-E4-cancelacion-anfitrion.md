### Test Case Specification: Estado de la reserva · HU2 · E4 Cancelación por anfitrión
**Created** : 2026-11-02  **Spec/Plan Link** : [spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 · release/0.1.0 · PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E4
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verificar que `CANCELLED_BY_HOST` registra una identidad de operación y delega en UC09 el reembolso **real de prueba del 100 % del valor pagado** (alquiler, seguro y depósito), sin liquidación al propietario ni respuesta a Reservas. La delegación no acredita éxito: UC09 registra el resultado tras confirmación de Mercado Pago, incluso si llega en la respuesta inicial del POST.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC09 | Plan Document | [plan.md](../../009-reembolsar-dinero-arrendatario/plan.md) |
| Reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Webhook | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Política A/B | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso. Modo A separado para contrato y fallos controlados; no sustituye la evidencia real.
* UC07 recibe el estado decidido por Reservas, sin determinar penalidades ni umbrales temporales.

##### Dependencies & Assumptions
* Seed QA v7 vigente, seguro por pasajero `15000.0000`, comisión `15.0000`; stub de Flota con tarifa regular `350000.00`.
* Credenciales de prueba MCO, Public Key y secreto de webhook de la misma aplicación. Callback público HTTPS registrado para `payment`, terminado en `/api/v1/webhook/gateway`.
* RabbitMQ, relay del outbox y workers activos; no detener consumidores para forzar estados intermedios.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070204` y UUID auxiliares ausentes. Preparación propia; no reutilizar pagos reembolsados ni borrar datos para repetir sin autorización.
* **Environment**. Compose `dev,qa`; app `http://localhost:8080`, health `/actuator/health`, RabbitMQ `http://localhost:15672`, Flota `http://localhost:8081`, BD `host=localhost port=5433 dbname=seashare user=seashare`; pasarela `https://api.mercadopago.com` con credenciales de prueba.
* **Test Accounts**. Comprador/vendedor de prueba MCO; vendedor con saldo para reembolsar `400000.00 COP`. Tokenizar desde el frontend con titular `APRO`. Sustituir `<TOKEN_APRO>` por token nuevo y `<EMAIL_COMPRADOR>` por email de prueba; metadata `VISA / 6260`. Cancelación y reembolso usan el pago original, no otro token.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único por entrega. Correlacionar consumo y webhook; no guardar tokens, credenciales ni datos completos de tarjeta.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar reserva | RabbitMQ → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070204","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';` → una fila, tarifa `350000.0000`. | [X] | |
| 2 | Calcular valor | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070204/calculated-value` | Sin cuerpo | HTTP 200; alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] | |
| 3 | Preparar cobro real | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070204","status":"PENDING","status_changed_at":"2026-12-08T10:00:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | `SELECT id, status, amount, external_reference FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';` → `CAPTURED`. `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';` → una fila por `400000.00`. Proveedor `approved`, `live_mode=false`, sin reembolsos previos. | [X] | |

##### Preparación de variantes negativas (sección 2)

Comprobar ausencia de los UUID auxiliares. Reservar `c0a80100-0000-4000-8000-000000070237` sin registrarlo: `SELECT count(*) FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070237';` = 0.

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin cálculo | RabbitMQ → `reservation.info.provided`; no calcular ni cobrar | `{"reservation_id":"c0a80100-0000-4000-8000-000000070234","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT total_amount FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070234';` → una fila con NULL. | [X] | |
| 2 | Reserva calculada sin cobro | Publicar `reservation.info.provided`, esperar persistencia y ejecutar `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070235/calculated-value` sin cuerpo; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070235","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | HTTP 200, total `400000.00`; `SELECT count(*) FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070235';` = 0. | [X] | |
| 3 | Reserva para estado inválido | RabbitMQ → `reservation.info.provided`; no calcular ni cobrar | `{"reservation_id":"c0a80100-0000-4000-8000-000000070236","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Información persistida, sin operaciones. | [X] | |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso total real sin liquidación

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar estado de partida | Consultar `refund_intent`, `settlement_intent` y `deposit_disposition` por `reservation_id = 'c0a80100-0000-4000-8000-000000070204'`; `SELECT count(*) FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204' AND status = 'CANCELLED_BY_HOST';` | Cobro CAPTURED de la preparación | Sin reembolsos, liquidaciones, depósito ni log de cancelación; conservar IDs del cobro. | [X] | |
| 2 | Cancelar por anfitrión | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070204","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-08T11:05:00Z"}` | Evento consumido por Message-Id, sin ese mensaje en `finance.reservation-status.v1.dlq`; UC07 delega a UC09. | [X] | |
| 3 | Verificar log | `SELECT status, operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204' AND status = 'CANCELLED_BY_HOST';` | Clave `c0a80100-0000-4000-8000-000000070204:CANCELLED_BY_HOST` | Una fila `REFUND`, marca original `11:05:00Z`, procesada y `outcome = delegated`. | [X] | |
| 4 | Verificar intención y depósito | `SELECT id, trigger, scope, amount, status, charge_intent_id, operation_key, idempotency_key, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';`; `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';` | `CANCELLED_BY_HOST / FULL` | Una intención por `400000.00` ligada al cobro; `operation_key` coincide con log y clave UUID v4 propia para el proveedor. Depósito `REFUND_REQUESTED`, sin disputa. | [X] | |
| 5 | Verificar comando y ausencia de liquidación | Consultar `outbox_message` por el refund_intent_id del paso 4 y revisar publicaciones de la entrega; `SELECT count(*) FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';` | Message-Id e ID de reembolso | Un comando lógico publicado `refund.command` con `refund_intent_id` correspondiente, sin datos de tarjeta. Cero liquidaciones y ningún `settlement.command` atribuible al evento. | [X] | |
| 6 | Verificar reembolso real | Panel o `GET https://api.mercadopago.com/v1/payments/{payment_id}` y arreglo `refunds`; `SELECT id, status, status_detail, amount, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';`; `SELECT id, refund_intent_id, amount, released_amount, transaction_cost, external_reference FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070204';` | Pago = referencia externa de `charge_intent` | Reembolso real de prueba `approved` por `400000.00 COP`, ID igual a la referencia local; intención `COMPLETED`, exactamente un registro vinculado a ella. Neto = monto − costo; costo NULL implica neto igual al monto. | [X] | |
| 7 | Webhook posterior al reembolso | Historial real/túnel, logs del worker y `SELECT routing_key, payload, published_at FROM outbox_message WHERE routing_key = 'gateway.notification' AND payload::jsonb->>'data_id' = '<PAYMENT_ID>';` | Sustituir `<PAYMENT_ID>` por el pago propio; no usar solo entrega de creación del cobro | Entrega real `payment` firmada posterior al reembolso, HTTP 200 y notificación publicada; correlación por pago/`x-request-id` con UC09 y mismo `refund_id`, sin duplicados. HTTP 200 solo acredita recepción durable. | [X] | |
| 8 | Unidireccionalidad | Trazas del evento principal por Message-Id/reserva | Sin `reply_to` | Sin respuesta de negocio a Reservas; ack AMQP, HTTP de publicación y webhook no son respuesta de UC07 a M2. | [X] | |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sin cálculo | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070234","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-08T11:05:00Z"}` | `RESERVATION_INFO_INCOMPLETE`, sin operaciones. | [X] | |
| 2 | Sin información | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070237","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-08T11:05:00Z"}` | `RESERVATION_INFO_NOT_FOUND`, sin delegación. | [X] | |
| 3 | Sin cobro original | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070235","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-08T11:05:00Z"}` | UC09 devuelve FAILED y UC07 revierte el mensaje; fallo registrado por la política del listener. Sin logs/intenciones/comandos financieros persistidos ni llamada de reembolso. | [X] | |
| 4 | Reenvío terminal | Republicar el paso 2 principal con Message-Id nuevo y luego este cuerpo con otro Message-Id | `{"reservation_id":"c0a80100-0000-4000-8000-000000070204","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-08T11:10:00Z"}` | Repetir consultas principales: un log terminal con marca original `11:05:00Z`, misma intención/clave/comando/`refund_id`, un registro; depósito sin cambios y sin liquidación. | [X] | |
| 5 | Estado inválido | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070236","status":"cancelled-by-host","status_changed_at":"2026-12-08T11:05:00Z"}` | Inconsistencia en `operational_failure`, sin operaciones. | [X] | |
| 6 | Contrato y resultados controlados (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,RefundCalculatorTest,RequestRefundServiceTest,SendRefundToGatewayServiceTest,RegisterRefundResultServiceTest,MercadoPagoRefundAdapterTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas de reembolso total, ausencia de liquidación, FAILED y contrato HTTP; evidencia separada del recorrido real. | [X] | |
| 7 | Reintentos (modo A) | `.\mvnw.cmd "-Dtest=AmqpListenerRetryIntegrationTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures controlados | Política aprobada: 5 intentos, backoff, DLQ y registro del fallo; no detener la BD del recorrido real. | [X] | |

##### Edge Cases & Boundary Conditions
* **EC-001**. En negativos 1, 2, 3 y 5, comprobar el fallo en `operational_failure` y ausencia de logs, intenciones y depósito por el UUID de su fila. Revisar publicaciones durante la entrega para descartar comandos inesperados.
* **EC-002**. El worker puede completar antes de consultar. Esperar el resultado esperado; si no converge o falta webhook/saldo/sandbox, registrar el bloqueo, no PASS. Las pruebas A omitidas tampoco acreditan aprobación.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
