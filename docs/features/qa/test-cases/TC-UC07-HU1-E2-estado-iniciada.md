### Test Case Specification: Estado de la reserva · HU1 · E2 Estado "iniciada" como origen del bloqueo temporal (TTL 15 min)
**Created** : 2026-11-02  **Spec/Plan Link** : [spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 · release/0.1.0 · PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verificar que `INITIATED` reconoce el contexto del bloqueo temporal de 15 minutos de Reservas, sin administrar el TTL ni ejecutar cobro, reembolso, liquidación o seguimiento de garantía. UC07 no responde a Reservas ni registra una identidad de operación. Solo PENDING habilita Procesar cobro; no requiere un INITIATED previamente persistido por Finanzas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Errores y colas | Conventions | [README.md](../../../technical-plan/contracts/README.md) |
| Preparación propia y modos | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Pasarela no aplicable**. INITIATED retorna sin consultar información financiera ni persistir su marca temporal. No preparar PENDING ni un cobro para este escenario.

##### Dependencies & Assumptions
* Seed QA v7 vigente; Flota con tarifa `350000.00`, seguro por pasajero `15000.0000` y comisión `15.0000`.
* RabbitMQ, listener de `finance.reservation-status.v1` y relay activos. No se requiere token ni invocar Mercado Pago. Modo A separado para contrato, validación y ausencia de interacciones.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070102` ausente, preparación propia sin depender de otro TC. Reservar `c0a80100-0000-4000-8000-000000070192` para ausencia de información: `SELECT count(*) FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070192';` = 0 y no registrarla. Si un UUID existe, detenerse y acordar repetición sin borrar datos ni volúmenes sin autorización.
* **Environment**. Compose `dev,qa`; app `http://localhost:8080`, health `/actuator/health`, RabbitMQ `http://localhost:15672`, Flota `http://localhost:8081`, BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. N/A; sin tarjeta ni tokenización.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único por entrega. Confirmar consumo por trazas y ausencia del mensaje en DLQ, no solo por cola vacía. No guardar credenciales.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar reserva | RabbitMQ → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070102","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070102';` → una fila, tarifa `350000.0000`. | [X] | `qa-070102-info-01`; tarifa y fechas verificadas. |
| 2 | Calcular sin cobrar | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070102/calculated-value` | Sin cuerpo; no enviar PENDING | HTTP 200; alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] | HTTP 200 con el desglose esperado. |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: INITIATED sin acción financiera ni temporizador

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar estado de partida | Consultar `reservation_information`, `reservation_status_log`, `charge_intent`, `refund_intent`, `settlement_intent` y `deposit_disposition` filtrando `reservation_id = 'c0a80100-0000-4000-8000-000000070102'`; `SELECT reason, payload_ref FROM operational_failure WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070102';` | Reserva propia | Montos de preparación; cero logs, intenciones y depósito. Conservar los fallos previos. | [X] | Desglose esperado; cero logs, intenciones, depósitos y fallos; outbox vacío. |
| 2 | Notificar INITIATED | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070102","status":"INITIATED","status_changed_at":"2026-12-05T14:15:00Z"}` | Evento consumido por Message-Id, sin ese mensaje en DLQ, sin delegación financiera. | [X] | `qa-070102-initiated-01` recibido; DLQ vacía. |
| 3 | Verificar ausencia de cambios | Repetir consultas del paso 1 y revisar publicaciones del outbox durante la entrega | Misma reserva y Message-Id | Montos idénticos; sin nuevos logs, intenciones, depósito ni fallos. Sin comandos financieros atribuibles al evento; no deducirlo solo de un JOIN con intenciones inexistentes. | [X] | Desglose sin cambios; cero logs, intenciones, depósito y fallos; outbox completo vacío. |
| 4 | Verificar TTL y unidireccionalidad | Revisar trazas del evento y contrato UC07 | Sin `reply_to` | Sin temporizador de Finanzas ni respuesta de negocio a M2; ack/HTTP de publicación no es respuesta de negocio. | [X] | Outbox vacío tras la entrega; política devuelve plan vacío y servicio retorna sin delegar. TTL pertenece a Reservas. |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reenvío misma marca y otra marca | Republicar paso 2 principal con Message-Id nuevo; después publicar este cuerpo con otro Message-Id | `{"reservation_id":"c0a80100-0000-4000-8000-000000070102","status":"INITIATED","status_changed_at":"2026-12-05T14:16:00Z"}` | Eventos consumidos, ningún efecto ni log; no hay identidad de operación que deduplicar. | [X] | `qa-070102-repeat-01/02` recibidos y comprobados por separado; cero efectos y outbox vacío. |
| 2 | Reserva inexistente | Repetir consultas del paso 1 con la reserva ausente, publicar `reservation.status.changed` y comparar | `{"reservation_id":"c0a80100-0000-4000-8000-000000070192","status":"INITIATED","status_changed_at":"2026-12-05T14:15:00Z"}` | Evento consumido; sin nuevo fallo, log ni operación. Reserva continúa ausente: INITIATED no consulta su existencia. | [X] | `qa-070102-missing-01` recibido; reserva, fallos, logs, intenciones y outbox en cero. |
| 3 | Falta marca temporal | RabbitMQ → `reservation.status.changed`, propiedades AMQP válidas | `{"reservation_id":"c0a80100-0000-4000-8000-000000070102","status":"INITIATED"}` | Evento confirmado con fallo lógico por campo obligatorio, sin operaciones. | [X] | `qa-070102-no-date`: `status_changed_at is required`; sin operaciones ni outbox. |
| 4 | Estado inválido | Publicar cada cuerpo en `reservation.status.changed` con Message-Id distinto | `{"reservation_id":"c0a80100-0000-4000-8000-000000070102","status":"initiated","status_changed_at":"2026-12-05T14:20:00Z"}` y `{"reservation_id":"c0a80100-0000-4000-8000-000000070102","status":"STARTED","status_changed_at":"2026-12-05T14:20:00Z"}` | Fallo lógico por cada mensaje, sin delegación; catálogo sensible a mayúsculas. | [X] | `qa-070102-lower` y `qa-070102-started`: fallo por enum no reconocido, sin logs ni outbox. |
| 5 | JSON no objeto | RabbitMQ → `reservation.status.changed`, propiedades AMQP válidas | `[]` | Fallo lógico, sin operaciones; correlacionar por Message-Id, no exigiendo un reservation_id que no se pudo extraer. | [X] | `qa-070102-array`: cuerpo debe ser objeto; sin operaciones ni outbox. |
| 6 | Contrato y validación (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas; incluye INITIATED sin interacción financiera. Evidencia separada, sin contar pruebas omitidas como aprobadas. | [X] | Suite idéntica ejecutada el 2026-11-13: 49 pruebas, 0 fallos, 0 errores y 0 omitidas. |

##### Edge Cases & Boundary Conditions
* **EC-001**. Los mensajes inválidos registran fallo sin efectos financieros. Buscar `operational_failure` por Message-Id en `payload_ref` si no se pudo extraer la reserva.
* **EC-002**. INITIATED no persiste marca ni administra TTL; PENDING tampoco lo reinicia. La prueba de cobro pertenece a TC-UC07-HU1-E3.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
