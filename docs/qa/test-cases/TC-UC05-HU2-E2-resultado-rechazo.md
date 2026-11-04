### Test Case Specification: Procesar cobro · HU2 · E2 La Pasarela reporta el rechazo del cobro
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../../docs/features/005-procesar-cobro/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU2-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante el rechazo reportado por la Pasarela para una solicitud de cobro previamente enviada, el sistema registra la transacción como fallida con su detalle y referencia externa, **no** crea el `RegistroDeCobro`, **no** marca la reserva como cobrada y deja el resultado disponible para "Solicitar confirmación de pago" (UC06).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../../docs/features/005-procesar-cobro/spec.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../docs/technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../docs/technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Contrato UC06 (estado devuelto) | REST contract | [UC06-confirmacion-pago.md](../../../docs/technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo A (CI/QA funcional):** la Pasarela se **simula**; el rechazo se provoca con el estado del pago en el simulador y su notificación `payment.updated`.
* **Modo B (release):** debe provocarse un rechazo real con tarjeta/caso de prueba de Mercado Pago sandbox y verificar la ausencia de `charge_record`, según [`pagos-api-real.md`](../pagos-api-real.md). Sin esa evidencia el TC no se aprueba.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 debe haber pasado** (existe la intención de la reserva, sin resultado definitivo).
* El simulador permite fijar el estado del pago en `rejected` y su `status_detail`.

##### Preconditions & Default Setup
* **Initial State** : reserva de prueba con valor total registrado (alquiler `360.00`, seguro `200.00`, depósito `12.00`, total `572.00`; ajustar a la versión vigente del entorno). Intención en `PENDING_SEND`/`IN_PROCESS` con `external_reference = 1234567890`, `payer_email` y `payment_method_type = CREDIT_CARD`; ninguna fila en `charge_record`. La reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`.
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Rechazo reportado por la Pasarela
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el punto de partida | `psql -c "SELECT status, amount, external_reference FROM charge_intent WHERE external_reference = '1234567890';"` | N/A | Intención sin resultado definitivo y sin `charge_record` (RF-004). | [ ] | |
| 2 | Simular el rechazo en la Pasarela | Fijar en el simulador el pago `1234567890` como `rejected` | `status=rejected`, `status_detail=cc_rejected_bad_filled_other` (ejemplo del contrato §4) | El simulador devuelve `rejected` al consultar el pago. | [ ] | |
| 3 | Notificar el resultado | `POST http://localhost:8080/api/v1/webhook/gateway?data.id=1234567890&type=payment` con firma válida | body `{"action":"payment.updated","type":"payment"}`; `x-signature` del contrato; `x-request-id: <uuid>` | `200 OK` con `{"status":"received"}`; notificación persistida en `outbox_message` (RF-005). | [ ] | |
| 4 | Verificar la consulta del worker y el mensaje interno | Request journal del simulador y consola RabbitMQ → `finance.gateway-notification.v1` | N/A | `GET /v1/payments/1234567890` y mensaje `{"type":"payment","action":"payment.updated","data_id":"1234567890"}`. | [ ] | |
| 5 | Verificar el estado fallido registrado | `psql -c "SELECT status, status_detail, amount, external_reference FROM charge_intent WHERE external_reference = '1234567890';"` | N/A | Intención en `REJECTED` con el `status_detail` de la Pasarela; el monto solicitado y la referencia externa se conservan (RF-006). | [ ] | |
| 6 | Verificar que no se genera registro de cobro | `psql -c "SELECT count(*) FROM charge_record WHERE charge_intent_id = '<id de la intención>';"` | N/A | `count = 0`: ninguna transacción rechazada genera `RegistroDeCobro` (RF-008, CE-004). | [ ] | |
| 7 | Verificar la disponibilidad para UC06 | `curl -s http://localhost:8080/api/v1/reservations/<UUID>/payment-confirmation` | N/A | `200 OK`: `status = REJECTED`, `detail` con el detalle de la Pasarela, `charged_amount = null` y `external_reference` informada (RF-007; contrato UC06 §4). | [ ] | |
| 8 | Confirmar que la reserva no queda cobrada | Revisar `charge_record` y la respuesta del paso 7 | N/A | No existe registro financiero del cobro y la respuesta nunca es `APPROVED` (RF-008, CE-004). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | La Pasarela rechaza el token con `400` | Simular `400` de validación en `POST /v1/payments` | token inválido | Fallo **no reintentable**: intención `REJECTED` con detalle técnico, sin `charge_record` y sin marcar la reserva como cobrada (RF-013; contrato §5). | [ ] | No es un veredicto financiero. |
| 2 | Caída, `502`/`503` o timeout (10 s) al enviar | Simular `503` / timeout | N/A | Intención `COMMUNICATION_ERROR` (no `REJECTED`); 5 intentos con la misma `X-Idempotency-Key` y, al agotarlos, DLQ + `operational_failure`; UC06 devuelve `UNKNOWN` (RNF-003). | [ ] | |
| 3 | Notificación con `data.id` desconocido | Webhook con un id sin intención asociada | `data.id=9999999999` | `200 OK` y evento en `operational_failure`, sin `charge_record` (caso extremo del SPEC). | [ ] | |
| 4 | Notificación repetida del mismo rechazo | Reenviar el webhook dos veces | mismos datos | `200 OK`; el resultado ya registrado no se altera ni crea registros adicionales (idempotencia). | [ ] | |
| 5 | Resultado distinto posterior sobre una intención terminal | Enviar `approved` después de `REJECTED` | `status=approved` | No se crea `charge_record` ni se reporta cobro exitoso; el nuevo intento de cobro exige una intención nueva (nueva `PENDING` con nueva `status_changed_at`) (RF-004A, RF-008). | [ ] | |
| 6 | La Pasarela informa `cancelled` | Repetir el flujo con estado `cancelled` | `status=cancelled` | Intención `CANCELLED` con su detalle, sin `charge_record` y nunca `APPROVED` en UC06 (CE-004). | [ ] | |
| 7 | BD no disponible al persistir la notificación | Detener PostgreSQL y reenviar el webhook | N/A | `500 INTERNAL_ERROR` para que la Pasarela reintente (contrato de webhook, §3 regla 7). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un cobro rechazado deja `charge_record` en cero filas para su intención: la integridad del cobro se mide sobre el registro inmutable, no sobre el estado de la intención (CE-004).
* **EC-002** : Rechazo financiero (`status=rejected`) y fallo técnico (`REJECTED` por `400` del token, o `COMMUNICATION_ERROR`) son condiciones distintas y no deben confundirse (README §4.4).
* **EC-003** : `charged_amount` viaja en `null` aunque la intención conserve `amount`; solo se informa en `APPROVED` (D-UC06-04).
* **EC-004** : El SPEC no define la deduplicación de dos resultados **distintos** para la misma transacción; solo se exige que ninguno produzca un cobro exitoso sin captura ni un registro sin captura.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
