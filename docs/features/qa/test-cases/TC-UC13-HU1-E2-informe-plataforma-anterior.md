### Test Case Specification: Consultar informe financiero · HU1 · E2 Informe de la plataforma de un período anterior
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/013-consultar-informe-financiero/spec.md](../../013-consultar-informe-financiero/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC13-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con alcance declarado de Administrador Financiero, al seleccionar **un período anterior** de la periodicidad elegida el sistema devuelve únicamente los agregados de ese período —sin mezclar datos de otros períodos— y su comparación con el período inmediatamente anterior equivalente, sin aceptar rangos libres de fechas.

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
* Fecha de referencia 2026-11-02. El período por defecto es `2026-10`; el anterior elegido para la prueba es `2026-08`.

##### Dependencies & Assumptions
* **TC-UC13-HU1-E1 debe haber pasado** (consulta por defecto y dataset mensual cargado).
* Existen registros confirmados en 2026-07, 2026-08, 2026-09 y 2026-10, generados por UC05, UC09 y UC10.

##### Preconditions & Default Setup
* **Initial State** : dataset mensual con 2026-08: 6 cobros `800000.00`, 1 reembolso `50000.00`, 5 dispersiones `700000.00`, 5 comisiones `105000.00`; 2026-07 (comparación): neto `40000.00`; 2026-09 y 2026-10 con datos distintos para detectar mezcla de períodos.
* **Environment** : staging local (`docker compose --profile app up --build -d`); PostgreSQL con `docker exec -it seashare-postgres psql -U seashare -d seashare`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta de un período anterior con alcance PLATFORM
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar los datos del período elegido y sus vecinos | `SELECT record_type, COUNT(*), SUM(amount) FROM v_financial_records WHERE created_at >= '2026-08-01T00:00:00-05:00' AND created_at < '2026-09-01T00:00:00-05:00' GROUP BY record_type;` | `2026-08` | 6 cobros, 1 reembolso, 5 dispersiones y 5 comisiones con los totales de la precondición (RF-005, RF-007). | [x] | |
| 2 | Solicitar un período anterior explícito | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY&period=2026-08` | `period=2026-08` | `200 OK`; el sistema acepta el período anterior sin exigir un rango de fechas (RF-004). | [x] | |
| 3 | Verificar el período devuelto | Leer `period`, `is_closed`, `scope`, `periodicity` | N/A | `period = "2026-08"`, `is_closed = true`, `scope = "PLATFORM"`, `periodicity = "MONTHLY"` (RF-001, RF-003). | [x] | |
| 4 | Verificar que no se mezclan períodos | Comparar totales con el `SELECT` del paso 1 y con 2026-09/2026-10 | N/A | `total_collections = "800000.00"`, `total_refunds = "50000.00"`, `total_settlements = "700000.00"`, `total_commissions = "105000.00"`; ningún monto de otro período (RF-005, CE-001, CE-003). | [x] | |
| 5 | Verificar el neto del período anterior | Calcular `cobros − reembolsos − dispersiones` | `800000.00 − 50000.00 − 700000.00` | `net_amount = "50000.00"`; comisión informativa sin doble conteo (RF-008, RF-009, CE-002). | [x] | |
| 6 | Verificar cantidades | Leer `counts` | N/A | `CHARGE=6`, `REFUND=1`, `SETTLEMENT=5`, `COMMISSION=5` (RF-009). | [x] | |
| 7 | Verificar la comparación inmediatamente anterior | Leer `comparison` | período anterior `2026-07`, neto `40000.00` | `previous_period = "2026-07"` (no `2026-09`), `absolute_variation = "10000.00"`, `percentage_variation = "25.00"` (RF-010, CE-004). | [x] | |
| 8 | Verificar que el período explícito prevalece sobre el defecto | Repetir sin `period` y comparar | sin `period` → `2026-10` | La llamada sin `period` devuelve el último cerrado (`2026-10`); con `period=2026-08` devuelve exactamente `2026-08` (RF-003, RF-004). | [x] | |
| 9 | Verificar la equivalencia en otras periodicidades | `GET ...&periodicity=QUARTERLY&period=2026-Q2` y `GET ...&periodicity=SEMI_MONTHLY&period=2026-09-H1` | `2026-Q2`, `2026-09-H1` | Devuelve esos períodos; la comparación es `2026-Q1` y `2026-08-H2` respectivamente (RF-002, RF-010, CE-003, CE-004). | [x] | |
| 10 | Verificar que la consulta no modifica registros | Contar filas y sumar montos en `v_financial_records` antes y después | N/A | Mismo conteo y mismos montos; la operación es de solo lectura (contract §6). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Mes inexistente | `GET ...&periodicity=MONTHLY&period=2026-13` | `2026-13` | `400 INVALID_PERIOD`; no se calcula el informe (contract §2, RF-004). | [x] | |
| 2 | Trimestre inexistente | `GET ...&periodicity=QUARTERLY&period=2026-Q5` | `2026-Q5` | `400 INVALID_PERIOD`; sin valores parciales (RF-004, CE-003). | [x] | |
| 3 | Formato que no corresponde a la periodicidad | `GET ...&periodicity=QUARTERLY&period=2026-08` | `2026-08` | `400 INVALID_PERIOD`; no se reinterpreta como trimestre (contract §2). | [x] | |
| 4 | Falta `periodicity` o `scope` | Dos llamadas, cada una sin uno de los parámetros | N/A | `400 VALIDATION_ERROR` en ambos casos (catálogo §3.4, E2). | [x] | |
| 5 | Período anterior sin registros | `GET ...&periodicity=MONTHLY&period=2020-01` | `2020-01` | `200 OK` con métricas y cantidades en cero; `percentage_variation = null` si el anterior también es cero (casos extremos del SPEC, RF-010). | [x] | |
| 6 | Período en curso | `GET ...&periodicity=MONTHLY&period=2026-11` | `2026-11` | `200 OK`, `is_closed = false`; informe parcial con corte al momento de la consulta (casos extremos del SPEC). | [x] | |
| 7 | Rango libre de fechas | `GET ...&periodicity=MONTHLY&period=2026-08&from=2026-08-01&to=2026-08-10` | `from`, `to` | El rango ajeno al contrato se ignora; el informe sigue siendo el mensual completo `2026-08` (RF-004, CE-003). | [x] | |
| 8 | Base de datos no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, sin detalle interno (E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Límite en America/Bogota: `2026-08-01T04:59:00Z` (31-jul 23:59 local) queda fuera de `2026-08`; `2026-08-01T05:00:00Z` (1-ago 00:00 local) entra (RNF-003).
* **EC-002** : La quincena `H1` cubre días 1–15 y `H2` del 16 al último día del mes; el trimestre respeta Ene-Mar, Abr-Jun, Jul-Sep y Oct-Dic (RF-002, CE-003).
* **EC-003** : Un período anterior no puede consultarse con un rango de fechas libre; solo con el identificador canónico (RF-004).
* **EC-004** : Si el neto anterior es cero, la variación porcentual es `null` y la absoluta se informa igual (RF-010, CE-004).
* **EC-005** : Las comisiones no alteran el neto del período anterior aunque se consulten como métrica informativa (RF-009, CE-002).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
