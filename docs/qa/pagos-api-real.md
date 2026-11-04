# Pruebas de pagos: simuladas y contra la API real de Mercado Pago

Este documento define los **dos modos obligatorios** de prueba para las operaciones de pago del
Módulo 3. No son alternativas: el modo simulado protege la regresión automática y el modo real
demuestra que la integración funciona contra Mercado Pago.

| Modo | Propósito | Entorno | Cuándo se ejecuta | ¿Bloquea? |
|---|---|---|---|---|
| **A · Simulado** | Regresión de lógica, contratos, webhook, colas y errores sin red externa | JUnit + WireMock + simulador de QA + Testcontainers | En cada `.\mvnw.cmd clean verify` y en la ejecución funcional de los TC | Sí: el build debe quedar verde |
| **B · API real (sandbox)** | Verificar la integración real de cobro, webhook `payment` y reembolso | Mercado Pago **MCO sandbox** con credenciales `TEST-*` y usuarios de prueba | Antes de aprobar `release/0.1.0`; se ejecuta en las fases de integración real y E2E (F3/F4) | Sí: sin estas evidencias el release de pagos no se aprueba |

**Alcance del modo B:** UC05 (cobro) y UC09 (reembolso). **UC10 no aplica**: su liquidación es un
libro interno simulado y no llama a Mercado Pago (D-UC10-18). UC06 solo consulta datos ya
persistidos y no contacta a la pasarela.

## 1. Modo A · Simulado (gate de CI)

- **UC05/UC09:** pruebas unitarias, de aplicación, de contrato con WireMock, de webhook con MockMvc,
  de persistencia con Testcontainers y de mensajería con RabbitMQ. Los TC de `docs/qa/test-cases/`
  que indican «simulador» o «request journal del simulador» pertenecen a este modo.
- **UC10:** pruebas del libro interno, persistencia y conciliación; no hay HTTP ni journal de
  pasarela.
- **Reglas:** no requiere credenciales, no sale a Internet y no debe fallar por indisponibilidad de
  Mercado Pago. Un fallo aquí bloquea el merge.
- **Comando de referencia:** `.\mvnw.cmd clean verify` (Surefire; el proyecto no configura Failsafe).

## 2. Modo B · API real de Mercado Pago (gate de release)

### 2.1 Condiciones de ejecución

- Aplicación arrancada con `.env` local (gitignored) y credenciales **TEST** de la app
  `SeaShareFinanzasDemo` (site MCO). Nunca usar credenciales de producción ni versionarlas.
- `GATEWAY_WEBHOOK_SECRET` completo, copiado del panel de Mercado Pago. Sin él el webhook responde
  `401 UNAUTHENTICATED` y el modo B no es válido.
- Callback público HTTPS registrado en Mercado Pago, topic `payment`. Si el túnel cambia, actualizar
  el callback antes de ejecutar.
- Usuarios de prueba MCO: vendedor con fondos y comprador. Anotar sus IDs en la evidencia.
- Tokenización de la tarjeta de prueba mediante Bricks o el flujo de tokenización de Mercado Pago.
  El sistema solo recibe el token; nunca PAN, CVV ni fecha de vencimiento.

### 2.2 Escenarios mínimos obligatorios

| ID | Escenario | Acción real | Criterio de aceptación |
|---|---|---|---|
| **B-01** | Cobro aprobado | `POST /v1/payments` con token de tarjeta de prueba aprobada | `status = approved`, `external_reference` = reserva, monto único correcto, `X-Idempotency-Key` UUID v4 persistido, `ChargeIntent` en `CAPTURED` y `ChargeRecord` creado una sola vez |
| **B-02** | Cobro rechazado | `POST /v1/payments` con tarjeta/caso de rechazo de prueba | `ChargeIntent` en `REJECTED` (o estado terminal correspondiente), sin `ChargeRecord`, sin reintento infinito ni éxito asumido |
| **B-03** | Webhook `payment` real | Mercado Pago entrega la notificación al callback público | Firma válida, `200 OK`, notificación persistida antes del acuse, `GET /v1/payments/{id}` consultado y resultado aplicado de forma idempotente |
| **B-04** | Reembolso parcial | `POST /v1/payments/{payment_id}/refunds` con `amount` del depósito | Reembolso real con `id` externo; `refund_intent` `COMPLETED`; `released_amount` correcto; un solo `refund_record` |
| **B-05** | Reembolso total | `POST /v1/payments/{payment_id}/refunds` por el total | Reembolso real; intención y registro únicos; sin duplicados si se repite la notificación |
| **B-06** | Webhook de reembolso | Notificación `payment` posterior al reembolso | El worker consulta `GET /v1/payments/{id}/refunds`, asocia por `refund.id == external_reference` y no altera reembolsos ajenos |
| **B-07** | Idempotencia | Repetir el mismo cobro/reembolso con la misma `X-Idempotency-Key` | Mercado Pago devuelve la misma operación; no se crea un segundo cobro, reembolso, intención ni registro |
| **B-08** | Datos sensibles | Inspeccionar payloads, logs y BD tras B-01…B-07 | Solo se envía/persiste el token; no aparecen PAN, CVV ni vencimiento |

### 2.3 Evidencia exigida

Registrar en el reporte de release:

- fecha y entorno; app ID y site (`MCO`);
- IDs de los usuarios de prueba (vendedor/comprador);
- `payment_id`, `refund_id`, `x-request-id` del webhook y estado final en Mercado Pago;
- filas resultantes en `charge_intent`, `charge_record`, `refund_intent`, `refund_record` y
  `outbox_message`;
- resultado de `quality_evaluation` y `notifications_history` cuando se usen para diagnosticar;
- captura o extracto del panel de Mercado Pago que confirme el cobro/reembolso y la entrega del
  webhook.

Guardar la evidencia sin credenciales ni datos de tarjeta.

### 2.4 Criterio de salida

Todos los escenarios B-01…B-08 deben pasar. Un fallo, una credencial ausente o un webhook no
entregado **bloquea la aprobación del release**; no se sustituye por el modo simulado. Si Mercado
Pago sandbox no está disponible, el resultado es «bloqueado», no «aprobado con simulador».

## 3. Estado actual y automatización

- **Modo A:** implementado y verde en `.\mvnw.cmd clean verify` (WireMock para UC05/UC09, webhook
  MockMvc, persistencia Testcontainers y libro simulado de UC10).
- **Modo B:** especificado en este documento; **pendiente de ejecución** en las fases F3/F4. Las
  pruebas reales no forman parte de `mvn verify` para no depender de Internet ni de credenciales.
  Si en F3 se añade una suite Java *live*, debe ser opt-in (variable de entorno o perfil) y quedar
  excluida por defecto del build.
- Los TC de `docs/qa/test-cases/` siguen siendo válidos para el modo A. La aprobación de release
  exige además este checklist B.
