### Test Case Specification: Estado de la reserva · HU3 · E1 Finalización única de la reserva
**Created** : 2026-11-02  **Spec/Plan Link** : [spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 · release/0.1.0 · PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verificar que `COMPLETED` delega **una única liquidación estándar** en UC10 (alquiler bruto − comisión − seguro), registra liquidación y comisión en el libro interno y abre el depósito en `HELD`. UC07 no toma decisiones ni ejecuta operaciones monetarias sobre la garantía y no responde a Reservas. El estado COMPLETED de la intención de liquidación acredita contabilización interna, no transferencia real al propietario.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Plan UC10 | Plan Document | [plan.md](../../010-liquidar-fondos-alquiler/plan.md) |
| Plan UC08 | Plan Document | [plan.md](../../008-brindar-informacion-de-disputa-de-garantia/plan.md) |
| Política A/B y alcance UC10 | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Preparación en modo B**. COMPLETED usa el libro interno de UC10 (`GATEWAY_SETTLEMENT_MODE=simulated`), sin llamada a Mercado Pago ni transferencia real al propietario. Modo A separado para fallos controlados.

##### Dependencies & Assumptions
* Seed QA v7 vigente: seguro `15000.0000` por pasajero, comisión `15.0000`; Flota con tarifa regular `350000.00`. La reserva conserva la versión de parámetros del cálculo.
* Credenciales de prueba MCO, Public Key y secreto de webhook configurados; callback HTTPS registrado para `payment` cuando se comprueben entregas del cobro preparatorio.
* RabbitMQ, relay del outbox y workers activos; no detener consumidores para forzar estados intermedios.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070301` y UUID auxiliares ausentes. Preparación propia; no borrar datos para repetir sin autorización.
* **Environment**. Compose `dev,qa`; app `http://localhost:8080`, health `/actuator/health`, RabbitMQ `http://localhost:15672`, Flota `http://localhost:8081`, BD `host=localhost port=5433 dbname=seashare user=seashare`. Cobro contra `https://api.mercadopago.com` con credenciales de prueba.
* **Test Accounts**. Comprador/vendedor MCO de prueba. Tokenizar desde el frontend con titular `APRO`; sustituir `<TOKEN_APRO>` por token nuevo y `<EMAIL_COMPRADOR>` por email de prueba, metadata `VISA / 6260`. La finalización no necesita token ni saldo para un reembolso.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único por entrega. Confirmar consumo por trazas y ausencia del mensaje en DLQ; no guardar tokens ni credenciales.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar reserva | RabbitMQ → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070301","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` → una fila, tarifa `350000.0000`. | [X] | |
| 2 | Calcular valor | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070301/calculated-value` | Sin cuerpo | HTTP 200; alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`; comisión vigente `15.0000 %`. | [X] | |
| 3 | Preparar cobro real | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070301","status":"PENDING","status_changed_at":"2026-12-09T14:30:00Z","payment_token_ref":"<TOKEN_APRO>","payment_method_type":"VISA","payment_metadata":{"last_four":"6260","payer_email":"<EMAIL_COMPRADOR>"}}` | `SELECT id, status, amount, external_reference FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` → `CAPTURED`. `SELECT id, charge_intent_id, amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` → una fila por `400000.00`, con desglose. Pago real `approved`, `live_mode=false`, sin reembolsos previos. | [X] | |

##### Preparación de variantes negativas (sección 2)

Comprobar ausencia de los UUID auxiliares. Reservar `c0a80100-0000-4000-8000-000000070314` sin registrarlo: `SELECT count(*) FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070314';` = 0.

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin cálculo | RabbitMQ → `reservation.info.provided`; no calcular ni cobrar | `{"reservation_id":"c0a80100-0000-4000-8000-000000070311","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT total_amount FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070311';` → una fila con NULL. | [X] | |
| 2 | Reserva calculada sin cobro | Publicar `reservation.info.provided`, esperar persistencia y ejecutar `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070313/calculated-value` sin cuerpo; no enviar PENDING | `{"reservation_id":"c0a80100-0000-4000-8000-000000070313","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | HTTP 200, total `400000.00`; `SELECT count(*) FROM charge_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070313';` = 0. | [X] | |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Liquidación estándar interna y depósito HELD

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar estado de partida | Consultar `settlement_intent`, `refund_intent` y `deposit_disposition` por `reservation_id = 'c0a80100-0000-4000-8000-000000070301'`; `SELECT count(*) FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301' AND status = 'COMPLETED';` | Cobro CAPTURED de la preparación | Sin liquidaciones, reembolsos, depósito ni log de finalización; conservar IDs del cobro. | [X] | |
| 2 | Finalizar | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070301","status":"COMPLETED","status_changed_at":"2026-12-10T09:00:00Z"}` | Evento consumido por Message-Id, sin ese mensaje en DLQ; UC07 delega a UC10 y abre seguimiento del depósito. | [X] | |
| 3 | Verificar identidad | `SELECT status, operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301' AND status = 'COMPLETED';` | Clave `c0a80100-0000-4000-8000-000000070301:COMPLETED` | Una fila `SETTLEMENT`, marca `09:00:00Z`, procesada y `outcome = delegated`. | [X] | |
| 4 | Verificar liquidación estándar | `SELECT id, trigger, scope, amount, commission_amount, deposit_amount, charge_intent_id, operation_key, idempotency_key, status FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` | `COMPLETED / STANDARD` | Una intención ligada al cobro, clave de negocio del log y UUID v4 propio; `amount = 282500.00` = `350000 − 52500 − 15000`; comisión `52500.00`, depósito asociado `35000.00`. | [X] | |
| 5 | Verificar apertura del depósito | `SELECT disposition, completed_at, dispute_id, dispute_status FROM deposit_disposition WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` | Marca del evento principal | Una fila `HELD`, `completed_at = 2026-12-10T09:00:00Z`, no hora de recepción; disputa y estado de disputa nulos. | [X] | |
| 6 | Verificar comando y ausencia de operación de depósito | Consultar `outbox_message` por el settlement_intent_id del paso 4 y revisar publicaciones de la entrega; `SELECT count(*) FROM refund_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` | ID de liquidación y Message-Id | Un comando lógico `settlement.command` publicado para STANDARD. Cero reembolsos, liquidaciones DEPOSIT o comandos atribuibles a otra operación; HELD es solo seguimiento. | [X] | |
| 7 | Verificar resultado interno y comisión | `SELECT id, status, status_detail, external_reference, idempotency_key FROM settlement_intent WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';`; `SELECT id, settlement_intent_id, amount, rental_gross_amount, insurance_amount, deposit_amount, external_reference FROM settlement_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';`; `SELECT id, settlement_intent_id, settlement_record_id, amount FROM commission_record WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';` | Resultado UC10 | Intención `COMPLETED`, referencia `sim-settlement-<idempotency_key>`, detalle `simulated internal settlement`; un registro de `282500.00`, alquiler bruto `350000.00`, seguro `15000.00`, depósito `35000.00`, referencia coincidente. Un registro de comisión `52500.00`, ligado a esa intención y registro. Cuadre `282500 + 52500 + 15000 + 35000 = 400000`. No acredita transferencia real. | [X] | |
| 8 | Verificar garantía y unidireccionalidad | `SELECT count(*) FROM dispute_event_log WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070301';`; trazas por Message-Id/reserva del evento principal | Sin `reply_to` | Cero eventos de disputa, depósito sigue HELD, sin decisión de daño ni destino final. UC07 no publica respuesta de negocio a M2. Ack AMQP/HTTP de publicación no son respuesta de negocio. | [X] | |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Sin cálculo | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070311","status":"COMPLETED","status_changed_at":"2026-12-10T09:00:00Z"}` | `RESERVATION_INFO_INCOMPLETE`; sin liquidación ni apertura del depósito. | [X] | |
| 2 | Sin cobro original | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070313","status":"COMPLETED","status_changed_at":"2026-12-10T09:00:00Z"}` | UC10 devuelve FAILED, UC07 propaga excepción y revierte; fallo registrado por la política del listener. Sin log, liquidación, comandos ni depósito. | [X] | |
| 3 | Sin información | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070314","status":"COMPLETED","status_changed_at":"2026-12-10T09:00:00Z"}` | `RESERVATION_INFO_NOT_FOUND`, sin delegación. | [X] | |
| 4 | Reenvío terminal | Republicar paso 2 principal con Message-Id nuevo y luego este cuerpo con otro Message-Id | `{"reservation_id":"c0a80100-0000-4000-8000-000000070301","status":"COMPLETED","status_changed_at":"2026-12-10T09:30:00Z"}` | Mismas consultas: un log terminal, misma intención/clave/comando y un registro de liquidación/comisión; HELD conserva `completed_at = 09:00:00Z`. | [X] | |
| 5 | Contrato y fallos controlados del libro interno (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,SettlementCalculatorTest,RequestSettlementServiceTest,SendSettlementToGatewayServiceTest,RegisterSettlementResultServiceTest,SimulatedSettlementGatewayAdapterTest,GatewayProfilesTest,ReconcileSettlementIntentsServiceTest,OpenDepositTrackingServiceTest,DepositOutcomeSingleIntentIntegrationTest,ReservationStatusEndToEndIntegrationTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas de parámetros, FAILED, depósito ya resuelto, liquidación y persistencia; evidencia separada del recorrido manual. | [X] | |
| 6 | Reintentos (modo A) | `.\mvnw.cmd "-Dtest=AmqpListenerRetryIntegrationTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures controlados | Política aprobada: 5 intentos, backoff, DLQ y registro del fallo; no detener la BD del recorrido real. | [X] | |

##### Edge Cases & Boundary Conditions
* **EC-001**. En negativos 1–3, comprobar el fallo en `operational_failure` y ausencia de logs, intenciones y depósito por el UUID de su fila. Revisar publicaciones durante la entrega para descartar comandos inesperados.
* **EC-002**. El libro interno puede completar antes de consultar; esperar el resultado y registrar el estado real si no converge. No hay webhook ni transferencia de liquidación en Mercado Pago. Las pruebas A omitidas no acreditan aprobación.
* **EC-003**. Sin resolución de UC08 el depósito sigue HELD, incluso después de 24 h; Finanzas no administra ese temporizador.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
