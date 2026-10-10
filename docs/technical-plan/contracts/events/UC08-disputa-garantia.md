# UC08 — Brindar información de disputa de garantía

| Campo | Valor |
|---|---|
| Caso de uso | UC08 Brindar información de disputa de garantía |
| SPEC | `docs/features/008-brindar-informacion-de-disputa-de-garantia/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | No (unidireccional) |
| Quién puede publicarlo | Sistema de Reservas y Operaciones |
| Efectos secundarios | Reembolso (UC09) o Liquidación (UC10) total del depósito |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Sistema de Reservas y Operaciones informa el estado de una disputa de garantía. El sistema ejecuta las consecuencias financieras (reembolsar 100% al arrendatario o liquidar 100% al propietario) usando sus propios registros internos, sin recibir montos en la notificación [SPEC HU1].

## 2. Petición (Mensaje AMQP)

**Exchange**: `seashare.reservations` (Topic)
**Routing Key**: `reservation.dispute.updated`
**Cola Consumidora**: `finance.guarantee-dispute.v1`

### Propiedades AMQP (Headers)

| Propiedad | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `Message-Id` | Sí | UUID o cadena única del mensaje |

### Body

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservation_id` | UUID | Sí | Identificador de la reserva | [SPEC RF-001, RNF-001] |
| `dispute_id` | UUID | Sí | Identificador de la disputa | [SPEC RF-001] |
| `status` | string | Sí | `PENDING`, `REJECTED`, `COMPLETED` | [SPEC RF-001] |
| `status_changed_at` | datetime | Sí | Fecha y hora en que ocurrió la transición (ISO 8601) | [SPEC RF-001] |
| `event_key` | string | Sí | Versión o clave idempotente del evento | [SPEC RF-001, RF-008] |

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "dispute_id": "d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a",
  "status": "REJECTED",
  "status_changed_at": "2026-12-23T10:00:00Z",
  "event_key": "v1.0-4a5b6c"
}
```

## 3. Reglas de procesamiento

1. **Reconocimiento de estados**: Solo se reconocen los tres estados. Cualquier otro (incluyendo sub-estados de liberación parcial) se ignora monetariamente y se registra como fallo [SPEC RF-002, RF-010].
2. **Idempotencia**: Se aplica idempotencia por `(reservation_id, dispute_id, event_key)`. Notificaciones repetidas no generan operaciones monetarias adicionales [SPEC RF-008].
3. **Acciones**:
   - `PENDING`: Solo se registra, no dispara acción financiera [SPEC RF-004].
   - `REJECTED`: Reembolso total (100%) al arrendatario del depósito retenido. No requiere motivo [SPEC RF-003, RF-005].
   - `COMPLETED`: Liquidación total (100%) al propietario del depósito retenido [SPEC RF-006].
4. **Validación interna**: Se consulta el depósito cobrado y registrado. Si no hay depósito registrado, se anota un fallo controlado sin ejecutar acciones asumidas [SPEC casos extremos].
5. **No hay respuesta**: Es unidireccional. No se notifica a Reservas el resultado [SPEC RF-009].
6. **Sin temporizadores**: El sistema no ejecuta cron jobs, temporizadores internos ni tareas en segundo plano sobre la ventana de 24 h ni sobre el estado de la disputa; esa gestión es del Sistema de Reservas y Operaciones, que envía la notificación correspondiente (por ejemplo, `REJECTED` al vencer la ventana sin reclamo) [SPEC RF-009, RF-009A].

## 4. Respuesta exitosa

No aplica (unidireccional). Confirmación (`ack`) en RabbitMQ.

## 5. Respuestas de error

Errores de lógica y validación se registran y se hace `ack`. Errores del broker o base de datos van por Nack con requeue o DLQ.

## 6. Trazabilidad

HU1 · RF-001 a RF-010 · RNF-001 a RNF-003 · CE-001 a CE-006.
