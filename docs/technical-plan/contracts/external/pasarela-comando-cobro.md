# Pasarela: Comando de cobro

| Campo | Valor |
|---|---|
| Caso de uso | UC05 Procesar cobro |
| SPEC | `docs/features/005-procesar-cobro/spec.md` |
| Dirección | Sistema (Worker) → Pasarela de Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema envía una solicitud de autorización o cobro a la Pasarela de Pago por el valor total de la reserva, utilizando el token de pago provisto. Esta llamada se realiza asíncronamente desde un worker (patrón outbox) para no bloquear la transacción que registró la intención de cobro [SPEC HU1].

## 2. Petición (Llamada al Adaptador)

Esta es una representación del modelo canónico (DTO `SolicitudCobroPasarela`) que el adaptador (`GatewayPort`) transformará a la API específica del proveedor.

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `operation_type` | string | Sí | `AUTHORIZE` o `CHARGE` (según capacidad configurada) | [SPEC RF-003] |
| `amount` | string decimal | Sí | Monto total a autorizar/cobrar (incluye alquiler, seguro y depósito) | [SPEC RF-002, RNF-002] |
| `payment_token` | string | Sí | Token o referencia segura del medio de pago | [SPEC RF-001, RF-011] |
| `reservation_ref` | string | Sí | Identificador de la reserva para trazabilidad | [SPEC SolicitudCobroPasarela] |
| `idempotency_key` | string | Sí | Clave única (ej. `cobro-<reservation_id>`) para evitar duplicados en la pasarela | [SPEC RNF-003] |

```json
{
  "operation_type": "CHARGE",
  "amount": "990000.00",
  "payment_token": "tok_12345abcdef",
  "reservation_ref": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "idempotency_key": "cobro-b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10-v1"
}
```

## 3. Reglas de procesamiento

1. **Minimización de datos**: NUNCA se envía el número completo de tarjeta, CVV o fecha de expiración [SPEC RF-011, RNF-004].
2. **Monto único**: El monto es uno solo, englobando alquiler, seguro y depósito. No se hacen llamadas separadas por concepto [SPEC RF-015].
3. **Idempotencia**: Se pasa la `idempotency_key` en los headers de la llamada HTTP a la pasarela (ej. `Idempotency-Key` en Stripe). Un timeout no significa fallo [SPEC RNF-003].

## 4. Respuesta exitosa (Técnica)

La pasarela responde confirmando la recepción y estado inicial. El resultado final puede venir por webhook (`pasarela-webhook-resultados.md`).

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado inmediato (`PENDING`, `COMPLETED`, `FAILED`, `REJECTED`) | [SPEC RF-006] |
| `external_reference` | string | Identificador único de la transacción en la pasarela | [SPEC RF-012] |
| `detail` | string | Mensaje o código de error detallado (si falló síncronamente) | [SPEC] |

## 5. Manejo de Errores

Si la llamada HTTP falla por `Timeout`, `502`, `503`, el worker lanza excepción y el mensaje AMQP interno se reintenta (respetando la `idempotency_key`). El estado en la `IntenciónDeCobro` se marcará como `FALLA_COMUNICACION` hasta que el worker tenga éxito o se concilie.

## 6. Trazabilidad

HU1 · RF-001 a RF-004, RF-011, RF-015 · RNF-001 a RNF-004.
