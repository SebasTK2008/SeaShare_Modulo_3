### Test Case Specification: Configurar parámetros financieros globales · HU2 · E1 Configuración o ajuste del porcentaje de incremento por fin de semana
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../011-configurar-parametros-financieros-globales/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el Administrador Financiero puede definir o ajustar el porcentaje de incremento de tarifa dinámica aplicable a fines de semana y puentes festivos, y que el sistema lo persiste como una **nueva versión vigente** completa de `ParámetrosFinancierosGlobales`, conservando el historial y aplicándolo únicamente a los cálculos de tarifa posteriores.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../011-configurar-parametros-financieros-globales/spec.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Contrato REST — obtener parámetros | REST contract | [UC11-obtener-parametros-financieros.md](../../../technical-plan/contracts/rest/UC11-obtener-parametros-financieros.md) |
| Contrato REST — estimación individual (consumidor) | REST contract | [UC01-estimacion-individual.md](../../../technical-plan/contracts/rest/UC01-estimacion-individual.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin frontend: la pantalla se representa por sus dos endpoints REST; el efecto se observa a través de "Brindar tarifa base" consumido por la estimación individual.

##### Dependencies & Assumptions
* **Seed de `financial_parameters`** (automático con el perfil `qa`, ver `matriz-qa.md` → *Seed inicial de QA*): con la BD recién recreada deja `v7` vigente; si otro TC de UC11 ya creó versiones, recrear la BD o ajustar `version_number` en los pasos.
* **TC-UC11-HU1-E1 debe haber pasado** (versionado append-only operativo).
* Flota disponible para resolver la tarifa base de la embarcación de prueba.

##### Preconditions & Default Setup
* **Initial State** : `GET /api/v1/financial-parameters` devuelve `version_number = 7` con `weekend_increase_percentage = "10.0000"` (valor de ejemplo; ajustar a la versión vigente del entorno). Embarcación `B-001` con tarifa base conocida en Flota.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, PostgreSQL accesible para `psql`.
* **Test Accounts** : N/A (el Administrador Financiero es el usuario esperado, sin validación de identidad).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Ajuste del incremento de fin de semana con nueva versión vigente
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Leer la configuración vigente | `GET /api/v1/financial-parameters` | N/A | `200 OK` con `weekend_increase_percentage = "10.0000"` y `version_number = 7` (RF-009). | [x] | Punto de partida. |
| 2 | Guardar el nuevo porcentaje | `PUT /api/v1/financial-parameters` con `Content-Type: application/json` | `{"platform_commission_percentage":"15.0000","insurance_fee_per_passenger":"15000.0000","weekend_increase_percentage":"12.0000","high_season_increase_percentage":"25.0000"}` | `200 OK` con `{"version_number":8}` (RF-003, RF-005). | [x] | Se envían los cuatro valores. |
| 3 | Confirmar la lectura posterior | `GET /api/v1/financial-parameters` | N/A | `version_number = 8` y `weekend_increase_percentage = "12.0000"`; el resto sin cambios (RF-009). | [x] | |
| 4 | Verificar el historial inmutable | `SELECT version_number, weekend_increase_pct FROM financial_parameters ORDER BY version_number` | N/A | La versión 7 conserva `10.0000` y la 8 se agrega; ninguna fila previa se actualiza (RF-005, CE-002). | [x] | |
| 5 | Verificar la aplicación en un fin de semana | `POST /api/v1/estimates/individual` | `{"boat_id":"<B-001>","start_date":"2026-11-07","end_date":"2026-11-07","passengers":2}` | La tarifa diaria devuelta refleja el incremento del 12 % sobre la tarifa base y usa la versión 8 (RF-006). | [x] | Fecha de ejemplo en sábado; ajustar al calendario del entorno. |
| 6 | Verificar que un día hábil no lo aplica | `POST /api/v1/estimates/individual` con la misma embarcación en día hábil fuera de temporada alta | `{"boat_id":"<B-001>","start_date":"2026-11-05","end_date":"2026-11-05","passengers":2}` | La tarifa devuelta no incluye el incremento de fin de semana (comportamiento de UC02 con la versión vigente). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Porcentaje fuera de rango | `PUT` con incremento `101.0000` | `"weekend_increase_percentage":"101.0000"` | `400 VALIDATION_ERROR` con `errors[]` sobre `weekend_increase_percentage`; la versión vigente no cambia (RF-013). | [x] | |
| 2 | Valor no numérico | `PUT` con incremento `"diez"` | texto no decimal | `400 VALIDATION_ERROR` (`"must be a decimal string"`); sin persistencia (RF-013). | [x] | |
| 3 | Campo obligatorio ausente | `PUT` sin `weekend_increase_percentage` | body incompleto | `400 VALIDATION_ERROR`; no se crea versión parcial (RF-011). | [x] | |
| 4 | Otro parámetro inválido en el mismo cuerpo | `PUT` con incremento válido y seguro negativo | `"insurance_fee_per_passenger":"-5"` | `400 VALIDATION_ERROR`; **ninguno** de los cuatro valores se persiste (atomicidad, RF-005). | [x] | |
| 5 | `Content-Type` no soportado | `PUT` con `Content-Type: application/xml` | N/A | `415 UNSUPPORTED_MEDIA_TYPE` (E1). | [x] | |
| 6 | Fallo de persistencia | Detener PostgreSQL y enviar un `PUT` válido | N/A | `500 PARAMETERS_SAVE_FAILED` (`retryable: true`); configuración anterior íntegra (HU4 esc. 3). | [x] | |
| 7 | Reenvío idéntico | Repetir el `PUT` del paso 2 de la Section 1 | mismos cuatro valores | `200 OK` con `version_number = 8`; no se crea versión nueva (idempotencia). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Los límites `0.0000` y `100.0000` son válidos y se persisten tal cual (RF-013).
* **EC-002** : Los puentes festivos usan el mismo porcentaje que los fines de semana; el calendario de fines de semana lo resuelve UC02, no UC11 (RF-003).
* **EC-003** : Una reserva o estimación ya calculada antes del cambio conserva sus importes y su referencia de versión (RF-007).
* **EC-004** : El porcentaje se expresa como `tarifa × (1 + % / 100)`; el `GET` lo devuelve con 4 decimales (README §3.1).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
