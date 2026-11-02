### Test Case Specification: UC10 Liquidar fondos · HU2 · E1 Liquidación estándar por reserva completada
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al completarse una reserva, el sistema recupera alquiler, seguro náutico y depósito registrados, calcula la comisión con la versión de parámetros referenciada por la reserva y solicita al Propietario la liquidación estándar `alquiler bruto − comisión − seguro náutico`, manteniendo el depósito asociado para su resolución posterior.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 (cálculo de comisión y collector) | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de liquidación | External contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; se verifica por el request journal, la BD y los logs de la app.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro aprobado con `charge_record` (alquiler, seguro y depósito).
* **`GATEWAY_COLLECTOR_ID` configurado con un valor distinto de `0`**: sin cuenta destino no se producen dispersiones (README, variables de entorno).
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
| 4 | Verificar la intención registrada | `SELECT * FROM settlement_intent WHERE reservation_id = '...'` | N/A | `trigger = COMPLETED`, `scope = STANDARD`, `amount = 705000.00`, `commission_amount = 135000.00`, `deposit_amount = 30000.00`, estado `PENDING_SEND` y clave `settlement:COMPLETED:<operationKey>` (RF-005, RF-009, RF-014). | [ ] | `900000 − 135000 − 60000`. |
| 5 | Verificar el comando interno | Inspeccionar `finance.settlement-command.v1` y el *outbox* | N/A | Cuerpo `{"settlement_intent_id":"..."}`; el monto no se recibe desde Reservas (RF-001). | [ ] | |
| 6 | Verificar la llamada a la Pasarela | Request journal: `POST /v1/advanced_payments/{charge_intent.external_reference}/disbursements` | `disbursements[0].amount = 705000.00`, `collector_id = <GATEWAY_COLLECTOR_ID>`, `external_reference = <reservation_id>`; header `X-Idempotency-Key` | Se solicita exactamente el neto `alquiler − comisión − seguro` (RF-005, RF-008, CE-001). | [ ] | |
| 7 | Verificar la respuesta técnica | `SELECT status, external_reference, gateway_capability FROM settlement_intent` | N/A | Se guarda el `id` externo y el estado reportado (`approved` → `COMPLETED`; `pending`/`in_process` → `IN_PROCESS`); sin `settlement_record` todavía (RF-010, RF-012). | [ ] | |
| 8 | Verificar el depósito asociado y no dispuesto | `SELECT deposit_amount FROM settlement_intent` y `SELECT disposition FROM deposit_disposition` | N/A | Depósito `30000.00` conservado; UC10 **no** lo dispone en la liquidación estándar (RF-005; plan UC10, regla 5). | [ ] | |
| 9 | Verificar que aún no hay comisión registrada | `SELECT count(*) FROM commission_record WHERE settlement_intent_id = '<id>'` | N/A | `0` filas: el `RegistroDeComisión` solo se crea al confirmar la Pasarela (RF-014). | [ ] | El éxito se cubre en TC-UC10-HU5-E1. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin alquiler o sin seguro | Publicar `COMPLETED` para una reserva sin desglose | falta `rental_amount` o `insurance_amount` | Se registra el fallo en `operational_failure`; no se crea intención ni se llama a la Pasarela (RF-013; caso extremo del UC10). | [ ] | Sin cálculo parcial. |
| 2 | Versión de parámetros sin comisión | Reserva cuyo `frozen_parameters_id` no existe o no tiene comisión % | referencia inválida | La información se considera incompleta: se registra el fallo y no se calcula con un valor asumido (caso extremo del UC10; plan UC10, regla 9). | [ ] | |
| 3 | Cuenta destino no configurada | Ejecutar con `GATEWAY_COLLECTOR_ID = 0` | configuración `0` | No se llama a la Pasarela; intención `COMMUNICATION_ERROR`, registro en `operational_failure` y reintento (plan UC10, regla 7). | [ ] | |
| 4 | La Pasarela rechaza con `400` de negocio | Simular `400` (`collector_id` no vinculado o fondos insuficientes) | N/A | Intención `REJECTED`, fallo **no reintentable**, sin registro inmutable (contrato de liquidación §5). | [ ] | |
| 5 | Caída, `5xx` o timeout | Simular `503` o timeout de 10 s | N/A | Intención `COMMUNICATION_ERROR`, sin asumir resultado; 5 intentos con la misma clave idempotente (RNF-003). | [ ] | |
| 6 | Evento `COMPLETED` repetido | Publicar dos veces el mismo estado | misma `operationKey` | Sin segunda intención ni segunda dispersión; el mensaje se confirma (UC07 RF-009A; contrato UC07, regla 7). | [ ] | |

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
