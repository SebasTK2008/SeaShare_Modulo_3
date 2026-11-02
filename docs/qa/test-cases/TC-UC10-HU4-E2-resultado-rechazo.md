### Test Case Specification: Liquidar fondos de alquiler · HU4 · E2 La Pasarela reporta rechazo o fallo de la dispersión
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/010-liquidar-fondos-alquiler/spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC10-HU4-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la Pasarela de Pago reporta el rechazo o fallo de una dispersión previamente solicitada, el sistema registra el estado externo y su detalle en la `IntenciónDeDispersión`, **no** marca la liquidación como completada, **no** crea `RegistroDeDispersión` ni `RegistroDeComisión`, y deja el resultado disponible internamente para consulta y conciliación.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC10 | Specification Document | [spec.md](../../../docs/features/010-liquidar-fondos-alquiler/spec.md) |
| Plan UC10 | Plan Document | [plan.md](../../../docs/features/010-liquidar-fondos-alquiler/plan.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../docs/technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de liquidación | External contract | [pasarela-comando-liquidacion.md](../../../docs/technical-plan/contracts/external/pasarela-comando-liquidacion.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; los estados externos se provocan desde el simulador (WireMock) y la notificación se firma con el secret del entorno.

##### Dependencies & Assumptions
* **TC-UC10-HU4-E1 debe haber pasado**: el camino de notificación `type=merchant_order` y la consulta de detalle funcionan.
* Existe una `settlement_intent` enviada a la Pasarela, con `external_reference` persistida y sin registro confirmado previo.

##### Preconditions & Default Setup
* **Initial State** : `settlement_intent` de la reserva `R-200` = `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` (alcance `STANDARD`) en estado `IN_PROCESS`, `external_reference = mp_disp_QA_02`. Montos de ejemplo (ajustar a la versión vigente del entorno): alquiler bruto `360.00`, seguro `200.00`, depósito `12.00`, comisión `54.00`, monto solicitado `106.00`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Registro del rechazo sin marcar la liquidación como completada
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la solicitud previa | `SELECT` sobre `settlement_intent` | `reservation_id = 0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Intención en estado en curso, con monto solicitado y `external_reference`; sin filas en `settlement_record` para esa intención (RF-009). | [ ] | |
| 2 | Simular el rechazo en la Pasarela | Configurar el simulador para responder el detalle en estado rechazado | `status = rejected`, `status_detail = cc_rejected_insufficient_amount` (valor del entorno) | N/A (paso de preparación del simulador). | [ ] | |
| 3 | Simular la notificación | `POST /api/v1/webhook/gateway?data.id=mp_disp_QA_02&type=merchant_order` con `x-signature` válida y `x-request-id` | Body mínimo del contrato (`action`, `type`) | `200 OK` inmediato; la notificación se persiste de forma durable (contract §3.2). | [ ] | |
| 4 | Verificar la consulta de detalle | Request journal del simulador (`GET /v1/advanced_payments/{id}`) | `{id} = mp_disp_QA_02` | La consulta se ejecuta en la cola `finance.gateway-notification.v1`; el estado se toma de la consulta (contract §3.4). | [ ] | |
| 5 | Verificar la intención actualizada | `SELECT` sobre `settlement_intent` | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | Estado `REJECTED` y `status_detail` con el detalle externo; la intención es la fuente de verdad del resultado (RF-010). | [ ] | |
| 6 | Verificar que no se creó un registro exitoso | `SELECT count(*)` sobre `settlement_record` y `commission_record` por la intención | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | **Cero** filas en ambas tablas: un fallo externo nunca se registra como dispersión exitosa (RF-012, CE-005). | [ ] | |
| 7 | Verificar que no se reporta como liquidado | `GET /api/v1/financial-records?reservation_id=0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` | La reserva no muestra un registro de dispersión confirmado; el resultado fallido queda disponible internamente para conciliación (RF-012). | [ ] | |
| 8 | Verificar la repetibilidad | Reprocesar la misma notificación (`data.id` idéntico) | misma firma y payload | `200 OK`; no se altera el estado ya registrado ni se crea un segundo resultado (contract §6). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Dispersión aún en proceso | Simular detalle con estado `in_process`/`pending` | `status = in_process` | Intención en `IN_PROCESS`; sin `RegistroDeDispersión`; la operación no se reporta como liquidada (RF-012). | [ ] | |
| 2 | Dispersión cancelada | Simular detalle con estado `cancelled` | `status = cancelled` | Intención en `CANCELLED` con su detalle; sin registros exitosos (RF-012). | [ ] | |
| 3 | Dispersión expirada | Simular detalle con estado `expired` | `status = expired` | Intención en `EXPIRED` con su detalle; sin registros exitosos (RF-012). | [ ] | |
| 4 | Estado externo desconocido | Simular un `status` no contemplado | `status = unknown_state` | La intención **no** cambia de estado y el evento se registra para revisión; no se asume éxito ni rechazo (plan UC10, regla 8). | [ ] | |
| 5 | Firma inválida en el webhook | Enviar `x-signature` con `v1` alterado | firma inválida | `401 UNAUTHENTICATED`; no se procesa y la intención conserva su estado (contract §3.1). | [ ] | |
| 6 | `data.id` sin intención asociada | Enviar un `data.id` que no corresponde a ninguna intención | `mp_disp_desconocido` | `200 OK` y registro en `operational_failure`; ningún estado financiero se modifica (README §4.4). | [ ] | |
| 7 | La Pasarela responde `404` al consultar | Simular `404` en `GET /v1/advanced_payments/{id}` | N/A | La intención **no** se cierra en `REJECTED`; se conserva su estado y el evento queda en `operational_failure`. | [ ] | |
| 8 | Falla transitoria del paso 2 | Simular `503`/timeout en la consulta | N/A | Reintentos de la cola interna (5 intentos, backoff base 1 s) y DLQ con `operational_failure` al agotarlos; nunca éxito falso (CE-004). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un `400` de la Pasarela al crear la dispersión (por ejemplo `collector_id` no vinculado) es fallo no reintentable y deja la intención en estado fallido sin registro (contrato de liquidación §5).
* **EC-002** : Un `5xx` o timeout al enviar la solicitud deja la intención en `COMMUNICATION_ERROR` y se reintenta; no se confunde con rechazo de negocio (RNF-003, CE-004).
* **EC-003** : Un resultado fallido no borra ni modifica un `RegistroDeDispersión` ya confirmado de la misma reserva por otra operación (inmutabilidad, RF-010).
* **EC-004** : El detalle técnico expuesto en `4xx` es accionable; en `5xx` es genérico, sin trazas ni SQL (README §4.3).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
