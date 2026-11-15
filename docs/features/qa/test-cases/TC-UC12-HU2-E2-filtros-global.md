### Test Case Specification: Registros financieros · HU2 · E2 Consulta global filtrada
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/012-consultar-registros-financieros/spec.md](../../012-consultar-registros-financieros/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC12-HU2-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, existiendo registros que no coinciden con los filtros solicitados, el Administrador Financiero que consulta el alcance global por tipo de transacción, propietario, embarcación, reserva o rango de fechas recibe **únicamente** los registros que coinciden con **todos** los filtros indicados, con sus metadatos de paginación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC12 | Specification Document | [spec.md](../../012-consultar-registros-financieros/spec.md) |
| Implementation Plan UC12 | Plan Document | [plan.md](../../012-consultar-registros-financieros/plan.md) |
| Contrato REST — consultar registros | REST contract | [UC12-consultar-registros-financieros.md](../../../technical-plan/contracts/rest/UC12-consultar-registros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Los registros son inmutables y provienen de UC05/UC09/UC10; se preparan con datos confirmados.
* La inclusividad de los extremos de `date_from`/`date_to` no está fijada por el contrato: se verifica el rango y se documenta el comportamiento observado.

##### Dependencies & Assumptions
* **TC-UC12-HU2-E1 debe haber pasado** (alcance global y paginación por defecto correctos).
* Base de datos accesible (`docker compose exec postgres psql -U seashare -d seashare`).

##### Preconditions & Default Setup
* **Initial State** : registros globales de 3 propietarios (`a1b2c3d4-…` y otros dos, ilustrativos), 4 embarcaciones (`3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` entre ellas), varias reservas, los cuatro tipos y fechas dentro y fuera del período de prueba.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (alcance declarado como dato plano; RF-011).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta global filtrada
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fijar la línea base filtrada | `psql`: conteos de `v_financial_records` por tipo, propietario y embarcación | N/A | Totales de contraste para cada filtro (RF-002, RF-004). | [x] | Ajustar al entorno. |
| 2 | Filtrar globalmente por tipo | `GET /api/v1/financial-records?scope=PLATFORM&page=1&transaction_type=SETTLEMENT` | `transaction_type=SETTLEMENT` | Solo `record_type = SETTLEMENT`, de todos los propietarios; `total_elements` = conteo global de dispersiones (RF-002, RF-003). | [x] | |
| 3 | Filtrar por comisión | `GET ?scope=PLATFORM&page=1&transaction_type=COMMISSION` | `transaction_type=COMMISSION` | Solo `record_type = COMMISSION`; no se compara numéricamente el monto de comisión (SPEC casos extremos; RF-002). | [x] | |
| 4 | Filtrar por propietario | `GET ?scope=PLATFORM&page=1&owner_id=a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d` | `owner_id` | Solo registros de ese propietario; `owner_id` actúa como filtro opcional en alcance global (contrato §3.1). | [x] | |
| 5 | Filtrar por embarcación | `GET ?scope=PLATFORM&page=1&boat_id=3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01` | `boat_id` | Solo registros de esa embarcación, de cualquier propietario (RF-002). | [x] | |
| 6 | Filtrar por reserva | `GET ?scope=PLATFORM&page=1&reservation_id=b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | `reservation_id` | Solo registros de esa reserva, de todos sus tipos (contrato §2). | [x] | |
| 7 | Filtrar por rango de fechas | `GET ?scope=PLATFORM&page=1&date_from=2026-12-01&date_to=2026-12-31` | fechas del período con datos | Todos los `created_at` caen dentro del rango; no aparecen registros fuera de él (contrato §2). | [x] | Documentar inclusividad de extremos. |
| 8 | Combinar todos los filtros | `GET ?scope=PLATFORM&page=1&transaction_type=CHARGE&owner_id=a1b2…&boat_id=3f2c1a54-…&reservation_id=b7d0e2a1-…&date_from=2026-12-01&date_to=2026-12-31` | todos los filtros | Solo los registros que cumplen **todos** los filtros (AND), sin importar el propietario (RF-002, RF-008). | [x] | |
| 9 | Verificar metadatos y recorrido completo | Inspeccionar `total_elements`/`total_pages` y recorrer las páginas del conjunto filtrado | N/A | `total_elements` coincide con el conteo filtrado en BD; la unión de páginas no omite ni duplica registros (RF-007, CE-002, RNF-003). | [x] | |
| 10 | Verificar el detalle de cada registro | Revisar los campos de los elementos | N/A | `record_type`, `reservation_id`, `owner_id`, `boat_id`, `amount`, `created_at` y `external_reference` cuando exista (RF-005, RF-006, CE-004). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Filtros contradictorios | `GET ?scope=PLATFORM&page=1&owner_id=<propietario A>&boat_id=<embarcación del propietario B>` | propietario A + embarcación de B | `200 OK` con `content = []`, `total_elements = 0`, `total_pages = 0`; no es error (SPEC casos extremos). | [x] | |
| 2 | Filtros sin coincidencias | `GET ?scope=PLATFORM&page=1&transaction_type=REFUND&date_from=<fecha sin reembolsos>` | combinación sin datos | Página vacía `200`, sin tratarla como error (SPEC casos extremos; RF-009). | [x] | |
| 3 | Tipo de transacción inválido | `GET ?scope=PLATFORM&page=1&transaction_type=PAYOUT` | enumeración inválida | `400 VALIDATION_ERROR`; la consulta no se ejecuta (RF-009; contrato §5). | [x] | |
| 4 | Formato de fecha inválido | `GET ?…&date_from=2026-13-01` y `date_to=31-12-2026` | formatos no `YYYY-MM-DD` | `400 VALIDATION_ERROR` (E2; contrato §2). | [x] | |
| 5 | Rango de fechas invertido | `GET ?…&date_from=2026-12-31&date_to=2026-12-01` | inicio posterior al fin | `400 VALIDATION_ERROR` por petición mal formada (E2); el contrato no lo detalla: registrar la respuesta observada si difiere. | [x] | |
| 6 | Página fuera del total filtrado | `GET ?…&transaction_type=COMMISSION&page=10` | `page` mayor que el total del filtro | `404 PAGE_OUT_OF_RANGE`, `retryable = false` (RF-009). | [x] | |
| 7 | Paginación inválida con filtros | `GET ?…&transaction_type=COMMISSION&page=0` y `size=101` | `page=0`, `size=101` | `400 VALIDATION_ERROR`; no se ejecuta la consulta (RF-009). | [x] | |
| 8 | Filtro por propietario inexistente | `GET ?scope=PLATFORM&page=1&owner_id=00000000-0000-4000-8000-000000000000` | UUID válido sin registros | `200 OK` con página vacía; un identificador del *query string* que no existe es filtro vacío, no `404` (README §4.3 regla 2). | [x] | |
| 9 | Fallo interno | Detener PostgreSQL y repetir la consulta; restaurar | N/A | `500 INTERNAL_ERROR`, `retryable = true`, `detail` genérico (E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `COMMISSION` devuelve solo `RegistroDeComisión`; un cobro con el mismo importe que una comisión **no** se incluye (RF-002; SPEC casos extremos).
* **EC-002** : Un `owner_id` inexistente es un filtro vacío (página vacía), no un recurso inexistente de la URL (README §4.3 regla 2).
* **EC-003** : Resolver filtros no consulta al Sistema de Gestión de Flota (RF-012; plan regla 10).
* **EC-004** : Los filtros se combinan con AND; nunca se relajan entre sí (plan §Reglas de negocio 2).
* **EC-005** : Con filtros activos, el recorrido de páginas no omite ni duplica registros y el orden sigue siendo `created_at DESC, record_id DESC` (RNF-003, contrato §3.4).
* **EC-006** : Solo se consultan los cuatro registros inmutables; intenciones en curso o fallidas quedan fuera aunque coincidan con los filtros (RF-004).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
