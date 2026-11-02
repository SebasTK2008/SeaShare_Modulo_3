### Test Case Specification: Estado de la reserva · HU1 · E3 Estado "pendiente": continúa TTL y dispara el cobro
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/007-brindar-estado-de-la-reserva/spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC07-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el estado `PENDING` con el token del medio de pago, el sistema lo reconoce como continuación del TTL ya iniciado (sin reiniciarlo), ejecuta "Procesar cobro" para la reserva, registra la identidad de la operación y **no** dispara reembolso ni liquidación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC07 | Specification Document | [spec.md](../../../docs/features/007-brindar-estado-de-la-reserva/spec.md) |
| Plan UC07 | Plan Document | [plan.md](../../../docs/features/007-brindar-estado-de-la-reserva/plan.md) |
| Contrato evento UC07 | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Feature Spec UC05 | Specification Document | [spec.md](../../../docs/features/005-procesar-cobro/spec.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; el alcance de este TC termina en la delegación a UC05 y en la intención de cobro. El detalle del envío a la Pasarela se cubre en TC-UC05-HU1-E1.

##### Dependencies & Assumptions
* **TC-UC03-HU1-E1** y **TC-UC04-HU1-E1** deben haber pasado (información y valor total calculados).
* **TC-UC07-HU1-E2** debe haber pasado (el `INITIATED` previo no inicia operaciones).
* RabbitMQ arriba; colas `finance.reservation-status.v1` y `finance.charge-command.v1` disponibles.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con montos calculados (ilustrativos, ajustar al entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`. Sin `charge_intent` previa ni filas de `reservation_status_log`.
* **Environment** : staging local (`docker compose --profile app up --build -d`); health `http://localhost:8080/actuator/health`; consola RabbitMQ `http://localhost:15672`; PostgreSQL `localhost:5433`, base `seashare`. Publicación: `POST /api/exchanges/%2F/seashare.reservations/publish`, `routing_key=reservation.status.changed`, `content_type=application/json`, `message_id=<uuid v4>`.
* **Test Accounts** : N/A.

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: "Pendiente" dispara "Procesar cobro"
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar que no hay cobro previo | `psql` sobre `charge_intent` y `reservation_status_log` | misma reserva | Cero filas: el intento es nuevo. | [ ] | |
| 2 | Notificar `PENDING` con el medio de pago | Publicar `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z","payment_token_ref":"tok_12345abcdef","payment_method_type":"CREDIT_CARD","payment_metadata":{"last_four":"4242","payer_email":"arrendatario@example.com"}}` | `ack`; la cola vuelve a 0 y no hay mensaje en la DLQ (RF-001, RF-002A). | [ ] | |
| 3 | Verificar la identidad de la operación | `SELECT status, operation_type, idempotency_key, status_changed_at, processed_at, outcome FROM reservation_status_log` | misma reserva | **Una** fila `PENDING` / `CHARGE` con clave `<reservation_id>:PENDING:2026-12-05T14:30:00Z`, `status_changed_at` igual al notificado y `processed_at`/`outcome` no nulos (RF-009A, plan §Reglas 7). | [ ] | |
| 4 | Verificar la delegación a UC05 | `SELECT status, amount, payment_token_ref, payment_method_type, payer_email FROM charge_intent` | misma reserva | Intención creada por UC05 con el token recibido, `CREDIT_CARD`, `payer_email` y monto total `990000.00`; UC07 no calcula montos (RF-002A, RNF-002). | [ ] | |
| 5 | Verificar el comando interno de cobro | Consola RabbitMQ u `outbox_message` (`finance.charge-command.v1`) | enrutado `charge.command` | Un mensaje con `charge_intent_id` y `payer_email`; **no** lleva el token (lo lee el worker de UC05, contratos/README §3.5). | [ ] | |
| 6 | Verificar que no hay reembolso ni liquidación | `SELECT count(*)` sobre `refund_intent` y `settlement_intent` | misma reserva | Cero filas: `PENDING` no dispara reembolso ni dispersión (RF-008). | [ ] | |
| 7 | Verificar que no se toca el depósito | `SELECT count(*)` sobre `deposit_disposition` | misma reserva | Cero filas: el seguimiento del depósito recién se abre con `COMPLETED` (RF-007, RF-008). | [ ] | |
| 8 | Verificar que el TTL no se reinicia | Comparar `status_changed_at` con la marca del `INITIATED` previo | `INITIATED = 14:15:00Z`, `PENDING = 14:30:00Z` | La marca original del TTL se conserva y UC07 no crea temporizadores ni reescribe el instante (RF-002A, CE-007). | [ ] | |
| 9 | Verificar la unidireccionalidad | Consola RabbitMQ: comprobar que no se publica respuesta | N/A | Sin respuesta al Sistema de Reservas y Operaciones (RF-009). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | `PENDING` sin token | Publicar sin `payment_token_ref` | payload sin token | `ack` y `operational_failure`; no se llama a UC05 ni se crea `charge_intent` (contrato §2, plan §Reglas 3). | [ ] | |
| 2 | Reserva sin montos calculados | Publicar `PENDING` para una `reservation_information` incompleta | reserva sin cálculo | `ack` y `operational_failure` (`RESERVATION_INFO_INCOMPLETE`); sin delegación ni cobro parcial (RNF-003, CE-004). | [ ] | |
| 3 | Reserva sin registro financiero | Publicar `PENDING` para un UUID no registrado por UC03 | UUID nuevo | `ack` y `operational_failure` (`RESERVATION_INFO_NOT_FOUND`); sin llamada a UC05 (equivale a E3/E6, README §4.4). | [ ] | |
| 4 | Entrega repetida de la misma transición | Republicar el mismo `PENDING` con la misma marca | mismo payload | `ack`; **una** sola fila de log y **una** sola `charge_intent` (RF-009A). | [ ] | |
| 5 | Nuevo intento con marca distinta | Marcar la `charge_intent` anterior como `REJECTED` y publicar otro `PENDING` con `status_changed_at` posterior | `2026-12-05T14:45:00Z` | Nueva fila de log y **nueva** `charge_intent`: un rechazo de pago habilita otro intento (clave por intento, D-UC07-11; UC05 RF-004A). | [ ] | |
| 6 | `payment_metadata` no es objeto | Publicar `payment_metadata` como texto | `"payment_metadata":"4242"` | `ack` y fallo lógico; no se crea intención (contrato §2). | [ ] | |
| 7 | Falla transitoria de base de datos | Detener PostgreSQL antes de publicar | N/A | `nack`; 5 intentos con backoff base 1 s; DLQ y `operational_failure` al agotarlos (equivale a E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El token no viaja en el log ni en el comando de la cola; solo se persiste en `charge_intent` (UC05, RNF-001 del UC05).
* **EC-002** : El cobro es un monto único (alquiler + seguro + depósito) y no llamadas separadas por concepto (UC05 RF-015).
* **EC-003** : `fechaHoraEstado` es el momento de la transición, no el de recepción; un reenvío idéntico produce la misma clave (RNF-001, contrato §3.7).
* **EC-004** : La apertura del seguimiento del depósito no ocurre en `PENDING`; solo en `COMPLETED` y en estado `HELD` (RF-007).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
