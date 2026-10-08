# Pasarela: Webhook de resultados (Mercado Pago)

| Campo | Valor |
|---|---|
| Casos de uso | UC05, UC09, UC10 |
| SPECs | 005 (Cobro), 009 (Reembolso), 010 (Liquidación) |
| Dirección | Mercado Pago → sistema |
| ¿Responde? | Sí (`200 OK` o `204 No Content` para confirmar recepción) |
| Quién puede llamarlo | Solo Mercado Pago (verificado criptográficamente con `x-signature`) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

La Pasarela de Pago (Mercado Pago) notifica de forma asíncrona el resultado de las operaciones (cobros, reembolsos, dispersiones). El sistema asocia el resultado a la intención original mediante la clave idempotente o la referencia del cobro y actualiza sus registros, generando los registros inmutables de auditoría si la operación fue exitosa.

1. **Notificación entrante**: MP hace `POST` al endpoint del sistema con un payload mínimo y query params identificadores. El sistema responde `200 OK` inmediatamente para acusar recibo.
2. **Consulta activa a la API de MP**: El sistema usa el `data.id` recibido para consultar la API de Mercado Pago y obtener el estado y detalle completo de la operación, y actualiza sus registros internos.

Este contrato describe únicamente el paso 1 (el endpoint que el sistema expone a MP). El paso 2 se describe en los contratos de comando de cobro, reembolso y liquidación.

## 2. Petición (Webhook entrante de Mercado Pago)

`POST /api/v1/webhook/gateway` **[CONV]**

### Query Parameters (enviados por Mercado Pago en la URL)

| Parámetro | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `data.id` | string | Sí | Identificador del recurso notificado en MP (ej. ID del pago, reembolso o liquidación) |
| `type` | string | Sí | Tipo de evento: `payment`, `refund`, `merchant_order`, etc. Permite determinar qué recurso consultar en el paso 2 |

Ejemplo de URL que llega al sistema:
```
POST /api/v1/webhook/gateway?data.id=1234567890&type=payment
```

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `x-signature` | Sí | Firma HMAC-SHA256 para validar autenticidad. Formato: `ts=<timestamp>,v1=<hash>` |
| `x-request-id` | Sí | UUID único del request enviado por MP, usado en la verificación de la firma |

#### Verificación de la firma `x-signature`

MP firma cada notificación con HMAC-SHA256 usando el **secret del webhook** configurado en el panel de MP. El sistema DEBE verificar la firma antes de procesar el payload:

1. Extraer `ts` y `v1` del header `x-signature`.
2. Construir el mensaje: `id:<data.id del query param>;request-id:<x-request-id>;ts:<ts>;`
3. Calcular `HMAC-SHA256(secret_webhook, mensaje)`.
4. Comparar el resultado con `v1`. Si no coincide → `401 UNAUTHENTICATED`.

### Body

| Campo | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `action` | string | Sí | Acción que disparó el evento. Ej: `payment.created`, `payment.updated` |
| `api_version` | string | Sí | Versión de la API de MP que envió la notificación (ej. `v1`) |
| `data` | object | Sí | Objeto con el `id` del recurso notificado |
| `data.id` | string | Sí | ID del recurso en MP (coincide con el query param `data.id`) |
| `date_created` | datetime | No | Fecha y hora de creación del evento en MP |
| `id` | integer | No | ID único de la notificación en MP |
| `live_mode` | boolean | No | `true` en producción, `false` en sandbox |
| `type` | string | Sí | Tipo de recurso: `payment`, `refund`, `merchant_order` (coincide con el query param `type`) |
| `user_id` | string | No | ID de la cuenta de MP del vendedor |

```json
{
  "action": "payment.updated",
  "api_version": "v1",
  "data": {
    "id": "1234567890"
  },
  "date_created": "2026-10-08T15:30:00Z",
  "id": 987654321,
  "live_mode": true,
  "type": "payment",
  "user_id": "987654"
}
```

## 3. Reglas de procesamiento

1. **Verificación de autenticidad (paso previo obligatorio)**: Se verifica la firma `x-signature` usando el HMAC-SHA256 y el secret del webhook configurado en el panel de Mercado Pago. Si la firma es inválida o ausente → `401 UNAUTHENTICATED`. **Sin este paso no se procesa nada.**

2. **Acuse de recibo inmediato**: El sistema responde `200 OK` a MP sin esperar el resultado del procesamiento interno. MP considera la notificación fallida si no recibe respuesta en tiempo (reintentará si recibe `4xx` o `5xx`).

3. **Procesamiento asíncrono (paso 2 — fuera de este contrato)**: Con el `data.id` recibido, el sistema consulta la API de MP para obtener el detalle completo del recurso (`type=payment` → `GET /v1/payments/{data.id}`, etc.) y determina el estado final de la operación. Luego actualiza las intenciones internas y genera los registros inmutables.

4. **Mapeo de tipo de evento a operación interna**:
   - `type=payment` con `action=payment.updated` y estado `approved` → operación de cobro completada (UC05) → actualiza `IntenciónDeCobro`, genera `RegistroDeCobro` inmutable [SPEC 5 RF-006].
   - `type=payment` con `action=payment.updated` y estado `rejected` / `expired` → cobro fallido / expirado [SPEC 5 RF-014].
   - `type=refund` → operación de reembolso (UC09) → actualiza `IntenciónDeReembolso`, genera `RegistroDeReembolso` inmutable [SPEC 9 RF-007].
   - `type=merchant_order` con estado de captura completada → liquidación (UC10) → genera `RegistroDeDispersión` y `RegistroDeComisión` atómicamente [SPEC 10 RF-010].

5. **Deduplicación**: Se usa el `data.id` de MP junto con la `idempotency_key` del comando original para localizar la intención interna y evitar procesar dos veces la misma notificación.

6. **`data.id` desconocido**: Si el ID no puede asociarse a ninguna intención interna, el sistema responde `200 OK` (para evitar reintentos infinitos de MP) y registra el evento en `operational_failure` para revisión manual [PEND OQ-xx].

7. **Resiliencia**: Si el procesamiento interno falla (base de datos no disponible, error al consultar la API de MP en el paso 2), el sistema responde `500 INTERNAL_ERROR` para que MP reintente la notificación.

## 4. Respuesta exitosa

`200 OK` — Sin cuerpo o con un cuerpo JSON mínimo. Confirma a Mercado Pago que la notificación fue recibida.

```json
{ "status": "received" }
```

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 401 | `UNAUTHENTICATED` | Firma `x-signature` ausente o no válida | No | [CONV] §4.4 |
| 400 | `VALIDATION_ERROR` | Payload mal formado o campos obligatorios ausentes (`action`, `data.id`, `type`) | No | [CONV] §4.4 |
| 500 | `INTERNAL_ERROR` | Error del sistema al acusar recibo o al iniciar el paso 2. MP reintentará | Sí | [CONV] §4.4 |

## 6. Idempotencia y reintentos

Mercado Pago reintenta la notificación si no recibe `200 OK` o `204 No Content` (lo hace ante cualquier `4xx` o `5xx`, excepto `401`). El sistema garantiza idempotencia en el paso 2 mediante la deduplicación por `data.id` de MP e `idempotency_key` interna.
