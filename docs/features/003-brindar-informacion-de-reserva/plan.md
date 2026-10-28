# Implementation Plan: UC03 - Brindar Información de Reserva

**Date**: 2026-10-09
**Updated**: 2026-10-24 (criterio de integración real con UC02)
**Spec**: [spec.md](spec.md)

## Summary

UC03 es el caso de uso **unidireccional** por el que el Sistema de Reservas y Operaciones notifica al sistema los datos de una reserva que el arrendatario ya ingresó (`reservation_id`, `boat_id`, `start_date`, `end_date`, `passengers`, `owner_id` y `max_capacity`). El sistema **incluye (`<<include>>`) a UC02** pasando `boat_id` y `start_date` para obtener la tarifa vigente de la embarcación en esa fecha, y registra internamente la `InformaciónDeReserva` en la tabla `reservation_information` para que quede disponible para UC04 (valor calculado) y para que los registros financieros posteriores se asocien a su propietario [SPEC RF-001, RF-002, RF-004, HU1]. **No devuelve ninguna respuesta ni desglose** a Reservas (RF-006, CE-002) y **no calcula ningún valor total** (RF-007).

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. El adaptador de entrada es un **listener AMQP** sobre `seashare.reservations` / `reservation.info.provided` / cola `finance.reservation-info.v1` [contrato UC03]; el servicio de aplicación invoca **solo por `port.in`** el puerto de UC02 `ProvideBaseRateUseCase` y persiste con un adaptador JPA (entidad propia, MapStruct, general-plan D-04). Los fallos se registran mediante el catálogo único de puertos `FailureRecorderPort`/`operational_failure`; el registro usa transacción independiente y existe un procedimiento de reproceso. La clasificación acuse/reenvío/DLQ sigue `contracts/README.md` §4.4 y la configuración AMQP común, con DLQ `<cola>.dlq`. El `Message-Id` del encabezado AMQP se usa **solo para trazabilidad**: la idempotencia la da el *upsert* por `reservation_id` sin deduplicación por `message_id` (D-07) [decisión D-UC03-03].

UC03 corresponde a la **fase 5** de la hoja de ruta [general-plan §10]: consume de UC02 (`ProvideBaseRateUseCase`, `BoatId`, `BaseRate`, `BaseRateException`) y del núcleo compartido (`DomainException`, VOs y `FailureRecorderPort`). `reservation_information` forma parte de la **migración inicial única** `V1__initial_schema.sql`; UC03 no define ni numera migraciones propias [general-plan §4.2, D-31].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir los datos específicos de la reserva | `ReservationInfoMessage`, `RegisterReservationInformationCommand`, `ReservationInfoListener` | T007, T008, T011 | T012 |
| **RF-002** incluir (`<<include>>`) UC02 con `boat_id` y `start_date` | `RegisterReservationInformationService` (vía `ProvideBaseRateUseCase`, de UC02) | T017 | T018 |
| **RF-003** no consultar Flota por propietario/capacidad | `RegisterReservationInformationService` (la única llamada a UC02 lleva `boat_id` + `start_date`) + ArchUnit | T017, T024 | T023 (CE-004) |
| **RF-004** registrar internamente la información | `ReservationInformation` (dominio), `ReservationInformationPersistenceAdapter` | T010, T015 | T016, T019 |
| **RF-005** validar fechas y pasajeros vs capacidad | `ReservationInformation` (dominio) + `CHECK` en el esquema de `reservation_information` | T010, T006 | T013, T016 |
| **RF-006** sin respuesta ni desglose a Reservas | `RegisterReservationInformationUseCase` (`void`) + `ReservationInfoListener` (sin reply) | T010, T011 | T022 (CE-002) |
| **RF-007** sin calcular totales (alquiler/seguro/depósito) | `RegisterReservationInformationService` (no usa `PricingCalculator`; no existe `ReservationValueCalculator`) + ArchUnit | T017, T024 | T022 (CE-002) |
| **RF-008** exclusivo de Reservas | Cola dedicada `finance.reservation-info.v1` enlazada al exchange de Reservas + topología compartida `RabbitTopologyConfig`/`AmqpNames` | T011 | T012, T020 |
| **RNF-001** DTO para la solicitud | `ReservationInfoMessage` (infra) + `RegisterReservationInformationCommand` (aplicación) | T007, T008 | T012 |
| **RNF-002** `BigDecimal` tarifa base, 4 decimales internos | `BaseRate` (de UC02), columna `base_rate NUMERIC(18,4)` | T006, T005, T010 | T013, T016, T019 |
| **RNF-003** manejo de errores robusto (registro interno) | `FailureRecorderPort` + política ack/nack/DLQ del listener | T004, T017, T020 | T020, T021 |
| **CE-001** trazabilidad interna (100 % registrado, 0 discrepancias) | Adaptador de persistencia + `ReservationInformationPersistenceAcceptanceTest` | T015, T019 | **T019 `ce001_…`** |
| **CE-002** unidireccional: 0 respuestas/desgloses | ArchUnit (sin controller, sin publicaciones, `void`) | T022, T024 | **T022 `ce002_…`** |
| **CE-003** fallas registradas sin información incompleta | `ReservationInfoListener` + `AmqpListenerFailurePolicy` (permanentes) y recoverer compartido (transitorias) | T017, T020, T021 | **T021 `ce003_…`** + T020 |
| **CE-004** 0 consultas directas a Flota por owner/capacidad | `RegisterReservationInformationService` (validación antes de UC02) + WireMock sobre Flota | T017, T023 | **T023 `ce004_…`** |
| **HU1** registrar internamente la reserva (P1) | Fases 3–5 | T010–T023 | T012–T023 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.2 (parent del `pom.xml`). Para UC03: **Spring AMQP**, Spring Data JPA, MapStruct, ArchUnit, Testcontainers RabbitMQ y `spring-rabbit-test`; estas dependencias ya están declaradas en el `pom.xml` (no se requiere editar dependencias para este caso de uso).
**Storage**: PostgreSQL 16+ — tabla `reservation_information` (de UC03) y tabla técnica compartida `operational_failure`. `operational_failure.reservation_id` es nullable y no tiene FK; `payload_ref` referencia el mensaje o JSON almacenado. El esquema de ambas tablas pertenece a la migración inicial única (general-plan §4.2, D-31) [SPEC general-plan §4].
**Messaging**: RabbitMQ 3.13+ — exchange `seashare.reservations` (topic, durable), routing key `reservation.info.provided`, cola `finance.reservation-info.v1`, DLQ `finance.reservation-info.v1.dlq` **[CONV]** [contrato UC03; README §3.5]
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL **y** RabbitMQ), `spring-rabbit-test`, WireMock (Flota, a través de UC02), Awaitility (reintentos/DLQ), ArchUnit [CONV]
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: Sin meta propia: el SPEC 03 no define metas para un consumidor AMQP; rigen reintentos y DLQ [CONV].
**Constraints**: unidireccional sin canal de respuesta (RF-006, CE-002); `owner_id` y `max_capacity` solo desde la solicitud, nunca de Flota (RF-003, CE-004); no se persiste información incompleta y se conserva la versión válida previa (RNF-003, casos extremos); *upsert* con invalidación de montos calculados (casos extremos; D-UC03-02); `BigDecimal` escala 4 para la tarifa (RNF-002); exclusivo del Sistema de Reservas y Operaciones (RF-008)
**Scale/Scope**: 1 evento AMQP entrante [contrato UC03]; 8 RF + 3 RNF + 4 CE + 1 HU del SPEC 03; 1 puerto de entrada (`RegisterReservationInformationUseCase`) + 1 puerto de salida propio (`ReservationInformationRepository`) + piezas consumidas por nombre (UC02, `FailureRecorderPort`)

**Estado actual del repositorio (relevante para UC03)**: el repositorio ya contiene el núcleo compartido y las implementaciones de UC11, UC02 y UC01, además de la base de UC03. Punto de partida:

- **Base y configuración**: existen `SeashareM3Application.java`, `application.properties` (datasource, `spring.rabbitmq.*` con 1 + 4 = 5 intentos y DLQ, Jackson `SNAKE_CASE`, Flyway), `application-dev.properties`/`application-prod.properties`, `TestcontainersConfiguration` (PostgreSQL 16 y RabbitMQ 3.13), `SeashareM3ApplicationTests` y `TestSeashareM3Application`.
- **UC03 ya implementado**: `domain/model/ReservationInformation.java` (create/withBaseRate/invalidateCalculatedAmounts/withCalculated e invariantes), `domain/exception/DomainException`, `ReservationInfoIncompleteException` y `BaseRateException`; `application/port/in/RegisterReservationInformationUseCase.java` (`void register(...)`), `application/dto/RegisterReservationInformationCommand.java`, `application/port/out/ReservationInformationRepository.java` (`findByReservationId`, `save`, `saveCalculatedAmounts`); `infrastructure/adapter/out/persistence/reservation/` (entidad JPA, repositorio Spring Data y adaptador con upsert + invalidación); tabla `reservation_information` en `V1__initial_schema.sql` con CHECK y FKs; pruebas `ReservationInformationTest`, `ReservationInformationPersistenceAdapterIntegrationTest` y `FlywaySchemaTest`; dobles `InMemoryReservationInformationRepository` y `InMemoryFailureRecorder` en `support/`.
- **Núcleo compartido ya implementado**: `DomainException`, `FailureRecorderPort` + adaptador de `operational_failure`, `ReservationId`, `OwnerId`, `BoatId`, `BaseRate`, `Money`, `ReservationValue`, `ReservationLockPort`; topología AMQP `RabbitTopologyConfig`/`AmqpNames` (exchange `seashare.reservations`, cola `finance.reservation-info.v1`, DLQ `finance.reservation-info.v1.dlq`), `AmqpListenerFailurePolicy` y `OperationalFailureMessageRecoverer`; configuración de reintentos en `application.properties`; pruebas AMQP compartidas.
- **UC02 ya implementado**: `ProvideBaseRateUseCase`, `ProvideBaseRateCommand` (incluye overload de 2 args), `BaseRateResult`, `BaseRate`, `BoatId`, `BaseRateException` (con `PARAMETERS_UNAVAILABLE` transitorio), `ProvideBaseRateService`, adaptador `FleetRateRestClient` y configuración de Flota.
- **Implementado en esta rama**: `RegisterReservationInformationService` (aplicación), `ReservationInfoListener` y `ReservationInfoMessage`/`ReservationInfoMessageMapper` (mensajería), `ReservationInformationValidationException`/`BaseRateUnavailableException`, validación estricta de enteros en el listener, y las pruebas `RegisterReservationInformationServiceTest`, `ReservationInfoListenerContractTest`, `ReservationInfoListenerRetryPolicyTest`, `ReservationInformationAcceptanceTest`, `ReservationInformationPersistenceAcceptanceTest`, `ReservationInformationSourceIntegrityAcceptanceTest`, `ReservationInformationPersistenceAdapterTest` y `Uc03ArchitectureTest`. La firma vigente del puerto es `void register(RegisterReservationInformationCommand command)`.
- **Pendiente de verificación**: T026 (`./mvnw clean verify` con Docker para ejecutar CE-001 y la política de reintentos/DLQ; en este entorno Docker no está disponible y esas pruebas se omiten). T025 queda cubierto por esta actualización del plan.
- **Alcance de la implementación**: la topología y la política de fallos AMQP son compartidas; `ReservationInfoListener` reutiliza `RabbitTopologyConfig`/`AmqpNames`/`AmqpListenerFailurePolicy`/`OperationalFailureMessageRecoverer` (no se crearon `RabbitMqProperties` ni `ReservationInfoRabbitConfig`). El adaptador de persistencia existente mapea manualmente (no hay `ReservationInformationMapper` de MapStruct), su *upsert* es lectura-modificación-escritura transaccional (no `INSERT … ON CONFLICT`) y adquiere el bloqueo asesor por reserva al inicio de la transacción (D-15).

## Project Structure

### Documentation (this feature)

```text
docs/features/003-brindar-informacion-de-reserva/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                        # Leyenda, formatos, catálogo §3.4, tablas §4.4 (colas)
└── events/
    └── UC03-informacion-reserva.md                  # Exchange, routing key, cola, headers, body, reglas
```

Contratos/piezas de otros planes (solo referencia; **no se re-planifican**): `external/flota-consulta-tarifas-base.md` (conexión única de Flota, vía UC02), plan de UC02 (`ProvideBaseRateUseCase`, `BaseRateResult`, `BoatId`, `BaseRate`, `BaseRateException`) y núcleo compartido (`DomainException`, `FailureRecorderPort`, `operational_failure`).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── model/
│   │   └── ReservationInformation.java              # T010 [SPEC "InformaciónDeReserva"; general-plan §11]
│   ├── valueobject/
│   │   ├── ReservationId.java                       # VO del núcleo compartido; UC03 lo consume por nombre
│   │   ├── OwnerId.java                             # VO del núcleo compartido; UC03 lo consume por nombre
│   │   ├── BoatId.java                              # de UC02 (T006) — consumido por nombre
│   │   └── BaseRate.java                            # de UC02 (T006) — consumido por nombre [general-plan §11]
│   └── exception/
│       ├── DomainException.java                     # T003 [núcleo compartido]
│       ├── ReservationInformationValidationException.java  # T009 [SPEC RF-005; nombre [CONV]]
│       └── BaseRateUnavailableException.java        # T009 [SPEC casos extremos (sin tarifa); nombre [CONV]]
├── application/
│   ├── port/in/
│   │   └── RegisterReservationInformationUseCase.java  # T010 [general-plan §3.5] — void, sin respuesta (RF-006)
│   ├── port/out/
│   │   ├── ReservationInformationRepository.java    # T014 [general-plan §3.5; firma publicada §Firmas referenciadas por otros planes]
│   │   └── FailureRecorderPort.java                 # puerto compartido, consumido por nombre
│   ├── service/
│   │   └── RegisterReservationInformationService.java  # T017 [general-plan §3.5]
│   └── dto/
│       └── RegisterReservationInformationCommand.java  # T008 [SPEC RNF-001; nombre [CONV]]
└── infrastructure/
    ├── adapter/in/messaging/
    │   ├── ReservationInfoListener.java             # T011 [contrato UC03; RF-006]
    │   └── dto/
    │       ├── ReservationInfoMessage.java          # T007 [SPEC RNF-001; claves snake_case del contrato]
    │       └── ReservationInfoMessageMapper.java    # T007 [CONV: mensaje → command]
    ├── adapter/out/persistence/
    │   ├── ReservationInformationJpaEntity.java     # T015 [general-plan D-04; mapeo manual]
    │   ├── ReservationInformationJpaRepository.java # T015 [CONV]
    │   └── ReservationInformationPersistenceAdapter.java  # T015 [upsert con invalidación; lock asesor D-15]
    └── config/
        ├── RegisterReservationInformationConfiguration.java  # T017 [wiring del servicio]
        └── amqp/                                    # topología/política compartida: RabbitTopologyConfig, AmqpNames, AmqpListenerFailurePolicy, OperationalFailureMessageRecoverer

src/main/resources/
└── db/migration/
    └── V1__initial_schema.sql                        # T006 [migración inicial única; contiene reservation_information (general-plan §4.2, D-31)]

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── Uc03ArchitectureTest.java                    # T022, T024 (CE-002/RF-003/RF-007)
├── domain/model/
│   └── ReservationInformationTest.java              # T013
├── application/service/
│   ├── RegisterReservationInformationServiceTest.java  # T018
│   ├── ReservationInformationAcceptanceTest.java    # T021 (CE-003)
│   └── ReservationInformationSourceIntegrityAcceptanceTest.java  # T023 (CE-004, WireMock)
├── infrastructure/adapter/in/messaging/
│   ├── ReservationInfoListenerContractTest.java     # T012 (mensaje vs contrato UC03)
│   └── ReservationInfoListenerRetryPolicyTest.java  # T020 (ack/nack/DLQ, Testcontainers RabbitMQ)
├── infrastructure/adapter/out/persistence/
│   ├── ReservationInformationPersistenceAdapterIntegrationTest.java  # T016 (Testcontainers PostgreSQL)
│   └── ReservationInformationPersistenceAdapterTest.java  # bloqueo asesor D-15 (unitario)
├── integration/
│   └── ReservationInformationPersistenceAcceptanceTest.java  # T019 (CE-001, Testcontainers PostgreSQL)
└── support/
    ├── InMemoryFailureRecorder.java                 # de UC02·T004/T016 — reutilizado por nombre [CONV]
    └── MessagingJsonMapper.java                     # mapper de pruebas alineado con JacksonConfig
```

`ReservationInformation` (dominio) y `ReservationInformationRepository` (puerto de salida) son **de UC03** (`general-plan.md` §4 asigna la tabla a «Entidad (UC03/UC04)»: UC03 crea `reservation_information`, UC04 la completa). La tabla y el puerto se usan después por otros planes **por nombre**; UC03 publica la firma en «Firmas referenciadas por otros planes». `ProvideBaseRateUseCase`/`BoatId`/`BaseRate`/`BaseRateException` son de **UC02**; `FailureRecorderPort`/`operational_failure` son **compartidos** y UC03 no los implementa ni redeclara.

**Firmas referenciadas por otros planes** [general-plan §3.5]: UC03 **no expone su puerto de entrada a otros planes** (`RegisterReservationInformationUseCase` lo consume únicamente su adaptador `messaging`). Como dueño de `reservation_information`, publica aquí la firma de su puerto de **salida** `ReservationInformationRepository`, que los planes de UC05/UC07/UC09/UC08/UC10 referencian por nombre y que UC04 usará para completar montos, la referencia de versión `frozen_parameters_id` y `calculated_at` (general-plan D-27). Firma [CONV]:

```java
public interface ReservationInformationRepository {
    Optional<ReservationInformation> findByReservationId(ReservationId reservationId);  // lectura (UC11/UC12/UC13; UC04)
    ReservationInformation save(ReservationInformation information);  // upsert por reservation_id; el adaptador
                                                                      // deja las columnas calculadas y
                                                                      // frozen_parameters_id en NULL (invalidación),
                                                                      // reescribe base_rate_parameters_id y persiste
                                                                      // en una transacción única
    // --- UC04: completa montos + frozen_parameters_id + calculated_at ---
    //   NO anula el bloque informativo; transacción única con pg_advisory_xact_lock(reservationId);
    //   debe ejecutarse sobre la fila ya existente (si no existe, el servicio no la crea: responde 404).
    ReservationInformation saveCalculatedAmounts(ReservationInformation information);
}
```

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) en un solo módulo Maven, con subpaquetes temáticos sin reglas entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas del §3.4 aplicadas aquí: `domain` sin Spring/JPA/Jackson/AMQP; `application` solo depende de `domain` (se permite `@Transactional` pragmático); `infrastructure.adapter.in` solo invoca `application.port.in`; `infrastructure.adapter.out` solo implementa `application.port.out`; el listener nunca accede a un repositorio; la entidad JPA no sale de `adapter.out.persistence`; los DTOs entrantes viven en `infrastructure.adapter.in` y la aplicación trabaja con `command` (RNF-001). Identificadores en inglés (D-23) y lenguaje ubicuo §11 (*InformaciónDeReserva → `ReservationInformation`*; *SolicitudInformacionReserva* no está en §11 → `ReservationInfoMessage` **[CONV, D-UC03-09]**).

## Reglas de negocio

Todas las reglas provienen del SPEC 03 (`spec.md`) y su contrato, salvo indicación. Ejemplo base del contrato: `start_date = 2026-12-20`, `end_date = 2026-12-22`, `passengers = 4`, `max_capacity = 6`, `owner_id = a1b2c3d4-…`.

1. **Datos recibidos** [SPEC RF-001, RNF-001]: el mensaje AMQP trae los 7 campos obligatorios: `reservation_id`, `boat_id`, `start_date`, `end_date`, `passengers`, `owner_id`, `max_capacity`. Ausencia, `null`, tipo inválido (UUID, fecha `YYYY-MM-DD`, entero) o formato inválido ⇒ mensaje inválido (equivalente a E2). Los enteros se validan como enteros JSON (no se aceptan strings ni flotantes) antes de mapear el DTO. `owner_id` y `max_capacity` ya fueron obtenidos por Reservas desde Flota; el sistema los usa tal cual [SPEC RF-001, RF-003].
2. **Naturaleza unidireccional** [SPEC RF-006, CE-002]: el sistema **no devuelve nada** a Reservas: el listener no responde y el caso de uso no publica mensajes de salida. La confirmación es el `ack` del mensaje tras persistir.
3. **Inclusión de UC02** [SPEC RF-002]: el servicio invoca `ProvideBaseRateUseCase.provideBaseRates(new ProvideBaseRateCommand(List.of(boatId), startDate))` — **una sola consulta** a Flota con una lista de un elemento, con `evaluatedDate = start_date` recibida. La tarifa final (ya dinámica, salida de UC02 a escala 4) se usa para toda la duración de la reserva. Ejemplo: mensaje del contrato → UC02 evalúa `2026-12-20` y devuelve la tarifa final por unidad de tiempo (p. ej. `437500.0000`, ilustrativo).
4. **Fuentes de propietario y capacidad** [SPEC RF-003, CE-004]: UC03 **jamás** consulta a Flota por `owner_id` ni `max_capacity`; la única llamada externa es la inclusión de UC02 con `boat_id` + `start_date`. Si faltan `owner_id` o `max_capacity` en el mensaje, el dato es incompleto y el registro no se persiste [casos extremos].
5. **Validaciones de dominio** [SPEC RF-005, CE-001]: `end_date >= start_date` y `1 <= passengers <= max_capacity`. Violación ⇒ `ReservationInformationValidationException` (permanente): `ack` + registro interno, **sin persistir** y conservando la versión válida previa. Los mismos invariantes se replican como `CHECK` en el esquema de `reservation_information` (general-plan §4).
6. **`base_rate` con precisión** [SPEC RNF-002]: la tarifa se almacena como `BigDecimal` a escala 4 (salida de UC02 **sin redondeo**) en la columna `base_rate NUMERIC(18,4)`. UC03 no redondea y no calcula montos de alquiler, seguro ni depósito [SPEC RF-007].
7. ***Upsert*** [SPEC casos extremos; contrato regla 4; CE-001]: si ya existe una `InformaciónDeReserva` para el `reservation_id`, se **sobrescribe** el bloque informativo completo (`boat_id`, `base_rate`, `base_rate_parameters_id`, `start_date`, `end_date`, `passengers`, `owner_id`, `max_capacity`) con la versión más reciente, **re-incluyendo UC02** para volver a consultar la tarifa vigente de la fecha de inicio (su `parametersId` se guarda como `base_rate_parameters_id`; `NULL` si la fecha era regular).
8. **Invalidación de montos al sobrescribir** [SPEC casos extremos; D-UC03-02]: todo *upsert* deja en `NULL`, **en conjunto**, `rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`, `frozen_parameters_id` y `calculated_at` (general-plan §4: montos y referencia de versión se escriben juntos). `base_rate_parameters_id` **no** se anula: se reescribe con el `parametersId` de la nueva consulta a UC02. UC04 **debe recalcular** antes de que pueda procesarse un nuevo cobro (general-plan D-17). La carrera entre el *upsert* y el cálculo de UC04 se serializa con el bloqueo asesor por reserva (`pg_advisory_xact_lock`, general-plan D-15).
9. **Reentregas y duplicados** [D-UC03-03; general-plan D-07]: no hay tabla *inbox* ni deduplicación por `Message-Id`; el `Message-Id` solo se usa para trazabilidad (log y `payload_ref`). Una reentrega (entrega al-menos-una-vez) de un mensaje idéntico produce el mismo estado informativo y **re-invalida** los montos; como UC04 recalcula de forma determinista (D-17), el impacto es que UC04 debe volver a ejecutarse antes de un nuevo cobro.
10. **No persistir información incompleta** [casos extremos, RNF-003, CE-003]: si la validación falla, falta `owner_id`/`max_capacity` o la inclusión de UC02 no puede completarse, **no se escribe nada**; como el *upsert* ocurre en una única transacción al final del flujo, la versión válida previa se conserva automáticamente.
11. **Embarcación sin tarifa base** [casos extremos; D-UC03-04]: si UC02 devuelve un resultado **vacío** (embarcación sin tarifa o no reconocida; el diseño batch de UC02 nunca lanza `BASE_RATE_NOT_AVAILABLE`, omite el resultado), UC03 lanza `BaseRateUnavailableException` (permanente): `ack` + registro interno en `operational_failure`, sin reintento y sin persistir. UC02 ya registró su propio fallo `BASE_RATE_NOT_AVAILABLE`; UC03 registra el suyo.
12. **Fallas transitorias** [RNF-003; D-UC03-05; README §4.4]: `BaseRateException(FLEET_UNAVAILABLE)`, `BaseRateException(DYNAMIC_RATE_NOT_CONFIGURED)` y `BaseRateException(PARAMETERS_UNAVAILABLE)` (de UC02, transitorias) y fallas de persistencia (BD) ⇒ `nack` con **5 reintentos y *backoff* exponencial (base 1 s)** → **DLQ** `finance.reservation-info.v1.dlq`; se registra en `operational_failure`. Jamás se persiste una tarifa asumida ni información a medias [principio rector 4].
13. **Clasificación del listener** [CONV; README §4.4]: resumen de la política de acuse (detalle en §Contratos):
    | Situación | Equivalencia | Acción |
    |---|---|---|
    | Mensaje mal formado, campos ausentes/inválidos, validación RF-005 fallida, sin tarifa base | E2 / E6 | `ack` + registro en `operational_failure` (no se reintenta) |
    | `FLEET_UNAVAILABLE`, `DYNAMIC_RATE_NOT_CONFIGURED`, `PARAMETERS_UNAVAILABLE`, fallo de BD | E5 / E8 | `nack` + reintentos (5, *backoff* exponencial base 1 s) → DLQ + registro |
14. **Seguridad fuera de alcance** [general-plan §7.1, D-21]: el sistema **no implementa** autenticación ni autorización, tampoco para el canal AMQP. La cola dedicada `finance.reservation-info.v1` identifica al usuario esperado (el Sistema de Reservas y Operaciones); la operación del broker es infraestructura, no control de acceso del proyecto. No se implementa `SecurityConfig` ni pruebas de seguridad.
15. **Orden de procesamiento** [CONV, README §4.3]: el mensaje se valida por completo (reglas 1 y 5) **antes** de invocar a UC02, y UC02 se invoca antes de persistir: una llamada externa nunca se gasta en un mensaje ya inválido.

## Esquema (migración única)

`reservation_information` forma parte de la **migración inicial única** `V1__initial_schema.sql` (general-plan §4.2 y D-31): UC03 **no** define, posee ni numera migraciones. Requisitos de esquema que la tabla debe cumplir (fuente: general-plan §4):

- `reservation_id` `UUID` PK (el recibido, no se genera); `boat_id`, `owner_id`, `start_date` (`DATE`), `end_date` (`DATE`), `passengers` (`INTEGER`), `max_capacity` (`INTEGER`).
- `base_rate` `NUMERIC(18,4)` `NOT NULL` (escala 4, sin redondeo) y `base_rate_parameters_id` `UUID` (FK a `financial_parameters(id)`; versión usada por la tarifa dinámica de UC02; `NULL` si la fecha era regular) [D-24/D-27].
- Columnas calculadas por UC04 (`NULL` hasta entonces y anuladas por el *upsert* de UC03, D-UC03-02): `rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount` (`NUMERIC(18,4)`), `frozen_parameters_id` (FK a `financial_parameters(id)`, D-27) y `calculated_at` (`TIMESTAMPTZ`).
- `CHECK (end_date >= start_date)` y `CHECK (passengers BETWEEN 1 AND max_capacity)` [SPEC RF-005]; FKs a versiones de `financial_parameters` (tabla *append-only*: la fila referenciada nunca se modifica) [D-24/D-27].

Notas: la PK es el `reservation_id` recibido (no se genera UUID). Las dos FK apuntan a **versiones** de `financial_parameters` (tabla append-only: la fila referenciada nunca se modifica). Los valores aplicados (comisión % y seguro por pasajero) **no se copian** en la reserva: UC03 devuelve `frozen_parameters_id` y UC04/UC10 resuelven los valores con `FinancialParametersRepository.findById` al reconstruir el agregado (**sin join**; D-27, T015). UC11 **nunca** modifica estas columnas y un cambio posterior de parámetros globales no afecta a la fila (D-27); por el FK, `financial_parameters` no puede purgarse mientras existan reservas que la referencien. No hay columnas ni índices inventados: coinciden con `general-plan` §4.

## Contratos

Fuente: `contracts/events/UC03-informacion-reserva.md` y las convenciones de `contracts/README.md` §3.

### Evento AMQP: `reservation.info.provided` (Reservas → sistema)

| Propiedad | Valor |
|---|---|
| Exchange | `seashare.reservations` (topic, durable) |
| Routing Key | `reservation.info.provided` |
| Cola consumidora | `finance.reservation-info.v1` |
| DLQ (reintentos agotados) | `finance.reservation-info.v1.dlq` **[CONV]** |
| Headers | `Content-Type: application/json` (obligatorio), `Message-Id` (obligatorio, uso de trazabilidad), `App-Id: reservations-service` (no obligatorio) |
| `delivery_mode` | `2` (persistente) |
| Respuesta al productor | **Ninguna** (RF-006) |

Body (todos los campos obligatorios, `snake_case`) [SPEC RF-001]:

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "boat_id": "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01",
  "start_date": "2026-12-20",
  "end_date": "2026-12-22",
  "passengers": 4,
  "owner_id": "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d",
  "max_capacity": 6
}
```

### Respuesta exitosa

No aplica (unidireccional). El mensaje se confirma (`ack`) **tras persistir** en una única transacción [contrato UC03 §4].

### Respuestas de error / política de acuse

| Situación | Clasificación | Acción AMQP | Registro interno |
|---|---|---|---|
| Cuerpo no JSON, headers ausentes, campos ausentes/`null`, tipos/formato inválidos | E2 | `ack` (no reintentar) | `operational_failure` |
| `end_date < start_date` o `passengers` fuera de `1..max_capacity` | E2 (RF-005) | `ack` | `operational_failure` |
| Falta `owner_id` o `max_capacity` (dato incompleto) | E6 (dato del recurso recibido) | `ack` | `operational_failure` |
| Sin tarifa base (UC02 devuelve vacío) | E6 | `ack` | `operational_failure` |
| `FLEET_UNAVAILABLE` / `DYNAMIC_RATE_NOT_CONFIGURED` / `PARAMETERS_UNAVAILABLE` (UC02) | E5 / E4 | `nack` + reintentos (5, *backoff* exponencial base 1 s) → DLQ | `operational_failure` al agotar |
| Fallo de persistencia (BD) | E8 | `nack` + reintentos → DLQ | `operational_failure` al agotar |
| Mensaje repetido (misma `reservation_id`, mismo contenido) | Idempotente | `ack`; *upsert* del mismo estado (re-invalida montos, D-UC03-03) | — |

*Nota*: los errores de negocio se confirman (`ack`) para no reprocesar en vano [contrato UC03 §5]; las fallas transitorias se reintentan conforme al general-plan §7.2.

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), contrato UC03 y SPEC 03. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %. Las pruebas de fallos usan un doble `InMemoryFailureRecorder`, sin redefinir ni depender de tareas externas.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | invariantes RF-005 (fechas, pasajeros vs capacidad, requeridos y límites válidos), tarifa escala 4 | `domain/model/ReservationInformationTest` |
| Unitario (`application`) | orquestación: incluye UC02 una sola vez, upsert re-consulta tarifa, sin tarifa (permanente), FLEET/DYNAMIC/PARAMETERS (transitorio), validación, sin cálculos (RF-007) | `RegisterReservationInformationServiceTest` (Mockito sobre `ProvideBaseRateUseCase` y `ReservationInformationRepository`) |
| Unitario (persistencia) | bloqueo asesor por reserva (D-15) en `save`/`saveCalculatedAmounts` | `ReservationInformationPersistenceAdapterTest` |
| Contrato (messaging) | cuerpo y headers del contrato UC03: mensaje válido, campos ausentes/`null`, tipos/formato inválidos (UUID, fechas, enteros estrictos) | `ReservationInfoListenerContractTest` |
| Integración (Testcontainers) | esquema de `reservation_information` (migración inicial única) + *upsert* + invalidación + reescritura de `base_rate_parameters_id` + CHECK + versión previa conservada | `ReservationInformationPersistenceAdapterIntegrationTest` |
| Política del listener (Testcontainers RabbitMQ) | `ack`/`nack`/reintentos/DLQ según la tabla de decisión; registro en `operational_failure`; propagación de fallos de BD | `ReservationInfoListenerRetryPolicyTest` |
| Aceptación | CE-001 en `ReservationInformationPersistenceAcceptanceTest`; CE-003 en `ReservationInformationAcceptanceTest`; CE-004 en `ReservationInformationSourceIntegrityAcceptanceTest` | las tres clases citadas |
| Arquitectura | reglas §3.4; RF-003/RF-006/RF-007 (sin controller, sin publicaciones, sin cálculos, sin consultas directas a Flota por owner/capacidad) | `arch/Uc03ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-004)** — nóminal `ce00X_<descripcion>`:

- **`ce001_reserva_notificada_registrada_completa_y_upsert`** (CE-001, T019): integración (Testcontainers PostgreSQL) en `ReservationInformationPersistenceAcceptanceTest`: la reserva del ejemplo del contrato queda registrada con los 7 campos + `base_rate` exactos; un segundo mensaje con la misma `reservation_id` sobrescribe e **invalida** los montos (NULL); cero discrepancias.
- **`ce002_unidireccional_sin_respuesta_ni_desglose`** (CE-002, T022): ArchUnit — no existe `@RestController`/adaptador web para UC03; el listener y el servicio no publican (sin `RabbitTemplate`, `OutboxPort` ni `MessageBrokerPort`) y `RegisterReservationInformationUseCase` devuelve `void`; ninguna clase de UC03 depende de `PricingCalculator` (no existe `ReservationValueCalculator`) (RF-007).
- **`ce003_fallas_registradas_sin_informacion_incompleta`** (CE-003, T021): con dobles (`InMemoryFailureRecorder` + stub de `ProvideBaseRateUseCase`): fallos permanentes (sin tarifa, `owner_id`/`max_capacity` ausentes, validación) → registro interno, **no se persiste información incompleta** y la versión válida previa se conserva; fallos transitorios (FLEET/DYNAMIC/PARAMETERS) → se propagan y los registra el recoverer compartido en T020 al agotar reintentos.
- **`ce004_cero_consultas_a_flota_por_propietario_y_capacidad`** (CE-004, T023): integración con WireMock sobre Flota (a través del adaptador de UC02) en `ReservationInformationSourceIntegrityAcceptanceTest`: (a) el **único** request a Flota contiene únicamente `boat_ids` (sin `owner_id` ni `max_capacity`); (b) si el mensaje llega sin `owner_id` o sin `max_capacity`, la validación falla **antes** de invocar a UC02 y no se realiza ninguna llamada a Flota (cero requests). Usa el `ProvideBaseRateService` **real**, no un mock de `ProvideBaseRateUseCase`.

**Integración real con UC02 (criterio obligatorio):** CE-004 (T023) debe construir el `ProvideBaseRateService` **real** de UC02 (con `FleetRateRestClient` sobre WireMock y dobles en memoria de `FinancialParametersRepository`/`FailureRecorderPort`), no un mock de `ProvideBaseRateUseCase`. Así se detecta cualquier regresión de firma o de comportamiento del `<<include>>` antes de cerrar UC03; las reglas internas de UC02 se siguen probando en su propio plan.

Además: la regresión de consumidores se cubre con el criterio anterior; UC04 completará los montos, `frozen_parameters_id` y `calculated_at`; los demás planes leen la tabla por nombre; aquí solo se publica la firma del repositorio.

## Decisiones de diseño (D-UC03-xx)

Registro de las decisiones de diseño de este plan, con su contexto y estado. Los IDs `D-UC03-xx` son propios de este plan; los `D-xx` sin prefijo UC03 son decisiones del plan general (§9).

| ID | Descripción | Contexto | Decisión | Estado |
|---|---|---|---|---|
| **D-UC03-01** | Esquema de `reservation_information` en la migración única | `general-plan.md` §4 (tabla `reservation_information`) y §4.2/D-31 (política de migraciones) | UC03 no define ni posee migraciones: `reservation_information` se crea en la **migración inicial única** `V1__initial_schema.sql` (general-plan §4.2, D-31) | Aplicada |
| **D-UC03-02** | Columnas que invalida el *upsert* de una reserva ya calculada | SPEC 03 (casos extremos); general-plan §4 y D-17 | La invalidación anula en conjunto `rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`, `frozen_parameters_id` y `calculated_at`; `base_rate_parameters_id` se reescribe con el `parametersId` de la nueva consulta a UC02 | Decidida |
| **D-UC03-03** | Idempotencia ante la entrega al-menos-una-vez del broker | SPEC 03; general-plan D-07 | *Upsert* puro sin tabla *inbox* ni dedup por `Message-Id`; una reentrega re-invalida montos y UC04 recalcula determinista (D-17); la carrera con UC04 se serializa con el bloqueo asesor por reserva (`pg_advisory_xact_lock`, D-15) | Decidida |
| **D-UC03-04** | Clasificación del fallo cuando UC02 no puede completarse | SPEC 03 (casos extremos); README §4.4 | Embarcación sin tarifa (UC02 devuelve vacío) = **permanente** → `ack` + registro; FLEET/DYNAMIC/PARAMETERS = **transitorio** → reintentos/DLQ | Decidida |
| **D-UC03-05** | Política de acuse del listener | SPEC 03; README §4.4 y general-plan §7.2 | `acknowledge-mode=AUTO` de la política compartida: el listener registra y retorna (ack) en errores permanentes; `nack` con 5 reintentos y *backoff* exponencial (base 1 s) solo en fallas transitorias → DLQ + registro | Decidida |
| **D-UC03-06** | Aislamiento del registro de fallos respecto de la transacción de negocio | `general-plan.md` §3.5/§4 | El listener/política compartida consume `FailureRecorderPort` por nombre; el servicio no registra fallos. El puerto registra con `REQUIRES_NEW`, `reservation_id` nullable y `payload_ref` referencial; se documenta un procedimiento de reproceso | Aplicada |
| **D-UC03-07** | Artefactos Maven para AMQP y sus pruebas | `pom.xml` actual | Se documentan en T001 sin coordinar mediante tareas externas ni editar en silencio | Aplicada |
| **D-UC03-08** | Identificación del productor en el canal AMQP (RF-008 fija quién publica: solo Reservas) | SPEC 03 RF-008; general-plan §7.1 (seguridad fuera de alcance) y D-21 | Sin autenticación: el sistema no implementa seguridad; la cola dedicada identifica al usuario esperado. No se implementa `SecurityConfig` | Aplicada |
| **D-UC03-09** | Nombres de los DTOs del mensaje respecto del lenguaje ubicuo | SPEC 03; general-plan §11 | `ReservationInfoMessage` (infraestructura) → `RegisterReservationInformationCommand` (aplicación); `InformaciónDeReserva` → `ReservationInformation` (ya en §11) | Decidida |
| **D-UC03-10** | `ReservationId`/`OwnerId` son VOs compartidos del núcleo | `general-plan.md` §3.3 | UC03 los consume por nombre y no los redeclara; el lenguaje ubicuo vigente prevalece | Aplicada |
| **D-UC03-11** | `ProvideBaseRateUseCase` es *batch-only* (una consulta por invocación) y nunca lanza `BASE_RATE_NOT_AVAILABLE` | SPEC 03 RF-002; plan de UC02 | UC03 invoca con lista de un elemento y correlaciona por `boat_id`; resultado vacío ⇒ `BaseRateUnavailableException` (regla 11) | Decidida |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable de forma independiente; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias con otros casos de uso se expresan mediante puertos, firmas y tablas, no mediante numeración de tareas externas.

### Phase 1: Setup

- [x] **T001** · `pom.xml`: registrar Spring AMQP, pruebas RabbitMQ y Testcontainers RabbitMQ, sin editar dependencias en silencio.
- [x] **T002** · `application.properties` (datasource + `spring.rabbitmq.*`) con la configuración AMQP común (reintentos 1 + 4 = 5 y DLQ `<cola>.dlq`) y base de `ArchitectureTest`.

### Phase 2: Foundational

- [x] **T003** · `DomainException` (`domain/exception`), base del árbol de excepciones del núcleo común.
- [x] **T004** · consumir `FailureRecorderPort` y `operational_failure` por sus firmas compartidas; registro `REQUIRES_NEW`, `reservation_id` nullable, sin FK, `payload_ref` referencial y procedimiento de reproceso.
- [x] **T005** · consumir por nombre las piezas de UC02: `ProvideBaseRateUseCase`, `ProvideBaseRateCommand`, `BaseRateResult`, `BoatId`, `BaseRate` y `BaseRateException`; UC03 no las implementa ni las redeclara.
- [x] **T006** · verificar el esquema de `reservation_information` contra la migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31); UC03 no define migraciones propias; si la implementación detecta desviaciones ⇒ nueva versión `V2+`.
- [x] **T007** · `ReservationInfoMessage` (`infrastructure/adapter/in/messaging/dto`, claves `snake_case` del contrato) + `ReservationInfoMessageMapper` (mensaje → `RegisterReservationInformationCommand`) [RNF-001; D-UC03-09].
- [x] **T008** · `RegisterReservationInformationCommand` (los 7 campos obligatorios) usando los VOs compartidos `ReservationId`/`OwnerId` por nombre [general-plan §3.3; D-UC03-10].
- [x] **T009** · Excepciones de dominio: `ReservationInformationValidationException` (permanente) y `BaseRateUnavailableException` (permanente), ambas sobre `DomainException` [CONV].

### Phase 3: US1 — Recepción AMQP y validaciones (HU1; RF-001, RF-005, RF-008, RNF-001)

- [x] **T010** · `RegisterReservationInformationUseCase` (`void`, sin respuesta — RF-006) + dominio `ReservationInformation` (inmutable): invariantes RF-005 (`end_date >= start_date`; `1 <= passengers <= max_capacity`), campos requeridos y `baseRate` a escala 4 [RNF-002].
- [x] **T011** · `ReservationInfoListener` sobre la topología/política compartida (`RabbitTopologyConfig`, `AmqpNames`, `AmqpListenerFailurePolicy`, `OperationalFailureMessageRecoverer`): exchange `seashare.reservations` (topic, durable), binding `reservation.info.provided`, cola `finance.reservation-info.v1`, DLQ `finance.reservation-info.v1.dlq` **[CONV]**, `acknowledge-mode=AUTO` (el contenedor confirma al retornar tras persistir/registrar), reintentos transitorios: 5 con *backoff* exponencial (base 1 s) **[CONV]**; `Message-Id` a logs y `payload_ref`.
- [x] **T012** · Tests de contrato del mensaje AMQP (`ReservationInfoListenerContractTest`): parseo del body del contrato; campos ausentes/`null`; tipos/formato inválidos (UUID, fechas, enteros) → mensaje inválido (ack + registro).
- [x] **T013** · Pruebas unitarias de dominio `ReservationInformation`: fechas (fin == inicio válido; fin < inicio inválido), pasajeros (1, `max_capacity`, `max_capacity + 1`), campos requeridos, tarifa escala 4 [RF-005, RNF-002].

### Phase 4: US1 — Registro con inclusión de UC02 e invalidación (HU1; RF-002, RF-003, RF-004, RNF-002, CE-001)

- [x] **T014** · `ReservationInformationRepository` (`application/port/out`) con la firma publicada en «Firmas referenciadas por otros planes»: `findByReservationId` + `save` (upsert). [general-plan §3.5]
- [x] **T015** · `ReservationInformationJpaEntity` + `ReservationInformationJpaRepository` (sobre `reservation_information`, PK `reservation_id`) + `ReservationInformationPersistenceAdapter`: *upsert* de lectura-modificación-escritura en una **transacción única** que sobrescribe el bloque informativo (incluido `base_rate_parameters_id`) y **deja las columnas calculadas y `frozen_parameters_id` en NULL** (invalidación, **D-UC03-02**); adquiere el bloqueo asesor por reserva (D-15). Las lecturas (`findByReservationId`) mapean las columnas de la fila, incluida `frozen_parameters_id` como `UUID`; **no** hacen join a `financial_parameters` [CONV núcleo]: la resolución de los valores de la versión referenciada es responsabilidad del UC que los necesita, vía `FinancialParametersRepository.findById(...)` (D-27).
- [x] **T016** · Pruebas de integración del adaptador (Testcontainers PostgreSQL): persistencia de los 7 campos + `base_rate`; sobrescritura; reescritura de `base_rate_parameters_id`; invalidación de montos + `frozen_parameters_id` + `calculated_at`; lectura de `frozenParametersId` (sin join); FK hacia versiones de `financial_parameters`; CHECK de fechas y pasajeros (constraint de BD); versión válida previa conservada ante fallo de persistencia.
- [x] **T017** · `RegisterReservationInformationService`: valida (reglas 1 y 5) → incluye UC02 con `List.of(boatId)` y `evaluatedDate = start_date` (RF-002, regla 3) → *upsert*; el servicio no registra fallos (los registra el listener/política); correlaciona por `boat_id`; resultado vacío → `BaseRateUnavailableException` (regla 11); `BaseRateException` FLEET/DYNAMIC/PARAMETERS → transitorio (regla 12); **sin cálculos de valor** (RF-007).
- [x] **T018** · Pruebas unitarias del servicio (Mockito): flujo feliz; upsert re-consulta la tarifa; sin tarifa (permanente: excepción, sin persistir); FLEET/DYNAMIC/PARAMETERS (transitorio); validación fallida; una sola invocación a `ProvideBaseRateUseCase`.
- [x] **T019** · **`ce001_reserva_notificada_registrada_completa_y_upsert`** (CE-001; prueba escrita, requiere Docker): test de aceptación de integración (Testcontainers PostgreSQL) en `ReservationInformationPersistenceAcceptanceTest`: la reserva del contrato queda registrada completa con `base_rate` exacta (escala 4) y el reenvío sobrescribe e invalida; cero discrepancias.

### Phase 5: US1 — Unidireccionalidad y manejo de fallos (HU1; RF-006, RF-003, RNF-003; CE-002, CE-003, CE-004)

- [x] **T020** · Implementar la política de acuse del listener (tabla de decisión de §Contratos) y `ReservationInfoListenerRetryPolicyTest` (Testcontainers RabbitMQ; prueba escrita, requiere Docker): mensaje inválido/sin tarifa → `ack` + registro; `FLEET_UNAVAILABLE`/`DYNAMIC_RATE_NOT_CONFIGURED`/`PARAMETERS_UNAVAILABLE` → `nack` + reintentos → DLQ `finance.reservation-info.v1.dlq`; fallo de BD → propagación (cubierta por `ReservationInfoListenerContractTest` y la política compartida).
- [x] **T021** · **`ce003_fallas_registradas_sin_informacion_incompleta`** (CE-003): con dobles — fallos permanentes (sin tarifa, `owner_id`/`max_capacity` ausentes, validación) → registro interno y **cero información incompleta persistida**; fallos transitorios (FLEET/DYNAMIC/PARAMETERS) → propagación y registro en T020 al agotar reintentos; versión válida previa conservada.
- [x] **T022** · **`ce002_unidireccional_sin_respuesta_ni_desglose`** (CE-002): ArchUnit — sin `@RestController` ni adaptador web/scheduler; el listener y el servicio no publican (sin `RabbitTemplate`, `OutboxPort` ni `MessageBrokerPort`); `RegisterReservationInformationUseCase` devuelve `void`; ninguna clase de UC03 usa `PricingCalculator` (no existe `ReservationValueCalculator`) (RF-007).
- [x] **T023** · **`ce004_cero_consultas_a_flota_por_propietario_y_capacidad`** (CE-004): integración con WireMock sobre Flota (a través del adaptador de UC02): el request a Flota contiene solo `boat_ids` (sin `owner_id`/`max_capacity`); si falta `owner_id`/`max_capacity`, cero llamadas a Flota (validación previa, regla 15). Usa `ProvideBaseRateService` real, no un mock del puerto. Prueba: `ReservationInformationSourceIntegrityAcceptanceTest`.
- [x] **T024** · ArchUnit (reglas §3.4 y específicas de UC03) en `Uc03ArchitectureTest`: `domain` sin Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; `infrastructure.adapter.in.messaging` solo invoca `application.port.in`; entidad JPA no sale de `adapter.out.persistence`; sin `PricingCalculator` (no existe `ReservationValueCalculator`) (CE-002).

### Phase 6: Polish — **parcialmente compartido con la Fase general**

- [x] **T025** · Documentación y referencias: publicar la firma de `ReservationInformationRepository`, documentar el esquema de `reservation_information` frente a la migración inicial única (general-plan §4.2, D-31) y dejar constancia de la topología AMQP vigente (cola `finance.reservation-info.v1`, DLQ `finance.reservation-info.v1.dlq`; reproceso manual fuera del alcance). No se edita ningún contrato ni SPEC sin acuerdo.
- [ ] **T026** · revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-004.

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─┬─> T003 ─> T004 ─┐
      │         │                  │
      │         └──────────────────┴─> T005 ─> T006 ─> T007 ─> T008 ─> T009
      │                                      │
      ├────────────────────────────> T010 ─> T011 ──> T012
      │                                      │
      │                                     T013 <────┘
      │
      ├─────────────────────────────────────────────> T014 ─> T015 ─> T016
      │                                                      │
      │                                               T017 ─> T018 ─> T019
      │
      ├─────────────────────────────────────────────────────────> T020 ─> T021 ─> T022 ─> T023 ─> T024
      │
      └─────────────────────────────────────────────────────────> T025 ─> T026
```

- **Grupo 1 (T001–T009)**: Setup y Foundational de UC03, con consumo del catálogo compartido de puertos y VOs.
- **US1 (T010–T019)**: dominio y listener (T010–T013) → repositorio y adaptador (T014–T016) → servicio y aceptación CE-001 (T017–T019). El adaptador de persistencia y el servicio dependen del esquema de `reservation_information` (migración inicial única; T006).
- **Fallos y unidireccionalidad (T020–T024)**: después del flujo feliz; T020 (política de acuse) depende de T010/T011; las aceptaciones CE-003/CE-004 dependen de T017 y de los dobles.
- **Polish (T025–T026)**: al final.
- **Secuencia**: T020 (reintentos/DLQ) usa la topología AMQP vigente (cola `finance.reservation-info.v1` y DLQ `finance.reservation-info.v1.dlq`); T004 depende de que el catálogo compartido de puertos esté disponible, sin crear una implementación paralela en UC03.

## Notes

- **Invalidación y carrera con UC04**: el *upsert* invalida montos y la referencia de versión sin tocar los registros inmutables; UC04 recalcula de forma determinista a partir de la información registrada y de la versión referenciada antes de un nuevo cobro (general-plan D-17). La carrera entre ambos se serializa con el bloqueo asesor por reserva (`pg_advisory_xact_lock`, general-plan D-15).

- **Política AMQP vigente**: cola `finance.reservation-info.v1` con DLQ `finance.reservation-info.v1.dlq` [CONV]; `acknowledge-mode=AUTO` de la política compartida (el listener retorna tras persistir/registrar); los fallos transitorios se reintentan 5 veces con *backoff* exponencial (base 1 s) antes de la DLQ; el reproceso manual de mensajes queda fuera del alcance de este plan. Sin meta de rendimiento propia. La seguridad del canal está **fuera de alcance** (general-plan §7.1; D-21).
- **Migraciones**: migración inicial única `V1__initial_schema.sql` con todo el esquema (general-plan §4.2, D-31); UC03 no aporta ni numera migraciones; los cambios detectados se registran como versión posterior `V2+`.
- **Reentregas**: sin dedup por `Message-Id` (D-07); el *upsert* re-invalida montos y UC04 recalcula determinista (D-17). Una reentrega idéntica después de un cálculo obliga a re-ejecutar UC04 antes de un nuevo cobro (D-UC03-03).
- `ProvideBaseRateUseCase`, `BoatId`, `BaseRate` y `BaseRateException` son de **UC02** (plan ya redactado): UC03 los consume por nombre y por `port.in` y no los implementa; sus reglas internas se prueban en UC02. La aceptación CE-004 sí ejecuta el `ProvideBaseRateService` real para detectar regresiones de integración.
- `FailureRecorderPort`/`operational_failure` son **compartidos**: el listener y la política compartida los referencian por nombre; las pruebas usan `InMemoryFailureRecorder`. El servicio no registra fallos. El registro se aísla con `REQUIRES_NEW` y existe procedimiento de reproceso.
- UC03 **no depende del puerto de `financial_parameters`** (no calcula seguro; RF-007); la versión usada por la tarifa dinámica llega vía `BaseRateResult.parametersId` (UC02) y se persiste como `base_rate_parameters_id`. El adaptador de UC03 persiste y devuelve `frozen_parameters_id` como `UUID`; **no** hace join ni reconstruye `FrozenParameters` [CONV núcleo]: esa resolución corresponde a UC04/UC10 vía `FinancialParametersRepository.findById(...)` (D-27). **No usa** `PricingCalculator` (no existe `ReservationValueCalculator`; esos cálculos son de UC01 y UC04).
- Los planes posteriores leerán `reservation_information` por nombre; la firma de `ReservationInformationRepository` se publica en «Firmas referenciadas por otros planes».
- Etiquetas usadas: `[SPEC]` (SPEC 03 y contrato), `[CONV]` (general-plan e inferido).

## Checklist de auto-revisión

- [x] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Contratos/Esquema, Estrategia de testing, fases con `T0NN`, Dependencies, Notes).
- [x] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [x] Fecha `2026-10-09` y enlace a `spec.md`.
- [x] Toda regla marcada `[SPEC]` o `[CONV]`.
- [x] Decisiones `D-UC03-01` a `D-UC03-11` en «Decisiones de diseño» con decisión explícita y estado; el esquema de `reservation_information` se crea en la migración inicial única (general-plan §4.2, D-31).
- [x] Cada RF/RNF/CE/HU del SPEC 03 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce004_…`.
- [x] Requisitos de esquema de `reservation_information` (con los `CHECK` de RF-005) verificados contra la migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31).
- [x] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§11) aplicadas; sin cálculos de valor (RF-007) y sin consultas a Flota por owner/capacidad (RF-003).
- [x] «Firmas referenciadas por otros planes» declarado: `RegisterReservationInformationUseCase` no se expone; `ReservationInformationRepository` publicado para los demás planes y UC04.
- [x] Referencias por nombre: `ProvideBaseRateUseCase` (UC02), `FailureRecorderPort`/`operational_failure` y VOs compartidos; sin redefiniciones ni coordinación por tareas externas.
