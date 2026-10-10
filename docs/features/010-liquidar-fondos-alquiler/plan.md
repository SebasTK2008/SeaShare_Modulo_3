# Implementation Plan: UC10 - Liquidar fondos de alquiler

**Date**: 2026-10-09  
**Spec**: [spec.md](spec.md)

## Summary

UC10 es el caso de uso interno que solicita dispersiones al propietario por tres motivos: cancelación moderada (50 % del alquiler), cancelación tardía (100 % del alquiler), liquidación estándar de una reserva `COMPLETADA` y liquidación total del depósito cuando una disputa termina en `COMPLETADO`. No recibe montos desde Reservas: recupera los valores de `reservation_information` y conserva la comisión calculada en la intención de dispersión [SPEC RF-001..RF-009, RF-016].

El sistema registra una `SettlementIntent` antes de enviar el comando a Mercado Pago. La respuesta/webhook actualiza siempre la intención; solo un resultado externo exitoso crea un `SettlementRecord` inmutable y, únicamente para liquidación estándar, un `CommissionRecord` inmutable en la misma transacción. Operaciones rechazadas, canceladas, expiradas o pendientes no crean registros exitosos [SPEC RF-010..RF-014].

Enfoque técnico: servicio Spring Boot único con arquitectura hexagonal (`domain` / `application` / `infrastructure`), puertos internos consumidos por UC07/UC08, ACL de pasarela para el contrato de disbursement, outbox/worker para reintentos y webhook compartido firmado. UC10 es dueño de `settlement_intent`, `settlement_record` y `commission_record`; UC12/UC13 solo los consultan. Las piezas de otros planes se referencian por nombre y no se replanifican.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente | Tareas | Prueba |
|---|---|---|---|
| RF-001 | `RequestSettlementUseCase`, commands internos | T005-T008 | T011, T012 |
| RF-002, RF-003, RF-007 | `SettlementCalculator`, `SettlementScope` | T004, T009 | T013 |
| RF-004, RF-005 | `SettlementIntent`, `FrozenParameters` | T004, T009 | T014 |
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
**Primary Dependencies**: Spring Boot 4.1.1; Spring Web MVC, Validation, Data JPA (Hibernate), AMQP, Security OAuth2 Resource Server, Actuator, Flyway, MapStruct, Resilience4j, Micrometer/Prometheus, ArchUnit y ShedLock opcional. UC10 usa especialmente JPA, AMQP, Web MVC para webhook, cliente HTTP de pasarela, Testcontainers, WireMock y Awaitility. Dependencias compartidas se agregan en `UC11·T001`–`UC11·T009`; este plan no modifica `pom.xml`.  
**Storage**: PostgreSQL 16+, `NUMERIC(18,4)` para importes y `timestamptz` para instantes. UC10 es dueño de `settlement_intent`, `settlement_record` y `commission_record`; lee `reservation_information` y `charge_intent/charge_record` por puertos de sus dueños [general-plan §4].  
**Messaging**: RabbitMQ 3.13+ con outbox, colas quorum, confirmaciones de publicador y DLQ; comandos de pasarela se envían desde worker y los resultados entran por el webhook compartido [general-plan §5; contratos externos].  
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers PostgreSQL/RabbitMQ, WireMock para Mercado Pago, Awaitility y ArchUnit [CONV].  
**Target Platform**: contenedores Docker sobre Linux; desarrollo con Docker Compose [SPEC general-plan].  
**Project Type**: servicio backend único hexagonal, sin frontend propio.  
**Performance Goals**: el SPEC no fija SLA numérico [NEEDS CLARIFICATION OQ-UC10-01]; rigen timeouts/reintentos de pasarela y procesamiento idempotente. Un timeout nunca se interpreta como éxito.  
**Constraints**: `BigDecimal` a 4 decimales; comisión congelada en la reserva/intención; cancelaciones y depósito no crean comisión; registros financieros inmutables; solo confirmación exitosa crea `SettlementRecord`; sin datos asumidos ni registros exitosos parciales; sin datos sensibles de pago [SPEC RNF-002/003; general-plan §2].  
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
│   │   ├── ChargeRecordQueryPort.java             # dueño UC05, solo referencia
│   │   ├── FailureRecorderPort.java               # UC11·T007
│   │   └── OutboxPort.java                        # UC11·T009
│   ├── service/SettlementService.java
│   └── dto/
│       ├── SettlementCommand.java
│       ├── SettlementResultCommand.java
│       ├── GatewaySettlementRequest.java
│       └── GatewaySettlementResult.java
└── infrastructure/
    ├── adapter/in/
    │   ├── webhook/GatewayWebhookController.java  # contrato compartido firmado
    │   └── scheduler/SettlementWorker.java        # solo reintentos/outbox, no negocio nuevo
    ├── adapter/out/
    │   ├── gateway/{PaymentGatewayAdapter,GatewaySettlementMapper}.java
    │   ├── persistence/settlement/
    │   │   ├── {SettlementIntentJpaEntity,SettlementRecordJpaEntity,CommissionRecordJpaEntity}.java
    │   │   ├── SettlementPersistenceAdapter.java
    │   │   └── SettlementPersistenceMapper.java
    │   └── messaging/OutboxMessagingAdapter.java
    └── config/{GatewayWebhookSecurityConfig,ProblemDetailsConfig,ObservabilityConfig}.java

src/main/resources/db/migration/
└── V1__create_settlement_tables.sql # solo documentado; consolidación asigna definitivo

src/test/java/com/seashare/seasharem3/
├── arch/ArchitectureTest.java
├── contract/{SettlementCommandContractTest,GatewayWebhookContractTest}.java
├── domain/service/SettlementCalculatorTest.java
├── application/service/SettlementServiceTest.java
├── infrastructure/adapter/out/gateway/PaymentGatewayAdapterTest.java
├── infrastructure/adapter/in/webhook/GatewayWebhookControllerTest.java
└── integration/SettlementProcessingIT.java
```

**Structure Decision**: sigue `general-plan.md` §3.2–§3.4 y el patrón UC01/UC08/UC11. `domain` no depende de Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; adaptadores de entrada invocan `application.port.in`; adaptadores de salida implementan `application.port.out`; controllers/webhooks no acceden a repositorios; entidades JPA permanecen en `adapter/out/persistence`; DTOs externos viven en infraestructura.

**Firmas referenciadas**: UC10 publica `RequestSettlementUseCase` para UC07 y UC08. Consume `ReservationInformationRepository`, `ChargeRecordQueryPort`, `FailureRecorderPort` y `OutboxPort` por nombre. UC12/UC13 consumen registros mediante la vista financiera; no se implementan ni modifican aquí.

## Reglas de negocio

1. **Entrada interna sin montos externos** [SPEC RF-001]: UC07/UC08 entregan reserva y desencadenante; los montos se recuperan internamente.
2. **Cancelación moderada** [SPEC RF-002, RF-007]: `0.50 × rental_amount`; sin comisión ni seguro.
3. **Cancelación tardía** [SPEC RF-003, RF-007]: `1.00 × rental_amount`; sin comisión ni seguro.
4. **Estándar** [SPEC RF-005]: `rental_amount − frozen_commission − insurance_amount`; conserva el depósito asociado. La comisión proviene de la intención/parámetro congelado, nunca del singleton actual.
5. **Depósito por disputa** [SPEC RF-006]: `DEPOSIT_DISPUTE` liquida el 100 % del depósito interno, sin comisión ni seguro y sin duplicar la decisión de UC08.
6. **Intención antes de pasarela** [SPEC RF-009]: registrar desencadenante, alcance, monto, comisión si aplica, depósito, cobro original, capacidad e idempotency key antes de enviar.
7. **Pasarela** [SPEC RF-008; contrato comando]: enviar `amount`, `collector_id`, `external_reference` y `X-Idempotency-Key`; el adaptador decide la forma Marketplace/Split soportada, no el caso de uso.
8. **Confirmación** [SPEC RF-010, RF-012]: aprobado crea registro inmutable; `pending`, `rejected`, `cancelled`, `expired` solo actualizan intención.
9. **Comisión** [SPEC RF-004, RF-014]: se conserva en la intención; solo estándar confirmado crea exactamente un `CommissionRecord`, atómico con `SettlementRecord`; jamás es atributo del registro de dispersión.
10. **Trazabilidad** [SPEC RF-009A, RF-015, RF-016]: registros incluyen intención, reserva, propietario, embarcación, bruto, seguro, depósito, monto confirmado, detalle, referencia externa y fecha de creación.
11. **Faltantes** [SPEC RF-013]: alquiler/seguro/depósito requerido ausente → registrar fallo, no calcular parcialmente ni llamar pasarela.
12. **Timeout/error** [SPEC RNF-003; contrato comando]: mismo idempotency key para retry; timeout no es rechazo ni éxito; conservar intención en estado de comunicación pendiente/fallida para conciliación.
13. **Webhook** [CONV contrato webhook]: verificar `x-signature`; responder inmediatamente; consultar detalle en Mercado Pago fuera del request y deduplicar por `data.id` + idempotency key.
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

`POST /api/v1/webhook/gateway`; Mercado Pago envía `data.id` y `type`, firma `x-signature` y `x-request-id`. Firma inválida → `401 UNAUTHENTICATED`; payload inválido → `400 VALIDATION_ERROR`; fallo interno → `500 INTERNAL_ERROR` para permitir reintento. UC10 procesa `type=merchant_order`/resultado de liquidación, actualiza intención y genera registros solo si el estado final es exitoso.

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
- **`ce002_comision_por_alcance`**: cero comisión en cancelación/depósito; estándar usa comisión congelada.
- **`ce003_intencion_a_registro`**: toda solicitud queda en intención y todo éxito genera registro trazable.
- **`ce004_fallas_de_pasarela_sin_estado_indefinido`**: timeout, 5xx, respuesta pendiente y webhook fallido dejan intención conciliable, nunca éxito falso.
- **`ce005_fallo_no_es_exito`**: rejected/cancelled/expired/pending no crean `SettlementRecord`.
- **`ce006_una_comision_atomica`**: estándar confirmado crea exactamente un `SettlementRecord` y un `CommissionRecord` relacionados.

## Discrepancias y preguntas abiertas

| ID | Descripción | Propuesta por defecto | Estado |
|---|---|---|---|
| **OQ-UC10-01** | El SPEC no define SLA ni estado exacto para timeout | heredar timeout/retry de pasarela y mantener intención conciliable, nunca éxito | Abierta |
| **D-UC10-01** | UC10 puede ser invocado por UC07 y UC08, pero las firmas se definen en planes distintos | publicar `RequestSettlementUseCase` aquí y copiar la firma en UC07/UC08; cambios externos no se aplican | Decidida |
| **D-UC10-02** | La pasarela puede unificar depósito o usar operación relacionada | ACL decide capacidad soportada; UC10 conserva siempre el componente de depósito | Decidida por contrato |
| **D-UC10-03** | El webhook es compartido por UC05/UC09/UC10 | UC10 consume solo resultados asociados a su intención; no crea otro webhook | Decidida |
| **D-UC10-04** | Numeración de migraciones del bloque C | documentar **V1**; consolidación asigna definitiva | Decidida |
| **OQ-UC10-02** | Mecanismo transversal de autenticación/autorización | heredar configuración compartida; no definir seguridad propia de UC10 | Diferida |

## Implementation Phases

### Phase 1: Setup — Compartido

- [ ] **T001** Referenciar `UC11·T001`–`UC11·T009` para JPA, AMQP, errores, outbox, observabilidad y transacciones.
- [ ] **T002** Publicar la firma de `RequestSettlementUseCase` y documentar consumidores UC07/UC08, sin editar sus planes.
- [ ] **T003** Configurar ACL, idempotency key, outbox/worker y webhook compartido; no duplicar el webhook de otros UC.

### Phase 2: Foundational

- [ ] **T004** Crear `SettlementScope`, estados, `Money`, `SettlementCalculator` y reglas de precisión.
- [ ] **T005** Crear puertos, commands/results y adapters de consulta para reserva/cobro; consumir piezas de UC03/UC05 por nombre.
- [ ] **T006** Crear `SettlementGatewayPort`, DTOs del comando/resultado y mapper ACL de Mercado Pago.
- [ ] **T007** Crear modelos de intención/registro/comisión y repositorios; definir restricciones de idempotencia e inmutabilidad.
- [ ] **T008** Implementar persistencia de intención antes del envío y consulta de intención para webhook/worker.

### Phase 3: User Stories

- [ ] **T009** [US1/US2/US3] Implementar cálculo por cancelación, estándar y depósito con validación de datos internos y sin montos externos.
- [ ] **T010** [US1/US2/US3] Implementar `RequestSettlementUseCase`, outbox, envío a pasarela y reintentos con la misma clave.
- [ ] **T011** [US4] Implementar recepción/verificación de webhook, consulta de detalle y `RegisterSettlementResultUseCase`.
- [ ] **T012** [US4/US5] Implementar transacción de confirmación: actualizar intención; en éxito crear registro; en estándar crear comisión atómicamente.
- [ ] **T013** `ce001_precision_financiera_100_casos`.
- [ ] **T014** `ce002_comision_por_alcance`.
- [ ] **T015** Prueba de depósito total y trazabilidad con disputa UC08.
- [ ] **T016** `ce003_intencion_a_registro`.
- [ ] **T017** `ce005_fallo_no_es_exito` y `ce006_una_comision_atomica`.

### Phase 4: Resiliencia y Polish

- [ ] **T018** Integración con PostgreSQL/RabbitMQ y disponibilidad para UC12/UC13 mediante la vista financiera.
- [ ] **T019** Pruebas de duplicados de webhook, estados fuera de orden, concurrencia y registros inmutables.
- [ ] **T020** `ce004_fallas_de_pasarela_sin_estado_indefinido` con WireMock, timeout, 5xx, DLQ y conciliación.
- [ ] **T021** ArchUnit, cobertura y revisión de contratos; no modificar contratos ni planes externos.

## Dependencies & Execution Order

`T001 → T002 → T003 → T004 → T005 → T006 → T007 → T008 → T009 → T010 → T011 → T012 → T013..T021`.

UC10 consume datos/puertos publicados por UC03 y UC05, y expone `RequestSettlementUseCase` para UC07/UC08. UC12/UC13 solo consumen sus registros confirmados. Migración: **V1**, únicamente como referencia documental; la numeración final y cualquier cambio de otros planes se consolidan después.

## Notes

No modificar `pom.xml`, `general-plan.md`, SPEC, contratos ni planes de UC03/UC05/UC07/UC08/UC09/UC11/UC12/UC13. No implementar transferencias fuera del ACL, estados de éxito para timeouts, comisiones en penalidades/depósitos ni registros mutables.

## Checklist de auto-revisión

- [ ] Alineado con SPEC 10, contratos de liquidación/webhook y `contracts/README.md`.
- [ ] Cada RF/RNF/CE/HU tiene componente, tarea y prueba.
- [ ] Intención, registro, comisión, idempotencia e inmutabilidad están trazados.
- [ ] Solo éxitos externos crean registros financieros.
- [ ] Migración indicada como V1 y ningún cambio externo aplicado.
