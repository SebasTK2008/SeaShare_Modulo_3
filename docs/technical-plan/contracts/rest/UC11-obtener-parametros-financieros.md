# UC11 — Obtener parámetros financieros globales

| Campo | Valor |
|---|---|
| Caso de uso | UC11 Configurar parámetros financieros globales — **Obtener** (HU3) |
| SPEC | `docs/features/011-configurar-parametros-financieros-globales/spec.md` |
| Dirección | Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Administrador Financiero (usuario esperado; el sistema no valida el llamador — seguridad fuera de alcance, general-plan §7.1) [SPEC RF-008] |
| Efectos secundarios | Ninguno (solo lectura) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Administrador Financiero consulta la configuración financiera vigente de la plataforma. El sistema devuelve la **versión vigente** de los parámetros globales (identificada por su `version_number`) junto con los valores calculados de solo lectura, de manera que la pantalla de configuración pueda mostrar el estado actual antes de editarlo [SPEC HU3].

## 2. Petición

`GET /api/v1/financial-parameters` **[CONV]**

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Body

No aplica.

## 3. Reglas de procesamiento

1. Se recupera la **versión vigente** de `ParámetrosFinancierosGlobales` (la de mayor `version_number`); las versiones anteriores se conservan como historial inmutable [SPEC RF-006].
2. Si no existe ninguna versión de la configuración, el sistema informa que está pendiente y **no inventa valores** para ningún parámetro [SPEC HU3 escenario 2]. En REST esto se modela devolviendo un `404 Not Found` [CONV].
3. La regla del depósito de garantía se informa como texto (`guarantee_deposit_rule`): **10 % de la tarifa diaria vigente registrada de la reserva** (general-plan D-29); al nivel de parámetros globales se informa la regla, no un monto, porque el monto depende de la embarcación [SPEC RF-012, HU3].
4. El sistema incluye las ventanas de temporada alta calculadas por el calendario [SPEC HU3, RF-012].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `version_number` | integer | Número de la versión vigente consultada (para trazabilidad de los cálculos) | [CONV] — general-plan D-24 |
| `platform_commission_percentage` | string decimal | Porcentaje de comisión de la plataforma | [SPEC RF-009] |
| `insurance_fee_per_passenger` | string decimal | Tarifa del seguro náutico por pasajero | [SPEC RF-009] |
| `weekend_increase_percentage` | string decimal | Porcentaje de incremento por fin de semana | [SPEC RF-009] |
| `high_season_increase_percentage` | string decimal | Porcentaje de incremento por temporada alta | [SPEC RF-009] |
| `guarantee_deposit_rule` | string | Regla del depósito de garantía (solo lectura) | [SPEC RF-012] |
| `high_season_windows` | array de strings | Ventanas de temporada alta calculadas (solo lectura); formato `MM-dd→MM-dd` para ventanas fijas y `semana-santa` para la ventana móvil | [SPEC RF-012] + [CONV] |

```json
{
  "version_number": 7,
  "platform_commission_percentage": "15.00",
  "insurance_fee_per_passenger": "15000.00",
  "weekend_increase_percentage": "10.00",
  "high_season_increase_percentage": "25.00",
  "guarantee_deposit_rule": "10% de la tarifa diaria vigente registrada",
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
| 404 | `PARAMETERS_NOT_CONFIGURED` | Todavía no existe ninguna versión de la configuración | Sí | [SPEC HU3] |
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
