### Test Case Specification: Registros financieros · HU1 · E1 Consulta propia con paginación por defecto
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/012-consultar-registros-financieros/spec.md](../../features/012-consultar-registros-financieros/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC12-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, existiendo más de diez registros financieros de las reservas de un Propietario, la consulta de la primera página **sin indicar tamaño** devuelve como máximo diez registros de su alcance, junto con el número de página, el tamaño utilizado, el total de registros y el total de páginas, sin exponer registros de otros propietarios.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC12 | Specification Document | [spec.md](../../features/012-consultar-registros-financieros/spec.md) |
| Implementation Plan UC12 | Plan Document | [plan.md](../../features/012-consultar-registros-financieros/plan.md) |
| Contrato REST — consultar registros | REST contract | [UC12-consultar-registros-financieros.md](../../technical-plan/contracts/rest/UC12-consultar-registros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Los registros financieros son inmutables y los generan UC05/UC09/UC10; se preparan con datos de prueba ya confirmados (o *seed* controlado), no desde esta consulta.
* Sin frontend propio: la consulta se ejecuta directamente contra el endpoint.

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1, TC-UC09-HU3-E1 y TC-UC10-HU4-E1 deben haber pasado** (existen registros de cobro, reembolso, dispersión y comisión).
* Base de datos accesible (`docker compose exec postgres psql -U seashare -d seashare`).

##### Preconditions & Default Setup
* **Initial State** : `v_financial_records` con **23** registros del propietario `a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d` (valor ilustrativo; ajustar al entorno) y al menos 5 registros de otro propietario. Los 23 incluyen los cuatro tipos (`CHARGE`, `REFUND`, `SETTLEMENT`, `COMMISSION`).
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (el alcance viaja como dato plano; el sistema no valida identidad — RF-011).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta propia con paginación por defecto
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el conjunto del propietario | `psql`: `SELECT count(*) FROM v_financial_records WHERE owner_id = 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d';` | `owner_id` de prueba | `count = 23` (> 10) y existe al menos un registro de otro propietario (RF-004). | [ ] | Ajustar el total a la versión vigente del entorno. |
| 2 | Solicitar la primera página sin tamaño | `GET /api/v1/financial-records?scope=OWNER&owner_id=a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d&page=1` con `Accept: application/json` | `scope=OWNER`, `page=1`, sin `size` | `200 OK`, `Content-Type: application/json` (contrato §4). | [ ] | |
| 3 | Verificar el tamaño por defecto | Contar elementos de `content` e inspeccionar `page_size` | N/A | `page_size = 10` y `content` con exactamente 10 elementos; el resto no se devuelve en esta página (RF-008, CE-003). | [ ] | |
| 4 | Verificar los metadatos de paginación | Inspeccionar `page_number`, `page_size`, `total_elements`, `total_pages` | N/A | `page_number = 1`, `page_size = 10`, `total_elements = 23`, `total_pages = 3` (RF-007). | [ ] | |
| 5 | Verificar el alcance declarado | Revisar `owner_id` de cada elemento | N/A | Todos los elementos tienen `owner_id = a1b2c3d4-…`; ningún registro de otro propietario aparece (RF-003, CE-001). | [ ] | |
| 6 | Verificar el detalle de cada registro | Revisar los campos de cada elemento | N/A | Cada uno incluye `record_type`, `reservation_id`, `owner_id`, `boat_id`, `amount`, `created_at`; `external_reference` aparece cuando existe (RF-005, RF-006, CE-004). | [ ] | |
| 7 | Verificar el orden estable | Comparar con `psql`: `SELECT * FROM v_financial_records WHERE owner_id = 'a1b2…' ORDER BY created_at DESC, record_id DESC LIMIT 10;` | N/A | Orden `created_at DESC` y, ante empate, `record_id DESC` (contrato §3.4, RNF-003). | [ ] | |
| 8 | Recorrer el resto de páginas | `GET` con `page=2` y `page=3` | `page=2`, `page=3` | Página 2 con 10 elementos y página 3 con 3; la unión de las tres páginas cubre los 23 registros sin duplicados ni omisiones (RNF-003). | [ ] | |
| 9 | Verificar que la consulta es de solo lectura | Comparar conteos antes/después y revisar que no se genere archivo | N/A | `count` sin cambios, ninguna fila creada/modificada y ningún CSV generado (RF-010, RF-013, CE-005). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Falta `owner_id` con alcance de Propietario | `GET ?scope=OWNER&page=1` | sin `owner_id` | `400 VALIDATION_ERROR`; la consulta no se ejecuta (contrato §2 y §5; RF-001). | [ ] | |
| 2 | Número de página inválido | `GET ?scope=OWNER&owner_id=…&page=0` | `page=0` | `400 VALIDATION_ERROR`, `retryable = false` (RF-009; E2). | [ ] | |
| 3 | Página fuera del total | `GET ?scope=OWNER&owner_id=…&page=4` | `page=4` con 3 páginas | `404 PAGE_OUT_OF_RANGE`, `retryable = false` (RF-009; contrato §3.4). | [ ] | |
| 4 | Tamaño de página fuera del máximo | `GET ?scope=OWNER&owner_id=…&page=1&size=101` | `size=101` | `400 VALIDATION_ERROR` (máximo 100; contrato §2, general-plan D-22). | [ ] | |
| 5 | Tamaño de página cero o negativo | `GET ?…&page=1&size=0` y `size=-5` | `size=0`, `size=-5` | `400 VALIDATION_ERROR`; no se ejecuta la consulta (RF-009). | [ ] | |
| 6 | Alcance inválido o ausente | `GET ?scope=OWN&page=1` y `GET ?owner_id=…&page=1` | enumeración inválida / sin `scope` | `400 VALIDATION_ERROR` (contrato §2; E2). | [ ] | |
| 7 | Fallo interno | Detener PostgreSQL y repetir la consulta; restaurar | N/A | `500 INTERNAL_ERROR`, `retryable = true`, `detail` genérico (E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Exactamente 10 registros en el alcance → `total_pages = 1` y una sola página llena; no aparece una página 2 vacía.
* **EC-002** : `total_pages` = techo(`total_elements` / 10); con 23 registros la última página tiene 3 (contrato §3.4).
* **EC-003** : Un filtro de embarcación de otro propietario dentro de `scope=OWNER` no amplía el alcance: devuelve página vacía, nunca registros ajenos (SPEC casos extremos; CE-001).
* **EC-004** : Las intenciones en curso o fallidas (`IntenciónDeCobro`, etc.) no aparecen: solo los cuatro registros inmutables (RF-004).
* **EC-005** : `size=100` es válido y devuelve hasta 100 elementos; `page_size` refleja el valor solicitado (contrato §2).
* **EC-006** : Sin `Authorization` la consulta funciona igual (no hay `401/403`: seguridad fuera de alcance, RF-011).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
