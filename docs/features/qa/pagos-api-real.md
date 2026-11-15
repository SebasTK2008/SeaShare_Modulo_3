# Pruebas de pagos: simuladas y contra la API real de Mercado Pago

Este documento define los **dos modos obligatorios** de prueba para las operaciones de pago del
Módulo 3. No son alternativas: el modo simulado protege la regresión automática y el modo real
demuestra que la integración funciona contra Mercado Pago.

| Modo | Propósito | Entorno | Cuándo se ejecuta | ¿Bloquea? |
|---|---|---|---|---|
| **A · Suite automática** | Regresión, contratos HTTP, firma, colas y resultados controlados | JUnit + WireMock embebido + Testcontainers | En cada `.\mvnw.cmd clean verify`; los TC indican las clases para verificaciones puntuales | Sí: el build debe quedar verde |
| **B · API real (sandbox)** | Verificar la integración real de cobro, webhook `payment` y reembolso | Mercado Pago **MCO sandbox** con credenciales `TEST-*` y usuarios de prueba | Antes de aprobar `release/0.1.0`; se ejecuta en las fases de integración real y E2E (F3/F4) | Sí: sin estas evidencias el release de pagos no se aprueba |

**Alcance del modo B:** UC05 (cobro) y UC09 (reembolso). **UC10 no aplica**: su liquidación es un
libro interno simulado y no llama a Mercado Pago (D-UC10-18). UC06 solo consulta datos ya
persistidos y no contacta a la pasarela.

## Decisión para el MVP y despliegue

Usa B como recorrido manual principal. Usa A para `expired` en UC05, el resultado negativo controlado de reembolso en UC09, fallos HTTP y de comunicación, y las comprobaciones de contrato indicadas en cada TC. Cada TC de UC06, UC07 y UC08 prepara dentro del archivo los datos necesarios para verificar su propio escenario. UC10 puede mostrar montos, comisiones y registros del libro interno en esa misma demo.

| Entorno | Configuración del repositorio | Qué se puede verificar |
|---|---|---|
| CI | Los workflows ejecutan Maven `verify`. El workflow de CI también construye la imagen. | Regresión A. El repo no configura ahí un despliegue ni ejecuta B. |
| Demo local | Compose activa `dev,qa` por defecto. El perfil `qa` carga el seed financiero. `FLEET_BASE_URL` apunta al stub de Flota y `GATEWAY_BASE_URL` a la API de Mercado Pago. | Con credenciales de prueba, frontend tokenizando con la Public Key correspondiente y callback público firmado se pueden mostrar cobro, confirmación, reembolso y liquidación interna juntos. |
| Staging de demo | Configura infraestructura propia y credenciales de prueba, un callback HTTPS público para ese entorno y los parámetros financieros necesarios. | El recorrido B puede ejecutarse desde un entorno desplegado. El stub de Flota y el seed local se deben sustituir por datos de ese entorno si se usa `prod`. |
| Producción | `SPRING_PROFILES_ACTIVE=prod` exige las variables de DB, RabbitMQ, Flota y pasarela del entorno, incluido token y secreto de webhook no vacíos. El perfil no determina si las credenciales son de prueba o producción. | Los mismos adaptadores de cobro y reembolso usan la configuración suministrada. Las credenciales de producción habilitan operaciones con dinero real. |

A y B clasifican la evidencia de prueba. No existe un selector de despliegue A/B. `GATEWAY_SETTLEMENT_MODE=simulated` solo controla UC10 y es el único valor implementado. El MVP captura directamente el cobro, contabiliza liquidaciones y comisiones en el libro interno y carece de transferencia real al propietario y de captura diferida. Usar B conserva las funciones implementadas.

Configuración comprobada en [Compose](../../../docker-compose.yml), [perfiles Spring](../../../src/main/resources/), [workflows](../../../.github/workflows/) y [diseño de integración](../../technical-plan/mercadopago-integration.md).

## Plan aplicado a los casos

| Defecto observado | Cambio aplicado |
|---|---|
| Un mismo TC pedía sandbox y respuestas simuladas | Recorrido principal identificado por modo y pruebas automáticas con comandos por clase. Los escenarios de expiración y fallo de reembolso se ejecutan en A. |
| En pagos faltaban entradas concretas y se reutilizaban UUID abreviados, montos incompatibles y consultas globales. | Cada TC incluye precondiciones, preparación propia, cuerpos de mensaje, importes y consultas por UUID en tablas, siguiendo el formato de UC02 y UC03. |
| Los estados intermedios dependían del tiempo del worker. Algunos pasos inferían headers desde un GET o confundían un webhook manual con una entrega real. | Criterios observables por reserva, evidencia separada de BD, panel y suite. El webhook real exige su propia evidencia incluso si el POST ya resolvió la intención. |
| Los TC mostraban marcas de PASS anteriores y la matriz repetía la política de modos | Los casos revisados quedan pendientes de ejecución y la matriz remite al procedimiento vigente. |

## 1. Modo A · Simulado (gate de CI)

- **UC05/UC09:** pruebas unitarias, de aplicación, de contrato con WireMock, de webhook con MockMvc,
  de persistencia con Testcontainers y de mensajería con RabbitMQ. Cada TC identifica las comprobaciones que usan esta suite.
- **UC10:** pruebas del libro interno, persistencia y conciliación; no hay HTTP ni journal de
  pasarela.
- **Reglas:** no requiere credenciales, no sale a Internet y no debe fallar por indisponibilidad de
  Mercado Pago. Un fallo aquí bloquea el merge.
- **Comando de referencia:** `.\mvnw.cmd clean verify` (Surefire; el proyecto no configura Failsafe).

## 2. Modo B · API real de Mercado Pago (gate de release)

### 2.1 Condiciones de ejecución

- Aplicación arrancada con `.env` local (gitignored) y credenciales **TEST** de
  la aplicación de Mercado Pago del equipo (`<APP_ID>`, site MCO; cada equipo usa la suya). Nunca usar
  credenciales de producción ni versionarlas.
- `GATEWAY_WEBHOOK_SECRET` completo, copiado del panel de Mercado Pago. Sin él el webhook responde
  `401 UNAUTHENTICATED` y el modo B no es válido.
- Callback público HTTPS registrado en Mercado Pago, topic `payment`. Si el túnel cambia, actualizar
  el callback antes de ejecutar. El procedimiento es genérico: cualquier túnel HTTPS sirve.
- Usuarios de prueba MCO: vendedor con fondos y comprador. Anotar sus IDs en la evidencia.
- Tokenización de la tarjeta de prueba con la `public_key` desde el repositorio de frontend
  ([contrato de frontera](../../technical-plan/contracts/external/frontend-tokenizacion.md)).
  El sistema solo recibe el token; nunca PAN, CVV ni fecha de vencimiento.

**Procedimiento paso a paso:** [`runbook-sandbox-mercadopago.md`](runbook-sandbox-mercadopago.md).

### 2.2 Escenarios mínimos obligatorios

Los IDs B-01…B-10 identifican evidencias de release. Los TC vinculados son independientes y contienen su propia preparación; se pueden recrear los volúmenes de QA entre casos.

| ID | Escenario | Acción real | Criterio de aceptación |
|---|---|---|---|
| **B-01** | Cobro aprobado | [UC05 HU1 E1](test-cases/TC-UC05-HU1-E1-envio-solicitud-cobro.md) con `APRO` y marca de tarjeta | `approved`, referencia de negocio igual a la reserva, `400000.00 COP`, UUID v4 en BD, intención `CAPTURED` y un registro |
| **B-02** | Cobro rechazado | [UC05 HU2 E2](test-cases/TC-UC05-HU2-E2-resultado-rechazo.md) con `OTHE` | Intención `REJECTED`, detalle observado y cero registros |
| **B-03** | Webhook `payment` real | [UC05 HU2 E1](test-cases/TC-UC05-HU2-E1-resultado-exitoso.md) | Entrega HTTP 200 del proveedor, outbox y consulta del worker al pago correlacionados por ID |
| **B-04** | Reembolso parcial | [UC09 HU1 E4](test-cases/TC-UC09-HU1-E4-reembolso-tardio-deposito.md) | Reembolso real de `35000.00 COP`, intención `COMPLETED`, neto correcto y un registro |
| **B-05** | Reembolso total | [UC09 HU1 E1](test-cases/TC-UC09-HU1-E1-reembolso-flexible.md) con reserva separada | Reembolso real de `400000.00 COP`, una intención y un registro |
| **B-06** | Webhook posterior al reembolso | [UC09 HU3 E1](test-cases/TC-UC09-HU3-E1-resultado-exitoso.md) | Entrega real, consulta del worker a la lista de reembolsos y coincidencia del ID local y externo |
| **B-07** | Idempotencia | Variantes de evento repetido y repetición HTTP de [UC05 HU1 E1](test-cases/TC-UC05-HU1-E1-envio-solicitud-cobro.md) y [UC09 HU1 E4](test-cases/TC-UC09-HU1-E4-reembolso-tardio-deposito.md). Cada caso prepara su propia operación. | Un cobro y un reembolso por operación, con los mismos IDs, intenciones y registros |
| **B-08** | Datos sensibles | [UC05 HU1 E2](test-cases/TC-UC05-HU1-E2-medio-tokenizado.md) y revisión de evidencias de B-01…B-07 | La intención usa una referencia tokenizada. El registro financiero, los logs y los mensajes internos excluyen el token y los datos completos de la tarjeta. |
| **B-09** | `payment_method_id` omitido | Variante `CREDIT_CARD` de [UC05 HU1 E1](test-cases/TC-UC05-HU1-E1-envio-solicitud-cobro.md), en reserva separada | Guarda `status`, `status_detail` y código de error del proveedor si está disponible. `approved` valida la omisión. Si falla con `4001`, registra el defecto y valida la exigencia de marca en el contrato antes de aprobar el release. |
| **B-10** | Reembolso total con `amount` explícito | Ejecutar [UC09 HU1 E1](test-cases/TC-UC09-HU1-E1-reembolso-flexible.md), incluida su preparación. El adaptador vigente envía `{"amount":400000.00}`. | Reembolso `approved`. Si el proveedor rechaza ese cuerpo, registra el defecto para evaluar el cuerpo vacío y vuelve a validar después del ajuste. |

### 2.3 Evidencia exigida

Registrar en el reporte de release:

- fecha y entorno; app ID y site (`MCO`);
- IDs de los usuarios de prueba (vendedor/comprador);
- `payment_id`, `refund_id`, `x-request-id` del webhook y estado final en Mercado Pago;
- filas resultantes en `charge_intent`, `charge_record`, `refund_intent`, `refund_record` y
  `outbox_message`;
- resultado de `quality_evaluation` y `notifications_history` cuando se usen para diagnosticar;
- captura o extracto del panel de Mercado Pago que confirme el cobro/reembolso y la entrega del
  webhook.

Guardar la evidencia sin credenciales ni datos de tarjeta.

### 2.4 Criterio de salida

Todos los escenarios B-01…B-10 deben pasar. Un fallo, una credencial ausente o un webhook no
entregado **bloquea la aprobación del release**; no se sustituye por el modo simulado. Si Mercado
Pago sandbox no está disponible, el resultado es «bloqueado», no «aprobado con simulador».

## 3. Estado actual y automatización

- **Aplicación en Mercado Pago:** la del equipo (`<APP_ID>`), producto Checkout API / API Payments,
  con credenciales `TEST-`. **Antes de cada ejecución** hay que verificar en el panel de la aplicación
  que el webhook del topic `payment` apunta al túnel HTTPS vigente (y reconfigurarlo si el túnel
  cambió); sin entregas válidas, B-03 y B-06 no pueden pasar. Si el equipo dispone del MCP de Mercado
  Pago, `notifications_history` muestra las entregas y sus códigos de respuesta (un `502` indica que el
  túnel o la aplicación estaban caídos).
- **Modo A:** implementado y verde en `.\mvnw.cmd clean verify` (WireMock para UC05/UC09, webhook
  MockMvc, persistencia Testcontainers y libro simulado de UC10).
- **Modo B:** especificado en este documento; **pendiente de ejecución** en las fases F3/F4. Las
  pruebas reales no forman parte de `mvn verify` para no depender de Internet ni de credenciales.
- Los TC revisados se ejecutan con el modo indicado en cada archivo. La aprobación del release exige además este checklist B.
