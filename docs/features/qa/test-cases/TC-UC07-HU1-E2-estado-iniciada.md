### Test Case Specification: Estado de la reserva · HU1 · E2 Estado "iniciada" como origen del bloqueo temporal (TTL 15 min)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../007-brindar-estado-de-la-reserva/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir el estado `INITIATED`, el sistema lo reconoce como el origen del bloqueo temporal (TTL) de 15 minutos **sin** administrar el temporizador y **sin** ejecutar reembolso, liquidación ni cobro. Solo `PENDING` habilita "Procesar cobro".

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
* UC07 no expone endpoints REST ni temporizadores: el TTL pertenece al Sistema de Reservas y Operaciones; aquí solo se verifica el reconocimiento del estado.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1 debe haber pasado** (existe `reservation_information`) y **TC-UC04-HU1-E1** (montos calculados).
* RabbitMQ arriba y cola `finance.reservation-status.v1` vacía.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos calculados (ilustrativos: alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`). Sin intenciones ni filas de `reservation_status_log`.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reconocimiento de "iniciada" como origen del TTL
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la línea base | `psql` sobre `reservation_status_log`, `charge_intent`, `refund_intent`, `settlement_intent` y `shedlock` | `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | Cero filas de log e intenciones; sin tareas de UC07 registradas. | [x] | |
| 2 | Notificar el estado `INITIATED` | Publicar `reservation.status.changed` en `seashare.reservations` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"INITIATED","status_changed_at":"2026-12-05T14:15:00Z"}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001). | [x] | |
| 3 | Verificar que no se registra una operación | `SELECT` sobre `reservation_status_log` | `status = 'INITIATED'` | Cero filas: `INITIATED` no dispara operaciones monetarias y no reclama identidad (plan §Reglas 8). | [x] | |
| 4 | Verificar que no se inicia el cobro | `SELECT count(*)` sobre `charge_intent` | misma reserva | Sin cambios: `INITIATED` no ejecuta "Procesar cobro" (solo `PENDING`, RF-002A). | [x] | |
| 5 | Verificar que no hay reembolso ni liquidación | `SELECT count(*)` sobre `refund_intent` y `settlement_intent` | misma reserva | Cero filas nuevas (RF-002, RF-008). | [x] | |
| 6 | Verificar que UC07 no administra el TTL | Revisar `shedlock` y los jobs programados de la aplicación; revisar que no exista estado operativo persistido | N/A | No aparece ninguna tarea ni marca de temporizador creada por UC07: el TTL de 15 min lo administra Reservas (RF-002, CE-007). | [x] | |
| 7 | Verificar la marca temporal registrada como contexto | Comparar el `status_changed_at` del evento con cualquier referencia almacenada | `2026-12-05T14:15:00Z` | Se usa la hora de la transición, no la de recepción (`fechaHoraEstado`, RNF-001). | [x] | |
| 8 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | UC07 no responde al Sistema de Reservas y Operaciones (RF-009). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | `INITIATED` repetida con la misma marca | Republicar el mismo evento | mismo payload | `ack`; ninguna operación (no hay nada que deduplicar porque no se reclama identidad). | [x] | |
| 2 | `INITIATED` repetida con otra marca | Republicar con `status_changed_at` posterior | `2026-12-05T14:16:00Z` | `ack`; ninguna operación ni fila de log. | [x] | |
| 3 | Falta `status_changed_at` | Publicar el evento sin la marca temporal | payload sin `status_changed_at` | `ack` y registro en `operational_failure`; sin operación (RNF-001). | [x] | |
| 4 | Estado en minúsculas o desconocido | Publicar `status=initiated` o `status=STARTED` | valor inválido | `ack` e inconsistencia registrada; sin operación (RF-001; equivale a E2 del catálogo). | [x] | |
| 5 | Reserva sin registro financiero | Publicar `INITIATED` para un UUID no registrado por UC03 | UUID nuevo | `ack`; el fallo se registra en `operational_failure` y no se ejecuta operación alguna (caso extremo del spec, RNF-003). | [x] | El plan §Reglas 2 y 6 no consulta prerrequisitos en estados sin acción: discrepancia a confirmar en release. |
| 6 | Cuerpo JSON que no es objeto | Publicar un arreglo JSON como cuerpo | `[]` | `ack` y registro en `operational_failure`; sin operación (contrato §2; equivale a E2). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El TTL de 15 minutos no se reinicia en `PENDING` ni se inicia en `INITIATED` desde Finanzas; ambos son responsabilidad de Reservas (RF-002, CE-007).
* **EC-002** : Si `status_changed_at` llega con otra zona horaria, se interpreta como instante ISO-8601; la comparación con la recepción no altera la decisión.
* **EC-003** : La transición `INITIATED` → `PENDING` es la única que habilita el cobro; verificarlo junto con TC-UC07-HU1-E3.
* **EC-004** : Un `INITIATED` tardío (después de 15 min) no produce ninguna acción en Finanzas: no hay cron ni temporizador (spec, casos extremos).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
