# Implementation Plan: UC10 - Liquidar fondos de alquiler

**Date**: 2026-10-09  
**Spec**: [spec.md](spec.md)

## Summary

UC10 es el caso de uso interno que solicita dispersiones al propietario por tres motivos: cancelación moderada (50 % del alquiler), cancelación tardía (100 % del alquiler), liquidación estándar de una reserva `COMPLETED` y liquidación total del depósito cuando una disputa termina en `COMPLETED`. No recibe montos desde Reservas: recupera los valores de `reservation_information` y conserva la comisión calculada en la intención de dispersión [SPEC RF-001..RF-009, RF-016].

El sistema registra una `SettlementIntent` antes de enviar el comando a Mercado Pago. La respuesta/webhook actualiza siempre la intención; solo un resultado externo exitoso crea un `SettlementRecord` inmutable y, únicamente para liquidación estándar, un `CommissionRecord` inmutable en la misma transacción. Operaciones rechazadas, canceladas, expiradas o pendientes no crean registros exitosos [SPEC RF-010..RF-014].

Enfoque técnico: servicio Spring Boot único con arquitectura hexagonal (`domain` / `application` / `infrastructure`), puertos internos consumidos por UC07/UC08, ACL de pasarela para el contrato de disbursement, outbox/worker para reintentos y webhook despachado por UC05. UC10 es dueño de `settlement_intent`, `settlement_record` y `commission_record`; UC12/UC13 solo los consultan. Las piezas de otros planes se referencian por nombre y no se replanifican.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001 | `RequestSettlementUseCase`, commands internos | T005-T008 | T011, T012 |
| RF-002, RF-003, RF-007 | `SettlementCalculator`, `SettlementScope` | T004, T009 | T013 |
| RF-004, RF-005 | `SettlementIntent`, `FrozenParameters` (comisión de la versión referenciada por la reserva) | T004, T009 | T014 |
| RF-006 | rama `DEPOSIT_DISPUTE` | T009 | T015 |
| RF-008, RF-009 | `SettlementGatewayPort`, `SettlementIntentRepository` | T005, T008, T010 | T016 |
| RF-009A, RF-015, RF-016 | `SettlementRecord`, mapper y persistencia | T010, T017 | T018 |
| RF-010, RF-012, RF-014 | `RegisterSettlementResultUseCase`, transacción de confirmación | T006, T010, T017 | T019, T020 |
| RF-011 | registros publicados en vista financiera | T017 | T018 |
| RF-013 | validación de información interna + `FailureRecorderPort` | T009 | T021 |
| RNF-001 | DTOs de comando y resultado de pasarela | T006, T007 | T016, T019 |
| RNF-002 | `BigDecimal`, `Money`, `SettlementCalculator` | T004, T009 | **T013 `ce001_…`** |
| RNF-003 | outbox, timeout, retry, conciliación y webhook | T003, T006, T010 | **T021 `ce004_…`** |
| CE-001 | calculadora por alcance | T004, T009 | **T013 `ce001_…`** |
| CE-002 | regla de comisión por alcance | T009, T017 | **T014 `ce002_…`** |
| CE-003 | intención → registro + vista | T008, T017 | **T018 `ce003_…`** |
| CE-004 | ACL/outbox/webhook | T003, T006, T010 | **T021 `ce004_…`** |
| CE-005 | estados externos no exitosos | T010, T017 | **T019 `ce005_…`** |
| CE-006 | creación atómica de comisión | T017 | **T020 `ce006_…`** |
| HU1 | compensaciones por cancelación | T009 | T013, T014 |
| HU2 | liquidación estándar | T009, T017 | T013, T014, T020 |
| HU3 | depósito por disputa | T009 | T015 |
| HU4 | resultado de pasarela | T006, T010 | T019, T021 |
| HU5 | comisión independiente | T017 | T020 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]  
**Primary Dependencies**: Spring Boot 4.1.1; Spring Web MVC, Validation, Data JPA (Hibernate), AMQP, Actuator, Flyway, MapStruct, Resilience4j, Micrometer/Prometheus, ArchUnit y ShedLock opcional. UC10 usa especialmente JPA, AMQP, cliente HTTP de pasarela, Testcontainers, WireMock y Awaitility. Las dependencias pertenecen al núcleo compartido; este plan no modifica `pom.xml`. (Sin *starter* de seguridad: fuera de alcance, general-plan §7.1.)
**Storage**: PostgreSQL 16+, `NUMERIC(18,4)` para importes y `timestamptz` para instantes. UC10 es dueño de `settlement_intent`, `settlement_record` y `commission_record`, creadas por la migración inicial única (general-plan §4.2, D-31); lee `reservation_information` y `charge_intent/charge_record` por puertos de sus dueños [general-plan §4].
**Messaging**: RabbitMQ 3.13+ con outbox, colas quorum, confirmaciones de publicador y DLQ; comandos de pasarela se envían desde worker y los resultados entran por el webhook compartido [general-plan §5; contratos externos].  
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers PostgreSQL/RabbitMQ, WireMock para Mercado Pago, Awaitility y ArchUnit [CONV].  
**Target Platform**: contenedores Docker sobre Linux; desarrollo con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: Sin meta propia: el SPEC 10 no fija SLA numérico; rigen el timeout de pasarela (10 s, general-plan §7.2), los reintentos del worker y el procesamiento idempotente; un timeout nunca se interpreta como éxito [CONV].  
**Constraints**: `BigDecimal` a 4 decimales; la comisión aplicada proviene de la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`) y se conserva en la intención; cancelaciones y depósito no crean comisión; registros financieros inmutables; solo confirmación exitosa crea `SettlementRecord`; sin datos asumidos ni registros exitosos parciales; sin datos sensibles de pago [SPEC RNF-002/003; general-plan §2].  
**Scale/Scope**: dos puertos de entrada internos, un adaptador de pasarela, webhook compartido, tres tablas propietarias, 16 RF + 3 RNF + 6 CE + 5 HU [SPEC 10].

## Project Structure

### Documentation

```text
docs/features/010-liquidar-fondos-alquiler/
├── plan.md
└── spec.md
docs/technical-plan/contracts/external/
├── pasarela-comando-liquidacion.md
└── pasarela-webhook-resultados.md
```

### Source Code

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java
├── domain/
│   ├── model/{SettlementIntent,SettlementRecord,CommissionRecord}.java
│   ├── valueobject/{SettlementScope,SettlementStatus,IdempotencyKey,Money}.java
│   ├── service/SettlementCalculator.java
│   └── exception/{DomainException,SettlementException}.java
├── application/
│   ├── port/in/
│   │   ├── RequestSettlementUseCase.java          # UC07/UC08 lo consumen
│   │   └── RegisterSettlementResultUseCase.java   # webhook/worker
│   ├── port/out/
│   │   ├── SettlementGatewayPort.java             # ACL Mercado Pago
│   │   ├── SettlementIntentRepository.java
│   │   ├── SettlementRecordRepository.java
│   │   ├── CommissionRecordRepository.java
│   │   ├── ReservationInformationRepository.java # dueño UC03, solo referencia
│   │   ├── ChargeRecordQueryPort.java             # catálogo compartido, dueño UC05
│   │   ├── FailureRecorderPort.java               # puerto compartido
│   │   ├── ReservationLockPort.java               # puerto compartido
│   │   └── OutboxPort.java                        # puerto compartido
│   ├── service/SettlementService.java
│   └── dto/
│       ├── SettlementCommand.java
│       ├── SettlementResultCommand.java
│       ├── GatewaySettlementRequest.java
│       └── GatewaySettlementResult.java
└── infrastructure/
    ├── adapter/in/scheduler/SettlementWorker.java  # reintentos/outbox
    ├── adapter/out/
    │   ├── gateway/{PaymentGatewayAdapter,GatewaySettlementMapper}.java
    │   ├── persistence/settlement/
    │   │   ├── {SettlementIntentJpaEntity,SettlementRecordJpaEntity,CommissionRecordJpaEntity}.java
    │   │   ├── SettlementPersistenceAdapter.java
    │   │   └── SettlementPersistenceMapper.java
    │   └── messaging/OutboxMessagingAdapter.java
    └── config/{GatewayWebhookConfig,ProblemDetailsConfig,ObservabilityConfig}.java

src/main/resources/db/migration/
└── V1__initial_schema.sql # migración inicial única; contiene settlement_intent, settlement_record y commission_record (general-plan §4.2, D-31)

src/test/java/com/seashare/seasharem3/
├── arch/ArchitectureTest.java
├── contract/{SettlementCommandContractTest,GatewayWebhookContractTest}.java
├── domain/service/SettlementCalculatorTest.java
├── application/service/SettlementServiceTest.java
├── infrastructure/adapter/out/gateway/PaymentGatewayAdapterTest.java
├── infrastructure/adapter/in/webhook/GatewayWebhookControllerTest.java
└── integration/SettlementProcessingIT.java
```

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4. UC05 es dueño del receptor/verificación/despachador del webhook; UC10 solo registra su manejador y define sus puertos `SendSettlementCommand`/`ReconcileSettlementResult`. `domain` no depende de Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; adaptadores de entrada invocan `application.port.in`; adaptadores de salida implementan `application.port.out`.

**Firmas referenciadas**: UC10 publica `RequestSettlementUseCase` para UC07 y UC08, y define `SendSettlementCommand`/`ReconcileSettlementResult` para el webhook/despachador de UC05. `SettlementCommand` incluye `trigger`, `scope` y `dispute_id` cuando aplica. Consume `ReservationInformationRepository`, `ChargeRecordQueryPort`, `FailureRecorderPort`, `ReservationLockPort` y `OutboxPort` por nombre.

## Reglas de negocio

1. **Entrada interna sin montos externos** [SPEC RF-001]: UC07/UC08 entregan reserva y desencadenante; los montos se recuperan internamente.
2. **Cancelación moderada** [SPEC RF-002, RF-007]: `0.50 × rental_amount`; sin comisión ni seguro.
3. **Cancelación tardía** [SPEC RF-003, RF-007]: `1.00 × rental_amount`; sin comisión ni seguro.
4. **Estándar** [SPEC RF-005]: liquidación = alquiler bruto − comisión; el seguro no interviene (general-plan D-30). La versión de parámetros referenciada por la reserva (`frozen_parameters_id`) es la fuente autoritativa: los valores aplicados (comisión %) se resuelven de ella y no se copian (general-plan D-24/D-27).
5. **Depósito por disputa** [SPEC RF-006]: el `scope` vigente de general-plan y `dispute_id` identifican la liquidación del 100 % del depósito interno, sin comisión ni seguro.
6. **Intención antes de pasarela** [SPEC RF-009]: registrar desencadenante, alcance, monto, comisión si aplica, depósito, cobro original, capacidad e idempotency key antes de enviar.
7. **Pasarela** [SPEC RF-008; contrato comando]: los campos exigidos quedan fijados: `payer_email` viaja en `payment_metadata` (mensaje de UC07); `payment_method_id` ← `payment_method_type`; `installments` = 1; `description` la fija el sistema; `collector_id` = dato plano de la configuración de la integración (cuenta del Propietario), no se valida.
8. **Confirmación** [SPEC RF-010, RF-012]: aprobado crea registro inmutable; `pending`, `rejected`, `cancelled`, `expired` solo actualizan intención.
9. **Comisión** [SPEC RF-004, RF-014]: comisión = % de la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`, resuelta vía `ReservationInformationRepository.findByReservationId`) × alquiler bruto; se conserva en la intención; comisión o parámetros ausentes → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`; 4 decimales internos y salidas `HALF_UP` a 2 decimales (`contracts/README.md` §3.1).
10. **Trazabilidad** [SPEC RF-009A, RF-015, RF-016]: registros incluyen intención, reserva, propietario, embarcación, bruto, seguro, depósito, monto confirmado, detalle, referencia externa y fecha de creación.
11. **Faltantes** [SPEC RF-013]: alquiler/seguro/depósito requerido ausente → registrar fallo, no calcular parcialmente ni llamar pasarela.
12. **Timeout/error** [SPEC RNF-003; contrato comando]: un timeout o `5xx` deja la intención en `COMMUNICATION_ERROR` y el worker reintenta con la misma clave idempotente (`reservation_id` + `status` + `status_changed_at`, general-plan D-33); nunca se interpreta como éxito; el webhook resuelve el resultado. Fallos de negocio (`400`) → estado fallido para resolución manual.
13. **Webhook** [contrato webhook]: UC05 verifica firma y despacha por `type`; UC10 solo reconcilia su resultado mediante `ReconcileSettlementResult` y no implementa receptor propio.
14. **Inmutabilidad** [SPEC RF-010..RF-014; general-plan §2]: registros confirmados nunca se actualizan ni eliminan.

## Contratos

Fuentes: `external/pasarela-comando-liquidacion.md` y `external/pasarela-webhook-resultados.md`; convenciones en `contracts/README.md` §3.2–§4.4.

### Puerto interno

`RequestSettlementUseCase` es llamado por UC07 y UC08. Su firma final se publica en este plan y se copia en los planes consumidores:

```java
SettlementRequestResult request(SettlementCommand command);
```

El comando debe expresar reserva, desencadenante/alcance, idempotency key y referencias internas; no acepta un monto arbitrario de Reservas. El adaptador recupera y calcula los importes desde registros internos.

### Comando a Mercado Pago

```text
POST /v1/advanced_payments/{id}/disbursements (o equivalente ACL)
Authorization: Bearer <ACCESS_TOKEN>
X-Idempotency-Key: única por intención
```

Payload mínimo del ACL: `disbursements[].amount`, `collector_id` y `external_reference`. El componente de depósito se unifica en `amount` cuando la operación lo requiera, conservando el desglose internamente.

### Webhook compartido

`POST /api/v1/webhook/gateway`; Mercado Pago envía `data.id` y `type`, firma `x-signature` y `x-request-id`. Firma inválida → `401 UNAUTHENTICATED` (la verificación de firma es requisito de la integración con Mercado Pago, no seguridad del proyecto; general-plan §7.1); payload inválido → `400 VALIDATION_ERROR`; fallo interno → `500 INTERNAL_ERROR` para permitir reintento. UC10 procesa `type=merchant_order`/resultado de liquidación, actualiza intención y genera registros solo si el estado final es exitoso.

## Estrategia de testing

Cobertura objetivo [CONV]: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Cobertura | Ubicación |
|---|---|---|
| Unitario dominio | porcentajes, alcances, comisión, depósito, escala 4 | `domain/service/SettlementCalculatorTest` |
| Unitario aplicación | intención, faltantes, idempotencia, puertos y estados externos | `application/service/SettlementServiceTest` |
| Contrato | payload de pasarela, idempotency key, webhook y firma | `contract/*Settlement*Test` |
| Integración | PostgreSQL, outbox, webhook, duplicados y transacción atómica | `integration/SettlementProcessingIT` |
| Resiliencia | timeout, 5xx, retry, DLQ y conciliación | `PaymentGatewayAdapterTest`, `SettlementProcessingIT` |
| Arquitectura | reglas §3.4 e inmutabilidad | `arch/ArchitectureTest` |

- **`ce001_precision_financiera_100_casos`**: ≥100 casos para cancelaciones, estándar y depósito; exactitud `BigDecimal` y escala 4.
- **`ce002_comision_por_alcance`**: cero comisión en cancelación/depósito; estándar usa la comisión de la versión referenciada por la reserva.
- **`ce003_intencion_a_registro`**: toda solicitud queda en intención y todo éxito genera registro trazable.
- **`ce004_fallas_de_pasarela_sin_estado_indefinido`**: timeout, 5xx, respuesta pendiente y webhook fallido dejan intención conciliable, nunca éxito falso.
- **`ce005_fallo_no_es_exito`**: rejected/cancelled/expired/pending no crean `SettlementRecord`.
- **`ce006_una_comision_atomica`**: estándar confirmado crea exactamente un `SettlementRecord` y un `CommissionRecord` relacionados.

## Decisiones de diseño (D-UC10-xx)

Registro de las decisiones de diseño de este plan, con su contexto y estado. Los IDs `D-UC10-xx` son propios de este plan; los `D-xx` sin prefijo UC10 son decisiones del plan general (§9).

| ID | Descripción | Contexto | Decisión | Estado |
|---|---|---|---|---|
| **D-UC10-01** | Firmas del puerto publicado por UC10 para UC07/UC08 | Auditoría de planes | UC10 publica `RequestSettlementUseCase` aquí y copia su firma en UC07/UC08; cambios externos no se aplican | Aplicada |
| **D-UC10-02** | La pasarela puede unificar el depósito o usar una operación relacionada | contrato comando de liquidación | El ACL decide la capacidad soportada; UC10 conserva siempre el componente de depósito | Aplicada |
| **D-UC10-03** | El webhook es compartido por UC05/UC09/UC10 | contrato webhook compartido | UC10 consume solo los resultados asociados a su intención; no crea otro webhook | Aplicada |
| **D-UC10-04** | Migraciones de UC10 | general-plan §4.2 y D-31 | Migración inicial única `V1__initial_schema.sql` (general-plan D-31); UC10 no define ni ordena migraciones propias | Aplicada |
| **D-UC10-05** | Mecanismo transversal de autenticación/autorización | general-plan §7.1 y D-21 | Seguridad fuera de alcance: sin autenticación ni autorización; UC10 no define ni hereda configuración de seguridad | Aplicada |
| **D-UC10-06** | Campos del pagador y metadatos exigidos por la pasarela | contrato comando; mensaje de UC07 | `payer_email` viaja en `payment_metadata` (mensaje de UC07); `payment_method_id` ← `payment_method_type`; `installments` = 1; `description` la fija el sistema; `collector_id` = dato plano de la configuración de la integración (cuenta del Propietario), no se valida | Aplicada |
| **D-UC10-07** | Tratamiento del seguro en la base de liquidación | SPEC 10 RF-004/RF-014 | El seguro no interviene: liquidación = alquiler bruto − comisión (general-plan D-30) | Aplicada |
| **D-UC10-08** | Cálculo, base y ausencia de comisión | SPEC 10 RF-004/RF-014 | Comisión = % de la versión referenciada × alquiler bruto; comisión o parámetros ausentes → `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Aplicada |
| **D-UC10-09** | Escala y redondeo | `contracts/README.md` §3.1 | 4 decimales internos; salidas `HALF_UP` a 2 decimales | Aplicada |
| **D-UC10-10** | Idempotencia de la operación | general-plan D-33 | Clave `reservation_id` + `status` + `status_changed_at`; los reintentos técnicos reutilizan la clave y un `status_changed_at` nuevo es un intento nuevo | Aplicada |
| **D-UC10-11** | Parámetros congelados frente a globales | general-plan D-24/D-27 | UC10 usa la **versión de parámetros referenciada** por la reserva (`frozen_parameters_id`) como fuente autoritativa para reservas ya calculadas (los valores aplicados —comisión %— se resuelven de ella, sin copiarse en la reserva); una nueva versión de `financial_parameters` nunca altera cálculos ya realizados | Aplicada |

## Implementation Phases

### Phase 1: Setup

- [ ] **T001** Consumir JPA, AMQP, errores, outbox, observabilidad y transacciones del núcleo compartido.
- [ ] **T002** Publicar la firma de `RequestSettlementUseCase` y documentar consumidores UC07/UC08, sin editar sus planes.
- [ ] **T003** Configurar ACL, idempotency key, outbox/worker y el manejador UC10 del webhook propiedad de UC05.

### Phase 2: Foundational

- [ ] **T004** Crear `SettlementScope`, `SettlementTrigger`, estados y `SettlementCalculator`; `Money`, `ReservationId`, `OwnerId` y `ClockPort` se consumen del núcleo compartido y no se redeclaran.
- [ ] **T005** Crear puertos, `SettlementCommand`/results y adaptadores de consulta para reserva/cobro; el command incluye `trigger`, `scope` y `dispute_id` cuando aplica. Consume piezas de UC03/UC05 por nombre.
- [ ] **T005a** Congelar la firma pública `SettlementRequestResult request(SettlementCommand)`; `scope` usa el valor vigente del catálogo y `DEPOSIT_DISPUTE` solo cuando existe `dispute_id`. No se usa `void` ni se inventa otro valor.
- [ ] **T006** Crear `SettlementGatewayPort`, DTOs del comando/resultado y mapper ACL de Mercado Pago.
- [ ] **T007** Crear modelos de intención/registro/comisión y repositorios; definir restricciones de idempotencia e inmutabilidad.
- [ ] **T008** Implementar persistencia de intención antes del envío y consulta de intención para webhook/worker.

### Phase 3: User Stories

- [ ] **T009** [US1/US2/US3] Implementar cálculo por cancelación, estándar y depósito con validación de datos internos (la comisión estándar usa el % de la **versión de parámetros referenciada** por la reserva), sin montos externos.
- [ ] **T010** [US1/US2/US3] Implementar `RequestSettlementUseCase`, outbox, envío a pasarela y reintentos con la misma clave.
- [ ] **T011** [US4] Implementar recepción/verificación de webhook, consulta de detalle y `RegisterSettlementResultUseCase`.
- [ ] **T012** [US4/US5] Implementar transacción de confirmación: actualizar intención; en éxito crear registro; la comisión solo se crea en la liquidación estándar con el % de la versión de parámetros referenciada por la reserva.
- [ ] **T012a** Verificar `settlement_intent`, `settlement_record` y `commission_record` contra la migración inicial única `V1__initial_schema.sql`, incluyendo claves idempotentes, FKs, `dispute_id`, estados y restricciones de inmutabilidad (general-plan §4.2, D-31).
- [ ] **T013** `ce001_precision_financiera_100_casos`.
- [ ] **T014** `ce002_comision_por_alcance`.
- [ ] **T015** Prueba de depósito total y trazabilidad con disputa UC08.
- [ ] **T016** `ce003_intencion_a_registro`.
- [ ] **T017** `ce005_fallo_no_es_exito` y `ce006_una_comision_atomica`.
- [ ] **T017a** **`rf013_informacion_interna_incompleta_registra_fallo_sin_llamada`**: cubre RF-013 cuando falta alquiler, seguro o depósito y verifica `FailureRecorderPort` sin cálculo parcial ni llamada a pasarela.

### Phase 4: Resiliencia y Polish

- [ ] **T018** Integración con PostgreSQL/RabbitMQ y disponibilidad para UC12/UC13 mediante la vista financiera.
- [ ] **T019** Pruebas de duplicados de webhook, estados fuera de orden, concurrencia y registros inmutables.
- [ ] **T020** `ce004_fallas_de_pasarela_sin_estado_indefinido` con WireMock, timeout, 5xx, DLQ y conciliación.
- [ ] **T021** ArchUnit, cobertura y revisión de contratos; no modificar contratos ni planes externos.

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012 → T013..T021`.

UC10 consume datos/puertos publicados por UC03 y UC05, y expone `RequestSettlementUseCase` para UC07/UC08. UC12/UC13 solo consumen sus registros confirmados. Las tablas de UC10 forman parte de la migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31); UC10 no define migraciones propias.

## Notes

No modificar `pom.xml`, `general-plan.md`, SPEC, contratos ni planes de UC03/UC05/UC07/UC08/UC09/UC11/UC12/UC13. No implementar transferencias fuera del ACL, estados de éxito para timeouts, comisiones en penalidades/depósitos ni registros mutables.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 10, contratos de liquidación/webhook y `contracts/README.md`.
- [ ] Cada RF/RNF/CE/HU tiene componente, tarea y prueba.
- [ ] Intención, registro, comisión, idempotencia e inmutabilidad están trazados.
- [ ] Solo éxitos externos crean registros financieros.
- [ ] Requisitos de esquema de UC10 verificados contra la migración inicial única (general-plan §4.2, D-31).
