# UC01 — Estimación en lote

| Campo | Valor |
|---|---|
| Caso de uso | UC01 Solicitar estimación para reserva — modalidad **lote** (HU1) |
| SPEC | `docs/features/001-solicitar-estimacion-para-reserva/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Sistema de Reservas y Operaciones [SPEC UC01 HU1] |
| Efectos secundarios | Ninguno (solo lectura; la estimación es informativa y no bloquea activos) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Reservas envía una lista de identificadores de embarcación y recibe, ya calculado, el costo estimado de cada una, para mostrarlo en la pantalla principal. Reservas **no hace ninguna operación de precio** [SPEC UC01 CE-002].

## 2. Petición

`POST /api/v1/estimates/batch` **[CONV]** (OQ-02)

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
| `boat_ids` | array de UUID | Sí | Identificadores de las embarcaciones a estimar. Puede ser `[]`. Máximo `seashare.estimates.max-batch-size` (por defecto **50**) | [SPEC HU1 flujo 2: "lista de identificadores (`boat_ids`)"; RF-006: máximo "50 o 100"] — el valor 50 es [PEND] D-22 |

El lote se define **únicamente** por `boat_ids`: no lleva fechas ni número de pasajeros. El sistema fija siempre la fecha actual como fecha de evaluación, 1 día de duración y 1 pasajero (reglas 6 y 11).

**Campos no aceptados**: si el payload incluye `start_date`, `end_date`, `passengers` u otros campos desconocidos, la solicitud se rechaza con `400 VALIDATION_ERROR` sin procesar el lote (regla 11 y §5) **[CONV]**.

### Ejemplo

```json
{
  "boat_ids": [
    "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
    "9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02"
  ]
}
```

## 3. Reglas de procesamiento

1. **Lista vacía** → respuesta `200` con arreglos vacíos, sin error [SPEC casos extremos, `boat_ids` vacía].
2. **Más identificadores que el máximo** → se rechaza **sin procesar ningún elemento** [SPEC RF-006, casos extremos "1,000 embarcaciones"]. Reservas debe partir la solicitud en lotes.
3. El sistema consulta a Flota **una sola vez enviando la lista de IDs** [SPEC RF-005] — ver [`../external/flota-consulta-tarifas-base.md`](../external/flota-consulta-tarifas-base.md) — e invoca UC02 por cada embarcación.
4. **Identificadores inexistentes**: se omiten (Flota no los confirma) y se estima solo a las reconocidas con tarifa [SPEC casos extremos].
5. **Embarcación sin tarifa base** (nula o faltante): no se estima, el resto del lote se devuelve y nunca se muestra un precio asumido [SPEC casos extremos]. El SPEC permite "excluirla **o** marcarla"; este contrato la **marca** en `unavailable` ([PEND] OQ-11).
6. **Supuestos fijos del lote** (no son valores por defecto configurables): duración **1 día**, **1 pasajero** y **fecha actual** como fecha de evaluación de la tarifa [SPEC RF-002, HU1].
7. **Fórmula**: `estimated_total = (tarifa base final × duración en días) + (tarifa de seguro × pasajeros)` [SPEC RF-002]. La tarifa base final ya incluye la tarifa dinámica (UC02).
8. La estimación **no incluye** el depósito de garantía ni penalidades o ajustes posteriores [SPEC RF-002].
9. **Falla de Flota** (caída, *timeout*, inalcanzable o respuesta inválida): no se devuelven estimaciones parciales ni inventadas → `503 FLEET_UNAVAILABLE` [SPEC casos extremos, RNF-003, CE-004].
10. **Falta la tarifa de seguro o el porcentaje de incremento que exige la fecha evaluada** (fin de semana o temporada alta): no se calcula nada parcial → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` [SPEC casos extremos]. Un día regular no necesita el porcentaje (UC02 casos extremos).
11. **Sin fechas ni pasajeros en el cuerpo**: el lote se define solo por `boat_ids`. Si el payload incluye `start_date`, `end_date`, `passengers` u otros campos desconocidos, se responde `400 VALIDATION_ERROR` sin procesar el lote **[CONV]**. Por eso la validación de rango de fechas no aplica en esta modalidad (solo en la individual) [SPEC casos extremos].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `evaluation_date` | date | Fecha usada para evaluar la tarifa (siempre la fecha actual de la petición) | [CONV] informativo de RF-002 |
| `duration_days` | integer | Duración aplicada (siempre 1) | [CONV] informativo de RF-002 |
| `passengers` | integer | Pasajeros aplicados (siempre 1) | [CONV] informativo de RF-002 |
| `estimates` | array | Una entrada por embarcación estimada | [SPEC HU1 flujo 4: "arreglo de estimaciones"] |
| `estimates[].boat_id` | UUID | Identificador de la embarcación | [SPEC] |
| `estimates[].estimated_total` | string decimal | Costo estimado | [SPEC RF-002] |
| `unavailable` | array | Embarcaciones reconocidas **sin tarifa base** | [SPEC casos extremos] + OQ-11 |
| `unavailable[].boat_id` | UUID | Identificador | — |
| `unavailable[].reason` | string | `SIN_TARIFA_BASE` ("sin estimación disponible") | [SPEC casos extremos] |

```json
{
  "evaluation_date": "2026-10-03",
  "duration_days": 1,
  "passengers": 1,
  "estimates": [
    { "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01", "estimated_total": "365000.00" }
  ],
  "unavailable": [
    { "boat_id": "9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02", "reason": "SIN_TARIFA_BASE" }
  ]
}
```

*(Ilustración: tarifa base final 350000.00 + seguro 15000.00 × 1 pasajero.)*

Lista vacía:

```json
{ "evaluation_date": "2026-10-03", "duration_days": 1, "passengers": 1, "estimates": [], "unavailable": [] }
```

## 5. Respuestas de error

**Orden de validación** [CONV] (§4.3 del índice): E1 → E2 → E3 → E4 → E6 → E7 (E5 no aplica: no hay recurso en la URL; E8 no produce error en el lote: los identificadores no reconocidos se omiten y los sin tarifa se reportan en `unavailable`). La petición se valida completa (E4) antes de consultar los parámetros financieros (E6) o llamar a Flota (E7).

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo no es JSON, `boat_ids` ausente o no es arreglo, algún elemento no es UUID, tipos incorrectos, o campos que el lote no acepta (`start_date`, `end_date`, `passengers` u otros desconocidos) | No | [CONV] |
| 400 | `BATCH_SIZE_EXCEEDED` | `boat_ids` supera el máximo | No | [SPEC RF-006] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es el Sistema de Reservas y Operaciones | No | [SPEC HU1] |
| 503 | `FLEET_UNAVAILABLE` | Flota caída, *timeout*, inalcanzable o con respuesta inválida | Sí | [SPEC casos extremos, RNF-003] |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Falta el seguro o el porcentaje requerido por la fecha evaluada | Sí (cuando el Administrador Financiero configure) | [SPEC casos extremos] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Lote demasiado grande",
  "status": 400,
  "detail": "La solicitud contiene 1000 identificadores y el máximo permitido es 50.",
  "code": "BATCH_SIZE_EXCEEDED",
  "retryable": false
}
```

## 6. Idempotencia y reintentos

La operación es de solo lectura: repetir la petición no produce efectos. Reservas puede reintentar ante errores con `retryable: true`.

## 7. Trazabilidad

HU1 (escenario 1: 10 embarcaciones en pantalla) · RF-001, RF-002, RF-004, RF-005, RF-006 · RNF-001, RNF-002, RNF-003 · CE-001, CE-002, CE-004.