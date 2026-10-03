# UC12 — Consultar registros financieros

| Campo | Valor |
|---|---|
| Caso de uso | UC12 Consultar registros financieros (HU1, HU2) |
| SPEC | `docs/features/012-consultar-registros-financieros/spec.md` |
| Dirección | Propietario / Administrador Financiero → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo Propietario o Administrador Financiero [SPEC RF-011] |
| Efectos secundarios | Ninguno (solo lectura) [SPEC RF-010] |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Permite a Propietarios y Administradores Financieros consultar el histórico de operaciones financieras confirmadas (cobros, reembolsos, dispersiones y comisiones) de manera paginada. El alcance de los datos devueltos se filtra automáticamente según el rol del llamador [SPEC HU1, HU2].

## 2. Petición

`GET /api/v1/financial-records` **[CONV]**

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Parámetros de consulta (Query Parameters)

| Parámetro | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `page` | integer | Sí | Número de página (1-indexed) | [SPEC RF-001] |
| `size` | integer | No | Tamaño de página (por defecto 10) | [SPEC RF-001, RF-008] |
| `transaction_type` | string | No | Filtro por tipo: `COBRO`, `REEMBOLSO`, `DISPERSION`, `COMISION` | [SPEC RF-002, D-18] |
| `owner_id` | UUID | No | Filtro por propietario asociado. Un Propietario no puede usarlo para ver a otros | [SPEC RF-002, RF-003, casos extremos] |
| `boat_id` | UUID | No | Filtro por embarcación asociada | [SPEC RF-002] |

## 3. Reglas de procesamiento

1. **Determinación del alcance**: El sistema extrae la identidad del llamador (`owner_id` del token) y su rol.
   - Si es **Propietario**: Solo puede ver registros de sus reservas. El parámetro `owner_id`, de enviarse, no puede ampliar este alcance [SPEC RF-003, casos extremos].
   - Si es **Administrador Financiero**: Puede ver toda la plataforma y usar `owner_id` para filtrar a un propietario específico [SPEC RF-003].
2. **Origen de datos**: Solo se consultan las cuatro tablas de registros inmutables. Las operaciones en curso o fallidas (intenciones) se ignoran [SPEC RF-004, casos extremos].
3. **Filtro `COMISION`**: Si `transaction_type=COMISION`, se devuelven únicamente registros de tipo `RegistroDeComisión`. No se hacen comparaciones numéricas [SPEC RF-002, casos extremos].
4. **Paginación**: Se garantiza que no se omiten ni duplican registros entre páginas [SPEC RNF-003]. Si `page` es mayor al total disponible, se devuelve error controlado [SPEC RF-009, casos extremos]. Si no hay resultados para los filtros, se devuelve una página vacía [SPEC casos extremos].
5. **Independencia de Flota**: No se consulta a Flota para resolver el alcance [SPEC RF-012].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `content` | array | Lista de `RegistroFinancieroResultado` de la página | [SPEC RF-007, RNF-001] |
| `page_number` | integer | Número de página actual | [SPEC RF-007] |
| `page_size` | integer | Tamaño de página utilizado | [SPEC RF-007] |
| `total_elements` | integer | Total de registros que coinciden con los filtros | [SPEC RF-007] |
| `total_pages` | integer | Total de páginas disponibles | [SPEC RF-007] |

Estructura de cada elemento en `content` (DTO `RegistroFinancieroResultado` [SPEC RNF-001]):

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `record_type` | string | Tipo de registro: `COBRO`, `REEMBOLSO`, `DISPERSION`, `COMISION` | [SPEC RF-005] |
| `reservation_id` | UUID | Reserva asociada | [SPEC RF-005] |
| `owner_id` | UUID | Propietario asociado | [SPEC RF-005] |
| `boat_id` | UUID | Embarcación asociada | [SPEC RF-005] |
| `amount` | string decimal | Monto de la operación | [SPEC RF-005, RNF-002] |
| `created_at` | datetime | Fecha y hora de creación del registro | [SPEC RF-005] |
| `external_reference` | string | Referencia de la pasarela de pago (nullable) | [SPEC RF-006] |

```json
{
  "content": [
    {
      "record_type": "COBRO",
      "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
      "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
      "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
      "amount": "990000.00",
      "created_at": "2026-12-05T14:30:00Z",
      "external_reference": "TXN-987654321"
    }
  ],
  "page_number": 1,
  "page_size": 10,
  "total_elements": 1,
  "total_pages": 1
}
```

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `page` < 1, o enumeración inválida | No | [SPEC RF-009] |
| 400 | `PAGE_NOT_FOUND` | La página solicitada excede el total de páginas disponibles | No | [SPEC casos extremos] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es Propietario ni Administrador Financiero | No | [SPEC RF-011] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

## 6. Idempotencia y reintentos

Solo lectura; seguro reintentar.

## 7. Trazabilidad

HU1, HU2 · RF-001 a RF-013 · RNF-001 a RNF-003 · CE-001 a CE-005.
