### Test Case Specification: Estado de la reserva · HU1 · E1 Estado operativo sin acción financiera (disponible/reservado/en navegación)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante las notificaciones de estado `AVAILABLE`, `RESERVED` e `IN_NAVIGATION` de una reserva con información financiera registrada, el sistema reconoce el estado y **no** dispara reembolso, liquidación ni cobro, y no devuelve respuesta al Sistema de Reservas y Operaciones.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay endpoint REST para UC07: la entrada es el evento AMQP. La Pasarela de pago no interviene en este escenario.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1 debe haber pasado** (existe `reservation_information`) y **TC-UC04-HU1-E1** (montos calculados).
* RabbitMQ arriba y cola `finance.reservation-status.v1` sin mensajes pendientes.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos calculados (ilustrativos, ajustar a la versión vigente del entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`. Sin `reservation_status_log`, `charge_intent`, `refund_intent`, `settlement_intent` ni `deposit_disposition`.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health en `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL en `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish` con `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>` y `delivery_mode=2`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Estados operativos sin acción financiera
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql -h localhost -p 5433 -U seashare -d seashare -c "SELECT ..."` sobre `reservation_status_log` y las tablas de intenciones | `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | Cero filas de log y de intenciones para la reserva. | [x] | |
| 2 | Notificar `AVAILABLE` | Publicar `reservation.status.changed` en `seashare.reservations` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"AVAILABLE","status_changed_at":"2026-12-05T14:00:00Z"}` | Mensaje confirmado (`ack`): la cola vuelve a 0 y no aparece nada en `finance.reservation-status.v1.dlq` (RF-001). | [x] | |
| 3 | Verificar que no se registra operación | `SELECT` sobre `reservation_status_log` | `status = 'AVAILABLE'` | Cero filas: solo los estados que disparan operaciones monetarias se registran (plan §Reglas 8). | [x] | |
| 4 | Notificar `RESERVED` | Publicar el mismo evento con el nuevo estado y una marca posterior | `status=RESERVED`, `status_changed_at=2026-12-05T14:05:00Z` | `ack`; cero operaciones, cero filas de log (RF-002). | [x] | |
| 5 | Notificar `IN_NAVIGATION` | Publicar el mismo evento con el nuevo estado | `status=IN_NAVIGATION`, `status_changed_at=2026-12-05T14:10:00Z` | `ack`; cero operaciones, cero filas de log (RF-002). | [x] | |
| 6 | Verificar ausencia de operaciones financieras | `SELECT count(*)` sobre `charge_intent`, `refund_intent`, `settlement_intent` y `deposit_disposition` | misma reserva | Conteos sin cambios: no hay cobro, reembolso, liquidación ni desenlace del depósito (RF-008, CE-001, CE-003). | [x] | |
| 7 | Verificar el outbox | `SELECT routing_key, payload FROM outbox_message WHERE created_at >= <inicio>` | N/A | No hay mensajes nuevos con `charge.command`, `refund.command` ni `settlement.command` atribuibles a estos eventos. | [x] | |
| 8 | Verificar la unidireccionalidad | Consola RabbitMQ: revisar que no exista respuesta ni publicación de retorno hacia Reservas | N/A | UC07 no devuelve respuesta al Sistema de Reservas y Operaciones (RF-009). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Cuerpo JSON inválido | Publicar un cuerpo que no sea JSON parseable | `{ "status": ` | `ack` y registro en `operational_failure`; sin operación (equivale a E2 del catálogo, README §4.4). | [x] | |
| 2 | Faltan propiedades AMQP obligatorias | Publicar sin `Message-Id` o con `Content-Type: text/plain` | N/A | `ack` y registro en `operational_failure` (contrato §2; equivale a E1 del catálogo). | [x] | |
| 3 | Estado no reconocido | Publicar `status=CONFIRMED` | `CONFIRMED` | `ack`, inconsistencia en `operational_failure` y ninguna operación (RF-001; contrato §3.1). | [x] | |
| 4 | Estado en minúsculas | Publicar `status=available` | `available` | `ack` y fallo lógico: el catálogo usa MAYÚSCULAS (RNF-001). | [x] | |
| 5 | Entrega repetida del mismo estado | Republicar `AVAILABLE` con la misma marca | mismo payload | `ack`; sin operación ni fila de log (notificación repetida sin efecto). | [x] | |
| 6 | Falta `reservation_id` | Publicar el evento sin identificador de reserva | payload sin `reservation_id` | `ack` y registro en `operational_failure`; sin operación (RF-001, RNF-001). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Repetir los tres estados con marcas de tiempo distintas no crea operaciones ni filas de log: no existe identidad de operación que reclamar.
* **EC-002** : `reservation_information` es de solo lectura aquí; estos estados no persisten estado operativo ni modifican montos.
* **EC-003** : Una sucesión imposible (`RESERVED` → `AVAILABLE`) no se valida en UC07; las sucesiones pertenecen al Sistema de Reservas (casos extremos del spec).
* **EC-004** : El bloqueo temporal (TTL) no se inicia en estos estados y UC07 no lo administra (plan §Reglas 2).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
