### Test Case Specification: Procesar cobro · HU1 · E2 Uso de un medio de pago tokenizado sin almacenar datos sensibles
**Created** : 2026-11-02  **Spec/Plan Link** : [docs/features/005-procesar-cobro/spec.md](../../../docs/features/005-procesar-cobro/spec.md)  **Status** : Draft

#### Test Overview *(mandatory)*

**Test Case ID** : TC-UC05-HU1-E2
**Product Name & Version** : SeaShare — Módulo 3 (Finanzas) 0.1.0 (`release/0.1.0`)
**Author** : QA
**Current Owner** : QA
**Priority** : P1 - Critical

--------------------------------------------------------------------------------

#### Objectives & Context *(mandatory)*

##### Test Objectives
Este caso de prueba verifica que el sistema utiliza **únicamente** el token o referencia segura del medio de pago para iniciar la operación, y que no recibe ni persiste el número completo de la tarjeta, el código de seguridad ni otros datos sensibles.

##### Document References
| Reference Name | Type of Reference | Document Location / Link |
| ------ | ------ | ------ |
| Feature Spec UC05 | Specification Document | [spec.md](../../../docs/features/005-procesar-cobro/spec.md) |
| Contrato pasarela — comando de cobro | External contract | [pasarela-comando-cobro.md](../../../docs/technical-plan/contracts/external/pasarela-comando-cobro.md) |
| Catálogo de errores y convenciones | Conventions | [contracts/README.md](../../../docs/technical-plan/contracts/README.md) |

--------------------------------------------------------------------------------

#### Prerequisites & Environment

##### Test Case Limitations
* La Pasarela de pago se **simula**; el token se genera/representa en el simulador.

##### Dependencies & Assumptions
* **TC-UC05-HU1-E1 debe haber pasado** (existe una reserva con valor total y una solicitud de cobro en curso).
* Acceso a logs de la aplicación y a la base de datos para inspección.

##### Preconditions & Default Setup
* **Initial State** : reserva `R-100` = `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` con valor total registrado y token emitido por la Pasarela simulada: `mp_tok_QA_01`; `payer_email = arrendatario@qa.com`; `payment_method_type = CREDIT_CARD`.
* **Environment** : staging local (`docker compose --profile app`).

--------------------------------------------------------------------------------

#### Test Execution Flow *(mandatory)*

##### Section 1: Uso exclusivo del token y minimización de datos
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Confirmar que la Pasarela tokenizó el medio de pago | Verificar en el simulador que existe el token del medio de pago | `mp_tok_QA_01` | Existe una referencia segura de un solo uso; el sistema no recibe el PAN ni el CVV (RF-011). | [ ] | |
| 2 | Iniciar la solicitud de cobro con el token | Publicar el estado `PENDING` de UC07 con la metadata de pago | `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10`, token, `payer_email`, `CREDIT_CARD` | El sistema acepta únicamente el token como referencia del medio de pago (RF-001). | [ ] | |
| 3 | Inspeccionar lo enviado a la Pasarela | Request journal del simulador (`POST /v1/payments`) | N/A | El body solo contiene `token` como dato del medio de pago; no aparecen número completo de tarjeta, CVV ni fecha de vencimiento (RF-011). | [ ] | |
| 4 | Inspeccionar lo persistido en la intención | `SELECT` sobre `charge_intent` | `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | Se guardan el token/referencia segura y, si aplica, metadata no sensible (tipo de medio, últimos 4); no se persiste el número completo, el CVV ni la fecha de vencimiento (RF-011). | [ ] | |
| 5 | Inspeccionar los logs de la app | Buscar el token y patrones de tarjeta en los logs del worker | N/A | El token del medio de pago **no** se escribe en logs; tampoco aparecen datos de tarjeta (RNF-004). | [ ] | |
| 6 | Confirmar que la operación quedó registrada | `SELECT` sobre `charge_intent` | `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` | La solicitud de cobro en curso quedó registrada usando solo la referencia segura (RF-004). | [ ] | |

##### Section 2: Errores y casos extremos de este escenario
| Step # | Step Description | Path & Action | Test Data | Expected Results | Pass/Fail | Comments |
| :---: | ------ | ------ | ------ | ------ | :---: | ------ |
| 1 | Solicitud sin token | Publicar el estado `PENDING` sin `payment_token_ref` | payload sin token | No se envía el cobro; intención `REJECTED` con detalle técnico y sin registro de cobro (contrato de cobro, regla 5). | [ ] | |
| 2 | Intento de enviar datos sensibles en la solicitud | Construir un payload con número de tarjeta/CVV y enviarlo | payload con datos sensibles | El sistema no acepta ni propaga esos campos; no los persiste ni los reenvía a la Pasarela. | [ ] | |
| 3 | Error técnico del worker | Provocar un fallo controlado del worker con token presente | `mp_tok_QA_01` | El mensaje de error y la traza **no** exponen el token ni datos de tarjeta (RNF-004). | [ ] | |
| 4 | Repetición del mensaje con el mismo token | Publicar dos veces el mismo `PENDING` | misma clave/token | Sin doble cobro y sin duplicar el token en almacenamiento (idempotencia). | [ ] | |

##### Edge Cases & Boundary Conditions
* **EC-001** : Un token de un solo uso no puede reutilizarse para un segundo cobro exitoso; la Pasarela lo rechaza según su propio contrato.
* **EC-002** : Si la Pasarela entrega metadata no sensible (últimos 4 dígitos, tipo de medio), se almacena solo eso, nunca el dato completo.
* **EC-003** : El correo del pagador viaja en el mensaje interno de la cola (dato plano, no sensible) pero el token **no** viaja ahí: el worker lo lee de `charge_intent`.

--------------------------------------------------------------------------------

#### Defect Severity Reference

| Severity Level | Code | Definition & Impact |
| ------ | :---: | ------ |
| **Critical** | **C** | Crash, corrupción de datos o bloqueo completo del journey crítica sin workaround. |
| **Major** | **M** | Funcionalidad núcleo rota con workaround incómodo. |
| **Small / Minor** | **S** | Cosmético, typo, alineación menor, no bloqueante. |
