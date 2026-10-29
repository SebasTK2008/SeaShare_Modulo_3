# Pasarela: Comando de cobro (Mercado Pago)

| Campo | Valor |
|---|---|
| Caso de uso | UC05 Procesar cobro |
| SPEC | `docs/features/005-procesar-cobro/spec.md` |
| Dirección | Sistema (Worker) → Mercado Pago |
| ¿Responde? | Sí (respuesta síncrona técnica o acuse de recibo) |
| Responsable de implementarlo | Adaptador de Pasarela (ACL) |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema envía una solicitud de cobro a la API de Pagos de Mercado Pago (`POST /v1/payments`) por el valor total de la reserva, utilizando el token de tarjeta generado en el frontend. Esta llamada se realiza asíncronamente desde un worker (patrón outbox) para no bloquear la transacción que registró la intención de cobro [SPEC HU1].

El worker toma el comando de la cola interna `finance.charge-command.v1`, que solo lleva el identificador de la intención y el correo del pagador; el token del medio de pago se lee de `charge_intent` [CONV] (UC05 plan §Reglas 7, D-UC05-10).

## 2. Petición (Llamada a Mercado Pago)

Esta es una representación del modelo que el adaptador (`GatewayPort`) transformará y enviará a Mercado Pago.

`POST /v1/payments`

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Authorization` | Sí | `Bearer <ACCESS_TOKEN>` (requisito de la integración con Mercado Pago; no es seguridad del proyecto — general-plan §7.1) |
| `X-Idempotency-Key` | Sí | Clave idempotente del intento: el valor de `charge_intent.idempotency_key`, derivado de `reservation_id` + `status` + `status_changed_at` (general-plan D-33, D-UC05-04). Es la **misma** clave en los reintentos técnicos del worker, de modo que un reintento no genera un cobro doble |

### Body (Payload)

| Campo | Tipo | Oblig. | Descripción | Origen |
|---|---|---|---|---|
| `transaction_amount` | decimal (número JSON) | Sí | Monto total a cobrar (incluye alquiler, seguro y depósito), redondeado `HALF_UP` a 2 decimales | [SPEC RF-002, RNF-002] + [CONV] (forma numérica: ver README §3.1) |
| `token` | string | Sí | Token seguro de la tarjeta generado por el SDK de Mercado Pago | [SPEC RF-001, RF-011] |
| `description` | string | Sí | Descripción de la reserva para el estado de cuenta; la fija el sistema como `Reserva <reservation_id>` | [CONV] |
| `installments` | integer | Sí | Número de cuotas; el sistema fija `1` | [CONV] |
| `payment_method_id` | string | Sí | ID del medio de pago; se envía el `payment_method_type` recibido en el mensaje de UC07 con una única traducción: `CREDIT_CARD` → `credit_card` (valor que exige Mercado Pago); cualquier otro valor pasa tal cual (ej. `visa`, `master`) | [SPEC] + [CONV] |
| `payer.email` | string | Sí | Email del arrendatario; se envía `payment_metadata.payer_email` del mensaje de UC07 (dato plano) | [SPEC] + [CONV] |
| `external_reference` | string | Sí | Identificador de la reserva para trazabilidad cruzada; el sistema envía el `reservation_id` | [SPEC SolicitudCobroPasarela] + [CONV] |

```json
{
  "transaction_amount": 990000.00,
  "token": "ff8080814c11e237014c1ff593b57b4d",
  "description": "Reserva b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "installments": 1,
  "payment_method_id": "credit_card",
  "payer": {
    "email": "arrendatario@example.com"
  },
  "external_reference": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10"
}
```

## 3. Reglas de procesamiento

1. **Minimización de datos**: NUNCA se envía ni se almacena el número completo de tarjeta o CVV. Solo se opera con el `token` de un solo uso [SPEC RF-011, RNF-004].
2. **Monto único**: El `transaction_amount` es uno solo, englobando alquiler, seguro y depósito. No se hacen llamadas separadas por concepto [SPEC RF-015].
3. **Idempotencia**: Se garantiza pasando el `X-Idempotency-Key` en los headers de la llamada. Un timeout no significa fallo [SPEC RNF-003].
4. **Cobro directo**: se solicita el cobro con captura automática; no se usa autorización retenida ni `capture=false`. La aprobación de Mercado Pago ya es el cobro (general-plan D-32).
5. **Datos obligatorios**: si el tipo de medio o el correo del pagador no están disponibles, **no se envía** la solicitud (el SPEC exige no enviar un cobro con datos incompletos): la intención de cobro queda en `REJECTED` con el detalle técnico y sin registro de cobro [CONV] (UC05 plan D-UC05-11).
6. **Protección de la pasarela**: la llamada usa un *timeout* de **10 s** de conexión y de lectura (general-plan §7.2) y el adaptador aplica un *circuit breaker* (50 % de fallos sobre una ventana de 10 llamadas, mínimo 5 llamadas, 10 s abierto, 1 llamada en *half-open*) para no gastar los reintentos de la cola contra una pasarela caída. Un `400` de validación del token **no** cuenta como fallo del circuito: no es una caída de la pasarela [CONV] (general-plan §7.2, D-20).

## 4. Respuesta exitosa (Técnica)

Mercado Pago responde confirmando la recepción y estado inicial del pago. El resultado definitivo asíncrono llegará por el webhook (`pasarela-webhook-resultados.md`).

| Campo | Tipo | Descripción | Origen |
|---|---|---|---|
| `status` | string | Estado en MP (`approved`, `in_process`, `rejected`) | [SPEC RF-006] |
| `id` | integer | Identificador único del pago en Mercado Pago; se guarda en la columna `charge_intent.external_reference` y con él se asocian el webhook y la conciliación (el `external_reference` **enviado** en el cuerpo es el `reservation_id`: son dos datos distintos) | [SPEC RF-012] + [CONV] |
| `status_detail` | string | Detalle del estado (ej. `accredited`, `cc_rejected_bad_filled_other`); puede venir ausente | [SPEC] |

La respuesta técnica y el webhook convergen en el mismo servicio de registro del resultado: un segundo resultado igual es idempotente [CONV] (UC05 plan D-UC05-08).

## 5. Manejo de Errores

Si la llamada HTTP falla, el worker la clasifica así y deja la `IntenciónDeCobro` en el estado correspondiente:

| Respuesta de la pasarela | Clasificación | Efecto en la intención | Reintento | Origen |
|---|---|---|---|---|
| `400` (validación del token) | **No reintentable** | `REJECTED` con el detalle técnico de la pasarela en `status_detail`; **sin** registro de cobro y sin marcar la reserva como cobrada | Ninguno; el mensaje de la cola se confirma | [SPEC RF-013]; README §4.4 |
| `502`, `503`, *timeout* o caída | **Reintentable** | `COMMUNICATION_ERROR`: el resultado del cobro es desconocido | El mensaje AMQP interno se reintenta (5 intentos, *backoff* exponencial base 1 s) **respetando la `X-Idempotency-Key`**; al agotarlos, DLQ y registro en `operational_failure` | [SPEC RNF-003]; README §4.4 |
| Otro `4xx` (credenciales, petición irrecuperable) | **Reintentable** | `COMMUNICATION_ERROR` | Igual que el caso anterior | [CONV] README §4.2 E8: la dependencia respondió `4xx` a una petición que construyó el sistema |
| Otro `5xx` | **Reintentable** | `COMMUNICATION_ERROR` | Igual que el caso anterior | [CONV] README §4.4 |

Un *timeout* **nunca** permite concluir que el cobro falló: la intención queda en `COMMUNICATION_ERROR` y la conciliación posterior consulta el estado real del pago [SPEC RNF-003].

## 6. Trazabilidad

HU1 · RF-001 a RF-004, RF-011, RF-015 · RNF-001 a RNF-004.
