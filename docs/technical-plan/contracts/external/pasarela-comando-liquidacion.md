# Pasarela: Comando de liquidación (Mercado Pago)

| Campo | Valor |
|---|---|
| Caso de uso | UC10 Liquidar fondos de alquiler |
| SPEC | `docs/features/010-liquidar-fondos-alquiler/spec.md` |
| Dirección | Sistema (Worker) → Mercado Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema transfiere los fondos de una reserva completada a la cuenta de Mercado Pago vinculada del Propietario. Este proceso es la "liquidación" o "dispersión". Ocurre tras completarse la estadía sin incidentes, resolver disputas a favor del propietario, o al aplicar penalidades de cancelación [SPEC HU1, HU2, HU3].

En Mercado Pago, esto se gestiona mediante el esquema de **Marketplace** / **Split Payments**, dividiendo los fondos asociados al pago original.

## 2. Petición (Llamada a Mercado Pago)

Dependiendo de la integración de Mercado Pago elegida para el flujo (Advanced Payments, Split directo en el cobro, o transferencias directas API), el envío de fondos se instrumenta usualmente liberando o dispersando a la cuenta del vendedor (`application_fee`).
Asumiendo un modelo de Advanced Payments o dispersión (Transfer):

`POST /v1/advanced_payments/{id}/disbursements`
*(o equivalente según la sub-API exacta que use el proyecto para el modelo de Marketplace).*

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <ACCESS_TOKEN>` |
| `X-Idempotency-Key` | Sí | Clave única (ej. `dispersion-<reserva>-<estado>`) |

### Body (Payload)

El adaptador convierte la `SolicitudDispersiónPasarela` a la estructura del split de Mercado Pago.

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `disbursements` | array | Sí | Arreglo con los montos a dispersar y destinatarios | [SPEC RF-008] |
| `disbursements[].amount` | decimal | Sí | Monto neto a dispersar al Propietario | [SPEC RNF-002] |
| `disbursements[].collector_id` | integer | Sí | ID de la cuenta de Mercado Pago del Propietario (Seller) | [SPEC RF-008] |
| `disbursements[].external_reference` | string | Sí | Referencia de la reserva | [SPEC SolicitudDispersiónPasarela] |

```json
{
  "disbursements": [
    {
      "amount": 675000.00,
      "collector_id": 123456789,
      "external_reference": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10"
    }
  ]
}
```

## 3. Reglas de procesamiento

1. **Independencia de comisiones**: El sistema asume que cobró inicialmente a una cuenta master (la de la plataforma) y ahora transfiere el neto al propietario. El monto remanente tras la dispersión se queda en la cuenta de la plataforma como recaudación propia (comisión + seguro de averías) [SPEC RF-005].
2. **Cuenta destino vinculada**: El `collector_id` del propietario debe estar previamente autorizado y vinculado a la aplicación de Marketplace del sistema en Mercado Pago.
3. **Componente de depósito**: Si parte de la liquidación al propietario incluye dinero que venía del depósito de garantía retenido al cliente, el sistema unifica esto en el `amount` enviado a MP (para simplificar la transferencia) y maneja el detalle del concepto de forma interna [SPEC RF-016].

## 4. Respuesta exitosa (Técnica)

Mercado Pago confirma la dispersión creada. El resultado final del movimiento puede llegar por webhook (ej. eventos de `merchant_order` o `payment`).

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado inmediato (ej. `approved`, `pending`) | [SPEC RF-010] |
| `id` | integer | Identificador único de la transferencia/dispersión en MP | [SPEC RF-010] |

## 5. Manejo de Errores

Timeouts o errores HTTP `5xx` reintentan desde el worker con el mismo `X-Idempotency-Key`. Fallos por errores de negocio (ej. `400` porque el `collector_id` no está vinculado, o fondos insuficientes) reportados síncronamente marcan la `IntenciónDeDispersión` en estado fallido para resolución manual o re-vinculación de cuenta [SPEC RNF-003, RF-012].

## 6. Trazabilidad

HU1 a HU3 · RF-005, RF-006, RF-008, RF-016 · RNF-001 a RNF-003.
