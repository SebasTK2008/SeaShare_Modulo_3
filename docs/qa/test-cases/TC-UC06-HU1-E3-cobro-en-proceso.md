### Test Case Specification: Solicitar confirmación de pago · HU1 · E3 Consulta de un cobro aún en proceso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/006-solicitar-confirmacion-de-pago/spec.md](../../../docs/features/006-solicitar-confirmacion-de-pago/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC06-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una `IntenciónDeCobro` todavía en curso, el sistema devuelve fielmente el estado "en proceso" —sin inventar ni anticipar una aprobación o un rechazo— junto con los montos y referencias disponibles, en una consulta de solo lectura que no contacta a la Pasarela.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC06 | Specification Document | [spec.md](../../../docs/features/006-solicitar-confirmacion-de-pago/spec.md) |
| Contrato REST UC06 | REST contract | [UC06-confirmacion-pago.md](../../../docs/technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Feature Spec UC05 (estados internos) | Specification Document | [spec.md](../../../docs/features/005-procesar-cobro/spec.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* No hay autenticación: la seguridad está fuera del alcance del proyecto y el contrato no valida al llamador (README §3.2).
* La comprobación de "la Pasarela aún no reportó" se hace con el worker pausado (o con `GATEWAY_BASE_URL` apuntando a un puerto cerrado), para que no llegue el resultado durante la consulta.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 debe haber pasado** y la intención debe seguir sin resultado definitivo (`PENDING_SEND` o `IN_PROCESS`).
* UC05 crea la intención de forma asíncrona tras el estado `PENDING` de UC07.

##### Preconditions & Default Setup
* **Initial State** : reserva con `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` (UUID de ejemplo del contrato; sustituir por el de la reserva de prueba). `charge_intent` en `IN_PROCESS` con `amount = 572.00` (ajustar a la versión vigente del entorno) y `external_reference = "1234567890"`; ninguna fila en `charge_record`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`; credenciales de prueba de Mercado Pago en `.env` (runbook).
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Consulta de un cobro en proceso
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la intención en curso | `psql -c "SELECT status, amount, external_reference FROM charge_intent WHERE reservation_id = 'b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10' ORDER BY created_at DESC, id DESC LIMIT 1;"` | N/A | La intención más reciente está en `PENDING_SEND` o `IN_PROCESS`, sin resultado definitivo (RF-002). | [ ] | |
| 2 | Solicitar la confirmación de pago | `curl -s -H "Accept: application/json" http://localhost:8080/api/v1/reservations/b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10/payment-confirmation` | N/A | `200 OK` con `Content-Type: application/json` (RF-001; contrato §4). | [ ] | |
| 3 | Verificar el estado devuelto | Leer `status` del cuerpo | N/A | `status = IN_PROCESS` (tanto `PENDING_SEND` como `IN_PROCESS` se exponen así) (RF-003, CE-001). | [ ] | |
| 4 | Verificar la ausencia de resultado inventado | Leer `charged_amount` y el resto de montos | N/A | `charged_amount`, `authorized_amount`, `captured_amount` y `released_amount` en `null`; no hay importe cobrado (RF-003, RF-004). | [ ] | |
| 5 | Verificar la referencia y el detalle disponibles | Leer `external_reference` y `detail` | N/A | `external_reference = "1234567890"` si la Pasarela ya la asignó; `null` si aún no existe. `detail` = `status_detail` vigente o `null` (RF-004). | [ ] | |
| 6 | Verificar que la consulta es de solo lectura | Comparar `charge_intent` y `charge_record` antes y después de dos consultas | N/A | Sin cambios de estado ni de montos y sin filas nuevas; la intención no pasa a un estado terminal (RF-002; caso extremo del SPEC). | [ ] | |
| 7 | Verificar que no se contacta a la Pasarela | Logs de la app (`docker logs seashare-app`) durante los pasos 2 a 6 | N/A | Cero llamadas nuevas a la Pasarela: la consulta se resuelve con la intención ya existente (RF-006, CE-002). | [ ] | |
| 8 | Consultar de nuevo tras el resultado | Registrar el resultado en la Pasarela (TC-UC05-HU2-E1/E2) y repetir el `GET` | N/A | La misma consulta devuelve el estado vigente actualizado (`APPROVED` o `REJECTED`) sin caché ni reinicio (RF-003; contrato UC06 §3 regla 6). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Todavía no existe `IntenciónDeCobro` | Consultar inmediatamente tras publicar `PENDING`, antes de que UC05 cree la intención | UUID sin intención | `404 CHARGE_INTENT_NOT_FOUND`, `retryable: true`, sin estado asumido; el llamador puede reintentar (RF-005, RNF-003, CE-003). | [ ] | |
| 2 | Intención en `COMMUNICATION_ERROR` | Consultar una reserva cuya intención quedó en falla de comunicación | N/A | `status = UNKNOWN` (nunca `IN_PROCESS`, `APPROVED` ni `REJECTED`); un timeout no es un veredicto (contrato §4; RNF-003 del UC05). | [ ] | |
| 3 | `reservation_id` con formato inválido | `GET /api/v1/reservations/R-100/payment-confirmation` | `R-100` (no UUID) | `400 VALIDATION_ERROR`, `retryable: false`; validación antes de consultar la BD (E2; README §4.3). | [ ] | |
| 4 | `PENDING_SEND` sin referencia externa | Consultar la intención antes de que el worker reciba el id de pago | N/A | `status = IN_PROCESS` y `external_reference = null`; el resto del cuerpo se informa igual (RF-004). | [ ] | |
| 5 | Varias intenciones para la misma reserva | Crear un segundo intento y consultar sin esperar su resultado | dos intenciones | Se devuelve la **más reciente** por `created_at`, desempatando por `id` descendente (D-UC06-05). | [ ] | |
| 6 | BD no disponible | Detener PostgreSQL y repetir la consulta | N/A | `500 INTERNAL_ERROR`, `retryable: true`, con `detail` genérico sin detalles internos (README §4.3). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `IN_PROCESS` siempre viaja con `charged_amount = null`; no se publica un monto cobrado antes de que exista captura (D-UC06-04).
* **EC-002** : La consulta no adelanta estado aunque el pago ya esté aprobado en la Pasarela: mientras UC05 no registre el resultado, la respuesta sigue siendo `IN_PROCESS` (RF-003, fidelidad al registro).
* **EC-003** : Repetir la consulta no crea intenciones, no altera `created_at` y no genera registros financieros (RF-002; caso extremo del SPEC).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
