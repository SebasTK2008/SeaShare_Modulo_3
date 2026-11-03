### Test Case Specification: Procesar cobro · HU1 · E1 Envío exitoso de la solicitud de cobro a la Pasarela de pago
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../005-procesar-cobro/spec.md)  **Status** : Approved  **Execution** : 2026-11-02 Â· release/0.1.0 Â· PASS (`docs/features/qa/matriz-qa.md`)

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU1-E1
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que, ante una reserva con valor total ya calculado y registrado y un medio de pago tokenizado, el sistema recupera el valor total, lo envía a la Pasarela (simulada) con la referencia segura y registra internamente la solicitud de cobro en curso a la espera del resultado.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../005-procesar-cobro/spec.md) |
| Contrato evento UC07 (disparador) | Events contract | [UC07-estado-reserva.md](../../../technical-plan/contracts/events/UC07-estado-reserva.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Catálogo de errores y política de colas | Conventions | [contracts/README.md](../../../technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula** (sin acceso a Mercado Pago real en QA); se verifica por el request journal del simulador y por los logs de la app.

##### Dependencies & Assumptions
* **TC-UC04-HU1-E1 debe haber pasado**: la reserva de prueba tiene el valor total calculado y registrado.
* RabbitMQ arriba y *outbox* activo (`finance.charge-command.v1`).

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con valor total registrado, `payment_token_ref = mp_tok_QA_01`, `payer_email = arrendatario@qa.com`, `payment_method_type = CREDIT_CARD`. Montos de ejemplo (ajustar a la versión vigente de parámetros del entorno): alquiler `360.00`, seguro `200.00`, depósito `12.00`, total `572.00`.
* **Environment** : staging local (`docker compose --profile app up --build -d`), health en `http://localhost:8080/actuator/health`, consola RabbitMQ en `http://localhost:15672`.
* **Test Accounts** : N/A (seguridad fuera de alcance).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Envío exitoso de la solicitud de cobro
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar el valor total registrado | Consultar en BD el valor total de la reserva (salida de TC-UC04-HU1-E1) | `reservation_id = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | Existe un valor total registrado con desglose de alquiler, seguro y depósito (RF-002). | [x] | |
| 2 | Iniciar el pago de la reserva | Publicar en RabbitMQ el estado `PENDING` de UC07 con la metadata de pago no sensible | `reservation_id=b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`, token, `payer_email`, `payment_method_type=CREDIT_CARD` | El mensaje se confirma (ack); el sistema crea una `IntenciónDeCobro` en `PENDING_SEND` sin llamada síncrona a la Pasarela (RF-001). | [x] | Disparador real: UC07 HU1 E3. |
| 3 | Verificar el mensaje de la cola interna | Inspeccionar `finance.charge-command.v1` (consola RabbitMQ o BD de outbox) | N/A | El mensaje lleva `charge_intent_id` y `payer_email`; **no** lleva el token (el worker lo lee de la intención) (RF-011). | [x] | |
| 4 | Verificar la llamada a la Pasarela | Revisar el request journal del simulador (`POST /v1/payments`) | N/A | `transaction_amount = 572.00` (monto ÚNICO: alquiler + seguro + depósito), `token` = referencia segura, `description = "Reserva b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10"`, `installments = 1`, `payment_method_id = credit_card`, `payer.email`, `external_reference = b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` (RF-002, RF-015). | [x] | |
| 5 | Verificar la clave idempotente | Revisar el header del request | N/A | `X-Idempotency-Key` = `charge_intent.idempotency_key` (derivada de `reservation_id` + `status` + `status_changed_at`) (RNF-003 del UC05). | [x] | |
| 6 | Verificar la respuesta técnica | Revisar la `IntenciónDeCobro` en BD | N/A | Estado inicial reportado por la Pasarela (`approved` / `in_process` / `rejected`) y su `id` externo guardados en la intención (RF-012). | [x] | |
| 7 | Verificar la intención en curso | `SELECT` sobre `charge_intent` | `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | Fila en estado de operación en curso, con clave idempotente, `payer_email` y referencia externa; aún **no** hay `RegistroDeCobro` (RF-004). | [x] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Reserva sin valor total calculado | Publicar `PENDING` para una reserva que no pasó por UC04 | reserva sin valor | Error controlado, **sin** solicitud a la Pasarela y sin monto asumido (RF-009). | [x] | |
| 2 | Faltan datos obligatorios de pago | Publicar sin `payment_method_type` o sin `payer_email` | payload incompleto | No se envía el cobro: intención `REJECTED` con detalle técnico y sin registro de cobro (contrato de cobro, regla 5). | [x] | |
| 3 | La Pasarela rechaza el token con `400` | Simular `400` de validación | token inválido | Intención `REJECTED`, sin reintento, mensaje confirmado y sin marcar la reserva como cobrada (RF-013). | [x] | |
| 4 | Caída, `502`/`503` o timeout (10 s) | Simular `503` / timeout | N/A | Intención `COMMUNICATION_ERROR`; no se asume resultado; reintentos 5 con backoff base 1 s y la misma `X-Idempotency-Key` (RNF-003 del UC05). | [x] | Detalle a fondo en la fase de resiliencia. |
| 5 | Mensaje de estado repetido | Publicar dos veces el mismo `PENDING` | misma clave | La segunda entrega se confirma sin segunda solicitud monetaria (idempotencia). | [x] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : El monto enviado es uno solo e incluye depósito y seguro; no hay llamadas separadas por concepto (RF-015).
* **EC-002** : `BigDecimal` de 4 decimales internos → redondeo final `HALF_UP` a 2 decimales hacia la Pasarela (RNF-002 del UC05).
* **EC-003** : `payment_method_type` distinto de `CREDIT_CARD` se envía tal cual; solo `CREDIT_CARD` se traduce a `credit_card` (contrato de cobro).

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
