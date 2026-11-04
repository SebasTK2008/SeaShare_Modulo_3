### Test Case Specification: Procesar cobro · HU2 · E1 La Pasarela reporta el cobro como exitoso
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../../docs/features/005-procesar-cobro/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU2-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una solicitud de cobro previamente enviada a la Pasarela, cuando la Pasarela reporta que la transacción fue exitosa el sistema registra el estado, el monto cobrado, el detalle y la referencia externa, crea el `RegistroDeCobro` inmutable y deja la operación disponible para "Solicitar confirmación de pago" (UC06).

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../../docs/features/005-procesar-cobro/spec.md) |
| Contrato webhook de resultados | External contract | [pasarela-webhook-resultados.md](../../../docs/technical-plan/contracts/external/pasarela-webhook-resultados.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../docs/technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Contrato UC06 (consulta del resultado) | REST contract | [UC06-confirmacion-pago.md](../../../docs/technical-plan/contracts/rest/UC06-confirmacion-pago.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo A (CI/QA funcional):** la Pasarela se **simula**; el resultado se provoca con el webhook del simulador y se comprueba en su request journal y en los logs de la app.
* **Modo B (release):** el resultado debe confirmarse con una notificación `payment` real de Mercado Pago sandbox y la consulta real del pago, según [`pagos-api-real.md`](../pagos-api-real.md). Sin esa evidencia el TC no se aprueba.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 debe haber pasado**: existe una `IntenciónDeCobro` para la reserva, sin resultado definitivo.
* RabbitMQ arriba, *outbox* activo y worker de `finance.charge-command.v1` operativo.

##### Preconditions & Default Setup
* **Initial State** : reserva de prueba con valor total registrado (alquiler `360.00`, seguro `200.00`, depósito `12.00`, total `572.00`; ajustar a la versión vigente del entorno). Intención en `PENDING_SEND`/`IN_PROCESS`, con `external_reference = 1234567890` (id del pago en el simulador), `payer_email` y `payment_method_type = CREDIT_CARD`. La reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` (UUID v4 que se usa en los comandos).
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Resultado exitoso reportado por la Pasarela
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el punto de partida | `psql -c "SELECT id, status, amount, external_reference FROM charge_intent WHERE reservation_id = '<UUID>' ORDER BY created_at DESC;"` | reserva de prueba | Intención sin resultado definitivo y **sin** fila en `charge_record` (RF-004). | [ ] | |
| 2 | Simular el reporte de la Pasarela | `POST http://localhost:8080/api/v1/webhook/gateway?data.id=1234567890&type=payment` con firma del contrato | body `{"action":"payment.updated","type":"payment","data":{"id":"1234567890"}}`; `x-signature: ts=<ts>,v1=<HMAC-SHA256(secret_webhook, id:1234567890;request-id:<uuid>;ts:<ts>;)>`; `x-request-id: <uuid>` | `200 OK` con `{"status":"received"}`; la notificación queda persistida en `outbox_message` antes del acuse (RF-005). | [ ] | Firma calculada con el secret del entorno. |
| 3 | Verificar el mensaje interno | Consola RabbitMQ → `finance.gateway-notification.v1` → Get messages | N/A | Cuerpo `{"type":"payment","action":"payment.updated","data_id":"1234567890"}` (README §3.5). | [ ] | |
| 4 | Verificar la consulta del worker a la Pasarela | Request journal del simulador | N/A | `GET /v1/payments/1234567890`; el estado se toma de esa consulta, no del cuerpo del webhook (contrato de webhook, §3 regla 4). | [ ] | |
| 5 | Verificar el estado y el monto registrados | `psql -c "SELECT status, status_detail, amount, external_reference FROM charge_intent WHERE external_reference = '1234567890';"` | N/A | Intención en `CAPTURED`; `amount` conserva el total (`572.0000` de escala interna) y `status_detail` / `external_reference` reportados por la Pasarela (RF-006, RF-012). | [ ] | |
| 6 | Verificar el registro inmutable de auditoría | `psql -c "SELECT * FROM charge_record WHERE charge_intent_id = '<id de la intención>';"` | N/A | Una fila con `charge_intent_id`, desglose `rental_amount`/`insurance_amount`/`deposit_amount`, propietario y embarcación copiados, metadatos no sensibles y fecha de creación (RF-006, RF-010, RF-012, RF-004B). | [ ] | |
| 7 | Verificar la disponibilidad para UC06 | `curl -s http://localhost:8080/api/v1/reservations/<UUID>/payment-confirmation` | N/A | `200 OK`: `status = APPROVED`, `charged_amount = "572.00"`, `external_reference = "1234567890"`; `authorized_amount`, `captured_amount` y `released_amount` en `null` (RF-007; contrato UC06 §4). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Firma ausente o inválida | Repetir el `POST` del paso 2 sin `x-signature` / con `v1` alterado | firma inválida | `401 UNAUTHENTICATED`; **no** se procesa nada y la intención no cambia (contrato §5; README §4.4). | [ ] | |
| 2 | Payload mal formado | Enviar sin `action` en el cuerpo, o sin `data.id`/`type` en la query | payload incompleto | `400 VALIDATION_ERROR`; la intención no cambia (E2 del catálogo). | [ ] | |
| 3 | `data.id` sin intención asociada | Webhook con un id que no coincide con ningún `external_reference` | `data.id=9999999999` | `200 OK` y registro del evento en `operational_failure`; **sin** `charge_record` (caso extremo del SPEC). | [ ] | |
| 4 | La Pasarela responde `404` al consultar el pago | Simular `404` en `GET /v1/payments/{id}` | N/A | Un `404` no es veredicto financiero: la intención conserva su estado y el evento queda en `operational_failure` (D-UC05-13). | [ ] | |
| 5 | Notificación duplicada del mismo resultado | Reenviar el mismo webhook dos veces | mismos datos | `200 OK`; el resultado ya registrado no se altera y no se duplica el `charge_record` (idempotencia; `UNIQUE(charge_intent_id)`). | [ ] | |
| 6 | La Pasarela reporta `in_process` y luego `approved` | Primera consulta `in_process`; segunda `approved` | dos webhooks | Intención `IN_PROCESS` y después `CAPTURED`; el registro de auditoría se crea **solo** al capturar (RF-006, RF-008). | [ ] | |
| 7 | BD no disponible al persistir la notificación | Detener PostgreSQL y reenviar el webhook | N/A | `500 INTERNAL_ERROR` para que la Pasarela reintente (contrato de webhook, §3 regla 7); no queda acuse sin registro. | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : La respuesta técnica `approved` de `POST /v1/payments` y el webhook convergen en el mismo servicio de registro; un segundo resultado igual es idempotente (D-UC05-08).
* **EC-002** : `charge_record` no admite `UPDATE` (trigger anti-mutación) y `charge_intent_id` es único: un cobro confirmado deja exactamente un registro.
* **EC-003** : El monto interno de 4 decimales se serializa `HALF_UP` a 2 decimales en la respuesta de UC06 (RNF-002; README §3.1).
* **EC-004** : Un estado que no sea cobro confirmado (`rejected`, `expired`, `cancelled`, `in_process`) nunca crea `charge_record` ni se reporta como `APPROVED` (RF-008, CE-004).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
