### Test Case Specification: UC10 Liquidar fondos · HU2 · E1 Liquidación estándar por reserva completada
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../010-liquidar-fondos-alquiler/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al completarse una reserva, el sistema recupera alquiler, seguro náutico y depósito registrados, calcula la comisión con la versión de parámetros referenciada por la reserva y registra en el **libro interno simulado** la liquidación estándar `alquiler bruto − comisión − seguro náutico`, creando de forma atómica el registro de liquidación y el de comisión, y manteniendo el depósito asociado para su resolución posterior.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 (cálculo de comisión y libro simulado) | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato del libro interno de liquidación | Internal contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La liquidación usa un **libro interno simulado**: no hay petición HTTP ni journal de pasarela; se verifica por la BD y los logs de la app.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro aprobado con `charge_record` (alquiler, seguro y depósito).
* **`GATEWAY_SETTLEMENT_MODE=simulated`** (valor por defecto).
* La versión de parámetros referenciada por la reserva (`frozen_parameters_id`) contiene el porcentaje de comisión.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` en estado `COMPLETED`; desglose ilustrativo (ajustar a la versión vigente del entorno): alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; parámetro de comisión de la versión congelada `15.0000 %` (ejemplo del contrato UC11).
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Liquidación estándar del alquiler al Propietario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar los montos registrados | `SELECT rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = '...'` | reserva `b7d0e2a1-…` | Alquiler `900000.00`, seguro `60000.00` y depósito `30000.00` registrados (RF-005, RF-016). | [ ] | |
| 2 | Confirmar la versión congelada de parámetros | `SELECT commission_pct FROM financial_parameters WHERE id = (SELECT frozen_parameters_id FROM reservation_information WHERE reservation_id = '...')` | `15.0000` | Se obtiene el porcentaje de la versión referenciada por la reserva, no el vigente actual (RF-004, RF-014). | [ ] | |
| 3 | Notificar la finalización | Publicar en `seashare.reservations` con routing key `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"COMPLETED","status_changed_at":"2026-12-20T10:00:00Z"}` | El mensaje se confirma (ack); se crea la solicitud de dispersión sin montos externos (RF-001). | [ ] | Disparador real: UC07. |
| 4 | Verificar la intención registrada | `SELECT * FROM settlement_intent WHERE reservation_id = '...'` | N/A | `trigger = COMPLETED`, `scope = STANDARD`, `amount = 705000.00`, `commission_amount = 135000.00`, `deposit_amount = 30000.00`, estado inicial `PENDING_SEND`, `gateway_capability = STANDARD_SETTLEMENT` e `idempotency_key` UUID v4 (RF-005, RF-009, RF-014). | [ ] | `900000 − 135000 − 60000`. |
| 5 | Verificar el comando interno | Inspeccionar `finance.settlement-command.v1` y el *outbox* | N/A | Cuerpo `{"settlement_intent_id":"..."}`; el monto no se recibe desde Reservas (RF-001). | [ ] | |
| 6 | Verificar el registro en el libro interno | `SELECT status, external_reference, status_detail FROM settlement_intent` tras el worker | N/A | Estado `COMPLETED`; `external_reference = sim-settlement-<idempotency_key>` y `status_detail = simulated internal settlement`; se liquidó exactamente el neto `alquiler − comisión − seguro` (RF-005, RF-008, CE-001; D-UC10-18). | [ ] | Sin petición HTTP. |
| 7 | Verificar el registro inmutable | `SELECT * FROM settlement_record WHERE settlement_intent_id = '<id>'` | N/A | Exactamente **una** fila con `amount = 705000.00`, `rental_gross_amount = 900000.00`, `insurance_amount = 60000.00` y `deposit_amount = 30000.00` (RF-009A, RF-010, RF-016). | [ ] | |
| 8 | Verificar el depósito asociado y no dispuesto | `SELECT deposit_amount FROM settlement_intent` y `SELECT disposition FROM deposit_disposition` | N/A | Depósito `30000.00` conservado; UC10 **no** lo dispone en la liquidación estándar (RF-005; plan UC10, regla 5). | [ ] | |
| 9 | Verificar el registro de comisión | `SELECT count(*) FROM commission_record WHERE settlement_intent_id = '<id>'` | N/A | Exactamente **una** fila por `135000.00`, creada de forma atómica con el registro de liquidación (RF-014, CE-006). | [ ] | Se detalla en TC-UC10-HU5-E1. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin alquiler o sin seguro | Publicar `COMPLETED` para una reserva sin desglose | falta `rental_amount` o `insurance_amount` | Se registra el fallo en `operational_failure`; no se crea intención ni se escribe en el libro interno (RF-013; caso extremo del UC10). | [ ] | Sin cálculo parcial. |
| 2 | Versión de parámetros sin comisión | Reserva cuyo `frozen_parameters_id` no existe o no tiene comisión % | referencia inválida | La información se considera incompleta: se registra el fallo y no se calcula con un valor asumido (caso extremo del UC10; plan UC10, regla 9). | [ ] | |
| 3 | Modo de liquidación inválido | Arrancar con `GATEWAY_SETTLEMENT_MODE` distinto de `simulated` | configuración inválida | La aplicación no arranca (D-UC10-18). | [ ] | |
| 4 | Fallo del libro interno | Provocar `GatewayBusinessRejectionException` con un doble de prueba | N/A | Intención `REJECTED`, sin registro de liquidación ni de comisión; el adaptador simulado real no produce rechazos. | [ ] | Se cubre en pruebas unitarias. |
| 5 | Reintento del worker | Reprocesar el mismo `settlement_intent_id` | misma intención | La referencia determinista evita un segundo registro; la comisión sigue siendo única (D-UC10-10, CE-006). | [ ] | |
| 6 | Evento `COMPLETED` repetido | Publicar dos veces el mismo estado | misma `operationKey` | Sin segunda intención ni segunda liquidación; el mensaje se confirma (UC07 RF-009A; contrato UC07, regla 7). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La comisión se calcula con el porcentaje de `frozen_parameters_id`; un cambio posterior de parámetros globales no altera esta liquidación (RF-004, RF-014; D-35).
* **EC-002** : El seguro náutico se **resta** del alquiler bruto; la comisión no es atributo de `RegistroDeDispersión` y solo se materializa al confirmar (RF-014).
* **EC-003** : El depósito no forma parte del `amount` liquidado; permanece asociado para su resolución posterior por reembolso o disputa (RF-005, RF-016).
* **EC-004** : `BigDecimal` con 4 decimales internos → `HALF_UP` a 2 decimales en el monto enviado y en los registros (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
