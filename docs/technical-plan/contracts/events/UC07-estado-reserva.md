# UC07 — Brindar el estado de la reserva

| Campo | Valor |
|---|---|
| Caso de uso | UC07 Brindar el estado de la reserva |
| SPEC | `docs/features/007-brindar-estado-de-la-reserva/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | No (unidireccional) |
| Quién puede publicarlo | Sistema de Reservas y Operaciones |
| Efectos secundarios | Dispara UC05 (Procesar cobro), UC09 (Reembolsar) y/o UC10 (Liquidar) según el estado |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Sistema de Reservas y Operaciones notifica el estado vigente de una reserva. El sistema ejecuta las operaciones financieras correspondientes (cobro, reembolso, liquidación) según las reglas de negocio establecidas, o no ejecuta ninguna si el estado no lo requiere [SPEC HU1, HU2, HU3].

## 2. Petición (Mensaje AMQP)

**Exchange**: `seashare.reservations` (Topic)
**Routing Key**: `reservation.status.changed`
**Cola Consumidora**: `finance.reservation-status.v1`

### Propiedades AMQP (Headers)

| Propiedad | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `Message-Id` | Sí | UUID o cadena única del mensaje |

### Body

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservation_id` | UUID | Sí | Identificador de la reserva | [SPEC RF-001, RNF-001] |
| `status` | string | Sí | Estado informado (enum de 10 valores en MAYUSCULAS, ej. `PENDIENTE`, `COMPLETADA`) | [SPEC RF-001, RNF-001] |
| `status_changed_at` | datetime | Sí | Fecha y hora en que ocurrió la transición (ISO 8601) | [SPEC RNF-001] |
| `payment_token_ref` | string | Sí (si `PENDIENTE`) | Token o referencia segura del medio de pago | [SPEC RF-002A, RNF-001] |
| `payment_method_type` | string | No | Tipo de método de pago (si disponible en `PENDIENTE`) | [SPEC RNF-001] |
| `payment_metadata` | object | No | Metadatos no sensibles del pago | [SPEC RNF-001] |

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "status": "PENDIENTE",
  "status_changed_at": "2026-12-05T14:30:00Z",
  "payment_token_ref": "tok_12345abcdef",
  "payment_method_type": "CREDIT_CARD",
  "payment_metadata": {
    "last_four": "4242"
  }
}
```

## 3. Reglas de procesamiento

1. **Reconocimiento**: Solo 10 estados válidos (`DISPONIBLE`, `INICIADA`, `RESERVADO`, `EN_NAVEGACION`, `PENDIENTE`, `CANCELADO_FLEXIBLEMENTE`, `CANCELADO_MODERADAMENTE`, `CANCELADO_TARDIAMENTE`, `CANCELADO_POR_ANFITRION`, `COMPLETADA`). Cualquier otro se registra como inconsistencia sin acción [SPEC casos extremos, RF-001].
2. **Iniciada**: Reconoce el comienzo del bloqueo temporal (TTL) de 15 minutos iniciado por el Sistema de Reservas y Operaciones; no origina operaciones monetarias ni administra el temporizador [SPEC RF-002].
3. **Pendiente**: Dispara `Procesar cobro` (UC05) usando el token. No reinicia el TTL [SPEC RF-002A].
4. **Cancelaciones**:
   - `CANCELADO_FLEXIBLEMENTE`: Reembolso 100% total [SPEC RF-003].
   - `CANCELADO_MODERADAMENTE`: Reembolso 50% alquiler + 100% depósito; Liquidación 50% alquiler [SPEC RF-004].
   - `CANCELADO_TARDIAMENTE`: Liquidación 100% alquiler; Reembolso 100% depósito [SPEC RF-005].
   - `CANCELADO_POR_ANFITRION`: Reembolso 100% total [SPEC RF-005A].
5. **Completada**: Liquidación estándar del alquiler (alquiler bruto - comisión - seguro). Depósito se mantiene pendiente asociado a la reserva para posterior resolución [SPEC RF-006, RF-007].
6. **Requisitos previos**: Se valida que existan montos previamente calculados en `InformaciónDeReserva` (UC04). Si no existen o están incompletos, se registra el fallo [SPEC casos extremos, RNF-003].
7. **Idempotencia**: Si se recibe repetido el mismo estado de cancelación o completado, se ignora [SPEC casos extremos].

## 4. Respuesta exitosa

No aplica (unidireccional). El mensaje se confirma (`ack`) tras procesar o tras delegar exitosamente las intenciones mediante outbox.

## 5. Respuestas de error

Los errores lógicos (reserva no encontrada o sin cálculo) se confirman (`ack`) tras registrar el fallo interno para no bloquear la cola. Errores transitorios (ej. BD caída) se reintentan o van a DLQ.

## 6. Trazabilidad

HU1 a HU3 · RF-001 a RF-010 · RNF-001 a RNF-003 · CE-001 a CE-007.
