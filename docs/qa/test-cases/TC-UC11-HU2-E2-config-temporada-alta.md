### Test Case Specification: Configurar parámetros financieros globales · HU2 · E2 Configuración o ajuste del porcentaje de incremento por temporada alta
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/011-configurar-parametros-financieros-globales/spec.md](../../../docs/features/011-configurar-parametros-financieros-globales/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC11-HU2-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el Administrador Financiero puede definir o ajustar el porcentaje de incremento aplicable durante la temporada alta, que el sistema lo persiste en una **nueva versión vigente** completa y que "Brindar tarifa base" lo aplica a las fechas que caen en las ventanas de temporada alta determinadas por el calendario, sin que UC11 configure dichas fechas.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC11 | Specification Document | [spec.md](../../../docs/features/011-configurar-parametros-financieros-globales/spec.md) |
| Feature Spec UC02 (calendario) | Specification Document | [spec.md](../../../docs/features/002-brindar-tarifa-base/spec.md) |
| Contrato REST — guardar parámetros | REST contract | [UC11-guardar-parametros-financieros.md](../../../docs/technical-plan/contracts/rest/UC11-guardar-parametros-financieros.md) |
| Contrato REST — obtener parámetros | REST contract | [UC11-obtener-parametros-financieros.md](../../../docs/technical-plan/contracts/rest/UC11-obtener-parametros-financieros.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin frontend: la pantalla se representa por sus dos endpoints REST; el efecto se observa a través de la estimación individual, que consume "Brindar tarifa base".

##### Dependencies & Assumptions
* **TC-UC11-HU2-E1 debe haber pasado** (versionado y efecto dinámico operativos).
* Flota disponible para resolver la tarifa base de la embarcación de prueba.

##### Preconditions & Default Setup
* **Initial State** : `GET /api/v1/financial-parameters` devuelve `version_number = 7` con `high_season_increase_percentage = "25.0000"` y las ventanas calculadas `["11-15→01-15","06-01→07-30","semana-santa","10-05→10-12"]` (valores de ejemplo; ajustar a la versión vigente del entorno). Embarcación `B-001` con tarifa base conocida.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, PostgreSQL accesible para `psql`.
* **Test Accounts** : N/A (el Administrador Financiero es el usuario esperado, sin validación de identidad).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Ajuste del incremento de temporada alta con nueva versión vigente
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Leer la configuración vigente | `GET /api/v1/financial-parameters` | N/A | `200 OK` con `high_season_increase_percentage = "25.0000"`, `version_number = 7` y `high_season_windows` de solo lectura (RF-009, RF-012). | [ ] | Punto de partida. |
| 2 | Guardar el nuevo porcentaje | `PUT /api/v1/financial-parameters` con `Content-Type: application/json` | `{"platform_commission_percentage":"15.0000","insurance_fee_per_passenger":"15000.0000","weekend_increase_percentage":"10.0000","high_season_increase_percentage":"30.0000"}` | `200 OK` con `{"version_number":8}` (RF-004, RF-005). | [ ] | Se envían los cuatro valores. |
| 3 | Confirmar la lectura posterior | `GET /api/v1/financial-parameters` | N/A | `version_number = 8` y `high_season_increase_percentage = "30.0000"`; las ventanas de temporada alta no cambian (RF-009, RF-012). | [ ] | |
| 4 | Verificar el historial inmutable | `SELECT version_number, high_season_increase_pct FROM financial_parameters ORDER BY version_number` | N/A | La versión 7 conserva `25.0000` y la 8 se agrega sin modificar filas previas (RF-005, CE-002). | [ ] | |
| 5 | Verificar la aplicación dentro de la ventana | `POST /api/v1/estimates/individual` con una fecha de temporada alta | `{"boat_id":"<B-001>","start_date":"2026-12-02","end_date":"2026-12-02","passengers":2}` | La tarifa diaria devuelta refleja el incremento del 30 % sobre la tarifa base (RF-006). | [ ] | Fecha de ejemplo dentro de `11-15→01-15`; ajustar al calendario del entorno. |
| 6 | Verificar que fuera de la ventana no lo aplica | `POST /api/v1/estimates/individual` con la misma embarcación en una fecha fuera de toda ventana | `{"boat_id":"<B-001>","start_date":"2026-11-05","end_date":"2026-11-05","passengers":2}` | La tarifa devuelta no incluye el incremento de temporada alta (regla de calendario de UC02). | [ ] | |
| 7 | Verificar que no se configuran fechas | `GET /api/v1/financial-parameters` e inspección del `PUT` | N/A | Las ventanas se devuelven calculadas y el `PUT` no admite fechas de temporada alta (RF-012). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Porcentaje fuera de rango | `PUT` con incremento `150.0000` | `"high_season_increase_percentage":"150.0000"` | `400 VALIDATION_ERROR` con `errors[]` sobre `high_season_increase_percentage`; sin cambios en la versión vigente (RF-013). | [ ] | |
| 2 | Valor no numérico | `PUT` con incremento `"treinta"` | texto no decimal | `400 VALIDATION_ERROR` (`"must be a decimal string"`); sin persistencia (RF-013). | [ ] | |
| 3 | Campo obligatorio ausente | `PUT` sin `high_season_increase_percentage` | body incompleto | `400 VALIDATION_ERROR`; no se crea versión parcial (RF-011). | [ ] | |
| 4 | Intento de enviar las ventanas de temporada alta | `PUT` con un campo `high_season_windows` | `"high_season_windows":["12-01→12-31"]` | `400 VALIDATION_ERROR`: el campo no pertenece al contrato y las fechas no son configurables (RF-012, catálogo §3.4). | [ ] | |
| 5 | Otro parámetro inválido en el mismo cuerpo | `PUT` con incremento válido y comisión `-1` | `"platform_commission_percentage":"-1"` | `400 VALIDATION_ERROR`; **ninguno** de los cuatro valores se persiste (atomicidad, RF-005). | [ ] | |
| 6 | Fallo de persistencia | Detener PostgreSQL y enviar un `PUT` válido | N/A | `500 PARAMETERS_SAVE_FAILED` (`retryable: true`); la configuración anterior se conserva íntegra (HU4 esc. 3). | [ ] | |
| 7 | Reenvío idéntico | Repetir el `PUT` del paso 2 de la Section 1 | mismos cuatro valores | `200 OK` con `version_number = 8`; no se crea versión nueva (idempotencia). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Los límites `0.0000` y `100.0000` son válidos y se persisten tal cual (RF-013).
* **EC-002** : La vigencia de la temporada alta la determina el calendario de UC02 (SPEC 2, RF-006); UC11 solo configura el porcentaje.
* **EC-003** : Si una fecha cae en fin de semana y en temporada alta, el resultado se contrasta con la regla vigente de UC02; este caso no fija una combinación nueva.
* **EC-004** : Una estimación ya calculada antes del cambio conserva sus importes y su referencia de versión de parámetros (RF-007).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
