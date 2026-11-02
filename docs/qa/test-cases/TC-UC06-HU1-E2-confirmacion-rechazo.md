### Test Case Specification: Solicitar confirmación de pago · HU1 · E2 Confirmación de un cobro rechazado
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/006-solicitar-confirmacion-de-pago/spec.md](../../../docs/features/006-solicitar-confirmacion-de-pago/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC06-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una `IntenciónDeCobro` marcada como rechazada por "Procesar cobro", el sistema devuelve el estado externo correspondiente y su detalle, **sin** reportar un cobro aprobado y **sin** exponer montos cobrados, de modo que Reservas pueda revertir el bloqueo temporal del activo.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC06 | Specification Document | [spec.md](../../../docs/features/006-solicitar-confirmacion-de-pago/spec.md) |
| Contrato REST UC06 | REST contract | [UC06-confirmacion-pago.md](../../../docs/technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Feature Spec UC05 (resultado del cobro) | Specification Document | [spec.md](../../../docs/features/005-procesar-cobro/spec.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay autenticación: la seguridad está fuera del alcance del proyecto y el contrato no valida al llamador (README §3.2).

##### Dependencies & Assumptions
* **TC-UC05-HU2-E2 debe haber pasado**: la intención de la reserva quedó en `REJECTED` con su `status_detail` y sin `RegistroDeCobro`.
* La reserva conserva su bloqueo temporal: revertirlo es decisión del Sistema de Reservas y Operaciones, no de UC06.

##### Preconditions & Default Setup
* **Initial State** : reserva con `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` (UUID de ejemplo del contrato; sustituir por el de la reserva de prueba). `charge_intent` en `REJECTED`, `amount = 572.00` (ajustar a la versión vigente del entorno), `status_detail = cc_rejected_bad_filled_other` (ejemplo del contrato de cobro) y `external_reference = "1234567890"`; `charge_record` sin filas.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`; simulador de la Pasarela con su request journal accesible.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta de un cobro rechazado
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el estado registrado por UC05 | `psql -c "SELECT status, status_detail, amount, external_reference FROM charge_intent WHERE reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10' ORDER BY created_at DESC, id DESC LIMIT 1;"` | N/A | La intención más reciente está en `REJECTED` con su detalle; `charge_record` sin filas (RF-002). | [ ] | |
| 2 | Solicitar la confirmación de pago | `curl -s -H "Accept: application/json" http://localhost:8080/api/v1/reservations/b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10/payment-confirmation` | N/A | `200 OK` con `Content-Type: application/json` (RF-001; contrato §4). | [ ] | |
| 3 | Verificar el estado devuelto | Leer `status` del cuerpo | N/A | `status = REJECTED`, fiel al estado registrado (RF-003, CE-001). | [ ] | |
| 4 | Verificar el detalle del rechazo | Leer `detail` | N/A | `detail` = `status_detail` de la intención; el contrato admite `null` si no está disponible (RF-003). | [ ] | |
| 5 | Verificar que no se reporta cobro aprobado | Leer `charged_amount`, `authorized_amount`, `captured_amount` y `released_amount` | N/A | `charged_amount = null` y el resto en `null`: nunca se informa un cobro aprobado cuando la Pasarela rechazó (RF-003, RF-004). | [ ] | |
| 6 | Verificar la referencia externa | Leer `external_reference` | N/A | `"1234567890"`, la referencia provista por la Pasarela (RF-004). | [ ] | |
| 7 | Verificar que la consulta es de solo lectura | Comparar `charge_intent` y `charge_record` antes y después de dos consultas | N/A | Sin cambios y sin filas nuevas (RF-002; caso extremo del SPEC). | [ ] | |
| 8 | Verificar que no se contacta a la Pasarela | Request journal del simulador durante los pasos 2 a 7 | N/A | Cero llamadas nuevas a la Pasarela (RF-006, CE-002). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin `IntenciónDeCobro` | Consultar una reserva sobre la que nunca se ejecutó "Procesar cobro" | UUID sin intención | `404 CHARGE_INTENT_NOT_FOUND`, `retryable: true`; error controlado, sin estado asumido (RF-005, RNF-003, CE-003). | [ ] | |
| 2 | `reservation_id` con formato inválido | `GET /api/v1/reservations/R-100/payment-confirmation` | `R-100` (no UUID) | `400 VALIDATION_ERROR`, `retryable: false`; validación antes de consultar la BD (E2; README §4.3). | [ ] | |
| 3 | Falla de comunicación con la Pasarela | Consultar una reserva cuya intención quedó en `COMMUNICATION_ERROR` | N/A | `status = UNKNOWN` (nunca `REJECTED` ni `APPROVED`); el mapeo del estado interno está en el contrato §4 (RF-003). | [ ] | |
| 4 | La Pasarela informó `cancelled` o `expired` | Consultar reservas con esas intenciones | N/A | `status = CANCELLED` / `EXPIRED` con su `detail`; nunca `APPROVED` (caso extremo del SPEC, esc. 2). | [ ] | |
| 5 | Nueva intención tras el rechazo | Crear un segundo intento (nueva `PENDING`, nueva `status_changed_at`) y consultar | dos intenciones | Se devuelve la intención **más reciente**; la rechazada sigue en BD y no se mezcla con la nueva (RF-002; D-UC06-05). | [ ] | |
| 6 | BD no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, con `detail` genérico sin detalles internos (README §4.3). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `charged_amount` es `null` aunque la intención conserve `amount`: el monto informado solo se publica en `APPROVED` (D-UC06-04).
* **EC-002** : Un rechazo financiero (`REJECTED` con detalle de la Pasarela) no se confunde con una falla técnica (`UNKNOWN`); la coincidencia con la intención se verifica estado a estado (CE-001).
* **EC-003** : La respuesta no expone el token ni datos sensibles del medio de pago: el contrato UC06 no publica esos campos (RF-004; CE-005 del UC05).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
