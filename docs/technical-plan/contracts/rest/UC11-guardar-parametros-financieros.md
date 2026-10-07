# UC11 — Guardar parámetros financieros globales

| Campo | Valor |
|---|---|
| Caso de uso | UC11 Configurar parámetros financieros globales — **Guardar** (HU1, HU2, HU4) |
| SPEC | `docs/features/011-configurar-parametros-financieros-globales/spec.md` |
| Dirección | Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Administrador Financiero [SPEC RF-008] |
| Efectos secundarios | Actualiza o crea la entidad lógica `ParámetrosFinancierosGlobales` [SPEC RF-005] |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Administrador Financiero guarda la configuración financiera global (comisión, seguro e incrementos dinámicos). El sistema valida y persiste todos los valores como una única operación atómica, sobrescribiendo cualquier configuración vigente [SPEC HU4].

## 2. Petición

`PUT /api/v1/financial-parameters` **[CONV]** (Al ser reemplazo total de la entidad única, PUT es adecuado).

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
| `platform_commission_percentage` | string decimal | Sí | Porcentaje de comisión de la plataforma | [SPEC RF-001, RNF-001] |
| `insurance_fee_per_passenger` | string decimal | Sí | Tarifa del seguro náutico por pasajero | [SPEC RF-002, RNF-001] |
| `weekend_increase_percentage` | string decimal | Sí | Porcentaje de incremento por fin de semana | [SPEC RF-003, RNF-001] |
| `high_season_increase_percentage` | string decimal | Sí | Porcentaje de incremento por temporada alta | [SPEC RF-004, RNF-001] |

```json
{
  "platform_commission_percentage": "15.00",
  "insurance_fee_per_passenger": "15000.00",
  "weekend_increase_percentage": "10.00",
  "high_season_increase_percentage": "25.00"
}
```

## 3. Reglas de procesamiento

1. **Validación de tipos y nulos**: Ningún campo obligatorio puede estar vacío o ser no numérico/formato inválido [SPEC RF-013].
2. **Rangos**: Porcentaje de comisión, incremento de fin de semana e incremento de temporada alta deben estar entre 0.00 y 100.00 [SPEC RF-013].
3. **Monto**: Tarifa del seguro debe ser ≥ 0 [SPEC RF-013].
4. **Persistencia**: Si todos son válidos, se persiste atómicamente sobrescribiendo la configuración previamente vigente [SPEC RF-005, RF-011, RNF-003].
5. **Fallos**: Ante una validación fallida, el sistema no actualiza ningún parámetro, informa un error controlado y conserva la configuración anterior [SPEC RF-013, HU4 escenario 3].
6. **Efecto retroactivo**: Ninguno. Reservas previamente calculadas no se afectan [SPEC RF-007].
7. **Campos excluidos**: No se envía ni se configura el depósito de garantía ni las fechas de temporada alta [SPEC RF-012, Casos Extremos].

## 4. Respuesta exitosa

`204 No Content` — Sin cuerpo **[CONV]** o `200 OK` con un cuerpo vacío o de confirmación.

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo inválido, campos vacíos, valores fuera de rango o negativos | No | [SPEC RF-013] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es Administrador Financiero | No | [SPEC RF-008] |
| 500 | `PARAMETERS_SAVE_FAILED` | La persistencia falló; la configuración anterior se conserva íntegra | Sí | [SPEC HU4 esc. 3] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Error de validación",
  "status": 400,
  "detail": "El porcentaje de comisión (150.00) debe estar entre 0 y 100.",
  "code": "VALIDATION_ERROR",
  "retryable": false
}
```

## 6. Idempotencia y reintentos

`PUT` es idempotente por naturaleza. Enviar la misma configuración sobrescribirá con los mismos valores. Reintentar fallas del servidor es seguro.

## 7. Trazabilidad

HU1, HU2, HU4 · RF-001 a RF-005, RF-007, RF-008, RF-011 a RF-013 · RNF-001, RNF-002, RNF-003 · CE-001 a CE-004.
