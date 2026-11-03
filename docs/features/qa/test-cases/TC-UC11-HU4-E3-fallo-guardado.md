### Test Case Specification: Parámetros financieros · HU4 · E3 Fallo durante el guardado (sin cambios parciales)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../011-configurar-parametros-financieros-globales/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU4-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando los valores superan la validación pero la persistencia falla o la actualización no puede completarse, el sistema **no actualiza ningún parámetro**, informa un error controlado y conserva íntegramente la configuración anterior, sin dejar versiones ni valores parciales.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../011-configurar-parametros-financieros-globales/spec.md) |
| Implementation Plan UC11 | Plan Document | [plan.md](../../011-configurar-parametros-financieros-globales/plan.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La falla de persistencia se provoca de forma controlada (detener PostgreSQL o forzar un conflicto de `version_number`); no se modifica el código ni el esquema.
* En un entorno compartido, coordinar la ventana de indisponibilidad de la base.

##### Dependencies & Assumptions
* **TC-UC11-HU4-E1 debe haber pasado** (guardado exitoso y versionado append-only).
* Acceso a `docker compose` para detener/restaurar `postgres` y a los logs de la aplicación con `X-Correlation-Id`.

##### Preconditions & Default Setup
* **Initial State** : `financial_parameters` con `v7` vigente (`15.0000` / `15000.0000` / `10.0000` / `25.0000`, valores ilustrativos) y `count = 7` filas.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (sin validación de identidad; RF-008).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Fallo durante el guardado
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fijar la configuración anterior | `GET /api/v1/financial-parameters` y `psql` con `ORDER BY version_number` | N/A | `v7` vigente y `count = 7`; valores y `created_at` anotados como referencia. | [x] | |
| 2 | Preparar valores válidos | Verificar que el cuerpo del `PUT` pasa la validación (rangos 0–100 y seguro ≥ 0) | `"18.50"`, `"16500.00"`, `"12.00"`, `"30.00"` | Cuerpo válido según RF-013; el fallo debe ser de persistencia, no de validación. | [x] | |
| 3 | Provocar la falla de persistencia | `docker compose stop postgres` y ejecutar el `PUT` con `X-Correlation-Id: qa-uc11-hu4-e3` | mismo cuerpo del paso 2 | `500 Internal Server Error`, `Content-Type: application/problem+json`, `code = PARAMETERS_SAVE_FAILED`, `retryable = true` (contrato §5, E8). | [x] | |
| 4 | Verificar el error controlado | Inspeccionar el cuerpo *Problem Details* | N/A | `detail` genérico: sin SQL, sin trazas, sin nombres de tabla ni de clase; el detalle técnico queda en el log con el `X-Correlation-Id` (README §4.3 regla 9). | [x] | |
| 5 | Restaurar la base y verificar la configuración anterior | `docker compose start postgres`; `GET /api/v1/financial-parameters` | N/A | `200 OK` con `version_number = 7` y los cuatro valores originales; ningún valor del intento fallido quedó aplicado (HU4 esc. 3, RF-011). | [x] | |
| 6 | Verificar que no hubo cambios parciales | `psql`: contar filas y comparar `v7` con la línea base | N/A | `count = 7`; no existe fila nueva ni versión parcial; los cuatro campos de `v7` siguen intactos (RF-005, RF-011, CE-002). | [x] | |
| 7 | Reintentar tras la recuperación | Repetir el `PUT` con el mismo cuerpo | mismo cuerpo del paso 2 | `200 OK` con `{ "version_number": 8 }` y los cuatro valores persistidos; `retryable = true` se cumple (contrato §6). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fallo al confirmar la transacción | Forzar un conflicto de `UNIQUE(version_number)` (dos `PUT` concurrentes con la misma versión calculada) | dos cuerpos válidos distintos | El guardado perdedor responde `500 PARAMETERS_SAVE_FAILED`; la fila ganadora y todas las anteriores quedan intactas (plan D-UC11-10). | [x] | |
| 2 | Validación fallida no debe ser `500` | `PUT` con `platform_commission_percentage = "150.00"` | 150 % | `400 VALIDATION_ERROR` con `errors[].field = platform_commission_percentage`; sin persistencia (E2; RF-013). | [x] | |
| 3 | Valor no numérico | `PUT` con `insurance_fee_per_passenger = "abc"` | texto | `400 VALIDATION_ERROR`, mensaje `"must be a decimal string"` (contrato §5; D-UC11-15). | [x] | |
| 4 | Campo obligatorio `null` | `PUT` con `weekend_increase_percentage: null` | `null` | `400 VALIDATION_ERROR`; la versión vigente no cambia (E2; RF-013). | [x] | |
| 5 | Falla en la primera configuración | Tabla vacía + PostgreSQL detenido + `PUT` válido | N/A | `500 PARAMETERS_SAVE_FAILED`; la tabla sigue vacía y el `GET` sigue dando `404 PARAMETERS_NOT_CONFIGURED` (D-UC11-07). | [x] | |
| 6 | Error no controlado distinto de persistencia | Provocar una excepción no prevista (p. ej. fallo al leer la versión vigente antes de insertar) | N/A | `500 INTERNAL_ERROR`, `retryable = true`, `detail` genérico; la configuración anterior se conserva (plantilla del catálogo E8). | [x] | |
| 7 | Timeout del cliente con escritura ya confirmada | Enviar el `PUT` y cortar la conexión después del commit; reintentar el mismo cuerpo | mismo cuerpo | Al reintentar no se duplica la versión: si el commit ocurrió, se devuelve la versión vigente; si no, se crea una sola (contrato §6). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Atomicidad de los cuatro campos: ante cualquier fallo no queda una versión con comisión nueva y seguro antiguo (RF-005, RF-011).
* **EC-002** : Un `5xx` nunca expone trazas, SQL, nombres de tabla ni URLs internas; solo el log con `X-Correlation-Id` contiene el detalle (README §4.3 regla 9).
* **EC-003** : `retryable = true` habilita reintentar la misma petición sin cambios; la operación es segura por idempotencia (contrato §6).
* **EC-004** : El fallo no afecta a reservas ya calculadas ni a sus referencias de versión (RF-007, CE-002).
* **EC-005** : Tras un fallo, la versión vigente sigue siendo la anterior; el número que reciba el siguiente guardado válido lo asigna el entorno (ajustar a la versión vigente del entorno).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
