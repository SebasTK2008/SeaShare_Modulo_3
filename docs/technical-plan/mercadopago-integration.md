# Integración con Mercado Pago: vista única

Este documento reúne en un solo lugar **cómo está integrado Mercado Pago en el Módulo 3**, qué
configuración usa y qué brechas de código quedan abiertas frente a la documentación. No sustituye a
los contratos: los indexa.

| Campo | Valor |
|---|---|
| Aplicación | `SeaShareM3` (App ID `2901490065617188`) |
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

UC07/UC08 ──> settlement_intent ──> libro interno (approved, sim-settlement-<operationKey>)
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

- **Clave enviada a Mercado Pago**: `X-Idempotency-Key` con **UUID v4** por intento, persistido en
  `charge_intent.idempotency_key` / `refund_intent.idempotency_key` / `settlement_intent.idempotency_key`.
- **Deduplicación de negocio**: la aplica **UC07/UC08** sobre su registro de eventos, no los
  servicios de cobro o reembolso.
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

> `reconciliation-interval` y los umbrales del *circuit breaker* **no** están declarados en
> `META-INF/additional-spring-configuration-metadata.json`: no aparecen en el autocompletado del IDE
> ni en la tabla de variables del README. Se ajustan por `application.properties` o por entorno con
> el nombre completo.

---

## 6. Estados

| Estado en Mercado Pago | `charge_intent` | `refund_intent` |
|---|---|---|
| `approved` | `CAPTURED` | `COMPLETED` |
| `in_process` | `IN_PROCESS` | `IN_PROCESS` |
| `pending` | `IN_PROCESS` *(ver G-02)* | `IN_PROCESS` |
| `rejected` | `REJECTED` | `REJECTED` |
| `cancelled` | `CANCELLED` | `CANCELLED` |
| `expired` | `EXPIRED` | `EXPIRED` |
| `refunded` / `charged_back` / `in_mediation` | Sin cambio *(ver G-03)* | — |
| Desconocido | Sin cambio + `operational_failure` | Sin cambio + `operational_failure` |

---

## 7. Brechas de código pendientes

La documentación describe el comportamiento **objetivo**. Las siguientes brechas son de **código** y
deben resolverse en una sesión de implementación, no rebajando la documentación.

| ID | Brecha | Evidencia | Severidad | ¿Bloquea sandbox? |
|---|---|---|---|---|
| **G-01** | No existe stub de pasarela para los TC que citan "request journal del simulador" | `wiremock/` solo contiene `mappings/fleet-base-rates.json` | Media | No (se cubre con modo B o con la suite automática) |
| **G-02** | `pending` no se mapea en UC05; se registra `operational_failure` y la intención no avanza | `RegisterChargeResultService.java:145-151` | **Alta** | **Sí**: un pago pendiente queda estancado |
| **G-03** | `refunded` / `charged_back` / `in_mediation` registran `operational_failure` en lugar de ser no-op | `RegisterChargeResultService.java:80-83,151` | Media | No, pero contamina la revisión manual |
| **G-04** | Reembolso sin intención local se ignora en silencio | `RefundWebhookHandler.java:64-67` | **Alta** | **Sí**: rompe la trazabilidad de B-06 |
| **G-05** | `404` de `GET /refunds` devuelve lista vacía sin registrar el evento | `MercadoPagoRefundAdapter.java:149-150` | Media | No, pero oculta errores de configuración |
| **G-06** | El webhook no valida `Content-Type` | `GatewayWebhookController.java:56-62` | Baja | No |
| **G-07** | `MercadoPagoPaymentResponse.refunds` se lee y no se usa | `MercadoPagoPaymentResponse.java:20-21` | Baja | No |
| **G-08** | Faltan `reconciliation-interval` y `circuit-breaker.*` en la metadata de configuración | `additional-spring-configuration-metadata.json` | Baja | No |

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
