# Runbook: integración real con Mercado Pago en sandbox

Este documento es el **procedimiento operativo** para ejecutar la integración real de Mercado Pago
(modo B de `docs/qa/pagos-api-real.md`). No sustituye al checklist de escenarios: lo pone en orden.

**Objetivo:** cobrar y reembolsar de verdad contra el sandbox de Mercado Pago (dinero de prueba),
recibiendo webhooks reales. **No** se usan credenciales de producción.

---

## 0. Antes de empezar

| Requisito | Cómo se comprueba |
|---|---|
| Docker y JDK 25 (`java.version` del `pom.xml`) | `docker --version`, `java -version` |
| Repositorio en la rama de trabajo | `git branch --show-current` |
| Suite automática en verde | `.\mvnw.cmd clean verify` |
| Una cuenta de Mercado Pago de desarrollador | Acceso a [Tus integraciones](https://www.mercadopago.com/developers/panel/app) |
| Un túnel HTTPS disponible | Cualquier herramienta que exponga `localhost:8080` por HTTPS público |

> **Aviso:** el archivo `.env` local no se versiona. Si tu `.env` define
> `GATEWAY_BASE_URL=http://fleet-stub:8080`, **las llamadas irán al stub de Flota y no a Mercado
> Pago**. Bórrala o ajústala a `https://api.mercadopago.com`.

---

## 1. Aplicación y credenciales de prueba

1. Entra en [Tus integraciones](https://www.mercadopago.com/developers/panel/app) y selecciona la
   aplicación del equipo (`<APP_ID>`; cada equipo usa su propia aplicación de Mercado Pago). Si hay que recrearla: producto
   **Checkout API**, API **Payments** (no Orders); el *site* lo toma Mercado Pago de tu cuenta (MCO).
2. En la aplicación, abre **Credenciales de prueba** y copia:
   - **`access_token`** de prueba (prefijo `TEST-`) → `GATEWAY_ACCESS_TOKEN`.
   - **`public_key`** → se la pasas al repositorio de frontend (nunca al backend).
3. En **Webhooks → Configurar notificaciones** (modo **Prueba**):
   - URL: `https://<tu-tunel>/api/v1/webhook/gateway`
   - Evento: **Pagos** (topic `payment`).
   - Guarda y copia la **firma secreta** → `GATEWAY_WEBHOOK_SECRET`.

> Sin `GATEWAY_WEBHOOK_SECRET` el webhook responde `401 UNAUTHENTICATED` y el modo B no es válido.
> Si rotas la firma en el panel, actualiza la variable y reinicia la aplicación.

4. Crea los **usuarios de prueba** (vendedor con fondos y comprador) desde el panel de Mercado Pago.
   Anota sus IDs: son parte de la evidencia.

---

## 2. Configurar el entorno local

Copia `.env.example` a `.env` y completa:

```dotenv
# Pasarela (sandbox)
GATEWAY_BASE_URL=https://api.mercadopago.com
GATEWAY_ACCESS_TOKEN=TEST-...
GATEWAY_WEBHOOK_SECRET=...
GATEWAY_SETTLEMENT_MODE=simulated
```

Comprueba que NO quede `GATEWAY_BASE_URL=http://fleet-stub:8080`: esa URL es del stub de Flota y
desviaría todos los cobros. El perfil `dev` (por defecto) ya apunta a Mercado Pago; solo el access token
de prueba y el secreto del webhook salen del `.env`. (El perfil `prod` no arranca si esas variables
faltan o están vacías.)

Levanta el stack:

```powershell
docker compose --profile app up --build -d
curl.exe -fsS http://localhost:8080/actuator/health
```

> El PostgreSQL del compose se publica en el puerto **5433** (`POSTGRES_HOST_PORT`), no en 5432.

---

## 3. Exponer el webhook por HTTPS

Mercado Pago exige una URL **HTTPS pública**; `localhost` no sirve.

1. Levanta tu túnel preferido apuntando al puerto `8080`. Este documento no impone proveedor.
2. La URL pública debe terminar en `/api/v1/webhook/gateway`.
3. Regístrala en el panel (paso 1.5) y verifica que responde:
   - Sin firma válida debe devolver `401 UNAUTHENTICATED` (es la señal de que el endpoint está vivo).
4. Si el túnel cambia de host, **actualiza la URL en el panel antes de ejecutar** los escenarios.

La integración **no** envía `notification_url` en el pago: la recepción depende por completo de esta
configuración de la aplicación (`pasarela-webhook-resultados.md` §2).

### 3.1 Enviar un webhook firmado a mano

Para ejercitar el endpoint sin esperar a Mercado Pago (por ejemplo en los TC de firma inválida o de
reembolsos), se firma la notificación con el secreto del webhook, con el mismo esquema que valida la
aplicación (`MercadoPagoSignatureVerifier`): HMAC-SHA256 de `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`.

```bash
ID=1234567890; REQ=$(openssl rand -hex 16); TS=$(date +%s)
SIG=$(printf 'id:%s;request-id:%s;ts:%s;' "$ID" "$REQ" "$TS" | openssl dgst -sha256 -hmac "$GATEWAY_WEBHOOK_SECRET" | sed 's/^.* //')
curl -i -X POST "http://localhost:8080/api/v1/webhook/gateway?data.id=$ID&type=payment" \
  -H 'Content-Type: application/json' -H "x-request-id: $REQ" -H "x-signature: ts=$TS,v1=$SIG" \
  -d '{"action":"payment.updated","type":"payment","data":{"id":"'"$ID"'"}}'
```

Responde `200` con la firma correcta y `401 UNAUTHENTICATED` con una incorrecta. Usa un `data.id` real
(el `payment_id` del cobro) si quieres que el worker lo procese.

---

## 4. Obtener un `card_token`

El sistema no tokeniza: recibe el token ya generado. El contrato con el repositorio de frontend está
en [`contracts/external/frontend-tokenizacion.md`](../technical-plan/contracts/external/frontend-tokenizacion.md).

Para ejecutar el modo B necesitas:

| Dato | De dónde sale |
|---|---|
| `payment_token_ref` | Tokenización con la `public_key` (CardForm/Brick) contra el sandbox |
| `payment_method_type` | Marca devuelta por Mercado Pago (`VISA`, `MASTER`, …) o tipo genérico (`CREDIT_CARD`) |
| `payment_metadata.payer_email` | Correo del comprador de prueba |
| `payment_metadata.last_four` | Últimos 4 dígitos de la tarjeta de prueba |

Selecciona **una tarjeta de prueba aprobada** y **una de rechazo** según la documentación de
Mercado Pago para MCO. No versiones números de tarjeta ni tokens.

El resultado del pago lo decide el **nombre del titular** que se escribe al tokenizar la tarjeta de
prueba (documento `123456789` para `APRO` y `OTHE`), no la tarjeta:

| Resultado deseado | Titular | Estado esperado del pago |
|---|---|---|
| Aprobado | `APRO` | `approved` |
| Rechazo por error general | `OTHE` | `rejected` |
| Pendiente de pago | `CONT` | `pending` / `in_process` |
| Rechazo con validación para autorizar | `CALL` | `rejected` |
| Rechazo por fondos insuficientes | `FUND` | `rejected` |
| Rechazo por código de seguridad inválido | `SECU` | `rejected` |
| Rechazo por fecha de vencimiento | `EXPI` | `rejected` |
| Rechazo por error de formulario | `FORM` | `rejected` |

> **No existe un titular que produzca `expired`.** `EXPI` genera un rechazo por fecha de vencimiento
> de la tarjeta, no el estado `expired` de un pago. El escenario de pago expirado
> (`TC-UC05-HU2-E3`) solo se reproduce en modo A.

Los **reembolsos** exigen saldo disponible en la cuenta vendedora de prueba (de lo contrario
Mercado Pago rechaza la operación). Si hace falta, acredita dinero de prueba al usuario vendedor
desde el panel o con la herramienta `add_money_test_user` del MCP de Mercado Pago. El plazo de
reembolso es de 180 días desde la aprobación del pago.

---

## 5. Sembrar el escenario

1. Registra la información de la reserva y calcula su valor total (UC03/UC04) siguiendo el TC
   correspondiente de `docs/qa/test-cases/`.
2. Publica el estado `PENDING` de UC07 con el token y los metadatos del paso 4.
3. Verifica que se creó la intención:

```sql
SELECT id, status, amount, payment_token_ref IS NOT NULL AS tiene_token,
       payer_email, idempotency_key, external_reference
FROM charge_intent
WHERE reservation_id = '<reservation_id>';
```

---

## 6. Ejecutar los escenarios

Ejecuta en orden los escenarios **B-01…B-10** de `docs/qa/pagos-api-real.md` §2.2. Para cada uno:

1. Realiza la acción.
2. Registra la respuesta de Mercado Pago.
3. Verifica el efecto en base de datos y en logs.
4. Guarda la evidencia (sección 8).

**B-09 y B-10 son bloqueantes de decisión**, no solo de prueba:

- **B-09** decide si `payment_method_id` puede omitirse. Si Mercado Pago responde `400` con código
  `4001`, la omisión no es válida y el repositorio de frontend debe aportar la marca.
- **B-10** decide si el reembolso total acepta `amount` explícito. Si lo rechaza, hay que ajustar el
  cliente para enviar el cuerpo vacío.

---

## 7. Verificar el resultado

```sql
-- Cobro
SELECT status, status_detail, external_reference FROM charge_intent WHERE id = '<charge_intent_id>';
SELECT * FROM charge_record WHERE charge_intent_id = '<charge_intent_id>';

-- Reembolso
SELECT status, status_detail, external_reference FROM refund_intent WHERE id = '<refund_intent_id>';
SELECT amount, released_amount, transaction_cost FROM refund_record WHERE refund_intent_id = '<refund_intent_id>';

-- Webhook y fallos
SELECT * FROM outbox_message ORDER BY created_at DESC LIMIT 20;
SELECT * FROM operational_failure ORDER BY created_at DESC LIMIT 20;
```

Criterios de salud:

- `charge_intent.status = CAPTURED` y **una sola** fila en `charge_record`.
- `refund_intent.status = COMPLETED` y **una sola** fila en `refund_record`.
- `outbox_message` con el mensaje `gateway.notification` por cada webhook.
- `operational_failure` **vacía** en el camino feliz. Un `INTERNAL_ERROR` con un estado `pending`,
  `refunded`, `charged_back` o `in_mediation` indicaría una regresión: esos estados ya no se
  registran como fallo (G-02 y G-03 cerradas en
  [`docs/technical-plan/mercadopago-integration.md`](../technical-plan/mercadopago-integration.md)).

---

## 8. Evidencias

Registra, en un único documento por ejecución:

| Dato | Ejemplo |
|---|---|
| Fecha y entorno | `2026-11-05 · local + túnel` |
| App ID y site | `6664... · MCO` |
| IDs de usuarios de prueba | vendedor / comprador |
| `payment_id` y `refund_id` | los devueltos por Mercado Pago |
| `x-request-id` del webhook | el del header recibido |
| Estado final en Mercado Pago | `approved` / `refunded` |
| Filas en BD | capturas o salida de las consultas del paso 7 |
| Captura del panel | cobro/reembolso y entrega del webhook |

Guarda la evidencia **sin credenciales, sin tokens y sin números de tarjeta**.

Si el equipo dispone del MCP de Mercado Pago, adjunta además el resultado de `notifications_history`
(entregas del webhook) y `quality_evaluation` (calidad de la integración) como diagnóstico
complementario.

---

## 9. Diagnóstico rápido

| Síntoma | Causa probable | Qué revisar |
|---|---|---|
| `401 UNAUTHENTICATED` en el webhook | Secret vacío o distinto del panel | `GATEWAY_WEBHOOK_SECRET` y reinicio |
| No llega ningún webhook | URL no registrada, túnel caído o topic distinto de `payment` | Panel → Webhooks; logs del túnel |
| `400` con `3028` | Se envió un tipo genérico como `payment_method_id` | D-UC05-16; el adaptador ya lo omite |
| `400` con `4001` | `payment_method_id` ausente y MP no lo infiere | Escenario B-09; el frontend debe aportar la marca |
| Intención `REJECTED` sin llamada a MP | Falta `payer_email` | Mensaje de UC07 |
| `COMMUNICATION_ERROR` y reintentos | Timeout, `5xx` o credenciales inválidas | Logs del worker; DLQ |
| Cobro en MP pero intención sin `external_reference` | Timeout antes de guardar la referencia | Conciliación (`seashare.gateway.reconciliation-interval`) |

---

## 10. Limpieza

1. Baja el stack: `docker compose --profile app down`.
2. Detén el túnel.
3. Si vas a repetir desde cero, elimina el volumen (`docker compose down -v`) y vuelve a levantar.
4. No borres las evidencias de la ejecución.

---

## 11. Modo A y casos que el sandbox no reproduce

El modo A **no** usa Mercado Pago ni un simulador: es la suite automática (`.\mvnw.cmd clean verify`), que
levanta WireMock **embebido en JUnit** (`MercadoPagoChargeAdapterTest`, `MercadoPagoRefundAdapterTest`) y
MockMvc para el webhook. Los casos de prueba de `docs/qa/test-cases/` se ejecutan contra el sandbox real
(esta guía); lo que el sandbox no puede producir se cubre con la suite automática:

| Caso | Cómo se provoca | Cobertura |
|---|---|---|
| Pago rechazado | Titular `OTHE` al tokenizar (§4) | Sandbox |
| Token inválido (`400`) | Un `payment_token_ref` falso | Sandbox |
| Pasarela caída (`COMMUNICATION_ERROR`) | `GATEWAY_BASE_URL` apuntando a un puerto cerrado (por ejemplo `http://localhost:9`) y reinicio de la aplicación | Sandbox + app |
| Pago `expired`, `404` de consulta, `503` | No se reproducen en el sandbox | Suite automática (`MercadoPago*AdapterTest`, `RegisterChargeResultServiceTest`) |
| Liquidación (UC10) | No hay HTTP: es un libro interno simulado (D-UC10-18) | Suite automática y TC de UC10 |
