# UC06 — Solicitar confirmación de pago

| Campo | Valor |
|---|---|
| Caso de uso | UC06 Solicitar confirmación de pago (HU1) |
| SPEC | `docs/features/006-solicitar-confirmacion-de-pago/spec.md` |
| Dirección | Sistema de Reservas y Operaciones → sistema |
| ¿Responde? | Sí (síncrono) |
| Quién puede llamarlo | Solo el Sistema de Reservas y Operaciones [SPEC RF-001] |
| Efectos secundarios | Ninguno: solo lectura de la `IntenciónDeCobro`; **no contacta a la pasarela** [SPEC RF-002, RF-006] |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

Reservas consulta el estado vigente del cobro de una reserva para decidir si avanza a `RESERVADO` o revierte el bloqueo temporal. Solo un estado aprobado y verificable permite avanzar la reserva (`docs/context/contexto-modulo3.md`, flujo paso 6).

## 2. Petición

`GET /api/v1/reservations/{reservation_id}/payment-confirmation` **[CONV]** (OQ-02). Sin cuerpo.

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <token>` [PEND OQ-01] |
| `Accept` | No | `application/json` |
| `X-Correlation-Id` | No | Cadena libre |

### Parámetros de ruta

| Parámetro | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `reservation_id` | UUID | Sí | Identificador de la reserva | [SPEC RF-001, `SolicitudConfirmacionPago`]; tipo [PEND] OQ-05 |

## 3. Reglas de procesamiento

1. El sistema busca la `IntenciónDeCobro` creada por "Procesar cobro" para esa reserva [SPEC RF-002].
2. Devuelve fielmente el estado registrado, sin anticipar ni inventar un resultado que la pasarela aún no reportó [SPEC RF-003, casos extremos].
3. Incluye los montos autorizado, capturado, liberado o cobrado y la referencia externa **cuando estén disponibles** [SPEC RF-004]; los no disponibles van como `null`.
4. `APROBADO` **no implica** que la captura o la liquidación posterior ya se haya ejecutado [SPEC escenario 1].
5. Un cobro `RECHAZADO`, `CANCELADO` o `EXPIRADO` nunca se reporta como aprobado [SPEC escenario 2].
6. Consultas repetidas devuelven el estado vigente en ese momento, sin crear ni modificar registros [SPEC casos extremos].
7. Sin `IntenciónDeCobro` para la reserva → error controlado, sin asumir ningún estado [SPEC RF-005].

## 4. Respuesta exitosa

`200 OK` — `Content-Type: application/json`

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `reservation_id` | UUID | Identificador de la reserva | [SPEC `ConfirmacionPagoResultado`] |
| `status` | string | Estado de la operación (tabla siguiente) | [SPEC RF-003] |
| `detail` | string o `null` | Detalle del estado cuando esté disponible | [SPEC RF-003] |
| `authorized_amount` | string decimal o `null` | Monto autorizado | [SPEC RF-004] |
| `captured_amount` | string decimal o `null` | Monto capturado | [SPEC RF-004] |
| `released_amount` | string decimal o `null` | Monto liberado | [SPEC RF-004] |
| `charged_amount` | string decimal o `null` | Monto cobrado | [SPEC RF-004, RNF-002] |
| `external_reference` | string o `null` | Referencia externa asignada por la pasarela | [SPEC escenario 1, RF-004] |

| `status` | Significado |
|---|---|
| `EN_PROCESO` | La pasarela aún no reportó un resultado definitivo [SPEC escenario 3] |
| `APROBADO` | La pasarela aprobó la autorización o el cobro |
| `RECHAZADO` | La pasarela rechazó la operación |
| `CANCELADO` | La operación fue cancelada |
| `EXPIRADO` | La autorización venció sin captura (UC05 RF-014) |
| `DESCONOCIDO` | Estado externo no determinado (incluye la falla de comunicación, [PEND] OQ-12) |

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "status": "APROBADO",
  "detail": null,
  "authorized_amount": "990000.00",
  "captured_amount": null,
  "released_amount": null,
  "charged_amount": null,
  "external_reference": "pg-8841"
}
```

## 5. Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Origen |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reservation_id` no tiene formato válido | No | [CONV] |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 |
| 403 | `FORBIDDEN` | El llamador no es el Sistema de Reservas y Operaciones | No | [SPEC RF-001] |
| 404 | `CHARGE_INTENT_NOT_FOUND` | No existe `IntenciónDeCobro` para esa reserva ("Procesar cobro" nunca se inició) | Sí | [SPEC RF-005, RNF-003] + [CONV] |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] |

```json
{
  "type": "about:blank",
  "title": "Cobro no iniciado",
  "status": 404,
  "detail": "No existe una IntenciónDeCobro para la reserva b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10.",
  "code": "CHARGE_INTENT_NOT_FOUND",
  "retryable": true
}
```

## 6. Idempotencia y reintentos

Solo lectura. `CHARGE_INTENT_NOT_FOUND` es reintentable porque la intención la crea UC05 de forma asíncrona al recibir el estado `PENDIENTE` (UC07).

## 7. Trazabilidad

HU1 (escenarios 1, 2 y 3) · RF-001 a RF-006 · RNF-001, RNF-002, RNF-003 · CE-001, CE-002, CE-003.