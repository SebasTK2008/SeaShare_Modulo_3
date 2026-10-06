# UC02 — Flota: Consulta de tarifas base

| Campo | Valor |
|---|---|
| Caso de uso | UC02 Brindar tarifa base (interno a UC01 y UC03) |
| SPEC | `docs/features/002-brindar-tarifa-base/spec.md` |
| Dirección | Sistema → Sistema de Gestión de Flota |
| ¿Responde? | Sí (síncrono) |
| Responsable de implementarlo | Sistema de Gestión de Flota |
| Efectos secundarios | Ninguno (solo lectura) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema consulta al Sistema de Gestión de Flota la tarifa base vigente para una o varias embarcaciones. Flota es la fuente autoritativa de esta tarifa (el precio fi1jado por el propietario). El sistema luego aplicará la tarifa dinámica sobre este valor [SPEC HU1, RF-004].

## 2. Petición

`POST /api/v1/fleet/base-rates` **[CONV]** (Se usa POST para enviar una lista de IDs, evitando límites de URL y simplificando UC01 en lote).

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json` |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Body

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `boat_ids` | array de UUID | Sí | Identificadores de las embarcaciones | [SPEC RNF-001] |

```json
{
  "boat_ids": [
    "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
    "9c10-5a2b7e6f1d01-3f2c1a54-8b3e-4d7a"
  ]
}
```

## 3. Reglas de procesamiento (Acuerdo con Flota)

1. Flota debe devolver la tarifa base de las embarcaciones solicitadas [SPEC RF-001].
2. **SLA**: < 500 ms para un lote de hasta 50 embarcaciones [SPEC RNF-003, referenciado en el plan].
3. Si una embarcación no tiene tarifa configurada o no existe, Flota simplemente no la incluye en la respuesta o devuelve un valor nulo para ella, y el sistema asume que la tarifa "no está disponible" [SPEC casos extremos].
4. **No** se envía el tipo o categoría; la tarifa devuelta debe ser un escalar monetario `BigDecimal` [SPEC RF-001, RNF-002].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `rates` | array de objetos | Lista de tarifas devueltas | [SPEC RNF-001] |

Cada objeto en `rates` contiene:

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `boat_id` | UUID | Identificador de la embarcación | [SPEC RNF-001] |
| `base_rate` | string decimal | Tarifa base por unidad de tiempo | [SPEC RNF-001, RNF-002] |

```json
{
  "rates": [
    {
      "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
      "base_rate": "350000.00"
    }
  ]
}
```

## 5. Respuestas de error

Si Flota devuelve errores `4xx` o `5xx`, o si ocurre un *timeout*, el sistema no asume ningún valor. Registra el fallo y propaga el error (ej. `FLEET_UNAVAILABLE` en UC01) [SPEC casos extremos, RNF-003].

## 6. Idempotencia y reintentos

Consulta segura. El sistema puede configurar reintentos con *backoff* antes de dar por fallida la operación.

## 7. Trazabilidad

HU1 · RF-001, RF-004 · RNF-001 a RNF-003 · CE-001 a CE-003.
