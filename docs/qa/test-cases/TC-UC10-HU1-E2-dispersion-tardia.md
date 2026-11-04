### Test Case Specification: UC10 Liquidar fondos · HU1 · E2 Dispersión por cancelación tardía (100 % alquiler)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación tardía (<24 h), el sistema recupera el monto de alquiler registrado y registra en el **libro interno simulado** la liquidación del **100 %** como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico, y sin que el registro implique una transferencia real de fondos.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 (D-UC10-18, libro simulado) | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato del libro interno de liquidación | Internal contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La liquidación usa un **libro interno simulado** (`SimulatedSettlementGatewayAdapter`): no hay llamada HTTP a Mercado Pago, ni cuenta destino, ni confirmación externa.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro aprobado con `charge_record`.
* **`GATEWAY_SETTLEMENT_MODE=simulated`** (valor por defecto): cualquier otro valor impide el arranque.
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
| 1 | Confirmar el alquiler registrado | `SELECT rental_amount FROM charge_record WHERE reservation_id = '...'` | reserva `b7d0e2a1-…` | Alquiler `900000.00` registrado; fuente autoritativa del cálculo (RF-001, RF-003). | [ ] | |
| 2 | Confirmar el modo de liquidación | Revisar `GATEWAY_SETTLEMENT_MODE` | `simulated` | No se requiere cuenta destino ni credencial de marketplace (D-UC10-18). | [ ] | |
| 3 | Notificar la cancelación tardía | Publicar en `seashare.reservations` con routing key `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"CANCELLED_STRICT","status_changed_at":"2026-12-20T10:00:00Z"}` | El mensaje se confirma (ack); se crea la solicitud de dispersión sin montos externos (RF-001). | [ ] | Estado `CANCELLED_STRICT` = tardía. |
| 4 | Verificar la intención registrada | `SELECT * FROM settlement_intent WHERE reservation_id = '...'` | N/A | `trigger = CANCELLED_STRICT`, `scope = CANCELLATION_COMPENSATION`, `amount = 900000.00`, `commission_amount = 0`, `deposit_amount = 30000.00`, estado inicial `PENDING_SEND`, `gateway_capability = STANDARD_SETTLEMENT` e `idempotency_key` UUID v4 (RF-003, RF-007, RF-009). | [ ] | 100 % del alquiler. |
| 5 | Verificar el comando interno | Inspeccionar `finance.settlement-command.v1` y el *outbox* | N/A | Cuerpo `{"settlement_intent_id":"..."}`; el monto no se recibe desde Reservas (RF-001). | [ ] | |
| 6 | Verificar el registro en el libro interno | `SELECT status, external_reference, status_detail FROM settlement_intent` tras el worker | N/A | Estado `COMPLETED`; `external_reference = sim-settlement-<operationKey>` y `status_detail = simulated internal settlement` (D-UC10-18). | [ ] | Sin petición HTTP ni journal de pasarela. |
| 7 | Verificar el registro inmutable | `SELECT * FROM settlement_record WHERE settlement_intent_id = '<id>'` | N/A | Exactamente **una** fila con `amount = 900000.00`, `rental_gross_amount = 900000.00`, `insurance_amount = 0` y `deposit_amount = 30000.00` (RF-009A, RF-010, RF-016). | [ ] | |
| 8 | Verificar que no se genera comisión | `SELECT count(*) FROM commission_record WHERE settlement_intent_id = '<id>'` | N/A | `0` filas: la penalidad no lleva comisión de la plataforma (RF-007, RF-014, CE-002). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin alquiler registrado | Publicar `CANCELLED_STRICT` para una reserva sin desglose | reserva sin `rental_amount` | Se registra el fallo en `operational_failure`; no se crea intención ni se escribe en el libro interno (RF-013; caso extremo del UC10). | [ ] | Sin cálculo parcial. |
| 2 | Modo de liquidación inválido | Arrancar con `GATEWAY_SETTLEMENT_MODE` distinto de `simulated` | configuración inválida | La aplicación no arranca (D-UC10-18). | [ ] | |
| 3 | Fallo del libro interno | Provocar `GatewayBusinessRejectionException` con un doble de prueba | N/A | Intención `REJECTED`, sin registro inmutable; el adaptador simulado real no produce rechazos. | [ ] | Se cubre en pruebas unitarias. |
| 4 | Reintento del worker | Reprocesar el mismo `settlement_intent_id` | misma intención | Referencia determinista y registro único (D-UC10-10). | [ ] | |
| 5 | Evento repetido con la misma identidad | Publicar dos veces el mismo `CANCELLED_STRICT` | misma `operationKey` | Sin segunda intención ni segunda liquidación; el mensaje se confirma (UC07 RF-009A; contrato UC07, regla 7). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El 100 % del alquiler no incluye el depósito: el `amount` liquidado es `900000.00`, no `930000.00`; el depósito se resuelve por el flujo de reembolso de UC09 (RF-003, RF-016).
* **EC-002** : Ni comisión ni seguro se descuentan en cancelaciones; la intención conserva `commission_amount = 0` y no se crea `commission_record` (RF-007, RF-014, CE-002).
* **EC-003** : El depósito queda asociado como componente del registro, aunque la operación específica no lo liquide (RF-016).
* **EC-004** : `BigDecimal` con 4 decimales internos → redondeo `HALF_UP` a 2 decimales en el monto y el registro (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
