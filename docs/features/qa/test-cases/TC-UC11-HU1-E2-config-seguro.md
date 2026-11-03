### Test Case Specification: Configurar parámetros financieros globales · HU1 · E2 Configuración o ajuste de la tarifa del seguro náutico por pasajero
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../011-configurar-parametros-financieros-globales/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el Administrador Financiero puede definir o ajustar la tarifa del seguro náutico por pasajero y que el sistema la persiste en una **nueva versión vigente** completa de `ParámetrosFinancierosGlobales`, conservando las versiones anteriores como historial inmutable y haciendo que solo los cálculos de valor de reserva posteriores la utilicen.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../011-configurar-parametros-financieros-globales/spec.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Contrato REST — obtener parámetros | REST contract | [UC11-obtener-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-obtener-parametros-financieros.md) |
| Contrato REST — valor calculado (consumidor) | REST contract | [UC04-valor-calculado-reserva.md](../../../technical-plan/contracts/rest/UC04-valor-calculado-reserva.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin frontend: la pantalla se representa por sus dos endpoints REST; no hay validación de rol (seguridad fuera de alcance).

##### Dependencies & Assumptions
* **TC-UC11-HU1-E1 debe haber pasado** (versionado append-only operativo).
* Existe información de reserva registrada con un número conocido de pasajeros, para comprobar el efecto de la tarifa.

##### Preconditions & Default Setup
* **Initial State** : `GET /api/v1/financial-parameters` devuelve `version_number = 7` con `insurance_fee_per_passenger = "15000.0000"` (valor de ejemplo; ajustar a la versión vigente del entorno). Reserva `R-402` = `4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f8a` informada con 2 pasajeros.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, PostgreSQL accesible para `psql`.
* **Test Accounts** : N/A (el Administrador Financiero es el usuario esperado, sin validación de identidad).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Ajuste de la tarifa de seguro con nueva versión vigente
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Leer la configuración vigente | `GET /api/v1/financial-parameters` | N/A | `200 OK` con la tarifa actual `"15000.0000"` y `version_number = 7` (RF-009). | [x] | Punto de partida. |
| 2 | Guardar la nueva tarifa | `PUT /api/v1/financial-parameters` con `Content-Type: application/json` | `{"platform_commission_percentage":"15.0000","insurance_fee_per_passenger":"18000.0000","weekend_increase_percentage":"10.0000","high_season_increase_percentage":"25.0000"}` | `200 OK` con `{"version_number":8}` (RF-002, RF-005). | [x] | Se envían los cuatro valores: el `PUT` es reemplazo total. |
| 3 | Confirmar la lectura posterior | `GET /api/v1/financial-parameters` | N/A | `version_number = 8` y `insurance_fee_per_passenger = "18000.0000"`; los demás parámetros sin cambios (RF-009). | [x] | |
| 4 | Verificar el historial inmutable | `SELECT version_number, insurance_fee_per_passenger FROM financial_parameters ORDER BY version_number` | N/A | La versión 7 conserva `15000.0000` y la 8 se agrega sin modificar filas previas (RF-005, CE-002). | [x] | |
| 5 | Verificar el efecto en el valor de reserva | `POST /api/v1/reservations/{reservation_id}/calculated-value` | `reservation_id = 4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f8a` (2 pasajeros) | El desglose devuelto incluye `insurance_amount = "36000.00"` (2 × `18000.00`), calculado con la versión 8 (RF-006). | [x] | Verificar que la reserva registra la referencia a la versión empleada. |
| 6 | Verificar la ausencia de efecto retroactivo | Repetir la consulta del valor de una reserva calculada antes del cambio | reserva calculada con la versión 7 | Conserva su `insurance_amount` y su `frozen_parameters_id = 7`; no se recalcula (RF-007). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Tarifa negativa | `PUT` con seguro `-1` | `"insurance_fee_per_passenger":"-1"` | `400 VALIDATION_ERROR` con `errors[]` sobre `insurance_fee_per_passenger`; ninguna versión se modifica (RF-013). | [x] | |
| 2 | Tarifa con formato inválido | `PUT` con `"18.000,00"` o `"dieciocho mil"` | texto no decimal | `400 VALIDATION_ERROR` (`"must be a decimal string"`); sin persistencia (RF-013). | [x] | |
| 3 | Campo obligatorio ausente | `PUT` sin `insurance_fee_per_passenger` | body incompleto | `400 VALIDATION_ERROR`; no se aplican actualizaciones parciales (RF-011). | [x] | |
| 4 | Otro parámetro inválido en el mismo cuerpo | `PUT` con tarifa válida y comisión `120` | `"platform_commission_percentage":"120"` | `400 VALIDATION_ERROR`; la tarifa nueva **tampoco** se persiste (atomicidad, RF-005). | [x] | |
| 5 | `Content-Type` no soportado | `PUT` con `Content-Type: text/plain` | N/A | `415 UNSUPPORTED_MEDIA_TYPE` (E1). | [x] | |
| 6 | Fallo de persistencia | Detener PostgreSQL y enviar un `PUT` válido | N/A | `500 PARAMETERS_SAVE_FAILED` (`retryable: true`); la configuración anterior queda íntegra (HU4 esc. 3). | [x] | |
| 7 | Reenvío idéntico | Repetir el `PUT` del paso 2 de la Section 1 | mismos cuatro valores | `200 OK` con `version_number = 8`: no se crea versión nueva (idempotencia). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Una tarifa `0.0000` es válida (monto ≥ 0) y se persiste como versión vigente (RF-013).
* **EC-002** : La tarifa es por pasajero; el monto del seguro de una reserva depende del número de pasajeros informado, no de un valor fijo global.
* **EC-003** : Precisiones mayores que 4 decimales se rechazan; la escala interna es `NUMERIC(18,4)` (RNF-002).
* **EC-004** : El depósito de garantía y las ventanas de temporada alta no se configuran aquí y permanecen como datos de solo lectura (RF-012).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
