# Contratos del sistema financiero de SEA-SHARE (Módulo 3)

Un archivo `.md` por contrato. Todo el contenido se deriva de los SPEC de `docs/features/` (única fuente de verdad). Lo que un SPEC no define **no se inventa**: se marca y se enlaza a un punto abierto (`OQ-xx`) del [plan general §12.1](../general-plan.md).

## 1. Leyenda

| Etiqueta | Significado |
|---|---|
| **[SPEC]** | Lo dice textualmente un SPEC. Se cita el UC y el RF/RNF/CE/HU. |
| **[CONV]** | Convención técnica necesaria para materializar el SPEC (ruta, nombre de campo, código HTTP, nombre de cola). No cambia el comportamiento de negocio. |
| **[PEND]** | El SPEC no lo define. Se aplica la propuesta por defecto y se referencia el `OQ-xx`. |

Los ejemplos usan **valores ilustrativos**.

## 2. Índice de contratos

### REST

| Archivo | Operación | Quién → quién | UC |
|---|---|---|---|
| [`rest/UC01-estimacion-lote.md`](rest/UC01-estimacion-lote.md) | `POST /api/v1/estimates/batch` | Reservas → sistema | UC01 |
| [`rest/UC01-estimacion-individual.md`](rest/UC01-estimacion-individual.md) | `POST /api/v1/estimates/individual` | Reservas → sistema | UC01 |
| [`rest/UC04-valor-calculado-reserva.md`](rest/UC04-valor-calculado-reserva.md) | `POST /api/v1/reservations/{reservation_id}/calculated-value` | Reservas → sistema | UC04 |
| [`rest/UC06-confirmacion-pago.md`](rest/UC06-confirmacion-pago.md) | `GET /api/v1/reservations/{reservation_id}/payment-confirmation` | Reservas → sistema | UC06 |
| [`rest/UC11-obtener-parametros-financieros.md`](rest/UC11-obtener-parametros-financieros.md) | `GET /api/v1/admin/financial-parameters` | Administrador Financiero → sistema | UC11 |
| [`rest/UC11-guardar-parametros-financieros.md`](rest/UC11-guardar-parametros-financieros.md) | `PUT /api/v1/admin/financial-parameters` | Administrador Financiero → sistema | UC11 |
| [`rest/UC12-consultar-registros-financieros.md`](rest/UC12-consultar-registros-financieros.md) | `GET /api/v1/financial-records` | Propietario / Administrador Financiero → sistema | UC12 |
| [`rest/UC13-consultar-informe-financiero.md`](rest/UC13-consultar-informe-financiero.md) | `GET /api/v1/financial-reports` | Propietario / Administrador Financiero → sistema | UC13 |
| [`rest/UC13-exportar-informe-financiero.md`](rest/UC13-exportar-informe-financiero.md) | `GET /api/v1/financial-reports/export` | Propietario / Administrador Financiero → sistema | UC13 |

### Colas de eventos (RabbitMQ, unidireccionales)

| Archivo | Routing key | Quién → quién | UC |
|---|---|---|---|
| [`events/UC03-informacion-reserva.md`](events/UC03-informacion-reserva.md) | `reservation.info.provided` | Reservas → sistema | UC03 |
| [`events/UC07-estado-reserva.md`](events/UC07-estado-reserva.md) | `reservation.status.changed` | Reservas → sistema | UC07 |
| [`events/UC08-disputa-garantia.md`](events/UC08-disputa-garantia.md) | `reservation.dispute.updated` | Reservas → sistema | UC08 |

### Integraciones externas

| Archivo | Operación | Quién → quién | UC |
|---|---|---|---|
| [`external/flota-consulta-tarifas-base.md`](external/flota-consulta-tarifas-base.md) | `POST /api/v1/boats/base-rates/query` (contrato **requerido** a Flota) | Sistema → Flota | UC01, UC02, UC03 |
| [`external/pasarela-comando-cobro.md`](external/pasarela-comando-cobro.md) | Comando de autorización/cobro | Sistema → Pasarela | UC05 |
| [`external/pasarela-comando-reembolso.md`](external/pasarela-comando-reembolso.md) | Comando de liberación/reembolso | Sistema → Pasarela | UC09 |
| [`external/pasarela-comando-liquidacion.md`](external/pasarela-comando-liquidacion.md) | Comando de captura/liquidación | Sistema → Pasarela | UC10 |
| [`external/pasarela-webhook-resultados.md`](external/pasarela-webhook-resultados.md) | `POST /api/v1/webhooks/payment-gateway` | Pasarela → sistema | UC05, UC09, UC10 |

Casos de uso **sin contrato propio**:

- **UC02** (Brindar tarifa base): solo interno (RF-003); su única conexión externa es la consulta a Flota.
- **UC05**: no tiene endpoint público; lo dispara UC07 con el estado `PENDIENTE` (UC07 RF-002A).
- **UC09 y UC10**: los invocan UC07, UC08 y los eventos automáticos; solo se comunican hacia afuera con la pasarela.
- **UC11 "Cancelar"**: no genera petición; descartar cambios no persistidos es una acción del cliente (UC11 RF-010).
- **Eventos automáticos de UC08** (24 h sin disputa; disputa `PENDIENTE` más de 7 días): los dispara el sistema, no se reciben por cola.

## 3. Convenciones comunes

### 3.1 Formatos de datos

| Dato | Formato | Origen |
|---|---|---|
| Cuerpos JSON | UTF-8, claves en `snake_case` (`boat_ids` ya viene fijado por UC01) | [CONV] |
| Valores de enumeración | En español e idénticos a los SPEC (`PENDIENTE`, `RECHAZADO`, `CANCELADO_TARDIAMENTE`…) | [CONV] |
| Identificadores | UUID (la embarcación es UUID en `docs/context/sea-share.md`; el resto, OQ-05) | [PEND] OQ-05 |
| Fechas | `YYYY-MM-DD` | [CONV] |
| Instantes | ISO-8601 en UTC, por ejemplo `2026-10-01T15:04:05Z` | [CONV] |
| Dinero | **String decimal** (`"350000.00"`), nunca número JSON; el sistema usa `BigDecimal` | [SPEC RNF-002] + [CONV] |
| Porcentajes | String decimal de 0 a 100 (`"15.00"` = 15 %), coherente con `tarifa × (1 + % / 100)` | [SPEC UC02 RF-002] + [CONV] |
| Moneda, escala y redondeo | No definidos | [PEND] OQ-03 |

### 3.2 Headers comunes (REST)

**Petición**

| Header | Obligatorio | Valor | Origen |
|---|---|---|---|
| `Authorization` | Sí | `Bearer <token>` | [PEND] OQ-01 (los SPEC fijan quién puede usar cada caso de uso, no el mecanismo) |
| `Content-Type` | Sí en `POST`/`PUT` con cuerpo | `application/json` | [CONV] |
| `Accept` | No | `application/json` (o `text/csv` en la exportación) | [CONV] |
| `X-Correlation-Id` | No | Cadena libre para trazabilidad; si se omite, el sistema genera una | [CONV] |

**Respuesta**

| Header | Valor | Origen |
|---|---|---|
| `Content-Type` | `application/json` en éxito; `application/problem+json` en error | [CONV] |
| `X-Correlation-Id` | El recibido o el generado | [CONV] |

### 3.3 Formato de error

Todos los errores REST usan *Problem Details* (RFC 9457) con dos campos adicionales. Los SPEC solo exigen "un error controlado" y que no haya cálculos parciales ni cambios de estado; el cuerpo y los códigos HTTP son **[CONV]** (OQ-02).

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string (URI) | `about:blank` mientras no se publique un catálogo de documentación |
| `title` | string | Resumen corto del error |
| `status` | integer | Código HTTP |
| `detail` | string | Explicación específica de esta ocurrencia |
| `code` | string | Código estable, legible por máquina (catálogo de §3.4) |
| `retryable` | boolean | Si repetir la misma petición más tarde puede tener éxito |
| `errors` | array (opcional) | En `VALIDATION_ERROR`: `[{ "field": "...", "message": "..." }]` |

```json
{
  "type": "about:blank",
  "title": "Estimación no disponible",
  "status": 503,
  "detail": "El Sistema de Gestión de Flota no respondió dentro del tiempo de espera.",
  "code": "FLEET_UNAVAILABLE",
  "retryable": true
}
```

### 3.4 Catálogo de códigos de error

| `code` | HTTP | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Cuerpo/parámetros mal formados, campos obligatorios ausentes, valores fuera de rango | No | [SPEC UC11 RF-013, UC12 RF-009]; resto [CONV] |
| `BATCH_SIZE_EXCEEDED` | 400 | El lote de UC01 supera el máximo de identificadores | No | [SPEC UC01 RF-006] |
| `INVALID_DATE_RANGE` | 400 | Formato de fecha inválido, inicio en el pasado o fin anterior al inicio (UC01 individual) | No | [SPEC UC01 casos extremos] |
| `INVALID_PERIOD` | 400 | Periodicidad o período inválido, o rango libre de fechas (UC13) | No | [SPEC UC13 RF-004, casos extremos] |
| `UNAUTHENTICATED` | 401 | Credencial ausente o inválida | No | [PEND] OQ-01 |
| `FORBIDDEN` | 403 | Rol no autorizado para el caso de uso | No | [SPEC UC11 RF-008/CE-003, UC12 RF-011] |
| `RESERVATION_INFO_NOT_FOUND` | 404 | UC04 sin información de reserva registrada | Sí (posible carrera con UC03, OQ-06) | [SPEC UC04 RF-009] |
| `CHARGE_INTENT_NOT_FOUND` | 404 | UC06 sin `IntenciónDeCobro` para la reserva | Sí (UC05 la crea de forma asíncrona tras `PENDIENTE`) | [SPEC UC06 RF-005] + [CONV] |
| `PAGE_OUT_OF_RANGE` | 404 | UC12: la página solicitada no existe | No | [SPEC UC12 RF-009] |
| `BASE_RATE_NOT_AVAILABLE` | 422 | UC01 individual: embarcación sin tarifa base (o no reconocida por Flota, OQ-11) | No | [SPEC UC01 casos extremos] |
| `RESERVATION_INFO_INCOMPLETE` | 422 | UC04: información registrada incompleta (por ejemplo, sin tarifa base) | No | [SPEC UC04 RNF-003] |
| `PARAMETERS_SAVE_FAILED` | 500 | UC11: la persistencia falló; la configuración anterior se conserva íntegra | Sí | [SPEC UC11 HU4 esc. 3] |
| `INTERNAL_ERROR` | 500 | Error no previsto | Sí | [CONV] |
| `FLEET_UNAVAILABLE` | 503 | Flota caída, *timeout* o inalcanzable | Sí | [SPEC UC01 casos extremos, RNF-003] |
| `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | 503 | Falta la tarifa de seguro o el porcentaje que exige la fecha evaluada (UC01, UC04) | No (hasta que el Administrador Financiero configure) | [SPEC UC01 y UC04 casos extremos] |

`401`, `403` y `500 INTERNAL_ERROR` aplican a **todos** los contratos REST y se repiten en cada uno solo cuando tienen una particularidad.

### 3.5 Contratos de colas (resumen)

Los tres contratos de `events/` comparten:

| Propiedad AMQP | Valor | Origen |
|---|---|---|
| Exchange | `seashare.reservations` (`topic`, durable) | [CONV] OQ-09 |
| `content_type` | `application/json` | [CONV] |
| `delivery_mode` | `2` (persistente) | [CONV] |
| Respuesta al productor | **Ninguna** (unidireccional) | [SPEC UC03 RF-006, UC07 RF-009, UC08 RF-009] |
| Fallos | Se registran internamente (`operational_failure`) | [SPEC UC03 RNF-003, UC07 RF-010, UC08 RNF-003] |