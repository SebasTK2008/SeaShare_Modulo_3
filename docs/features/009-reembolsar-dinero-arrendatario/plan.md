# Implementation Plan: UC09 - Reembolsar Dinero a Arrendatario

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC09 solicita a la Pasarela de Pago (Mercado Pago) la **liberación** de una autorización o el **reembolso** de un cobro capturado, por el monto que fija la regla de negocio según el estado de cancelación (flexible, moderada, tardía o por anfitrión) o según el estado `RECHAZADO` de la disputa de garantía; registra la `RefundIntent` en curso y, cuando la pasarela confirma el reembolso, crea el `RefundRecord` inmutable. **No calcula ni aplica descuentos por costos transaccionales**, no admite retención parcial del depósito y no recibe montos de Reservas [SPEC HU1, HU2, HU3, RF-001…RF-013, CE-001…CE-005].

Enfoque técnico: arquitectura hexagonal de tres capas bajo `com.seashare.seasharem3` [general-plan §3.2–§3.4]. UC09 **no tiene endpoint público**: lo invocan UC07 (cancelaciones) y UC08 (`RECHAZADO`) por `RequestRefundUseCase`. El cálculo del monto es una política de dominio pura (`RefundCalculator`, general-plan §3.3); la intención y el mensaje *outbox* se persisten en una sola transacción y un *worker* llama a la pasarela con una clave idempotente propia (general-plan D-08). El resultado entra por respuesta técnica, webhook (`type=refund`, consulta activa) o conciliación, y converge en `RegisterRefundResultUseCase`. `RefundIntent`/`RefundRecord` son del bloque B; `settlement_*` y `deposit_disposition` son del bloque C y se referencian por nombre.

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Reembolsar dinero a arrendatario" con el actor *Pasarela de pago* y con un `<<extend>>` desde "Brindar información de disputa garantía". El disparo desde "Brindar el estado de la reserva" proviene del SPEC 7 (RF-003 a RF-005A).

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir solicitud interna sin atributo de origen | `RequestRefundUseCase`, `RefundRequestCommand`, `RequestRefundService` | T006, T008 | T007 |
| **RF-002** flexible y anfitrión: 100 % del total | `RefundCalculator` | T004, T008 | T003, T007 |
| **RF-003** moderada: 50 % del alquiler + 100 % del depósito, sin seguro | `RefundCalculator` | T004 | T003 |
| **RF-003A** tardía: 100 % del depósito | `RefundCalculator` | T004 | T003 |
| **RF-004** `RECHAZADO` de disputa: depósito íntegro | `RefundCalculator`, `RequestRefundService` (trigger `DISPUTA_RECHAZADO`) | T016 | T015, T017 |
| **RF-005** sin retención parcial del depósito | `RefundCalculator` (el depósito siempre al 100 %) | T004, T016 | T015, T026 |
| **RF-006** intención con cobro original y clave idempotente | `RefundIntent`, `RefundIntentRepository`, `charge_intent_id` | T001, T002, T008, T009 | T007, T010 |
| **RF-006A** `RegistroDeReembolso` con `intencionDeReembolsoId` | `refund_record.refund_intent_id` (FK `NOT NULL`) | T001, T002, T020 | T010, T024 |
| **RF-007** recibir resultado y registrar | `RegisterRefundResultUseCase`; webhook `type=refund`; respuesta técnica; conciliación | T020, T021, T023 | T019, T021 |
| **RF-008** registro disponible para UC12/UC13 | Índices de `refund_record`; vista `v_financial_records` (dueño C) | T001, T020 | T010, T024 |
| **RF-009** sin registro para rechazado/cancelado/expirado/en proceso | `RegisterRefundResultService` | T020 | T019 |
| **RF-010** error controlado sin depósito/monto registrado | `RequestRefundService` + `FailureRecorderPort` | T008 | T007 |
| **RF-011** distinguir monto reembolsado y costo transaccional | `refund_record.transaction_cost` (nullable) | T001, T020 | T019 |
| **RF-012** sin descuentos transaccionales | `RefundCalculator` (sin lógica de costos); adaptador envía el monto de negocio | T004, T011 | T003, T012, T026 |
| **RF-013** reserva, propietario, embarcación y disputa en el registro | `RefundRecord` | T001, T002, T020 | T019, T024 |
| **RNF-001** DTOs con la pasarela | `RefundGatewayRequest/Result`, `MercadoPagoRefundRequest/Response` | T011 | T012 |
| **RNF-002** `BigDecimal` | `RefundCalculator`, `Money`, `NUMERIC(18,4)` | T002, T004 | T003, T012 |
| **RNF-003** manejo de errores, timeouts, fallbacks | Worker, `FALLA_COMUNICACION`, conciliación | T013, T023 | T014, T023, T025 |
| **CE-001** reembolso del depósito rechazado = 100 % | `RefundCalculator` | T016 | **T017** |
| **CE-002** 0 decisiones de daños, 0 retenciones parciales, 0 descuentos | `RefundCalculator`, adaptador | T004, T016 | **T026** |
| **CE-003** trazabilidad intención → registro | FK `refund_intent_id` | T001, T020 | **T024** |
| **CE-004** fallas de comunicación sin estado indefinido | Worker + conciliación | T013, T023 | **T025** |
| **CE-005** historias unificadas para `RECHAZADO` | Un solo trigger `DISPUTA_RECHAZADO` sin atributo de origen | T016 | **T018** |
| **HU1** reembolso por cancelación (P1) | Fase 3 | T001–T014 | T003, T007, T010, T012, T014 |
| **HU2** reembolso del depósito por disputa rechazada (P1) | Fase 4 | T015–T018 | T015, T017, T018 |
| **HU3** registrar resultado de la pasarela (P1) | Fase 5 | T019–T026 | T019, T021–T026 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [general-plan]
**Primary Dependencies**: Spring Boot 4.1.1. Para UC09: Spring Data JPA, Spring AMQP, Resilience4j, cliente HTTP, Flyway, MapStruct, ArchUnit. **Hoy no están en el `pom.xml`**; los agrega `UC11·T001` (incluye AMQP, Resilience4j, WireMock y Awaitility).
**Storage**: PostgreSQL 16+; `NUMERIC(18,4)`, `timestamptz` [general-plan §4]
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL + RabbitMQ), WireMock, Awaitility, ArchUnit [general-plan]
**Target Platform**: Contenedores Docker (Linux) [general-plan]
**Project Type**: Servicio backend único (hexagonal) [general-plan]
**Performance Goals**: No definidos por el SPEC 9 `[NEEDS CLARIFICATION: se buscó en spec.md 009, general-plan.md y contratos de pasarela; solo existe el timeout de 10 s de la pasarela del general-plan §7.2 [CONV]]`
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
│   │   ├── RefundTrigger.java                   # T002  estados de cancelación + DISPUTA_RECHAZADO [CONV]
│   │   ├── RefundScope.java                     # T002  FULL / HALF_RENTAL_PLUS_DEPOSIT / DEPOSIT_ONLY [CONV]
│   │   ├── RefundOperationType.java             # T002  RELEASE / REFUND [SPEC SolicitudReembolsoPasarela]
│   │   └── RefundIntentStatus.java              # T002  [general-plan §10]
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
    ├── adapter/in/webhook/                      # el controlador es de UC05·T019 (compartido); UC09 aporta el mapeo type=refund (T021)
    ├── adapter/out/gateway/
    │   ├── MercadoPagoRefundAdapter.java        # T011, T022
    │   └── dto/{MercadoPagoRefundRequest, MercadoPagoRefundResponse}.java   # T011
    └── adapter/out/persistence/
        ├── RefundIntentJpaEntity.java / RefundRecordJpaEntity.java          # T009
        ├── RefundIntentJpaRepository.java / RefundRecordJpaRepository.java  # T009
        ├── RefundPersistenceMapper.java         # T009  (MapStruct)
        └── RefundIntentPersistenceAdapter.java / RefundRecordPersistenceAdapter.java  # T009

src/main/resources/db/migration/
└── V3x__create_refund_intent_and_refund_record.sql   # T001  (rango V3x, después de la migración de UC05)

src/test/java/com/seashare/seasharem3/
├── domain/service/RefundCalculatorTest.java                              # T003, T015
├── application/service/{RequestRefundServiceTest, RegisterRefundResultServiceTest}.java  # T007, T019
├── infrastructure/adapter/out/gateway/MercadoPagoRefundAdapterTest.java  # T012, T022
├── infrastructure/adapter/out/persistence/RefundPersistenceAdapterTest.java  # T010
└── acceptance/Uc09AcceptanceTest.java                                    # T017, T018, T024–T026
```

**Puertos expuestos**: `RequestRefundUseCase` lo llaman **UC07** (cancelaciones, bloque B) y **UC08** (`RECHAZADO`, bloque C). Firma [CONV], a publicar en el canal al terminar T006:

```java
public interface RequestRefundUseCase {
    void request(RefundRequestCommand command);
}
public record RefundRequestCommand(
    UUID reservationId,
    RefundTrigger trigger,   // CANCELADO_FLEXIBLEMENTE | CANCELADO_MODERADAMENTE | CANCELADO_TARDIAMENTE
                             // | CANCELADO_POR_ANFITRION | DISPUTA_RECHAZADO
    UUID disputeId,          // nullable; solo con DISPUTA_RECHAZADO [SPEC RF-013]
    String operationKey      // identidad de operación de UC07 (RF-009A) o event_key de UC08 (RF-008), tomada tal cual [CONV]
) {}
```

Sin monto ni atributo de origen financiero [SPEC RF-001, RF-007 UC08]. Ante datos faltantes (RF-010) UC09 **registra el fallo en `operational_failure` y retorna sin solicitar nada**; los errores técnicos (BD) se propagan para que el *listener* aplique `nack` (README §4.4). Una `operationKey` repetida es idempotente. `RefundIntentRepository` y `RefundRecordRepository` los consumen el bloque C (UC12/UC13, por la vista `v_financial_records`) solo por nombre de tabla.

**Structure Decision**: servicio único hexagonal; `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; el listener y los jobs solo invocan `application.port.in`; las entidades JPA no salen de `adapter/out/persistence` [general-plan §3.4].

## Reglas de negocio

Todas provienen del SPEC 9 salvo indicación. Los importes del ejemplo salen del contrato UC04 (alquiler `900000.00`, seguro `60000.00`, depósito `30000.00`, total `990000.00`; **ilustrativos**).

1. **Montos por estado** [SPEC RF-002, RF-003, RF-003A, RF-004, RF-005; UC07 RF-003…RF-005A]:

| `trigger` | Monto solicitado | `scope` | Ejemplo |
|---|---|---|---|
| `CANCELADO_FLEXIBLEMENTE` | 100 % del valor total pagado (alquiler + seguro + depósito) | `FULL` | `990000.00` |
| `CANCELADO_POR_ANFITRION` | 100 % del valor total pagado | `FULL` | `990000.00` |
| `CANCELADO_MODERADAMENTE` | 50 % del alquiler + 100 % del depósito; **el seguro no se reembolsa** | `HALF_RENTAL_PLUS_DEPOSIT` | `450000.00 + 30000.00 = 480000.00` |
| `CANCELADO_TARDIAMENTE` | 100 % del depósito; alquiler y seguro no se reembolsan | `DEPOSIT_ONLY` | `30000.00` |
| `DISPUTA_RECHAZADO` | 100 % del depósito cobrado y registrado; sin retención parcial | `DEPOSIT_ONLY` | `30000.00` |

   Rendimiento del 50 %: escala interna de 4 decimales; redondeo final hacia la pasarela **[PEND OQ-UC09-04]**.
2. **Origen único del monto** [SPEC RF-001; UC08 RF-007]: los importes salen de `reservation_information` (`rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`). Reservas nunca envía montos.
3. **Liberar o reembolsar** [SPEC RF-002…RF-004]: "según el estado del cobro original". La `ChargeIntent` original se identifica por el cobro aprobado de la reserva (`AUTORIZADO` → `RELEASE`; `CAPTURADO` → `REFUND`) y se guarda como `charge_intent_id`. Cómo se ejecuta la liberación parcial de una autorización **[PEND OQ-UC09-01]**.
4. **Sin descuentos** [SPEC RF-012; caso extremo]: el sistema solicita el monto de negocio y solo registra el costo o monto neto que la pasarela reporte. La nota "menos costos transaccionales" de `sea-share.md` §2.2 y `consistencia-m2-m3.md` no se calcula aquí (D-UC09-03).
5. **Datos faltantes** [SPEC RF-010, caso extremo]: sin el monto de alquiler o el depósito requerido por el `trigger`, o sin cobro original aprobado, no se calcula nada parcial: se registra el fallo en `operational_failure` y no se contacta a la pasarela **[CONV]**.
6. **Intención** [SPEC RF-006]: `RefundIntent` en `PENDIENTE_ENVIO` con `trigger`, `scope`, `amount`, `charge_intent_id`, clave idempotente y, cuando aplique, `dispute_id` (D-UC09-02); más el mensaje *outbox* en la **misma transacción**. La clave idempotente se deriva de forma determinística de `reservationId` + `trigger` + `operationKey` **[CONV]**. Reintentos técnicos usan la misma clave [general-plan §7.2].
7. **Resultado** [SPEC RF-007, RF-009; general-plan §10]: la intención se actualiza siempre. `RefundRecord` solo con reembolso/liberación **completado** y confirmado, en la misma transacción, con `refund_intent_id`, monto confirmado, detalle, referencia externa, reserva, propietario, embarcación y disputa si aplica. Nunca para rechazado, cancelado, expirado o en proceso. Una notificación repetida no altera lo registrado [SPEC caso extremo]. El costo transaccional (`transaction_cost`) es nullable: se registra cuando la respuesta o consulta de la pasarela lo informa y queda `NULL` cuando no **[CONV]**.
8. **Idempotencia y exclusión mutua del depósito** [SPEC casos extremos; UC07 RF-009A/RF-009B; UC08 RF-008]: la misma `operationKey` no genera una segunda solicitud. Quién ejecuta el *compare-and-set* de `deposit_disposition` (dueño: bloque C) **[PEND OQ-UC09-03]**.
9. **Fallas de la pasarela** [SPEC RNF-003, caso extremo]: timeout, `5xx`, caída → la intención queda en `FALLA_COMUNICACION` (no exitosa ni rechazada) y se reintenta con la misma clave; un `4xx` de negocio (por ejemplo, fondos insuficientes o plazo excedido) detiene los reintentos y deja la intención en error para revisión manual [contrato §5].
10. **No definido por el SPEC 9** `[NEEDS CLARIFICATION]` (siguen abiertas): OQ-UC09-01, OQ-UC09-03 y OQ-UC09-04.

## Contratos de API

UC09 **no expone endpoints REST propios** (README §2: "UC09 y UC10: los invocan UC07, UC08 y los eventos automáticos; solo se comunican hacia afuera con la pasarela").

| Interfaz | Dirección | Contrato | Resumen |
|---|---|---|---|
| `RequestRefundUseCase` (puerto) | UC07 / UC08 → UC09 | — | Ver «Puertos expuestos» |
| `POST /v1/payments/{id}/refunds` | Worker → Mercado Pago | `pasarela-comando-reembolso.md` | `{id}` = pago original; `amount` opcional (sin cuerpo = reembolso total); `X-Idempotency-Key` |
| `POST /api/v1/webhook/gateway` (`type=refund`) | Mercado Pago → sistema | `pasarela-webhook-resultados.md` | Se consulta el recurso, se actualiza `RefundIntent` y se crea `RefundRecord` |

El contrato de reembolso solo describe `POST …/refunds`; **no define cómo se liberan autorizaciones** (OQ-UC09-01). Códigos del webhook: ver `UC05` (controlador compartido).

## Estrategia de testing

Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | `RefundCalculator`: 5 triggers, el depósito siempre al 100 %, `BigDecimal` | `RefundCalculatorTest` |
| Unitario (`application`) | datos faltantes, cobro original, idempotencia, resultados | `RequestRefundServiceTest`, `RegisterRefundResultServiceTest` |
| Integración (Testcontainers PostgreSQL) | migración, FK, `UNIQUE(idempotency_key)`, trigger anti-mutación | `RefundPersistenceAdapterTest` |
| Integración (RabbitMQ + WireMock) | outbox → worker → pasarela, misma clave en reintentos | `RefundCommandListenerTest`, `MercadoPagoRefundAdapterTest` |
| Arquitectura | reglas §3.4 | `ArchitectureTest` (UC11·T003, ampliado en T027) |

**Pruebas de aceptación (CE-001…CE-005)**:

- **`ce001_reembolso_por_disputa_rechazada_es_el_100_por_ciento_del_deposito`** (T017).
- **`ce002_sin_decisiones_de_danos_sin_retenciones_parciales_ni_descuentos`** (T026).
- **`ce003_trazabilidad_intencion_registro_en_reembolsos`** (T024).
- **`ce004_fallas_de_comunicacion_sin_reembolso_en_estado_indefinido`** (T025).
- **`ce005_rechazado_de_disputa_y_ausencia_de_reclamo_se_tratan_igual`** (T018): ambos orígenes llegan como el mismo `trigger` y producen la misma intención.

## Discrepancias y puntos abiertos

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC09-01** | `operational_failure`, `outbox_message`, `FailureRecorderPort`, `OutboxPort` y las dependencias AMQP/Resilience4j debían quedar definidos en las tareas compartidas de UC11 | Reparto de piezas compartidas vs plan UC11 | Se referencian por nombre: `UC11·T001` (dependencias), `UC11·T002` (tablas), `UC11·T007` (puertos) y `UC11·T046` (adaptadores). **Cierre:** el plan UC11 ya los incluye | **Cerrada** |
| **D-UC09-02** | `refund_intent` (general-plan §4) no tiene `dispute_id` (el registro lo exige, RF-013) ni columna para el detalle del estado externo (HU3 escenario 2) | general-plan §4 vs SPEC 9 | Se agregan `dispute_id` (nullable) y `status_detail` a `refund_intent` en la migración V3x (T001). **Cierre:** `refund_intent` es una tabla propia del bloque B | **Cerrada** |
| **D-UC09-03** | `sea-share.md` y `consistencia-m2-m3.md` dicen que la cancelación flexible reembolsa "menos costos transaccionales"; el SPEC 9 prohíbe calcularlos (RF-012) | `sea-share.md` §2.2 vs SPEC RF-012 | Rige el SPEC: se solicita el monto íntegro y se registra lo que la pasarela reporte | Decidida |
| **D-UC09-04** | El contrato describe "reembolso parcial/total" y nada sobre **liberación** de autorizaciones, que el SPEC exige | `pasarela-comando-reembolso.md` vs SPEC RF-002…RF-004 | `RefundOperationType` distingue `RELEASE`/`REFUND`; el adaptador de `RELEASE` queda pendiente (OQ-UC09-01) | **Abierta** |
| **D-UC09-05** | general-plan §3.5 lista 2 puertos de entrada para UC09; el diseño agrega `SendRefundToGatewayUseCase` y `ReconcileRefundIntentsUseCase` (el worker y el job son adaptadores y solo pueden invocar `port.in`) | general-plan §3.5 vs §3.4 | Se agregan como [CONV]. **Cierre:** son puertos propios de UC09 | **Cerrada** |
| **D-UC09-06** | El SPEC dice que no define deduplicación explícita de notificaciones repetidas; el contrato y general-plan §5 sí deduplican por `data.id` + clave idempotente | SPEC 9 caso extremo vs webhook §3.5 | Se sigue el contrato y general-plan; el resultado ya registrado no se altera | Resuelta |

## Preguntas abiertas (OQ-UC09-xx)

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC09-01** | ¿Cómo se libera una autorización (total o parcial, p. ej. 50 % del alquiler + depósito) en Mercado Pago? El contrato solo cubre `POST …/refunds` | T011, T013 | Sin definir: el tipo `RELEASE` queda sin adaptador hasta confirmarse | **Abierta** |
| **OQ-UC09-02** | ¿De qué campo de la respuesta/consulta de la pasarela sale el costo transaccional del reembolso (RF-011)? El contrato no lo incluye | T020 | `transaction_cost` queda `NULL` mientras no haya fuente | **Cerrada** → **Adoptada.** |
| **OQ-UC09-03** | ¿Quién ejecuta el *compare-and-set* de `deposit_disposition` (tabla del bloque C): UC08 antes de llamar a `RequestRefundUseCase`, o UC09? No hay puerto que lo exponga | T008, T016 | UC08 (dueña de la tabla) lo ejecuta antes de invocar; UC09 solo garantiza idempotencia por `operationKey` | **Abierta** (avisar a C) |
| **OQ-UC09-04** | Redondeo del 50 % del alquiler y escala del monto hacia la pasarela (4 decimales internos; el contrato envía `990000.00`) | T004, T011 | Sin regla fijada; se aplica la que se acuerde para todos | **Abierta** |
| **OQ-UC09-05** | Formato y derivación de la clave idempotente (el contrato usa `reembolso-<reserva>-<estado>` de ejemplo) y qué `operationKey` aporta UC07/UC08 | T006, T008 | Clave determinística a partir de `reservationId` + `trigger` + `operationKey` | **Cerrada** → **Adoptada.** |
| **OQ-UC09-06** | ¿Qué hacer si no hay cobro original aprobado? El SPEC solo prevé la ausencia de alquiler/depósito (RF-010), UC08 prevé "cobro no cobrado" | T008 | Registrar fallo en `operational_failure` sin solicitar nada | **Cerrada** → **Adoptada.** |
| **OQ-UC09-07** | Valores de `scope` en `refund_intent` (general-plan §4 solo los define para `settlement_intent`) | T001, T002 | `FULL`, `HALF_RENTAL_PLUS_DEPOSIT`, `DEPOSIT_ONLY` **[CONV]** | **Cerrada** → **Adoptada.** |

**`[NEEDS CLARIFICATION]` consolidado (abiertas):** OQ-UC09-01, OQ-UC09-03 y OQ-UC09-04. Discrepancia abierta: D-UC09-04.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). Las fases 1 y 2 son **Compartido** y remiten a tareas de UC11; las tareas locales empiezan en la Fase 3.

### Phase 1: Setup — **Compartido**

- [ ] `UC11·T001`–`UC11·T003`: starters (incluye AMQP, Resilience4j, WireMock y Awaitility), migración V1 y `ArchitectureTest`.

### Phase 2: Foundational — **Compartido**

- [ ] `UC11·T004`–`UC11·T009`: excepciones base, `ProblemDetailsConfig`, `SecurityConfig`.
- [ ] `UC11·T046`–`UC11·T047`: `operational_failure`, `outbox_message`, `FailureRecorderPort`, `OutboxPort`, adaptadores y `OutboxRelayJob`.

### Phase 3: US1 — Reembolso por cancelación (HU1; RF-001…RF-003A, RF-005, RF-006, RF-010, RF-012; RNF-001…RNF-003)

- [ ] **T001** · Migración `V3x__create_refund_intent_and_refund_record.sql`: `refund_intent` (columnas de general-plan §4 + `dispute_id`, `status_detail`; `scope` con `FULL`/`HALF_RENTAL_PLUS_DEPOSIT`/`DEPOSIT_ONLY`; FK `charge_intent_id` a `charge_intent`; `UNIQUE(idempotency_key)`; índice `(reservation_id)`), `refund_record` (`refund_intent_id NOT NULL` con FK; `transaction_cost` nullable; índices `(owner_id, created_at DESC)`, `(boat_id)`, `(reservation_id)`), trigger anti-`UPDATE`/`DELETE` reutilizando la función de `UC05·T002`. Debe ir **después** de la migración de UC05. · M: `none` · P: `pending`
- [ ] **T002** · Dominio: `RefundTrigger`, `RefundScope`, `RefundOperationType`, `RefundIntentStatus`, `RefundIntent` (transiciones), `RefundRecord` (inmutable). · M: `none` · P: `pending`
- [ ] **T003** · Pruebas de `RefundCalculator` para los 4 estados de cancelación con los importes ilustrativos (`990000.00`, `480000.00`, `30000.00`, `990000.00`), seguro excluido en moderada y tardía, y `BigDecimal` sin pérdida. · M: `none` · P: `pending`
- [ ] **T004** · `RefundCalculator` (dominio puro). Redondeo condicionado a **OQ-UC09-04**. · M: `none` · P: `pending`
- [ ] **T005** · Puertos de salida: `RefundIntentRepository`, `RefundRecordRepository`, `RefundGatewayPort` (`send`, `fetchRefund`); solicitar a UC05 el método `ChargeIntentRepository.findApprovedByReservationId` (`UC05·T005`). · M: `none` · P: `pending`
- [ ] **T006** · Puertos de entrada y `RefundRequestCommand`; **publicar en el canal la firma de `RequestRefundUseCase`**. · M: `none` · P: `pending`
- [ ] **T007** · Pruebas de `RequestRefundService`: 4 triggers; sin depósito o alquiler → fallo registrado y sin llamada a la pasarela (RF-010); sin cobro original aprobado → fallo registrado sin solicitar nada; `operationKey` repetida → sin segunda intención; `AUTORIZADO`→`RELEASE`, `CAPTURADO`→`REFUND`. · M: `none` · P: `pending`
- [ ] **T008** · `RequestRefundService` (transacción única: intención + *outbox*; sin atributo de origen; clave idempotente derivada de `reservationId` + `trigger` + `operationKey`). Depende de `UC11·T007` (`OutboxPort`, `FailureRecorderPort`), `UC05·T005` y, para `reservation_information`, del bloque A. · M: `none` · P: `pending`
- [ ] **T009** · Persistencia: entidades JPA, repositorios, mapper MapStruct y adaptadores. · M: `none` · P: `pending`
- [ ] **T010** · Integración (Testcontainers): FK a `charge_intent`, `UNIQUE(idempotency_key)`, trigger anti-mutación en `refund_record`. · M: `none` · P: `pending`
- [ ] **T011** · ACL de la pasarela: `RefundGatewayRequest/Result`, `MercadoPagoRefundRequest/Response`, `MercadoPagoRefundAdapter.send` (`POST /v1/payments/{id}/refunds`, `X-Idempotency-Key`, `amount` explícito). Sin cálculo de descuentos (RF-012). Condicionada a **OQ-UC09-01** para `RELEASE`. · M: `none` · P: `pending`
- [ ] **T012** · Pruebas WireMock del envío: cuerpo y cabeceras del contrato, monto exacto, sin deducciones. · M: `none` · P: `pending`
- [ ] **T013** · *Worker*: `RefundCommandListener` + `SendRefundToGatewayService` con timeout 10 s y *circuit breaker*; timeout/`5xx` → `FALLA_COMUNICACION` y reintento con la misma clave; `4xx` de negocio → sin reintento y en error. Cola/exchange: **OQ-UC05-08**. · M: `none` · P: `pending`
- [ ] **T014** · Pruebas del *worker*: reintentos con la misma clave, `FALLA_COMUNICACION` no es rechazo, `4xx` no se reintenta (RNF-003). · M: `none` · P: `pending`

### Phase 4: US2 — Reembolso del depósito por disputa rechazada (HU2; RF-004, RF-005; CE-001, CE-005)

- [ ] **T015** · Pruebas: `DISPUTA_RECHAZADO` → 100 % del depósito (`30000.00`), nunca parcial; con `disputeId`; el comando no lleva atributo de origen. · M: `none` · P: `pending`
- [ ] **T016** · Implementar `DISPUTA_RECHAZADO` en `RefundCalculator` y `RequestRefundService`, copiando `disputeId` a la intención. Interacción con `deposit_disposition`: **OQ-UC09-03**. · M: `none` · P: `pending`
- [ ] **T017** · **`ce001_reembolso_por_disputa_rechazada_es_el_100_por_ciento_del_deposito`** (CE-001). · M: `none` · P: `pending`
- [ ] **T018** · **`ce005_rechazado_de_disputa_y_ausencia_de_reclamo_se_tratan_igual`** (CE-005). · M: `none` · P: `pending`

### Phase 5: US3 — Registrar el resultado del reembolso (HU3; RF-006A, RF-007…RF-009, RF-011, RF-013; CE-003, CE-004)

- [ ] **T019** · Pruebas de `RegisterRefundResultService`: completado, en proceso, rechazado, cancelado, expirado; repetido no altera el registro; sin intención asociada → `operational_failure`; resultado de disputa tras reembolso ya solicitado → no segunda devolución y se registra la inconsistencia; `transaction_cost` informado y ausente (RF-011). · M: `none` · P: `pending`
- [ ] **T020** · `RegisterRefundResultService`: actualiza siempre la intención; crea `RefundRecord` solo en `COMPLETADO`, atómico, con `refund_intent_id`, monto confirmado, detalle, referencia externa, reserva, propietario, embarcación y disputa. `transaction_cost` se registra solo cuando la pasarela lo informa; si no, `NULL`. · M: `none` · P: `pending`
- [ ] **T021** · Mapeo `type=refund` del webhook compartido (`UC05·T019`/`UC05·T020`) hacia `RegisterRefundResultUseCase`, con pruebas (coordinar con OQ-UC05-07). · M: `none` · P: `pending`
- [ ] **T022** · `MercadoPagoRefundAdapter.fetchRefund` y pruebas WireMock. · M: `none` · P: `pending`
- [ ] **T023** · `ReconcileRefundIntentsService` + registro en `GatewayReconciliationJob` (compartido, `UC05·T023`): reintenta/consulta intenciones sin resultado con la misma clave. · M: `none` · P: `pending`
- [ ] **T024** · **`ce003_trazabilidad_intencion_registro_en_reembolsos`** (CE-003, RF-006A, RF-008, RF-013). · M: `none` · P: `pending`
- [ ] **T025** · **`ce004_fallas_de_comunicacion_sin_reembolso_en_estado_indefinido`** (CE-004). · M: `none` · P: `pending`
- [ ] **T026** · **`ce002_sin_decisiones_de_danos_sin_retenciones_parciales_ni_descuentos`** (CE-002, RF-005, RF-012). · M: `none` · P: `pending`

### Phase 6: Polish & Cross-Cutting Concerns

- [ ] **T027** · ArchUnit: `RefundCalculator` y `domain` sin Spring/JPA; `RefundRecord` sin *setters*; listener/jobs sin repositorios; UC09 no depende de `SettlementIntent`. · M: `none` · P: `pending`
- [ ] **T028** · Alinear documentos: `pasarela-comando-reembolso.md` (liberación, costo transaccional, ejemplo de clave) y general-plan §4 (`dispute_id`, `status_detail`, `scope`). · M: `none` · P: `pending`
- [ ] **T029** · Cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending`
- [ ] **T030** · `./mvnw clean verify` y ejecución de CE-001…CE-005. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
UC11·T001–T009 + UC11·T046 + UC05·T002–T005 ─> T001 ─> T002 ─> T003 ─> T004 ─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010
                                                                                          └─> T011 ─> T012 ─> T013 ─> T014
T015 ─> T016 ─> T017 ─> T018 ─> T019 ─> T020 ─> T021 ─> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027 ─> T028 ─> T029 ─> T030
```

- **Depende de**: `UC05·T002` (migración y función de inmutabilidad; FK a `charge_intent`), `UC05·T005` (`findApprovedByReservationId`), `UC05·T019`/`UC05·T023` (webhook y job compartidos) y de bloque A para `reservation_information`.
- **Bloquea a otros planes**: T006 (firma de `RequestRefundUseCase`) bloquea UC07 (T-cancelaciones) y UC08 (bloque C).
- **Riesgo de secuencia**: T011/T013 dependen de OQ-UC09-01; T016 de OQ-UC09-03.

## Notes

- El SPEC 9 no define metas de rendimiento ni cómo se liberan autorizaciones (OQ-UC09-01).
- UC09 no decide si hay daños ni retiene parcialmente: solo ejecuta el reembolso total del depósito cuando UC08 informa `RECHAZADO`.
- Etiquetas: `[SPEC]`, `[CONV]`, `[PEND]`/`[NEEDS CLARIFICATION]`.
- Los valores numéricos de los ejemplos son ilustrativos.

