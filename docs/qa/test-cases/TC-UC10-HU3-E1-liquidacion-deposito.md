### Test Case Specification: UC10 Liquidar fondos · HU3 · E1 Liquidación total del depósito retenido
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al recibir `COMPLETED` desde una disputa de garantía, el sistema recupera internamente el depósito cobrado y registrado y solicita su liquidación **total** al Propietario, mediante una operación soportada por la integración configurada, sin recibir montos ni instrucciones de pago desde Reservas y sin asumir que es una transferencia directa.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Contrato evento UC08 (disparador) | Events contract | [UC08-disputa-garantia.md](../../../docs/technical-plan/contracts/events/UC08-disputa-garantia.md) |
| Contrato pasarela — comando de liquidación | External contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela se **simula**; se verifica por el request journal, la BD y los logs de la app.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: existe un cobro aprobado con `charge_record` y depósito registrado.
* **`GATEWAY_COLLECTOR_ID` configurado con un valor distinto de `0`**: sin cuenta destino no se producen dispersiones (README, variables de entorno).
* La reserva ya fue completada y su liquidación estándar no dispuso el depósito.

##### Preconditions & Default Setup
* **Initial State** : reserva `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` en `COMPLETED`; depósito cobrado y registrado `30000.00` (ajustar a la versión vigente del entorno); `deposit_disposition.disposition = HELD`; disputa `d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a` en `COMPLETED`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Liquidación del 100 % del depósito al Propietario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el depósito cobrado y retenido | `SELECT deposit_amount FROM charge_record WHERE reservation_id = '...'` y `SELECT disposition FROM deposit_disposition WHERE reservation_id = '...'` | reserva `b7d0e2a1-…` | Depósito `30000.00` registrado y en `HELD`; UC10 no recibe montos del evento (RF-006). | [ ] | |
| 2 | Confirmar la cuenta destino | Verificar la variable de entorno del worker | `GATEWAY_COLLECTOR_ID` ≠ `0` | Existe `collector_id` configurado para la dispersión (plan UC10, regla 7). | [ ] | |
| 3 | Notificar la disputa completada | Publicar en `seashare.reservations` con routing key `reservation.dispute.updated` | `{"reservation_id":"b7d0e2a1-…","dispute_id":"d8e1f2a3-…","status":"COMPLETED","status_changed_at":"2026-12-23T10:00:00Z","event_key":"v1.0-4a5b6c"}` | El mensaje se confirma (ack); se crea la solicitud de dispersión sin instrucciones de pago externas (RF-001). | [ ] | |
| 4 | Verificar la intención registrada | `SELECT * FROM settlement_intent WHERE reservation_id = '...'` | N/A | `trigger = DISPUTE_COMPLETED`, `scope = DEPOSIT`, `amount = 30000.00`, `commission_amount = 0`, `deposit_amount = 30000.00`, `dispute_id` y `charge_intent_id` informados, estado `PENDING_SEND` y clave `settlement:DISPUTE_COMPLETED:<operationKey>` (RF-006, RF-009). | [ ] | |
| 5 | Verificar el desenlace único del depósito | `SELECT disposition FROM deposit_disposition WHERE reservation_id = '...'` | N/A | `disposition = SETTLEMENT_REQUESTED` por *compare-and-set*; solo este trigger dispone el depósito hacia el Propietario (D-34; plan UC10, regla 5). | [ ] | |
| 6 | Verificar el comando interno | Inspeccionar `finance.settlement-command.v1` y el *outbox* | N/A | Cuerpo `{"settlement_intent_id":"..."}`; no viaja un monto asumido desde Reservas (RF-001). | [ ] | |
| 7 | Verificar la llamada a la Pasarela | Request journal: `POST /v1/advanced_payments/{charge_intent.external_reference}/disbursements` | `disbursements[0].amount = 30000.00`, `collector_id = <GATEWAY_COLLECTOR_ID>`, `external_reference = <reservation_id>`; header `X-Idempotency-Key` | Se solicita el **100 % del depósito** mediante la capacidad soportada por la integración (RF-006, RF-008, CE-001). | [ ] | |
| 8 | Verificar la respuesta técnica | `SELECT status, external_reference, gateway_capability FROM settlement_intent` | N/A | Se guarda el `id` externo y el estado reportado (`pending`/`in_process` → `IN_PROCESS`); aún **no** hay `settlement_record` (RF-010, RF-012). | [ ] | |
| 9 | Verificar que no se genera comisión | `SELECT count(*) FROM commission_record WHERE settlement_intent_id = '<id>'` | N/A | `0` filas: la liquidación del depósito no lleva comisión ni seguro (RF-014, CE-002). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Disputa `COMPLETED` sin depósito cobrado y registrado | Publicar `COMPLETED` para una reserva sin depósito | sin `deposit_amount` | Se trata como información incompleta: fallo en `operational_failure`, sin intención ni llamada a la Pasarela (RF-013; caso extremo del UC10). | [ ] | |
| 2 | Segunda notificación `COMPLETED` de la misma disputa | Reenviar el mismo evento / `event_key` | mismo `dispute_id` | Sin segunda liquidación del depósito; la repetición se registra para conciliación (caso extremo del UC10; contrato UC08, regla 2). | [ ] | |
| 3 | Depósito ya reembolsado | Publicar `COMPLETED` con `deposit_disposition.disposition = REFUND_REQUESTED` | depósito ya reembolsado | El *compare-and-set* falla: se conserva el desenlace existente y se registra la inconsistencia, sin segunda operación monetaria (D-34; caso extremo del UC09). | [ ] | |
| 4 | Cuenta destino no configurada | Ejecutar con `GATEWAY_COLLECTOR_ID = 0` | configuración `0` | No se llama a la Pasarela; intención `COMMUNICATION_ERROR`, registro en `operational_failure` y reintento (plan UC10, regla 7). | [ ] | |
| 5 | La Pasarela rechaza con `400` de negocio | Simular `400` (`collector_id` no vinculado o fondos insuficientes) | N/A | Intención `REJECTED`, fallo **no reintentable**, sin registro inmutable (contrato de liquidación §5). | [ ] | |
| 6 | Caída, `5xx` o timeout | Simular `503` o timeout de 10 s | N/A | Intención `COMMUNICATION_ERROR`, sin asumir resultado; 5 intentos con la misma clave idempotente (RNF-003). | [ ] | |
| 7 | Estado de disputa no reconocido | Publicar un estado no contemplado (p. ej. un sub-estado de liberación parcial) | `status` fuera de `PENDING`/`REJECTED`/`COMPLETED` | Se ignora monetariamente y se registra el fallo; no existe retención ni liquidación parcial (contrato UC08, regla 1). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La liquidación es siempre del 100 % del depósito; no existe retención ni liquidación parcial (RF-006, CE-001).
* **EC-002** : La intención conserva `commission_amount = 0` y no se crea `RegistroDeComisión` en la liquidación del depósito (RF-014, CE-002).
* **EC-003** : La operación se ejecuta con la capacidad que soporte la integración configurada; el sistema no asume una transferencia directa (RF-008; escenario E1 del UC10).
* **EC-004** : `BigDecimal` con 4 decimales internos → redondeo `HALF_UP` a 2 decimales hacia la Pasarela (RNF-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
