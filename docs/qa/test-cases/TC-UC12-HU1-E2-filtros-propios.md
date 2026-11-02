### Test Case Specification: Registros financieros · HU1 · E2 Consulta propia con filtros
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/012-consultar-registros-financieros/spec.md](../../features/012-consultar-registros-financieros/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC12-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, existiendo registros de varios tipos, propietarios y embarcaciones, el Propietario que consulta una página con filtros válidos recibe **únicamente** los registros de sus reservas que coinciden con todos los filtros indicados, sin que ningún filtro amplíe el alcance declarado.

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
* Los registros son inmutables y provienen de UC05/UC09/UC10; se preparan con datos confirmados, no desde esta consulta.
* La inclusividad de los extremos de `date_from`/`date_to` no está fijada por el contrato: se verifica el rango y se documenta el comportamiento observado.

##### Dependencies & Assumptions
* **TC-UC12-HU1-E1 debe haber pasado** (paginación por defecto y alcance de Propietario correctos).
* Base de datos accesible (`docker compose exec postgres psql -U seashare -d seashare`).

##### Preconditions & Default Setup
* **Initial State** : registros del propietario `a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d` (ilustrativo) repartidos en dos embarcaciones (`3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` y una segunda), dos reservas y los cuatro tipos; rango de fechas con registros dentro y fuera. Otro propietario con registros propios.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (alcance declarado como dato plano; RF-011).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta propia con filtros
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fijar la línea base por tipo | `psql`: `SELECT record_type, count(*) FROM v_financial_records WHERE owner_id = 'a1b2…' GROUP BY record_type;` | N/A | Conteos por tipo del propietario; sirven de contraste para cada filtro (RF-002, RF-004). | [ ] | Ajustar al entorno. |
| 2 | Filtrar por tipo `REFUND` | `GET /api/v1/financial-records?scope=OWNER&owner_id=a1b2…&page=1&transaction_type=REFUND` | `transaction_type=REFUND` | Todos los elementos son `record_type = REFUND` y del propietario declarado; `total_elements` igual al conteo base de `REFUND` (RF-002, RF-003). | [ ] | |
| 3 | Filtrar por tipo `COMMISSION` | `GET ?scope=OWNER&owner_id=a1b2…&page=1&transaction_type=COMMISSION` | `transaction_type=COMMISSION` | Devuelve solo `record_type = COMMISSION`; no se decide por comparación numérica del monto (SPEC casos extremos; RF-002). | [ ] | |
| 4 | Filtrar por embarcación | `GET ?scope=OWNER&owner_id=a1b2…&page=1&boat_id=3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` | `boat_id` de la primera embarcación | Todos los elementos tienen ese `boat_id` y el `owner_id` declarado (RF-002, RF-003). | [ ] | |
| 5 | Filtrar por reserva | `GET ?scope=OWNER&owner_id=a1b2…&page=1&reservation_id=b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | `reservation_id` de la reserva de prueba | Solo registros de esa reserva, de todos los tipos asociados (contrato §2; RF-002). | [ ] | |
| 6 | Filtrar por rango de fechas | `GET ?scope=OWNER&owner_id=a1b2…&page=1&date_from=2026-12-01&date_to=2026-12-31` | fechas del período con datos | Todos los `created_at` caen dentro del rango; no aparecen registros fuera de él (contrato §2). | [ ] | Documentar si los extremos son inclusivos. |
| 7 | Combinar filtros | `GET ?scope=OWNER&owner_id=a1b2…&page=1&transaction_type=CHARGE&boat_id=3f2c1a54-…&date_from=2026-12-01&date_to=2026-12-31` | tipo + embarcación + fechas | Solo los registros que cumplen **todos** los filtros (AND), del propietario declarado (RF-002, RF-003). | [ ] | |
| 8 | Verificar los metadatos con filtros | Inspeccionar la respuesta del paso 7 | N/A | `total_elements` coincide con el conteo filtrado en BD; `page_size = 10`; `total_pages` corresponde al conjunto filtrado (RF-007). | [ ] | |
| 9 | Verificar el detalle y los montos | Revisar los elementos devueltos | N/A | `record_type`, `reservation_id`, `owner_id`, `boat_id`, `amount` (string decimal), `created_at` y `external_reference` cuando exista (RF-005, RF-006, RNF-002). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Filtros sin coincidencias | `GET ?scope=OWNER&owner_id=a1b2…&page=1&transaction_type=SETTLEMENT&boat_id=<embarcación sin dispersiones>` | combinación sin datos | `200 OK` con `content = []`, `total_elements = 0`, `total_pages = 0`; la ausencia de registros no es error (SPEC casos extremos). | [ ] | |
| 2 | Tipo de transacción inválido | `GET ?…&transaction_type=PAYOUT` | enumeración inválida | `400 VALIDATION_ERROR`; la consulta no se ejecuta (RF-009; contrato §5). | [ ] | |
| 3 | Embarcación de otro propietario | `GET ?scope=OWNER&owner_id=a1b2…&page=1&boat_id=<embarcación del otro propietario>` | `boat_id` ajeno | Página vacía (`200`, `total_elements = 0`); el filtro **no** amplía el alcance ni expone registros ajenos (CE-001; SPEC casos extremos). | [ ] | |
| 4 | Reserva de otro propietario | `GET ?scope=OWNER&owner_id=a1b2…&page=1&reservation_id=<reserva del otro propietario>` | `reservation_id` ajeno | Página vacía; ningún registro de otro propietario se filtra por reserva (CE-001, RF-003). | [ ] | |
| 5 | Página fuera del total filtrado | `GET ?…&transaction_type=REFUND&page=5` | `page` mayor que el total del filtro | `404 PAGE_OUT_OF_RANGE`, `retryable = false` (RF-009; contrato §3.4). | [ ] | |
| 6 | Página inválida con filtros | `GET ?…&transaction_type=REFUND&page=0` | `page=0` | `400 VALIDATION_ERROR`; no se ejecuta la consulta (RF-009). | [ ] | |
| 7 | Formato de fecha inválido | `GET ?…&date_from=01/12/2026` y `date_from=2026-13-01` | formatos no `YYYY-MM-DD` | `400 VALIDATION_ERROR` (E2; contrato §2). | [ ] | |
| 8 | Rango de fechas invertido | `GET ?…&date_from=2026-12-31&date_to=2026-12-01` | inicio posterior al fin | `400 VALIDATION_ERROR` por petición mal formada (E2); el contrato no lo detalla: registrar la respuesta observada si difiere. | [ ] | |
| 9 | Tamaño de página excedido | `GET ?…&size=101` | `size=101` | `400 VALIDATION_ERROR` (máximo 100; contrato §2). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `COMMISSION` devuelve solo `RegistroDeComisión`; `external_reference` puede ser `null` en comisiones (plan D-UC12-02).
* **EC-002** : Con `scope=OWNER`, `owner_id` **es** el alcance declarado, no un filtro adicional; ningún otro filtro lo amplía (RF-003).
* **EC-003** : Los filtros se combinan con AND, nunca con OR (plan §Reglas de negocio 2).
* **EC-004** : Resolver filtros no consulta al Sistema de Gestión de Flota (RF-012).
* **EC-005** : Los montos provienen del registro inmutable y no se recalculan en la consulta (RF-004; informativo).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
