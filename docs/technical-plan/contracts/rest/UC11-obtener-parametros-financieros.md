# UC11 — Obtener parámetros financieros globales

| Campo | Valor |
|---|---|
| Caso de uso | UC11 Configurar parámetros financieros globales — **Obtener** (HU3) |
| SPEC | `docs/features/011-configurar-parametros-financieros-globales/spec.md` |
| Dirección | Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Administrador Financiero [SPEC RF-008] |
| Efectos secundarios | Ninguno (solo lectura) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Administrador Financiero consulta la configuración financiera vigente de la plataforma. El sistema devuelve los parámetros globales vigentes y los valores calculados de solo lectura, de manera que la pantalla de configuración pueda mostrar el estado actual antes de editarlo [SPEC HU3].

## 2. Petición

`GET /api/v1/financial-parameters` **[CONV]**

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Body

No aplica.

## 3. Reglas de procesamiento

1. Se recupera la entidad única `ParámetrosFinancierosGlobales` vigente [SPEC RF-006].
2. Si no existe una configuración previa, el sistema informa que está pendiente y **no inventa valores** para ningún parámetro [SPEC HU3 escenario 2]. En REST esto se modela devolviendo un `404 Not Found` [CONV].
3. El sistema calcula el valor del depósito como 10% de la tarifa base diaria, pero dado que en este contexto no hay una tarifa base de una reserva específica, esto es meramente representativo o un texto informativo [SPEC RF-012, HU3] *(Nota: el spec indica "el depósito calculado como 10% de la tarifa base diaria", por lo que al nivel de parámetros globales se informará la regla del 10% en lugar de un monto, ya que el monto depende de la embarcación)*.
4. El sistema incluye las ventanas de temporada alta calculadas por el calendario [SPEC HU3, RF-012].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `platform_commission_percentage` | string decimal | Porcentaje de comisión de la plataforma | [SPEC RF-009] |
| `insurance_fee_per_passenger` | string decimal | Tarifa del seguro náutico por pasajero | [SPEC RF-009] |
| `weekend_increase_percentage` | string decimal | Porcentaje de incremento por fin de semana | [SPEC RF-009] |
| `high_season_increase_percentage` | string decimal | Porcentaje de incremento por temporada alta | [SPEC RF-009] |
| `guarantee_deposit_rule` | string | Regla del depósito de garantía (solo lectura) | [SPEC RF-012] |
| `high_season_windows` | array de strings | Ventanas de temporada alta calculadas (solo lectura) | [SPEC RF-012] [PEND formato: ver *OQ-UC11-03* en el plan UC11] |

```json
{
  "platform_commission_percentage": "15.00",
  "insurance_fee_per_passenger": "15000.00",
  "weekend_increase_percentage": "10.00",
  "high_season_increase_percentage": "25.00",
  "guarantee_deposit_rule": "10% de la tarifa base diaria",
  "high_season_windows": [
    "11-15→01-15",
    "06-01→07-30",
    "semana-santa",
    "10-05→10-12"
  ]
}
```

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no tiene rol de Administrador Financiero | No | [SPEC RF-008] |
| 404 | `PARAMETERS_NOT_CONFIGURED` | Todavía no existe configuración | Sí | [SPEC HU3] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Configuración no encontrada",
  "status": 404,
  "detail": "La configuración de parámetros financieros globales aún no se ha realizado.",
  "code": "PARAMETERS_NOT_CONFIGURED",
  "retryable": true
}
```

## 6. Idempotencia y reintentos

Solo lectura; es seguro reintentar.

## 7. Trazabilidad

HU3 · RF-006, RF-008, RF-009, RF-012.
