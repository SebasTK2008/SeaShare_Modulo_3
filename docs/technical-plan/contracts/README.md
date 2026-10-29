# Contratos del sistema financiero de SEA-SHARE (Módulo 3)

Un archivo `.md` por contrato. Todo el contenido se deriva de los SPEC de `docs/features/` (única fuente de verdad). Lo que un SPEC no define **no se inventa**: se resuelve con la convención documentada en este README o en el contrato correspondiente.

**Qué prevalece en caso de conflicto**

- **Rutas**: prevalece el contrato específico. El índice de la §2 solo las replica; si difieren, se corrige el índice.
- **Códigos de error**: prevalece la [tabla de decisión de la §4](#4-tabla-de-decisión-de-errores). Cada contrato debe usar el `code` y el HTTP del catálogo de la §3.4, que se deriva de esa tabla.

## 1. Leyenda

| Etiqueta | Significado |
|---|---|
| **[SPEC]** | Lo dice textualmente un SPEC. Se cita el UC y el RF/RNF/CE/HU. |
| **[CONV]** | Convención técnica necesaria para materializar el SPEC (ruta, nombre de campo, código HTTP, nombre de cola). No cambia el comportamiento de negocio. |

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
| [`external/pasarela-comando-cobro.md`](external/pasarela-comando-cobro.md) | Comando de cobro | Sistema → Pasarela (Mercado Pago) | UC05 |
| [`external/pasarela-comando-reembolso.md`](external/pasarela-comando-reembolso.md) | Comando de reembolso | Sistema → Pasarela (Mercado Pago) | UC09 |
| [`external/pasarela-comando-liquidacion.md`](external/pasarela-comando-liquidacion.md) | Comando de liquidación | Sistema → Pasarela (Mercado Pago) | UC10 |
| [`external/pasarela-webhook-resultados.md`](external/pasarela-webhook-resultados.md) | `POST /api/v1/webhook/gateway` | Pasarela (Mercado Pago) → sistema | UC05, UC09, UC10 |

Casos de uso **sin contrato propio**:

- **UC02** (Brindar tarifa base): solo interno (RF-003); su única conexión externa es la consulta a Flota.
- **UC05**: no tiene endpoint público; lo dispara UC07 con el estado `PENDING` (UC07 RF-002A).
- **UC09 y UC10**: los invocan UC07, UC08 y los eventos automáticos; solo se comunican hacia afuera con la pasarela.
- **UC11 "Cancelar"**: no genera petición; descartar cambios no persistidos es una acción del cliente (UC11 RF-010).
- **Cierres de UC08 por vencimiento de ventana** (24 h sin disputa; resolución de una disputa `PENDING`): el sistema no ejecuta temporizadores ni cron jobs; el Sistema de Reservas y Operaciones decide el cierre y lo notifica como estado final (`REJECTED`/`COMPLETED`) por la cola de UC08 [SPEC 008 RF-009, RF-009A].

## 3. Convenciones comunes

### 3.1 Formatos de datos

| Dato | Formato | Origen |
|---|---|---|
| Rutas | Prefijo `/api/v1/`, segmentos en minúscula con guiones. La ruta del contrato específico es la fuente de verdad (§ inicio) | [CONV] |
| Cuerpos JSON | UTF-8, claves en `snake_case` (`boat_ids` ya viene fijado por UC01) | [CONV] |
| Valores de enumeración | En inglés (fuente canónica del Módulo 2) (`PENDING`, `REJECTED`, `CANCELLED_STRICT`…) | [CONV] |
| Identificadores | UUID v4 (los parámetros financieros agregan `version_number` secuencial) | [CONV] |
| Fechas | `YYYY-MM-DD` | [CONV] |
| Instantes | ISO-8601 en UTC, por ejemplo `2026-10-01T15:04:05Z` | [CONV] |
| Dinero | **String decimal** (`"350000.00"`), nunca número JSON; el sistema usa `BigDecimal` | [SPEC RNF-002] + [CONV] |
| Porcentajes | String decimal de 0 a 100 (`"15.00"` = 15 %), coherente con `tarifa × (1 + % / 100)` | [SPEC UC02 RF-002] + [CONV] |
| Períodos de informe (`period`) | `YYYY-MM-H1`/`YYYY-MM-H2` (quincenal), `YYYY-MM` (mensual), `YYYY-Q1`…`YYYY-Q4` (trimestral) | [CONV] UC13 |
| Escala y precisión | `NUMERIC(18,4)` en BD; `BigDecimal` con 4 decimales internos; toda salida monetaria (REST, comandos a la pasarela y registros) redondea `HALF_UP` a 2 decimales | [SPEC RNF-002] + [CONV] |

**Nota — UC11 conserva 4 decimales**: los cuatro parámetros configurables de
`GET /api/v1/financial-parameters` se serializan con 4 decimales para que el valor leído sea
exactamente el almacenado y `PUT → GET → PUT` sea idempotente. La regla de 2 decimales sigue
aplicando al resto de salidas monetarias.

**Nota — forma numérica para la pasarela**: la forma de string decimal de esta tabla aplica a la API REST, a los contratos de colas y a los registros del sistema. Cuando la pasarela exija número JSON (por ejemplo, `transaction_amount` en `POST /v1/payments`), el adaptador de la pasarela define su propia serialización; la forma externa del sistema no cambia [CONV].

### 3.2 Headers comunes (REST)

**Petición**

| Header | Obligatorio | Valor | Origen |
|---|---|---|---|
| `Content-Type` | Sí en `POST`/`PUT` con cuerpo | `application/json` | [CONV] |
| `Accept` | No | `application/json` (o `text/csv` en la exportación) | [CONV] |
| `X-Correlation-Id` | No | Cadena libre para trazabilidad; si se omite, el sistema genera una | [CONV] |

**No hay autenticación**: el servicio no exige `Authorization` ni valida identidad alguna; la seguridad (autenticación, autorización, tokens) está **fuera del alcance** del proyecto (general-plan §7.1, D-21). Los datos de identidad o rol que necesite un caso de uso (p. ej. `owner_id` y el alcance declarado de UC12/UC13) viajan como **datos planos** en la petición.

**Respuesta**

| Header | Valor | Origen |
|---|---|---|
| `Content-Type` | `application/json` en éxito; `application/problem+json` en error | [CONV] |
| `X-Correlation-Id` | El recibido o el generado | [CONV] |
| `Retry-After` | Opcional en `503`, en segundos | [CONV] |

### 3.3 Formato de error

Todos los errores REST usan *Problem Details* (RFC 9457) con dos campos adicionales. Los SPEC solo exigen "un error controlado" y que no haya cálculos parciales ni cambios de estado; el cuerpo y los códigos HTTP son **[CONV]** y se asignan con la [tabla de decisión de la §4](#4-tabla-de-decisión-de-errores).

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string (URI) | `about:blank` mientras no se publique un catálogo de documentación |
| `title` | string | Resumen corto del error (el del catálogo de la §3.4) |
| `status` | integer | Código HTTP |
| `detail` | string | Explicación específica de esta ocurrencia. En `5xx` es genérica: sin trazas, SQL ni nombres internos (§4.3) |
| `code` | string | Código estable, legible por máquina (catálogo de §3.4) |
| `retryable` | boolean | `true` si repetir **la misma petición, sin cambiarla**, puede tener éxito más adelante |
| `errors` | array (opcional) | En `VALIDATION_ERROR`: `[{ "field": "...", "message": "..." }]` |

```json
{
  "type": "about:blank",
  "title": "Fleet service unavailable",
  "status": 503,
  "detail": "The Fleet Management System is not available (no response within the timeout).",
  "code": "FLEET_UNAVAILABLE",
  "retryable": true
}
```

**Punto único de traducción** [CONV núcleo compartido]: un solo `GlobalExceptionHandler` (`infrastructure.adapter.in.web.error`) traduce las excepciones de dominio y las del framework (`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `ServletRequestBindingException`, `BindException`, fallback) a este formato, incluidos `code` y `retryable`. Los UC **no** declaran consejos (`@RestControllerAdvice`) propios: agregan su excepción de dominio y su entrada al catálogo de la §3.4.

### 3.4 Catálogo de códigos de error

Ordenado por HTTP. La columna **Fila** remite a la fila de la tabla de decisión (§4.2) que justifica el código. Ningún contrato define un `code` ni un HTTP que no esté aquí; si un caso nuevo lo requiere, primero se ubica su fila en la §4 y luego se agrega al catálogo.

| `code` | HTTP | Cuándo | `retryable` | Origen | Fila |
|---|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Cuerpo/parámetros mal formados, campos obligatorios ausentes o `null`, valores fuera de rango, campos no aceptados por el contrato | No | [SPEC UC11 RF-013, UC12 RF-009]; resto [CONV] | E2 |
| `BATCH_SIZE_EXCEEDED` | 400 | El lote de UC01 supera el máximo de identificadores | No | [SPEC UC01 RF-006] | E2 |
| `INVALID_DATE_RANGE` | 400 | Formato de fecha inválido, inicio en el pasado o fin anterior al inicio (UC01 individual) | No | [SPEC UC01 casos extremos] | E2 |
| `INVALID_PERIOD` | 400 | Periodicidad o período inválido, o rango libre de fechas (UC13) | No | [SPEC UC13 RF-004, casos extremos] | E2 |
| `UNAUTHENTICATED` | 401 | Webhook de la Pasarela: firma `x-signature` ausente o inválida | No | [CONV] — verificación de firma de Mercado Pago (general-plan §7.1) | §4.4 |
| `RESERVATION_INFO_NOT_FOUND` | 404 | UC04 sin información de reserva registrada | Sí (posible carrera con UC03) | [SPEC UC04 RF-009] | E3 |
| `CHARGE_INTENT_NOT_FOUND` | 404 | UC06 sin `IntenciónDeCobro` para la reserva | Sí (UC05 la crea de forma asíncrona tras `PENDING`) | [SPEC UC06 RF-005] + [CONV] | E3 |
| `PARAMETERS_NOT_CONFIGURED` | 404 | UC11 obtener: todavía no existe configuración | Sí (cuando el Administrador Financiero la guarde) | [SPEC UC11 HU3 esc. 2] | E3 |
| `PAGE_OUT_OF_RANGE` | 404 | UC12: la página solicitada excede el total disponible (`page > max(total_pages, 1)`) | No | [SPEC UC12 RF-009] | E3 |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP no soportado en la ruta | No | [CONV] | E1 |
| `NOT_ACCEPTABLE` | 406 | `Accept` no soportado por la operación | No | [CONV] | E1 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type` distinto de `application/json` en `POST`/`PUT` | No | [CONV] | E1 |
| `BASE_RATE_NOT_AVAILABLE` | 422 | UC01 individual: embarcación sin tarifa base o no reconocida por Flota | No | [SPEC UC01 casos extremos] | E6 |
| `RESERVATION_INFO_INCOMPLETE` | 422 | UC04: información registrada incompleta (por ejemplo, sin tarifa base) | No | [SPEC UC04 RNF-003] | E6 |
| `PARAMETERS_SAVE_FAILED` | 500 | UC11: la persistencia falló; la configuración anterior se conserva íntegra | Sí | [SPEC UC11 HU4 esc. 3] | E8 |
| `INTERNAL_ERROR` | 500 | Error no previsto | Sí | [CONV] | E8 |
| `FLEET_UNAVAILABLE` | 503 | Flota caída, inalcanzable, con el *circuit breaker* abierto, sin respuesta (*timeout*) o con una respuesta inválida | Sí | [SPEC UC01 casos extremos, RNF-003] + [CONV] | E5 |
| `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | 503 | Falta la tarifa de seguro o el porcentaje que exige la fecha evaluada (UC01, UC04) | Sí (cuando el Administrador Financiero configure) | [SPEC UC01 y UC04 casos extremos] + [CONV] | E4 |

`500 INTERNAL_ERROR` aplica a **todos** los contratos REST y se repite en cada uno solo cuando tiene una particularidad. `405`, `406` y `415` los emite el framework y tampoco hace falta repetirlos salvo que el contrato los condicione (por ejemplo, `406` en la exportación). El código `FORBIDDEN` (403) **queda retirado del catálogo**: el sistema no implementa control de acceso (seguridad fuera de alcance; general-plan §7.1). `UNAUTHENTICATED` (401) se conserva **solo** para el webhook de la Pasarela, como parte del contrato de la integración con Mercado Pago (verificación de firma) y no como autenticación del sistema.

### 3.5 Contratos de colas (resumen)

Los contratos de `events/` y las colas internas de comando comparten:

| Propiedad AMQP | Valor | Origen |
|---|---|---|
| Exchange de eventos | `seashare.reservations` (`topic`, durable) | [CONV] |
| Colas consumidoras | `finance.reservation-info.v1` (UC03), `finance.reservation-status.v1` (UC07), `finance.guarantee-dispute.v1` (UC08); DLQ `<cola>.dlq` | [CONV] |
| Exchange de comandos | `seashare.finance.commands` (`direct`, durable) | [CONV] |
| Colas internas de comando (UC05/09/10) | `finance.charge-command.v1`, `finance.refund-command.v1`, `finance.settlement-command.v1`; DLQ `<cola>.dlq` | [CONV] |
| Cola interna de notificaciones de la pasarela (UC05/09/10) | `finance.gateway-notification.v1`; DLQ `<cola>.dlq`; cuerpo `{"type": "...", "action": "...", "data_id": "..."}`; enrutado `gateway.notification` | [CONV] UC05 (paso 2 del webhook de resultados, D-UC05-12) |
| Tipo de cola | *quorum* durable (las siete colas de trabajo) | [CONV] |
| `content_type` | `application/json` | [CONV] |
| `delivery_mode` | `2` (persistente) | [CONV] |
| Respuesta al productor | **Ninguna** (unidireccional) | [SPEC UC03 RF-006, UC07 RF-009, UC08 RF-009] |
| Fallos | Se registran internamente (`operational_failure`) | [SPEC UC03 RNF-003, UC07 RF-010, UC08 RNF-003] |
| Qué hacer ante cada tipo de error | Ver §4.4 | [CONV] |

**Payload del outbox** [CONV núcleo compartido]: dentro de su propia transacción, el caso de uso escribe en `outbox_message` el `exchange`, la `routing_key` y el **mismo payload JSON** que define el contrato de la cola (claves `snake_case` y formatos de la §3.1). `OutboxRelayJob` lo publica **verbatim** con *publisher confirms* y marca `published_at`; la entrega es *at-least-once*, por lo que los consumidores deduplican por la clave idempotente del mensaje (general-plan D-08).

## 4. Tabla de decisión de errores

Todo contrato nuevo o modificado decide sus errores con esta sección. El objetivo es que dos personas, ante la misma situación, lleguen al mismo `code`, HTTP y `retryable`.

### 4.1 Principio

| Clase | Significa | Quién puede corregirlo | El cliente debe |
|---|---|---|---|
| **4xx** | La petición, tal como llegó, no puede tener éxito. | El llamador (cambiando la petición, su identidad o esperando a que un dato exista) | No reintentar igual, salvo `retryable: true` |
| **5xx** | La petición era válida; falló el sistema o una dependencia. | El sistema, una dependencia o un operador, nunca el llamador | Reintentar con *backoff* según `retryable` |

Pregunta de desempate: **¿existe alguna forma de que el llamador arregle esto cambiando lo que envía?** Si sí, es 4xx. Si el resultado sería el mismo para cualquier petición válida, es 5xx.

### 4.2 Tabla de decisión

Se evalúa **en orden**; se aplica la primera fila cuya situación se cumpla. `E8` puede ocurrir en cualquier paso. No hay filas de autenticación ni autorización (seguridad fuera de alcance; general-plan §7.1): el sistema no emite `401`/`403` en sus contratos REST; la única excepción es el `401 UNAUTHENTICATED` del webhook de la Pasarela (verificación de firma, §4.4).

| Fila | Situación | HTTP | `code` | `retryable` | Quién lo corrige | Ejemplos |
|---|---|---|---|---|---|---|
| **E1** | Método, `Content-Type` o `Accept` no soportados | 405 / 415 / 406 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` / `NOT_ACCEPTABLE` | No | Llamador | `PUT` sin `application/json`; exportación pidiendo `Accept: application/xml` |
| **E2** | La petición está mal formada o fuera del dominio permitido, **sin consultar ningún dato**: JSON no parseable, campo obligatorio ausente o `null`, tipo, formato, enumeración o rango inválido, regla entre campos violada, fecha pasada, lote sobre el máximo | 400 | `VALIDATION_ERROR`; o el específico: `BATCH_SIZE_EXCEEDED`, `INVALID_DATE_RANGE`, `INVALID_PERIOD` | No | Llamador | `passengers` negativo; `end_date` anterior a `start_date`; 1 000 `boat_ids` |
| **E3** | El recurso que identifica la **URL** no existe todavía, o la página pedida excede el total | 404 | `RESERVATION_INFO_NOT_FOUND`, `CHARGE_INTENT_NOT_FOUND`, `PARAMETERS_NOT_CONFIGURED`, `PAGE_OUT_OF_RANGE` | **Sí** solo si un proceso asíncrono puede crearlo después; **No** en `PAGE_OUT_OF_RANGE` | Llamador (corregir el ID o esperar) | UC04 antes de que UC03 termine; UC06 antes de que UC05 cree la intención; UC11 obtener sin configuración |
| **E4** | Falta una **configuración interna global** que cualquier petición válida necesitaría, idéntica para todos los llamadores | 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Sí | Operador (Administrador Financiero) | UC01/UC04 sin tarifa de seguro o sin el porcentaje que exige la fecha |
| **E5** | Falla una **dependencia externa**: está caída, inalcanzable o con *circuit breaker* abierto, no responde dentro del tiempo de espera, o respondió algo inválido | 503 | `FLEET_UNAVAILABLE` | Sí | Dependencia u operador | Flota con *timeout* de 1 s; Flota devuelve un cuerpo que no es JSON |
| **E6** | La petición es válida, pero los **datos del recurso referenciado en el cuerpo, o ya registrado**, impiden la operación | 422 | `BASE_RATE_NOT_AVAILABLE`, `RESERVATION_INFO_INCOMPLETE` | No | Dueño del dato (Propietario, Reservas) | `boat_id` sin tarifa base o desconocido para Flota; información de reserva sin tarifa base |
| **E7** | *Reservada*: conflicto con el estado actual de una entidad (transición inválida, duplicado no idempotente) | 409 | *Sin uso hoy.* Se define el `code` en el primer contrato que lo requiera | No | Llamador | Ninguno vigente |
| **E8** | Falla propia no clasificable: excepción no controlada, base de datos o persistencia, o la dependencia respondió `4xx` a una petición que **construimos nosotros** | 500 | `INTERNAL_ERROR`, o el específico del contrato: `PARAMETERS_SAVE_FAILED` | Sí | Equipo del sistema | UC11 guardar con la BD caída; Flota responde `400` a nuestro cuerpo mal armado |

### 4.3 Reglas de uso y desempate

1. **Orden de evaluación**: E1 → E2 → E3 → E4 → E5 → E6. Una petición inválida siempre da `4xx`, aunque la dependencia también esté caída: no se gasta una llamada externa en una petición que ya es inválida. Un contrato puede declarar un orden más específico dentro de E3 y E6, nunca alterar el orden entre filas.
2. **`404` solo para el recurso de la URL.** Un identificador inexistente dentro del **cuerpo** (por ejemplo `boat_id`) es E6 (`422`), no `404`.
3. **La fecha pasada es E2 (`400`)** aunque dependa del reloj: la petición es inválida por sí misma.
4. **Datos de la dependencia vs. fallo de la dependencia.** Si Flota responde bien y dice "no tiene tarifa" o "no la conozco", es E6. Si Flota no responde, responde fuera de tiempo o responde algo ilegible, es E5. Si Flota rechaza nuestra petición por estar mal construida, es E8: el llamador original no pudo evitarlo.
5. **Configuración global vs. dato del recurso.** Si lo que falta es igual para toda petición válida (parámetros financieros), es E4 (`5xx`). Si depende del identificador que envió el llamador (tarifa de **esa** embarcación), es E6 (`4xx`).
6. **`retryable` en 4xx** es `false` por defecto. Solo se marca `true` cuando el contrato documenta que otro proceso puede crear el recurso después (E3 asíncrono). **`retryable` en 5xx** es `true`; no se usa `503` con `retryable: false`, porque `503` ya significa "intenta más tarde".
7. **Nunca valores parciales**: ante E4, E5 o E6 se devuelve el error completo, jamás una estimación o desglose parcial (principio rector 4 del plan general).
8. **Alcance declarado**: UC12 y UC13 reciben el alcance como **dato plano** (`scope` de Propietario o Administrador Financiero, y `owner_id` cuando sea de Propietario) y aplican exactamente lo declarado, sin validar identidad ni pertenencia (seguridad fuera de alcance; general-plan §7.1). No hay `403` ni resolución de identidad autenticada.
9. **Detalle del error**: en `4xx` es específico y accionable (`field` y `message`). En `5xx` es genérico: sin trazas, SQL, nombres de clase ni URLs internas. El detalle técnico va al log con el `X-Correlation-Id`.
10. **Prohibido**: responder `200` con un cuerpo de error; `500` por una validación; `4xx` por una falla de dependencia; códigos nuevos que no estén en el catálogo de la §3.4. El `429` no se usa: ningún SPEC define límite de tasa.

### 4.4 Colas y webhook

Los canales sin respuesta al productor no pueden usar HTTP. Se aplican las mismas ideas: lo que el reintento no puede arreglar se confirma y se registra; lo transitorio se reintenta.

| Canal | Situación | Acción | Origen |
|---|---|---|---|
| Cola (todas: UC03, UC05, UC07, UC08, UC09, UC10) | Mensaje inválido, estado desconocido, reserva sin información previa, validación fallida (equivale a E2/E6) | `ack` y registro en `operational_failure`. Sin reintento ni DLQ: reintentar no cambia el resultado | [SPEC UC03 RNF-003, UC07 RF-010, UC08 RNF-003] |
| Cola (todas) | Falla transitoria: BD, Flota o pasarela (equivale a E5/E8) | Reintentos con *backoff* exponencial base 1 s hasta **5 intentos** (1 inicial + 4 reintentos); al agotarlos se rechaza a la DLQ y se registra en `operational_failure` | [CONV]; plan general §7.2 |
| Cola (UC07, UC08) | Mensaje repetido (misma clave idempotente) | `ack` sin operación monetaria adicional | [SPEC UC07 casos extremos, UC08 RF-008] |
| Webhook | Firma ausente o inválida | `401 UNAUTHENTICATED` | [CONV] — requisito de la integración con Mercado Pago, no control de acceso del sistema (general-plan §7.1) |
| Webhook | Payload mal formado (E2) | `400 VALIDATION_ERROR` | [CONV] |
| Webhook | Resultado ya registrado (duplicado) | `200 OK`, sin modificar el registro | [SPEC UC05 casos extremos, UC10 casos extremos] |
| Webhook | Falla interna: BD no disponible (E8) | `500 INTERNAL_ERROR`, para que la Pasarela reintente | [CONV] |
| Webhook | Resultado sin `IntenciónDe…` asociada (ningún `external_reference` coincide con el `data.id`) | `200 OK` y registro en `operational_failure` (evita reintentos infinitos de la Pasarela; el SPEC UC05 exige registrar el evento sin crear un registro de cobro) | [SPEC UC05 casos extremos] + [CONV] |

**Política compartida del núcleo** [CONV núcleo compartido]: las siete colas de trabajo son *quorum* con DLQ `<cola>.dlq`. `spring.rabbitmq.listener.simple.retry.max-retries=4` fija el contrato de **5 intentos**: Boot 4 / Spring Framework 7 cuentan los reintentos **después** del intento inicial (1 + 4 = 5), con *backoff* exponencial base 1 s (×2, máx. 16 s). Un único `MessageRecoverer` (`OperationalFailureMessageRecoverer`) traduce la cola al UC dueño, registra la causa raíz con transacción independiente (`REQUIRES_NEW`) y rechaza el mensaje a la DLQ. Para los errores lógicos, `AmqpListenerFailurePolicy.recordAndAcknowledge` registra y confirma. Ningún UC redefine esta política ni declara recoverers propios.

### 4.5 Lista de verificación para cada contrato

1. Cada fila de la tabla de errores del contrato usa un `code` y un HTTP **idénticos** a los del catálogo (§3.4).
2. El contrato declara el orden de validación aplicable (E1 → E6) cuando tiene más de una causa posible de error.
3. Ningún `5xx` expone detalles internos y ningún `4xx` esconde una falla propia.
4. Todo `retryable: true` en un `4xx` tiene su justificación asíncrona escrita en el contrato.
5. Si el contrato necesita un error que no existe, se agrega primero a la §4.2 y al catálogo, no solo al contrato.
