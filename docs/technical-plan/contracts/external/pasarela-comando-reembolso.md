# Pasarela: Comando de reembolso (Mercado Pago)

| Campo | Valor |
|---|---|
| Caso de uso | UC09 Reembolsar dinero a arrendatario |
| SPEC | `docs/features/009-reembolsar-dinero-arrendatario/spec.md` |
| Dirección | Sistema (Worker) → Mercado Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema envía una solicitud de reembolso (total o parcial) de un cobro aprobado a la Pasarela de Pago (Mercado Pago). Esto ocurre ante cancelaciones (UC07) o disputas rechazadas (UC08) [SPEC HU1, HU2].

## 2. Petición (Llamada a Mercado Pago)

Se invoca el endpoint de reembolsos de la API de Mercado Pago.

`POST /v1/payments/{id}/refunds`

Donde `{id}` corresponde al ID del pago original devuelto por Mercado Pago (`original_charge_ref` en el sistema).

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <ACCESS_TOKEN>` (requisito de la integración con Mercado Pago; no es seguridad del proyecto — general-plan §7.1) |
| `X-Idempotency-Key` | Sí | Clave idempotente por intento (ej. `reembolso-<reservation_id>-<status>-<status_changed_at>`; general-plan D-33) |

### Body (Payload)

Si no se envía body, Mercado Pago asume un **reembolso total**. Para un **reembolso parcial** (ej. reembolsar solo el depósito de garantía), se debe especificar el monto.

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `amount` | decimal | No | Monto específico a reembolsar. Omitir para reembolso total. | [SPEC RF-002, RF-003, RNF-002] |

```json
{
  "amount": 990000.00
}
```

## 3. Reglas de procesamiento

1. **Reembolsos parciales o totales**: Se invoca el mismo endpoint. Si la regla dicta reembolso únicamente del depósito, se envía el `amount` correspondiente [SPEC RF-005].
2. **Múltiples reembolsos**: Mercado Pago soporta múltiples reembolsos parciales sobre un mismo pago hasta alcanzar el monto total de la transacción original.
3. **Sin deducciones transaccionales propias**: El sistema manda a reembolsar el monto estipulado por negocio. Mercado Pago devuelve la parte proporcional de la comisión original al hacer el reembolso [SPEC RF-012].

## 4. Respuesta exitosa (Técnica)

Mercado Pago retorna el objeto del reembolso creado.

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado del reembolso (ej. `approved`) | [SPEC RF-007] |
| `status_detail` | string | Detalle del estado externo; el ACL lo mapea a `RefundGatewayResult.statusDetail` y a `refund_intent.status_detail`. Opcional | [SPEC RF-007] |
| `id` | integer | Identificador único del reembolso en Mercado Pago; se persiste como `refund_intent.external_reference` | [SPEC RF-007] |
| `amount` | decimal | Monto que fue reembolsado | [SPEC] |
| `fee_details` / costo | decimal | Costo transaccional reportado por Mercado Pago, cuando lo informe. El ACL lo mapea a `RefundGatewayResult.transactionCost`; si no viene, queda `null` | [SPEC RF-011] |
| `released_amount` | decimal | Monto neto liberado = `amount − transaction_cost` (o `amount` si no hay costo); se persiste en `refund_record.released_amount` | [SPEC RF-011; UC09 D-UC09-07] |

*(Nota: Aunque el reembolso puede responder `approved` síncronamente, también se emitirá un webhook asíncrono con `type=refund` que el sistema usará para asentar la contabilidad definitiva)*.

### Consulta de detalle

Para conciliación y para el webhook `type=refund`, el ACL consulta el reembolso dentro del pago original:

```text
GET /v1/payments/{payment_id}/refunds/{refund_id}
Authorization: Bearer <ACCESS_TOKEN>
```

`payment_id` es la referencia externa del `charge_intent` original y `refund_id` es el `data.id` del webhook. La respuesta se mapea al mismo `RefundGatewayResult`; un `404` no es un veredicto financiero y no cierra la intención.

## 5. Manejo de Errores

Fallas técnicas en la llamada (`Timeout`, `5xx`) reintentan el mensaje en el worker con la misma `X-Idempotency-Key`. Un `400` de validación del medio de pago (p. ej. token inválido) es fallo **no reintentable**: detiene los reintentos y marca la intención en estado de error, requiriendo revisión manual. El resto de `4xx` son fallo propio (E8) y se reintentan como un `5xx` [SPEC RNF-003; README §4.4].

## 6. Trazabilidad

HU1, HU2 · RF-002 a RF-006, RF-012 · RNF-001 a RNF-003.
