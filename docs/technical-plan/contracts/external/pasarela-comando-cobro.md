# Pasarela: Comando de cobro (Mercado Pago)

| Campo | Valor |
|---|---|
| Caso de uso | UC05 Procesar cobro |
| SPEC | `docs/features/005-procesar-cobro/spec.md` |
| Dirección | Sistema (Worker) → Mercado Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema envía una solicitud de cobro a la API de Pagos de Mercado Pago (`POST /v1/payments`) por el valor total de la reserva, utilizando el token de tarjeta generado en el frontend. Esta llamada se realiza asíncronamente desde un worker (patrón outbox) para no bloquear la transacción que registró la intención de cobro [SPEC HU1].

## 2. Petición (Llamada a Mercado Pago)

Esta es una representación del modelo que el adaptador (`GatewayPort`) transformará y enviará a Mercado Pago.

`POST /v1/payments`

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <ACCESS_TOKEN>` |
| `X-Idempotency-Key` | Sí | Clave única (ej. `cobro-<reservation_id>`) para evitar cobros duplicados |

### Body (Payload)

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `transaction_amount` | decimal | Sí | Monto total a cobrar (incluye alquiler, seguro y depósito) | [SPEC RF-002, RNF-002] |
| `token` | string | Sí | Token seguro de la tarjeta generado por el SDK de Mercado Pago | [SPEC RF-001, RF-011] |
| `description` | string | Sí | Descripción de la reserva para el estado de cuenta | [SPEC] |
| `installments` | integer | Sí | Número de cuotas (usualmente 1 para este negocio) | [SPEC] |
| `payment_method_id` | string | Sí | ID del medio de pago (ej. `visa`, `master`) | [SPEC] |
| `payer.email` | string | Sí | Email del arrendatario | [SPEC] |
| `external_reference` | string | Sí | Identificador de la reserva para trazabilidad cruzada | [SPEC SolicitudCobroPasarela] |

```json
{
  "transaction_amount": 990000.00,
  "token": "ff8080814c11e237014c1ff593b57b4d",
  "description": "Reserva de embarcación b7d0e2a1",
  "installments": 1,
  "payment_method_id": "visa",
  "payer": {
    "email": "arrendatario@example.com"
  },
  "external_reference": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10"
}
```

## 3. Reglas de procesamiento

1. **Minimización de datos**: NUNCA se envía ni se almacena el número completo de tarjeta o CVV. Solo se opera con el `token` de un solo uso [SPEC RF-011, RNF-004].
2. **Monto único**: El `transaction_amount` es uno solo, englobando alquiler, seguro y depósito. No se hacen llamadas separadas por concepto [SPEC RF-015].
3. **Idempotencia**: Se garantiza pasando el `X-Idempotency-Key` en los headers de la llamada. Un timeout no significa fallo [SPEC RNF-003].

## 4. Respuesta exitosa (Técnica)

Mercado Pago responde confirmando la recepción y estado inicial del pago. El resultado definitivo asíncrono llegará por el webhook (`pasarela-webhook-resultados.md`).

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado en MP (`approved`, `in_process`, `rejected`) | [SPEC RF-006] |
| `id` | integer | Identificador único del pago en Mercado Pago (`external_reference` interno) | [SPEC RF-012] |
| `status_detail` | string | Detalle del estado (ej. `accredited`, `cc_rejected_bad_filled_other`) | [SPEC] |

## 5. Manejo de Errores

Si la llamada HTTP falla por `Timeout`, `502`, `503`, el worker lanza excepción y el mensaje AMQP interno se reintenta (respetando la `X-Idempotency-Key`). El estado en la `IntenciónDeCobro` se marcará como `FALLA_COMUNICACION` hasta que el worker tenga éxito o se resuelva mediante el webhook. Los errores HTTP `400` por validación de token no son reintentables.

## 6. Trazabilidad

HU1 · RF-001 a RF-004, RF-011, RF-015 · RNF-001 a RNF-004.
