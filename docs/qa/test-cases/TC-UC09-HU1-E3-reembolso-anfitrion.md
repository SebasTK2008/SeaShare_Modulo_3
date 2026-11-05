### Test Case Specification: Reembolsos · HU1 · E3 Reembolso por cancelación del anfitrión
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/009-reembolsar-dinero-arrendatario/spec.md](../../../docs/features/009-reembolsar-dinero-arrendatario/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC09-HU1-E3
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una cancelación por el anfitrión, el sistema solicita el reembolso del 100 % del valor total pagado registrado (alquiler, seguro y depósito) al Arrendatario, sin penalización, sin retención y sin descuentos por costos transaccionales.

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
* El resultado externo del reembolso se cubre en UC09 HU3.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 y TC-UC05-HU2-E1 deben haber pasado** (cobro capturado con desglose).
* **TC-UC09-HU1-E1 debe haber pasado** (mismo flujo de solicitud con otro `trigger`).
* Worker de reembolso activo sobre `finance.refund-command.v1`.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con `rental_amount = 900000.00`, `insurance_amount = 60000.00`, `deposit_amount = 30000.00`, `amount = 990000.00` y depósito en `HELD` (ilustrativos; ajustar a la versión vigente del entorno).
* **Environment** : staging local (`docker compose --profile app`), `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Reembolso del 100 % al Arrendatario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el valor total registrado | `SELECT amount, rental_amount, insurance_amount, deposit_amount FROM charge_record WHERE reservation_id = 'b7d0e2a1-…';` | `990000.00` total | El valor total y su desglose están congelados en el registro del cobro (RF-002). | [ ] | |
| 2 | Publicar la cancelación por anfitrión | Publicar en `seashare.reservations` / `reservation.status.changed` | `{"reservation_id":"b7d0e2a1-…","status":"CANCELLED_BY_HOST","status_changed_at":"2026-12-21T09:00:00Z"}` | `ack`; UC07 delega el reembolso en UC09 (RF-001). | [ ] | |
| 3 | Verificar la intención de reembolso | `SELECT trigger, scope, amount, status, idempotency_key FROM refund_intent;` | `trigger = 'CANCELLED_BY_HOST'` | `scope = 'FULL'`, `amount = 990000.00`, clave UUID v4 (D-UC09-12) (RF-002, RF-006). | [ ] | |
| 4 | Verificar que no se aplica penalización | Comparar el `amount` con el valor total pagado | `990000.00` | No hay reducción por cancelación del anfitrión ni retención del depósito (RF-002, CE-002). | [ ] | |
| 5 | Verificar el desenlace del depósito | `SELECT disposition FROM deposit_disposition;` | N/A | `disposition = 'REFUND_REQUESTED'`; el depósito se devuelve junto con el resto del valor (RF-002). | [ ] | |
| 6 | Verificar la llamada a la Pasarela | Consultar el reembolso real: `GET /v1/payments/{id}/refunds` en Mercado Pago (o su panel) | `amount = 990000.00` | Reembolso único sobre el cobro original, con `X-Idempotency-Key` de la intención (contrato §2). | [ ] | El header `X-Idempotency-Key` lo verifica `MercadoPagoRefundAdapterTest`; en la BD, `refund_intent.idempotency_key` es el UUID v4 que se envía. |
| 7 | Verificar estado pendiente | `SELECT status FROM refund_intent;` y `SELECT count(*) FROM refund_record;` | N/A | Intención en curso y sin `refund_record` hasta la confirmación externa (RF-006, RF-007). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin valor total registrado | Publicar `CANCELLED_BY_HOST` sin cálculo previo | reserva sin montos | `operational_failure` y `FAILED`; sin llamada a la Pasarela ni monto asumido (RF-010, caso extremo del SPEC). | [ ] | |
| 2 | Cobro original no aprobado | Publicar la cancelación con cobro en `REJECTED`/`EXPIRED` | `charge_intent.status = 'EXPIRED'` | No se solicita reembolso (RF-006 "según el estado del cobro original"). | [ ] | |
| 3 | Cancelación repetida | Publicar dos veces el mismo estado | mismo `status_changed_at` | Una sola intención y un solo comando (idempotencia, RF-006). | [ ] | |
| 4 | Cambio de estado posterior | Publicar `CANCELLED_FLEXIBLE` después de `CANCELLED_BY_HOST` | distinto `status_changed_at` | No se duplica el reembolso; se registra la inconsistencia de identidad de operación (RNF-003). | [ ] | |
| 5 | `400` de validación de la Pasarela | Simular `400` no reintentable | N/A | Intención en `REJECTED` para revisión manual, sin `refund_record` (contrato §5). | [ ] | |
| 6 | `503` o timeout | Simular `503`/timeout en el worker | N/A | `COMMUNICATION_ERROR` con reintentos y misma clave; DLQ al agotar los 5 intentos (RNF-003). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El 100 % incluye alquiler, seguro y depósito en un único `amount` (RF-002).
* **EC-002** : El `trigger` y la clave idempotente distinguen esta cancelación de una flexible, aunque el importe coincida (RF-006).
* **EC-003** : No se descuenta ningún costo transaccional del monto solicitado; solo se registra el que reporte la Pasarela (RF-012).
* **EC-004** : Si el cobro original no fue capturado, no hay "liberación" posible y se registra el fallo sin inventar un monto (caso extremo del SPEC).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
