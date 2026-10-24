# Implementation Plan: UC09 - Reembolsar Dinero a Arrendatario

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC09 solicita a la Pasarela de Pago (Mercado Pago) el **reembolso** (total o parcial) de un cobro capturado, por el monto que fija la regla de negocio según el estado de cancelación (flexible, moderada, tardía o por anfitrión) o según el estado `REJECTED` de la disputa de garantía; registra la `RefundIntent` en curso y, cuando la pasarela confirma el reembolso, crea el `RefundRecord` inmutable y registra el `released_amount`. **No calcula ni aplica descuentos por costos transaccionales**, no admite retención parcial del depósito y no recibe montos de Reservas [SPEC HU1, HU2, HU3, RF-001…RF-013, CE-001…CE-005].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. UC09 **no tiene endpoint público**: lo invocan UC07 y UC08 por `RequestRefundUseCase`. El cálculo del monto es una política de dominio pura (`RefundCalculator`); la intención y el mensaje *outbox* se persisten en una sola transacción y un *worker* llama a la pasarela con una clave idempotente derivada de `(reservation_id, estado, tipo de operación)` [general-plan D-33]. El resultado converge en `RegisterRefundResultUseCase`. UC05 es dueño del receptor/verificación del webhook; UC09 registra su manejador `type=refund` y sus puertos `Send…`/`Reconcile…`. `RefundIntent`/`RefundRecord` son propiedad de UC09; las demás tablas se referencian por nombre.

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Reembolsar dinero a arrendatario" con el actor *Pasarela de pago* y con un `<<extend>>` desde "Brindar información de disputa garantía". El disparo desde "Brindar el estado de la reserva" proviene del SPEC 7 (RF-003 a RF-005A).

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud interna sin atributo de origen | `RequestRefundUseCase`, `RefundRequestCommand`, `RequestRefundService` | T006, T008 | T007 |
| **RF-002** flexible y anfitrión: 100 % del total | `RefundCalculator` | T004, T008 | T003, T007 |
| **RF-003** moderada: 50 % del alquiler + 100 % del depósito, sin seguro | `RefundCalculator` | T004 | T003 |
| **RF-003A** tardía: 100 % del depósito | `RefundCalculator` | T004 | T003 |
| **RF-004** `REJECTED` de disputa: depósito íntegro | `RefundCalculator`, `RequestRefundService` (trigger `DISPUTE_REJECTED`) | T016 | T015, T017 |
| **RF-005** sin retención parcial del depósito | `RefundCalculator` (el depósito siempre al 100 %) | T004, T016 | T015, T026 |
| **RF-006** intención con cobro original y clave idempotente | `RefundIntent`, `RefundIntentRepository`, `charge_intent_id` | T001, T002, T008, T009 | T007, T010 |
| **RF-006A** `RegistroDeReembolso` con `intencionDeReembolsoId` | `refund_record.refund_intent_id` (FK `NOT NULL`) | T001, T002, T020 | T010, T024 |
| **RF-007** recibir resultado y registrar | `RegisterRefundResultUseCase`; webhook `type=refund`; respuesta técnica; conciliación | T020, T021, T023 | T019, T021 |
| **RF-008** registro disponible para UC12/UC13 | Índices de `refund_record`; vista `v_financial_records` (dueño UC12/UC13) | T001, T020 | T010, T024 |
| **RF-009** sin registro para rechazado/cancelado/expirado/en proceso | `RegisterRefundResultService` | T020 | T019 |
| **RF-010** error controlado sin depósito/monto registrado | `RequestRefundService` + `FailureRecorderPort` | T008 | T007 |
| **RF-011** distinguir monto reembolsado y costo transaccional | `refund_record.transaction_cost` (nullable) | T001, T020 | T019 |
| **RF-012** sin descuentos transaccionales | `RefundCalculator` (sin lógica de costos); adaptador envía el monto de negocio | T004, T011 | T003, T012, T026 |
| **RF-013** reserva, propietario, embarcación y disputa en el registro | `RefundRecord` | T001, T002, T020 | T019, T024 |
| **RNF-001** DTOs con la pasarela | `RefundGatewayRequest/Result`, `MercadoPagoRefundRequest/Response` | T011 | T012 |
| **RNF-002** `BigDecimal` | `RefundCalculator`, `Money`, `NUMERIC(18,4)` | T002, T004 | T003, T012 |
| **RNF-003** manejo de errores, timeouts, fallbacks | Worker, `COMMUNICATION_ERROR`, conciliación | T013, T023 | T014, T023, T025 |
| **CE-001** reembolso del depósito rechazado = 100 % | `RefundCalculator` | T016 | **T017** |
| **CE-002** 0 decisiones de daños, 0 retenciones parciales, 0 descuentos | `RefundCalculator`, adaptador | T004, T016 | **T026** |
| **CE-003** trazabilidad intención → registro | FK `refund_intent_id` | T001, T020 | **T024** |
| **CE-004** fallas de comunicación sin estado indefinido | Worker + conciliación | T013, T023 | **T025** |
| **CE-005** historias unificadas para `REJECTED` | Un solo trigger `DISPUTE_REJECTED` sin atributo de origen | T016 | **T018** |
| **HU1** reembolso por cancelación (P1) | Fase 3 | T001–T014 | T003, T007, T010, T012, T014 |
| **HU2** reembolso del depósito por disputa rechazada (P1) | Fase 4 | T015–T018 | T015, T017, T018 |
| **HU3** registrar resultado de la pasarela (P1) | Fase 5 | T019–T026 | T019, T021–T026 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.1. Para UC09: Spring Data JPA, Spring AMQP, Resilience4j, cliente HTTP, Flyway, MapStruct, ArchUnit, WireMock y Awaitility. Se registran en el setup común sin referencias a tareas externas.
**Storage**: PostgreSQL 16+; `refund_intent` y `refund_record` son tablas de UC09 (creadas por la migración inicial única, general-plan §4.2, D-31); `NUMERIC(18,4)`, `timestamptz` [general-plan §4]. `operational_failure.reservation_id` es nullable y sin FK; `payload_ref` referencia el mensaje o JSON almacenado.
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), WireMock, Awaitility, ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: Sin meta propia: el SPEC 09 no define metas; aplica el timeout de pasarela de 10 s del general-plan §7.2 [CONV].
**Constraints**: `BigDecimal` con 4 decimales internos [SPEC RNF-002]; sin descuento por costos transaccionales [SPEC RF-012]; sin retención parcial [SPEC RF-005]; un timeout no es un fallo [general-plan D-08]; el depósito tiene un único desenlace (reembolso **o** liquidación) [UC07 RF-009B; general-plan D-16]
**Scale/Scope**: 2 tablas propias (`refund_intent`, `refund_record`); 0 endpoints REST propios; 13 RF + 3 RNF + 5 CE + 3 HU

**Estado actual del repositorio**: existen `SeashareM3Application`, `application.properties`, `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. No existen `domain`/`application`/`infrastructure`, migraciones ni reglas ArchUnit.

## Project Structure

### Documentation (this feature)

```text
docs/features/009-reembolsar-dinero-arrendatario/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                    # §3.4 catálogo, §4.4 colas y webhook
└── external/
    ├── pasarela-comando-reembolso.md            # POST /v1/payments/{id}/refunds
    └── pasarela-webhook-resultados.md           # POST /api/v1/webhook/gateway (type=refund)
```

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── domain/
│   ├── model/
│   │   ├── RefundIntent.java                    # T002  [SPEC RF-006]
│   │   └── RefundRecord.java                    # T002  inmutable [SPEC RF-006A, RF-013]
│   ├── valueobject/
│   │   ├── RefundTrigger.java                   # T002  estados de cancelación + DISPUTE_REJECTED [CONV]
│   │   ├── RefundScope.java                     # T002  FULL / HALF_RENTAL_PLUS_DEPOSIT / DEPOSIT_ONLY [CONV]
│   │   ├── RefundOperationType.java             # T002  REFUND [SPEC SolicitudReembolsoPasarela]
│   │   └── RefundIntentStatus.java              # T002  [general-plan §8]
│   └── service/
│       └── RefundCalculator.java                # T004  [general-plan §3.3]
├── application/
│   ├── port/in/
│   │   ├── RequestRefundUseCase.java            # T006  [general-plan §3.5]  (puerto expuesto)
│   │   ├── SendRefundToGatewayUseCase.java      # T006  [CONV] el worker es un adaptador y solo puede invocar port.in (general-plan §3.4)
│   │   ├── RegisterRefundResultUseCase.java     # T006  [general-plan §3.5]
│   │   └── ReconcileRefundIntentsUseCase.java   # T006  [CONV] job de conciliación
│   ├── port/out/
│   │   ├── RefundIntentRepository.java          # T005
│   │   ├── RefundRecordRepository.java          # T005  [CONV]
│   │   └── RefundGatewayPort.java               # T005  [general-plan §3.5]
│   ├── service/
│   │   ├── RequestRefundService.java            # T008
│   │   ├── SendRefundToGatewayService.java      # T013
│   │   ├── RegisterRefundResultService.java     # T020
│   │   └── ReconcileRefundIntentsService.java   # T023
│   └── dto/
│       ├── RefundRequestCommand.java            # T006  = SolicitudReembolso [SPEC]
│       ├── RefundGatewayRequest.java            # T011  = SolicitudReembolsoPasarela [SPEC]
│       └── RefundGatewayResult.java             # T011  = ResultadoReembolsoPasarela [SPEC]
└── infrastructure/
    ├── adapter/in/messaging/
    │   └── RefundCommandListener.java           # T013  worker de la cola interna
    ├── adapter/in/webhook/                      # UC05 recibe/verifica; UC09 registra el manejador type=refund (T021)
    ├── adapter/out/gateway/
    │   ├── MercadoPagoRefundAdapter.java        # T011, T022
    │   └── dto/{MercadoPagoRefundRequest, MercadoPagoRefundResponse}.java   # T011
    └── adapter/out/persistence/
        ├── RefundIntentJpaEntity.java / RefundRecordJpaEntity.java          # T009
        ├── RefundIntentJpaRepository.java / RefundRecordJpaRepository.java  # T009
        ├── RefundPersistenceMapper.java         # T009  (MapStruct)
        └── RefundIntentPersistenceAdapter.java / RefundRecordPersistenceAdapter.java  # T009

src/main/resources/db/migration/
└── V1__initial_schema.sql                # T001  migración inicial única; contiene refund_intent y refund_record (general-plan §4.2, D-31)

src/test/java/com/seashare/seasharem3/
├── domain/service/RefundCalculatorTest.java                              # T003, T015
├── application/service/{RequestRefundServiceTest, RegisterRefundResultServiceTest}.java  # T007, T019
├── infrastructure/adapter/out/gateway/MercadoPagoRefundAdapterTest.java  # T012, T022
├── infrastructure/adapter/out/persistence/RefundPersistenceAdapterTest.java  # T010
└── acceptance/Uc09AcceptanceTest.java                                    # T017, T018, T024–T026
```

**Puertos expuestos**: `RequestRefundUseCase` lo llaman UC07 y UC08. Firma [CONV], publicada al completar T006:

```java
public interface RequestRefundUseCase {
    void request(RefundRequestCommand command);
}
public record RefundRequestCommand(
    UUID reservationId,
    RefundTrigger trigger,   // CANCELLED_FLEXIBLE | CANCELLED_MODERATE | CANCELLED_STRICT
                             // | CANCELLED_BY_HOST | DISPUTE_REJECTED
    UUID disputeId,          // nullable; solo con DISPUTE_REJECTED [SPEC RF-013]
    String operationKey      // identidad de operación de UC07 (RF-009A) o event_key de UC08 (RF-008), tomada tal cual [CONV]
) {}
```

Sin monto ni atributo de origen financiero [SPEC RF-001, RF-007 UC08]. Ante datos faltantes (RF-010) UC09 **registra el fallo en `operational_failure` y retorna sin solicitar nada**; los errores técnicos (BD) se propagan para que el *listener* aplique `nack` (README §4.4). Una `operationKey` repetida es idempotente. `RefundIntentRepository` y `RefundRecordRepository` los consumen UC12/UC13 (por la vista `v_financial_records`) solo por nombre de tabla.

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; el listener y los jobs solo invocan `application.port.in`; las entidades JPA no salen de `adapter/out/persistence` [general-plan §3.4].

## Reglas de negocio

Todas provienen del SPEC 9 salvo indicación. Los importes del ejemplo salen del contrato UC04 (alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; **ilustrativos**).

1. **Montos por estado** [SPEC RF-002, RF-003, RF-003A, RF-004, RF-005; UC07 RF-003…RF-005A]:

| `trigger` | Monto solicitado | `scope` | Ejemplo |
|---|---|---|---|
| `CANCELLED_FLEXIBLE` | 100 % del valor total pagado (alquiler + seguro + depósito) | `FULL` | `990000.00` |
| `CANCELLED_BY_HOST` | 100 % del valor total pagado | `FULL` | `990000.00` |
| `CANCELLED_MODERATE` | 50 % del alquiler + 100 % del depósito; **el seguro no se reembolsa** | `HALF_RENTAL_PLUS_DEPOSIT` | `450000.00 + 30000.00 = 480000.00` |
| `CANCELLED_STRICT` | 100 % del depósito; alquiler y seguro no se reembolsan | `DEPOSIT_ONLY` | `30000.00` |
| `DISPUTE_REJECTED` | 100 % del depósito cobrado y registrado; sin retención parcial | `DEPOSIT_ONLY` | `30000.00` |

   Rendimiento del 50 %: escala interna de 4 decimales; redondeo final hacia la pasarela `HALF_UP` a 2 decimales **[CONV]** (README §3.1).
2. **Origen único del monto** [SPEC RF-001; UC08 RF-007]: los importes salen de `reservation_information` (`rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`). Reservas nunca envía montos.
3. **Reembolsar el cobro original** [SPEC RF-002…RF-004; general-plan D-32]: la `ChargeIntent` original es el cobro aprobado de la reserva (`CAPTURED`) y se guarda como `charge_intent_id`; los reembolsos —totales o parciales— se ejecutan con `POST /v1/payments/{id}/refunds`.
4. **Sin descuentos** [SPEC RF-012; caso extremo]: el sistema solicita el monto de negocio y solo registra el costo o monto neto que la pasarela reporte. La nota "menos costos transaccionales" de `sea-share.md` §2.2 y `consistencia-m2-m3.md` no se calcula aquí (D-UC09-03).
5. **Datos faltantes** [SPEC RF-010, caso extremo]: sin el monto de alquiler o el depósito requerido por el `trigger`, o sin cobro original aprobado, no se calcula nada parcial: se registra el fallo en `operational_failure` y no se contacta a la pasarela **[CONV]**.
6. **Intención** [SPEC RF-006]: `RefundIntent` en `PENDING_SEND` con `trigger`, `scope`, `amount`, `charge_intent_id`, clave idempotente y, cuando aplique, `dispute_id` (D-UC09-02); más el mensaje *outbox* en la **misma transacción**. La clave idempotente se deriva de forma determinística de `reservationId` + `trigger` + `operationKey` (la identidad de operación por intento que aporta UC07 es `reservation_id` + `status` + `status_changed_at`) **[CONV; general-plan D-33]**. Reintentos técnicos usan la misma clave [general-plan §7.2].
7. **Resultado** [SPEC RF-007, RF-009; general-plan §8]: la intención se actualiza siempre; al confirmarse el reembolso por pasarela, UC09 registra el `released_amount`. `RefundRecord` solo con reembolso **completado** y confirmado, en la misma transacción, con `refund_intent_id`, monto confirmado, detalle, referencia externa, reserva, propietario, embarcación y disputa si aplica. Nunca para rechazado, cancelado, expirado o en proceso. Una notificación repetida no altera lo registrado [SPEC caso extremo]. El costo transaccional (`transaction_cost`) es nullable: se registra cuando la respuesta o consulta de la pasarela lo informa y queda `NULL` cuando no **[CONV]**.
8. **Idempotencia y exclusión mutua del depósito** [SPEC casos extremos; UC07 RF-009A/RF-009B; UC08 RF-008]: la misma `operationKey` no genera una segunda solicitud; solo `PENDING` admite intentos nuevos y un `status_changed_at` nuevo es un intento nuevo [general-plan D-33]. El desenlace del depósito lo aplica UC08 mediante `deposit_disposition` con un único *compare-and-set*, invocado por UC09 con `OpenDepositTrackingUseCase` [general-plan D-34].
9. **Fallas de la pasarela** [SPEC RNF-003, caso extremo]: timeout, `5xx`, caída → la intención queda en `COMMUNICATION_ERROR` (no exitosa ni rechazada) y se reintenta con la misma clave hasta 5 intentos con *backoff* exponencial (base 1 s); agotados, el mensaje va a la DLQ y se registra en `operational_failure` [general-plan §7.2; README §4.4]. Un `4xx` de negocio (por ejemplo, fondos insuficientes o plazo excedido) detiene los reintentos y deja la intención en error para revisión manual [contrato §5].

## Contratos de API

UC09 **no expone endpoints REST propios** (README §2: "UC09 y UC10: los invocan UC07, UC08 y los eventos automáticos; solo se comunican hacia afuera con la pasarela").

| Interfaz | Dirección | Contrato | Resumen |
|---|---|---|---|
| `RequestRefundUseCase` (puerto) | UC07 / UC08 → UC09 | — | Ver «Puertos expuestos» |
| `POST /v1/payments/{id}/refunds` | Worker → Mercado Pago | `pasarela-comando-reembolso.md` | `{id}` = pago original; `amount` opcional (sin cuerpo = reembolso total); `X-Idempotency-Key` |
| `POST /api/v1/webhook/gateway` (`type=refund`) | Mercado Pago → sistema | `pasarela-webhook-resultados.md` | Se consulta el recurso, se actualiza `RefundIntent` y se crea `RefundRecord` |

El contrato de reembolso cubre `POST …/refunds` para reembolsos totales y parciales [general-plan D-32]. Códigos del webhook: ver `UC05` (controlador compartido).

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | `RefundCalculator`: 5 triggers, el depósito siempre al 100 %, `BigDecimal` | `RefundCalculatorTest` |
| Unitario (`application`) | datos faltantes, cobro original, idempotencia, resultados | `RequestRefundServiceTest`, `RegisterRefundResultServiceTest` |
| Integración (Testcontainers PostgreSQL) | esquema (migración inicial única), FK, `UNIQUE(idempotency_key)`, trigger anti-mutación | `RefundPersistenceAdapterTest` |
| Integración (RabbitMQ + WireMock) | outbox → worker → pasarela, misma clave en reintentos | `RefundCommandListenerTest`, `MercadoPagoRefundAdapterTest` |
| Arquitectura | reglas §3.4 | `ArchitectureTest` (base y ampliación T027) |

**Pruebas de aceptación (CE-001…CE-005)**:

- **`ce001_reembolso_por_disputa_rechazada_es_el_100_por_ciento_del_deposito`** (T017).
- **`ce002_sin_decisiones_de_danos_sin_retenciones_parciales_ni_descuentos`** (T026).
- **`ce003_trazabilidad_intencion_registro_en_reembolsos`** (T024).
- **`ce004_fallas_de_comunicacion_sin_reembolso_en_estado_indefinido`** (T025).
- **`ce005_rechazado_de_disputa_y_ausencia_de_reclamo_se_tratan_igual`** (T018): ambos orígenes llegan como el mismo `trigger` y producen la misma intención.

## Decisiones de diseño (D-UC09-xx)

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC09-01** | Las piezas técnicas compartidas no deben coordinarse mediante tareas externas | Auditoría de planes | UC09 consume `FailureRecorderPort`, `OutboxPort` y AMQP por contratos; no los redeclara ni referencia tareas de otro caso de uso | Aplicada |
| **D-UC09-02** | `refund_intent` necesita `dispute_id` y detalle del estado externo | general-plan §4 vs SPEC 9 | El esquema de UC09 (migración inicial única) incluye `dispute_id` nullable y `status_detail` | Aplicada |
| **D-UC09-03** | `sea-share.md` y `consistencia-m2-m3.md` dicen que la cancelación flexible reembolsa "menos costos transaccionales"; el SPEC 9 prohíbe calcularlos (RF-012) | `sea-share.md` §2.2 vs SPEC RF-012 | Rige el SPEC: se solicita el monto íntegro y se registra lo que la pasarela reporte | Decidida |
| **D-UC09-04** | El contrato solo cubre `POST …/refunds`; el SPEC exige cubrir los montos de RF-002…RF-004 según el estado del cobro original | `pasarela-comando-reembolso.md` vs SPEC RF-002…RF-004 | Los reembolsos —totales o parciales— se ejecutan con `POST /v1/payments/{id}/refunds`; el cobro es directo con captura automática [general-plan D-32] | Decidida |
| **D-UC09-05** | general-plan §3.5 lista 2 puertos de entrada para UC09; el diseño agrega `SendRefundToGatewayUseCase` y `ReconcileRefundIntentsUseCase` (el worker y el job son adaptadores y solo pueden invocar `port.in`) | general-plan §3.5 vs §3.4 | Se agregan como [CONV]; son puertos propios de UC09 | Aplicada |
| **D-UC09-06** | El SPEC dice que no define deduplicación explícita de notificaciones repetidas; el contrato y general-plan §5 sí deduplican por `data.id` + clave idempotente | SPEC 9 caso extremo vs webhook §3.5 | Se sigue el contrato y general-plan; el resultado ya registrado no se altera | Aplicada |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad granular y verificable; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias se expresan mediante puertos, firmas y tablas, no mediante tareas externas.

### Phase 1: Setup

- [ ] **T031** · registrar dependencias AMQP, Resilience4j, WireMock, Awaitility y base de `ArchitectureTest`.

### Phase 2: Foundational

- [ ] **T032** · consumir `DomainException`, `FailureRecorderPort`, `OutboxPort` y configuración AMQP común; registro REQUIRES_NEW y reproceso por el catálogo compartido.

### Phase 3: US1 — Reembolso por cancelación (HU1; RF-001…RF-003A, RF-005, RF-006, RF-010, RF-012; RNF-001…RNF-003)

- [ ] **T001** · Verificar `refund_intent` y `refund_record` contra la migración inicial única: `dispute_id` nullable, `status_detail`, scopes vigentes, FK e índices, inmutabilidad de `refund_record` y privilegios. UC09 no define migraciones propias (general-plan §4.2, D-31).
- [ ] **T002** · Dominio: `RefundTrigger`, `RefundScope`, `RefundOperationType`, `RefundIntentStatus`, `RefundIntent` (transiciones), `RefundRecord` (inmutable). · M: `none` · P: `pending`
- [ ] **T003** · Pruebas de `RefundCalculator` para los 4 estados de cancelación con los importes ilustrativos (`990000.00`, `480000.00`, `30000.00`, `990000.00`), seguro excluido en moderada y tardía, y `BigDecimal` sin pérdida. · M: `none` · P: `pending`
- [ ] **T004** · `RefundCalculator` (dominio puro). Redondeo final hacia la pasarela `HALF_UP` a 2 decimales **[CONV]** (README §3.1). · M: `none` · P: `pending`
- [ ] **T005** · Puertos de salida: `RefundIntentRepository`, `RefundRecordRepository`, `RefundGatewayPort` (`send`, `fetchRefund`) y consumo de `ChargeIntentRepository.findApprovedByReservationId` publicado por UC05.
- [ ] **T006** · Puertos de entrada y `RefundRequestCommand`; publicar la firma de `RequestRefundUseCase`.
- [ ] **T007** · Pruebas de `RequestRefundService`: 4 triggers; sin depósito o alquiler → fallo registrado y sin llamada a la pasarela (RF-010); sin cobro original aprobado → fallo registrado sin solicitar nada; `operationKey` repetida → sin segunda intención; el cobro original aprobado (`CAPTURED`) se asocia como `charge_intent_id`. · M: `none` · P: `pending`
- [ ] **T008** · `RequestRefundService` (transacción única: intención + *outbox*; sin atributo de origen; clave idempotente derivada de `(reservation_id, estado, tipo de operación)`). Solo `PENDING` admite nuevos intentos; estados terminales son idempotentes [general-plan D-33]. Consume los puertos compartidos por nombre.
- [ ] **T009** · Persistencia: entidades JPA, repositorios, mapper MapStruct y adaptadores. · M: `none` · P: `pending`
- [ ] **T010** · Integración (Testcontainers): FK a `charge_intent`, `UNIQUE(idempotency_key)`, trigger anti-mutación en `refund_record`. · M: `none` · P: `pending`
- [ ] **T011** · ACL de la pasarela: `RefundGatewayRequest/Result`, `MercadoPagoRefundRequest/Response`, `MercadoPagoRefundAdapter.send` (`POST /v1/payments/{id}/refunds`, `X-Idempotency-Key`, `amount` explícito). Sin cálculo de descuentos (RF-012). Los reembolsos totales y parciales usan este mismo endpoint [general-plan D-32]. · M: `none` · P: `pending`
- [ ] **T012** · Pruebas WireMock del envío: cuerpo y cabeceras del contrato, monto exacto, sin deducciones. · M: `none` · P: `pending`
- [ ] **T013** · *Worker*: `RefundCommandListener` + `SendRefundToGatewayService` con timeout 10 s y *circuit breaker*; timeout/`5xx` → `COMMUNICATION_ERROR` y reintento con la misma clave (5 intentos con *backoff* exponencial base 1 s; agotados → DLQ y registro en `operational_failure`); `4xx` de negocio → sin reintento y en error. Cola interna `finance.refund-command.v1` (DLQ `finance.refund-command.v1.dlq`). · M: `none` · P: `pending`
- [ ] **T014** · Pruebas del *worker*: reintentos con la misma clave, `COMMUNICATION_ERROR` no es rechazo, `4xx` no se reintenta, 5 intentos con *backoff* exponencial (base 1 s) y DLQ al agotarlos (RNF-003). · M: `none` · P: `pending`

### Phase 4: US2 — Reembolso del depósito por disputa rechazada (HU2; RF-004, RF-005; CE-001, CE-005)

- [ ] **T015** · Pruebas: `DISPUTE_REJECTED` → 100 % del depósito (`30000.00`), nunca parcial; con `disputeId`; el comando no lleva atributo de origen. · M: `none` · P: `pending`
- [ ] **T016** · Implementar `DISPUTE_REJECTED` en `RefundCalculator` y `RequestRefundService`, copiando `disputeId` a la intención. Interacción con `deposit_disposition` (UC08, único *compare-and-set*) por `OpenDepositTrackingUseCase` [general-plan D-34]. · M: `none` · P: `pending`
- [ ] **T017** · **`ce001_reembolso_por_disputa_rechazada_es_el_100_por_ciento_del_deposito`** (CE-001). · M: `none` · P: `pending`
- [ ] **T018** · **`ce005_rechazado_de_disputa_y_ausencia_de_reclamo_se_tratan_igual`** (CE-005). · M: `none` · P: `pending`

### Phase 5: US3 — Registrar el resultado del reembolso (HU3; RF-006A, RF-007…RF-009, RF-011, RF-013; CE-003, CE-004)

- [ ] **T019** · Pruebas de `RegisterRefundResultService`: completado, en proceso, rechazado, cancelado, expirado; repetido no altera el registro; sin intención asociada → `operational_failure`; resultado de disputa tras reembolso ya solicitado → no segunda devolución y se registra la inconsistencia; `transaction_cost` informado y ausente (RF-011). · M: `none` · P: `pending`
- [ ] **T020** · `RegisterRefundResultService`: actualiza siempre la intención; crea `RefundRecord` solo en `COMPLETED`, atómico, con `refund_intent_id`, monto confirmado, detalle, referencia externa, reserva, propietario, embarcación y disputa. UC09 registra el monto reembolsado confirmado y el `released_amount` al confirmarse el reembolso; el desenlace del depósito pertenece a UC08 (`deposit_disposition`, general-plan D-34). `transaction_cost` se registra solo cuando la pasarela lo informa; si no, `NULL`.
- [ ] **T021** · Registrar el manejador `type=refund` en el despachador propiedad de UC05 y conectar `RegisterRefundResultUseCase`; UC09 no implementa otro receptor ni verificador.
- [ ] **T022** · `MercadoPagoRefundAdapter.fetchRefund` y pruebas WireMock. · M: `none` · P: `pending`
- [ ] **T023** · `ReconcileRefundIntentsService` + registro en `GatewayReconciliationJob` (compartido, `UC05·T023`): reintenta/consulta intenciones sin resultado con la misma clave. · M: `none` · P: `pending`
- [ ] **T024** · **`ce003_trazabilidad_intencion_registro_en_reembolsos`** (CE-003, RF-006A, RF-008, RF-013). · M: `none` · P: `pending`
- [ ] **T025** · **`ce004_fallas_de_comunicacion_sin_reembolso_en_estado_indefinido`** (CE-004). · M: `none` · P: `pending`
- [ ] **T026** · **`ce002_sin_decisiones_de_danos_sin_retenciones_parciales_ni_descuentos`** (CE-002, RF-005, RF-012). · M: `none` · P: `pending`

### Phase 6: Polish & Cross-Cutting Concerns

- [ ] **T027** · ArchUnit: `RefundCalculator` y `domain` sin Spring/JPA; `RefundRecord` sin *setters*; listener/jobs sin repositorios; UC09 no depende de `SettlementIntent`. · M: `none` · P: `pending`
- [ ] **T028** · Alinear documentos: `pasarela-comando-reembolso.md` (costo transaccional) y general-plan §4 (`dispute_id`, `status_detail`, `scope`). · M: `none` · P: `pending`
- [ ] **T029** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending`
- [ ] **T030** · `./mvnw clean verify` y ejecución de CE-001…CE-005. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
Núcleo compartido + contratos publicados por UC05 ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010
                                                                                          └─> T011 ─> T012 ─> T013 ─> T014
T015 ─> T016 ─> T017 ─> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027 ─> T028 ─> T029 ─> T030
```

- **Depende de**: el esquema (migración inicial única) y la función de inmutabilidad de `charge_record` publicados por UC05, `ChargeIntentRepository.findApprovedByReservationId`, el despachador webhook de UC05 y los puertos de información de reserva.
- **Dependencias externas**: UC07 y UC08 consumen `RequestRefundUseCase` por su firma publicada; no se coordinan mediante tareas externas.
- **Orden interno**: T011/T013 usan el contrato `pasarela-comando-reembolso.md`; T016 se apoya en `OpenDepositTrackingUseCase` (UC08, general-plan D-34).

## Notes

- `RequestRefundUseCase` usa el catálogo único de puertos y la firma compartida con UC05/UC10; UC09 no crea un webhook propio.
- UC09 es responsable de escribir `released_amount` al confirmarse la liberación/reembolso por pasarela; el desenlace del depósito pertenece a UC08 (`deposit_disposition`, D-34).

- Rige el cobro directo con captura automática: no hay autorización retenida y todo reembolso (total o parcial) se ejecuta con `POST /v1/payments/{id}/refunds` [general-plan D-32].
- UC09 no decide si hay daños ni retiene parcialmente: solo ejecuta el reembolso total del depósito cuando UC08 informa `REJECTED`.
- Etiquetas: `[SPEC]`, `[CONV]`.
- Los valores numéricos de los ejemplos son ilustrativos.

