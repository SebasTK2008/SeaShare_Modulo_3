# Implementation Plan: UC03 - Brindar Información de Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC03 es el caso de uso **unidireccional** por el que el Sistema de Reservas y Operaciones notifica al sistema los datos de una reserva que el arrendatario ya ingresó (`reservation_id`, `boat_id`, `start_date`, `end_date`, `passengers`, `owner_id` y `max_capacity`). El sistema **incluye (`<<include>>`) a UC02** pasando `boat_id` y `start_date` para obtener la tarifa vigente de la embarcación en esa fecha, y registra internamente la `InformaciónDeReserva` en la tabla `reservation_information` para que quede disponible para UC04 (valor calculado) y para que los registros financieros posteriores se asocien a su propietario [SPEC RF-001, RF-002, RF-004, HU1]. **No devuelve ninguna respuesta ni desglose** a Reservas (RF-006, CE-002) y **no calcula ningún valor total** (RF-007).

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. El adaptador de entrada es un **listener AMQP** sobre `seashare.reservations` / `reservation.info.provided` / cola `finance.reservation-info.v1` [contrato UC03]; el servicio de aplicación invoca **solo por `port.in`** el puerto de UC02 `ProvideBaseRateUseCase` y persiste con un adaptador JPA (entidad propia, MapStruct, general-plan D-04). Los fallos se registran mediante el catálogo único de puertos `FailureRecorderPort`/`operational_failure`; el registro usa transacción independiente y existe un procedimiento de reproceso [D-09, D-10]. La clasificación acuse/reenvío/DLQ sigue `contracts/README.md` §4.4 y la configuración AMQP común, con DLQ `<cola>.dlq` [D-22]. El `Message-Id` del encabezado AMQP se usa **solo para trazabilidad**: la idempotencia la da el *upsert* por `reservation_id` sin deduplicación por `message_id` (D-07) [decisión D-UC03-03].

UC03 corresponde a la **fase 5** de la hoja de ruta [general-plan §12]: consume de UC02 (`ProvideBaseRateUseCase`, `BoatId`, `BaseRate`, `BaseRateException`) y del núcleo compartido (`DomainException`, VOs y `FailureRecorderPort`). **La política de migraciones permanece abierta**; UC03 documenta la DDL exacta de `reservation_information` en su rol de dueño, sin aprobar el versionado global [OQ-CROSS-07 / D-08].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir los datos específicos de la reserva | `ReservationInfoMessage`, `RegisterReservationInformationCommand`, `ReservationInfoListener` | T007, T008, T011 | T012 |
| **RF-002** incluir (`<<include>>`) UC02 con `boat_id` y `start_date` | `RegisterReservationInformationService` (vía `ProvideBaseRateUseCase`, de UC02) | T017 | T018 |
| **RF-003** no consultar Flota por propietario/capacidad | `RegisterReservationInformationService` (la única llamada a UC02 lleva `boat_id` + `start_date`) + ArchUnit | T017, T024 | T023 (CE-004) |
| **RF-004** registrar internamente la información | `ReservationInformation` (dominio), `ReservationInformationPersistenceAdapter` | T010, T015 | T016, T019 |
| **RF-005** validar fechas y pasajeros vs capacidad | `ReservationInformation` (dominio) + `CHECK` en la DDL de V1 | T010, T006 | T013, T016 |
| **RF-006** sin respuesta ni desglose a Reservas | `RegisterReservationInformationUseCase` (`void`) + `ReservationInfoListener` (sin reply) | T010, T011 | T022 (CE-002) |
| **RF-007** sin calcular totales (alquiler/seguro/depósito) | `RegisterReservationInformationService` (no usa `PricingCalculator`/`ReservationValueCalculator`) + ArchUnit | T017, T024 | T022 (CE-002) |
| **RF-008** exclusivo de Reservas | Cola dedicada `finance.reservation-info.v1` enlazada al exchange de Reservas + `ReservationInfoRabbitConfig` | T011 | T012, T020 |
| **RNF-001** DTO para la solicitud | `ReservationInfoMessage` (infra) + `RegisterReservationInformationCommand` (aplicación) | T007, T008 | T012 |
| **RNF-002** `BigDecimal` tarifa base, 4 decimales internos | `BaseRate` (de UC02), columna `base_rate NUMERIC(18,4)` | T006, T005, T010 | T013, T016, T019 |
| **RNF-003** manejo de errores robusto (registro interno) | `FailureRecorderPort` + política ack/nack/DLQ del listener | T004, T017, T020 | T020, T021 |
| **CE-001** trazabilidad interna (100 % registrado, 0 discrepancias) | Adaptador de persistencia + `ReservationInformationAcceptanceTest` | T015, T019 | **T019 `ce001_…`** |
| **CE-002** unidireccional: 0 respuestas/desgloses | ArchUnit (sin controller, sin publicaciones, `void`) | T022, T024 | **T022 `ce002_…`** |
| **CE-003** fallas registradas sin información incompleta | `ReservationInformationService` + `FailureRecorderPort` (doble) | T017, T021 | **T021 `ce003_…`** |
| **CE-004** 0 consultas directas a Flota por owner/capacidad | `RegisterReservationInformationService` (validación antes de UC02) + WireMock sobre Flota | T017, T023 | **T023 `ce004_…`** |
| **HU1** registrar internamente la reserva (P1) | Fases 3–5 | T010–T023 | T012–T023 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC03: **Spring AMQP**, Spring Data JPA, MapStruct, ArchUnit, Testcontainers RabbitMQ y `spring-rabbit-test`. Las dependencias se incorporan mediante la configuración AMQP común, sin editar el `pom.xml` en silencio.
**Storage**: PostgreSQL 16+ — tabla `reservation_information` (dueño UC03) y tabla técnica compartida `operational_failure`. `operational_failure.reservation_id` es nullable y no tiene FK; `payload_ref` referencia el mensaje o JSON almacenado. La DDL de ambas tablas se documenta exactamente en sus planes dueños [SPEC general-plan §4, D-10].
**Messaging**: RabbitMQ 3.13+ — exchange `seashare.reservations` (topic, durable), routing key `reservation.info.provided`, cola `finance.reservation-info.v1`, DLQ `finance.reservation-info.v1.dlq` **[CONV; OQ-UC03-02 / OQ-09]** [contrato UC03; README §3.5]
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL **y** RabbitMQ), `spring-rabbit-test`, WireMock (Flota, a través de UC02), Awaitility (reintentos/DLQ), ArchUnit [CONV]
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: El SPEC 03 no define metas de rendimiento `[NEEDS CLARIFICATION: OQ-UC03-04; se buscó en spec.md 003, general-plan.md y el contrato UC03; no hay metas propias para un consumidor AMQP]` **[CONV: sin meta propia]**
**Constraints**: unidireccional sin canal de respuesta (RF-006, CE-002); `owner_id` y `max_capacity` solo desde la solicitud, nunca de Flota (RF-003, CE-004); no se persiste información incompleta y se conserva la versión válida previa (RNF-003, casos extremos); *upsert* con invalidación de montos calculados (casos extremos; D-UC03-02); `BigDecimal` escala 4 para la tarifa (RNF-002); exclusivo del Sistema de Reservas y Operaciones (RF-008)
**Scale/Scope**: 1 evento AMQP entrante [contrato UC03]; 8 RF + 3 RNF + 4 CE + 1 HU del SPEC 03; 1 puerto de entrada (`RegisterReservationInformationUseCase`) + 1 puerto de salida propio (`ReservationInformationRepository`) + piezas consumidas por nombre (UC02, `FailureRecorderPort`)

**Estado actual del repositorio (relevante para UC03)**: igual que en UC11/UC02 — existen `SeashareM3Application.java`, `application.properties` (solo `spring.application.name=seashare-m3`), `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** los paquetes `domain`/`application`/`infrastructure`, la infraestructura AMQP, la tabla `reservation_information`, ni los puertos/piezas de UC02/UC11 (`ProvideBaseRateUseCase`, `BoatId`/`BaseRate`, `FailureRecorderPort`/`operational_failure`).

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
│   │   └── ReservationInformation.java              # T010 [SPEC "InformaciónDeReserva"; general-plan §13]
│   ├── valueobject/
│   │   ├── ReservationId.java                       # VO del núcleo compartido; UC03 lo consume por nombre
│   │   ├── OwnerId.java                             # VO del núcleo compartido; UC03 lo consume por nombre
│   │   ├── BoatId.java                              # de UC02 (T006) — consumido por nombre
│   │   └── BaseRate.java                            # de UC02 (T006) — consumido por nombre [general-plan §13]
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
    │   ├── ReservationInfoRabbitConfig.java         # T011 [CONV: exchange, binding, cola, DLQ, retry, ack manual]
    │   └── dto/
    │       ├── ReservationInfoMessage.java          # T007 [SPEC RNF-001; claves snake_case del contrato]
    │       └── ReservationInfoMessageMapper.java    # T007 [CONV: mensaje → command]
    ├── adapter/out/persistence/
    │   ├── ReservationInformationJpaEntity.java     # T015 [general-plan D-04]
    │   ├── ReservationInformationJpaRepository.java # T015 [CONV]
    │   ├── ReservationInformationMapper.java        # T015 [MapStruct; D-04]
    │   └── ReservationInformationPersistenceAdapter.java  # T015 [upsert con invalidación; transacción única]
    └── config/
        └── RabbitMqProperties.java                  # T011 [CONV: `seashare.messaging.*`: colas, reintentos, DLQ]

src/main/resources/
└── db/migration/
    └── reservation_information.sql                   # T006 [DDL exacta del dueño; política de migraciones abierta]

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── ArchitectureTest.java                        # T002 (base) + T022, T024 (reglas UC03)
├── domain/model/
│   └── ReservationInformationTest.java              # T013
├── application/service/
│   ├── RegisterReservationInformationServiceTest.java  # T018
│   └── ReservationInformationAcceptanceTest.java    # T019 (ce001), T021 (ce003), T022 (ce002), T023 (ce004)
├── infrastructure/adapter/in/messaging/
│   ├── ReservationInfoListenerContractTest.java     # T012 (mensaje vs contrato UC03)
│   └── ReservationInfoListenerRetryPolicyTest.java  # T020 (ack/nack/DLQ, Testcontainers RabbitMQ)
├── infrastructure/adapter/out/persistence/
│   └── ReservationInformationPersistenceAdapterTest.java  # T016 (Testcontainers PostgreSQL)
└── common/
    └── InMemoryFailureRecorder.java                 # de UC02·T004/T016 — reutilizado por nombre [CONV]
```

`ReservationInformation` (dominio) y `ReservationInformationRepository` (puerto de salida) son **de UC03** (`general-plan.md` §4 asigna la tabla a «Entidad (UC03/UC04)»: UC03 crea `reservation_information`, UC04 la completa). La tabla y el puerto se usan después por otros planes **por nombre**; UC03 publica la firma en «Firmas referenciadas por otros planes». `ProvideBaseRateUseCase`/`BoatId`/`BaseRate`/`BaseRateException` son de **UC02**; `FailureRecorderPort`/`operational_failure` son **compartidos** y UC03 no los implementa ni redeclara.

**Firmas referenciadas por otros planes** [general-plan §3.5]: UC03 **no expone su puerto de entrada a otros planes** (`RegisterReservationInformationUseCase` lo consume únicamente su adaptador `messaging`). Como dueño de `reservation_information`, publica aquí la firma de su puerto de **salida** `ReservationInformationRepository`, que los planes de UC05/UC07/UC09/UC08/UC10 referencian por nombre y que UC04 usará para completar montos y parámetros congelados (general-plan D-27). Firma [CONV]:

```java
public interface ReservationInformationRepository {
    Optional<ReservationInformation> findByReservationId(ReservationId reservationId);  // lectura (B/C; UC04)
    ReservationInformation save(ReservationInformation information);  // upsert por reservation_id; el adaptador
                                                                      // deja las columnas calculadas en NULL
                                                                      // (invalidación) y persiste en una
                                                                      // transacción única
    void saveCalculatedAmounts(ReservationId reservationId, CalculatedAmounts amounts); // catálogo único D-09
}
```

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) en un solo módulo Maven, con subpaquetes temáticos sin reglas entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas del §3.4 aplicadas aquí: `domain` sin Spring/JPA/Jackson/AMQP; `application` solo depende de `domain` (se permite `@Transactional` pragmático); `infrastructure.adapter.in` solo invoca `application.port.in`; `infrastructure.adapter.out` solo implementa `application.port.out`; el listener nunca accede a un repositorio; la entidad JPA no sale de `adapter.out.persistence`; los DTOs entrantes viven en `infrastructure.adapter.in` y la aplicación trabaja con `command` (RNF-001). Identificadores en inglés (D-23) y lenguaje ubicuo §13 (*InformaciónDeReserva → `ReservationInformation`*; *SolicitudInformacionReserva* no está en §13 → `ReservationInfoMessage` **[CONV, D-UC03-09]**).

## Reglas de negocio

Todas las reglas provienen del SPEC 03 (`spec.md`) y su contrato, salvo indicación. Ejemplo base del contrato: `start_date = 2026-12-20`, `end_date = 2026-12-22`, `passengers = 4`, `max_capacity = 6`, `owner_id = a1b2c3d4-…`.

1. **Datos recibidos** [SPEC RF-001, RNF-001]: el mensaje AMQP trae los 7 campos obligatorios: `reservation_id`, `boat_id`, `start_date`, `end_date`, `passengers`, `owner_id`, `max_capacity`. Ausencia, `null`, tipo inválido (UUID, fecha `YYYY-MM-DD`, entero) o formato inválido ⇒ mensaje inválido (equivalente a E4). `owner_id` y `max_capacity` ya fueron obtenidos por Reservas desde Flota; el sistema los usa tal cual [SPEC RF-001, RF-003].
2. **Naturaleza unidireccional** [SPEC RF-006, CE-002]: el sistema **no devuelve nada** a Reservas: el listener no responde y el caso de uso no publica mensajes de salida. La confirmación es el `ack` del mensaje tras persistir.
3. **Inclusión de UC02** [SPEC RF-002]: el servicio invoca `ProvideBaseRateUseCase.provideBaseRates(new ProvideBaseRateCommand(List.of(boatId), startDate))` — **una sola consulta** a Flota con una lista de un elemento, con `evaluatedDate = start_date` recibida. La tarifa final (ya dinámica, salida de UC02 a escala 4) se usa para toda la duración de la reserva. Ejemplo: mensaje del contrato → UC02 evalúa `2026-12-20` y devuelve la tarifa final por unidad de tiempo (p. ej. `437500.0000`, ilustrativo).
4. **Fuentes de propietario y capacidad** [SPEC RF-003, CE-004]: UC03 **jamás** consulta a Flota por `owner_id` ni `max_capacity`; la única llamada externa es la inclusión de UC02 con `boat_id` + `start_date`. Si faltan `owner_id` o `max_capacity` en el mensaje, el dato es incompleto y el registro no se persiste [casos extremos].
5. **Validaciones de dominio** [SPEC RF-005, CE-001]: `end_date >= start_date` y `1 <= passengers <= max_capacity`. Violación ⇒ `ReservationInformationValidationException` (permanente): `ack` + registro interno, **sin persistir** y conservando la versión válida previa. Los mismos invariantes se replican como `CHECK` en la DDL de V1 (general-plan §4).
6. **`base_rate` con precisión** [SPEC RNF-002]: la tarifa se almacena como `BigDecimal` a escala 4 (salida de UC02 **sin redondeo**, D-UC02-04) en la columna `base_rate NUMERIC(18,4)`. UC03 no redondea y no calcula montos de alquiler, seguro ni depósito [SPEC RF-007].
7. ***Upsert*** [SPEC casos extremos; contrato regla 4; CE-001]: si ya existe una `InformaciónDeReserva` para el `reservation_id`, se **sobrescribe** el bloque informativo completo (`boat_id`, `base_rate`, `start_date`, `end_date`, `passengers`, `owner_id`, `max_capacity`) con la versión más reciente, **re-incluyendo UC02** para volver a consultar la tarifa vigente de la fecha de inicio.
8. **Invalidación de montos al sobrescribir** [SPEC casos extremos; decisión confirmada, D-UC03-02]: todo *upsert* deja en `NULL`, **en conjunto**, `rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`, `commission_pct_applied`, `insurance_fee_per_passenger_applied` y `calculated_at` (general-plan §4: montos y parámetros congelados se escriben juntos). UC04 **debe recalcular** antes de que pueda procesarse un nuevo cobro (general-plan D-17).
9. **Reentregas y duplicados** [decisión confirmada, D-UC03-03; general-plan D-07]: no hay tabla *inbox* ni deduplicación por `Message-Id`; el `Message-Id` solo se loguea. Una reentrega (entrega al-menos-una-vez) de un mensaje idéntico produce el mismo estado informativo y **re-invalida** los montos; como UC04 recalcula de forma determinista (D-17), el impacto es que UC04 debe volver a ejecutarse antes de un nuevo cobro.
10. **No persistir información incompleta** [casos extremos, RNF-003, CE-003]: si la validación falla, falta `owner_id`/`max_capacity` o la inclusión de UC02 no puede completarse, **no se escribe nada**; como el *upsert* ocurre en una única transacción al final del flujo, la versión válida previa se conserva automáticamente.
11. **Embarcación sin tarifa base** [casos extremos; decisión confirmada, D-UC03-04]: si UC02 devuelve un resultado **vacío** (embarcación sin tarifa o no reconocida; el diseño batch de UC02 nunca lanza `BASE_RATE_NOT_AVAILABLE`, omite el resultado — D-UC02-08), UC03 lanza `BaseRateUnavailableException` (permanente): `ack` + registro interno en `operational_failure`, sin reintento y sin persistir. UC02 ya registró su propio fallo `BASE_RATE_NOT_AVAILABLE`; UC03 registra el suyo (D-UC02-08).
12. **Fallas transitorias** [RNF-003; decisión confirmada, D-UC03-05; README §4.4]: `BaseRateException(FLEET_UNAVAILABLE)` y `BaseRateException(DYNAMIC_RATE_NOT_CONFIGURED)` (de UC02, transitorias) y fallas de persistencia (BD) ⇒ `nack` con **3–5 reintentos y *backoff*** → **DLQ** `finance.reservation-info.v1.dlq`; se registra en `operational_failure`. Jamás se persiste una tarifa asumida ni información a medias [principio rector 4].
13. **Clasificación del listener** [CONV; README §4.4]: resumen de la política de acuse (detalle en §Contratos):
    | Situación | Equivalencia | Acción |
    |---|---|---|
    | Mensaje mal formado, campos ausentes/inválidos, validación RF-005 fallida, sin tarifa base | E4 / E8 | `ack` + registro en `operational_failure` (no se reintenta) |
    | `FLEET_UNAVAILABLE`, `DYNAMIC_RATE_NOT_CONFIGURED`, fallo de BD | E7 / E10 | `nack` + reintentos (3–5, backoff) → DLQ + registro |
14. **Seguridad del canal** [SPEC RF-008; decisión confirmada, OQ-UC03-01]: el mensaje se consume de la cola dedicada `finance.reservation-info.v1` enlazada al exchange de Reservas; sin autenticación adicional a nivel de aplicación (autorización a nivel de broker, análogo a OQ-01/OQ-09 del general-plan). No se implementa `SecurityConfig` para AMQP en este plan.
15. **Orden de procesamiento** [CONV, README §4.3]: el mensaje se valida por completo (reglas 1 y 5) **antes** de invocar a UC02, y UC02 se invoca antes de persistir: una llamada externa nunca se gasta en un mensaje ya inválido.

## Migración V1 (contribución)

La política global de migraciones permanece abierta (OQ-CROSS-07 / D-08). UC03 es dueño de la DDL exacta de `reservation_information`, sin aprobar el archivo, la versión ni la consolidación global. La DDL exacta es:

```sql
-- DDL exacta de la tabla propiedad de UC03; el versionado global queda abierto
CREATE TABLE reservation_information (
    reservation_id                  UUID         PRIMARY KEY,             -- [SPEC RF-001; general-plan §4]
    boat_id                         UUID         NOT NULL,                -- [SPEC RF-001]
    base_rate                       NUMERIC(18,4) NOT NULL,               -- [SPEC RNF-002; escala 4, sin redondeo (D-UC02-04)]
    start_date                      DATE         NOT NULL,                -- [SPEC RF-001]
    end_date                        DATE         NOT NULL,                -- [SPEC RF-001]
    passengers                      INTEGER      NOT NULL,                -- [SPEC RF-001]
    owner_id                        UUID         NOT NULL,                -- [SPEC RF-001/RF-003: recibido en el mensaje, nunca de Flota]
    max_capacity                    INTEGER      NOT NULL,                -- [SPEC RF-001/RF-003]
    -- Columnas calculadas por UC04; NULL hasta entonces y anuladas por el upsert de UC03 (D-UC03-02):
    rental_amount                   NUMERIC(18,4),
    insurance_amount                NUMERIC(18,4),
    deposit_amount                  NUMERIC(18,4),
    total_amount                    NUMERIC(18,4),
    commission_pct_applied          NUMERIC(18,4),                       -- parámetros congelados (UC04 RF-008, D-27)
    insurance_fee_per_passenger_applied NUMERIC(18,4),
    calculated_at                   TIMESTAMPTZ,
    CONSTRAINT chk_reservation_dates     CHECK (end_date >= start_date),                -- [SPEC RF-005]
    CONSTRAINT chk_reservation_passengers CHECK (passengers BETWEEN 1 AND max_capacity) -- [SPEC RF-005]
);
```

Notas: la PK es el `reservation_id` recibido (no se genera UUID). UC11 **nunca** modifica estas columnas y un cambio posterior de parámetros globales no afecta a la fila (D-27). No hay columnas ni índices inventados: coinciden con `general-plan` §4.

## Contratos

Fuente: `contracts/events/UC03-informacion-reserva.md` y las convenciones de `contracts/README.md` §3.

### Evento AMQP: `reservation.info.provided` (Reservas → sistema)

| Propiedad | Valor |
|---|---|
| Exchange | `seashare.reservations` (topic, durable) |
| Routing Key | `reservation.info.provided` |
| Cola consumidora | `finance.reservation-info.v1` |
| DLQ (reintentos agotados) | `finance.reservation-info.v1.dlq` **[CONV; OQ-UC03-02 / OQ-09]** |
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
| Cuerpo no JSON, headers ausentes, campos ausentes/`null`, tipos/formato inválidos | E4 | `ack` (no reintentar) | `operational_failure` |
| `end_date < start_date` o `passengers` fuera de `1..max_capacity` | E4 (RF-005) | `ack` | `operational_failure` |
| Falta `owner_id` o `max_capacity` (dato incompleto) | E8 (dato del recurso recibido) | `ack` | `operational_failure` |
| Sin tarifa base (UC02 devuelve vacío) | E8 | `ack` | `operational_failure` |
| `FLEET_UNAVAILABLE` / `DYNAMIC_RATE_NOT_CONFIGURED` (UC02) | E7 / E6 | `nack` + reintentos (3–5, *backoff*) → DLQ | `operational_failure` al agotar |
| Fallo de persistencia (BD) | E10 | `nack` + reintentos → DLQ | `operational_failure` al agotar |
| Mensaje repetido (misma `reservation_id`, mismo contenido) | Idempotente | `ack`; *upsert* del mismo estado (re-invalida montos, D-UC03-03) | — |

*Nota*: los errores de negocio se confirman (`ack`) para no reprocesar en vano [contrato UC03 §5]; las fallas transitorias se reintentan conforme al general-plan §7.2.

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), contrato UC03 y SPEC 03. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %. Las pruebas de fallos usan un doble `InMemoryFailureRecorder`, sin redefinir ni depender de tareas externas.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | invariantes RF-005 (fechas, pasajeros vs capacidad, requeridos), tarifa escala 4 | `domain/model/ReservationInformationTest` |
| Unitario (`application`) | orquestación: incluye UC02 una sola vez, upsert re-consulta tarifa, sin tarifa (permanente), FLEET/DYNAMIC (transitorio), validación, versión previa conservada, sin cálculos (RF-007) | `RegisterReservationInformationServiceTest` (Mockito sobre `ProvideBaseRateUseCase`, repo, `InMemoryFailureRecorder`) |
| Contrato (messaging) | cuerpo y headers del contrato UC03: mensaje válido, campos ausentes/`null`, tipos/formato inválidos | `ReservationInfoListenerContractTest` |
| Integración (Testcontainers) | migración V1 + *upsert* + invalidación + CHECK + versión previa conservada | `ReservationInformationPersistenceAdapterTest` |
| Política del listener (Testcontainers RabbitMQ) | `ack`/`nack`/reintentos/DLQ según la tabla de decisión; registro en `operational_failure` | `ReservationInfoListenerRetryPolicyTest` |
| Aceptación | CE-001…CE-004 | `ReservationInformationAcceptanceTest` |
| Arquitectura | reglas §3.4; RF-003/RF-006/RF-007 (sin controller, sin publicaciones, sin cálculos, sin consultas directas a Flota por owner/capacidad) | `arch/ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-004)** — nóminal `ce00X_<descripcion>`:

- **`ce001_reserva_notificada_registrada_completa_y_upsert`** (CE-001, T019): integración (Testcontainers PostgreSQL): la reserva del ejemplo del contrato queda registrada con los 7 campos + `base_rate` exactos; un segundo mensaje con la misma `reservation_id` sobrescribe e **invalida** los montos (NULL); cero discrepancias.
- **`ce002_unidireccional_sin_respuesta_ni_desglose`** (CE-002, T022): ArchUnit — no existe `@RestController`/adaptador web para UC03; el listener no envía mensajes (sin `RabbitTemplate` de salida ni outbox) y `RegisterReservationInformationUseCase` devuelve `void`; ninguna clase de UC03 depende de `PricingCalculator`/`ReservationValueCalculator` (RF-007).
- **`ce003_fallas_registradas_sin_informacion_incompleta`** (CE-003, T021): con dobles (`InMemoryFailureRecorder` + stub de `ProvideBaseRateUseCase`): UC02 falla (FLEET/DYNAMIC), sin tarifa, `owner_id` ausente, `max_capacity` ausente, validación fallida → en el 100 % de los casos el fallo queda registrado internamente, **no se persiste información incompleta** y la versión válida previa se conserva.
- **`ce004_cero_consultas_a_flota_por_propietario_y_capacidad`** (CE-004, T023): integración con WireMock sobre Flota (a través del adaptador de UC02): (a) el **único** request a Flota contiene únicamente `boat_ids` (sin `owner_id` ni `max_capacity`); (b) si el mensaje llega sin `owner_id` o sin `max_capacity`, la validación falla **antes** de invocar a UC02 y no se realiza ninguna llamada a Flota (cero requests).

Además: la regresión de consumidores **no forma parte de este plan** (UC04 completará montos y parámetros congelados; los demás planes leen la tabla por nombre; aquí solo se publica la firma del repositorio).

## Discrepancias y puntos abiertos

Registro de contradicciones detectadas al elaborar este plan. **No se resuelven en silencio**: cada una indica la decisión para avanzar y qué requiere confirmación. Los IDs `D-UC03-xx` son propios de este plan; `D-xx` sin prefijo UC03 son decisiones del plan general (§11) o del plan de UC02.

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC03-01** | La política de migraciones y la consolidación de DDL no están cerradas | `general-plan.md` §4 vs OQ-CROSS-07 / D-08 | UC03 mantiene la DDL exacta de `reservation_information` como dueño; no aprueba versión, archivo global ni orden de migraciones | Abierta; requiere decisión externa |
| **D-UC03-02** | El SPEC 03 solo dice que los montos calculados «se invalidan»; no fija qué columnas | SPEC 03 casos extremos vs general-plan §4 (montos y parámetros congelados «escritos juntos») | La invalidación anula en conjunto `rental_amount`, `insurance_amount`, `deposit_amount`, `total_amount`, `commission_pct_applied`, `insurance_fee_per_passenger_applied` y `calculated_at` | Decidida (**confirmada por el responsable**) |
| **D-UC03-03** | El SPEC 03 no define la deduplicación AMQP; la entrega del broker es al-menos-una-vez (D-07) | SPEC 03 vs general-plan D-07 («un `message_id` no está en ningún SPEC») | *Upsert* puro sin tabla *inbox* ni dedup por `Message-Id`; una reentrega re-invalida montos y UC04 recalcula determinista (D-17) | Decidida (**confirmada por el responsable**) |
| **D-UC03-04** | El SPEC 03 dice «registra el fallo y no persiste» cuando UC02 no puede completarse, sin clasificar el motivo | SPEC 03 casos extremos vs tabla de decisión README §4.4 | Embarcación sin tarifa (UC02 devuelve vacío) = **permanente** → `ack` + registro; FLEET/DYNAMIC = **transitorio** → reintentos/DLQ | Decidida (**confirmada por el responsable**) |
| **D-UC03-05** | Política de acuse del listener no definida por el SPEC 03 | SPEC 03 vs README §4.4 y general-plan §7.2 | `nack` con 3–5 reintentos y *backoff* solo en fallas transitorias → DLQ + registro; `ack` en inválidos | Decidida (**confirmada por el responsable**) |
| **D-UC03-06** | El registro de fallos debe aislarse de la transacción de negocio | `general-plan.md` §3.5/§4 y D-10 | UC03 consume `FailureRecorderPort` por nombre; el puerto registra con `REQUIRES_NEW`, `reservation_id` nullable y `payload_ref` referencial. Se documenta un procedimiento de reproceso | Aplicada |
| **D-UC03-07** | UC03 necesita artefactos Maven para AMQP y sus pruebas | pom actual vs UC03 | Se documentan en T001 sin coordinar mediante tareas externas ni editar en silencio | Aplicada |
| **D-UC03-08** | El SPEC RF-008 fija *quién* publica (solo Reservas) pero no el mecanismo de autenticación del canal AMQP | SPEC 03 RF-008 vs ausencia en SPEC/general (análogo a OQ-01) | Sin autenticación a nivel de aplicación: cola dedicada + autorización del broker; el `SecurityConfig` REST de UC11 no aplica aquí. Ver **OQ-UC03-01** | Abierta |
| **D-UC03-09** | `SolicitudInformacionReserva` (entidad clave del SPEC 03) no está en el lenguaje ubicuo §13 | SPEC 03 vs general-plan §13 | `ReservationInfoMessage` (infraestructura) → `RegisterReservationInformationCommand` (aplicación); `InformaciónDeReserva` → `ReservationInformation` (ya en §13) | Decidida **[CONV]** |
| **D-UC03-10** | `ReservationId`/`OwnerId` son VOs compartidos del núcleo | `general-plan.md` §3.3 | UC03 los consume por nombre y no los redeclara; el lenguaje ubicuo vigente prevalece | Aplicada |
| **D-UC03-11** | `ProvideBaseRateUseCase` es *batch-only* (una consulta por invocación) y nunca lanza `BASE_RATE_NOT_AVAILABLE` | SPEC 03 RF-002 vs plan UC02 (T016, D-UC02-08) | UC03 invoca con lista de un elemento y correlaciona por `boat_id`; resultado vacío ⇒ `BaseRateUnavailableException` (regla 11) | Decidida |

## Preguntas abiertas (OQ-UC03-xx)

Preguntas que este plan no puede resolver con el SPEC 03, el plan general ni los contratos. Hasta que se confirmen rige la propuesta por defecto y las tareas afectadas quedan condicionadas.

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC03-01** | ¿Qué mecanismo garantiza que solo Reservas publique en el exchange? El SPEC RF-008 fija quién, no cómo (análogo a OQ-01/OQ-09 del general-plan) | T011; regla 14 | Autorización a nivel de broker (vhost/usuario/permisos del exchange); sin autenticación en la aplicación. No se implementa `SecurityConfig` de AMQP en este plan | Abierta |
| **OQ-UC03-02** | ¿Cuál es el nombre y la topología definitivos de la DLQ y del reproceso manual de los mensajes? (heredada del general-plan OQ-09: nombres AMQP a acordar con Reservas) | T011, T020 | `finance.reservation-info.v1.dlq` **[CONV]**; reproceso manual fuera del alcance de este plan | Abierta |
| **OQ-UC03-03** | ¿Cuántos reintentos y con qué *backoff* para los mensajes transitorios? (general-plan §7.2: 3–5 con *backoff*) | T011, T020 | 5 reintentos con *backoff* exponencial (1 s base) **[CONV]** | Abierta |
| **OQ-UC03-04** | ¿Existe una meta de rendimiento para UC03? El SPEC 03 no la define | Technical Context | Sin meta propia: procesamiento AMQP asíncrono **[CONV]** | Abierta |
| **OQ-UC03-05** | ¿Cómo se resuelve la carrera UC03 (upsert/reenvío que invalida montos) vs UC04 (cálculo)? (general-plan OQ-06) | D-UC03-03; nota para UC04 | UC04 recalcula de forma determinista a partir de la información registrada y los parámetros congelados (general-plan D-17); ante una invalidación, UC04 debe volver a ejecutarse antes de un nuevo cobro | Abierta (de UC04) |

**`[NEEDS CLARIFICATION]` consolidado:** OQ-UC03-01 a OQ-UC03-05.

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable de forma independiente; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias con otros casos de uso se expresan mediante puertos, firmas y tablas, no mediante numeración de tareas externas.

### Phase 1: Setup

- [ ] **T001** · `pom.xml`: registrar Spring AMQP, pruebas RabbitMQ y Testcontainers RabbitMQ, sin editar dependencias en silencio.
- [ ] **T002** · `application.properties` (datasource + `spring.rabbitmq.*`) con la configuración AMQP común (`seashare.messaging.*`, DLQ `<cola>.dlq`) y base de `ArchitectureTest`.

### Phase 2: Foundational

- [ ] **T003** · `DomainException` (`domain/exception`), base del árbol de excepciones del núcleo común.
- [ ] **T004** · consumir `FailureRecorderPort` y `operational_failure` por sus firmas compartidas; registro `REQUIRES_NEW`, `reservation_id` nullable, sin FK, `payload_ref` referencial y procedimiento de reproceso.
- [ ] **T005** · consumir por nombre las piezas de UC02: `ProvideBaseRateUseCase`, `ProvideBaseRateCommand`, `BaseRateResult`, `BoatId`, `BaseRate` y `BaseRateException`; UC03 no las implementa ni las redeclara.
- [ ] **T006** · documentar la DDL exacta de `reservation_information` como tabla propia de UC03; la política y orden global de migraciones quedan abiertos (OQ-CROSS-07 / D-08).
- [ ] **T007** · `ReservationInfoMessage` (`infrastructure/adapter/in/messaging/dto`, claves `snake_case` del contrato) + `ReservationInfoMessageMapper` (mensaje → `RegisterReservationInformationCommand`) [RNF-001; D-UC03-09].
- [ ] **T008** · `RegisterReservationInformationCommand` (los 7 campos obligatorios) usando los VOs compartidos `ReservationId`/`OwnerId` por nombre [general-plan §3.3; D-UC03-10].
- [ ] **T009** · Excepciones de dominio: `ReservationInformationValidationException` (permanente) y `BaseRateUnavailableException` (permanente), ambas sobre `DomainException` [CONV].

### Phase 3: US1 — Recepción AMQP y validaciones (HU1; RF-001, RF-005, RF-008, RNF-001)

- [ ] **T010** · `RegisterReservationInformationUseCase` (`void`, sin respuesta — RF-006) + dominio `ReservationInformation` (inmutable): invariantes RF-005 (`end_date >= start_date`; `1 <= passengers <= max_capacity`), campos requeridos y `baseRate` a escala 4 [RNF-002].
- [ ] **T011** · `ReservationInfoListener` + `ReservationInfoRabbitConfig` + `RabbitMqProperties`: exchange `seashare.reservations` (topic, durable), binding `reservation.info.provided`, cola `finance.reservation-info.v1`, DLQ `finance.reservation-info.v1.dlq` **[CONV; OQ-UC03-02]**, `acknowledge-mode=MANUAL` (ack tras persistir), reintentos transitorios 3–5 con *backoff* **[OQ-UC03-03]**; `Message-Id` a logs.
- [ ] **T012** · Tests de contrato del mensaje AMQP (`ReservationInfoListenerContractTest`): parseo del body del contrato; campos ausentes/`null`; tipos/formato inválidos (UUID, fechas, enteros) → mensaje inválido (ack + registro).
- [ ] **T013** · Pruebas unitarias de dominio `ReservationInformation`: fechas (fin == inicio válido; fin < inicio inválido), pasajeros (1, `max_capacity`, `max_capacity + 1`), campos requeridos, tarifa escala 4 [RF-005, RNF-002].

### Phase 4: US1 — Registro con inclusión de UC02 e invalidación (HU1; RF-002, RF-003, RF-004, RNF-002, CE-001)

- [ ] **T014** · `ReservationInformationRepository` (`application/port/out`) con la firma publicada en «Firmas referenciadas por otros planes»: `findByReservationId` + `save` (upsert). [general-plan §3.5]
- [ ] **T015** · `ReservationInformationJpaEntity` + `ReservationInformationJpaRepository` (sobre `reservation_information`, PK `reservation_id`) + `ReservationInformationMapper` (MapStruct, D-04) + `ReservationInformationPersistenceAdapter`: *upsert* `INSERT … ON CONFLICT (reservation_id) DO UPDATE` que sobrescribe el bloque informativo y **deja las columnas calculadas en NULL** (invalidación, **D-UC03-02**) dentro de una **transacción única**.
- [ ] **T016** · Pruebas de integración del adaptador (Testcontainers PostgreSQL): persistencia de los 7 campos + `base_rate`; sobrescritura; invalidación de montos + parámetros congelados + `calculated_at`; CHECK de fechas y pasajeros (constraint de BD); versión válida previa conservada ante fallo de persistencia.
- [ ] **T017** · `RegisterReservationInformationService`: valida (reglas 1 y 5) → incluye UC02 con `List.of(boatId)` y `evaluatedDate = start_date` (RF-002, regla 3) → *upsert* con `FailureRecorderPort` (por nombre) ante fallos; correlaciona por `boat_id`; resultado vacío → `BaseRateUnavailableException` (regla 11); `BaseRateException` FLEET/DYNAMIC → transitorio (regla 12); **sin cálculos de valor** (RF-007).
- [ ] **T018** · Pruebas unitarias del servicio (Mockito + `InMemoryFailureRecorder`): flujo feliz; upsert re-consulta la tarifa; sin tarifa (permanente: registro, sin persistir, versión previa); FLEET/DYNAMIC (transitorio); validación fallida; una sola invocación a `ProvideBaseRateUseCase`.
- [ ] **T019** · **`ce001_reserva_notificada_registrada_completa_y_upsert`** (CE-001): test de aceptación de integración (Testcontainers PostgreSQL): la reserva del contrato queda registrada completa con `base_rate` exacta (escala 4) y el reenvío sobrescribe e invalida; cero discrepancias.

### Phase 5: US1 — Unidireccionalidad y manejo de fallos (HU1; RF-006, RF-003, RNF-003; CE-002, CE-003, CE-004)

- [ ] **T020** · Implementar la política de acuse del listener (tabla de decisión de §Contratos) y `ReservationInfoListenerRetryPolicyTest` (Testcontainers RabbitMQ): mensaje inválido/sin tarifa → `ack` + registro; `FLEET_UNAVAILABLE` → `nack` + reintentos → DLQ `finance.reservation-info.v1.dlq`; fallo de BD → `nack`.
- [ ] **T021** · **`ce003_fallas_registradas_sin_informacion_incompleta`** (CE-003): con dobles — UC02 falla (FLEET/DYNAMIC), sin tarifa, `owner_id` ausente, `max_capacity` ausente, validación fallida → registro interno en el 100 % de los casos y **cero información incompleta persistida**; versión válida previa conservada.
- [ ] **T022** · **`ce002_unidireccional_sin_respuesta_ni_desglose`** (CE-002): ArchUnit — sin `@RestController` ni adaptador web; el listener no publica (sin `RabbitTemplate` de salida ni `OutboxPort`); `RegisterReservationInformationUseCase` devuelve `void`; ninguna clase de UC03 usa `PricingCalculator`/`ReservationValueCalculator` (RF-007).
- [ ] **T023** · **`ce004_cero_consultas_a_flota_por_propietario_y_capacidad`** (CE-004): integración con WireMock sobre Flota (a través del adaptador de UC02): el request a Flota contiene solo `boat_ids` (sin `owner_id`/`max_capacity`); si falta `owner_id`/`max_capacity`, cero llamadas a Flota (validación previa, regla 15).
- [ ] **T024** · ArchUnit (reglas §3.4 y específicas de UC03): `domain` sin Spring/JPA/Jackson/AMQP; `application` solo depende de `domain`; `infrastructure.adapter.in.messaging` solo invoca `application.port.in`; entidad JPA no sale de `adapter.out.persistence`; sin `PricingCalculator`/`ReservationValueCalculator` (CE-002).

### Phase 6: Polish — **parcialmente compartido con la Fase general**

- [ ] **T025** · Documentación y referencias: publicar la firma de `ReservationInformationRepository`, documentar la DDL exacta y mantener abierta la política global de migraciones; confirmar la DLQ, el reproceso y la topología AMQP. No se edita ningún contrato ni SPEC sin acuerdo.
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
- **US1 (T010–T019)**: dominio y listener (T010–T013) → repositorio y adaptador (T014–T016) → servicio y aceptación CE-001 (T017–T019). El adaptador de persistencia y el servicio dependen de la migración V1 (T006).
- **Fallos y unidireccionalidad (T020–T024)**: después del flujo feliz; T020 (política de acuse) depende de T010/T011; las aceptaciones CE-003/CE-004 dependen de T017 y de los dobles.
- **Polish (T025–T026)**: al final.
- **Riesgo de secuencia**: T020 (reintentos/DLQ) depende de la decisión externa sobre topología y reproceso AMQP (OQ-UC03-02); T004 depende de que el catálogo compartido de puertos esté disponible, sin crear una implementación paralela en UC03.

## Notes

- **OQ-CROSS-05 / D-CROSS-05**: la invalidación de montos en el upsert posterior al cobro permanece abierta; UC03 no la resuelve.

- El SPEC 03 **no** define: mecanismo de autenticación del canal (OQ-UC03-01), nombre/topología de DLQ (OQ-UC03-02), política exacta de reintentos (OQ-UC03-03), metas de rendimiento (OQ-UC03-04) ni la resolución de la carrera con UC04 (OQ-UC03-05) → `[NEEDS CLARIFICATION]`/`[PEND]` con propuesta por defecto registrada.
- **Migraciones**: la política global permanece abierta (OQ-CROSS-07 / D-08). UC03 es dueño de la DDL exacta de `reservation_information` y no aprueba el archivo ni el versionado global.
- **Reentregas**: sin dedup por `Message-Id` (D-07); el *upsert* re-invalida montos y UC04 recalcula determinista (D-17). Una reentrega idéntica después de un cálculo obliga a re-ejecutar UC04 antes de un nuevo cobro (D-UC03-03).
- `ProvideBaseRateUseCase`, `BoatId`, `BaseRate` y `BaseRateException` son de **UC02** (plan ya redactado): UC03 los consume por nombre y por `port.in`, no los implementa ni los prueba.
- `FailureRecorderPort`/`operational_failure` son **compartidos**: UC03 los referencia por nombre y usa `InMemoryFailureRecorder` en las pruebas. El registro se aísla con `REQUIRES_NEW` y existe procedimiento de reproceso.
- UC03 **no lee `financial_parameters`** (no calcula seguro; RF-007) y **no usa** `PricingCalculator`/`ReservationValueCalculator` (esos cálculos son de UC01 y UC04).
- Los planes posteriores leerán `reservation_information` por nombre; la firma de `ReservationInformationRepository` se publica en «Firmas referenciadas por otros planes» (D-09).
- Etiquetas usadas: `[SPEC]` (SPEC 03 y contrato), `[CONV]` (general-plan e inferido), `[PEND]`/`[NEEDS CLARIFICATION]` (sin definir).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Contratos/Migración, Estrategia de testing, fases con `T0NN`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-09` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]`, `[CONV]`, `[PEND]` o `[NEEDS CLARIFICATION]`.
- [ ] Contradicciones `D-UC03-01` a `D-UC03-11` en «Discrepancias y puntos abiertos» con decisión explícita, sin resolución silenciosa; D-UC03-01 mantiene abierta la política de migraciones.
- [ ] Preguntas abiertas definidas en el propio plan (OQ-UC03-01 a OQ-UC03-05), con remisión a las OQ del general (OQ-01, OQ-06, OQ-09) cuando corresponden.
- [ ] Cada RF/RNF/CE/HU del SPEC 03 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce004_…`.
- [ ] DDL exacta de `reservation_information` propiedad de UC03, con los `CHECK` de RF-005; política de migraciones abierta.
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§13) aplicadas; sin cálculos de valor (RF-007) y sin consultas a Flota por owner/capacidad (RF-003).
- [ ] «Firmas referenciadas por otros planes» declarado: `RegisterReservationInformationUseCase` no se expone; `ReservationInformationRepository` publicado para los demás planes y UC04.
- [ ] Referencias por nombre: `ProvideBaseRateUseCase` (UC02), `FailureRecorderPort`/`operational_failure` y VOs compartidos; sin redefiniciones ni coordinación por tareas externas.
