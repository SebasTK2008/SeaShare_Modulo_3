### Test Case Specification: Registros financieros · HU2 · E1 Consulta global con paginación por defecto
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/012-consultar-registros-financieros/spec.md](../../features/012-consultar-registros-financieros/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC12-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, existiendo más de diez registros financieros en la plataforma, el Administrador Financiero que solicita una página **sin indicar tamaño** recibe como máximo diez registros del alcance global y los metadatos de paginación correspondientes, sin omisiones ni duplicados entre páginas.

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
* Los registros son inmutables y provienen de UC05/UC09/UC10; se preparan con datos confirmados.
* Sin frontend propio: la “sesión del Administrador Financiero” es el `scope=PLATFORM` declarado como dato plano.

##### Dependencies & Assumptions
* **TC-UC12-HU1-E1 debe haber pasado** (paginación por defecto y orden estable verificados en el alcance de Propietario).
* Al menos 3 propietarios con registros confirmados en la plataforma.

##### Preconditions & Default Setup
* **Initial State** : `v_financial_records` con **34** registros globales (valor ilustrativo; ajustar al entorno) de 3 propietarios distintos, 4 embarcaciones y los cuatro tipos de registro.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (sin validación de identidad ni rol; RF-011).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta global con paginación por defecto
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el conjunto global | `psql`: `SELECT count(*), count(DISTINCT owner_id) FROM v_financial_records;` | N/A | `count = 34` (> 10) y `count(DISTINCT owner_id) >= 3` (RF-004). | [ ] | Ajustar al entorno. |
| 2 | Solicitar la primera página global sin tamaño | `GET /api/v1/financial-records?scope=PLATFORM&page=1` con `Accept: application/json` | `scope=PLATFORM`, `page=1`, sin `size` ni `owner_id` | `200 OK`, `Content-Type: application/json`; `owner_id` no es obligatorio con alcance global (contrato §2). | [ ] | |
| 3 | Verificar el tamaño por defecto | Contar `content` e inspeccionar `page_size` | N/A | `page_size = 10` y `content` con exactamente 10 elementos (RF-008, CE-003). | [ ] | |
| 4 | Verificar los metadatos globales | Inspeccionar `page_number`, `page_size`, `total_elements`, `total_pages` | N/A | `page_number = 1`, `page_size = 10`, `total_elements = 34`, `total_pages = 4` (RF-007, CE-002). | [ ] | |
| 5 | Verificar el alcance global | Revisar los `owner_id` del conjunto de páginas | N/A | Los registros abarcan más de un propietario; no se aplica ningún filtro implícito por propietario (RF-003). | [ ] | |
| 6 | Verificar el detalle de cada registro | Revisar los campos de cada elemento | N/A | `record_type`, `reservation_id`, `owner_id`, `boat_id`, `amount`, `created_at` y `external_reference` cuando exista (RF-005, RF-006, CE-004). | [ ] | |
| 7 | Verificar el orden estable | Comparar con `psql`: `SELECT * FROM v_financial_records ORDER BY created_at DESC, record_id DESC LIMIT 10;` | N/A | Orden `created_at DESC` y, ante empate, `record_id DESC` (contrato §3.4, RNF-003). | [ ] | |
| 8 | Recorrer todas las páginas | `GET` con `page=2`, `page=3`, `page=4` | `page=2..4` | 10, 10 y 4 elementos; la unión de las cuatro páginas contiene los 34 `record_id` sin duplicados ni omisiones (CE-002, RNF-003). | [ ] | |
| 9 | Verificar que la consulta es de solo lectura | Comparar conteos y revisar que no se genere archivo | N/A | Ninguna fila creada, modificada o eliminada; ningún CSV generado (RF-010, RF-013, CE-005). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | `scope` ausente | `GET /api/v1/financial-records?page=1` | sin `scope` | `400 VALIDATION_ERROR`; la consulta no se ejecuta (contrato §2). | [ ] | |
| 2 | `scope` inválido | `GET ?scope=GLOBAL&page=1` | enumeración inválida | `400 VALIDATION_ERROR`, `retryable = false` (E2). | [ ] | |
| 3 | Página inválida | `GET ?scope=PLATFORM&page=0` y `page=-1` | `page=0`, `page=-1` | `400 VALIDATION_ERROR` (RF-009). | [ ] | |
| 4 | Página fuera del total | `GET ?scope=PLATFORM&page=5` | `page=5` con 4 páginas | `404 PAGE_OUT_OF_RANGE`, `retryable = false` (RF-009). | [ ] | |
| 5 | Tamaño mayor que el máximo | `GET ?scope=PLATFORM&page=1&size=101` | `size=101` | `400 VALIDATION_ERROR` (máximo 100; contrato §2, D-22). | [ ] | |
| 6 | Tamaño explícito válido | `GET ?scope=PLATFORM&page=1&size=3` | `size=3` | `page_size = 3`, `content` con 3 elementos, `total_pages = 12` (RF-007, RF-008). | [ ] | |
| 7 | Filtro opcional por propietario en alcance global | `GET ?scope=PLATFORM&page=1&owner_id=a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d` | `owner_id` | Solo registros de ese propietario; el total se reduce al conjunto filtrado (contrato §3.1; detalle en [TC-UC12-HU2-E2](TC-UC12-HU2-E2-filtros-global.md)). | [ ] | |
| 8 | Fallo interno | Detener PostgreSQL y repetir la consulta; restaurar | N/A | `500 INTERNAL_ERROR`, `retryable = true`, `detail` genérico (E8). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La unión de todas las páginas equivale al conjunto global de `v_financial_records`, sin duplicados ni omisiones (CE-002, RNF-003).
* **EC-002** : Sin `size`, el tamaño es exactamente 10; nunca se devuelve el conjunto completo en una página (CE-003).
* **EC-003** : Con 0 registros, `page=1` devuelve `200` con página vacía y `total_pages = 0`; `page=2` devuelve `404` (la fórmula es `page > max(total_pages, 1)`, contrato §3.4).
* **EC-004** : Con 34 registros, las páginas son 10/10/10/4; el total de páginas es techo(`total_elements` / 10).
* **EC-005** : `scope=PLATFORM` no exige identidad ni credenciales; sin `Authorization` responde igual y nunca `401/403` (RF-011).
* **EC-006** : Las intenciones en curso o fallidas no forman parte del conjunto global (RF-004).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
