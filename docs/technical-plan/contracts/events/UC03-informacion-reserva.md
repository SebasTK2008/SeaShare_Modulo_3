# UC03 — Brindar información de reserva

| Campo | Valor |
|---|---|
| Caso de uso | UC03 Brindar información de reserva |
| SPEC | `docs/features/003-brindar-informacion-de-reserva/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | No (unidireccional) |
| Quién puede publicarlo | Sistema de Reservas y Operaciones |
| Efectos secundarios | Inclusión de UC02; crea o actualiza `InformaciónDeReserva` |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Sistema de Reservas y Operaciones notifica los datos de una reserva ingresados por el arrendatario. El sistema obtiene la tarifa base vigente consultando a Flota (UC02) y registra internamente la información para que quede disponible para el cálculo definitivo del valor (UC04).

## 2. Petición (Mensaje AMQP)

**Exchange**: `seashare.reservations` (Topic)
**Routing Key**: `reservation.info.provided`
**Cola Consumidora**: `finance.reservation-info.v1`

### Propiedades AMQP (Headers)

| Propiedad | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `Message-Id` | Sí | UUID o cadena única del mensaje |
| `App-Id` | No | `reservations-service` |

### Body

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservation_id` | UUID | Sí | Identificador de la reserva | [SPEC RF-001] |
| `boat_id` | UUID | Sí | Identificador de la embarcación | [SPEC RF-001] |
| `start_date` | date | Sí | Fecha de inicio | [SPEC RF-001] |
| `end_date` | date | Sí | Fecha de fin | [SPEC RF-001] |
| `passengers` | integer | Sí | Número de pasajeros | [SPEC RF-001] |
| `owner_id` | UUID | Sí | Propietario de la embarcación | [SPEC RF-001] |
| `max_capacity` | integer | Sí | Capacidad máxima de la embarcación | [SPEC RF-001] |

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
  "start_date": "2026-12-20",
  "end_date": "2026-12-22",
  "passengers": 4,
  "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
  "max_capacity": 6
}
```

## 3. Reglas de procesamiento

1. **Validaciones**: `end_date >= start_date`, `1 <= passengers <= max_capacity`. Si no se cumplen, se registra el fallo internamente y no se persiste la información [SPEC RF-005].
2. **Dependencia**: No se consulta a Flota para obtener `owner_id` ni `max_capacity` [SPEC RF-003].
3. **Inclusión (UC02)**: Se incluye "Brindar tarifa base" pasando `boat_id` y `start_date` para obtener la tarifa vigente. Si falla, el mensaje puede reintentarse brevemente; si persiste, se registra el fallo y el mensaje va a la DLQ, sin persistir información incompleta [SPEC RF-002, RNF-003].
4. **Idempotencia y Upsert**: Si se recibe una reserva que ya existe, se sobrescribe con la versión más reciente (upsert) y se vuelven a consultar los valores [SPEC casos extremos]. Los montos previamente calculados (si existieran) se invalidan.
5. **No hay respuesta**: El sistema no devuelve nada a Reservas. Los fallos se registran en `operational_failure` [SPEC RF-006, RF-007].

## 4. Respuesta exitosa

No aplica (unidireccional). El mensaje se confirma (`ack`) al persistir en BD.

## 5. Respuestas de error

Los errores de negocio (validación) se confirman (`ack`) para no reprocesar en vano y se registran internamente. Errores transitorios (caída de BD, caída de Flota) se reintentan (Nack con requeue o DLQ tras N intentos).

## 6. Trazabilidad

HU1 · RF-001 a RF-008 · RNF-001 a RNF-003 · CE-001 a CE-004.
