### Test Case Specification: Estimaciones para reserva · HU3 · E1 Flota devuelve la tarifa base en tiempo óptimo (<500 ms)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/001-solicitar-estimacion-para-reserva/spec.md](../../../docs/features/001-solicitar-estimacion-para-reserva/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC01-HU3-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P2 - High

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el Sistema de Gestión de Flota devuelve la tarifa base de un lote de 50 embarcaciones en menos de 500 ms, y que el sistema la consume en una **única** consulta, estimando las 50 embarcaciones dentro del presupuesto de su cliente de Flota, sin crear cuellos de botella.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC01 (HU3) | Specification Document | [spec.md](../../../docs/features/001-solicitar-estimacion-para-reserva/spec.md) |
| Plan UC01 (carga ligera HU3) | Plan Document | [plan.md](../../../docs/features/001-solicitar-estimacion-para-reserva/plan.md) |
| Contrato Flota — tarifas base | External contract | [flota-consulta-tarifas-base.md](../../../docs/technical-plan/contracts/external/flota-consulta-tarifas-base.md) |
| Contrato estimación en lote | REST contract | [UC01-estimacion-lote.md](../../../docs/technical-plan/contracts/rest/UC01-estimacion-lote.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* El SLA de **< 500 ms es un compromiso de Flota** y solo se puede medir contra la Flota real del entorno de `release/0.1.0`. El stub local (WireMock) **no** demuestra el SLA: en local solo se verifica la consulta única y el presupuesto del cliente.
* Los tiempos incluyen la red entre el cliente de medición y Flota; registrar el método y el equipo usado.

##### Dependencies & Assumptions
* **TC-UC01-HU1-E1 debe haber pasado** (lote, invariante de cobertura y consulta única a Flota).
* 50 embarcaciones con tarifa base configurada en Flota; parámetros financieros vigentes cargados.

##### Preconditions & Default Setup
* **Initial State** : Flota conoce las 50 embarcaciones y su `base_rate`; `financial_parameters` vigente con seguro y porcentajes de la fecha evaluada.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, stub en `http://localhost:8081`; para el SLA, la Flota del entorno de release.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Tarifa base en tiempo óptimo y consumo sin cuello de botella
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Preparar el lote de medición | Cargar en Flota (o en el mapping del stub) 50 embarcaciones con tarifa | 50 UUID + `base_rate` | Flota devuelve tarifa para las 50 (RF-005). | [ ] | |
| 2 | Medir la respuesta de Flota | `POST /api/v1/fleet/base-rates` cronometrando la petición (`curl.exe -w "%{time_total}"`) | `{"boat_ids":[50 UUID]}` | Tiempo total **< 0.500 s** (HU3 esc. 1, contrato Flota §3 regla 2). | [ ] | Usar la Flota real; en local no es evidencia del SLA. |
| 3 | Repetir con 50 solicitudes concurrentes | Lanzar 50 peticiones simultáneas (script o herramienta de carga) | 50 peticiones de 1 embarcación (o 50 lotes) | p95 < 500 ms y 0 errores; sin degradación por concurrencia (Prueba Independiente HU3). | [ ] | Registrar nº de peticiones y errores. |
| 4 | Verificar el contenido completo de la respuesta | Revisar `rates` | N/A | 50 objetos con `boat_id` y `base_rate` como string decimal, sin tipo ni categoría (RNF-001, RF-007 de UC02). | [ ] | |
| 5 | Verificar el consumo del sistema en una sola consulta | `POST http://localhost:8080/api/v1/estimates/batch` con los 50 IDs y revisar el journal del stub | 50 UUID | Una sola llamada a Flota, 50 estimaciones y respuesta en menos de 2 s (presupuesto de carga ligera del plan UC01 T022). | [ ] | El cliente de Flota corta a 1 s. |
| 6 | Verificar que no hay caché ni degradación entre llamadas | Repetir el paso 5 y comparar el journal | N/A | Cada solicitud realiza su consulta (sin caché, D-19) y el tiempo se mantiene estable. | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Flota más lenta que el timeout del cliente | Simular demora de 1500 ms en el stub | `fixedDelay` 1500 ms | El sistema corta a 1 s y responde `503 FLEET_UNAVAILABLE`, sin estimaciones parciales (RNF-003, CE-004). | [ ] | |
| 2 | Flota responde `5xx` | Mapping con estado `503` | N/A | `503 FLEET_UNAVAILABLE` (fila E5 de la tabla de decisión). | [ ] | |
| 3 | Flota responde un cuerpo inválido | Mapping que devuelve texto no JSON | N/A | `503 FLEET_UNAVAILABLE`; una respuesta ilegible se trata como fallo de dependencia (E5). | [ ] | |
| 4 | Respuesta incompleta de Flota | Mapping que devuelve 49 de las 50 tarifas | 50 IDs, 49 `rates` | 49 estimaciones y 1 entrada en `unavailable[]` con `NO_BASE_RATE`; no se inventa tarifa (casos extremos). | [ ] | |
| 5 | Medición con 51 embarcaciones | Enviar 51 IDs al lote | 51 UUID | La petición se rechaza con `400 BATCH_SIZE_EXCEEDED`; la medición del SLA aplica al máximo de 50 (RF-006). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El SLA se mide para el lote máximo de 50; por encima de ese tamaño no hay compromiso y la petición se rechaza.
* **EC-002** : La medición en local se limita a demostrar la consulta única y el presupuesto holgado del cliente (1 s de timeout, < 2 s de extremo a extremo).
* **EC-003** : La latencia se registra con el mismo cliente y red en cada repetición; comparar percentiles, no solo el promedio.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
