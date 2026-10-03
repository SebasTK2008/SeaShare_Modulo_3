# Pasarela: Comando de reembolso

| Campo | Valor |
|---|---|
| Caso de uso | UC09 Reembolsar dinero a arrendatario |
| SPEC | `docs/features/009-reembolsar-dinero-arrendatario/spec.md` |
| Dirección | Sistema (Worker) → Pasarela de Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema envía una solicitud de liberación (de una autorización) o reembolso (de un cobro capturado) a la Pasarela de Pago. Esto ocurre ante cancelaciones (UC07) o disputas rechazadas (UC08) [SPEC HU1, HU2].

## 2. Petición (Llamada al Adaptador)

Representación del modelo canónico (`SolicitudReembolsoPasarela`).

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `operation_type` | string | Sí | `RELEASE` (liberar autorización) o `REFUND` (reembolsar cobro) | [SPEC SolicitudReembolsoPasarela] |
| `amount` | string decimal | Sí | Monto a liberar/reembolsar | [SPEC RF-002, RF-003, RNF-002] |
| `original_charge_ref` | string | Sí | `external_reference` del cobro o autorización original en la pasarela | [SPEC SolicitudReembolsoPasarela] |
| `reservation_ref` | string | Sí | Identificador de la reserva para trazabilidad | [SPEC] |
| `idempotency_key` | string | Sí | Clave única (ej. `reembolso-<reserva>-<estado>`) | [SPEC SolicitudReembolsoPasarela] |

```json
{
  "operation_type": "REFUND",
  "amount": "990000.00",
  "original_charge_ref": "TXN-987654321",
  "reservation_ref": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "idempotency_key": "reembolso-b7d0e2a1-6c44-CANCELADO_FLEXIBLEMENTE-v1"
}
```

## 3. Reglas de procesamiento

1. **Tipos de operación**: Dependiendo de si el cobro original fue solo autorizado o capturado, el adaptador invoca el endpoint de `void/release` o `refund` de la pasarela [SPEC RF-002].
2. **Sin montos parciales para el depósito**: Si la regla dicta reembolso del depósito, siempre es el 100% del depósito cobrado [SPEC RF-005].
3. **Sin deducciones transaccionales propias**: El sistema manda a reembolsar el monto estipulado por negocio. No descuenta costos transaccionales en el envío [SPEC RF-012].

## 4. Respuesta exitosa (Técnica)

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado inmediato (`PENDING`, `COMPLETED`, `FAILED`) | [SPEC RF-007] |
| `external_reference` | string | Identificador único del reembolso en la pasarela | [SPEC RF-007] |
| `detail` | string | Mensaje de error si falla síncronamente | [SPEC] |

*(Nota: En muchas pasarelas el refund es síncrono, pero se debe soportar el modelo asíncrono vía webhook por si queda `PENDING`)*.

## 5. Manejo de Errores

Fallas técnicas en la llamada (Timeout) reintentan el mensaje en el worker con la misma `idempotency_key`. El resultado final se concilia [SPEC RNF-003].

## 6. Trazabilidad

HU1, HU2 · RF-002 a RF-006, RF-012 · RNF-001 a RNF-003.
