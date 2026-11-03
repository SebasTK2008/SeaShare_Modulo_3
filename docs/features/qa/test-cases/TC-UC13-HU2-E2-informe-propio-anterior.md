### Test Case Specification: Consultar informe financiero · HU2 · E2 Informe propio de un período anterior
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/013-consultar-informe-financiero/spec.md](../../013-consultar-informe-financiero/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC13-HU2-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con alcance declarado de Propietario (`owner_id`), al seleccionar **un período anterior** el sistema devuelve únicamente los agregados de las reservas de ese propietario en ese período, calcula sus ganancias como las dispersiones confirmadas y las compara con el período inmediatamente anterior equivalente, sin exponer datos de otros propietarios ni aceptar rangos libres.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC13 | Specification Document | [spec.md](../../013-consultar-informe-financiero/spec.md) |
| Plan UC13 | Plan Document | [plan.md](../../013-consultar-informe-financiero/plan.md) |
| Contrato REST — consultar informe | API contract | [UC13-consultar-informe-financiero.md](../../../technical-plan/contracts/rest/UC13-consultar-informe-financiero.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin interfaz propia: verificación por HTTP y por SQL sobre `v_financial_records`.
* Montos y cantidades **ilustrativos**; ajustar a la versión vigente del entorno y al dataset de QA.
* Fecha de referencia 2026-11-02: período por defecto `2026-10`; período anterior elegido `2026-08`.

##### Dependencies & Assumptions
* **TC-UC13-HU2-E1 debe haber pasado** (informe propio por defecto y aislamiento entre propietarios verificados).
* Existen registros confirmados del Propietario A en 2026-07, 2026-08, 2026-09 y 2026-10, y registros del Propietario B en los mismos períodos.

##### Preconditions & Default Setup
* **Initial State** : Propietario A (`11111111-1111-4111-8111-111111111111`) en 2026-08: 2 cobros `200000.00`, 1 reembolso `50000.00`, 2 dispersiones `180000.00`, 2 comisiones `27000.00`; en 2026-07 sus dispersiones suman `150000.00`. Propietario B en 2026-08: 3 cobros `450000.00`, 2 dispersiones `400000.00`, 3 comisiones `78000.00` (sin reembolsos).
* **Environment** : staging local (`docker compose --profile app up --build -d`); PostgreSQL con `docker exec -it seashare-postgres psql -U seashare -d seashare`.
* **Test Accounts** : N/A (identidad declarada como dato plano).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta propia de un período anterior
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar los registros de A y B en el período | `SELECT owner_id, record_type, COUNT(*), SUM(amount) FROM v_financial_records WHERE created_at >= '2026-08-01T00:00:00-05:00' AND created_at < '2026-09-01T00:00:00-05:00' GROUP BY owner_id, record_type;` | `2026-08` | A y B tienen los totales de la precondición; B sirve de contraste (RF-006, CE-001). | [x] | |
| 2 | Solicitar el período anterior propio | `GET /api/v1/financial-report?scope=OWNER&owner_id=11111111-1111-4111-8111-111111111111&periodicity=MONTHLY&period=2026-08` | `period=2026-08`, A | `200 OK`; el período anterior se acepta sin rango de fechas (RF-004). | [x] | |
| 3 | Verificar período y alcance | Leer `period`, `is_closed`, `scope` | N/A | `period = "2026-08"`, `is_closed = true`, `scope = "OWNER"` (RF-003). | [x] | |
| 4 | Verificar que solo agrega registros de A en 2026-08 | Comparar con el `SELECT` del paso 1 y con 2026-09/2026-10 | N/A | `total_collections = "200000.00"`, `total_refunds = "50000.00"`, `total_settlements = "180000.00"`, `total_commissions = "27000.00"`; sin montos de B ni de otros períodos (RF-006, CE-001, CE-003). | [x] | |
| 5 | Verificar las ganancias del período anterior | Leer `net_amount` | dispersiones de A en agosto | `net_amount = "180000.00"` = total de dispersiones confirmadas de A; no se resta el reembolso (RF-008, CE-002). | [x] | |
| 6 | Verificar las cantidades | Leer `counts` | N/A | `CHARGE=2`, `REFUND=1`, `SETTLEMENT=2`, `COMMISSION=2` (RF-009). | [x] | |
| 7 | Verificar la comparación propia inmediatamente anterior | Leer `comparison` | A, anterior `2026-07`, dispersiones `150000.00` | `previous_period = "2026-07"`, `absolute_variation = "30000.00"`, `percentage_variation = "20.00"` (RF-010, CE-004). | [x] | |
| 8 | Verificar que el período explícito prevalece sobre el defecto | Repetir sin `period` y comparar | sin `period` → `2026-10` | Sin `period` devuelve `2026-10`; con `period=2026-08` devuelve exactamente `2026-08` y no mezcla septiembre u octubre (RF-003, RF-004). | [x] | |
| 9 | Verificar la equivalencia en otras periodicidades | `GET ...&owner_id=<A>&periodicity=QUARTERLY&period=2026-Q2` y `GET ...&periodicity=SEMI_MONTHLY&period=2026-09-H1` | `2026-Q2`, `2026-09-H1` | Devuelve esos períodos con la comparación `2026-Q1` y `2026-08-H2` respectivamente, siempre solo de A (RF-002, RF-010, CE-003, CE-004). | [x] | |
| 10 | Verificar que la consulta no modifica registros | Contar filas y sumar montos antes y después | N/A | Sin cambios en `v_financial_records`; la operación es de solo lectura (contract §6). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Alcance OWNER sin `owner_id` | `GET ...&scope=OWNER&periodicity=MONTHLY&period=2026-08` | N/A | `400 VALIDATION_ERROR` con `field = "owner_id"` (contract §5, E2). | [x] | |
| 2 | `owner_id` con formato inválido | `GET ...&scope=OWNER&owner_id=abc&periodicity=MONTHLY&period=2026-08` | `abc` | `400 VALIDATION_ERROR`; sin cálculo (E2). | [x] | |
| 3 | Período con formato incorrecto | `GET ...&owner_id=<A>&periodicity=MONTHLY&period=2026-Q2` | `2026-Q2` con `MONTHLY` | `400 INVALID_PERIOD`; no se reinterpreta el formato (RF-004, CE-003). | [x] | |
| 4 | Mes o trimestre inexistente | `period=2026-13` (MONTHLY) y `period=2026-Q5` (QUARTERLY) | valores inválidos | `400 INVALID_PERIOD` en ambos casos (contract §2). | [x] | |
| 5 | Falta `periodicity` o `scope` | Dos llamadas, cada una sin uno de los parámetros | N/A | `400 VALIDATION_ERROR` en ambos casos (E2). | [x] | |
| 6 | Período anterior sin registros del propietario | `GET ...&owner_id=<A>&periodicity=MONTHLY&period=2020-01` | `2020-01`, A | `200 OK` con métricas y cantidades en cero; `percentage_variation = null` si el anterior es cero (casos extremos del SPEC, RF-010). | [x] | |
| 7 | Período en curso | `GET ...&owner_id=<A>&periodicity=MONTHLY&period=2026-11` | `2026-11`, A | `200 OK`, `is_closed = false`; informe parcial de A con corte al momento de la consulta (casos extremos del SPEC). | [x] | |
| 8 | Rango libre de fechas | `GET ...&owner_id=<A>&periodicity=MONTHLY&period=2026-08&from=2026-08-01&to=2026-08-10` | `from`, `to` | El rango se ignora; el informe sigue siendo el mensual completo `2026-08` de A (RF-004, CE-003). | [x] | |
| 9 | Base de datos no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, sin detalle interno (E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El informe de A no incluye ninguna fila de B aunque ambos tengan registros en el mismo período y la misma embarcación (RF-006, CE-001).
* **EC-002** : `net_amount` del propietario es solo dispersiones; los cobros y reembolsos son informativos y no alteran sus ganancias (RF-008, CE-002).
* **EC-003** : La comparación usa el período anterior del mismo `owner_id` (`2026-07`), no el de la plataforma ni el siguiente período (RF-010, CE-004).
* **EC-004** : Límite en America/Bogota: `2026-09-01T04:59:00Z` (31-ago 23:59 local) pertenece a `2026-08`; `2026-08-01T04:59:00Z` (31-jul local) queda fuera (RNF-003).
* **EC-005** : Las comisiones del propietario se informan como métrica independiente y no se descuentan de `net_amount` (RF-009, CE-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
