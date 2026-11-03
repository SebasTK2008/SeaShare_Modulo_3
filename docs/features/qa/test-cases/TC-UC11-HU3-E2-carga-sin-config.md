### Test Case Specification: Parámetros financieros · HU3 · E2 Carga sin configuración previa (no inventa valores)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../011-configurar-parametros-financieros-globales/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU3-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando todavía no existe ninguna versión de la configuración financiera global, el sistema informa que la configuración está **pendiente** mediante un error controlado y **no inventa valores** para ninguno de los cuatro parámetros ni para los derivados de solo lectura.

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
* Sin frontend propio: el mensaje "configuración pendiente" se verifica por el `404 PARAMETERS_NOT_CONFIGURED` y por la ausencia de valores en el cuerpo.
* Requiere una base **sin** versiones: la migración inicial crea la tabla vacía y no inserta versiones (plan D-UC11-07).

##### Dependencies & Assumptions
* **TC-UC11-HU4-E1 debe haber pasado** para conocer que el primer `PUT` crea la versión `1`.
* Capacidad de recrear la base o de ejecutar `TRUNCATE`/restaurar un snapshot limpio antes de la prueba.

##### Preconditions & Default Setup
* **Initial State** : `financial_parameters` vacía (`SELECT count(*) = 0`); no hay ninguna versión guardada.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (sin validación de identidad; RF-008).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Carga sin configuración previa
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar que no hay configuración | `psql`: `SELECT count(*) FROM financial_parameters;` | N/A | `count = 0`; ninguna versión inicial sembrada por la migración (D-UC11-07). | [x] | |
| 2 | Solicitar la configuración | `GET http://localhost:8080/api/v1/financial-parameters` con `Accept: application/json` | N/A | `404 Not Found`, `Content-Type: application/problem+json` (contrato §5). | [x] | |
| 3 | Verificar el error controlado | Inspeccionar el cuerpo *Problem Details* | N/A | `type = about:blank`, `title = "Financial parameters not configured"`, `status = 404`, `code = PARAMETERS_NOT_CONFIGURED`, `retryable = true` y el `detail` del contrato (HU3 esc. 2). | [x] | |
| 4 | Verificar que no se inventan valores | Inspeccionar el cuerpo completo en busca de parámetros | N/A | No aparecen `platform_commission_percentage`, `insurance_fee_per_passenger`, `weekend_increase_percentage`, `high_season_increase_percentage`, `guarantee_deposit_rule` ni `high_season_windows`; no hay ceros ni porcentajes por defecto (HU3 esc. 2; RF-009). | [x] | |
| 5 | Verificar que la lectura no crea estado | Volver a contar filas y versiones | N/A | `count` sigue en `0`; el `GET` no crea una versión ni una fila “vacía” (solo lectura, contrato §1). | [x] | |
| 6 | Verificar el reintento seguro | Repetir el `GET` dos veces | N/A | Misma respuesta `404 PARAMETERS_NOT_CONFIGURED`; `retryable = true` indica que puede reintentarse cuando el Administrador guarde (contrato §6). | [x] | |
| 7 | Verificar el estado de la pantalla | Consumir la respuesta con el cliente de prueba | N/A | El cliente muestra “configuración pendiente” y campos sin valores, no un formulario precargado con valores ficticios (HU3 esc. 2). | [x] | Observación del cliente, sin UI propia. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | `Accept` no soportado | `GET /api/v1/financial-parameters` con `Accept: application/xml` | tabla vacía | `406 NOT_ACCEPTABLE` (E1), no `404`: la validación de formato precede a la búsqueda del recurso (README §4.3 regla 1). | [x] | |
| 2 | Método no soportado | `POST /api/v1/financial-parameters` | body del `PUT` | `405 METHOD_NOT_ALLOWED` (E1); la tabla sigue vacía. | [x] | |
| 3 | Primera configuración válida | `PUT /api/v1/financial-parameters` con los cuatro valores | `"15.00"`, `"15000.00"`, `"10.00"`, `"25.00"` | `200 OK` con `{ "version_number": 1 }`; luego el `GET` responde `200` con esos valores (D-UC11-07, RF-005). | [x] | Camino de recuperación del 404. |
| 4 | Guardado inválido antes de configurar | `PUT` con `platform_commission_percentage = "150.00"` | valor fuera de rango | `400 VALIDATION_ERROR` con `errors[].field`; la tabla **sigue vacía** y el `GET` sigue dando `404` (RF-013). | [x] | |
| 5 | Fallo interno durante la lectura | Detener PostgreSQL y solicitar el `GET`; restaurar | N/A | `500 INTERNAL_ERROR`, no `404`: no se reporta “sin configurar” ante una falla de BD (E8; README §4.3 regla 10). | [x] | |
| 6 | Configuración creada por otro proceso | Tras el `404`, otro cliente guarda una versión válida y se reintenta el `GET` | `version_number = 1` | `200 OK` con la versión guardada; el `retryable = true` del `404` se cumple (contrato §5). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El `404 PARAMETERS_NOT_CONFIGURED` de UC11 no debe confundirse con el `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` que consumen UC01/UC04: verificar HTTP y `code` (plan §Contratos; catálogo §3.4).
* **EC-002** : Tabla vacía tras reiniciar el entorno: si un *seed* externo inserta versiones, la prueba deja de ser válida; documentar el estado real del entorno (D-UC11-07).
* **EC-003** : El `404` no consume ni reserva un `version_number`; la primera escritura válida obtiene `1`.
* **EC-004** : Reintentar el `GET` tras el `404` nunca devuelve `200` con valores parciales o derivados calculados sin parámetros (principio “nunca valores parciales”, README §4.3 regla 7).
* **EC-005** : El `detail` del `404` es específico y accionable; no expone nombres de tablas ni SQL (README §4.3 regla 9).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
