# Runbook: integración real con Mercado Pago en sandbox

Este documento es el **procedimiento operativo** para ejecutar la integración real de Mercado Pago
(modo B de `docs/features/qa/pagos-api-real.md`). No sustituye al checklist de escenarios: lo pone en orden.

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

Usa `.env.example` como referencia y completa el `.env` local con los valores de prueba.

```dotenv
# Pasarela (sandbox)
SPRING_PROFILES_ACTIVE=dev,qa
FLEET_BASE_URL=http://fleet-stub:8080
GATEWAY_BASE_URL=https://api.mercadopago.com
GATEWAY_ACCESS_TOKEN=TEST-...
GATEWAY_WEBHOOK_SECRET=...
GATEWAY_SETTLEMENT_MODE=simulated
```

El Compose usa `dev,qa` por defecto. `qa` carga el seed financiero usado por los TC y el stub de Flota proporciona la tarifa base. Si hay variables del mismo nombre en la sesión de PowerShell, Compose puede tomar esas variables antes que el archivo `.env`. Comprueba que los valores efectivos correspondan al stack de prueba.

Levanta el stack:

```powershell
docker compose --profile app up --build -d --wait
curl.exe -fsS http://localhost:8080/actuator/health
```

> El PostgreSQL del compose se publica en el puerto **5433** (`POSTGRES_HOST_PORT`), no en 5432.

---

## 3. Exponer el webhook por HTTPS

Mercado Pago necesita un callback **HTTPS público**.

1. Levanta tu túnel HTTPS hacia el puerto `8080`. Si tu proveedor permite filtrar rutas, publica solo `POST /api/v1/webhook/gateway`; el módulo carece de autenticación general.
2. La URL pública debe terminar en `/api/v1/webhook/gateway`.
3. Regístrala en el panel (paso 1.3) y verifica que responde:
   - Sin firma válida debe devolver `401 UNAUTHENTICATED` (es la señal de que el endpoint está vivo).
4. Si el túnel cambia de host, **actualiza la URL en el panel antes de ejecutar** los escenarios.

La integración **no** envía `notification_url` en el pago: la recepción depende por completo de esta
configuración de la aplicación (`pasarela-webhook-resultados.md` §2).

### 3.1 Enviar un webhook firmado a mano

Para diagnosticar el callback de tu propio stack local, firma la notificación con el secreto de prueba. Esta petición sirve para diagnóstico y queda separada de la evidencia de entrega real de B-03 y B-06.

```powershell
$paymentId = Read-Host 'payment_id del cobro de prueba'
$requestId = [guid]::NewGuid().ToString()
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$secret = Read-Host 'Secreto del webhook de prueba' -AsSecureString
$key = [Text.Encoding]::UTF8.GetBytes([System.Net.NetworkCredential]::new('', $secret).Password)
$hmac = [System.Security.Cryptography.HMACSHA256]::new($key)
$manifest = [Text.Encoding]::UTF8.GetBytes("id:${paymentId};request-id:${requestId};ts:${ts};")
$signature = [BitConverter]::ToString($hmac.ComputeHash($manifest)).Replace('-', '').ToLowerInvariant()
$hmac.Dispose()
Remove-Variable secret,key
$uri = "http://localhost:8080/api/v1/webhook/gateway?data.id=$paymentId&type=payment"
$headers = @{ 'x-request-id' = $requestId; 'x-signature' = "ts=$ts,v1=$signature" }
Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json' -Body '{"action":"payment.updated","type":"payment"}'
```

La respuesta esperada es HTTP 200 con `{"status":"received"}`. Para comprobar firma ausente, repite el mismo comando de envío sin `-Headers $headers` y espera HTTP 401.

---

## 4. Obtener un `card_token`

El sistema no tokeniza: recibe el token ya generado. El contrato con el repositorio de frontend está
en [`contracts/external/frontend-tokenizacion.md`](../../technical-plan/contracts/external/frontend-tokenizacion.md).

Para ejecutar el modo B necesitas:

| Dato | De dónde sale |
|---|---|
| `payment_token_ref` | Tokenización con la `public_key` (CardForm/Brick) contra el sandbox |
| `payment_method_type` | Marca devuelta por Mercado Pago (`VISA`, `MASTER`, …) o tipo genérico (`CREDIT_CARD`) |
| `payment_metadata.payer_email` | Correo del comprador de prueba |
| `payment_metadata.last_four` | Últimos 4 dígitos de la tarjeta de prueba |

Usa la Visa crédito de prueba de la [guía de tarjetas de Mercado Pago para MCO](https://www.mercadopago.com.co/developers/es/docs/checkout-api-payments/additional-content/your-integrations/test/cards). Conserva los datos de tarjeta y token fuera del repositorio y de la evidencia.

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

Cada [TC de pagos](test-cases/) incluye su propia preparación de reserva, cálculo y cobro cuando corresponde. Ejecuta esa preparación dentro del caso elegido y usa el UUID, titular, payloads y consultas de ese archivo. Para repetir sobre el mismo UUID se pueden recrear los volúmenes locales de QA.

---

## 6. Ejecutar los escenarios

El checklist **B-01…B-10** de `docs/features/qa/pagos-api-real.md` §2.2 identifica la evidencia de release. Ejecuta los TC en el orden que necesites, preparando los datos de cada archivo. Para cada caso:

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

Usa las consultas del TC ejecutado y conserva evidencia para su propio UUID. `charge_intent.external_reference` contiene el `payment_id`; `refund_intent.external_reference` contiene el `refund_id`. Comprueba los estados, importes y número de registros que indique ese caso, y las entregas reales del webhook cuando formen parte de su alcance.

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

## 11. Modo A y casos con resultados controlados

La suite automática ejecuta WireMock embebido en JUnit y MockMvc. Usa `.\mvnw.cmd clean verify` para el gate completo o los comandos de cada TC. Las pruebas automáticas incluyen expiración, rechazo de reembolso, HTTP 404/5xx, timeout y firma inválida. Los recorridos con respuestas reales del proveedor usan B. UC10 se comprueba en el libro interno del mismo entorno, con cobertura adicional en la suite.
