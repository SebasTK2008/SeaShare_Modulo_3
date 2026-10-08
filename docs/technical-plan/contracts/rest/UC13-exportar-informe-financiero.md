# UC13 — Exportar informe financiero

| Campo | Valor |
|---|---|
| Caso de uso | UC13 Consultar informe financiero — **Exportar** (HU3) |
| SPEC | `docs/features/013-consultar-informe-financiero/spec.md` |
| Dirección | Propietario / Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo Propietario o Administrador Financiero [SPEC RF-012] |
| Efectos secundarios | Ninguno (solo lectura) [SPEC RF-013] |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Genera un archivo `.csv` con los mismos datos agregados del informe financiero consultado en la UI, garantizando idéntico alcance, cálculo y período, para que el usuario pueda descargarlo y conservarlo [SPEC HU3].

## 2. Petición

`GET /api/v1/financial-report/export` **[CONV]**

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Accept` | No | `text/csv` |
| `X-Correlation-Id` | No | Cadena libre |

### Parámetros de consulta (Query Parameters)

| Parámetro | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `periodicity` | string | Sí | `QUINCENAL`, `MENSUAL`, `TRIMESTRAL` | [SPEC RF-013] |
| `period` | string | Sí | Identificador explícito del período a exportar, en el formato canónico según periodicidad (ver tabla) | [SPEC RF-013] |

**Formato canónico de `period` según `periodicity`** (idéntico al contrato de consulta):

| `periodicity` | Formato | Ejemplos |
|---|---|---|
| `QUINCENAL` | `YYYY-MM-H1` (días 1-15) / `YYYY-MM-H2` (días 16-fin) | `2026-10-H1`, `2026-10-H2` |
| `MENSUAL` | `YYYY-MM` | `2026-10` |
| `TRIMESTRAL` | `YYYY-Q1` (Ene-Mar) / `YYYY-Q2` (Abr-Jun) / `YYYY-Q3` (Jul-Sep) / `YYYY-Q4` (Oct-Dic) | `2026-Q4` |

Un valor de `period` que no coincida con el formato de `periodicity` recibido devuelve `400 INVALID_PERIOD` [SPEC RF-004, casos extremos].

## 3. Reglas de procesamiento

1. **Igualdad de cálculo**: Utiliza internamente el mismo flujo que `GET /api/v1/financial-report` para determinar el alcance (por rol de usuario) y realizar las agregaciones [SPEC RNF-004].
2. **Formato CSV**: Transforma el resultado en un archivo de texto plano delimitado por comas [SPEC RF-012].
3. **Contenido**: El CSV debe incluir métricas agregadas (neto/ganancia, totales por tipo), período, periodicidad, nombre/id del solicitante y la fecha y hora de la generación [SPEC HU3 escenarios 1 y 2, RF-013].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: text/csv`
Header sugerido: `Content-Disposition: attachment; filename="informe_financiero_2026-10.csv"`

```csv
Fecha de Generacion,Solicitante,Alcance,Periodicidad,Periodo,Neto Generado,Total Cobros,Total Reembolsos,Total Dispersiones,Total Comisiones,Volumen Bruto Total,Fondos Retenidos,Monto Disputado,Disputas Abiertas
2026-11-01T10:00:00Z,AdminFinanzas,PLATFORM,MENSUAL,2026-10,150000.00,1150000.00,100000.00,900000.00,135000.00,1250000.00,100000.00,240.00,1
```

## 5. Respuestas de error

Los errores se devuelven preferiblemente como JSON (`application/problem+json`), dependiendo del soporte del cliente que hace la descarga:

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `INVALID_PERIOD` | Periodicidad no soportada o período con formato inválido | No | [SPEC RF-004, casos extremos] |
| 400 | `VALIDATION_ERROR` | Falta `periodicity` o `period`, u otro parámetro con tipo o formato incorrecto | No | [CONV] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es Propietario ni Administrador Financiero | No | [SPEC RF-012] |
| 500 | `INTERNAL_ERROR` | Error no previsto al generar el CSV | Sí | [CONV] |

## 6. Idempotencia y reintentos

Solo lectura; seguro reintentar.

## 7. Trazabilidad

HU3 · RF-012, RF-013, RF-015 · RNF-001, RNF-004 · CE-006.
