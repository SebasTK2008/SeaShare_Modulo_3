### Test Case Specification: Consultar informe financiero · HU3 · E1 Exportación del informe de la plataforma (.csv)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/013-consultar-informe-financiero/spec.md](../../013-consultar-informe-financiero/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC13-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P2 - High

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, con alcance declarado de Administrador Financiero y un período válido, el sistema genera un archivo `.csv` con **los mismos datos agregados de la consulta**, la periodicidad, el período, el alcance y la fecha y hora de generación, sin modificar ningún registro financiero.

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
* Sin interfaz propia: se descarga el CSV por HTTP y se compara con la respuesta JSON del mismo período.
* Montos y cantidades **ilustrativos**; ajustar a la versión vigente del entorno y al dataset de QA.
* Fecha de referencia 2026-11-02; período exportado `2026-10`.

##### Dependencies & Assumptions
* **TC-UC13-HU1-E1 debe haber pasado** (consulta del informe mensual de plataforma y dataset 2026-10 verificados).
* El servicio responde en `http://localhost:8080` y la base `v_financial_records` tiene los registros confirmados del período.

##### Preconditions & Default Setup
* **Initial State** : dataset mensual 2026-10 con cobros `1150000.00`, reembolsos `100000.00`, dispersiones `900000.00`, comisiones `135000.00`, neto `150000.00`; período anterior `2026-09` con neto `125000.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`); cliente con `curl`; PostgreSQL con `docker exec -it seashare-postgres psql -U seashare -d seashare`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Exportación del informe de la plataforma
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Obtener la línea base en JSON | `GET /api/v1/financial-report?scope=PLATFORM&periodicity=MONTHLY&period=2026-10` | `2026-10`, `PLATFORM` | `200 OK` con el informe agregado del período; se conserva para comparar (RF-013). | [x] | |
| 2 | Descargar el CSV | `curl -D headers.txt -o informe_plataforma.csv "http://localhost:8080/api/v1/financial-report/export?scope=PLATFORM&periodicity=MONTHLY&period=2026-10"` | mismos parámetros | `200 OK`; el cuerpo es el archivo CSV y no un JSON (RF-012). | [x] | |
| 3 | Verificar cabeceras HTTP | Leer `headers.txt` | N/A | `Content-Type: text/csv` (puede incluir `charset`); `Content-Disposition: attachment; filename="informe_financiero_2026-10.csv"` (contract §4). | [x] | |
| 4 | Verificar la fila de encabezado | Abrir el CSV | N/A | Exactamente 16 columnas en este orden: `Fecha de Generacion`, `Alcance`, `Periodicidad`, `Periodo`, `Neto Generado`, `Total Cobros`, `Total Reembolsos`, `Total Dispersiones`, `Total Comisiones`, `Count Charge`, `Count Refund`, `Count Settlement`, `Count Commission`, `Periodo Anterior`, `Variacion Absoluta`, `Variacion Porcentual` (contract §4). | [x] | |
| 5 | Verificar la estructura del archivo | Contar filas y columnas | N/A | Una fila de encabezado y una fila de datos; texto plano delimitado por comas, legible en UTF-8 (RF-012). | [x] | |
| 6 | Comparar métricas con la consulta | Contrastar cada columna con el JSON del paso 1 | N/A | `Neto Generado = 150000.00`, `Total Cobros = 1150000.00`, `Total Reembolsos = 100000.00`, `Total Dispersiones = 900000.00`, `Total Comisiones = 135000.00`; conteos `10/2/8/8` (RF-013, CE-006). | [x] | |
| 7 | Verificar la comparación exportada | Leer las tres últimas columnas | `2026-09`, neto `125000.00` | `Periodo Anterior = 2026-09`, `Variacion Absoluta = 25000.00`, `Variacion Porcentual = 20.00`; igual que el JSON (RF-010, RF-013, CE-006). | [x] | |
| 8 | Verificar alcance, período y fecha de generación | Leer las columnas 1 a 4 | N/A | `Alcance = PLATFORM`, `Periodicidad = MONTHLY`, `Periodo = 2026-10`; `Fecha de Generacion` en ISO-8601 UTC y correspondiente al instante de la descarga (HU3 esc. 1, RF-013). | [x] | |
| 9 | Verificar que la exportación no modifica registros | `SELECT record_type, COUNT(*), SUM(amount) FROM v_financial_records GROUP BY record_type;` antes y después | N/A | Mismo conteo y mismos montos; la exportación es de solo lectura (RF-013, CE-006). | [x] | |
| 10 | Repetir la exportación y otra periodicidad | Repetir el paso 2; luego `periodicity=QUARTERLY&period=2026-Q3` | `2026-10`, `2026-Q3` | La repetición devuelve las mismas métricas (la fecha de generación puede cambiar); el trimestral refleja `2026-Q3` y su comparación `2026-Q2` (RF-013, CE-006). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Exportar sin `period` | `GET /api/v1/financial-report/export?scope=PLATFORM&periodicity=MONTHLY` | sin `period` | `400 VALIDATION_ERROR`; a diferencia de la consulta, la exportación exige período explícito (contract §2 y §5). | [x] | |
| 2 | Omitir `scope` o `periodicity` | Dos llamadas, cada una sin uno de los parámetros | N/A | `400 VALIDATION_ERROR`; no se genera archivo (E2). | [x] | |
| 3 | Periodicidad no soportada | `...&periodicity=WEEKLY&period=2026-10` | `WEEKLY` | `400 INVALID_PERIOD`; sin CSV ni valores parciales (contract §5). | [x] | |
| 4 | Período con formato inválido | `...&periodicity=MONTHLY&period=2026-10-H1` | `2026-10-H1` | `400 INVALID_PERIOD`; no se interpreta como rango libre (RF-004, CE-003). | [x] | |
| 5 | `Accept` no soportado | Repetir la descarga con `Accept: application/xml` | `application/xml` | `406 NOT_ACCEPTABLE` (catálogo §3.4, E1); no se entrega un CSV con un tipo no solicitado. | [x] | La tabla §5 del contrato de exportación no lista el 406. |
| 6 | Método HTTP no soportado | `POST /api/v1/financial-report/export?scope=PLATFORM&periodicity=MONTHLY&period=2026-10` | N/A | `405 METHOD_NOT_ALLOWED` (E1). | [x] | |
| 7 | `owner_id` inválido en alcance PLATFORM | `...&scope=PLATFORM&owner_id=abc&periodicity=MONTHLY&period=2026-10` | `abc` | `400 VALIDATION_ERROR`; el parámetro, si se envía, debe ser UUID (E2). | [x] | |
| 8 | Período sin registros | `...&periodicity=MONTHLY&period=2020-01` | `2020-01` | `200 OK` con CSV de una fila de datos en ceros; `Variacion Porcentual` vacía si el anterior es cero (casos extremos del SPEC). | [x] | |
| 9 | Base de datos no disponible | Detener PostgreSQL y repetir la descarga | N/A | `500 INTERNAL_ERROR`, `retryable: true`, `detail` genérico sin trazas ni SQL (E8). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El CSV se produce con el mismo agregador que la consulta: no se recalcula por otra ruta (RNF-004, CE-006).
* **EC-002** : El archivo contiene solo agregados; no incluye filas de registros individuales ni el detalle paginado de UC12 (RF-011, RF-013).
* **EC-003** : `Variacion Porcentual` queda vacía cuando el período anterior es cero; la absoluta sí se informa (RF-010, contract §4).
* **EC-004** : Los montos usan punto decimal y 2 decimales, sin separador de miles (`1150000.00`), y comisiones no se restan del neto (RNF-002, RF-009).
* **EC-005** : El nombre del archivo deriva del período (`informe_financiero_2026-10.csv`); un período con caracteres no alfanuméricos se sanea en el nombre, no en el contenido (controller).
* **EC-006** : Exportaciones concurrentes del mismo período devuelven el mismo contenido métrico y no alteran registros (RF-013, CE-006).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
