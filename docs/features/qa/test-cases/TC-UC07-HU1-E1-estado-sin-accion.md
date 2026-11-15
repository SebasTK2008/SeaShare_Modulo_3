### Test Case Specification: Estado de la reserva · HU1 · E1 Estado operativo sin acción financiera (disponible/reservado/en navegación)
**Created** : 2026-11-02  **Spec/Plan Link** : [spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-13 Â· release/0.1.0 Â· PASS (docs/features/qa/matriz-qa.md)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Verificar que `AVAILABLE`, `RESERVED` e `IN_NAVIGATION` se reconocen sin cobro, reembolso, liquidación, seguimiento de depósito ni respuesta a Reservas. No se registra identidad de operación ni estado operativo persistido para estos estados.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Errores y política de colas | Conventions | [README.md](../../../technical-plan/contracts/README.md) |
| Política de preparación propia y modos | QA procedure | [pagos-api-real.md](../pagos-api-real.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Sin operación de pasarela**. Recorrido manual por AMQP; modo A para contrato y errores controlados. Los estados operativos no consultan información financiera ni requieren un cobro previo.

##### Dependencies & Assumptions
* Seed QA v7 y stub de Flota con tarifa regular `350000.00`, seguro por pasajero `15000.0000` y comisión `15.0000`.
* RabbitMQ, listener de `finance.reservation-status.v1` y relay del outbox activos. No se requiere token ni invocar Mercado Pago.

##### Preconditions & Default Setup
* **Initial State**. Reserva `c0a80100-0000-4000-8000-000000070101` ausente; este caso prepara sus datos y no depende de otros TC. Si el UUID existe, detenerse y acordar cómo repetir sin borrar datos ni volúmenes sin autorización.
* **Environment**. Compose `dev,qa`; app `http://localhost:8080`, health `/actuator/health`, RabbitMQ `http://localhost:15672`, Flota `http://localhost:8081`, BD `host=localhost port=5433 dbname=seashare user=seashare`.
* **Test Accounts**. N/A; sin tarjeta ni tokenización en este caso.
* **Mensajes AMQP**. Exchange `seashare.reservations`, `Content-Type: application/json` y `Message-Id` único por entrega. Confirmar consumo por trazas y ausencia del mensaje en DLQ, no solo por cola vacía. No guardar credenciales.

##### Preparación de los datos de este caso

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Registrar reserva | RabbitMQ → `reservation.info.provided` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","boat_id":"3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01","start_date":"2027-02-10","end_date":"2027-02-10","passengers":1,"owner_id":"a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d","max_capacity":6}` | `SELECT base_rate, start_date, end_date, passengers FROM reservation_information WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070101';` → una fila, tarifa `350000.0000`. | [X] | Message-Id `qa-070101-info-01`; tarifa y fechas verificadas. |
| 2 | Calcular valor sin cobrar | `POST http://localhost:8080/api/v1/reservations/c0a80100-0000-4000-8000-000000070101/calculated-value` | Sin cuerpo; no enviar PENDING | HTTP 200; alquiler `350000.00`, seguro `15000.00`, depósito `35000.00`, total `400000.00 COP`. | [X] | HTTP 200 y desglose esperado. |

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Estados operativos sin acción financiera

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar estado de partida | Consultar `reservation_information`, `reservation_status_log`, `charge_intent`, `refund_intent`, `settlement_intent` y `deposit_disposition` filtrando `reservation_id = 'c0a80100-0000-4000-8000-000000070101'`; `SELECT reason, payload_ref FROM operational_failure WHERE reservation_id = 'c0a80100-0000-4000-8000-000000070101';` | Reserva calculada sin cobro | Montos de preparación; cero logs, intenciones y depósito. Conservar también los fallos previos para comparación. | [X] | Desglose esperado; 0 logs, intenciones, depósitos y fallos. |
| 2 | Notificar AVAILABLE | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"AVAILABLE","status_changed_at":"2026-12-05T14:00:00Z"}` | Evento consumido por Message-Id, ausente de DLQ; recuentos y comandos siguen en cero. | [X] | `qa-070101-status-01`; recibido, DLQ vacía y sin efectos al finalizar la secuencia. |
| 3 | Notificar RESERVED | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"RESERVED","status_changed_at":"2026-12-05T14:05:00Z"}` | Mismo resultado: ningún log de operación ni intención. | [X] | `qa-070101-status-02`; recibido y sin efectos al finalizar la secuencia. |
| 4 | Notificar IN_NAVIGATION | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"IN_NAVIGATION","status_changed_at":"2026-12-05T14:10:00Z"}` | Mismo resultado: cero operaciones y sin modificación de montos. | [X] | `qa-070101-status-03`; recibido y sin efectos al finalizar la secuencia. |
| 5 | Verificar ausencia de efectos | Repetir consultas del paso 1 tras cada estado y revisar publicaciones del outbox durante cada entrega | Misma reserva y Message-Id | Montos idénticos, sin nuevas filas ni fallos. Sin comandos financieros atribuibles al evento; no inferirlo únicamente de un JOIN con intenciones inexistentes. | [X] | |
| 6 | Unidireccionalidad | Trazas por Message-Id/reserva de los tres eventos | Sin `reply_to` | No hay respuesta de negocio hacia Reservas; ack AMQP/HTTP de publicación no es respuesta de negocio. | [X] | |

##### Section 2: Errores y casos extremos de este escenario

| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reenvío sin efectos | Republicar cuerpo del paso 2 principal con Message-Id nuevo y luego este cuerpo con otro Message-Id | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"AVAILABLE","status_changed_at":"2026-12-05T14:20:00Z"}` | Eventos consumidos; mismo baseline, sin log ni identidad que reclamar. | [X] | `qa-070101-repeat-01/02`; sin efectos financieros. |
| 2 | JSON ilegible | RabbitMQ → `reservation.status.changed`, conservando propiedades AMQP válidas | Cuerpo literal incompleto: `{"status":` | Evento confirmado con fallo lógico en operational_failure, sin operaciones. Correlacionar por Message-Id, porque no puede extraerse reservation_id. | [X] | |
| 3 | Propiedades AMQP inválidas | Publicar este cuerpo dos veces: primero omitiendo Message-Id y después con Message-Id nuevo pero Content-Type `text/plain` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"AVAILABLE","status_changed_at":"2026-12-05T14:30:00Z"}` | Ambos mensajes confirmados con fallo lógico, sin delegación. Cuando falta Message-Id, correlacionar por instante acotado de publicación y payload_ref de la cola, no por reserva (se rechaza antes del parseo). | [X] | |
| 4 | Estado desconocido | RabbitMQ → `reservation.status.changed`, propiedades válidas | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"CONFIRMED","status_changed_at":"2026-12-05T14:30:00Z"}` | Fallo lógico por estado inválido, sin operaciones. | [X] | |
| 5 | Estado en minúsculas | RabbitMQ → `reservation.status.changed` | `{"reservation_id":"c0a80100-0000-4000-8000-000000070101","status":"available","status_changed_at":"2026-12-05T14:30:00Z"}` | Fallo lógico: enumeración sensible a mayúsculas, sin operaciones. | [X] | |
| 6 | Falta reserva | RabbitMQ → `reservation.status.changed` | `{"status":"AVAILABLE","status_changed_at":"2026-12-05T14:30:00Z"}` | Fallo lógico, sin operaciones; correlacionar por Message-Id, sin exigir un reservation_id que no se recibió. | [X] | |
| 7 | Contrato y errores controlados (modo A) | `.\mvnw.cmd "-Dtest=ReservationStatusPolicyTest,ReservationStatusListenerTest,ReservationStatusMessageMapperTest,HandleReservationStatusServiceTest,AmqpListenerFailurePolicyTest,OperationalFailureMessageRecovererTest" test` | Fixtures de las clases indicadas | Pruebas aprobadas; evidencia separada del recorrido manual, sin contar pruebas omitidas como aprobadas. | [X] | 49 pruebas, 0 fallos, 0 errores y 0 omitidas; 2026-11-13. |

##### Edge Cases & Boundary Conditions
* **EC-001**. Los negativos deben registrar fallo sin efectos financieros. Consultar `operational_failure` por Message-Id en `payload_ref` cuando no se pueda extraer la reserva; sin Message-Id, acotar por instante y cola.
* **EC-002**. Repetir estados no crea identidades. El TTL y las sucesiones operativas pertenecen a Reservas; modo A verifica las interacciones del servicio.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
