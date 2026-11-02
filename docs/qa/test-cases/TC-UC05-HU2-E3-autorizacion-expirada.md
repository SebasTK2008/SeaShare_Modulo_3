### Test Case Specification: Procesar cobro · HU2 · E3 La autorización aprobada expira antes de la captura
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../../docs/features/005-procesar-cobro/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU2-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, cuando la vigencia de una operación aprobada se cumple sin captura, el sistema registra el hecho de la expiración, **no** asume que los fondos permanecen disponibles, **no** crea `RegistroDeCobro` y deja el estado explícito de "expirada" disponible para "Solicitar confirmación de pago" (UC06) y para conciliación.

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
* La Pasarela de pago se **simula**; la expiración se provoca fijando el pago en estado `expired` en el simulador.
* **Premisa de diseño**: en la versión vigente el cobro es **directo con captura automática**, sin autorización retenida ni solicitud de captura (contrato de cobro, §3 regla 4; general-plan D-32). Por eso el escenario se materializa con el estado externo `expired` que reporta la Pasarela, que es el único equivalente observable de "la operación expiró sin cobro". La monitorización de vigencia de autorizaciones (RNF-005 del UC05) no tiene contraparte en este diseño.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 debe haber pasado** y la intención debe seguir sin resultado definitivo.
* El simulador permite fijar `status=expired` y el `status_detail` del pago.

##### Preconditions & Default Setup
* **Initial State** : reserva de prueba con valor total registrado (alquiler `360.00`, seguro `200.00`, depósito `12.00`, total `572.00`; ajustar a la versión vigente del entorno). Intención en `IN_PROCESS` con `external_reference = 1234567890`, `payer_email` y `payment_method_type = CREDIT_CARD`; **sin** fila en `charge_record`. Se usa el UUID v4 real de la reserva de TC-UC05-HU1-E1 (allí etiquetada `R-100`).
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Expiración registrada sin asumir fondos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar la operación en curso | `psql -c "SELECT status, amount, external_reference FROM charge_intent WHERE external_reference = '1234567890';"` | N/A | Intención `IN_PROCESS`, monto y referencia externa registrados; `charge_record` vacío (RF-004). | [ ] | |
| 2 | Simular el vencimiento de la vigencia | Fijar el pago `1234567890` como `expired` en el simulador | `status=expired`, `status_detail` informado por el simulador | La Pasarela deja de considerar la operación cobrable. | [ ] | Ajustar la vigencia al mínimo del entorno. |
| 3 | Notificar el resultado | `POST http://localhost:8080/api/v1/webhook/gateway?data.id=1234567890&type=payment` con firma válida | body `{"action":"payment.updated","type":"payment"}`; `x-signature` del contrato; `x-request-id: <uuid>` | `200 OK` con `{"status":"received"}`; notificación persistida en `outbox_message` (RF-005). | [ ] | |
| 4 | Verificar el mensaje interno y la consulta del worker | Consola RabbitMQ → `finance.gateway-notification.v1`; request journal del simulador | N/A | Mensaje `{"type":"payment","action":"payment.updated","data_id":"1234567890"}` y `GET /v1/payments/1234567890` con `status=expired`. | [ ] | |
| 5 | Verificar el registro de la expiración | `psql -c "SELECT status, status_detail, amount, external_reference FROM charge_intent WHERE external_reference = '1234567890';"` | N/A | Intención en `EXPIRED` con el detalle de la Pasarela; el monto y la referencia se conservan (RF-014). | [ ] | |
| 6 | Verificar que no se asume cobro ni se crea registro | `psql -c "SELECT count(*) FROM charge_record WHERE charge_intent_id = '<id de la intención>';"` | N/A | `count = 0`: ni cobro ni fondos disponibles asumidos; sin `RegistroDeCobro` (RF-008, RF-014, CE-007). | [ ] | |
| 7 | Verificar la disponibilidad para UC06 | `curl -s http://localhost:8080/api/v1/reservations/<UUID>/payment-confirmation` | N/A | `200 OK`: `status = EXPIRED`, `detail` con el detalle, `charged_amount = null` y `external_reference` informada (RF-014; contrato UC06 §4). | [ ] | |
| 8 | Verificar el flujo de recuperación | Revisar el estado en BD y el registro de conciliación (`operational_failure` / job de conciliación) | N/A | La operación queda en estado explícito `EXPIRED`, disponible para conciliación; el nuevo intento exige una `PENDING` nueva (nueva `status_changed_at`) y una intención nueva con nueva clave (RF-014, RF-004A). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | La Pasarela informa `cancelled` | Repetir el flujo con estado `cancelled` | `status=cancelled` | Intención `CANCELLED` con su detalle, sin `charge_record` y nunca `APPROVED` (CE-004). | [ ] | |
| 2 | Falla de comunicación al consultar el pago | Simular timeout/`5xx` en `GET /v1/payments/{id}` | N/A | La intención **conserva** su estado (no se asume `EXPIRED`); 5 intentos con *backoff* base 1 s, DLQ y `operational_failure` (RNF-003; D-UC05-13). | [ ] | |
| 3 | La Pasarela responde `404` al consultar el pago | Simular `404` | N/A | Un `404` no es veredicto financiero: la intención no se cierra y el evento queda en `operational_failure` (D-UC05-13). | [ ] | |
| 4 | Notificación repetida de expiración | Reenviar el mismo webhook dos veces | mismos datos | `200 OK`; el estado registrado no cambia y no se generan registros adicionales (idempotencia). | [ ] | |
| 5 | Webhook inválido | Enviar sin `action` o con firma inválida | payload incompleto / firma alterada | `400 VALIDATION_ERROR` / `401 UNAUTHENTICATED`; la intención no cambia (E2; README §4.4). | [ ] | |
| 6 | Expiración con `data.id` desconocido | Webhook con un id sin intención asociada | `data.id=9999999999` | `200 OK` y evento en `operational_failure`, sin `charge_record` (caso extremo del SPEC). | [ ] | |
| 7 | Reintento reutilizando la intención expirada | Republicar `PENDING` con el mismo `status_changed_at` | misma clave idempotente | No se abre otra intención ni se genera un segundo cobro; el reintento válido usa una marca de tiempo nueva (RF-004A). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : `EXPIRED` no es `REJECTED` ni `CANCELLED`: no constituye un rechazo del Arrendatario ni una reserva cobrada (caso extremo del SPEC).
* **EC-002** : `EXPIRED` es terminal para el intento: no crea `charge_record` y no admite transición posterior a `CAPTURED` (plan UC05 §Reglas 5 y 6).
* **EC-003** : RNF-005 (monitoreo de vigencia de autorizaciones) no es verificable con cobro directo; se cubre la parte observable —registro explícito de la expiración y disponibilidad para conciliación— y se deja constancia de la diferencia.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
