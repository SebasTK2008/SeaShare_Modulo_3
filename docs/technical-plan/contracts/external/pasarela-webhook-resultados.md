# Pasarela: Webhook de resultados (Mercado Pago)

| Campo | Valor |
|---|---|
| Casos de uso | UC05, UC09, UC10 |
| SPECs | 005 (Cobro), 009 (Reembolso), 010 (Liquidación) |
| Dirección | Mercado Pago → sistema |
| ¿Responde? | Sí (`200 OK` o `204 No Content` para confirmar recepción) |
| Quién puede llamarlo | Solo Mercado Pago (verificado criptográficamente con `x-signature`: requisito de la integración con Mercado Pago, no control de acceso del sistema; general-plan §7.1) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

La Pasarela de Pago (Mercado Pago) notifica de forma asíncrona el resultado de las operaciones (cobros, reembolsos, dispersiones). El sistema asocia el resultado a la intención original mediante la **referencia externa** (`external_reference`) que guardó al crear la operación y actualiza sus registros, generando los registros inmutables de auditoría si la operación fue exitosa.

1. **Notificación entrante**: MP hace `POST` al endpoint del sistema con un payload mínimo y query params identificadores. El sistema responde `200 OK` inmediatamente para acusar recibo.
2. **Consulta activa a la API de MP**: El sistema usa el `data.id` recibido —que es el `external_reference` con el que la intención quedó asociada— para consultar la API de Mercado Pago y obtener el estado y detalle completo de la operación, y actualiza sus registros internos.

Este contrato describe únicamente el paso 1 (el endpoint que el sistema expone a MP). El paso 2 se describe en los contratos de comando de cobro, reembolso y liquidación, y se ejecuta **de forma asíncrona** en el worker de la cola interna `finance.gateway-notification.v1` (§3.3).

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
| `action` | string | Sí | Acción que disparó el evento. Ej: `payment.created`, `payment.updated`. El sistema lo toma del cuerpo de la petición |
| `api_version` | string | No | Versión de la API de MP que envió la notificación (ej. `v1`) |
| `data` | object | No | Objeto con el `id` del recurso notificado. El sistema toma el identificador del **query param** `data.id`, no del cuerpo |
| `data.id` | string | No | ID del recurso en MP; el sistema lo toma del **query param** `data.id` |
| `date_created` | datetime | No | Fecha y hora de creación del evento en MP |
| `id` | integer | No | ID único de la notificación en MP |
| `live_mode` | boolean | No | `true` en producción, `false` en sandbox |
| `type` | string | Sí | Tipo de recurso: `payment`, `refund`, `merchant_order`; el sistema lo toma del **query param** `type` |
| `user_id` | string | No | ID de la cuenta de MP del vendedor |

Los campos que el sistema **exige** son `action` (cuerpo), `data.id` y `type` (query params): si falta alguno, la respuesta es `400 VALIDATION_ERROR` (§5). Los demás campos del cuerpo se aceptan y se ignoran [CONV].

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

1. **Verificación de autenticidad (paso previo obligatorio)**: Se verifica la firma `x-signature` usando el HMAC-SHA256 y el secret del webhook configurado en el panel de Mercado Pago. Si la firma es inválida o ausente → `401 UNAUTHENTICATED`. **Sin este paso no se procesa nada.** Requisito de la integración con Mercado Pago (no es seguridad del proyecto; general-plan §7.1).

2. **Acuse de recibo inmediato**: El sistema responde `200 OK` a MP sin esperar al resultado del procesamiento interno. MP considera la notificación fallida si no recibe respuesta en tiempo (MP reintenta si recibe `4xx` o `5xx`, excepto `401`). Antes de acusar recibo, el sistema **persiste la notificación de forma durable** en su *outbox* (`outbox_message`); si esa escritura falla, la respuesta es `500 INTERNAL_ERROR` (§3.7) y MP reintenta la notificación.

3. **Procesamiento asíncrono (paso 2 — fuera de este contrato)**: la notificación persistida se publica en la cola interna `finance.gateway-notification.v1` (enrutado `gateway.notification`, cuerpo `{"type": "...", "action": "...", "data_id": "..."}`). Su worker consulta la API de MP para obtener el detalle completo del recurso (`type=payment` → `GET /v1/payments/{data.id}`, etc.) y determina el estado final de la operación; luego actualiza las intenciones internas y genera los registros inmutables. El paso 2 **no** condiciona la respuesta HTTP: su reintento lo aplica la política de colas (README §4.4) [CONV] (UC05 plan T019/T020, D-UC05-12).

4. **Mapeo de tipo de evento a operación interna** (el estado se toma de la **consulta a la pasarela**, no del cuerpo de la notificación):
   - `type=payment` → operación de cobro (UC05) → actualiza `IntenciónDeCobro` y, con el estado `approved`, genera `RegistroDeCobro` inmutable [SPEC 5 RF-006]. Se atiende cualquier `action` del tipo (`payment.created`, `payment.updated`…); el `action` no discrimina el resultado.
   - `type=payment` con estado `rejected` / `expired` → cobro fallido / expirado [SPEC 5 RF-014].
   - `type=refund` → operación de reembolso (UC09) → `data.id` es el id del reembolso; se asocia con `refund_intent.external_reference`, se consulta `GET /v1/payments/{charge_reference}/refunds/{data.id}` y se actualiza `IntenciónDeReembolso`; con estado exitoso se genera `RegistroDeReembolso` inmutable [SPEC 9 RF-007].
   - `type=merchant_order` → liquidación (UC10) → `data.id` es la referencia externa de la dispersión; se asocia con `settlement_intent.external_reference`, se consulta `GET /v1/advanced_payments/{data.id}` y, con estado exitoso, se generan `RegistroDeDispersión` y `RegistroDeComisión` atómicamente [SPEC 10 RF-010].

5. **Deduplicación**: La notificación se asocia a la intención interna por el `external_reference` de la intención —el `data.id` del recurso que la pasarela devolvió al crear la operación y que el sistema guardó—, de modo que dos notificaciones de la misma operación no se procesan dos veces.

6. **`data.id` o `type` desconocidos**: Si el ID o el tipo no pueden asociarse a ninguna intención interna, el sistema responde `200 OK` (para evitar reintentos infinitos de MP) y registra el evento en `operational_failure` para revisión manual [CONV]. Lo mismo ocurre si la pasarela responde `404` al consultar el pago: un `404` **no** es un veredicto financiero, de modo que la intención **no** se cierra en `REJECTED` y el evento queda registrado [CONV] (UC05 plan D-UC05-13).

7. **Resiliencia**: Si el sistema no puede persistir la notificación (base de datos no disponible), responde `500 INTERNAL_ERROR` para que MP reintente la notificación. Si el fallo ocurre en el paso 2 asíncrono, la respuesta ya fue `200 OK` y el reintento lo aplica la cola interna (5 intentos, *backoff* exponencial base 1 s, DLQ y registro en `operational_failure` al agotarlos) [CONV] (README §4.4).

## 4. Respuesta exitosa

`200 OK` — Sin cuerpo o con un cuerpo JSON mínimo. Confirma a Mercado Pago que la notificación fue recibida.

```json
{ "status": "received" }
```

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 401 | `UNAUTHENTICATED` | Firma `x-signature` ausente o no válida | No | [CONV] §4.4 |
| 400 | `VALIDATION_ERROR` | Payload mal formado o campos obligatorios ausentes (`action` en el cuerpo; `data.id` y `type` en la query) | No | [CONV] §4.4 |
| 500 | `INTERNAL_ERROR` | Error del sistema al persistir la notificación antes de acusar recibo (base de datos no disponible). MP reintentará | Sí | [CONV] §4.4 |

El `500` cubre el **inicio** del paso 2 (la persistencia durable de la notificación). Un fallo en la ejecución del paso 2 ya no puede alterar la respuesta HTTP: se reintenta en la cola interna (§3.3 y README §4.4) [CONV].

## 6. Idempotencia y reintentos

Mercado Pago reintenta la notificación si no recibe `200 OK` o `204 No Content` (lo hace ante cualquier `4xx` o `5xx`, excepto `401`). El sistema garantiza idempotencia en el paso 2 asociando la notificación por el `external_reference` de la intención (el `data.id` de MP) y tratando el resultado ya registrado como duplicado. La persistencia de la notificación en el *outbox* y la entrega por la cola interna son *at-least-once*: el mensaje puede repetirse y el sistema no altera lo ya registrado.
