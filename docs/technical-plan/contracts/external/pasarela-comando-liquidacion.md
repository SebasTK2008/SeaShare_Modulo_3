# Pasarela: Comando de liquidación

| Campo | Valor |
|---|---|
| Caso de uso | UC10 Liquidar fondos de alquiler |
| SPEC | `docs/features/010-liquidar-fondos-alquiler/spec.md` |
| Dirección | Sistema (Worker) → Pasarela de Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema envía a la Pasarela de Pago la solicitud de dispersión (liquidación) de fondos hacia el Propietario. Ocurre al completarse una reserva (liquidación estándar), al completarse una disputa (liquidación de depósito) o por cancelaciones penalizadas [SPEC HU1, HU2, HU3].

## 2. Petición (Llamada al Adaptador)

Representación del modelo canónico (`SolicitudDispersiónPasarela`).

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `operation_type` | string | Sí | Tipo de operación de la integración (ej. `TRANSFER`, `SPLIT_CAPTURE`) | [SPEC RF-008] |
| `amount` | string decimal | Sí | Monto a dispersar al Propietario | [SPEC RNF-002] |
| `deposit_component_amount` | string decimal | Sí | Componente del depósito (para trazabilidad/conciliación) | [SPEC SolicitudDispersiónPasarela] |
| `original_charge_ref` | string | Sí | Referencia del cobro original (útil para transferencias vinculadas) | [SPEC SolicitudDispersiónPasarela] |
| `reservation_ref` | string | Sí | Identificador de la reserva | [SPEC SolicitudDispersiónPasarela] |
| `idempotency_key` | string | Sí | Clave única (ej. `dispersion-<reserva>-<estado>`) | [SPEC SolicitudDispersiónPasarela] |

```json
{
  "operation_type": "TRANSFER",
  "amount": "675000.00",
  "deposit_component_amount": "0.00",
  "original_charge_ref": "TXN-987654321",
  "reservation_ref": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "idempotency_key": "dispersion-b7d0e2a1-6c44-COMPLETADA-v1"
}
```

## 3. Reglas de procesamiento

1. **Independencia de comisiones**: El sistema ya calculó la comisión y el seguro, y restó esos montos del pago (en el caso de liquidación estándar). El `amount` enviado aquí es el neto final a transferir al Propietario [SPEC RF-005].
2. **Capacidad soportada**: La pasarela puede implementar esto como un *Capture* parcial donde se divide el dinero (Split Payment), o como un *Transfer* si los fondos se capturan primero a una cuenta principal (RF-008). El adaptador oculta este detalle [SPEC RF-008, SolicitudDispersiónPasarela].
3. **Componente de depósito**: Se informa el monto que corresponde a depósito para fines de seguimiento, pero el valor a operar es `amount` [SPEC RF-016].

## 4. Respuesta exitosa (Técnica)

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado inmediato (`PENDING`, `COMPLETED`, `FAILED`) | [SPEC RF-010] |
| `external_reference` | string | Identificador único de la transferencia/liquidación en la pasarela | [SPEC RF-010] |
| `detail` | string | Mensaje de error si falla síncronamente | [SPEC] |

## 5. Manejo de Errores

Timeouts o errores HTTP `5xx` reintentan desde el worker. Fallos reportados síncronamente (`FAILED`) se registran en la `IntenciónDeDispersión` [SPEC RNF-003, RF-012].

## 6. Trazabilidad

HU1 a HU3 · RF-005, RF-006, RF-008, RF-016 · RNF-001 a RNF-003.
