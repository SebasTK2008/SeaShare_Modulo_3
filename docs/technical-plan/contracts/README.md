# Contratos del sistema financiero de SEA-SHARE (Módulo 3)

Un archivo `.md` por contrato. Todo el contenido se deriva de los SPEC de `docs/features/` (única fuente de verdad). Lo que un SPEC no define **no se inventa**: se marca y se enlaza a un punto abierto (`OQ-xx`) del [plan general §12.1](../general-plan.md).

**Qué prevalece en caso de conflicto**

- **Rutas**: prevalece el contrato específico. El índice de la §2 solo las replica; si difieren, se corrige el índice.
- **Códigos de error**: prevalece la [tabla de decisión de la §4](#4-tabla-de-decisión-de-errores). Cada contrato debe usar el `code` y el HTTP del catálogo de la §3.4, que se deriva de esa tabla.

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
| [`rest/UC11-obtener-parametros-financieros.md`](rest/UC11-obtener-parametros-financieros.md) | `GET /api/v1/financial-parameters` | Administrador Financiero → sistema | UC11 |
| [`rest/UC11-guardar-parametros-financieros.md`](rest/UC11-guardar-parametros-financieros.md) | `PUT /api/v1/financial-parameters` | Administrador Financiero → sistema | UC11 |
| [`rest/UC12-consultar-registros-financieros.md`](rest/UC12-consultar-registros-financieros.md) | `GET /api/v1/financial-records` | Propietario / Administrador Financiero → sistema | UC12 |
| [`rest/UC13-consultar-informe-financiero.md`](rest/UC13-consultar-informe-financiero.md) | `GET /api/v1/financial-report` | Propietario / Administrador Financiero → sistema | UC13 |
| [`rest/UC13-exportar-informe-financiero.md`](rest/UC13-exportar-informe-financiero.md) | `GET /api/v1/financial-report/export` | Propietario / Administrador Financiero → sistema | UC13 |

### Colas de eventos (RabbitMQ, unidireccionales)

| Archivo | Routing key | Cola consumidora | Quién → quién | UC |
|---|---|---|---|---|
| [`events/UC03-informacion-reserva.md`](events/UC03-informacion-reserva.md) | `reservation.info.provided` | `finance.reservation-info.v1` | Reservas → sistema | UC03 |
| [`events/UC07-estado-reserva.md`](events/UC07-estado-reserva.md) | `reservation.status.changed` | `finance.reservation-status.v1` | Reservas → sistema | UC07 |
| [`events/UC08-disputa-garantia.md`](events/UC08-disputa-garantia.md) | `reservation.dispute.updated` | `finance.guarantee-dispute.v1` | Reservas → sistema | UC08 |

### Integraciones externas

| Archivo | Operación | Quién → quién | UC |
|---|---|---|---|
| [`external/flota-consulta-tarifas-base.md`](external/flota-consulta-tarifas-base.md) | `POST /api/v1/fleet/base-rates` (contrato **requerido** a Flota) | Sistema → Flota | UC01, UC02, UC03 |
| [`external/pasarela-comando-cobro.md`](external/pasarela-comando-cobro.md) | Comando de autorización/cobro | Sistema → Pasarela | UC05 |
| [`external/pasarela-comando-reembolso.md`](external/pasarela-comando-reembolso.md) | Comando de liberación/reembolso | Sistema → Pasarela | UC09 |
| [`external/pasarela-comando-liquidacion.md`](external/pasarela-comando-liquidacion.md) | Comando de captura/liquidación | Sistema → Pasarela | UC10 |
| [`external/pasarela-webhook-resultados.md`](external/pasarela-webhook-resultados.md) | `POST /api/v1/webhook/gateway?data.id=...&type=...` (Mercado Pago → sistema, flujo de dos pasos) | Pasarela → sistema | UC05, UC09, UC10 |

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
| Rutas | Prefijo `/api/v1/`, segmentos en minúscula con guiones. La ruta del contrato específico es la fuente de verdad (§ inicio) | [CONV] |
| Cuerpos JSON | UTF-8, claves en `snake_case` (`boat_ids` ya viene fijado por UC01) | [CONV] |
| Valores de enumeración | En español e idénticos a los SPEC (`PENDIENTE`, `RECHAZADO`, `CANCELADO_TARDIAMENTE`…) | [CONV] |
| Identificadores | UUID (la embarcación es UUID en `docs/context/sea-share.md`; el resto, OQ-05) | [PEND] OQ-05 |
| Fechas | `YYYY-MM-DD` | [CONV] |
| Instantes | ISO-8601 en UTC, por ejemplo `2026-10-01T15:04:05Z` | [CONV] |
| Dinero | **String decimal** (`"350000.00"`), nunca número JSON; el sistema usa `BigDecimal` | [SPEC RNF-002] + [CONV] |
| Porcentajes | String decimal de 0 a 100 (`"15.00"` = 15 %), coherente con `tarifa × (1 + % / 100)` | [SPEC UC02 RF-002] + [CONV] |
| Períodos de informe (`period`) | `YYYY-MM-H1`/`YYYY-MM-H2` (quincenal), `YYYY-MM` (mensual), `YYYY-Q1`…`YYYY-Q4` (trimestral) | [CONV] UC13 |
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
| `Retry-After` | Opcional en `503`, en segundos | [CONV] |

### 3.3 Formato de error

Todos los errores REST usan *Problem Details* (RFC 9457) con dos campos adicionales. Los SPEC solo exigen "un error controlado" y que no haya cálculos parciales ni cambios de estado; el cuerpo y los códigos HTTP son **[CONV]** (OQ-02) y se asignan con la [tabla de decisión de la §4](#4-tabla-de-decisión-de-errores).

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string (URI) | `about:blank` mientras no se publique un catálogo de documentación |
| `title` | string | Resumen corto del error |
| `status` | integer | Código HTTP |
| `detail` | string | Explicación específica de esta ocurrencia. En `5xx` es genérica: sin trazas, SQL ni nombres internos (§4.3) |
| `code` | string | Código estable, legible por máquina (catálogo de §3.4) |
| `retryable` | boolean | `true` si repetir **la misma petición, sin cambiarla**, puede tener éxito más adelante |
| `errors` | array (opcional) | En `VALIDATION_ERROR`: `[{ "field": "...", "message": "..." }]` |

```json
{
  "type": "about:blank",
  "title": "Estimación no disponible",
  "status": 503,
  "detail": "El Sistema de Gestión de Flota no está disponible (sin respuesta dentro del tiempo de espera).",
  "code": "FLEET_UNAVAILABLE",
  "retryable": true
}
```

### 3.4 Catálogo de códigos de error

Ordenado por HTTP. La columna **Fila** remite a la fila de la tabla de decisión (§4.2) que justifica el código. Ningún contrato define un `code` ni un HTTP que no esté aquí; si un caso nuevo lo requiere, primero se ubica su fila en la §4 y luego se agrega al catálogo.

| `code` | HTTP | Cuándo | `retryable` | Origen | Fila |
|---|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Cuerpo/parámetros mal formados, campos obligatorios ausentes o `null`, valores fuera de rango, campos no aceptados por el contrato | No | [SPEC UC11 RF-013, UC12 RF-009]; resto [CONV] | E4 |
| `BATCH_SIZE_EXCEEDED` | 400 | El lote de UC01 supera el máximo de identificadores | No | [SPEC UC01 RF-006] | E4 |
| `INVALID_DATE_RANGE` | 400 | Formato de fecha inválido, inicio en el pasado o fin anterior al inicio (UC01 individual) | No | [SPEC UC01 casos extremos] | E4 |
| `INVALID_PERIOD` | 400 | Periodicidad o período inválido, o rango libre de fechas (UC13) | No | [SPEC UC13 RF-004, casos extremos] | E4 |
| `UNAUTHENTICATED` | 401 | Credencial ausente o inválida | No | [PEND] OQ-01 | E1 |
| `FORBIDDEN` | 403 | Rol no autorizado para el caso de uso | No | [SPEC UC11 RF-008/CE-003, UC12 RF-011] | E2 |
| `RESERVATION_INFO_NOT_FOUND` | 404 | UC04 sin información de reserva registrada | Sí (posible carrera con UC03, OQ-06) | [SPEC UC04 RF-009] | E5 |
| `CHARGE_INTENT_NOT_FOUND` | 404 | UC06 sin `IntenciónDeCobro` para la reserva | Sí (UC05 la crea de forma asíncrona tras `PENDIENTE`) | [SPEC UC06 RF-005] + [CONV] | E5 |
| `PARAMETERS_NOT_CONFIGURED` | 404 | UC11 obtener: todavía no existe configuración | Sí (cuando el Administrador Financiero la guarde) | [SPEC UC11 HU3 esc. 2] | E5 |
| `PAGE_OUT_OF_RANGE` | 404 | UC12: la página solicitada no existe | No | [SPEC UC12 RF-009] | E5 |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP no soportado en la ruta | No | [CONV] | E3 |
| `NOT_ACCEPTABLE` | 406 | `Accept` no soportado por la operación | No | [CONV] | E3 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` distinto de `application/json` en `POST`/`PUT` | No | [CONV] | E3 |
| `BASE_RATE_NOT_AVAILABLE` | 422 | UC01 individual: embarcación sin tarifa base (o no reconocida por Flota, OQ-11) | No | [SPEC UC01 casos extremos] | E8 |
| `RESERVATION_INFO_INCOMPLETE` | 422 | UC04: información registrada incompleta (por ejemplo, sin tarifa base) | No | [SPEC UC04 RNF-003] | E8 |
| `PARAMETERS_SAVE_FAILED` | 500 | UC11: la persistencia falló; la configuración anterior se conserva íntegra | Sí | [SPEC UC11 HU4 esc. 3] | E10 |
| `INTERNAL_ERROR` | 500 | Error no previsto | Sí | [CONV] | E10 |
| `FLEET_UNAVAILABLE` | 503 | Flota caída, inalcanzable, con el *circuit breaker* abierto, sin respuesta (*timeout*) o con una respuesta inválida | Sí | [SPEC UC01 casos extremos, RNF-003] + [CONV] | E7 |
| `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | 503 | Falta la tarifa de seguro o el porcentaje que exige la fecha evaluada (UC01, UC04) | Sí (cuando el Administrador Financiero configure) | [SPEC UC01 y UC04 casos extremos] + [CONV] | E6 |

`401`, `403` y `500 INTERNAL_ERROR` aplican a **todos** los contratos REST y se repiten en cada uno solo cuando tienen una particularidad. `405`, `406` y `415` los emite el framework y tampoco hace falta repetirlos salvo que el contrato los condicione (por ejemplo, `406` en la exportación).

### 3.5 Contratos de colas (resumen)

Los tres contratos de `events/` comparten:

| Propiedad AMQP | Valor | Origen |
|---|---|---|
| Exchange | `seashare.reservations` (`topic`, durable) | [CONV] OQ-09 |
| `content_type` | `application/json` | [CONV] |
| `delivery_mode` | `2` (persistente) | [CONV] |
| Respuesta al productor | **Ninguna** (unidireccional) | [SPEC UC03 RF-006, UC07 RF-009, UC08 RF-009] |
| Fallos | Se registran internamente (`operational_failure`) | [SPEC UC03 RNF-003, UC07 RF-010, UC08 RNF-003] |
| Qué hacer ante cada tipo de error | Ver §4.4 | [CONV] |

## 4. Tabla de decisión de errores

Todo contrato nuevo o modificado decide sus errores con esta sección. El objetivo es que dos personas, ante la misma situación, lleguen al mismo `code`, HTTP y `retryable`.

### 4.1 Principio

| Clase | Significa | Quién puede corregirlo | El cliente debe |
|---|---|---|---|
| **4xx** | La petición, tal como llegó, no puede tener éxito. | El llamador (cambiando la petición, su identidad o esperando a que un dato exista) | No reintentar igual, salvo `retryable: true` |
| **5xx** | La petición era válida; falló el sistema o una dependencia. | El sistema, una dependencia o un operador, nunca el llamador | Reintentar con *backoff* según `retryable` |

Pregunta de desempate: **¿existe alguna forma de que el llamador arregle esto cambiando lo que envía?** Si sí, es 4xx. Si el resultado sería el mismo para cualquier petición válida, es 5xx.

### 4.2 Tabla de decisión

Se evalúa **en orden**; se aplica la primera fila cuya situación se cumpla. `E10` puede ocurrir en cualquier paso.

| Fila | Situación | HTTP | `code` | `retryable` | Quién lo corrige | Ejemplos |
|---|---|---|---|---|---|---|
| **E1** | Credencial ausente, vencida o inválida | 401 | `UNAUTHENTICATED` | No | Llamador | Cualquier REST sin token |
| **E2** | Credencial válida, pero el rol no puede usar el caso de uso | 403 | `FORBIDDEN` | No | Administración de accesos | UC11 con rol distinto de Administrador Financiero; UC01/UC04/UC06 con un llamador que no es Reservas |
| **E3** | Método, `Content-Type` o `Accept` no soportados | 405 / 415 / 406 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` / `NOT_ACCEPTABLE` | No | Llamador | `PUT` sin `application/json`; exportación pidiendo `Accept: application/xml` |
| **E4** | La petición está mal formada o fuera del dominio permitido, **sin consultar ningún dato**: JSON no parseable, campo obligatorio ausente o `null`, tipo, formato, enumeración o rango inválido, regla entre campos violada, fecha pasada, lote sobre el máximo | 400 | `VALIDATION_ERROR`; o el específico: `BATCH_SIZE_EXCEEDED`, `INVALID_DATE_RANGE`, `INVALID_PERIOD` | No | Llamador | `passengers` negativo; `end_date` anterior a `start_date`; 1 000 `boat_ids` |
| **E5** | El recurso que identifica la **URL** no existe todavía, o la página pedida excede el total | 404 | `RESERVATION_INFO_NOT_FOUND`, `CHARGE_INTENT_NOT_FOUND`, `PARAMETERS_NOT_CONFIGURED`, `PAGE_OUT_OF_RANGE` | **Sí** solo si un proceso asíncrono puede crearlo después; **No** en `PAGE_OUT_OF_RANGE` | Llamador (corregir el ID o esperar) | UC04 antes de que UC03 termine; UC06 antes de que UC05 cree la intención; UC11 obtener sin configuración |
| **E6** | Falta una **configuración interna global** que cualquier petición válida necesitaría, idéntica para todos los llamadores | 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Sí | Operador (Administrador Financiero) | UC01/UC04 sin tarifa de seguro o sin el porcentaje que exige la fecha |
| **E7** | Falla una **dependencia externa**: está caída, inalcanzable o con *circuit breaker* abierto, no responde dentro del tiempo de espera, o respondió algo inválido | 503 | `FLEET_UNAVAILABLE` | Sí | Dependencia u operador | Flota con *timeout* de 1 s; Flota devuelve un cuerpo que no es JSON |
| **E8** | La petición es válida, pero los **datos del recurso referenciado en el cuerpo, o ya registrado**, impiden la operación | 422 | `BASE_RATE_NOT_AVAILABLE`, `RESERVATION_INFO_INCOMPLETE` | No | Dueño del dato (Propietario, Reservas) | `boat_id` sin tarifa base o desconocido para Flota; información de reserva sin tarifa base |
| **E9** | Conflicto con el estado actual de una entidad: transición inválida, duplicado no idempotente | 409 | *Sin uso hoy.* Se define el `code` en el primer contrato que lo requiera | No | Llamador | Ninguno vigente |
| **E10** | Falla propia no clasificable: excepción no controlada, base de datos o persistencia, o la dependencia respondió `4xx` a una petición que **construimos nosotros** | 500 | `INTERNAL_ERROR`, o el específico del contrato: `PARAMETERS_SAVE_FAILED` | Sí | Equipo del sistema | UC11 guardar con la BD caída; Flota responde `400` a nuestro cuerpo mal armado |

### 4.3 Reglas de uso y desempate

1. **Orden de evaluación**: E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8. Una petición inválida siempre da `4xx`, aunque la dependencia también esté caída: no se gasta una llamada externa en una petición que ya es inválida. Un contrato puede declarar un orden más específico dentro de E5 y E8, nunca alterar el orden entre filas.
2. **`404` solo para el recurso de la URL.** Un identificador inexistente dentro del **cuerpo** (por ejemplo `boat_id`) es E8 (`422`), no `404`.
3. **La fecha pasada es E4 (`400`)** aunque dependa del reloj: la petición es inválida por sí misma.
4. **Datos de la dependencia vs. fallo de la dependencia.** Si Flota responde bien y dice "no tiene tarifa" o "no la conozco", es E8. Si Flota no responde, responde fuera de tiempo o responde algo ilegible, es E7. Si Flota rechaza nuestra petición por estar mal construida, es E10: el llamador original no pudo evitarlo.
5. **Configuración global vs. dato del recurso.** Si lo que falta es igual para toda petición válida (parámetros financieros), es E6 (`5xx`). Si depende del identificador que envió el llamador (tarifa de **esa** embarcación), es E8 (`4xx`).
6. **`retryable` en 4xx** es `false` por defecto. Solo se marca `true` cuando el contrato documenta que otro proceso puede crear el recurso después (E5 asíncrono). **`retryable` en 5xx** es `true`; no se usa `503` con `retryable: false`, porque `503` ya significa "intenta más tarde".
7. **Nunca valores parciales**: ante E6, E7 o E8 se devuelve el error completo, jamás una estimación o desglose parcial (principio rector 4 del plan general).
8. **Alcance del Propietario**: UC12 y UC13 resuelven el alcance filtrando por la identidad autenticada. Un Propietario que pide datos de otro obtiene el resultado de su propio alcance, no un `403` ni un `404`.
9. **Detalle del error**: en `4xx` es específico y accionable (`field` y `message`). En `5xx` es genérico: sin trazas, SQL, nombres de clase ni URLs internas. El detalle técnico va al log con el `X-Correlation-Id`.
10. **Prohibido**: responder `200` con un cuerpo de error; `500` por una validación; `4xx` por una falla de dependencia; códigos nuevos que no estén en el catálogo de la §3.4. El `429` no se usa: ningún SPEC define límite de tasa.

### 4.4 Colas y webhook

Los canales sin respuesta al productor no pueden usar HTTP. Se aplican las mismas ideas: lo que el reintento no puede arreglar se confirma y se registra; lo transitorio se reintenta.

| Canal | Situación | Acción | Origen |
|---|---|---|---|
| Cola (UC03, UC07, UC08) | Mensaje inválido, estado desconocido, reserva sin información previa, validación fallida (equivale a E4/E8) | `ack` y registro en `operational_failure`. Sin reintento ni DLQ: reintentar no cambia el resultado | [SPEC UC03 RNF-003, UC07 RF-010, UC08 RNF-003] |
| Cola (UC03, UC08) | Falla transitoria: BD, Flota (equivale a E7/E10) | `nack` con reintentos y *backoff* (3–5, plan general §7.2); al agotarlos va a la DLQ y se registra en `operational_failure` | [CONV] |
| Cola (UC07, UC08) | Mensaje repetido (misma clave idempotente) | `ack` sin operación monetaria adicional | [SPEC UC07 casos extremos, UC08 RF-008] |
| Webhook | Firma ausente o inválida (E1) | `401 UNAUTHENTICATED` | [CONV] |
| Webhook | Payload mal formado (E4) | `400 VALIDATION_ERROR` | [CONV] |
| Webhook | Resultado ya registrado (duplicado) | `200 OK`, sin modificar el registro | [SPEC UC05 casos extremos, UC10 casos extremos] |
| Webhook | Falla interna: BD no disponible (E10) | `500 INTERNAL_ERROR`, para que la Pasarela reintente | [CONV] |
| Webhook | Resultado sin `IntenciónDe…` asociada (`idempotency_key` desconocida) | **[PEND]** Propuesta: `200 OK` y registro en `operational_failure`, para evitar reintentos infinitos de la Pasarela. El SPEC UC05 exige registrar el evento sin crear un registro de cobro, pero no define la respuesta | [SPEC UC05 casos extremos] + [PEND] OQ por asignar |

### 4.5 Lista de verificación para cada contrato

1. Cada fila de la tabla de errores del contrato usa un `code` y un HTTP **idénticos** a los del catálogo (§3.4).
2. El contrato declara el orden de validación aplicable (E1 → E8) cuando tiene más de una causa posible de error.
3. Ningún `5xx` expone detalles internos y ningún `4xx` esconde una falla propia.
4. Todo `retryable: true` en un `4xx` tiene su justificación asíncrona escrita en el contrato.
5. Si el contrato necesita un error que no existe, se agrega primero a la §4.2 y al catálogo, no solo al contrato.

## Anexo A. Cambios de esta versión y contratos pendientes de ajuste

Este anexo es transitorio: se puede eliminar cuando los contratos listados estén actualizados. Los cambios de la §A.1 a la §A.3 ya están aplicados en los contratos y las decisiones de la §A.4 están confirmadas; las tablas se conservan como registro del cambio.

### A.1 Rutas del índice corregidas

| Contrato | Ruta anterior en este índice | Ruta actual (la del contrato) |
|---|---|---|
| UC11 obtener | `GET /api/v1/admin/financial-parameters` | `GET /api/v1/financial-parameters` |
| UC11 guardar | `PUT /api/v1/admin/financial-parameters` | `PUT /api/v1/financial-parameters` |
| UC13 consultar | `GET /api/v1/financial-reports` | `GET /api/v1/financial-report` |
| UC13 exportar | `GET /api/v1/financial-reports/export` | `GET /api/v1/financial-report/export` |
| Flota | `POST /api/v1/boats/base-rates/query` | `POST /api/v1/fleet/base-rates` |
| Webhook de la Pasarela | `POST /api/v1/webhooks/payment-gateway` | `POST /api/v1/webhook/gateway` |

Las demás rutas (UC01, UC04, UC06, UC12) ya coincidían. Se agregó también la columna de cola consumidora en la tabla de eventos, tomada de cada contrato.

### A.2 Cambios en el catálogo de errores

| Cambio | Detalle |
|---|---|
| Código agregado | `PARAMETERS_NOT_CONFIGURED` (404): ya lo usaba el contrato UC11 obtener y faltaba en el catálogo |
| Códigos nuevos | `METHOD_NOT_ALLOWED` (405), `NOT_ACCEPTABLE` (406), `UNSUPPORTED_MEDIA_TYPE` (415) |
| `FLEET_UNAVAILABLE` | Único código de E7: `503` cubriendo caída, inalcanzable, *circuit breaker* abierto, *timeout* y respuesta inválida. No se incorporan `FLEET_TIMEOUT` (504) ni `FLEET_INVALID_RESPONSE` (502) (decisión §A.4.3: no ampliar el catálogo) |
| `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | `retryable` pasa de `No` a `Sí`: `503` con `retryable: false` era contradictorio (regla 6 de la §4.3) |

### A.3 Contratos que deben ajustarse a la tabla de decisión

| Contrato | Hoy | Debe quedar |
|---|---|---|
| UC01 lote e individual | `FLEET_UNAVAILABLE` cubre *timeout* y caída; `FINANCIAL_PARAMETERS_NOT_CONFIGURED` con `retryable: No` | Mantener `FLEET_UNAVAILABLE` (503) unificado para caída, *timeout*, inalcanzable y respuesta inválida (§A.4.3); `retryable: Sí` en `FINANCIAL_PARAMETERS_NOT_CONFIGURED`; declarar el orden de validación |
| UC04 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` con `retryable: No` | `retryable: Sí` |
| UC11 guardar | No lista `PARAMETERS_SAVE_FAILED` | Agregar `500 PARAMETERS_SAVE_FAILED`, `retryable: Sí` |
| UC12 | `PAGE_NOT_FOUND`, HTTP 400 | `PAGE_OUT_OF_RANGE`, HTTP 404, `retryable: No` |
| UC13 consultar y exportar | Período o periodicidad inválidos como `VALIDATION_ERROR` | `INVALID_PERIOD` (400) |
| Webhook de la Pasarela | `UNAUTHORIZED` (401) y `BAD_REQUEST` (400) | `UNAUTHENTICATED` (401) y `VALIDATION_ERROR` (400), según §4.4 |
| Webhook de la Pasarela | No define la respuesta ante una `idempotency_key` desconocida | Definir según la propuesta [PEND] de §4.4 |

### A.4 Decisiones que requieren confirmación

1. `FINANCIAL_PARAMETERS_NOT_CONFIGURED` se mantiene como `5xx` (503) con `retryable: true`, porque la petición es válida y el problema es del estado del sistema. **[Confirmado]**
2. `404` queda reservado para el recurso de la URL; un `boat_id` inexistente en el cuerpo es `422`. **[Confirmado]**
3. Se introducen `502` y `504` para distinguir dependencias externas; si se prefiere no ampliar el catálogo, se pueden fusionar en `FLEET_UNAVAILABLE` (503). **[Decidido: no se amplía el catálogo; `FLEET_TIMEOUT` (504) y `FLEET_INVALID_RESPONSE` (502) quedan fusionados en `FLEET_UNAVAILABLE` (503)]**
4. La página fuera de rango pasa a `404`, coherente con la regla de "recurso inexistente". **[Confirmado]**