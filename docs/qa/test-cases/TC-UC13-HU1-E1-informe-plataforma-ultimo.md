### Test Case Specification: Consultar informe financiero · HU1 · E1 Informe de la plataforma del último período cerrado
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/013-consultar-informe-financiero/spec.md](../../../docs/features/013-consultar-informe-financiero/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC13-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con alcance declarado de Administrador Financiero y una periodicidad quincenal, mensual o trimestral, el sistema devuelve por defecto el informe del **último período cerrado**, con los totales agregados de cobros, reembolsos, dispersiones y comisiones, el neto de la plataforma y la comparación con el período inmediatamente anterior equivalente.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC13 | Specification Document | [spec.md](../../../docs/features/013-consultar-informe-financiero/spec.md) |
| Plan UC13 | Plan Document | [plan.md](../../../docs/features/013-consultar-informe-financiero/plan.md) |
| Contrato REST — consultar informe | API contract | [UC13-consultar-informe-financiero.md](../../../docs/technical-plan/contracts/rest/UC13-consultar-informe-financiero.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin interfaz propia: la verificación se hace por HTTP contra el servicio y por consultas SQL sobre `v_financial_records`.
* Los montos y cantidades son **ilustrativos y coherentes entre sí**; ajustar a la versión vigente del entorno y al dataset de QA.
* La fecha de referencia es 2026-11-02; si se ejecuta en otra fecha, recalcular el último período cerrado esperado.

##### Dependencies & Assumptions
* Existen registros financieros **confirmados** generados por UC05, UC09 y UC10 (por ejemplo, tras TC-UC10-HU4-E1) para varios propietarios y períodos.
* PostgreSQL arriba con `v_financial_records` y el servicio respondiendo en `http://localhost:8080/actuator/health`.

##### Preconditions & Default Setup
* **Initial State** : dataset mensual de 2026-10 (America/Bogota) con 10 cobros, 2 reembolsos, 8 dispersiones y 8 comisiones. Totales ilustrativos: cobros `1150000.00`, reembolsos `100000.00`, dispersiones `900000.00`, comisiones `135000.00`. Período 2026-09 (comparación): neto `125000.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, PostgreSQL accesible con `docker exec -it seashare-postgres psql -U seashare -d seashare`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta del último período cerrado con alcance PLATFORM
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar el dataset del período | `SELECT record_type, COUNT(*), SUM(amount) FROM v_financial_records WHERE created_at >= '2026-10-01T00:00:00-05:00' AND created_at < '2026-11-01T00:00:00-05:00' GROUP BY record_type;` | `2026-10`, America/Bogota | 10 cobros, 2 reembolsos, 8 dispersiones y 8 comisiones; totales de la precondición (RF-005, RF-007). | [ ] | |
| 2 | Solicitar el informe sin `period` | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY` | `scope=PLATFORM`, `periodicity=MONTHLY` | `200 OK`, `Content-Type: application/json` (RF-001, RF-003). | [ ] | |
| 3 | Verificar el período por defecto | Leer `period`, `is_closed` y `scope` del cuerpo | N/A | `period = "2026-10"`, `is_closed = true`, `scope = "PLATFORM"`, `periodicity = "MONTHLY"`; es el último mes cerrado a la fecha de ejecución (RF-003, CE-003). | [ ] | |
| 4 | Verificar los totales agregados | Comparar el cuerpo con el `SELECT` del paso 1 | N/A | `total_collections = "1150000.00"`, `total_refunds = "100000.00"`, `total_settlements = "900000.00"`, `total_commissions = "135000.00"` (RF-005, RF-007). | [ ] | |
| 5 | Verificar el neto de la plataforma | Calcular `cobros − reembolsos − dispersiones` | `1150000.00 − 100000.00 − 900000.00` | `net_amount = "150000.00"`; la comisión **no** se resta otra vez (RF-008, RF-009, CE-002). | [ ] | |
| 6 | Verificar cantidades y comisión informativa | Leer `counts` y `total_commissions` | N/A | `counts = {"CHARGE":10,"REFUND":2,"SETTLEMENT":8,"COMMISSION":8}`; `total_commissions` informativo e independiente (RF-009, CE-002). | [ ] | |
| 7 | Verificar la comparación con el período anterior | Leer el objeto `comparison` | período anterior `2026-09`, neto `125000.00` | `previous_period = "2026-09"`, `absolute_variation = "25000.00"`, `percentage_variation = "20.00"` (RF-010, CE-004). | [ ] | |
| 8 | Verificar que el DTO no trae detalle individual | Revisar las claves del JSON | N/A | Solo campos agregados (`periodicity`, `period`, `is_closed`, `scope`, `net_amount`, totales, `counts`, `comparison`); sin arreglo paginado de registros (RF-011). | [ ] | |
| 9 | Repetir con las otras periodicidades | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=SEMI_MONTHLY` y luego `periodicity=QUARTERLY` | `SEMI_MONTHLY`, `QUARTERLY` | `200 OK`; por defecto devuelve `2026-10-H2` y `2026-Q3` respectivamente, ambos `is_closed = true`, con su comparación equivalente (RF-002, RF-003, CE-003). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Omitir un parámetro obligatorio | `GET /api/v1/financial-report?scope=PLATFORM` (sin `periodicity`) | N/A | `400 VALIDATION_ERROR`; no se calcula ningún informe (catálogo §3.4, E2). | [ ] | |
| 2 | Omitir el alcance | `GET /api/v1/financial-report?periodicity=MONTHLY` | N/A | `400 VALIDATION_ERROR`; no se asume alcance por defecto (E2). | [ ] | |
| 3 | Periodicidad no soportada | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=WEEKLY` | `WEEKLY` | `400 INVALID_PERIOD`; sin cálculo ni valores parciales (contract §5). | [ ] | |
| 4 | Período con formato de otra periodicidad | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY&period=2026-10-H1` | `2026-10-H1` | `400 INVALID_PERIOD`; no se interpreta un rango libre (RF-004, CE-003). | [ ] | |
| 5 | Intento de rango libre de fechas | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY&from=2026-10-01&to=2026-10-15` | `from`, `to` | Los parámetros ajenos al contrato no alteran el período: el informe sigue siendo el mensual `2026-10` (RF-004, CE-003). | [ ] | |
| 6 | Método HTTP no soportado | `POST /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY` | N/A | `405 METHOD_NOT_ALLOWED` (catálogo §3.4, E1). | [ ] | |
| 7 | Base de datos no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, `detail` genérico sin trazas ni SQL (E8, README §4.3). | [ ] | |
| 8 | Período sin registros confirmados | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY&period=2020-01` | `2020-01` | `200 OK` con todas las métricas y cantidades en cero; no es error (casos extremos del SPEC). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un depósito de garantía pendiente sin registro confirmado no suma en dispersiones ni en el neto (RF-014, CE-005).
* **EC-002** : Los límites del período se evalúan en America/Bogota: un registro con `created_at = 2026-11-01T03:30:00Z` (31-oct 22:30 local) pertenece a `2026-10`; uno con `2026-10-01T04:30:00Z` (30-sep 23:30 local) no (RNF-003, plan D-UC13-02).
* **EC-003** : Si el período anterior tiene neto cero, `percentage_variation` es `null` y la absoluta sí se informa (RF-010, CE-004).
* **EC-004** : El período en curso (`2026-11` si se solicita explícito) se devuelve como parcial con `is_closed = false`, con corte al momento de la consulta (casos extremos del SPEC).
* **EC-005** : Los montos viajan como string decimal con 2 decimales (`"150000.00"`), redondeo `HALF_UP` al serializar (RNF-002, README §3.1).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
