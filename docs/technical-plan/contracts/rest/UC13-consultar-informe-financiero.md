# UC13 — Consultar informe financiero

| Campo | Valor |
|---|---|
| Caso de uso | UC13 Consultar informe financiero (HU1, HU2) |
| SPEC | `docs/features/013-consultar-informe-financiero/spec.md` |
| Dirección | Propietario / Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo Propietario o Administrador Financiero [SPEC RF-001] |
| Efectos secundarios | Ninguno (solo lectura) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Devuelve datos agregados de los registros financieros para un período específico. Para el Administrador Financiero, calcula el neto generado de toda la plataforma; para el Propietario, calcula sus ganancias por las reservas de sus embarcaciones [SPEC HU1, HU2].

## 2. Petición

`GET /api/v1/financial-report` **[CONV]**

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Parámetros de consulta (Query Parameters)

| Parámetro | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `periodicity` | string | Sí | `QUINCENAL`, `MENSUAL`, `TRIMESTRAL` | [SPEC RF-001, D-18] |
| `period` | string | No | Identificador del período. Si no se envía, usa el último cerrado | [SPEC RF-001, RF-003] |

*(Nota de diseño: El formato de `period` debe ser estándar, p. ej. `2026-Q1`, `2026-10`, `2026-10-Q1` para primera quincena de octubre. Se documentará el formato esperado en convenciones del DTO)*.

## 3. Reglas de procesamiento

1. **Períodos**: Quincenas (días 1-15 y 16-fin), Meses (calendario), Trimestres (Ene-Mar, Abr-Jun, Jul-Sep, Oct-Dic). No se aceptan rangos libres de fechas [SPEC RF-002, RF-004].
2. **Alcance**:
   - **Propietario**: Agrega solo los registros vinculados a sus reservas [SPEC RF-006].
   - **Administrador Financiero**: Agrega todos los registros de la plataforma [SPEC RF-005].
3. **Fórmula**:
   - **Administrador Financiero**: `Neto = (Total Cobros) - (Total Reembolsos) - (Total Dispersiones)`.
   - **Propietario**: `Ganancia = Total Dispersiones` (a su favor) [SPEC RF-008].
4. **Comisiones**: El total de comisiones es informativo. NO se suma ni resta nuevamente en las fórmulas anteriores [SPEC RF-009].
5. **Cantidades**: Se agrupa el número de registros por tipo (cobros, reembolsos, dispersiones, comisiones) [SPEC RF-009].
6. **Comparación temporal**: Se calcula contra el período inmediatamente anterior equivalente. Variación absoluta y porcentual. Si el valor anterior es 0, la variación porcentual es `null` o se marca como no calculable [SPEC RF-010].
7. **Período en curso**: Si el período consultado no ha cerrado, se identifica como abierto [SPEC casos extremos].
8. **Inclusión de registros**: Se usa la `created_at` de las tablas de registros inmutables. Operaciones pendientes, incluyendo depósitos pendientes sin desenlace, se ignoran [SPEC RNF-003, RF-014].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `periodicity` | string | Periodicidad solicitada | [SPEC InformeFinancieroResultado] |
| `period` | string | Período evaluado | [SPEC] |
| `is_closed` | boolean | Indica si el período consultado ya finalizó | [SPEC casos extremos] |
| `scope` | string | `PLATFORM` o `OWNER` | [SPEC CE-001] |
| `net_amount` | string decimal | Neto generado o ganancia, según el rol | [SPEC RF-008, RNF-002] |
| `total_collections` | string decimal | Suma de `RegistroDeCobro` | [SPEC RF-007] |
| `total_refunds` | string decimal | Suma de `RegistroDeReembolso` | [SPEC RF-007] |
| `total_settlements` | string decimal | Suma de `RegistroDeDispersión` | [SPEC RF-007] |
| `total_commissions` | string decimal | Suma de `RegistroDeComisión` (informativo) | [SPEC RF-009] |
| `counts` | object | Cantidad de registros (ej. `{"COBRO": 15, "REEMBOLSO": 2, ...}`) | [SPEC RF-009] |
| `comparison` | object | Comparación con el período anterior | [SPEC RF-010] |

```json
{
  "periodicity": "MENSUAL",
  "period": "2026-10",
  "is_closed": true,
  "scope": "PLATFORM",
  "net_amount": "150000.00",
  "total_collections": "1150000.00",
  "total_refunds": "100000.00",
  "total_settlements": "900000.00",
  "total_commissions": "135000.00",
  "counts": {
    "COBRO": 10,
    "REEMBOLSO": 2,
    "DISPERSION": 8,
    "COMISION": 8
  },
  "comparison": {
    "previous_period": "2026-09",
    "absolute_variation": "25000.00",
    "percentage_variation": "20.00"
  }
}
```

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Periodicidad o formato de período inválido | No | [SPEC casos extremos] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es Propietario ni Administrador Financiero | No | [SPEC] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

## 6. Idempotencia y reintentos

Solo lectura; seguro reintentar.

## 7. Trazabilidad

HU1, HU2 · RF-001 a RF-011, RF-014 a RF-015 · RNF-001 a RNF-003 · CE-001 a CE-005.
