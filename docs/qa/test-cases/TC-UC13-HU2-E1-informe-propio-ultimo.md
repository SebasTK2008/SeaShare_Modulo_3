### Test Case Specification: Consultar informe financiero · HU2 · E1 Informe propio del último período cerrado
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/013-consultar-informe-financiero/spec.md](../../../docs/features/013-consultar-informe-financiero/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC13-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con alcance declarado de Propietario (`owner_id`), el sistema devuelve por defecto el informe del **último período cerrado** agregando únicamente los registros financieros relacionados con ese `owner_id`, calcula sus ganancias como el total de dispersiones confirmadas y compara con el período anterior equivalente sin exponer datos de otros propietarios.

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
* Sin interfaz propia: verificación por HTTP y por SQL sobre `v_financial_records`.
* Montos y cantidades **ilustrativos**; ajustar a la versión vigente del entorno y al dataset de QA.
* Fecha de referencia 2026-11-02: último mes cerrado `2026-10`.

##### Dependencies & Assumptions
* **TC-UC13-HU1-E1 debe haber pasado** (agregado de plataforma y dataset mensual verificados).
* El dataset contiene al menos dos propietarios con registros en 2026-09 y 2026-10, generados por UC05, UC09 y UC10.

##### Preconditions & Default Setup
* **Initial State** : `owner_id = 11111111-1111-4111-8111-111111111111` (Propietario A) con 2 cobros `300000.00`, 1 reembolso `20000.00`, 2 dispersiones `250000.00` y 2 comisiones `37500.00` en 2026-10; en 2026-09 sus dispersiones suman `200000.00`. El Propietario B (`22222222-2222-4222-8222-222222222222`) tiene 5 cobros `500000.00`, 1 reembolso `80000.00`, 4 dispersiones `400000.00` y 4 comisiones `60000.00` en 2026-10.
* **Environment** : staging local (`docker compose --profile app up --build -d`); PostgreSQL con `docker exec -it seashare-postgres psql -U seashare -d seashare`.
* **Test Accounts** : N/A (la identidad viaja como dato plano; seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta propia del último período cerrado
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Verificar los registros de ambos propietarios | `SELECT owner_id, record_type, COUNT(*), SUM(amount) FROM v_financial_records WHERE created_at >= '2026-10-01T00:00:00-05:00' AND created_at < '2026-11-01T00:00:00-05:00' GROUP BY owner_id, record_type;` | A y B, `2026-10` | A y B tienen los totales de la precondición; sirven de contraste para detectar fuga de datos (RF-006, CE-001). | [ ] | |
| 2 | Solicitar el informe propio sin `period` | `GET /api/v1/financial-report?scope=OWNER&owner_id=11111111-1111-4111-8111-111111111111&periodicity=MONTHLY` | `scope=OWNER`, `owner_id` de A | `200 OK`; no se exige autenticación y el alcance declarado se aplica tal cual (RF-001, RF-006). | [ ] | |
| 3 | Verificar el período y el alcance | Leer `period`, `is_closed`, `scope`, `periodicity` | N/A | `period = "2026-10"`, `is_closed = true`, `scope = "OWNER"`, `periodicity = "MONTHLY"` (RF-003). | [ ] | |
| 4 | Verificar los totales del propietario | Comparar con el `SELECT` del paso 1 para A | N/A | `total_collections = "300000.00"`, `total_refunds = "20000.00"`, `total_settlements = "250000.00"`, `total_commissions = "37500.00"`; solo registros de A (RF-006, RF-007, CE-001). | [ ] | |
| 5 | Verificar el cálculo de ganancias | Leer `net_amount` | `total_settlements` de A | `net_amount = "250000.00"` = total de dispersiones confirmadas de A; **no** se aplica la fórmula de plataforma (RF-008, CE-002). | [ ] | |
| 6 | Verificar las cantidades | Leer `counts` | N/A | `CHARGE=2`, `REFUND=1`, `SETTLEMENT=2`, `COMMISSION=2`, sin conteos de B (RF-009, CE-001). | [ ] | |
| 7 | Verificar el aislamiento entre propietarios | Comparar con el informe `PLATFORM` y con los totales de B | plataforma: cobros `1150000.00`, neto `150000.00` | Los valores de A no coinciden con los de la plataforma ni incluyen los `400000.00` de dispersiones de B; no aparece ningún dato de otro propietario (RF-006, CE-001). | [ ] | |
| 8 | Verificar la comparación propia | Leer `comparison` | A, anterior `2026-09`, dispersiones `200000.00` | `previous_period = "2026-09"`, `absolute_variation = "50000.00"`, `percentage_variation = "25.00"` (RF-010, CE-004). | [ ] | |
| 9 | Verificar que no se consulta Flota ni se valida identidad | Revisar logs/request journal de Flota y la respuesta | N/A | No hay llamada al Sistema de Gestión de Flota; no hay `401`/`403`; se aplica el `owner_id` declarado (RF-015; general-plan §7.1). | [ ] | |
| 10 | Verificar que el DTO es solo agregado | Revisar las claves del JSON | N/A | Sin detalle individual paginado; solo métricas, `counts` y `comparison` (RF-011). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Alcance OWNER sin `owner_id` | `GET /api/v1/financial-report?scope=OWNER&periodicity=MONTHLY` | N/A | `400 VALIDATION_ERROR` con `field = "owner_id"`; no se asume propietario (contract §5, E2). | [ ] | |
| 2 | `owner_id` con formato inválido | `GET ...&scope=OWNER&owner_id=abc&periodicity=MONTHLY` | `abc` | `400 VALIDATION_ERROR`; no se calcula el informe (E2). | [ ] | |
| 3 | Propietario sin registros | `GET ...&scope=OWNER&owner_id=99999999-9999-4999-8999-999999999999&periodicity=MONTHLY` | UUID sin registros | `200 OK` con métricas y cantidades en cero; no es error (casos extremos del SPEC). | [ ] | |
| 4 | `owner_id` enviado con alcance PLATFORM | `GET ...&scope=PLATFORM&owner_id=<A>&periodicity=MONTHLY` | A | Prevalece el alcance declarado `PLATFORM`: el informe es global y no se filtra por A (RF-005, CE-001). | [ ] | |
| 5 | Periodicidad no soportada | `GET ...&scope=OWNER&owner_id=<A>&periodicity=WEEKLY` | `WEEKLY` | `400 INVALID_PERIOD`; sin cálculo parcial (contract §5). | [ ] | |
| 6 | Período con formato inválido | `GET ...&scope=OWNER&owner_id=<A>&periodicity=MONTHLY&period=2026-10-H1` | `2026-10-H1` | `400 INVALID_PERIOD`; no se acepta un rango libre (RF-004, CE-003). | [ ] | |
| 7 | Base de datos no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, `detail` genérico (E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Cero registros de otros propietarios en la respuesta, incluso si comparten la misma reserva o embarcación (RF-006, CE-001).
* **EC-002** : Un reembolso del propietario se informa en `total_refunds` pero **no** se resta de sus ganancias; `net_amount` sigue siendo solo dispersiones (RF-008, CE-002).
* **EC-003** : La comparación usa el período anterior del **mismo** `owner_id`; no se compara contra el agregado de la plataforma (RF-010, CE-004).
* **EC-004** : Depósitos de garantía pendientes sin registro confirmado no suman en las dispersiones del propietario (RF-014, CE-005).
* **EC-005** : Un propietario con solo reembolsos devuelve `net_amount = "0.00"` y `total_refunds` mayor que cero (RF-008).
* **EC-006** : Los límites del período se calculan en America/Bogota, igual que en el alcance de plataforma (RNF-003, plan D-UC13-02).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
