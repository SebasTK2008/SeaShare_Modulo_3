### Test Case Specification: UC10 Liquidar fondos · HU1 · E2 Dispersión por cancelación tardía (100 % alquiler)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../010-liquidar-fondos-alquiler/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación tardía (<24 h), el sistema recupera el monto de alquiler registrado y solicita la captura/liquidación del **100 %** como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico, y sin que la solicitud implique que los fondos ya fueron recibidos.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../010-liquidar-fondos-alquiler/spec.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de liquidación | External contract | [pasarela-comando-liquidacion.md](../../../technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; se verifica por el request journal del simulador y por los logs de la app.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro aprobado con `charge_record`.
* **`GATEWAY_COLLECTOR_ID` configurado con un valor distinto de `0`**: sin cuenta destino no se producen dispersiones (README, variables de entorno).
* RabbitMQ arriba y *outbox* activo (`finance.settlement-command.v1`).

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con cobro `APPROVED`; desglose ilustrativo (ajustar a la versión vigente del entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Dispersión del 100 % del alquiler al Propietario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el alquiler registrado | `SELECT rental_amount FROM charge_record WHERE reservation_id = '...'` | reserva `b7d0e2a1-…` | Alquiler `900000.00` registrado; fuente autoritativa del cálculo (RF-001, RF-003). | [x] | |
| 2 | Confirmar la cuenta destino | Verificar la variable de entorno del worker | `GATEWAY_COLLECTOR_ID` ≠ `0` | Existe `collector_id` configurado para la dispersión (plan UC10, regla 7). | [x] | |
| 3 | Notificar la cancelación tardía | Publicar en `seashare.reservations` con routing key `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"CANCELLED_STRICT","status_changed_at":"2026-12-20T10:00:00Z"}` | El mensaje se confirma (ack); se crea la solicitud de dispersión sin montos externos (RF-001). | [x] | Estado `CANCELLED_STRICT` = tardía. |
| 4 | Verificar la intención registrada | `SELECT * FROM settlement_intent WHERE reservation_id = '...'` | N/A | `trigger = CANCELLED_STRICT`, `scope = CANCELLATION_COMPENSATION`, `amount = 900000.00`, `commission_amount = 0`, `deposit_amount = 30000.00`, estado `PENDING_SEND` y clave `settlement:CANCELLED_STRICT:<operationKey>` (RF-003, RF-007, RF-009). | [x] | 100 % del alquiler. |
| 5 | Verificar el comando interno | Inspeccionar `finance.settlement-command.v1` y el *outbox* | N/A | Cuerpo `{"settlement_intent_id":"..."}`; el monto no se recibe desde Reservas (RF-001). | [x] | |
| 6 | Verificar la llamada a la Pasarela | Request journal: `POST /v1/advanced_payments/{charge_intent.external_reference}/disbursements` | `disbursements[0].amount = 900000.00`, `collector_id = <GATEWAY_COLLECTOR_ID>`, `external_reference = <reservation_id>`; header `X-Idempotency-Key` | Se dispersa el 100 % del alquiler, sin comisión ni seguro (RF-003, RF-007, CE-001, CE-002). | [x] | |
| 7 | Verificar la respuesta técnica | `SELECT status, external_reference, gateway_capability FROM settlement_intent` | N/A | Se guarda el `id` externo y el estado reportado (`pending`/`in_process` → `IN_PROCESS`); aún **no** hay `settlement_record` (RF-010, RF-012). | [x] | |
| 8 | Verificar que no se genera comisión | `SELECT count(*) FROM commission_record WHERE settlement_intent_id = '<id>'` | N/A | `0` filas: la penalidad no lleva comisión de la plataforma (RF-007, RF-014, CE-002). | [x] | |
| 9 | Verificar que la solicitud no equivale a pago recibido | `SELECT * FROM settlement_record WHERE settlement_intent_id = '<id>'` | N/A | Sin registro de dispersión confirmada: la solicitud no implica que el Propietario ya recibió los fondos (escenario E2 del UC10; RF-012). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin alquiler registrado | Publicar `CANCELLED_STRICT` para una reserva sin desglose | reserva sin `rental_amount` | Se registra el fallo en `operational_failure`; no se crea intención ni se llama a la Pasarela (RF-013; caso extremo del UC10). | [x] | Sin cálculo parcial. |
| 2 | Cuenta destino no configurada | Ejecutar con `GATEWAY_COLLECTOR_ID = 0` | configuración `0` | No se llama a la Pasarela; intención `COMMUNICATION_ERROR`, registro en `operational_failure` y reintento de la cola (plan UC10, regla 7). | [x] | |
| 3 | La Pasarela rechaza con `400` de negocio | Simular `400` (`collector_id` no vinculado o fondos insuficientes) | N/A | Intención `REJECTED`, fallo **no reintentable**, sin registro inmutable (contrato de liquidación §5). | [x] | |
| 4 | Caída, `5xx` o timeout | Simular `503` o timeout de 10 s | N/A | Intención `COMMUNICATION_ERROR`, sin asumir resultado; 5 intentos con la misma clave idempotente (RNF-003; README §4.4). | [x] | Detalle a fondo en resiliencia. |
| 5 | Evento repetido con la misma identidad | Publicar dos veces el mismo `CANCELLED_STRICT` | misma `operationKey` | Sin segunda intención ni segunda dispersión; el mensaje se confirma (UC07 RF-009A; contrato UC07, regla 7). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El 100 % del alquiler no incluye el depósito: el `amount` dispersado es `900000.00`, no `930000.00`; el depósito se resuelve por el flujo de reembolso de UC09 (RF-003, RF-016).
* **EC-002** : Ni comisión ni seguro se descuentan en cancelaciones; la intención conserva `commission_amount = 0` y no se crea `commission_record` (RF-007, RF-014, CE-002).
* **EC-003** : El depósito queda asociado como componente del registro, aunque la operación específica no lo disperse (RF-016).
* **EC-004** : `BigDecimal` con 4 decimales internos → redondeo `HALF_UP` a 2 decimales hacia la Pasarela (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
