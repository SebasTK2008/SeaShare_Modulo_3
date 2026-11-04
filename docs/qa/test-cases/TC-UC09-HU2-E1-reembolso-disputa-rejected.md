### Test Case Specification: UC09 Reembolsos · HU2 · E1 Reembolso por disputa `REJECTED` o ausencia de reclamo
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibirse el estado `REJECTED` de una disputa de garantía, el sistema recupera el depósito cobrado y registrado internamente y solicita a la Pasarela su liberación o reembolso íntegro (100 %), sin retención parcial y sin recibir montos desde el Sistema de Reservas y Operaciones. La operación queda pendiente hasta la confirmación externa.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md) |
| Contrato evento UC08 (disparador) | Events contract | [UC08-disputa-garantia.md](../../../docs/technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../docs/technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; se verifica por el request journal del simulador y por los logs de la app.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro aprobado y su `charge_record` con el desglose (alquiler, seguro, depósito).
* RabbitMQ arriba y *outbox* activo (`finance.refund-command.v1`).

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con cobro `APPROVED`; desglose ilustrativo (ajustar a la versión vigente del entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; `deposit_disposition.disposition = HELD`; disputa `d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a` abierta.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso íntegro del depósito por disputa rechazada
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el depósito cobrado y registrado | `SELECT deposit_amount FROM charge_record WHERE reservation_id = '...'` y `SELECT disposition FROM deposit_disposition WHERE reservation_id = '...'` | reserva `b7d0e2a1-…` | Depósito `30000.00` registrado y en `HELD`; es la fuente autoritativa del monto, con respaldo en `reservation_information` (RF-001, RF-004). | [ ] | |
| 2 | Notificar la disputa rechazada | Publicar en `seashare.reservations` con routing key `reservation.dispute.updated` | `{"reservation_id":"b7d0e2a1-…","dispute_id":"d8e1f2a3-…","status":"REJECTED","status_changed_at":"2026-12-23T10:00:00Z","event_key":"v1.0-4a5b6c"}` | El mensaje se confirma (ack); se invoca el reembolso sin atributo de origen financiero (RF-001). | [ ] | |
| 3 | Verificar la intención registrada | `SELECT * FROM refund_intent WHERE reservation_id = '...'` | N/A | `trigger = DISPUTE_REJECTED`, `scope = DEPOSIT_ONLY`, `amount = 30000.00`, `dispute_id` y `charge_intent_id` informados, estado `PENDING_SEND` y clave UUID v4 (D-UC09-12) (RF-004, RF-005, RF-006). | [ ] | Sin cálculo parcial. |
| 4 | Verificar el comando interno | Inspeccionar la cola `finance.refund-command.v1` y el *outbox* | N/A | Cuerpo `{"refund_intent_id":"..."}`; no viaja ningún monto ni atributo de origen desde Reservas (RF-001). | [ ] | |
| 5 | Verificar la llamada a la Pasarela | Request journal del simulador: `POST /v1/payments/{charge_intent.external_reference}/refunds` | Body `{"amount": 30000.00}`; header `X-Idempotency-Key` = clave de la intención | Se solicita exactamente el **100 % del depósito** (CE-001), con la referencia del cobro original y la misma clave idempotente (RF-006). | [ ] | |
| 6 | Verificar la respuesta técnica | `SELECT status, status_detail, external_reference FROM refund_intent` | N/A | Se guarda el `id` externo del reembolso y el estado reportado por la Pasarela; aún **no** existe `refund_record` (RF-007). | [ ] | |
| 7 | Verificar el desenlace único del depósito | `SELECT disposition FROM deposit_disposition WHERE reservation_id = '...'` | reserva `b7d0e2a1-…` | `disposition = REFUND_REQUESTED` por *compare-and-set*; no hay un segundo desenlace (D-34; caso extremo del UC09). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin depósito cobrado y registrado | Publicar `REJECTED` para una reserva sin `charge_record` de depósito | `deposit_amount` inexistente | Se registra el fallo en `operational_failure`; no se crea intención ni se llama a la Pasarela (RF-010; caso extremo del UC09). | [ ] | |
| 2 | Evento `REJECTED` repetido | Publicar dos veces con el mismo `event_key` | mismo `dispute_id` / `event_key` | La segunda entrega se confirma sin una segunda operación monetaria (contrato UC08, regla 2). | [ ] | |
| 3 | `REJECTED` después de una liquidación del depósito | Publicar `REJECTED` con `deposit_disposition.disposition = SETTLEMENT_REQUESTED` | reserva con depósito ya liquidado | El *compare-and-set* falla: se registra la inconsistencia para conciliación y no se reembolsa dos veces (caso extremo del UC09; D-34). | [ ] | |
| 4 | La Pasarela rechaza con `400` de negocio | Simular `400` (medio de pago o reembolso no válido) | N/A | Intención `REJECTED`, fallo **no reintentable**, sin `refund_record` (contrato de reembolso §5; README §4.4). | [ ] | |
| 5 | Caída, `5xx` o timeout | Simular `503` o timeout de 10 s | N/A | Intención `COMMUNICATION_ERROR`, sin asumir resultado; 5 intentos con *backoff* base 1 s y la misma clave (RNF-003; README §4.4). | [ ] | Detalle a fondo en resiliencia. |
| 6 | Estado de disputa no reconocido | Publicar un estado no contemplado (p. ej. un sub-estado de liberación parcial) | `status` fuera de `PENDING`/`REJECTED`/`COMPLETED` | Se ignora monetariamente y se registra el fallo; no hay reembolso parcial (RF-005; contrato UC08, regla 1). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Nunca existe retención parcial: el monto solicitado es exactamente el 100 % del depósito registrado, aunque el evento no traiga montos (RF-005, CE-001).
* **EC-002** : "Ausencia de reclamo" y "disputa rechazada" llegan con el mismo `status = REJECTED` y reciben tratamiento idéntico, sin atributo adicional (CE-005).
* **EC-003** : El monto se recupera de `charge_record`; si el desglose no existiera se usa `reservation_information` exigiendo `isCalculated()`, nunca un valor asumido (plan UC09, regla 2).
* **EC-004** : `BigDecimal` con 4 decimales internos → redondeo final `HALF_UP` a 2 decimales hacia la Pasarela (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
