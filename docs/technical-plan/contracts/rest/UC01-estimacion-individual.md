# UC01 — Estimación individual

| Campo | Valor |
|---|---|
| Caso de uso | UC01 Solicitar estimación para reserva — modalidad **individual** (HU2) |
| SPEC | `docs/features/001-solicitar-estimacion-para-reserva/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Sistema de Reservas y Operaciones |
| Efectos secundarios | Ninguno (solo lectura) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Reservas delega el cálculo exacto de una embarcación para un rango de fechas y un número de pasajeros. El sistema devuelve el valor total **y una advertencia obligatoria** que Reservas solo renderiza, sin lógica financiera [SPEC HU2].

## 2. Petición

`POST /api/v1/estimates/individual` **[CONV]** (OQ-02; el SPEC RF-004 habla de "un *endpoint*" para ambas modalidades — observación 4 del plan §12.2)

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Content-Type` | Sí | `application/json` |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Body

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `boat_id` | UUID | Sí | Embarcación a estimar | [SPEC HU2: "enviando el ID"] |
| `start_date` | date | Sí | Fecha de inicio. Define la tarifa base aplicada a toda la duración | [SPEC HU2, RF-003] |
| `end_date` | date | Sí | Fecha de fin. Puede ser igual a `start_date` (1 día) | [SPEC HU2, casos extremos] |
| `passengers` | integer | Sí | Número de pasajeros. El SPEC no fija un rango para UC01 (la capacidad máxima no es un insumo de este caso de uso) | [SPEC HU2] — rango [PEND] |

```json
{
  "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
  "start_date": "2026-12-20",
  "end_date": "2026-12-22",
  "passengers": 4
}
```

## 3. Reglas de procesamiento

1. **Validación previa a cualquier cálculo** [SPEC casos extremos]: formato de fecha inválido, `start_date` en el pasado o `end_date` anterior a `start_date` ⇒ solicitud inválida, error controlado, sin cálculo. La comparación con "hoy" usa la zona horaria de negocio ([PEND] OQ-04).
2. `start_date == end_date` es **válido** y equivale a 1 día; solo es inválida una fecha de fin anterior [SPEC casos extremos].
3. **Días** = cantidad **inclusiva** entre inicio y fin: del 20 al 22 son 3 días [SPEC RF-003; UC04 RF-003 usa la misma regla].
4. La tarifa base se consulta a Flota por el identificador y se le aplica la tarifa dinámica **de la fecha de inicio**; esa tarifa se usa para toda la duración [SPEC RF-003, UC02].
5. **Fórmula**: `estimated_total = (tarifa base final × días) + (tarifa de seguro × pasajeros)` [SPEC RF-002/RF-003].
6. El campo `warning` es **obligatorio** y su texto proviene del sistema [SPEC HU2, escenario 1].
7. Embarcación sin tarifa base ⇒ error controlado [SPEC casos extremos]. El SPEC no distingue "sin tarifa" de "no reconocida por Flota" en la modalidad individual; ambos devuelven `BASE_RATE_NOT_AVAILABLE` ([PEND] OQ-11).
8. Falla de Flota ⇒ `503 FLEET_UNAVAILABLE`, sin valores asumidos [SPEC casos extremos, RNF-003].
9. Falta la tarifa de seguro o el porcentaje que exige la fecha de inicio ⇒ `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, sin estimación parcial [SPEC casos extremos].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `boat_id` | UUID | Embarcación estimada | [SPEC] |
| `start_date` | date | Fecha de inicio recibida | [CONV] eco |
| `end_date` | date | Fecha de fin recibida | [CONV] eco |
| `duration_days` | integer | Cantidad de días inclusiva | [SPEC HU2: "informa la cantidad de días inclusiva"] |
| `passengers` | integer | Pasajeros recibidos | [CONV] eco |
| `estimated_total` | string decimal | Valor estimado | [SPEC RF-003] |
| `warning` | string | Advertencia obligatoria | [SPEC HU2 escenario 1] |

Texto exacto de `warning` (sin punto final, como en el SPEC):

> `Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva`

```json
{
  "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
  "start_date": "2026-12-20",
  "end_date": "2026-12-22",
  "duration_days": 3,
  "passengers": 4,
  "estimated_total": "1372500.00",
  "warning": "Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva"
}
```

*(Ilustración: tarifa de Flota 350000.00; el 20-dic-2026 cae en temporada alta con incremento 25 % y es domingo con incremento de fin de semana 15 % ⇒ se aplica el mayor, 437500.00 × 3 días = 1312500.00; seguro 15000.00 × 4 = 60000.00.)*

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `INVALID_DATE_RANGE` | Formato de fecha inválido, inicio en el pasado o fin anterior al inicio | No | [SPEC casos extremos] |
| 400 | `VALIDATION_ERROR` | Cuerpo no es JSON o faltan/tienen tipo incorrecto `boat_id` o `passengers` | No | [CONV] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es el Sistema de Reservas y Operaciones | No | [SPEC HU2] |
| 422 | `BASE_RATE_NOT_AVAILABLE` | Embarcación sin tarifa base o no reconocida por Flota | No | [SPEC casos extremos] + OQ-11 |
| 503 | `FLEET_UNAVAILABLE` | Flota caída, *timeout* o inalcanzable | Sí | [SPEC casos extremos, RNF-003] |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Falta el seguro o el porcentaje requerido | No | [SPEC casos extremos] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Rango de fechas inválido",
  "status": 400,
  "detail": "La fecha de fin 2026-12-19 es anterior a la fecha de inicio 2026-12-20.",
  "code": "INVALID_DATE_RANGE",
  "retryable": false
}
```

## 6. Idempotencia y reintentos

Solo lectura; es seguro reintentar ante errores con `retryable: true`.

## 7. Trazabilidad

HU2 (escenario 1) · RF-001, RF-003, RF-004 · RNF-001, RNF-002, RNF-003 · CE-001, CE-002, CE-003, CE-004.