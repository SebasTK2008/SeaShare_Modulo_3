### Test Case Specification: Parámetros financieros · HU4 · E2 Cancelación de cambios no guardados
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../011-configurar-parametros-financieros-globales/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU4-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, tras modificar uno o más campos en la pantalla sin guardar, la acción "Cancelar" descarta esos cambios no persistidos y conserva la configuración vigente **sin modificarla**, sin generar ninguna petición al servidor ni crear una versión nueva.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../011-configurar-parametros-financieros-globales/spec.md) |
| Implementation Plan UC11 | Plan Document | [plan.md](../../011-configurar-parametros-financieros-globales/plan.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Test Case Limitations
* **No existe endpoint de cancelación**: descartar los cambios es una acción del cliente (contrato §2 del índice, “UC11 Cancelar”; RF-010). La prueba se ejecuta con un cliente HTTP/colección que mantiene el formulario en memoria y descarta el cuerpo sin enviarlo.
* Sin frontend propio: la “pantalla” es el estado local del cliente de prueba.

##### Dependencies & Assumptions
* **TC-UC11-HU4-E1 debe haber pasado** (existe una versión vigente y el guardado crea versiones nuevas).
* Acceso a los logs de la aplicación para comprobar que no se emitió ningún `PUT`.

##### Preconditions & Default Setup
* **Initial State** : `financial_parameters` con `v7` vigente (`15.0000` / `15000.0000` / `10.0000` / `25.0000`, valores ilustrativos) y `count` conocido.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (sin validación de identidad; RF-008).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Cancelación de cambios no guardados
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Cargar la configuración vigente | `GET /api/v1/financial-parameters` | N/A | `200 OK` con `version_number = 7` y los cuatro valores vigentes; el cliente los deja en el formulario. | [x] | |
| 2 | Anotar el estado persistido | `psql`: `SELECT version_number, commission_pct, insurance_fee_per_passenger, weekend_increase_pct, high_season_increase_pct FROM financial_parameters ORDER BY version_number;` | N/A | Línea base: `v7` y `count = 7` filas (ajustar al entorno). | [x] | |
| 3 | Editar campos sin guardar | En el cliente, cambiar los cuatro valores del formulario **sin** ejecutar el `PUT` | `"20.00"`, `"18000.00"`, `"15.00"`, `"35.00"` (ilustrativos) | Los cambios existen solo en memoria del cliente; no se emite ninguna petición HTTP. | [x] | Verificar en el log de acceso que no hay `PUT`. |
| 4 | Seleccionar "Cancelar" | Acción del cliente que descarta el formulario y recarga desde el servidor | N/A | El cliente descarta los valores editados; **no** se envía `PUT`, `PATCH` ni `DELETE` (RF-010; contrato §2 del índice). | [x] | La ausencia de petición es el resultado esperado. |
| 5 | Verificar que la configuración no cambió | `GET /api/v1/financial-parameters` | N/A | `200 OK` con `version_number = 7` y los cuatro valores originales; ningún valor editado quedó persistido (RF-010, CE-004). | [x] | |
| 6 | Verificar que no se creó versión | `psql`: contar filas y máximos | N/A | `count` sigue en 7; no existe `v8`; ninguna fila fue modificada (CE-004, RF-005). | [x] | |
| 7 | Verificar el estado al reabrir | Repetir la carga de la pantalla | N/A | Se muestran los valores persistidos (`v7`), no los descartados (HU4 esc. 2). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Cancelar después de un guardado rechazado | `PUT` con `insurance_fee_per_passenger = "-1"` → `400`; luego "Cancelar" | valor negativo | El `400` no persistió nada y la cancelación descarta los valores inválidos del cliente; `GET` devuelve `v7` intacta (RF-011, CE-004). | [x] | |
| 2 | Cancelar dos veces / cerrar el formulario | Ejecutar "Cancelar" y volver a abrir; repetir | N/A | Comportamiento idempotente: sin peticiones de escritura y con la misma versión vigente (RF-010). | [x] | |
| 3 | Otro administrador guarda mientras se edita | Cliente A edita sin guardar; cliente B guarda `v8`; A pulsa "Cancelar" | valores de B válidos | A descarta **solo** sus cambios; `GET` devuelve `v8` de B; la cancelación de A no revierte ni sobrescribe lo ya persistido (RF-010, RF-007). | [x] | |
| 4 | Intento de "cancelar" por API | `DELETE /api/v1/financial-parameters` | N/A | `405 METHOD_NOT_ALLOWED` (E1); ninguna versión se elimina (RF-005, RF-010). | [x] | |
| 5 | Intento de revertir por base de datos | `psql`: `UPDATE financial_parameters SET commission_pct = 15.0000 WHERE version_number = 8;` | N/A | El `UPDATE` es rechazado por la inmutabilidad *append-only*; el historial no se modifica (RF-005, plan D-UC11-13). | [x] | No es un camino válido de cancelación. |
| 6 | Reenviar los valores vigentes como “cancelación” | `PUT` con los mismos cuatro valores de `v7` | valores de `v7` | `200 OK` con `{ "version_number": 7 }`; no se crea versión nueva (idempotencia, contrato §6). | [x] | Equivalente funcional a no guardar. |

##### Edge Cases & Boundary Conditions
* **EC-001** : "Cancelar" no genera petición ni versión; el estado del servidor es exactamente el mismo antes y después (RF-010, CE-004).
* **EC-002** : Un *refresh* del navegador/cliente sin guardar descarta los cambios locales y recarga los valores persistidos.
* **EC-003** : La cancelación no puede deshacer una versión ya creada por otra sesión; no hay *last-write-wins* ni rollback de versiones (RF-005, RF-007).
* **EC-004** : El sistema no expone un endpoint de cancelación: la ausencia se confirma con `405` en métodos de escritura distintos de `PUT` (contrato §2 del índice).
* **EC-005** : Cancelar no altera la referencia de versión de reservas ya calculadas (RF-007).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
