### Test Case Specification: Parámetros financieros · HU3 · E1 Carga de la configuración vigente
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../011-configurar-parametros-financieros-globales/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, existiendo una versión vigente de `ParámetrosFinancierosGlobales`, el sistema devuelve en **una única respuesta** los cuatro parámetros configurables, el número de versión vigente y los dos datos derivados de solo lectura (regla del depósito de garantía y ventanas de temporada alta calculadas), de modo que la pantalla de configuración muestre el estado actual antes de editarlo.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../011-configurar-parametros-financieros-globales/spec.md) |
| Implementation Plan UC11 | Plan Document | [plan.md](../../011-configurar-parametros-financieros-globales/plan.md) |
| Contrato REST — obtener parámetros | REST contract | [UC11-obtener-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-obtener-parametros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin frontend propio: el "abrir la pantalla" se verifica contra `GET /api/v1/financial-parameters` y el estado que un cliente de prueba muestra al recibir la respuesta.
* Las ventanas de temporada alta dependen del calendario de UC02 (`HighSeasonCalendar`); este TC no re-prueba el calendario, solo que el `GET` las expone.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`** (automático con el perfil `qa`, ver `matriz-qa.md` → *Seed inicial de QA*): existe al menos una versión persistida (seed o TC-UC11-HU4-E1); no bloquea la ejecución.
* Base de datos accesible (`docker compose exec postgres psql -U seashare -d seashare`).

##### Preconditions & Default Setup
* **Initial State** : `financial_parameters` con `v7` **vigente** y `v6` como versión anterior (el seed base carga `v1..v7`; basta con que exista una versión previa): `commission_pct = 15.0000`, `insurance_fee_per_passenger = 15000.0000`, `weekend_increase_pct = 10.0000`, `high_season_increase_pct = 25.0000` (valores ilustrativos del contrato; ajustar a la versión vigente del entorno).
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (el sistema no valida identidad; RF-008).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Carga de la configuración vigente
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el estado persistido | `psql`: `SELECT version_number, commission_pct, insurance_fee_per_passenger, weekend_increase_pct, high_season_increase_pct FROM financial_parameters ORDER BY version_number;` | N/A | Existen `v6` y `v7`; la vigente es `v7` (mayor `version_number`) (RF-006). | [x] | |
| 2 | Solicitar la configuración | `GET http://localhost:8080/api/v1/financial-parameters` con `Accept: application/json` y `X-Correlation-Id: qa-uc11-hu3-e1` | N/A | `200 OK`, `Content-Type: application/json`; un único objeto con los 7 campos del contrato (RF-009). | [x] | |
| 3 | Verificar los cuatro parámetros configurables | Comparar el cuerpo con la fila `v7` | `platform_commission_percentage`, `insurance_fee_per_passenger`, `weekend_increase_percentage`, `high_season_increase_percentage` | Se devuelven los valores de `v7` —no los de `v6`— con escala de 4 decimales (`"15.0000"`, `"15000.0000"`, `"10.0000"`, `"25.0000"`) (RF-006, RF-009, RNF-002). | [x] | |
| 4 | Verificar la trazabilidad de versión | Inspeccionar `version_number` en la respuesta | N/A | `version_number = 7`, igual a la versión vigente consultada (contrato §4; general-plan D-24). | [x] | |
| 5 | Verificar la regla del depósito | Inspeccionar `guarantee_deposit_rule` | N/A | Devuelve el texto `"10% de la tarifa base diaria"`; **no** devuelve un monto de depósito (el monto depende de la embarcación) (RF-012). | [x] | |
| 6 | Verificar las ventanas de temporada alta | Inspeccionar `high_season_windows` | N/A | Array calculado por calendario con las ventanas vigentes: `"11-15→01-15"`, `"06-01→07-30"`, `"semana-santa"`, `"10-05→10-12"` (RF-012; SPEC 2 RF-006). | [x] | Formato `MM-dd→MM-dd` / `semana-santa`. |
| 7 | Verificar que los derivados son de solo lectura | Inspeccionar el cuerpo completo | N/A | El depósito y las ventanas no admiten edición: no hay campos editables para ellos y el `GET` no expone un importe de depósito ni fechas configurables (RF-012). | [x] | |
| 8 | Verificar que la lectura no muta el estado | Repetir el `GET` y volver a contar filas | N/A | Segunda respuesta idéntica; `SELECT count(*) FROM financial_parameters` no cambia y no aparece una versión nueva (solo lectura, contrato §1). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | `Accept` no soportado | `GET /api/v1/financial-parameters` con `Accept: application/xml` | `Accept: application/xml` | `406 NOT_ACCEPTABLE`, `code = NOT_ACCEPTABLE`, `retryable = false` (catálogo E1). | [x] | |
| 2 | Método no soportado en la ruta | `POST /api/v1/financial-parameters` con cuerpo JSON | body del `PUT` | `405 METHOD_NOT_ALLOWED`, `code = METHOD_NOT_ALLOWED` (catálogo E1); el `GET` sigue respondiendo `200`. | [x] | |
| 3 | Fallo interno al leer | Detener PostgreSQL (`docker compose stop postgres`) y solicitar el `GET`; luego restaurar | N/A | `500 INTERNAL_ERROR`, `retryable = true`, `detail` genérico sin SQL ni nombres internos (E8; README §4.3 regla 9). No se confunde con `404`. | [x] | Restaurar el contenedor al terminar. |
| 4 | Sin ninguna versión configurada | Vaciar `financial_parameters` y solicitar el `GET` | tabla vacía | `404 PARAMETERS_NOT_CONFIGURED`; el detalle completo se cubre en [TC-UC11-HU3-E2](TC-UC11-HU3-E2-carga-sin-config.md). | [x] | Frontera del mismo endpoint. |
| 5 | `X-Correlation-Id` omitido | `GET` sin el header | N/A | La petición se atiende igual (`200`) y la respuesta trae un `X-Correlation-Id` (recibido o generado) (README §3.2). | [x] | |
| 6 | Valores en los límites del rango | Guardar porcentajes `0.0000` y `100.0000` y volver a cargar | `commission=0.0000`, `high_season=100.0000` | El `GET` devuelve exactamente `"0.0000"` y `"100.0000"`, sin normalizarlos a otros valores (RF-009, RF-013). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El valor leído coincide exactamente con el almacenado en `NUMERIC(18,4)`; `PUT → GET → PUT` con los mismos valores no crea versión nueva (contrato §4 y §6).
* **EC-002** : Las ventanas de temporada alta son derivadas: no se configuran por este caso de uso y enviarlas en el `PUT` debe rechazarse como campo no aceptado (RF-012).
* **EC-003** : El depósito se informa como **regla**, nunca como importe; sin `boat_id` en la petición el sistema no puede resolver un monto (RF-012, D-UC11-02).
* **EC-004** : Si otro proceso guarda una versión entre dos `GET`, el segundo devuelve la nueva versión vigente (mayor `version_number`) sin `404` ni caché obsoleta (RF-006).
* **EC-005** : La respuesta no incluye `id` interno ni `created_by`; solo los 7 campos del contrato (RNF-001).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
