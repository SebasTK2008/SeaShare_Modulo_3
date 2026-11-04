# Frontend: tokenización de tarjeta (frontera con el repositorio de cliente)

| Campo | Valor |
|---|---|
| Casos de uso | UC05 Procesar cobro (entrada) y UC07 Brindar el estado de la reserva (transporte) |
| SPEC | `docs/features/005-procesar-cobro/spec.md` |
| Dirección | Frontend (repositorio externo) → sistema |
| ¿Responde? | Sí (entrega del `card_token`) |
| Responsable de implementarlo | **Repositorio de frontend**. Este módulo solo consume el resultado |

Leyenda y convenciones comunes: [`../README.md`](../README.md).

## 1. Propósito

El sistema **nunca ve datos de tarjeta**. El frontend tokeniza con MercadoPago.js (CardForm o
CardPayment Brick) usando la `public_key` de la aplicación y entrega al sistema un `card_token` de
un solo uso. Este contrato define la frontera entre ambos repositorios: qué debe entregar el
frontend, dónde viaja y qué prohíbe el sistema [SPEC UC05 RF-011, RF-012, RNF-004].

## 2. Qué entrega el frontend

| Campo | Tipo | Oblig. | Descripción | Destino |
|---|---|---|---|---|
| `payment_token_ref` | string | Sí | `card_token` de un solo uso devuelto por MercadoPago.js | `charge_intent.payment_token_ref` → campo `token` de `POST /v1/payments` |
| `payment_method_type` | string | No | Marca (`VISA`, `MASTER`, …) o tipo genérico (`CREDIT_CARD`, `DEBIT_CARD`, `PREPAID_CARD`) | `charge_intent.payment_method_type`; decide si se envía `payment_method_id` (D-UC05-16) |
| `payment_metadata.last_four` | string (1–4 dígitos) | No | Últimos cuatro dígitos de la tarjeta | `charge_intent.last_four` |
| `payment_metadata.payer_email` | string | **Sí** | Correo del pagador | campo `payer.email` de `POST /v1/payments` |

Reglas:

1. **El token es obligatorio**: sin `payment_token_ref` el sistema rechaza el cobro con
   `InvalidPaymentTokenException` y no llama a Mercado Pago [SPEC RF-013].
2. **El correo es obligatorio**: sin `payer_email` la intención queda `REJECTED` con detalle técnico y
   **sin** llamar a la pasarela (`pasarela-comando-cobro.md` §3, regla 5).
3. **La marca la informa Mercado Pago**: si el frontend envía `payment_method_type`, debe tomarlo de
   la respuesta de tokenización de Mercado Pago, no inferirlo del número de tarjeta [D-UC05-16].
4. **`last_four` es opcional y no sensible**; el esquema admite como máximo 4 dígitos.

## 3. Qué NO debe entregar nunca

- Número completo de tarjeta (PAN).
- Código de seguridad (CVV).
- Fecha de vencimiento.
- Nombre del titular.

El esquema de base de datos no tiene columnas para esos datos y el backend no los recibe
[SPEC UC05 RF-011, RNF-004; `V1__initial_schema.sql`].

## 4. Cómo viaja

```text
Frontend (repo externo)
  └─ tokeniza con public_key
       └─ envía el estado PENDING de UC07 con payment_token_ref / payment_method_type / payment_metadata
            └─ UC07 → ReservationStatusCommand → ProcessChargeUseCase
                 └─ charge_intent (token persistido)
                      └─ cola finance.charge-command.v1 (solo charge_intent_id + payer_email)
                           └─ worker → POST /v1/payments
```

El token **no** viaja por la cola interna de comandos: el worker lo lee de `charge_intent`
(D-UC05-10, CE-005).

## 5. Requisitos de la tokenización

1. Usar la **`public_key`** de la aplicación de Mercado Pago (nunca el `access_token`, que es secreto
   y vive solo en el backend).
2. El `card_token` es de **un solo uso**: no reutilizarlo entre intentos ni entre reservas.
3. No registrar el token en logs del frontend ni en analítica.
4. Si la tokenización falla, **no** se debe notificar `PENDING` con un token vacío o inventado.

## 6. Casos de error en la frontera

| Situación | Respuesta esperada del sistema |
|---|---|
| Token ausente o vacío | `InvalidPaymentTokenException`; no se llama a Mercado Pago |
| Token inválido o expirado | `400` de Mercado Pago → intención `REJECTED` con `status_detail` técnico, sin reintento |
| `payer_email` ausente | Intención `REJECTED` sin llamar a la pasarela |
| `last_four` con más de 4 caracteres o no numérico | `PaymentMethodReference` lo rechaza antes de persistir |

## 7. Verificación

- **Modo A**: `TC-UC05-HU1-E2-medio-tokenizado.md` usa un token simulado; valida que el sistema solo
  acepta el token como referencia y no persiste datos sensibles.
- **Modo B**: B-01 (token de tarjeta aprobada) y B-02 (token de tarjeta rechazada) en
  `docs/qa/pagos-api-real.md`. Requiere tokenizar con el frontend real contra el sandbox.

## 8. Trazabilidad

HU1 · RF-001, RF-011, RF-012, RNF-004 · D-UC05-10, D-UC05-16 · contratos
`pasarela-comando-cobro.md` y `events/UC07-estado-reserva.md`.
