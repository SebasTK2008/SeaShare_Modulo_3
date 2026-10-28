# UC11 — Guardar parámetros financieros globales

| Campo | Valor |
|---|---|
| Caso de uso | UC11 Configurar parámetros financieros globales — **Guardar** (HU1, HU2, HU4) |
| SPEC | `docs/features/011-configurar-parametros-financieros-globales/spec.md` |
| Dirección | Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Administrador Financiero (usuario esperado; el sistema no valida el llamador — seguridad fuera de alcance, general-plan §7.1) [SPEC RF-008] |
| Efectos secundarios | Crea una **nueva versión vigente** de la entidad lógica `ParámetrosFinancierosGlobales`; las versiones anteriores se conservan como historial inmutable [SPEC RF-005] |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El Administrador Financiero guarda la configuración financiera global (comisión, seguro e incrementos dinámicos). El sistema valida y persiste todos los valores como una única operación atómica que crea una **nueva versión vigente**, conservando las versiones anteriores como historial inmutable [SPEC HU4].

## 2. Petición

`PUT /api/v1/financial-parameters` **[CONV]** (Al ser reemplazo total de la configuración vigente, PUT es adecuado; cada PUT válido con contenido distinto crea una versión nueva).

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
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
4. **Persistencia**: Si todos son válidos, se persiste atómicamente como una **versión nueva** (ninguna versión existente se modifica), que pasa a ser la vigente [SPEC RF-005, RF-011, RNF-003].
5. **Fallos**: Ante una validación fallida, el sistema no actualiza ningún parámetro, informa un error controlado y conserva la configuración anterior [SPEC RF-013, HU4 escenario 3].
6. **Efecto retroactivo: ninguno.** Reservas previamente calculadas no se afectan: conservan la **referencia a la versión de parámetros usada** (`frozen_parameters_id`), de la que se resuelven los valores aplicados, y UC11 no altera ninguna versión anterior [SPEC RF-007].
7. **Campos excluidos**: No se envía ni se configura el depósito de garantía ni las fechas de temporada alta [SPEC RF-012, Casos Extremos].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`:

```json
{ "version_number": 8 }
```

**[CONV]** Devuelve el número de la nueva versión vigente para facilitar la trazabilidad (general-plan D-24).

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo inválido, campos vacíos, valores fuera de rango o negativos | No | [SPEC RF-013] |
| 500 | `PARAMETERS_SAVE_FAILED` | La persistencia falló; la configuración anterior se conserva íntegra | Sí | [SPEC HU4 esc. 3] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Percentage out of range 0-100: 150.0000",
  "code": "VALIDATION_ERROR",
  "retryable": false,
  "errors": [
    {
      "field": "platform_commission_percentage",
      "message": "Percentage out of range 0-100: 150.0000"
    }
  ]
}
```

El campo de `errors[]` usa la clave JSON del cuerpo (`platform_commission_percentage`,
`insurance_fee_per_passenger`, `weekend_increase_percentage`, `high_season_increase_percentage`).
Un formato no numérico produce `field` y el mensaje `"must be a decimal string"`. Jackson reporta
el primer campo inválido; el cliente corrige y reenvía [D-UC11-15].

## 6. Idempotencia y reintentos

`PUT` es idempotente por naturaleza. Enviar la misma configuración (mismos cuatro valores) **no crea una versión nueva**: el sistema devuelve la versión vigente; un contenido distinto crea una versión nueva. Reintentar fallas del servidor es seguro.

El `GET` devuelve los cuatro parámetros con 4 decimales (la escala interna); reenviar exactamente esos valores no crea una versión nueva. Si el cliente redondea a 2 decimales antes de reenviar, el valor cambia y se crea una versión nueva.

## 7. Trazabilidad

HU1, HU2, HU4 · RF-001 a RF-005, RF-007, RF-008, RF-011 a RF-013 · RNF-001, RNF-002, RNF-003 · CE-001 a CE-004.
