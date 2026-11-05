### Test Case Specification: Reembolsos · HU1 · E1 Reembolso por cancelación flexible (>72 h)
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación flexible informada por "Brindar el estado de la reserva", el sistema recupera el valor total registrado (alquiler, seguro y depósito), registra la intención de reembolso y solicita a la Pasarela el 100 % en una sola operación, sin aplicar descuentos por costos transaccionales.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC09 | Specification Document | [spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md) |
| Implementation Plan UC09 | Plan Document | [plan.md](../../../docs/features/009-reembolsar-dinero-arrendatario/plan.md) |
| Contrato evento de estado de reserva (disparador) | Events contract | [UC07-estado-reserva.md](../../../docs/technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de reembolso | External contract | [pasarela-comando-reembolso.md](../../../docs/technical-plan/contracts/external/pasarela-comando-reembolso.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* **Modo A (suite automática):** el cuerpo y las cabeceras que el sistema envía a la Pasarela los verifican `MercadoPagoChargeAdapterTest` y `MercadoPagoRefundAdapterTest` (WireMock embebido); este TC verifica el efecto en la BD, las colas y la consulta real del reembolso.
* **Modo B (release):** el reembolso total debe ejecutarse contra la API real de Mercado Pago sandbox, según [`pagos-api-real.md`](../pagos-api-real.md). Sin esa evidencia el TC no se aprueba.
* El resultado definitivo de la Pasarela se cubre en UC09 HU3; aquí la operación queda pendiente.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 y TC-UC05-HU2-E1 deben haber pasado**: existe cobro `CAPTURED` con `charge_record` y `charge_intent.external_reference`.
* **TC-UC08-HU1-E1 debe haber pasado** (seguimiento del depósito operativo en `HELD`).
* Worker de reembolso activo sobre `finance.refund-command.v1`; *outbox* habilitado.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con `rental_amount = 900000.00`, `insurance_amount = 60000.00`, `deposit_amount = 30000.00`, `amount = 990000.00` y `deposit_disposition.disposition = 'HELD'` (valores ilustrativos del contrato UC04; ajustar a la versión vigente del entorno).
* **Environment** : staging local (`docker compose --profile app`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Cálculo y solicitud del 100 % del valor pagado
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar los montos internos | `SELECT amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'b7d0e2a1-…';` | `990000.00 / 900000.00 / 60000.00 / 30000.00` | El desglose inmutable del cobro está disponible; es la única fuente del monto (RF-001, RF-002). | [ ] | |
| 2 | Publicar la cancelación flexible | Publicar en `seashare.reservations` con routing key `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"CANCELLED_FLEXIBLE","status_changed_at":"2026-12-20T09:00:00Z"}` | `ack`; UC07 delega en UC09 la operación de reembolso (RF-001). | [ ] | Cola `finance.reservation-status.v1`. |
| 3 | Verificar la intención de reembolso | `SELECT trigger, scope, amount, charge_intent_id, status, idempotency_key FROM refund_intent;` | `trigger = 'CANCELLED_FLEXIBLE'` | `scope = 'FULL'`, `amount = 990000.00`, `charge_intent_id` del cobro capturado, estado en curso y clave UUID v4 (D-UC09-12) (RF-002, RF-006). | [ ] | |
| 4 | Verificar el desenlace del depósito | `SELECT disposition FROM deposit_disposition WHERE reservation_id = 'b7d0e2a1-…';` | N/A | `disposition = 'REFUND_REQUESTED'`, porque el depósito forma parte del valor a devolver (RF-002, RF-008). | [ ] | |
| 5 | Verificar el mensaje interno | Inspeccionar `outbox_message` y la cola `finance.refund-command.v1` | `{"refund_intent_id":"<uuid>"}` | El comando referencia la intención y no transporta datos de tarjeta; el worker lee el resto de la intención (RNF-001). | [ ] | |
| 6 | Verificar la llamada a la Pasarela | Consultar el reembolso real: `GET /v1/payments/{id}/refunds` en Mercado Pago (o su panel) | `{id}` = `charge_intent.external_reference` | Se reembolsa el cobro original con `amount = 990000.00`; header `X-Idempotency-Key` igual a la clave de la intención (contrato §2, RF-002). | [ ] | El sistema envía siempre `amount`, también en reembolso total (contrato §2). El header `X-Idempotency-Key` lo verifica `MercadoPagoRefundAdapterTest`; en la BD, `refund_intent.idempotency_key` es el UUID v4 que se envía. |
| 7 | Verificar ausencia de descuentos | Comparar el `amount` de la intención y de la llamada con el valor total registrado | `990000.00` | El sistema no calcula ni aplica costos transaccionales; no envía un monto menor (RF-012, CE-002). | [ ] | |
| 8 | Verificar el estado pendiente | `SELECT status FROM refund_intent;` y `SELECT count(*) FROM refund_record;` | N/A | La intención sigue en curso y **no** hay `refund_record`: el reembolso se considera pendiente hasta la confirmación externa (RF-006, RF-007). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin valor total registrado | Publicar `CANCELLED_FLEXIBLE` para una reserva sin `charge_record` ni cálculo previo | reserva sin montos | Fallo en `operational_failure`, respuesta interna `FAILED` y **sin** llamada a la Pasarela; no se calcula un monto parcial (RF-010, caso extremo del SPEC). | [ ] | |
| 2 | Cobro original no aprobado | Publicar la cancelación con cobro en `REJECTED`/`EXPIRED` | `charge_intent.status = 'REJECTED'` | No se registra reembolso ni se contacta a la Pasarela (RF-006 "según el estado del cobro original"). | [ ] | |
| 3 | Misma operación repetida | Publicar dos veces el mismo estado de cancelación | mismo `status_changed_at` | Una sola intención y un solo comando: `UNIQUE(reservation_id, operation_key)` (D-UC09-13). Si la cancelación y una disputa (UC08) pidieran el reembolso del mismo depósito, solo la primera crea la intención y la segunda registra `duplicate_deposit_outcome` en `operational_failure`, salvo que la primera haya terminado `REJECTED`, `CANCELLED` o `EXPIRED` (entonces la segunda es un reintento legítimo) (RF-006, RNF-003). | [ ] | |
| 4 | `400` de validación de la Pasarela | Simular `400` no reintentable | N/A | La intención queda en `REJECTED` para revisión manual, sin `refund_record` y sin reintentos (contrato §5, RNF-003). | [ ] | |
| 5 | `503` o timeout de la Pasarela | Simular `503`/timeout en el worker | N/A | Intención en `COMMUNICATION_ERROR`; 5 intentos con backoff base 1 s y la misma `X-Idempotency-Key`; al agotarlos, DLQ (RNF-003, README §4.4). | [ ] | |
| 6 | Mensaje con monto o motivo | Agregar `amount` o `reason` al evento de UC07 | campos extra | El monto y el motivo no se usan; el importe sale del `charge_record` (RF-001, CE-002). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Se envía un único `amount` que incluye alquiler, seguro y depósito; no hay una llamada por concepto (RF-002).
* **EC-002** : Precisión interna de 4 decimales y redondeo final `HALF_UP` a 2 decimales hacia la Pasarela (RNF-002, README §3.1).
* **EC-003** : `CANCELLED_FLEXIBLE` y `CANCELLED_BY_HOST` producen el mismo importe pero distinto `trigger` y clave idempotente.
* **EC-004** : La operación no se marca como completada hasta que la Pasarela confirme; el registro inmutable pertenece a UC09 HU3 (RF-007, RF-009).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
