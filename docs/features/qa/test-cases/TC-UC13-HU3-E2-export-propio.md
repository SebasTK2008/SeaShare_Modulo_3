### Test Case Specification: Consultar informe financiero · HU3 · E2 Exportación del informe propio (.csv)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/013-consultar-informe-financiero/spec.md](../../013-consultar-informe-financiero/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC13-HU3-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P2 - High

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con alcance declarado de Propietario (`owner_id`) y un período válido, el sistema genera un archivo `.csv` con los mismos datos agregados de la consulta propia, la periodicidad, el período, el alcance `OWNER` y la fecha y hora de generación, restringido a las reservas del propietario declarado y sin modificar registros financieros.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC13 | Specification Document | [spec.md](../../013-consultar-informe-financiero/spec.md) |
| Plan UC13 | Plan Document | [plan.md](../../013-consultar-informe-financiero/plan.md) |
| Contrato REST — exportar informe | API contract | [UC13-exportar-informe-financiero.md](../../../technical-plan/contracts/rest/UC13-exportar-informe-financiero.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* Sin interfaz propia: se descarga el CSV por HTTP y se compara con la respuesta JSON del mismo alcance y período.
* Montos y cantidades **ilustrativos**; ajustar a la versión vigente del entorno y al dataset de QA.
* Fecha de referencia 2026-11-02; período exportado `2026-10` para el Propietario A.

##### Dependencies & Assumptions
* **TC-UC13-HU2-E1 debe haber pasado** (informe propio del último período cerrado y aislamiento entre propietarios verificados).
* **TC-UC13-HU3-E1 debe haber pasado** (formato y columnas del CSV de plataforma verificados).
* Existe el dataset del Propietario A y del Propietario B en 2026-09 y 2026-10.

##### Preconditions & Default Setup
* **Initial State** : Propietario A (`11111111-1111-4111-8111-111111111111`) en 2026-10 con 2 cobros `300000.00`, 1 reembolso `20000.00`, 2 dispersiones `250000.00`, 2 comisiones `37500.00`; ganancia `250000.00`; período anterior `2026-09` con dispersiones `200000.00`. Propietario B en 2026-10 con dispersiones `400000.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`); cliente con `curl`; PostgreSQL con `docker exec -it seashare-postgres psql -U seashare -d seashare`.
* **Test Accounts** : N/A (identidad declarada como dato plano).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Exportación del informe propio
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Obtener la línea base propia en JSON | `GET /api/v1/financial-report?scope=OWNER&owner_id=11111111-1111-4111-8111-111111111111&periodicity=MONTHLY&period=2026-10` | A, `2026-10` | `200 OK` con el informe agregado de A; se conserva para comparar (RF-013, CE-006). | [x] | |
| 2 | Descargar el CSV propio | `curl -D headers_propio.txt -o informe_propio.csv "http://localhost:8080/api/v1/financial-report/export?scope=OWNER&owner_id=11111111-1111-4111-8111-111111111111&periodicity=MONTHLY&period=2026-10"` | mismos parámetros | `200 OK`; el cuerpo es el CSV y no un JSON (RF-012). | [x] | |
| 3 | Verificar cabeceras HTTP | Leer `headers_propio.txt` | N/A | `Content-Type: text/csv`; `Content-Disposition: attachment; filename="informe_financiero_2026-10.csv"` (contract §4). | [x] | |
| 4 | Verificar la fila de encabezado | Abrir el CSV | N/A | Las mismas 16 columnas y en el mismo orden que el contrato de exportación (contract §4). | [x] | |
| 5 | Verificar la estructura del archivo | Contar filas y columnas | N/A | Una fila de encabezado y una fila de datos; texto plano delimitado por comas, legible en UTF-8 (RF-012). | [x] | |
| 6 | Comparar métricas con la consulta propia | Contrastar cada columna con el JSON del paso 1 | N/A | `Neto Generado = 250000.00`, `Total Cobros = 300000.00`, `Total Reembolsos = 20000.00`, `Total Dispersiones = 250000.00`, `Total Comisiones = 37500.00`; conteos `2/1/2/2` (RF-013, CE-006). | [x] | |
| 7 | Verificar la comparación exportada | Leer las tres últimas columnas | A, anterior `2026-09`, dispersiones `200000.00` | `Periodo Anterior = 2026-09`, `Variacion Absoluta = 50000.00`, `Variacion Porcentual = 25.00`; igual que el JSON (RF-010, RF-013, CE-006). | [x] | |
| 8 | Verificar alcance, período y fecha de generación | Leer las columnas 1 a 4 | N/A | `Alcance = OWNER`, `Periodicidad = MONTHLY`, `Periodo = 2026-10`; `Fecha de Generacion` en ISO-8601 UTC y correspondiente al instante de la descarga (HU3 esc. 2, RF-013). | [x] | |
| 9 | Verificar el aislamiento en el archivo | Comparar con el CSV de plataforma y con los totales de B | B: dispersiones `400000.00` | El CSV no contiene los `400000.00` de B ni ningún dato de otro propietario; sus métricas coinciden solo con A (RF-006, CE-001, CE-006). | [x] | |
| 10 | Verificar que la exportación no modifica registros | `SELECT record_type, COUNT(*), SUM(amount) FROM v_financial_records GROUP BY record_type;` antes y después | N/A | Mismo conteo y mismos montos; operación de solo lectura (RF-013, CE-006). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Exportar con alcance OWNER sin `owner_id` | `GET /api/v1/financial-report/export?scope=OWNER&periodicity=MONTHLY&period=2026-10` | sin `owner_id` | `400 VALIDATION_ERROR` con `field = "owner_id"`; no se genera archivo (contract §5, E2). | [x] | |
| 2 | `owner_id` con formato inválido | `...&scope=OWNER&owner_id=abc&periodicity=MONTHLY&period=2026-10` | `abc` | `400 VALIDATION_ERROR`; sin CSV (E2). | [x] | |
| 3 | Exportar sin `period` | `...&scope=OWNER&owner_id=<A>&periodicity=MONTHLY` | sin `period` | `400 VALIDATION_ERROR`; la exportación exige período explícito (contract §2 y §5). | [x] | |
| 4 | Periodicidad o período inválidos | `periodicity=WEEKLY&period=2026-10` y `periodicity=MONTHLY&period=2026-13` | valores inválidos | `400 INVALID_PERIOD` en ambos casos; no se genera archivo (contract §5). | [x] | |
| 5 | `Accept` no soportado | Repetir la descarga con `Accept: application/xml` | `application/xml` | `406 NOT_ACCEPTABLE` (catálogo §3.4, E1); no se entrega el CSV como otro tipo. | [x] | La tabla §5 del contrato de exportación no lista el 406. |
| 6 | Propietario sin registros | `...&scope=OWNER&owner_id=99999999-9999-4999-8999-999999999999&periodicity=MONTHLY&period=2026-10` | UUID sin registros | `200 OK` con CSV de una fila de datos en ceros; no es error (casos extremos del SPEC). | [x] | |
| 7 | Período sin registros del propietario | `...&owner_id=<A>&periodicity=MONTHLY&period=2020-01` | `2020-01`, A | `200 OK` con métricas en cero; `Variacion Porcentual` vacía si el anterior es cero (casos extremos del SPEC). | [x] | |
| 8 | Base de datos no disponible | Detener PostgreSQL y repetir la descarga | N/A | `500 INTERNAL_ERROR`, `retryable: true`, `detail` genérico sin trazas ni SQL (E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El CSV propio no expone datos de otros propietarios, aunque compartan período, embarcación o reserva (RF-006, CE-001).
* **EC-002** : `Neto Generado` del propietario es solo dispersiones; cobros y reembolsos se informan pero no alteran la ganancia (RF-008, CE-002).
* **EC-003** : El archivo se produce con el mismo cálculo y alcance que la consulta JSON; no hay diferencias entre lo mostrado y lo descargado (RNF-004, CE-006).
* **EC-004** : `Variacion Porcentual` queda vacía cuando el período anterior es cero; la absoluta sí se informa (RF-010).
* **EC-005** : Los montos usan punto decimal y 2 decimales, sin separador de miles; las comisiones no se restan del neto (RNF-002, RF-009).
* **EC-006** : El contrato no define una columna con `owner_id`; el alcance se acredita con `Alcance = OWNER` y con la coincidencia exacta frente a la consulta del mismo `owner_id` (RF-013).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
