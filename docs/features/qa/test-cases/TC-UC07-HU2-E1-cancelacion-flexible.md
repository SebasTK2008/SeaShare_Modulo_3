### Test Case Specification: Estado de la reserva · HU2 · E1 Cancelación flexible (>72 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `CANCELLED_FLEXIBLE`, UC07 registra la identidad de la operación y delega en UC09 el reembolso del **100 % del valor total pagado** (alquiler, seguro y depósito), sin ejecutar "Liquidar fondos de alquiler" ni responder al Sistema de Reservas y Operaciones. El recorrido usa un cobro y un reembolso reales de prueba en Mercado Pago: UC07 no asume el éxito por delegar; UC09 registra el resultado cuando el proveedor lo confirma, incluso si la aprobación llega en la respuesta inicial del POST.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC09 | Plan Document | [plan.md](../../009-reembolsar-dinero-arrendatario/plan.md) |
| Contrato pasarela — reembolso | External contract | [pasarela-comando-reembolso.md](../../../technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Política de pruebas reales y simuladas | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo B** para la preparación del cobro y el reembolso. Las comprobaciones controladas de modo A se identifican en la segunda sección y no sustituyen la evidencia real.
* UC07 decide la operación a partir del estado informado por Reservas y Operaciones; no determina la política de cancelación ni calcula el umbral de 72 horas. Aquí se comprueba la consecuencia financiera de `CANCELLED_FLEXIBLE`.

##### Dependencies & Assumptions
* Seed de QA con versión vigente 7, seguro por pasajero `15000.0000` y comisión `15.0000`; stub de Flota con tarifa `350000.00` para la embarcación indicada.
* Credenciales de prueba de Mercado Pago y secreto de webhook configurados; tokenización con la Public Key de la misma aplicación. Callback HTTPS público registrado para `payment`, terminado en `/api/v1/webhook/gateway`, para comprobar la entrega real posterior al reembolso.
* RabbitMQ arriba, relay del outbox y workers activos; colas `finance.reservation-status.v1`, `finance.charge-command.v1`, `finance.refund-command.v1` y `finance.gateway-notification.v1` disponibles. No se detienen consumidores para forzar estados intermedios.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070201` ausente. Este caso prepara todos sus datos; no depende de ejecutar otro TC ni reutiliza un pago ya reembolsado. Puede repetirse sobre volúmenes de QA recreados únicamente con autorización previa para eliminar datos (`docker compose --profile app down -v`).
* **Environment**. Compose con `dev,qa`; app `http://localhost:8080`, health `http://localhost:8080/actuator/health`, RabbitMQ `http://localhost:15672`, stub de Flota `http://localhost:8081` y BD `host=localhost port=5433 dbname=seashare user=seashare`. Pasarela `https://api.mercadopago.com` con credenciales de prueba; no se usa un simulador de pagos.
* **Test Accounts**. Comprador y vendedor de prueba MCO; vendedor con saldo disponible para reembolsar `400000.00 COP`. Tokeniza la Visa crédito de prueba con titular `APRO` desde el frontend real. Sustituye `<TOKEN_APRO>` por un token nuevo y `<EMAIL_COMPRADOR>` por el email de prueba. Metadata de la Visa de ejemplo `VISA / 6260`. La cancelación y el reembolso usan la referencia del cobro original, no requieren otro token ni datos de tarjeta.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único en cada entrega, incluidos duplicados. Los cuerpos completos figuran en las tablas. Puede publicarse mediante `POST http://localhost:15672/api/exchanges/%2F/seashare.reservations/publish`; HTTP 200 con `routed=true` acredita enrutamiento, no el procesamiento ni la aprobación financiera.
* **Evidencia**. Registrar los IDs de reserva, intención, pago y reembolso, filas de BD, publicación del outbox y entrega real del webhook. No guardar tokens, credenciales ni datos completos de tarjeta en la evidencia.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar la reserva | RabbitMQ → `seashare.reservations` → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070201","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con tarifa regular `350000.0000`. `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` | [X] |  |
| 2 | Calcular el valor de la reserva | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070201/calculated-value` | Sin cuerpo | HTTP 200. Alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] |  |
| 3 | Preparar el cobro original | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070201","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | Intención `CAPTURED` y una fila de cobro por `400000.00`; pago real `approved`, `live_mode=false`, sin reembolsos previos. `SELECT id, status, status_detail, amount, external_reference, idempotency_key FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` y `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` | [X] |  |

##### Preparación de variantes negativas (sección 2)

Las reservas auxiliares siguientes deben estar ausentes antes de prepararlas y no reutilizan el cobro principal. El UUID `c0a80100-0000-4000-8000-000000070292` se reserva para ausencia de información: comprobar `SELECT count(*) FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070292';` = 0 y no registrarlo.

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Preparar reserva sin cálculo | RabbitMQ → `seashare.reservations` → `reservation.info.provided`; no invocar UC04 ni enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070291","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | Una fila con `total_amount IS NULL`. `SELECT base_rate, total_amount FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070291';` | [X] |  |
| 2 | Preparar reserva calculada sin cobro | Publicar `reservation.info.provided`, esperar su persistencia y ejecutar `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070293/calculated-value` sin cuerpo; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070293","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | HTTP 200, total `400000.00`. `SELECT count(*) FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070293';` = 0; sin cobro original. | [X] |  |
| 3 | Preparar reserva para estado inválido | Publicar `reservation.info.provided`, esperar su persistencia y ejecutar `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070294/calculated-value` sin cuerpo; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070294","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | HTTP 200, total `400000.00`. Reserva independiente sin cobro, reembolso ni liquidación. | [X] |  |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso total sin liquidación

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `SELECT count(*) FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';`; `SELECT count(*) FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';`; `SELECT count(*) FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';`; `SELECT count(*) FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201' AND status = 'CANCELLED_FLEXIBLE';` | Misma reserva, con el cobro de la preparación | Los cuatro recuentos son cero. La fila PENDING del cobro preparatorio no se confunde con una cancelación previa. | [X] |  |
| 2 | Notificar la cancelación flexible | RabbitMQ → `seashare.reservations` → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070201","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-05T15:00:00Z"}` | El evento se consume y UC07 delega en UC09. Confirmar procesamiento por Message-Id y ausencia de ese mensaje en `finance.reservation-status.v1.dlq`; no inferir aprobación del enrutamiento ni de una cola vacía. | [X] |  |
| 3 | Verificar identidad y delegación | `SELECT status, operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201' AND status = 'CANCELLED_FLEXIBLE';` | Marca recibida `2026-12-05T15:00:00Z` | Una fila `CANCELLED_FLEXIBLE / REFUND`, clave `c0a80100-0000-4000-8000-000000070201:CANCELLED_FLEXIBLE`, marca conservada, `processed_at` no nulo y `outcome = delegated` (RF-009A). | [X] |  |
| 4 | Verificar intención y depósito | `SELECT id, trigger, scope, amount, status, status_detail, charge_intent_id, external_reference, idempotency_key FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` y `SELECT disposition, dispute_id FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` | Trigger `CANCELLED_FLEXIBLE`, scope `FULL` | Una intención de `400000.00`, asociada al cobro original, con clave UUID v4 propia; depósito en `REFUND_REQUESTED`, sin disputa. Puede observarse `PENDING_SEND`, `IN_PROCESS` o ya `COMPLETED`; el resultado definitivo se valida en el paso 7. | [X] |  |
| 5 | Verificar ausencia de liquidación | `SELECT count(*) FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` y `SELECT count(*) FROM outbox_message o JOIN settlement_intent si ON o.payload::jsonb->>'settlement_intent_id' = si.id::text WHERE si.reservation_id = 'c0a80100-0000-4000-8000-000000070201' AND o.routing_key = 'settlement.command';` | Misma reserva | Ambos recuentos son cero: no se liquida el alquiler ni el depósito (RF-003). | [X] |  |
| 6 | Verificar el comando interno | `SELECT o.routing_key, o.payload, o.published_at FROM outbox_message o JOIN refund_intent ri ON o.payload::jsonb->>'refund_intent_id' = ri.id::text WHERE ri.reservation_id = 'c0a80100-0000-4000-8000-000000070201' AND o.routing_key = 'refund.command';` | ID de la intención de esta reserva | Un único comando lógico `refund.command` publicado, con cuerpo `{"refund_intent_id":"<ID_DE_LA_INTENCION>"}`. No incluye token ni datos de tarjeta. No se exige encontrar el mensaje esperando en `finance.refund-command.v1`: el worker puede haberlo consumido. | [X] |  |
| 7 | Verificar el reembolso real y su registro | Panel de Mercado Pago o `GET https://api.mercadopago.com/v1/payments/{payment_id}` y su arreglo `refunds`; comparar con `SELECT id, status, status_detail, amount, external_reference FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` y `SELECT id, refund_intent_id, amount, released_amount, transaction_cost, external_reference FROM refund_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070201';` | `payment_id = charge_intent.external_reference`; `refund_id = refund_intent.external_reference` | Reembolso real de prueba `approved` por `400000.00 COP`, con ID igual a la referencia local; intención `COMPLETED` y exactamente un `refund_record` vinculado a ella. `released_amount = amount - transaction_cost`; si el costo es NULL, el neto coincide con amount. Si el proveedor responde aún en curso, esperar y volver a consultar; la mera delegación no acredita éxito. | [X] |  |
| 8 | Verificar la unidireccionalidad | Revisar trazas de publicación y consumo del evento del paso 2, correlacionadas por Message-Id y reserva | Evento sin `reply_to`; operaciones internas de Finanzas | UC07 no publica respuesta de negocio hacia Reservas y Operaciones (RF-009). El ack AMQP, el HTTP 200 de la API de publicación y las notificaciones de Mercado Pago no son respuestas de negocio a M2. | [X] |  |
| 9 | Verificar webhook posterior al reembolso | Historial real de Mercado Pago o túnel HTTPS, logs del worker y `SELECT routing_key, payload, published_at FROM outbox_message WHERE routing_key = 'gateway.notification' AND payload::jsonb->>'data_id' = '<PAYMENT_ID>';` | Sustituir `<PAYMENT_ID>` por la referencia del cobro original; identificar la entrega posterior al reembolso, no solo `payment.created` del cobro | Entrega real `payment` firmada, HTTP 200 y notificación publicada. Correlacionar por `payment_id` y `x-request-id` con el procesamiento de UC09 y la consulta al proveedor; mismo `refund_id`, sin duplicar intención ni registro. El HTTP 200 del webhook solo acredita recepción durable, no el éxito del worker. | [X] |  |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin valor total calculado | RabbitMQ → `seashare.reservations` → `reservation.status.changed`; usar la variante sin cálculo | `{"reservation_id":"c0a80100-0000-4000-8000-000000070291","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-05T15:00:00Z"}` | Evento consumido; `SELECT reason, payload_ref FROM operational_failure WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070291';` informa `RESERVATION_INFO_INCOMPLETE`. Sin log de operación, reembolso, liquidación ni llamada de reembolso al proveedor; no se asumen montos. | [X] |  |
| 2 | Reserva sin información registrada | RabbitMQ → `seashare.reservations` → `reservation.status.changed`; usar el UUID reservado que no se registró por UC03 | `{"reservation_id":"c0a80100-0000-4000-8000-000000070292","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-05T15:00:00Z"}` | Evento consumido; `SELECT reason, payload_ref FROM operational_failure WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070292';` informa `RESERVATION_INFO_NOT_FOUND`. Sin log ni delegación financiera. | [X] |  |
| 3 | Sin cobro original aprobado | RabbitMQ → `seashare.reservations` → `reservation.status.changed`; usar la variante calculada sin cobro | `{"reservation_id":"c0a80100-0000-4000-8000-000000070293","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-05T15:00:00Z"}` | UC09 devuelve `FAILED` y UC07 revierte la transacción; evento confirmado con fallo en `SELECT use_case, reason, payload_ref FROM operational_failure WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070293';`. Sin log, intención de reembolso ni liquidación; sin llamada de reembolso al proveedor. | [X] |  |
| 4 | Reenvío del mismo estado terminal | RabbitMQ → `seashare.reservations` → `reservation.status.changed`; republicar el cuerpo del paso 2 de la sección 1 con Message-Id nuevo y después el cuerpo de esta fila, también con Message-Id nuevo | `{"reservation_id":"c0a80100-0000-4000-8000-000000070201","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-05T15:05:00Z"}` | Repetir las consultas de los pasos 3, 4, 6 y 7 de la sección 1: una fila de cancelación, misma intención y clave, un comando lógico y mismo `refund_id`; exactamente un registro de reembolso. La marca del log sigue siendo `15:00:00Z` y el depósito mantiene un solo desenlace. | [X] |  |
| 5 | Estado de cancelación en minúsculas | RabbitMQ → `seashare.reservations` → `reservation.status.changed`; usar la variante independiente para estado inválido | `{"reservation_id":"c0a80100-0000-4000-8000-000000070294","status":"cancelled_flexible","status_changed_at":"2026-12-05T15:00:00Z"}` | Evento confirmado con inconsistencia en `SELECT reason, payload_ref FROM operational_failure WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070294';`; sin log ni operaciones financieras (RF-001). | [X] |  |
| 6 | Fallas transitorias y agotamiento de reintentos (modo A) | `.\mvnw.cmd "-Dtest=AmqpListenerRetryIntegrationTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures y sonda transitoria de las clases indicadas; no detener la BD del recorrido B | Pruebas aprobadas de la política compartida: 5 intentos totales, backoff, rechazo a DLQ y registro del fallo cuando la persistencia está disponible. Esta evidencia controlada se registra por separado de las operaciones reales. | [X] |  |
| 7 | Contrato, delegación y resultados controlados (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,RefundCalculatorTest,RequestRefundServiceTest,SendRefundToGatewayServiceTest,RegisterRefundResultServiceTest,MercadoPagoRefundAdapterTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas, incluida la delegación sin liquidación, reversión ante `FAILED`, registro solo tras confirmación y contrato HTTP del reembolso. El adaptador envía `{"amount":400000.00}` para el importe de este recorrido y la clave UUID v4 de la intención como `X-Idempotency-Key`; cuerpo y cabecera se verifican en modo A, no se deducen del GET al proveedor. | [X] |  |

En los negativos 1, 2, 3 y 5, comprobar por el UUID de su fila que no hay logs, intenciones ni registros financieros; las consultas y el fallo esperado se indican en cada paso.

##### Edge Cases & Boundary Conditions

* **EC-001**. El reembolso es un único monto de `400000.00 COP` que incluye alquiler, seguro y depósito; el seguro **sí** se devuelve en la cancelación flexible. No se aplica una deducción propia al importe solicitado.
* **EC-002**. El desenlace del depósito (`REFUND_REQUESTED`) lo aplica UC09/UC08 con su compare-and-set; UC07 no escribe directamente `deposit_disposition` en cancelaciones. No preparar `COMPLETED` ni una liquidación previa para este escenario.
* **EC-003**. La respuesta del worker es asíncrona. `PENDING_SEND` o `IN_PROCESS` pueden resolverse antes de la primera consulta; `COMPLETED` y un registro son correctos si el proveedor ya confirmó `approved`. No se fuerza un resultado pendiente ni se exige ausencia de registro después de la aprobación. La aprobación tampoco acredita por sí sola que el banco ya haya abonado los fondos al comprador.
* **EC-004**. Montos con 4 decimales internos y redondeo `HALF_UP` a 2 decimales hacia la Pasarela. El adaptador envía `amount` explícito también en reembolsos totales; un rechazo del proveedor se registra como fallo, no se sustituye por una respuesta simulada.
* **EC-005**. Si faltan saldo, credenciales o disponibilidad del sandbox, registrar la operación y el motivo real y detener la ejecución cuando se necesite intervención. Si no converge al resultado esperado, conservar el estado observado; no marcar PASS por la sola creación de una intención. Los fallos de entrega o de procesamiento del webhook se registran por separado y no quedan aprobados porque el POST de reembolso haya funcionado.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
