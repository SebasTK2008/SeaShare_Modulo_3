### Test Case Specification: Parámetros financieros · HU4 · E1 Guardado completo y exitoso (versión atómica)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../features/011-configurar-parametros-financieros-globales/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU4-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, al guardar valores válidos de comisión, seguro, incremento de fin de semana e incremento de temporada alta, el sistema los valida y persiste **los cuatro** como una única operación atómica que crea una nueva versión vigente, conservando íntegras las versiones anteriores y sin efecto retroactivo sobre reservas ya calculadas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../features/011-configurar-parametros-financieros-globales/spec.md) |
| Implementation Plan UC11 | Plan Document | [plan.md](../../features/011-configurar-parametros-financieros-globales/plan.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay endpoint de consulta por número de versión: las versiones históricas se inspeccionan en base de datos.
* La verificación de “sin efecto retroactivo” requiere una reserva ya calculada; si el entorno no la tiene, registrar el paso como bloqueado y anotar la dependencia.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`**: existe una versión vigente persistida (seed o TC-UC11-HU3-E1); este TC no depende de otro TC.
* Base de datos accesible (`docker compose exec postgres psql -U seashare -d seashare`).

##### Preconditions & Default Setup
* **Initial State** : `financial_parameters` con `v7` vigente (`15.0000` / `15000.0000` / `10.0000` / `25.0000`, valores ilustrativos; ajustar a la versión vigente del entorno).
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (el `PUT` no transporta identidad ni exige `Authorization`; RF-008).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Guardado completo y exitoso
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Fijar la línea base | `GET /api/v1/financial-parameters` y `psql` con `ORDER BY version_number` | N/A | Versión vigente `v7` con los cuatro valores y `count = 7` filas (ajustar al entorno). | [ ] | |
| 2 | Guardar los cuatro valores válidos | `PUT http://localhost:8080/api/v1/financial-parameters` con `Content-Type: application/json` y `X-Correlation-Id: qa-uc11-hu4-e1` | `"platform_commission_percentage": "18.50"`, `"insurance_fee_per_passenger": "16500.00"`, `"weekend_increase_percentage": "12.00"`, `"high_season_increase_percentage": "30.00"` (ilustrativos) | `200 OK` con `{ "version_number": 8 }`; no `204`, no `401/403` (contrato §4; D-UC11-16). | [ ] | |
| 3 | Verificar la nueva versión vigente | `GET /api/v1/financial-parameters` | N/A | `version_number = 8` y los cuatro valores nuevos serializados con 4 decimales: `"18.5000"`, `"16500.0000"`, `"12.0000"`, `"30.0000"` (CE-001, RF-009). | [ ] | |
| 4 | Verificar atomicidad de la escritura | `psql`: `SELECT version_number, commission_pct, insurance_fee_per_passenger, weekend_increase_pct, high_season_increase_pct FROM financial_parameters ORDER BY version_number;` | N/A | Una sola fila nueva (`v8`) con los cuatro valores; ninguna fila intermedia ni valores mezclados con `v7` (RF-005, RF-011, RNF-003). | [ ] | |
| 5 | Verificar el historial inmutable | Comparar `v1…v7` antes y después del guardado | N/A | Todas las versiones anteriores conservan exactamente sus valores y su `created_at`; `count` pasó de 7 a 8 (RF-005, CE-002). | [ ] | |
| 6 | Verificar la unicidad y secuencia de versión | `psql`: `SELECT version_number, count(*) FROM financial_parameters GROUP BY version_number;` | N/A | `v8` es única y mayor que `v7`; la versión vigente es siempre la de mayor `version_number` (plan D-UC11-03). | [ ] | |
| 7 | Verificar ausencia de efecto retroactivo (opcional) | Consultar una reserva ya calculada (si el entorno tiene una; paso no bloqueante) | `reservation_id` de la reserva de prueba | La reserva conserva su referencia a la versión usada y sus montos no cambian; los nuevos parámetros solo aplican a cálculos posteriores (RF-007, CE-002). | [ ] | |
| 8 | Verificar que la nueva versión alimenta a los consumidores | Repetir el cálculo/estimación de una reserva nueva según el UC correspondiente | `boat_id` de prueba | El cálculo posterior usa `v8` (comisión 18.50 %, seguro 16500.00) y registra la referencia a esa versión (RF-006). | [ ] | Delegable al TC del UC consumidor. |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reenviar exactamente la misma configuración | `PUT` con los cuatro valores de `v8` | mismos valores de `v8` | `200 OK` con `{ "version_number": 8 }`; **no** se crea `v9` (idempotencia, contrato §6). | [ ] | |
| 2 | Campo obligatorio ausente | `PUT` sin `insurance_fee_per_passenger` | body incompleto | `400 VALIDATION_ERROR` con `errors[].field`; sin versión nueva ni cambios (RF-013, CE-004). | [ ] | |
| 3 | Un valor fuera de rango | `PUT` con `platform_commission_percentage = "150.00"` | comisión 150 % | `400 VALIDATION_ERROR`; `v8` permanece íntegra y sin actualizaciones parciales (RF-011, RF-013). | [ ] | Detalle en [TC-UC11-HU4-E3](TC-UC11-HU4-E3-fallo-guardado.md). |
| 4 | `Content-Type` incorrecto | `PUT` con `Content-Type: text/plain` | body JSON | `415 UNSUPPORTED_MEDIA_TYPE`, `code = UNSUPPORTED_MEDIA_TYPE` (E1). | [ ] | |
| 5 | Método no soportado en la ruta | `DELETE /api/v1/financial-parameters` | N/A | `405 METHOD_NOT_ALLOWED`; ninguna versión se elimina (E1; RF-005). | [ ] | |
| 6 | Campos de solo lectura en el cuerpo | `PUT` incluyendo `guarantee_deposit_rule` o `high_season_windows` | body con campo extra | `400 VALIDATION_ERROR` por campo no aceptado; nada se persiste (E2; RF-012). | [ ] | |
| 7 | Dos guardados concurrentes distintos | Enviar dos `PUT` simultáneos con contenidos diferentes | dos cuerpos válidos | Se crean dos versiones consecutivas con `UNIQUE(version_number)`; una es la vigente y no se corrompe ninguna anterior (RNF-003, plan D-UC11-03). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Límites aceptados `0.0000` y `100.0000` en los tres porcentajes; `100.0001` y `-0.0001` se rechazan con `400 VALIDATION_ERROR` (RF-013).
* **EC-002** : Seguro `0.0000` es válido (≥ 0); un importe negativo se rechaza (RF-013).
* **EC-003** : Los valores con más de 4 decimales se rechazan por exceso de precisión; el `GET` siempre devuelve 4 decimales (D-UC11-12, RNF-002).
* **EC-004** : `PUT → GET → PUT` con los valores leídos es idempotente y no crea versión nueva; redondear a 2 decimales antes de reenviar sí cambia el valor y crea versión (contrato §6).
* **EC-005** : El guardado no requiere `Authorization` ni `X-Correlation-Id`; la ausencia de ambos no altera el resultado (RF-008, README §3.2).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
