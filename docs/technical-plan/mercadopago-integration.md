# Integración con Mercado Pago: vista única

Este documento reúne en un solo lugar **cómo está integrado Mercado Pago en el Módulo 3**, qué
configuración usa y qué brechas de código quedan abiertas frente a la documentación. No sustituye a
los contratos: los indexa.

| Campo | Valor |
|---|---|
| Aplicación | La aplicación de Mercado Pago del equipo (`<APP_ID>`): cada equipo usa la suya, no hay una compartida |
| Producto | Checkout API |
| API | Payments API (`/v1/payments`) |
| Site | MCO (Colombia) |
| Casos de uso | UC05 (cobro), UC09 (reembolso), UC10 (liquidación interna) |
| Ambiente | Sandbox con credenciales `TEST-*` |
| ¿Pagos reales? | No. Dinero de prueba del sandbox |

---

## 1. Componentes

| Pieza | Clase / archivo | Rol |
|---|---|---|
| Adaptador de cobro | `infrastructure/adapter/out/gateway/MercadoPagoChargeAdapter.java` | `POST /v1/payments`, `GET /v1/payments/{id}` |
| Adaptador de reembolso | `infrastructure/adapter/out/gateway/MercadoPagoRefundAdapter.java` | `POST /v1/payments/{id}/refunds`, `GET .../refunds`, `GET .../refunds/{id}` |
| Libro de liquidación | `infrastructure/adapter/out/gateway/SimulatedSettlementGatewayAdapter.java` | Registro interno; **no** contacta Mercado Pago |
| Webhook | `infrastructure/adapter/in/webhook/GatewayWebhookController.java` | `POST /api/v1/webhook/gateway` |
| Verificador de firma | `infrastructure/adapter/in/webhook/MercadoPagoSignatureVerifier.java` | HMAC-SHA256 sobre `x-signature` |
| Despachador | `application/service/GatewayWebhookDispatcherService.java` | Persiste en outbox y responde `200` |
| Worker de notificaciones | `infrastructure/adapter/in/messaging/GatewayNotificationListener.java` | Consume `finance.gateway-notification.v1` |
| Worker de cobro | `application/service/SendChargeToGatewayService.java` | Envía el cobro desde la cola interna |
| Conciliación | `infrastructure/adapter/in/scheduler/GatewayReconciliationJob.java` | Job periódico con ShedLock |

---

## 2. Flujo

```text
UC07 (PENDING) ──> ProcessChargeService ──> charge_intent
                                      └──> outbox ──> finance.charge-command.v1
                                                          └──> SendChargeToGatewayService
                                                                   └──> POST /v1/payments
                                                                            └──> charge_intent.external_reference = payment_id

Mercado Pago ──> POST /api/v1/webhook/gateway?data.id=<payment_id>&type=payment
                     └──> verifica x-signature
                          └──> outbox ──> finance.gateway-notification.v1
                                            ├──> UC05: GET /v1/payments/{id}
                                            └──> UC09: GET /v1/payments/{id}/refunds

UC09 (cancelación/disputa) ──> refund_intent ──> POST /v1/payments/{payment_id}/refunds

UC07/UC08 ──> settlement_intent ──> libro interno (approved, sim-settlement-<idempotency_key>)
```

Puntos clave:

- **Un solo topic**: `payment`. UC05 y UC09 comparten el mismo evento.
- **El webhook se configura a nivel de aplicación**; el pago no envía `notification_url`.
- **La firma es obligatoria**: sin `GATEWAY_WEBHOOK_SECRET` válido, el webhook responde `401`.
- **La liquidación de UC10 no es una transferencia**: es un asiento contable interno.

---

## 3. Endpoints y topic

| Operación | Método y ruta | Contrato |
|---|---|---|
| Crear cobro | `POST /v1/payments` | [`pasarela-comando-cobro.md`](contracts/external/pasarela-comando-cobro.md) |
| Consultar pago | `GET /v1/payments/{id}` | idem |
| Reembolsar | `POST /v1/payments/{id}/refunds` | [`pasarela-comando-reembolso.md`](contracts/external/pasarela-comando-reembolso.md) |
| Consultar reembolsos | `GET /v1/payments/{id}/refunds` | idem |
| Consultar reembolso | `GET /v1/payments/{id}/refunds/{refund_id}` | idem |
| Recibir notificación | `POST /api/v1/webhook/gateway` | [`pasarela-webhook-resultados.md`](contracts/external/pasarela-webhook-resultados.md) |
| Tokenizar (frontend) | Fuera de este repositorio | [`frontend-tokenizacion.md`](contracts/external/frontend-tokenizacion.md) |

---

## 4. Idempotencia y conciliación

Hay dos claves distintas y no se intercambian (general-plan D-37):

- **`idempotency_key` (clave de la pasarela)**: `X-Idempotency-Key` con **UUID v4** por intento,
  generado una vez al crear la intención, persistido en `charge_intent.idempotency_key` /
  `refund_intent.idempotency_key` / `settlement_intent.idempotency_key` y reutilizado en los
  reintentos del worker. Mercado Pago exige una clave nueva por intento de transacción (1 a 128
  caracteres); reutilizarla con otro cuerpo devuelve `409 idempotency_key_already_used`. No es
  la clave de deduplicación interna.
- **`operation_key` (identidad de negocio)**: la clave de operación del comando (UC07:
  `reservationId:status[:statusChangedAt]`; UC08: `event_key` de la disputa), persistida en las
  tres intenciones con `UNIQUE(reservation_id, operation_key)`. Los servicios de cobro, reembolso y
  liquidación la consultan antes de crear una intención: una reentrega del mismo mensaje no crea
  una segunda. La constraint es la red de seguridad ante una carrera: el segundo hilo falla en su
  transacción, el mensaje se reintenta y la consulta previa converge.
- **Doble camino sobre el depósito**: UC07 y UC08 pueden reclamar el mismo desenlace del depósito
  con `operation_key` distintos. Por eso UC09/UC10 solo crean la intención cuando su solicitud aplicó
  la transición `HELD -> REFUND_REQUESTED/SETTLEMENT_REQUESTED`; si otra operación ya la aplicó,
  devuelven la intención existente y registran `duplicate_deposit_outcome` en `operational_failure`.
- **Deduplicación del evento de entrada**: la sigue aplicando **UC07/UC08** sobre su registro de
  eventos (`reservation_status_log`, `dispute_event_log`).
- **Webhook duplicado**: se asocia por `external_reference` y el registro ya creado no se altera.
- **Conciliación**: `GatewayReconciliationJob` corre cada `seashare.gateway.reconciliation-interval`
  (5 min por defecto) y consulta el estado real de las intenciones abiertas.

---

## 5. Configuración

### 5.1 Variables de entorno

| Variable | Obligatoria | Uso |
|---|---|---|
| `GATEWAY_BASE_URL` | Sí (dev tiene default) | `https://api.mercadopago.com` |
| `GATEWAY_ACCESS_TOKEN` | Sí | `access_token` de prueba (`TEST-*`) |
| `GATEWAY_WEBHOOK_SECRET` | Sí | Firma secreta del webhook |
| `GATEWAY_SETTLEMENT_MODE` | Sí | Solo `simulated` está implementado |

### 5.2 Propiedades internas (`seashare.gateway.*`)

| Propiedad | Valor por defecto | Efecto |
|---|---|---|
| `base-url` | sin default | URL base de la API |
| `access-token` | vacío | Credencial del backend |
| `webhook-secret` | vacío | Secreto HMAC |
| `settlement-mode` | `simulated` | Único valor admitido |
| `connect-timeout` | `10s` | Timeout de conexión |
| `read-timeout` | `10s` | Timeout de lectura |
| `circuit-breaker.failure-rate-threshold` | `50` | Umbral de fallos |
| `circuit-breaker.minimum-number-of-calls` | `5` | Llamadas mínimas |
| `circuit-breaker.sliding-window-size` | `10` | Ventana |
| `circuit-breaker.wait-duration-in-open-state` | `10s` | Tiempo abierto |
| `circuit-breaker.permitted-number-of-calls-in-half-open-state` | `1` | Llamadas en half-open |
| `reconciliation-interval` | `5m` | Cadencia del job de conciliación |

> `reconciliation-interval` y los umbrales del *circuit breaker* están declarados en
> `META-INF/additional-spring-configuration-metadata.json` (autocompletado del IDE), pero **no** se
> exponen como variables de entorno en el README: se ajustan por `application.properties` o por
> entorno con el nombre completo.

---

## 6. Estados

| Estado en Mercado Pago | `charge_intent` | `refund_intent` |
|---|---|---|
| `approved` | `CAPTURED` | `COMPLETED` |
| `in_process` | `IN_PROCESS` | `IN_PROCESS` |
| `pending` | `IN_PROCESS` | `IN_PROCESS` |
| `rejected` | `REJECTED` | `REJECTED` |
| `cancelled` | `CANCELLED` | `CANCELLED` |
| `expired` | `EXPIRED` | `EXPIRED` |
| `refunded` / `charged_back` / `in_mediation` | Sin cambio (no-op) | — |
| Desconocido | Sin cambio + `operational_failure` | Sin cambio + `operational_failure` |

---

## 7. Brechas de código

La documentación describe el comportamiento **objetivo**. El código se alineó con ella el
**2026-11-04**; una brecha se descartó y el resto está cerrada.

| ID | Brecha | Evidencia | Severidad | Estado |
|---|---|---|---|---|
| **G-01** | No existe un stub de pasarela para los TC que citan "request journal del simulador" | `wiremock/` solo contiene `mappings/fleet-base-rates.json` | Media | **Descartada** (2026-11-05): no se construye un simulador de Mercado Pago. El modo A es la suite automática (WireMock embebido en JUnit) y los TC se ejecutan contra el sandbox real; los TC ya no citan el simulador |
| **G-02** | `pending` no se mapeaba en UC05; se registraba `operational_failure` y la intención no avanzaba | `RegisterChargeResultService.toStatus` | Alta | **Cerrada** (2026-11-04) |
| **G-03** | `refunded` / `charged_back` / `in_mediation` registraban `operational_failure` en lugar de ser no-op | `RegisterChargeResultService.IGNORED_STATUSES` | Media | **Cerrada** (2026-11-04) |
| **G-04** | Reembolso sin intención local se ignoraba en silencio | `RefundWebhookHandler.handle` | Alta | **Cerrada** (2026-11-04) |
| **G-05** | `404` de `GET /refunds` devolvía lista vacía sin registrar el evento | `MercadoPagoRefundAdapter.executeFetchRefunds` | Media | **Cerrada** (2026-11-04) |
| **G-06** | El webhook no validaba `Content-Type` | `GatewayWebhookController.isJson` | Baja | **Cerrada** (2026-11-04) |
| **G-07** | `MercadoPagoPaymentResponse.refunds` se leía y no se usaba | `MercadoPagoPaymentResponse` | Baja | **Cerrada** (2026-11-04) |
| **G-08** | Faltaban `reconciliation-interval` y `circuit-breaker.*` en la metadata de configuración | `additional-spring-configuration-metadata.json` | Baja | **Cerrada** (2026-11-04) |

---

## 8. Documentos relacionados

- Contratos: [`contracts/external/pasarela-comando-cobro.md`](contracts/external/pasarela-comando-cobro.md),
  [`pasarela-comando-reembolso.md`](contracts/external/pasarela-comando-reembolso.md),
  [`pasarela-comando-liquidacion.md`](contracts/external/pasarela-comando-liquidacion.md),
  [`pasarela-webhook-resultados.md`](contracts/external/pasarela-webhook-resultados.md),
  [`frontend-tokenizacion.md`](contracts/external/frontend-tokenizacion.md).
- Pruebas: [`docs/qa/pagos-api-real.md`](../qa/pagos-api-real.md) (modos A/B y escenarios B-01…B-10),
  [`docs/qa/runbook-sandbox-mercadopago.md`](../qa/runbook-sandbox-mercadopago.md) (procedimiento).
- Arquitectura: [`general-plan.md`](general-plan.md).
