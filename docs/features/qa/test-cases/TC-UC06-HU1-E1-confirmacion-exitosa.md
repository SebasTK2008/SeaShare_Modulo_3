### Test Case Specification: Solicitar confirmación de pago · HU1 · E1 Confirmación de un cobro exitoso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/006-solicitar-confirmacion-de-pago/spec.md](../../006-solicitar-confirmacion-de-pago/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC06-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una `IntenciónDeCobro` marcada como aprobada por "Procesar cobro", el sistema devuelve al Sistema de Reservas y Operaciones el estado aprobado junto con el monto cobrado y la referencia externa, **sin** contactar a la Pasarela y **sin** afirmar que la captura o la liquidación posterior ya se ejecutaron.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC06 | Specification Document | [spec.md](../../006-solicitar-confirmacion-de-pago/spec.md) |
| Contrato REST UC06 | REST contract | [UC06-confirmacion-pago.md](../../../technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Feature Spec UC05 (origen de la intención) | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay autenticación: la seguridad está fuera del alcance del proyecto y el contrato no valida al llamador (README §3.2).

##### Dependencies & Assumptions
* **TC-UC05-HU2-E1 debe haber pasado**: la intención de la reserva quedó en `CAPTURED` con su `RegistroDeCobro`.
* No existen reembolsos confirmados por UC09 para la reserva (por eso `released_amount` es `null`).

##### Preconditions & Default Setup
* **Initial State** : reserva con `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` (UUID de ejemplo del contrato; sustituir por el de la reserva de prueba). `charge_intent` en `CAPTURED`, `amount` total `572.00` (ajustar a la versión vigente del entorno), `external_reference = "1234567890"`, `status_detail` informado por la Pasarela.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`; simulador de la Pasarela con su request journal accesible.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta de un cobro aprobado
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el estado registrado por UC05 | `psql -c "SELECT status, amount, status_detail, external_reference FROM charge_intent WHERE reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10' ORDER BY created_at DESC, id DESC LIMIT 1;"` | N/A | La intención más reciente está en `CAPTURED` con el monto total y la referencia externa (RF-002). | [x] | |
| 2 | Solicitar la confirmación de pago | `curl -s -H "Accept: application/json" http://localhost:8080/api/v1/reservations/b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10/payment-confirmation` | N/A | `200 OK` con `Content-Type: application/json` (RF-001; contrato §4). | [x] | |
| 3 | Verificar el estado devuelto | Leer `status` del cuerpo | N/A | `status = APPROVED`, fiel al estado `CAPTURED` de la intención (RF-003, CE-001). | [x] | |
| 4 | Verificar el monto cobrado y la referencia | Leer `charged_amount` y `external_reference` | N/A | `charged_amount = "572.00"` y `external_reference = "1234567890"` (RF-004; D-UC06-04). | [x] | |
| 5 | Verificar los montos no informados | Leer `authorized_amount`, `captured_amount` y `released_amount` | N/A | Los tres en `null`: el cobro es directo, no hay reembolsos confirmados y la respuesta no afirma captura (RF-004; contrato UC06 §3 regla 4). | [x] | |
| 6 | Verificar el detalle | Leer `detail` | N/A | `detail` = `status_detail` registrado en la intención, o `null` si la Pasarela no lo informó (RF-003; D-UC06-04). | [x] | |
| 7 | Verificar que la consulta es de solo lectura | Comparar `charge_intent` y `charge_record` antes y después de dos consultas | N/A | Sin cambios de estado ni de montos y sin filas nuevas: la consulta no crea ni modifica registros (RF-002; caso extremo del SPEC). | [x] | |
| 8 | Verificar que no se contacta a la Pasarela | Request journal del simulador durante los pasos 2 a 7 | N/A | Cero llamadas nuevas (`GET /v1/payments/*` sin registros nuevos) (RF-006, CE-002). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | `reservation_id` con formato inválido | `GET /api/v1/reservations/R-100/payment-confirmation` | `R-100` (no UUID) | `400 VALIDATION_ERROR`, `retryable: false`; la validación (E2) ocurre antes de consultar la BD (README §4.3). | [x] | |
| 2 | Reserva sin `IntenciónDeCobro` | Consultar una reserva sobre la que nunca se ejecutó "Procesar cobro" | UUID sin intención | `404 CHARGE_INTENT_NOT_FOUND` con `retryable: true`; no se asume ningún estado (RF-005, RNF-003). | [x] | |
| 3 | Cobro aprobado con reembolso ya confirmado | Consultar una reserva con `refund_record` confirmado por UC09 | reembolso confirmado | `released_amount` informado con la suma de lo liberado, 2 decimales `HALF_UP`; el resto de campos no cambia (RF-004). | [x] | |
| 4 | Varias intenciones para la misma reserva | Comparar dos intenciones con `created_at` distinto y una con el mismo instante | N/A | Se devuelve la más reciente por `created_at`, desempatando por `id` descendente (D-UC06-05). | [x] | |
| 5 | BD no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, con `detail` genérico sin trazas ni nombres internos (README §4.3). | [x] | |
| 6 | Método no soportado en la ruta | `POST /api/v1/reservations/<UUID>/payment-confirmation` | N/A | `405 METHOD_NOT_ALLOWED` (E1 del catálogo). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `APPROVED` se deriva de `CAPTURED`, pero `captured_amount` viaja en `null`: la respuesta no afirma que la captura ni la liquidación ya se ejecutaron (D-UC06-06).
* **EC-002** : Los montos se serializan como *string* decimal de 2 cifras `HALF_UP`; nunca como número JSON (RNF-002; README §3.1).
* **EC-003** : El `detail` del `200` es el `status_detail` de la intención y no debe confundirse con el `detail` de *Problem Details* (D-UC06-04).
* **EC-004** : UC06 refleja la `IntenciónDeCobro` y no consulta `charge_record`: la fuente de verdad de la confirmación es la intención (RF-002, RF-006).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
