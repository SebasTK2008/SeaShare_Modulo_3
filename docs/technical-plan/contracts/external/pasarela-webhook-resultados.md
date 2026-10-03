# Pasarela: Webhook de resultados

| Campo | Valor |
|---|---|
| Casos de uso | UC05, UC09, UC10 |
| SPECs | 005 (Cobro), 009 (Reembolso), 010 (Liquidación) |
| Dirección | Pasarela de Pago → sistema |
| ¿Responde? | Sí (200 OK para confirmar recepción) |
| Quién puede llamarlo | Solo la Pasarela de Pago (verificado criptográficamente) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

La Pasarela de Pago notifica de forma asíncrona el resultado de las operaciones (cobros, reembolsos, dispersiones). El sistema asocia el resultado a la intención original mediante la clave idempotente o la referencia del cobro y actualiza sus registros, generando los registros inmutables de auditoría si la operación fue exitosa.

## 2. Petición (Webhook)

`POST /api/v1/webhook/gateway` **[CONV]**

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `X-Gateway-Signature` | Sí | Firma HMAC o token para validar autenticidad **[CONV]** |

### Body

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `operation_type` | string | Sí | `CHARGE`, `REFUND`, `SETTLEMENT` | [SPEC UC05, UC09, UC10] |
| `idempotency_key` | string | Sí | Clave enviada por el sistema en el comando original | [SPEC UC05 RF-006, etc.] |
| `external_reference` | string | Sí | Referencia asignada por la pasarela | [SPEC UC05 RF-012] |
| `status` | string | Sí | `COMPLETED`, `FAILED`, `EXPIRED`, `REJECTED`, `CANCELLED` | [SPEC UC05 RF-006, RF-014] |
| `amount` | string decimal | No | Monto confirmado neto o final | [SPEC RNF-002] |
| `transaction_cost` | string decimal | No | Costo transaccional de la pasarela, si aplica (solo UC09) | [SPEC UC09 RF-011] |
| `detail` | string | No | Mensaje o código de error detallado | [SPEC UC05 RF-006] |

```json
{
  "operation_type": "CHARGE",
  "idempotency_key": "cobro-b7d0e2a1-6c44",
  "external_reference": "TXN-987654321",
  "status": "COMPLETED",
  "amount": "990000.00",
  "detail": "Aprobado exitosamente"
}
```

## 3. Reglas de procesamiento

1. **Autenticidad**: Se verifica la firma del webhook. Si es inválida, `401 Unauthorized`.
2. **Deduplicación**: Se utiliza la `idempotency_key` para buscar la intención original (Cobro, Reembolso o Liquidación). Si el resultado ya fue registrado, se devuelve `200 OK` ignorando el duplicado.
3. **Manejo por tipo**:
   - `CHARGE` (UC05): Actualiza `IntenciónDeCobro`. Si `COMPLETED`, genera `RegistroDeCobro` inmutable [SPEC 5 RF-006]. Si `EXPIRED`, dispara flujo de expiración [SPEC 5 RF-014].
   - `REFUND` (UC09): Actualiza `IntenciónDeReembolso`. Si `COMPLETED`, genera `RegistroDeReembolso` inmutable. Distingue `transaction_cost` si se envía [SPEC 9 RF-007, RF-011].
   - `SETTLEMENT` (UC10): Actualiza `IntenciónDeDispersión`. Si `COMPLETED`, genera `RegistroDeDispersión` y, si corresponde, `RegistroDeComisión` atómicamente [SPEC 10 RF-010].
4. **Resiliencia**: Si el procesamiento interno falla (ej. BD no disponible), devuelve `500` para que la Pasarela reintente el webhook.

## 4. Respuesta exitosa

`200 OK` — Sin cuerpo o un cuerpo JSON simple `{"status": "received"}`. Confirma a la Pasarela que el evento fue procesado.

## 5. Respuestas de error

| HTTP | `code` | Cuándo |
|---|---|---|
| 401 | `UNAUTHORIZED` | Firma inválida o ausente |
| 400 | `BAD_REQUEST` | Formato incorrecto del payload |
| 500 | `INTERNAL_ERROR` | Error del sistema. La Pasarela debe reintentar. |

## 6. Idempotencia y reintentos

El webhook de la Pasarela de Pago debe reintentar ante fallas de red o `500`. El sistema garantiza idempotencia basándose en `idempotency_key`.
