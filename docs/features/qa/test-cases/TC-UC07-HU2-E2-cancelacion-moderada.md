### Test Case Specification: Estado de la reserva · HU2 · E2 Cancelación moderada (72 h – 24 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 · release/0.1.0 · PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que `CANCELLED_MODERATE` delega en UC09 el reembolso real de prueba del **50 % del alquiler + 100 % del depósito** (sin seguro) y en UC10 la liquidación interna del **50 % del alquiler** como compensación. UC07 registra ambas identidades sin responder a Reservas y Operaciones; la delegación no acredita éxito externo ni transferencia real al propietario.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC09 | Plan Document | [plan.md](../../009-reembolsar-dinero-arrendatario/plan.md) |
| Plan UC10 | Plan Document | [plan.md](../../010-liquidar-fondos-alquiler/plan.md) |
| Contrato pasarela — reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Contrato webhook | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Política de modos A/B | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para cobro y reembolso en Mercado Pago. UC10 usa exclusivamente el libro interno (`GATEWAY_SETTLEMENT_MODE=simulated`), sin HTTP a Mercado Pago ni transferencia real al propietario. Modo A separado para fallos controlados.
* UC07 recibe la política ya elegida por Reservas; no calcula el umbral de 72–24 h ni administra el TTL.

##### Dependencies & Assumptions
* Seed QA v7 vigente: seguro por pasajero `15000.0000`, comisión `15.0000`; stub de Flota con tarifa regular `350000.00`.
* Credenciales de prueba MCO, Public Key y secreto de webhook de la misma aplicación. Callback HTTPS público registrado para `payment`, terminado en `/api/v1/webhook/gateway`.
* RabbitMQ, relay del outbox y workers activos; no detener consumidores para forzar estados intermedios.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070202` y UUID auxiliares ausentes. Preparación propia; no reutilizar pagos reembolsados ni borrar datos para repetir sin autorización.
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, health `/actuator/health`, RabbitMQ `http://localhost:15672`, Flota `http://localhost:8081`, BD `host=localhost port=5433 dbname=seashare user=seashare`; pasarela `https://api.mercadopago.com` con credenciales de prueba.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo para reembolsar `210000.00 COP`. Tokenizar desde el frontend con titular `APRO`; sustituir `<TOKEN_APRO>` por un token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata `VISA / 6260`. El reembolso usa el pago original, sin otro token.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único por entrega. Correlacionar consumo y webhook; no guardar tokens, credenciales ni datos completos de tarjeta.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar reserva propia | RabbitMQ → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070202","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` → tarifa `350000.0000`. | [X] | `qa-070202-info-01`: fila, tarifa y fechas verificadas. |
| 2 | Calcular valor | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070202/calculated-value` | Sin cuerpo | HTTP 200: alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] | HTTP 200 con desglose esperado. |
| 3 | Preparar cobro real | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070202","status":"PENDING","status_changed_at":"2026-12-06T09:00:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | `SELECT id, status, amount, external_reference FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` → `CAPTURED`. `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` → una fila por `400000.00`. Verificar pago real `approved`, `live_mode=false`, sin reembolsos previos. | [X] | `qa-070202-pending-01`: CAPTURED, registro con desglose y pago `1355372403` approved, `live_mode=false`, sin reembolsos. |

##### Preparación de variantes negativas (sección 2)

Comprobar ausencia de los UUID auxiliares antes de empezar. Reservar `c0a80100-0000-4000-8000-000000070215` para ausencia: `SELECT count(*) FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070215';` = 0; no registrarlo.

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin cálculo | RabbitMQ → `reservation.info.provided`; no calcular ni cobrar | `{"reservation_id":"c0a80100-0000-4000-8000-000000070212","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT total_amount FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070212';` → una fila con NULL. | [X] | `qa-070212-info-01`; total NULL. |
| 2 | Reserva calculada sin cobro | Publicar `reservation.info.provided`, esperar persistencia y ejecutar `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070213/calculated-value` sin cuerpo; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070213","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | HTTP 200, total `400000.00`; `SELECT count(*) FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070213';` = 0. | [X] | `qa-070213-info-01`; cálculo 200, total 400000 y cero cobros. |
| 3 | Reserva para estado inválido | RabbitMQ → `reservation.info.provided`; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070214","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila de información, sin operaciones financieras. | [X] | `qa-070214-info-01`; fila presente y sin intenciones. |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso parcial + liquidación compensatoria
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar estado de partida | Consultar `refund_intent`, `settlement_intent` y `deposit_disposition` por `reservation_id = 'c0a80100-0000-4000-8000-000000070202'`; `SELECT count(*) FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202' AND status = 'CANCELLED_MODERATE';` | Cobro CAPTURED de la preparación | Sin reembolsos, liquidaciones, depósito ni log de cancelación; conservar IDs del cobro. | [X] | Recuentos en cero; cobro `02b96e7c-a6cb-4a20-976e-b4823eeb3b0b`. |
| 2 | Notificar cancelación | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070202","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-06T09:40:00Z"}` | Evento consumido, correlacionado por Message-Id y ausente de `finance.reservation-status.v1.dlq`; UC07 delega ambas operaciones. | [X] | `qa-070202-moderate-01` recibido y ambas delegaciones registradas. |
| 3 | Verificar identidades | `SELECT operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202' AND status = 'CANCELLED_MODERATE';` | Clave `<reservation_id>:CANCELLED_MODERATE` | Dos filas (`REFUND`, `SETTLEMENT`), misma clave y marca original; `processed_at` no nulo, `outcome = delegated`. | [X] | Dos filas con clave y marca esperadas; ambas `delegated`. |
| 4 | Verificar reembolso y depósito | `SELECT id, trigger, scope, amount, status, charge_intent_id, operation_key, idempotency_key, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` y `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` | `CANCELLED_MODERATE / HALF_RENTAL_PLUS_DEPOSIT` | Una intención de `210000.00` = `175000.00 + 35000.00`, ligada al cobro original; `operation_key` coincide con la clave del log y `idempotency_key` es UUID v4 propio. Depósito `REFUND_REQUESTED`, sin disputa. | [X] | Intención `6b5fc59d-14ac-4bac-b3a1-1b9b449aa382` COMPLETED por 210000; depósito REFUND_REQUESTED. |
| 5 | Verificar compensación | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, charge_intent_id, operation_key, idempotency_key, status FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` | `CANCELLED_MODERATE / CANCELLATION_COMPENSATION` | Una intención por `175000.00`, comisión `0.00`, depósito asociado `35000.00` (no se liquida); mismo cobro y clave de negocio, UUID v4 propio distinto del reembolso. Seguro fuera de ambas operaciones: `210000 + 175000 + 15000 = 400000`. | [X] | Intención `cd7e1ff4-566d-4e37-9635-88e4ffc67cfd` COMPLETED; montos y claves correctos. |
| 6 | Verificar comandos | Consultar `outbox_message` por los IDs de intención obtenidos en pasos 4–5 | `refund.command` y `settlement.command` | Un comando lógico publicado por tipo, con `refund_intent_id` o `settlement_intent_id` correspondiente; sin token ni datos de tarjeta. | [X] | Un comando de cada tipo publicado con su ID de intención. |
| 7 | Verificar reembolso real | Panel o `GET https://api.mercadopago.com/v1/payments/{payment_id}` y arreglo `refunds`; `SELECT id, status, status_detail, amount, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';`; `SELECT id, refund_intent_id, amount, released_amount, transaction_cost, external_reference FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` | `payment_id = charge_intent.external_reference` | Reembolso de prueba `approved` por `210000.00 COP`, mismo ID externo/local; intención `COMPLETED`, exactamente un registro ligado a ella. Neto = monto − costo; costo NULL implica neto igual al monto. No exigir que el pago parcialmente reembolsado tenga estado `refunded`. | [X] | Pago `1355372403`, refund `1355334397` approved 210000; un registro local, neto 210000, costo NULL. |
| 8 | Verificar libro interno | `SELECT id, status, status_detail, external_reference, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';`; `SELECT id, settlement_intent_id, amount, insurance_amount, deposit_amount, external_reference FROM settlement_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';`; `SELECT count(*) FROM commission_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070202';` | Liquidación UC10 | `COMPLETED`, referencia `sim-settlement-<idempotency_key>` y detalle `simulated internal settlement`; un registro por `175000.00`, seguro `0.00`, depósito asociado `35000.00`. **Cero** registros de comisión: solo STANDARD los genera. No acredita transferencia real. | [X] | COMPLETED, referencia y detalle internos; un registro 175000, seguro 0, depósito 35000 y cero comisiones. |
| 9 | Verificar webhook posterior | Historial real de Mercado Pago/túnel, logs del worker y `SELECT routing_key, payload, published_at FROM outbox_message WHERE routing_key = 'gateway.notification' AND payload::jsonb->>'data_id' = '<PAYMENT_ID>';` | Sustituir `<PAYMENT_ID>` por el pago propio | Entrega real `payment` firmada posterior al reembolso, HTTP 200 y notificación publicada; correlación por pago y `x-request-id` con procesamiento UC09, mismo `refund_id`, sin duplicados. El HTTP 200 solo acredita recepción durable. | [X] | `payment.updated` HTTP 200 firmado, request `35e5674b-61fa-41ae-bce7-fc46e20eeb0a`; notificación publicada y worker consultó pago con fallback 405; mismo refund, un registro. |
| 10 | Verificar unidireccionalidad | Revisar trazas del evento del paso 2 por Message-Id/reserva | Sin `reply_to` | Sin respuesta de negocio hacia Reservas (RF-009); ack AMQP, HTTP de publicación y webhook no son respuestas de UC07 a M2. | [X] | Evento sin `reply_to`; outbox con comandos internos y notificaciones, sin respuesta de negocio a Reservas. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sin cálculo | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070212","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-06T09:40:00Z"}` | Evento consumido; `operational_failure.reason = RESERVATION_INFO_INCOMPLETE`; sin operaciones. | [X] | `qa-070202-negative-incomplete`: fallo esperado; cero logs, intenciones y depósito. |
| 2 | Sin información | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070215","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-06T09:40:00Z"}` | `RESERVATION_INFO_NOT_FOUND`; sin delegación. | [X] | `qa-070202-negative-missing`: fallo esperado; cero logs, intenciones y depósito. |
| 3 | Sin cobro original | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070213","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-06T09:40:00Z"}` | UC09 devuelve `FAILED`, excepción y rollback del mensaje; fallo registrado por la política del listener. Cero logs/intenciones/comandos financieros persistidos, sin llamada de reembolso. | [X] | `qa-070202-negative-no-charge`: UC09 CHARGE_INTENT_NOT_FOUND y UC07 FAILED; cero logs, intenciones y depósito. |
| 4 | Reenvío terminal | Republicar paso 2 principal con Message-Id nuevo; publicar también este cuerpo con otro Message-Id | `{"reservation_id":"c0a80100-0000-4000-8000-000000070202","status":"CANCELLED_MODERATE","status_changed_at":"2026-12-06T09:45:00Z"}` | Repetir consultas principales: siguen **dos** logs terminales, marca original `09:40:00Z`, una intención/comando/registro por tipo, mismos IDs y claves; depósito sin cambios, sin otra devolución. | [X] | `qa-070202-repeat-01/02`; dos logs, mismas intenciones/claves y un comando/registro por tipo; depósito sin cambios. |
| 5 | Estado en minúsculas | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070214","status":"cancelled_moderate","status_changed_at":"2026-12-06T09:40:00Z"}` | Inconsistencia registrada, sin operaciones (RF-001). | [X] | `qa-070202-negative-lower`: estado no reconocido; sin logs, intenciones ni depósito. |
| 6 | Contrato y fallos controlados (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,RefundCalculatorTest,SettlementCalculatorTest,RequestRefundServiceTest,RequestSettlementServiceTest,SendRefundToGatewayServiceTest,RegisterRefundResultServiceTest,MercadoPagoRefundAdapterTest,SendSettlementToGatewayServiceTest,RegisterSettlementResultServiceTest,SimulatedSettlementGatewayAdapterTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas de delegación, FAILED, cálculos y contrato; evidencia separada del recorrido real. | [X] | 115 pruebas, 0 fallos, 0 errores y 0 omitidas; 2026-11-13. |
| 7 | Reintentos (modo A) | `.\mvnw.cmd "-Dtest=AmqpListenerRetryIntegrationTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures controlados | Política aprobada: 5 intentos, backoff, DLQ y registro del fallo; no detener la BD del recorrido real. | [X] | 11 pruebas, 0 fallos, 0 errores y 0 omitidas; 2026-11-13. |

##### Edge Cases & Boundary Conditions
* **EC-001**. En negativos 1, 2, 3 y 5, comprobar el fallo en `operational_failure` y ausencia de logs, intenciones y depósito por el UUID de su fila. Revisar publicaciones durante la entrega para descartar comandos inesperados.
* **EC-002**. Las dos delegaciones se crean en una transacción local; sus resultados posteriores son independientes. No preparar COMPLETED ni liquidación previa.
* **EC-003**. Los workers pueden completar antes de la primera consulta. Esperar el resultado esperado; si no converge o falta webhook/saldo/sandbox, registrar el bloqueo, no PASS. Las pruebas A omitidas tampoco acreditan aprobación.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
