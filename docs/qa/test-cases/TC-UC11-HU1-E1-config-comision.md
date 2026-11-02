### Test Case Specification: Configurar parámetros financieros globales · HU1 · E1 Configuración o ajuste del porcentaje de comisión de la plataforma
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../../docs/features/011-configurar-parametros-financieros-globales/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el Administrador Financiero puede definir o ajustar el porcentaje de comisión de la plataforma y que el sistema lo persiste como una **nueva versión vigente** completa de `ParámetrosFinancierosGlobales`, conservando las versiones anteriores como historial inmutable y sin modificar los valores ya registrados en reservas previamente calculadas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../../docs/features/011-configurar-parametros-financieros-globales/spec.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../../docs/technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Contrato REST — obtener parámetros | REST contract | [UC11-obtener-parametros-financieros.md](../../../docs/technical-plan/contracts/rest/UC11-obtener-parametros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin frontend: la pantalla se representa por sus dos endpoints REST (`GET` y `PUT`); la validación de rol no existe (seguridad fuera de alcance).

##### Dependencies & Assumptions
* Existe al menos una versión vigente de `financial_parameters` (si no, ver TC-UC11-HU3-E2).
* Al menos una reserva calculada antes del cambio, para comprobar la ausencia de efecto retroactivo.

##### Preconditions & Default Setup
* **Initial State** : `GET /api/v1/financial-parameters` devuelve `version_number = 7`, `platform_commission_percentage = "15.0000"`, `insurance_fee_per_passenger = "15000.0000"`, `weekend_increase_percentage = "10.0000"`, `high_season_increase_percentage = "25.0000"` (valores de ejemplo; ajustar a la versión vigente del entorno). Reserva `R-400` = `2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e` calculada con `frozen_parameters_id` de la versión 7.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, PostgreSQL accesible para `psql`.
* **Test Accounts** : N/A (el Administrador Financiero es el usuario esperado, sin validación de identidad).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Ajuste del porcentaje de comisión con nueva versión vigente
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Leer la configuración vigente | `GET /api/v1/financial-parameters` | N/A | `200 OK` con `version_number = 7`, los cuatro parámetros con 4 decimales y los datos de solo lectura (RF-009). | [ ] | Punto de partida. |
| 2 | Guardar la configuración con el nuevo porcentaje | `PUT /api/v1/financial-parameters` con `Content-Type: application/json` | `{"platform_commission_percentage":"18.0000","insurance_fee_per_passenger":"15000.0000","weekend_increase_percentage":"10.0000","high_season_increase_percentage":"25.0000"}` | `200 OK` con `{"version_number":8}`: se crea una nueva versión vigente (RF-001, RF-005). | [ ] | El `PUT` es reemplazo total: se envían los cuatro valores. |
| 3 | Confirmar la lectura posterior | `GET /api/v1/financial-parameters` | N/A | `version_number = 8` y `platform_commission_percentage = "18.0000"`; el resto de parámetros se conserva (RF-009). | [ ] | |
| 4 | Verificar el historial inmutable | `SELECT version_number, commission_pct, created_at FROM financial_parameters ORDER BY version_number` | N/A | La fila de la versión 7 permanece intacta y la versión 8 se agrega; no hay `UPDATE` sobre filas previas (RF-005, CE-002). | [ ] | |
| 5 | Verificar la precisión almacenada | `SELECT commission_pct FROM financial_parameters WHERE version_number = 8` | N/A | Valor `18.0000` en `NUMERIC(18,4)`, coherente con el `GET` (RNF-002). | [ ] | |
| 6 | Verificar el efecto en cálculos posteriores | `POST /api/v1/reservations/{reservation_id}/calculated-value` para una reserva nueva | `reservation_id = 3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f` | El cálculo usa la versión 8 y deja registrada su referencia (`frozen_parameters_id`); la comisión aplicada corresponde al 18 % (RF-006, RF-007). | [ ] | |
| 7 | Verificar la ausencia de efecto retroactivo | `SELECT` sobre la información de la reserva ya calculada y repetir su valor calculado | `reservation_id = 2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e` | La reserva conserva su `frozen_parameters_id = 7` y sus importes; no se recalculan con el 18 % (RF-007). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Porcentaje fuera de rango | `PUT /api/v1/financial-parameters` con comisión `150.0000` | `"platform_commission_percentage":"150.0000"` | `400 VALIDATION_ERROR`, `retryable: false`, con `errors[]` sobre `platform_commission_percentage`; la versión vigente no cambia (RF-013, E2). | [ ] | |
| 2 | Valor no numérico | `PUT` con comisión `"quince"` | texto no decimal | `400 VALIDATION_ERROR` con mensaje `"must be a decimal string"`; sin persistencia (RF-013). | [ ] | |
| 3 | Campo obligatorio ausente | `PUT` sin `platform_commission_percentage` | body incompleto | `400 VALIDATION_ERROR`; no se crea versión parcial (RF-011, RF-013). | [ ] | |
| 4 | Otro campo inválido en el mismo cuerpo | `PUT` con seguro `-1` y comisión válida | `"insurance_fee_per_passenger":"-1"` | `400 VALIDATION_ERROR`; **ninguno** de los cuatro valores se persiste (atomicidad, RF-005). | [ ] | |
| 5 | `Content-Type` no soportado | `PUT` con `Content-Type: text/plain` | N/A | `415 UNSUPPORTED_MEDIA_TYPE` (E1, catálogo §3.4). | [ ] | |
| 6 | Método no soportado | `POST /api/v1/financial-parameters` | N/A | `405 METHOD_NOT_ALLOWED` (E1). | [ ] | |
| 7 | Fallo de persistencia | Detener PostgreSQL y enviar un `PUT` válido | N/A | `500 PARAMETERS_SAVE_FAILED` (o `INTERNAL_ERROR`), `retryable: true`; la configuración anterior se conserva íntegra (HU4 esc. 3). | [ ] | |
| 8 | Reenvío idéntico | Repetir el `PUT` del paso 2 de la Section 1 | mismos cuatro valores | `200 OK` con `version_number = 8`: no se crea una versión nueva (idempotencia del contrato). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Los límites `0.0000` y `100.0000` son válidos para la comisión; ambos se persisten y se leen igual (RF-013).
* **EC-002** : `PUT → GET → PUT` con los mismos valores de 4 decimales no crea versión nueva; redondear a 2 decimales antes de reenviar sí cambia el valor y crea versión (contrato, nota de idempotencia).
* **EC-003** : Una precisión mayor que 4 decimales se rechaza con error de validación (escala interna de 4 decimales).
* **EC-004** : El depósito de garantía y las ventanas de temporada alta no se envían en el `PUT`; son datos calculados de solo lectura (RF-012).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
