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

La dispersión se instrumenta con la sub-API de **Advanced Payments** de Mercado Pago, liberando el neto hacia la cuenta del vendedor:

`POST /v1/advanced_payments/{id}/disbursements`

`{id}` es la **referencia externa del cobro original** (`charge_intent.external_reference`); el contrato original no definía su origen y esta es la interpretación `[CONV]` del ACL. Si la integración configurada no soporta este recurso, el ACL puede usar el endpoint equivalente documentado en su implementación, conservando el mismo payload y la misma clave idempotente.

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <ACCESS_TOKEN>` (requisito de la integración con Mercado Pago; no es seguridad del proyecto — general-plan §7.1) |
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

1. **Independencia de comisiones**: El sistema asume que cobró inicialmente a una cuenta master (la de la plataforma) y ahora transfiere el neto al propietario. La liquidación estándar se calcula como **alquiler bruto − comisión − seguro náutico** (D-35); las cancelaciones y el depósito no aplican comisión ni seguro. El monto remanente tras la dispersión se queda en la cuenta de la plataforma [SPEC RF-005, RF-007].
2. **Cuenta destino vinculada**: El `collector_id` del propietario debe estar previamente autorizado y vinculado a la aplicación de Marketplace del sistema en Mercado Pago; llega como **dato plano** de la configuración de la integración y el sistema lo envía tal cual, sin validar identidad [CONV].
3. **Componente de depósito**: Si parte de la liquidación al propietario incluye dinero que venía del depósito de garantía retenido al cliente, el sistema unifica esto en el `amount` enviado a MP (para simplificar la transferencia) y maneja el detalle del concepto de forma interna [SPEC RF-016].

## 4. Respuesta exitosa (Técnica)

Mercado Pago confirma la dispersión creada. El resultado final del movimiento puede llegar por webhook (ej. eventos de `merchant_order` o `payment`).

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado inmediato (ej. `approved`, `pending`) | [SPEC RF-010] |
| `status_detail` | string | Detalle del estado externo; el ACL lo mapea a `GatewaySettlementResult.statusDetail` y a `settlement_intent.status_detail`. Opcional | [SPEC RF-010] |
| `id` | integer | Identificador único de la transferencia/dispersión en MP; se persiste como `settlement_intent.external_reference` | [SPEC RF-010] |
| `amount` | decimal | Monto confirmado por la pasarela; el ACL lo mapea a `GatewaySettlementResult.amount`. Si no viene, el registro usa el monto de la intención | [SPEC RF-010; RF-016] |

### Consulta de detalle

Para conciliación y para el webhook `type=merchant_order`, el ACL consulta la dispersión por su referencia externa:

```text
GET /v1/advanced_payments/{id}
Authorization: Bearer <ACCESS_TOKEN>
```

`{id}` es el `data.id` del webhook, que el sistema guardó como `settlement_intent.external_reference` al recibir la respuesta síncrona. La respuesta se mapea al mismo `GatewaySettlementResult`; un `404` no es un veredicto financiero y no cierra la intención.

## 5. Manejo de Errores

Timeouts o errores HTTP `5xx` reintentan desde el worker con el mismo `X-Idempotency-Key`. Un `400` de validación del medio de pago (ej. `collector_id` no vinculado o fondos insuficientes) es fallo **no reintentable**: marca la `IntenciónDeDispersión` en estado fallido para resolución manual o re-vinculación de cuenta. El resto de `4xx` son fallo propio (E8) y se reintentan como un `5xx` [SPEC RNF-003, RF-012; README §4.4].

## 6. Trazabilidad

HU1 a HU3 · RF-005, RF-006, RF-008, RF-016 · RNF-001 a RNF-003.
