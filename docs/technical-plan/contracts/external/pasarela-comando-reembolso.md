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

El sistema envía una solicitud de liberación (de una autorización) o reembolso (de un cobro capturado) a la Pasarela de Pago (Mercado Pago). Esto ocurre ante cancelaciones (UC07) o disputas rechazadas (UC08) [SPEC HU1, HU2].

## 2. Petición (Llamada a Mercado Pago)

Se invoca el endpoint de reembolsos de la API de Mercado Pago.

`POST /v1/payments/{id}/refunds`

Donde `{id}` corresponde al ID del pago original devuelto por Mercado Pago (`original_charge_ref` en el sistema).

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <ACCESS_TOKEN>` |
| `X-Idempotency-Key` | Sí | Clave única (ej. `reembolso-<reserva>-<estado>`) |

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
| `id` | integer | Identificador único del reembolso en Mercado Pago | [SPEC RF-007] |
| `amount` | decimal | Monto que fue reembolsado | [SPEC] |

*(Nota: Aunque el reembolso puede responder `approved` síncronamente, también se emitirá un webhook asíncrono con `type=refund` que el sistema usará para asentar la contabilidad definitiva)*.

## 5. Manejo de Errores

Fallas técnicas en la llamada (`Timeout`, `5xx`) reintentan el mensaje en el worker con la misma `X-Idempotency-Key`. Fallos por negocio (`400`, `403` ej. fondos insuficientes o límite de tiempo excedido) detienen los reintentos y marcan la intención en estado de error, requiriendo revisión manual [SPEC RNF-003].

## 6. Trazabilidad

HU1, HU2 · RF-002 a RF-006, RF-012 · RNF-001 a RNF-003.
