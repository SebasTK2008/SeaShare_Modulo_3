# Implementation Plan: UC04 - Solicitar el Valor Calculado de la Reserva

**Date**: 2026-10-09 (plan original)
**Updated**: 2026-10-28 (ver [Actualizaciones del plan](#actualizaciones-del-plan))
**Spec**: [spec.md](spec.md)

> **Actualización del plan — 2026-10-28.** Este documento parte del plan original del **2026-10-09** (commit `ea8748b`) y de su última revisión previa a esta actualización, conservada en el historial de Git (commit `ac99ea1`, 2026-10-24). Las secciones modificadas durante la actualización para la implementación de UC04 están marcadas con `[ACT-xx]` y se detallan en [Actualizaciones del plan](#actualizaciones-del-plan). Salvo las líneas de versión y la sección «Actualizaciones del plan», el contenido sin marcador pertenece al plan original.

## Summary

UC04 es el caso de uso **síncrono** por el que el Sistema de Reservas y Operaciones solicita el **valor financiero definitivo** de una reserva identificada por su `reservation_id` [SPEC RF-001]. El sistema recupera la `InformaciónDeReserva` previamente registrada por UC03 (tarifa base, fechas, pasajeros, propietario y capacidad; RF-002) y calcula: **monto de alquiler** = `tarifa base registrada × días inclusivos` [RF-003], **seguro náutico** = `tarifa de seguro × pasajeros` [RF-004], **depósito de garantía** = `10 % de la tarifa diaria vigente registrada` (la misma del alquiler, con la dinámica ya resuelta) **sin** multiplicar por duración, pasajeros ni daño [RF-005, HU1] y **valor total** = alquiler + seguro + depósito, que es el monto que se cobra en "Procesar cobro" [RF-006]. Devuelve el desglose completo [RF-007] y **actualiza la fila interna** con los montos calculados, la **referencia a la versión usada** (`frozen_parameters_id`) y `calculated_at` —los valores aplicados (comisión % y tarifa de seguro por pasajero) se resuelven de esa versión y **no se copian** en la reserva—, dejándola disponible para UC05 [RF-008, CE-004].

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. Adaptador de entrada REST (`POST /api/v1/reservations/{reservation_id}/calculated-value`, sin cuerpo) que invoca exclusivamente `application.port.in`; lógica de cálculo pura en `ReservationValueCalculator` (dominio, nombrado en general-plan §3.3); persistencia JPA sobre `reservation_information` (tabla de UC03) y lectura de la **versión vigente** de `financial_parameters` (tabla versionada de UC11) **por nombre** solo cuando la reserva no tiene referencia de versión (el recálculo determinista usa la versión referenciada, resuelta por `CalculateReservationValueService` con `FinancialParametersRepository.findById(...)`). **[ACT-04]** Errores en *Problem Details* (RFC 9457) con el catálogo de `contracts/README.md` §3.4 y orden E1→E2→E3→E4→E6 (sin filas de autenticación: seguridad fuera de alcance; E5 **no aplica**: UC04 no llama a ninguna dependencia externa) [contrato UC04 §5; README §4.3].

**Decisiones clave del plan** (confirmadas con el responsable):
- **D-17 (general-plan)**: UC04 **recalcula siempre** a partir de la información registrada y de la **versión de parámetros referenciada** en la reserva (`frozen_parameters_id`; la vigente si no hay referencia), de modo que cada solicitud devuelve el mismo desglose (determinista) e idempotente [general-plan D-17; SPEC casos extremos].
- **Redondeo**: precisión interna de 4 decimales; las salidas monetarias se redondean `HALF_UP` a 2 decimales (`contracts/README.md` §3.1) [SPEC RNF-002].
- **Depósito**: 10 % de la tarifa diaria vigente registrada (la misma del alquiler, con la tarifa dinámica ya resuelta); general-plan D-29 [SPEC RF-005].
- **Concurrencia** (D-UC04-05): la lectura-modificación-escritura se ejecuta en una transacción única y se serializa con el bloqueo asesor por reserva (`pg_advisory_xact_lock`; general-plan D-15), compartido con el *upsert* de UC03.
- **Registro de fallos**: el SPEC 04 pide "registra el fallo" en los casos de información incompleta y de parámetros sin configurar → UC04 usa `FailureRecorderPort` **por nombre**. El `404` **no** registra (estado transitorio esperado; ver D-UC04-06).

UC04 corresponde a la **fase 5** de la hoja de ruta [general-plan §10]: depende de UC03 (crea `reservation_information` y publica su repositorio) y del núcleo compartido de parámetros financieros. **No depende directamente de UC02**: la tarifa vigente ya quedó registrada por UC03. **No persiste tablas nuevas ni define migraciones**: rige la migración inicial única (general-plan §4.2, D-31).

## Actualizaciones del plan

> **Actualización 2026-10-28 [ACT-xx]**: registro único de los cambios aplicados sobre el plan original durante la revisión previa y la implementación de UC04. Cada fila indica la sección afectada, el texto que se sustituyó y el motivo. El resto del documento conserva la redacción original salvo la marca `[ACT-xx]` que acompaña a la sección cambiada.

| ID | Sección | Antes → Después | Motivo |
|---|---|---|---|
| **ACT-01** | `Technical Context` · `Primary Dependencies` | `Spring Boot 4.1.1` → `Spring Boot 4.1.2 (parent del pom.xml)` | Alinear con el build actual (la misma corrección se aplicó en UC02). |
| **ACT-02** | `Technical Context` · «Estado actual del repositorio» | «solo `SeashareM3Application`, `application.properties` con `spring.application.name`; no existen los paquetes de capas, la tabla `reservation_information` ni las piezas de UC02/UC03/UC11» → inventario real: núcleo compartido y UC01/UC02/UC03/UC11 implementados, piezas de UC04 ya creadas antes de empezar (`ReservationValue`, `CalculateReservationValueUseCase/Command/Result`, las tres excepciones, `withCalculated`, `saveCalculatedAmounts`) y estado final del caso de uso (calculator, servicio, controller/response, wiring y pruebas) | Registrar el estado real del repositorio antes de ejecutar T008–T028 y al cierre de la implementación. |
| **ACT-03** | `Project Structure`, `Scale/Scope` y `Notes` | `ReservationId` «de UC03·T008» y `Money` «de UC02·T006» → VOs del núcleo compartido (UC11·T007); `ReservationInformation.withCalculated` y `ReservationInformationRepository.saveCalculatedAmounts` «UC04 añade» → «ya implementados en el merge de UC03»; se añade al árbol el wiring (`CalculateReservationValueConfiguration`); rutas de pruebas `ReservationValueControllerContractTest`/`ReservationValuePersistenceAdapterTest`/`common/InMemoryFailureRecorder` → `contract/UC04CalculatedValueContractTest`, `arch/Uc04ArchitectureTest`, ampliación de `ReservationInformationPersistenceAdapterTest` y `ReservationInformationPersistenceAdapterIntegrationTest`, `integration/ReservationValueConcurrencyIntegrationTest` y `integration/ReservationValuePersistenceAcceptanceTest`, con los dobles en `support/` | Reflejar el código realmente existente y la convención de pruebas de los planes UC01/UC02/UC03/UC11. |
| **ACT-04** | `Summary` y «Firmas referenciadas por otros planes» | «la versión referenciada, resuelta por el adaptador de UC03» y «`FrozenParameters` … se obtiene con `FinancialParametersRepository.findById(...)`» → la resolución la hace `CalculateReservationValueService`, que recibe `FinancialParameters` de `findById(...)`/`findCurrent()`; `FrozenParameters` queda como VO de representación del §11 **sin uso en UC04** y la transición congelada recibe solo el `UUID` | UC03 declara que no hace *join* ni reconstruye `FrozenParameters` (plan UC03, T015 y Notes) y `findById` devuelve `FinancialParameters`. |
| **ACT-05** | `Reglas de negocio` 9 y 15 | «(la de mayor `version_number`, leída con `findCurrent()`)» → `findCurrent()` **solo en el primer cálculo** (los recálculos usan la versión referenciada); la rama de comisión nula del 503 se precisa como **vía defensiva inalcanzable con datos reales** (invariante de `FinancialParameters` y columna `NOT NULL`) | Coincidir con el comportamiento implementado y con el alcance real de E4/E6. |
| **ACT-06** | `Esquema (migración única)`, `Reglas de negocio` 18 y T011 | «operación `SELECT … FOR UPDATE`/`UPDATE`» → lectura-modificación-escritura JPA bajo `pg_advisory_xact_lock(reservation_id)`, adquirido por el servicio **antes** de leer y re-adquirido por el adaptador al escribir (reentrante en la misma transacción); citas `UC03·T016` (invalidación) y `UC03·T015` (resolución) corregidas | La regla 18 y D-15 fijan el bloqueo asesor (el `FOR UPDATE` no se usa) y las citas de tareas de UC03 estaban invertidas. |
| **ACT-07** | `Contratos` · ejemplo de error 404 | Ejemplo con `title` «Reserva sin información registrada» y `detail` en español → ejemplo del contrato §5 (`title` «Reservation information not found», `detail` en inglés) | El `title` lo fija `ErrorCode` y el contrato §5 ya publicaba ese ejemplo (`contracts/README.md` §3.3 manda el del catálogo); el plan documentaba una respuesta distinta de la real. |
| **ACT-08** | `Reglas de negocio` 16 y 17, y `Notes` | Sin documentar → la regla 16 fija `use_case = "UC04"`, `reason = <código>` y `payload_ref = "reservationId=<uuid>"`; la 17 anota que **E1 solo aplica en su vertiente `415` y esta no**, porque la petición no tiene cuerpo (`Content-Type` solo es obligatorio en `POST`/`PUT` con cuerpo, README §3.2) | Documentar las decisiones aplicadas en T011/T017 que el plan dejaba sin fijar. |
| **ACT-09** | `Discrepancias y decisiones` | D-UC04-01, D-UC04-02 y D-UC04-05 en «Decidida» → «Aplicada»; D-UC04-01 reescrita sin la premisa del «contrato describe los ya registrados» (el contrato §3 regla 7 ya recoge D-17) y D-UC04-05 con la nota de que el puerto está implementado desde UC03 | Cerrar decisiones ya resueltas por el contrato vigente y por el merge de UC03. |
| **ACT-10** | `Estrategia de testing` y descripción de las pruebas CE | Nombres y rutas originales → los reales (tabla de niveles, clases de prueba y nombres de los métodos `ce00X_…`) y dobles en `support/` | Convención de pruebas de los planes UC01/UC02/UC03/UC11; los nombres originales apuntaban a clases inexistentes. |
| **ACT-11** | `Implementation Phases` y `Estrategia de testing` | Todas las casillas en `[ ]` → `[x]` al cerrar la implementación; T002 redactada (perfil `dev`/`prod` con el datasource y `CorrelationIdFilter`), T010 con `reservationId` en `ReservationValueResult`, T006/T007 con su estado real, la nota de cierre de las fases, la puerta JaCoCo de UC04 y el resultado de `./mvnw clean verify` en T028 | Dejar constancia del estado final y de los ajustes detectados al ejecutar. |
| **ACT-12** | `Notes` y `Checklist de auto-revisión` | Ítems sin marcar → `[x]`; determinismo y firmas de UC03/UC03 reescritos para no contradecir el cuerpo; se añaden las notas de bloqueo (adquirido en el servicio y re-adquirido en el adaptador, reentrante en la misma transacción), `MoneySerializer` para la salida a 2 decimales, las vías defensivas probadas con doble y la nota de que `E1` solo aplica en su vertiente `415` | Cierre de la implementación y registro de lo no evidente en el plan original. |

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir la solicitud con el `reservation_id` | `ReservationValueController`, `CalculateReservationValueCommand` | T010, T012 | T013, T014 |
| **RF-002** recuperar la información registrada por UC03 | `CalculateReservationValueService` (vía `ReservationInformationRepository.findByReservationId`) | T005, T011 | T014, T015 |
| **RF-003** alquiler = tarifa base × días inclusivos | `ReservationValueCalculator` | T008 | T009, T022 (CE-001) |
| **RF-004** seguro = tarifa de seguro × pasajeros | `ReservationValueCalculator` | T008 | T009, T022 |
| **RF-005** depósito = 10 % de la tarifa diaria vigente registrada, sin multiplicadores | `ReservationValueCalculator` | T008 | T009, T022 |
| **RF-006** total = alquiler + seguro + depósito (monto de UC05) | `ReservationValueCalculator`, `CalculateReservationValueService` | T008, T011 | T022, T025 (CE-004) |
| **RF-007** devolver el desglose completo | `ReservationValueResponse`, `ReservationValueController` | T012 | T013 |
| **RF-008** congelar la referencia de versión, actualizar la información interna e invalidación | `frozen_parameters_id` (UUID), transición `ReservationInformation.withCalculated(...)`, `saveCalculatedAmounts`, `CalculateReservationValueService` | T006, T007, T011 | T015, T016, T025 (CE-004) |
| **RF-009** error controlado si no hay información registrada | `CalculateReservationValueService`, `GlobalExceptionHandler` (núcleo compartido) | T011, T012 | T017, T024 (CE-003) |
| **RNF-001** DTOs para la comunicación | `CalculateReservationValueCommand`, `ReservationValueResult`, `ReservationValueResponse` | T010, T012 | T013 |
| **RNF-002** `BigDecimal` con precisión interna de 4 decimales | `Money` (VO del núcleo compartido), `ReservationValueCalculator` | T006, T008 | T009, T022 |
| **RNF-003** manejo de errores robusto (info ausente/incompleta, seguro no configurado) | `CalculateReservationValueService` + `FailureRecorderPort` + `GlobalExceptionHandler` (núcleo compartido) | T004, T011, T012 | T014, T018, T024 |
| **CE-001** precisión: total = suma exacta, cero errores de redondeo | `ReservationValueCalculator` + aceptación | T008 | **T022 `ce001_…`** |
| **CE-002** cálculo solo desde la información registrada (sin Flota / tarifa dinámica) | `ReservationValueCalculator`, `CalculateReservationValueService` + ArchUnit | T008, T011, T026 | **T023 `ce002_…`** |
| **CE-003** sin información registrada → error controlado | `CalculateReservationValueService`, `GlobalExceptionHandler` (núcleo compartido) | T011, T012 | **T024 `ce003_…`** |
| **CE-004** desglose disponible internamente para "Procesar cobro" | `saveCalculatedAmounts` + integración | T007, T011 | **T025 `ce004_…`** |
| **HU1** (P1) | Fases 3–5 | T010–T028 | T013–T028 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.2 (parent del `pom.xml`) **[ACT-01]**. Para UC04: Spring Web MVC, Validation, Jackson, Spring Data JPA, MapStruct, ArchUnit y Testcontainers PostgreSQL. Las dependencias se documentan en el setup común del repositorio, sin editar el `pom.xml` en silencio.
**Storage**: PostgreSQL 16+ — **escritura/lectura** de `reservation_information` (tabla de UC03/UC04; UC03 la crea y UC04 la completa) y **solo lectura de `financial_parameters`** (tabla versionada de UC11; la versión vigente o la congelada se resuelve por `findById`, sin join). `NUMERIC(18,4)` para dinero y porcentajes, `timestamptz` para instantes [SPEC general-plan §4]. **Sin tablas propias ni migraciones** (rige la migración inicial única, general-plan D-31)
**Messaging**: No aplica (REST síncrono; sin RabbitMQ en UC04) [contrato UC04]
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (PostgreSQL), ArchUnit [CONV]; los fallos se prueban con un doble `InMemoryFailureRecorder` del puerto compartido
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: Sin meta propia: se heredan los RNF del SPEC y los límites del plan general. Operación puntual síncrona, sin SLA específico **[CONV]**
**Constraints**: la precisión interna es de 4 decimales y las salidas monetarias se redondean `HALF_UP` a 2 decimales (`contracts/README.md` §3.1); el cálculo se basa **exclusivamente** en la información registrada por UC03, sin recalcular ni volver a consultar la tarifa base ni la dinámica (CE-002); parámetros congelados por reserva mediante la **referencia a la versión usada** (RF-008, D-27): los valores aplicados se resuelven de esa versión y **no se copian**; sin cálculos ni cambios parciales ante cualquier error (RNF-003, principio rector 4); una sola transacción por solicitud
**Scale/Scope**: 1 endpoint REST (`POST /api/v1/reservations/{reservation_id}/calculated-value`) [contrato UC04]; 9 RF + 3 RNF + 4 CE + 1 HU del SPEC 04; 1 puerto de entrada (`CalculateReservationValueUseCase`) + 1 operación del puerto de salida de UC03 ya existente (`saveCalculatedAmounts`, aportada por UC03) + 2 piezas consumidas por nombre (UC03, UC11) **[ACT-03]**

**Estado actual del repositorio (relevante para UC04)** **[ACT-02]**: el núcleo compartido y UC01, UC02, UC03 y UC11 están implementados y verificados con `./mvnw clean verify`. Existen los paquetes `domain`/`application`/`infrastructure`, la migración única `V1__initial_schema.sql` (con `reservation_information`, `financial_parameters` y `operational_failure`), el datasource en los perfiles `application-dev`/`application-prod` (el perfil por defecto es `dev`) y los dobles compartidos en `src/test/java/com/seashare/seasharem3/support/`. **UC04 queda implementado en esta actualización**: dominio (`ReservationValue`, `ReservationValueCalculator`), aplicación (`CalculateReservationValueUseCase`, `CalculateReservationValueCommand`, `ReservationValueResult`, `CalculateReservationValueService`), adaptador de entrada (`ReservationValueController`, `ReservationValueResponse` y el wiring en `CalculateReservationValueConfiguration`), las tres excepciones de dominio y las pruebas de dominio, de servicio, de contrato, de arquitectura y de aceptación. Se conservan del merge de UC03 `ReservationInformation.withCalculated(...)` y `ReservationInformationRepository.saveCalculatedAmounts(...)`, con el bloqueo asesor del adaptador.

## Project Structure

### Documentation (this feature)

```text
docs/features/004-solicitar-el-valor-calculado-de-la-reserva/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                        # Leyenda, formatos, Problem Details, catálogo §3.4, tabla de decisión §4
└── rest/
    └── UC04-valor-calculado-reserva.md              # POST /api/v1/reservations/{reservation_id}/calculated-value (HU1)
```

Contratos/piezas de otros planes (solo referencia; **no se re-planifican**): plan de UC03 (`ReservationInformation`, `ReservationId`, `ReservationInformationRepository` y el esquema de la tabla en la migración inicial única) y núcleo compartido (`FinancialParameters`, `FinancialParametersRepository`, `ProblemDetailsConfig`, `DomainException`, `FailureRecorderPort`, `operational_failure`).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── model/
│   │   └── ReservationInformation.java              # de UC03 — consumido; `withCalculated` ya existe (merge de UC03) **[ACT-03]**
│   ├── valueobject/
│   │   ├── ReservationId.java                       # VO del núcleo compartido (UC11·T007); consumido por nombre **[ACT-03]**
│   │   ├── Money.java                               # VO del núcleo compartido (UC11·T007); consumido por nombre [general-plan §11, D-11] **[ACT-03]**
│   │   ├── Percentage.java                          # VO del catálogo compartido; consumido por nombre
│   │   └── ReservationValue.java                    # T006 [SPEC "DesgloseValorReserva"; nombre [CONV]]
│   ├── service/
│   │   └── ReservationValueCalculator.java          # T008 [SPEC RF-003…RF-006, RNF-002; general-plan §3.3]
│   └── exception/
│       ├── DomainException.java                     # del núcleo compartido
│       ├── ReservationInfoNotFoundException.java    # T010 [SPEC RF-009; nombre [CONV]]
│       ├── ReservationInfoIncompleteException.java  # T010 [SPEC RNF-003/casos extremos]
│       └── FinancialParametersNotConfiguredException.java  # T010 [SPEC casos extremos]
├── application/
│   ├── port/in/
│   │   └── CalculateReservationValueUseCase.java    # T010 [general-plan §3.5]
│   ├── port/out/
│   │   ├── ReservationInformationRepository.java    # de UC03 — ya ampliado con saveCalculatedAmounts (implementado desde el merge de UC03) **[ACT-03]**
│   │   ├── FinancialParametersRepository.java       # puerto compartido — consumido por nombre [general-plan §4]
│   │   └── FailureRecorderPort.java                 # puerto compartido, consumido por nombre
│   ├── service/
│   │   └── CalculateReservationValueService.java    # T011 [general-plan §3.5]
│   └── dto/
│       ├── CalculateReservationValueCommand.java    # T010 [SPEC RNF-001; nombre [CONV]]
│       └── ReservationValueResult.java              # T010 [SPEC RNF-001; nombre [CONV]]
└── infrastructure/
    ├── adapter/in/web/
    │   ├── ReservationValueController.java          # T012 [CONV]
    │   └── dto/
    │       └── ReservationValueResponse.java        # T012 [contrato UC04; snake_case, montos string 2 dec]
    ├── adapter/out/persistence/reservation/
    │   └── ReservationInformationPersistenceAdapter  # T007/T016: saveCalculatedAmounts YA implementada en el adaptador
    │                                                 # de UC03 (merge; misma pieza, sin duplicar) **[ACT-03]**
    └── config/
        └── CalculateReservationValueConfiguration.java  # wiring del servicio y del calculator (T011) **[ACT-03]**

src/test/java/com/seashare/seasharem3/
├── arch/
│   ├── ArchitectureTest.java                        # T002 (base, ya existente)
│   └── Uc04ArchitectureTest.java                    # T026 (reglas §3.4 específicas + CE-002) **[ACT-03]**
├── domain/valueobject/
│   └── ReservationValueTest.java                    # T009 [CONV núcleo: la transición recibe UUID, no el VO]
├── domain/service/
│   └── ReservationValueCalculatorTest.java          # T009
├── application/service/
│   ├── CalculateReservationValueServiceTest.java    # T014, T018, T020
│   └── ReservationValueAcceptanceTest.java          # T022 (ce001), T023 (ce002), T024 (ce003), T025 (ce004)
├── contract/
│   └── UC04CalculatedValueContractTest.java         # T013 (200/400/correlación), T017 (404/422/503/500 y orden) **[ACT-03]**
├── integration/
│   ├── ReservationValueConcurrencyIntegrationTest.java  # T019 (Testcontainers PostgreSQL) **[ACT-03]**
│   └── ReservationValuePersistenceAcceptanceTest.java   # T015, T016, T025 (Testcontainers PostgreSQL) **[ACT-03]**
├── infrastructure/adapter/out/persistence/reservation/
│   └── ReservationInformationPersistenceAdapterTest.java y ReservationInformationPersistenceAdapterIntegrationTest.java  # T015/T016: idempotencia y bloque informativo **[ACT-03]**
└── support/
    └── InMemoryFailureRecorder.java                 # de UC02 — reutilizado por nombre con InMemoryReservationInformationRepository, InMemoryFinancialParametersRepository y NoOpReservationLockPort **[ACT-03]**
```

`ReservationInformation` y `ReservationInformationRepository` son de **UC03** (dueño de `reservation_information`; UC03 la crea, UC04 la completa); `ReservationId` es un VO del núcleo compartido (UC11·T007). `FinancialParameters`/`FinancialParametersRepository`, `Money`, `Percentage`, `FailureRecorderPort` y `operational_failure` pertenecen al núcleo/catálogo compartido y se consumen por nombre; UC04 no los implementa ni redeclara. **[ACT-03]**

**Firmas referenciadas por otros planes** [general-plan §3.5]: UC04 **no expone puertos de entrada a otros planes** (`CalculateReservationValueUseCase` lo consume únicamente su adaptador REST). Como completador de `reservation_information`, UC04 usa la operación del puerto de salida `ReservationInformationRepository` (cuyo dueño es UC03) que guarda los montos calculados sin anular el bloque informativo; esa operación **ya estaba implementada** al llegar aquí (merge de UC03). Firma publicada [CONV] **[ACT-03]**:

```java
public interface ReservationInformationRepository {
    // --- puerto definido por UC03 (upsert con invalidación del bloque informativo) ---
    Optional<ReservationInformation> findByReservationId(ReservationId reservationId);
    ReservationInformation save(ReservationInformation information);

    // --- UC04 la consume: completa montos + frozen_parameters_id + calculated_at (ya implementado por UC03) ---
    //   NO anula el bloque informativo; transacción única con pg_advisory_xact_lock(reservationId);
    //   debe ejecutarse sobre la fila ya existente (si no existe, el servicio no la crea: responde 404).
    ReservationInformation saveCalculatedAmounts(ReservationInformation information);
}
```

Además, el dominio `ReservationInformation` (UC03) ya incorpora un **método de transición** para construir la versión con montos calculados (T007, aportado por el merge de UC03):

```java
ReservationInformation withCalculated(ReservationValue value,
                                      UUID frozenParametersId,
                                      Instant calculatedAt);
```

[CONV núcleo, congelado] `ReservationInformation` persiste únicamente la **referencia** `frozen_parameters_id` (UUID, FK a `financial_parameters`); no se copian en la reserva los valores aplicados. Quien resuelve esa versión es `CalculateReservationValueService`, que recibe un `FinancialParameters` de `FinancialParametersRepository.findById(...)` (recálculo) o `findCurrent()` (primer cálculo) y calcula con él (D-27). El VO compartido `FrozenParameters` (`parametersId`, `commissionPct`, `insuranceFeePerPassenger`, general-plan §11) **no se usa en UC04**: queda como representación del §11 para quien lo necesite. La transición congelada recibe solo el UUID, no el VO. **[ACT-04]**

Los demás planes siguen leyendo `reservation_information` **por nombre**; el consumo de UC05 es de lectura directa de las columnas calculadas, sin recalcular (CE-004).

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) en un solo módulo Maven, con subpaquetes temáticos sin reglas entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas del §3.4 aplicadas aquí: `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `application.port.in`; `infrastructure.adapter.out` solo implementa `application.port.out`; ningún controller accede a un repositorio; la entidad JPA no sale de `adapter.out.persistence`; los DTOs HTTP viven en `infrastructure.adapter.in` y `application` trabaja con `command`/`result` (RNF-001). Identificadores en inglés (D-23) y lenguaje ubicuo §11 (*InformaciónDeReserva → `ReservationInformation`*, *Depósito de garantía → `GuaranteeDeposit`*, *Parámetros congelados → `frozen_parameters_id` como referencia y VO compartido `FrozenParameters` para los valores resueltos*). `DesgloseValorReserva` y `SolicitudValorReserva` del SPEC 04 no están en §11 → `ReservationValue` (dominio) / `ReservationValueResult` (aplicación) y `CalculateReservationValueCommand` (aplicación) **[CONV, D-UC04-07]**.

## Reglas de negocio

Todas las reglas provienen del SPEC 04 (`spec.md`), su contrato o el `general-plan.md`, salvo indicación. Ejemplo base del contrato UC04: `reservation_id = b7d0e2a1-…`, `base_rate = 300000.00`, reserva del día 10 al día 12 (3 días), `insurance_fee_per_passenger = 15000.00`, `passengers = 4`, `commission_pct = 20.00`.

1. **Solicitud** [SPEC RF-001; contrato §2]: `POST /api/v1/reservations/{reservation_id}/calculated-value`, **sin cuerpo**; la reserva se identifica solo por su identificador (UUID v4). Formato inválido ⇒ `400 VALIDATION_ERROR` (E2).
2. **Recuperar la información registrada** [SPEC RF-002]: se lee la `InformaciónDeReserva` de UC03 (`findByReservationId`). Si no existe ⇒ `404 RESERVATION_INFO_NOT_FOUND` (`retryable: true`: carrera con UC03) [SPEC RF-009; contrato §5].
3. **Días inclusivos** [SPEC RF-003, HU1; contrato regla 2]: `dias = end_date − start_date + 1`; `start_date == end_date` ⇒ 1 día; del día 10 al 12 ⇒ 3 días. UC03 garantiza `end_date >= start_date`, por lo que `dias >= 1` (no se revalida).
4. **Monto de alquiler** [SPEC RF-003, HU1]: `rental_amount = base_rate registrada × dias`. Es el precio **bruto** de uso de la embarcación; **no** incluye seguro, depósito ni comisión. Ejemplo: `300000.00 × 3 = 900000.00`.
5. **Monto del seguro náutico** [SPEC RF-004]: `insurance_amount = insurance_fee_per_passenger × passengers` (tarifa de la versión de parámetros usada: la vigente en el primer cálculo, la referenciada en los recálculos). Ejemplo: `15000.00 × 4 = 60000.00`.
6. **Depósito de garantía** [SPEC RF-005, HU1; general-plan §3.5, D-29]: el depósito es el **10 % de la tarifa diaria vigente registrada** (la misma del alquiler, con la tarifa dinámica ya resuelta), **sin** multiplicar por duración, pasajeros ni daño; no consulta Flota ni recalcula la tarifa base [SPEC CE-002].
7. **Valor total** [SPEC RF-006; contrato regla 5]: `total_amount = rental_amount + insurance_amount + guarantee_deposit`; es el monto que "Procesar cobro" (UC05) cobra al arrendatario. Ejemplo: `900000.00 + 60000.00 + 30000.00 = 990000.00`.
8. **Precisión y redondeo** [SPEC RNF-002; `contracts/README.md` §3.1]: UC04 usa `BigDecimal` con **4 decimales internos**; toda salida monetaria se redondea `HALF_UP` a **2 decimales**. La prueba CE-001 verifica esa política.
9. **Congelar la referencia de versión** [SPEC RF-008; general-plan D-27]: en el **primer cálculo** se lee la versión vigente (`findCurrent()`, la de mayor `version_number`) y se registra su **referencia** en `frozen_parameters_id` (FK a la fila usada); en los **recálculos** se resuelve esa versión referenciada con `findById(...)`, por lo que una versión vigente más nueva no cambia nada. Los valores aplicados (**comisión %** y **tarifa de seguro por pasajero**) **no se copian**: se resuelven de esa versión (la comisión % la usa UC10 en la liquidación; el seguro, este cálculo). No es retroactivo. **[ACT-05]**
10. **Actualizar la información interna** [SPEC RF-008; contrato regla 6]: se escriben en `reservation_information` los cuatro montos, la referencia `frozen_parameters_id` y `calculated_at`, dejando el desglose disponible para UC05 **sin recalcular** (CE-004). La escritura ocurre en una **única transacción**; no se anula el bloque informativo (fechas, tarifa base, pasajeros, propietario, capacidad).
11. **Invalidación por UC03** [SPEC RF-008, casos extremos; UC03 casos extremos]: si UC03 vuelve a registrar la reserva, su *upsert* deja los montos calculados, `frozen_parameters_id` y `calculated_at` en `NULL`; UC04 **debe recalcular** antes de un nuevo cobro. UC04 siempre recalcula en cada solicitud, de modo que tras una invalidación una nueva llamada regenera el desglose con la versión vigente en ese momento (y su referencia).
12. **Solicitud repetida / determinismo** [SPEC casos extremos; general-plan D-17]: el cálculo se hace desde la misma información registrada y la **versión de parámetros referenciada** (`frozen_parameters_id`; la vigente solo si no hay referencia), por lo que repetir la solicitud devuelve el mismo desglose y la actualización interna es idempotente (sobrescribe con valores equivalentes). El contrato §3 regla 7 ya describe exactamente ese comportamiento (D-UC04-01, confirmada). **[ACT-05]**
13. **Sin consultar Flota ni recalcular la tarifa** [SPEC CE-002; general-plan §3.5]: `CalculateReservationValueService` y `ReservationValueCalculator` **no** dependen de `ProvideBaseRateUseCase`, `FleetRatePort` ni `DynamicRatePolicy`; solo usan la `base_rate` registrada.
14. **Información registrada incompleta** [SPEC RNF-003, casos extremos; contrato regla 10]: si la fila existe pero no permite calcular (p. ej. `base_rate` nula), ⇒ `422 RESERVATION_INFO_INCOMPLETE` (E6), **sin** cálculo parcial. Es una vía **defensiva** de integridad: el diseño de UC03 (columna `base_rate NOT NULL`) garantiza que no se persista información incompleta (D-UC04-02).
15. **Parámetros no configurados** [SPEC casos extremos; contrato regla 11]: si no existe **versión vigente** de `financial_parameters` (primer cálculo) o la versión resuelta no tiene `insurance_fee_per_passenger`/`commission_pct`, ⇒ `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` (E4); **no** se calcula ni el seguro ni un total parcial y **no** se escribe nada. Un recálculo con referencia siempre resuelve la versión referenciada (FK; la tabla es append-only). La ausencia de la **tarifa de seguro** es la que exige el contrato regla 11; la de la **comisión** es una comprobación defensiva propia, **inalcanzable con datos reales** (invariante de `FinancialParameters` y columna `NOT NULL`), que se prueba con un doble. **[ACT-05]**
16. **Registro de fallos** [SPEC casos extremos; D-UC04-06]: los casos 422 (información incompleta) y 503 (parámetros no configurados) **registran el fallo** en `operational_failure` vía `FailureRecorderPort` (por nombre, D-UC04-08), porque el SPEC 04 lo exige textualmente: `use_case = "UC04"`, `reservation_id` de la solicitud, `reason = <código del catálogo>` (`RESERVATION_INFO_INCOMPLETE`/`FINANCIAL_PARAMETERS_NOT_CONFIGURED`) y `payload_ref = "reservationId=<uuid>"`. El `404` **no** registra: es un estado transitorio esperado (Reservas puede reintentar; carrera con UC03). **[ACT-08]**
17. **Orden de validación** [CONV; README §4.3/§4.5]: `E1 → E2 → E3 → E4 → E6` (sin filas de autenticación: seguridad fuera de alcance; `E7` sin uso). La existencia de la reserva (E3) se evalúa antes de leer los parámetros (E4) y de comprobar la completitud (E6); una petición mal formada (E2) nunca llega a la base de datos. `E5` **no aplica** (UC04 no llama a ninguna dependencia externa) y de `E1` solo la vertiente `415`: la petición **no tiene cuerpo**, de modo que `Content-Type` no es obligatorio (`contracts/README.md` §3.2) y el endpoint no responde `415` (sí lo harían `405`/`406` si el método o el `Accept` no se soportan). **[ACT-08]**
18. **Concurrencia y atomicidad** [general-plan D-15]: la lectura-modificación-escritura se ejecuta en una **transacción única** (`@Transactional` del servicio, permitido en `application` por §3.4-2) y se serializa con un **bloqueo asesor de PostgreSQL por `reservation_id`** (`pg_advisory_xact_lock`) que el servicio adquiere **antes de leer** y que el adaptador vuelve a adquirir al escribir (bloqueo reentrante en la misma transacción), de modo que un *upsert* concurrente de UC03 no invalida la fila entre la lectura y la escritura ni pierde su propia invalidación. Un fallo en cualquier paso deja la fila intacta (ni montos nuevos ni invalidación parcial). **[ACT-06]**
19. **Sin persistencia propia ni migración** [general-plan §3.3/§4.2]: UC04 no crea tablas ni archivos Flyway; escribe columnas ya existentes de `reservation_information` (migración inicial única, general-plan D-31). Repetir la petición no añade filas.
20. **Seguridad fuera de alcance** [general-plan §7.1, D-21]: el sistema **no implementa** autenticación ni autorización; los contratos ya no declaran `401 UNAUTHENTICATED` / `403 FORBIDDEN` [SPEC RF-001]. "Solo el Sistema de Reservas y Operaciones" describe al usuario esperado, no una validación: esta fase **no implementa** `SecurityConfig` ni pruebas 401/403.

## Esquema (migración única)

**UC04 no define migraciones.** No crea tablas ni archivos Flyway: `reservation_information` forma parte de la migración inicial única `V1__initial_schema.sql` (general-plan §4.2, D-31). La tabla debe contener las columnas que UC04 escribe [general-plan §4]:

| Columna | Tipo | Origen | Uso en UC04 |
|---|---|---|---|
| `rental_amount` | `NUMERIC(18,4)` | Migración inicial única (general-plan §4) | `UPDATE` (monto de alquiler) |
| `insurance_amount` | `NUMERIC(18,4)` | Migración inicial única (general-plan §4) | `UPDATE` (seguro náutico) |
| `deposit_amount` | `NUMERIC(18,4)` | Migración inicial única (general-plan §4) | `UPDATE` (depósito de garantía) |
| `total_amount` | `NUMERIC(18,4)` | Migración inicial única (general-plan §4) | `UPDATE` (valor total) |
| `frozen_parameters_id` | `UUID` | Migración inicial única (general-plan §4) | `UPDATE` (FK: referencia a la versión usada de `financial_parameters`; única fuente de los valores aplicados) |
| `calculated_at` | `TIMESTAMPTZ` | Migración inicial única (general-plan §4) | `UPDATE` (instante del cálculo) |

La operación de UC04 sobre la tabla es una lectura-modificación-escritura JPA dentro de una **transacción única** serializada con `pg_advisory_xact_lock(reservation_id)` (el bloqueo se adquiere en el servicio **antes** de leer y el adaptador lo re-adquiere al escribir; el `SELECT … FOR UPDATE` no se usa), **sin** tocar el bloque informativo. El adaptador de UC03 ya anula estas columnas (incluida `frozen_parameters_id`) en su *upsert* (invalidación, `UC03·T015`); UC04 las completa con los montos, la **referencia a la versión usada** (`frozen_parameters_id`) y `calculated_at`. Los valores aplicados **no se escriben**: se resuelven de la versión referenciada en `CalculateReservationValueService` mediante `FinancialParametersRepository.findById` (sin join; D-27). No hay índices ni columnas nuevas. **[ACT-06]**

## Contratos

Fuente: `contracts/rest/UC04-valor-calculado-reserva.md` y `contracts/README.md` §3. El SPEC 04 **no** fija la ruta: rige el contrato.

### `POST /api/v1/reservations/{reservation_id}/calculated-value`

**Petición**: sin cuerpo. Headers [README §3.2]: `Accept` (opcional), `X-Correlation-Id` (opcional; se genera si falta y se devuelve). No hay cabecera de autenticación (seguridad fuera de alcance; general-plan §7.1).

| Parámetro de ruta | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `reservation_id` | UUID v4 | Sí | Identificador de la reserva [SPEC RF-001] |

**200 OK** — `Content-Type: application/json` (ejemplo del contrato; montos string decimal a 2 decimales):

```json
{
  "reservation_id": "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10",
  "rental_amount": "900000.00",
  "insurance_amount": "60000.00",
  "guarantee_deposit_amount": "30000.00",
  "total_amount": "990000.00"
}
```

*(Ilustración: `base_rate` 300000.00; reserva del día 10 al 12 = 3 días; seguro 15000.00 × 4 pasajeros; depósito 10 % de 300000.00.)*

### Respuestas de error

| HTTP | `code` | Cuándo | `retryable` | Definición |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `reservation_id` no es un UUID v4 válido | No | [CONV] E2 |
| 404 | `RESERVATION_INFO_NOT_FOUND` | No existe información registrada para esa reserva | **Sí** (carrera con UC03) | [SPEC RF-009] E3 |
| 422 | `RESERVATION_INFO_INCOMPLETE` | La información registrada no permite calcular (p. ej. sin tarifa base) | No | [SPEC RNF-003] E6 |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Primer cálculo (sin referencia): no existe versión vigente de parámetros, o falta la tarifa de seguro (valor nulo); en la versión referenciada, si faltara también la comisión (vía defensiva, no alcanzable con datos reales) | Sí (cuando el Administrador Financiero configure) | [SPEC casos extremos] E4 |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] E8 |

```json
{
  "type": "about:blank",
  "title": "Reservation information not found",
  "status": 404,
  "detail": "There is no registered information for reservation b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10.",
  "code": "RESERVATION_INFO_NOT_FOUND",
  "retryable": true
}
```

*(Ejemplo tomado del contrato §5: el `title` lo fija `ErrorCode` (el contrato §5 y `contracts/README.md` §3.3) y `detail` proviene del mensaje de la excepción; el plan publicaba antes un ejemplo en español que no es el que devuelve el sistema.)* **[ACT-07]**

**Mapeo de excepciones** (lo implementa `GlobalExceptionHandler`, pieza del núcleo compartido; UC04 no declara advice propio):

| Origen (dominio/aplicación) | HTTP / `code` |
|---|---|
| `ReservationInfoNotFoundException` | 404 `RESERVATION_INFO_NOT_FOUND` (`retryable: true`) |
| `ReservationInfoIncompleteException` | 422 `RESERVATION_INFO_INCOMPLETE` |
| `FinancialParametersNotConfiguredException` | 503 `FINANCIAL_PARAMETERS_NOT_CONFIGURED` |
| UUID de ruta inválido (framework) | 400 `VALIDATION_ERROR` |
| Excepción no controlada | 500 `INTERNAL_ERROR` |

Todos los errores usan *Problem Details* (RFC 9457) con `code` y `retryable`, y `X-Correlation-Id` (§3.3). **Orden de validación**: E1 → E2 → E3 → E4 → E6 (E5 no aplica: sin dependencia externa; E7 sin uso; E8 puede ocurrir en cualquier paso).

### Idempotencia y reintentos

Idempotente: devuelve siempre el mismo desglose mientras la información de la reserva no cambie (usa la versión de parámetros referenciada; la vigente solo si no hay referencia); la actualización interna sobrescribe con valores equivalentes [SPEC casos extremos; general-plan D-17]. `RESERVATION_INFO_NOT_FOUND` es reintentable si UC03 aún está en cola (carrera con UC03). `FINANCIAL_PARAMETERS_NOT_CONFIGURED` es reintentable cuando el Administrador Financiero configure los parámetros.

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), contrato UC04 y SPEC 04. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %; la puerta JaCoCo de UC04 incluye `domain/service/*` (el calculator), `domain/valueobject/ReservationValue` y `application/service/CalculateReservationValueService`. Las pruebas de fallos usan `InMemoryFailureRecorder` (de UC02, en `support/`) como doble de `FailureRecorderPort` (D-UC04-08), junto con `InMemoryReservationInformationRepository`, `InMemoryFinancialParametersRepository` y `NoOpReservationLockPort`. **[ACT-10] [ACT-11]**

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | `ReservationValue` (la referencia congelada es `UUID frozenParametersId`; el VO `FrozenParameters` no se usa en UC04), fórmula (RF-003–RF-006), días inclusivos, depósito sin multiplicadores, precisión y redondeo a 2 dec (RNF-002, D-UC04-04) | `domain/valueobject/ReservationValueTest`, `domain/service/ReservationValueCalculatorTest` |
| Unitario (`application`) | orquestación: 404 sin fila, 422 incompleta, 503 parámetros ausentes/nulos, orden E3→E4→E6, bloqueo antes de leer, escritura única, registro de fallos, determinismo (D-17) | `CalculateReservationValueServiceTest` (Mockito sobre los tres puertos + `NoOpReservationLockPort` + `java.time.Clock` fijo) |
| Contrato / Web (MockMvc) | cuerpo y códigos del contrato: 200 feliz, `X-Correlation-Id`, 400 sin invocar el caso de uso, 404 `retryable`, 422, 503, 500 | `contract/UC04CalculatedValueContractTest` **[ACT-10]** |
| Integración (Testcontainers PostgreSQL) | `saveCalculatedAmounts` escribe montos + `frozen_parameters_id` + `calculated_at` y preserva el bloque informativo; repetición idempotente; una nueva versión de parámetros globales no altera la referencia ni los valores resueltos; tras invalidación de UC03, UC04 recalcula; bloqueo asesor serializa UC04/UC03 | `integration/ReservationValuePersistenceAcceptanceTest`, `integration/ReservationValueConcurrencyIntegrationTest`, `ReservationInformationPersistenceAdapterTest` y `ReservationInformationPersistenceAdapterIntegrationTest` **[ACT-10]** |
| Aceptación | CE-001…CE-004 (sin Docker) | `application/service/ReservationValueAcceptanceTest` |
| Arquitectura | reglas §3.4 (base) y CE-002 (sin Flota/UC02/tarifa dinámica) | `arch/ArchitectureTest` + `arch/Uc04ArchitectureTest` **[ACT-10]** |

**Pruebas de aceptación (CE-001…CE-004)** — nóminal `ce00X_<descripcion>`:

- **`ce001_totalIsExactlyTheSumOfTheThreeAmountsWithoutRoundingErrors`** (CE-001, T022; `ReservationValueAcceptanceTest`): prueba parametrizada con el ejemplo del contrato y casos con tarifas/seguros de 4 decimales, distintos días inclusivos y pasajeros; verifica con `BigDecimal` que `total_amount == rental_amount + insurance_amount + guarantee_deposit_amount` exactamente y que cada monto quedó a 2 decimales HALF_UP, con cero discrepancias. **[ACT-10]**
- **`ce002_calculationUsesTheRegisteredRateWithoutQueryingFlota`** (CE-002, T023): (a) ArchUnit en `Uc04ArchitectureTest` — UC04 no depende de `ProvideBaseRateUseCase`, `FleetRatePort`, `DynamicRatePolicy` ni `PricingCalculator`; (b) `ReservationValueAcceptanceTest` con WireMock como Flota (UC02/UC03 reales) — **cero** peticiones a Flota durante UC04 y el cambio de tarifa en el stub no altera el desglose (se usa la `base_rate` registrada). **[ACT-10]**
- **`ce003_unknownReservationIsAnsweredWithAControlledErrorAndNoPartialCalculation`** y **`ce003_missingParametersAreRecordedAndNothingIsCalculated`** (CE-003, T024; `ReservationValueAcceptanceTest`): sin información registrada → `404` controlado sin cálculo parcial ni registro de fallo; sin versión de parámetros → `503` **registrado** y sin escritura. El `422` (vía defensiva, D-UC04-02) se prueba con un doble en `CalculateReservationValueServiceTest` y los *Problem Details* completos en `contract/UC04CalculatedValueContractTest`. **[ACT-10]**
- **`ce004_breakdownIsPersistedAndReadableForTheChargeWithoutRecalculation`** (CE-004, T025; `ReservationValueAcceptanceTest`) y **`ce004_breakdownIsPersistedWithTheReferencedVersionForTheCharge`** (T015/T025; `integration/ReservationValuePersistenceAcceptanceTest` con Testcontainers): tras un cálculo exitoso, la fila tiene los cuatro montos, `frozen_parameters_id` y `calculated_at`, y una lectura por repositorio (como la haría UC05) obtiene el desglose **sin recalcular**. **[ACT-10]**

Además: la regresión de consumidores **no forma parte de este plan** (UC05 lee la tabla por nombre; aquí solo se deja el desglose persistido).

## Discrepancias y decisiones

Registro de contradicciones detectadas al elaborar este plan y de las decisiones adoptadas para resolverlas. Los IDs `D-UC04-xx` son propios de este plan; los `D-xx` sin prefijo son decisiones del plan general (§9) o de los planes de UC02/UC03.

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC04-01** | La solicitud repetida debe ser determinista e idempotente (el contrato §3 regla 7 ya describe el recálculo con la **versión referenciada**, así que la discrepancia original quedó resuelta a favor de D-17) | general-plan D-17; contrato UC04 §3 regla 7 | UC04 **recalcula siempre** desde la información registrada y la versión de parámetros referenciada (`frozen_parameters_id`; la vigente si no hay referencia); el efecto es determinista e idempotente | **Aplicada** **[ACT-09]** |
| **D-UC04-02** | El SPEC 04 asume que puede existir información registrada incompleta por una falla previa de UC03, pero el plan de UC03 **nunca** persiste información incompleta | SPEC 04 casos extremos vs plan UC03 (columna `base_rate NOT NULL` en el esquema, regla 10 de UC03) | `422 RESERVATION_INFO_INCOMPLETE` queda como vía **defensiva** de integridad; el caso normal de "aún no registrada" es `404`. Se conserva el código por estar en el catálogo y se prueba con un doble | **Aplicada** (vía defensiva; con datos reales no es alcanzable) **[ACT-09]** |
| **D-UC04-03** | La base del depósito de garantía quedó fijada transversalmente | SPEC 04 HU1/RF-005; general-plan D-29 | El depósito es el **10 % de la tarifa diaria vigente registrada** (la misma del alquiler, con la tarifa dinámica ya resuelta); UC04 no consulta Flota ni recalcula la tarifa base | Aplicada |
| **D-UC04-04** | RNF-002 exige precisión interna y el contrato ilustra montos con dos decimales | SPEC 04 RNF-002/CE-001; `contracts/README.md` §3.1 | Política del catálogo aplicada: `BigDecimal` con **4 decimales internos** y toda salida monetaria redondeada `HALF_UP` a **2 decimales**; CE-001 la verifica | Aplicada |
| **D-UC04-05** | La firma publicada por UC03 de `ReservationInformationRepository` no permite guardar montos: su `save` **anula** las columnas calculadas | `general-plan.md` §4 (tabla entidad de UC03/UC04) vs plan UC03 («Firmas referenciadas por otros planes») | UC04 **usa** la operación `saveCalculatedAmounts` que UC03 añadió al mismo puerto (ya implementada en el adaptador de UC03; misma pieza de UC03, sin duplicarla). La firma se publica en «Firmas referenciadas por otros planes». Concurrencia con `pg_advisory_xact_lock(reservation_id)`, adquirido por el servicio antes de leer (regla 18) | **Aplicada** (puerto y adaptador fusionados con UC03; solo se consume) **[ACT-09]** |
| **D-UC04-06** | El SPEC 04 pide "registra el fallo" en los casos de info incompleta y seguro no configurado; no lo pide para "no existe información" | SPEC 04 casos extremos vs ausencia para el `404` | UC04 usa `FailureRecorderPort` por nombre en 422 y 503; el 404 **no** registra (estado transitorio esperado, reintentable). A diferencia de UC01, el SPEC 04 sí lo exige | Decidida |
| **D-UC04-07** | `DesgloseValorReserva`/`SolicitudValorReserva` (SPEC 04) no están en el lenguaje ubicuo §11 | SPEC 04 entidades clave vs general-plan §11 | `ReservationValue`/`ReservationValueResult` (resultado) y `CalculateReservationValueCommand` (solicitud); `InformaciónDeReserva` → `ReservationInformation` (ya en §11) | Decidida **[CONV]** |
| **D-UC04-08** | El registro de fallos es una pieza transversal | `general-plan.md` §3.5/§4 | UC04 consume `FailureRecorderPort` por nombre; no lo implementa ni redeclara. Su registro usa la transacción independiente y el procedimiento de reproceso del catálogo común | Aplicada |
| **D-UC04-09** | El SPEC/contrato fijan *quién* llama (solo Reservas); la seguridad está fuera de alcance | SPEC 04 RF-001; general-plan §7.1 y D-21 | **Seguridad fuera de alcance**: el sistema no implementa autenticación ni autorización; los contratos ya no documentan 401/403 y no hay `SecurityConfig` ni pruebas de seguridad | Aplicada |
| **D-UC04-10** | ¿Se copian los parámetros aplicados en la reserva o basta la referencia a la versión? Detectada redundancia entre `frozen_parameters_id` y las copias `commission_pct_applied`/`insurance_fee_per_passenger_applied` | `general-plan.md` §4/D-27 (versión por FK) vs plan UC03/UC04 (copias) | **Sin copias**: `frozen_parameters_id` es la única fuente; UC03 devuelve la referencia y UC04/UC10 resuelven los valores con `FinancialParametersRepository.findById` al leer el agregado, **sin join** (la lógica de cálculo no cambia: el recálculo usa la misma versión). Alternativa descartada: mantener copias + FK (posible divergencia) | Decidida |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable de forma independiente; su casilla es el mecanismo de seguimiento (todas quedaron en `[x]` al cerrar la implementación, ver la nota inferior). Las dependencias con otros casos de uso se expresan mediante puertos, firmas y tablas, no mediante numeración de tareas externas. **[ACT-11]**

> **Actualización 2026-10-28 [ACT-11]**: al completarse la implementación, todas las tareas `T001`-`T028` se marcaron `[x]`; en el plan original estaban `[ ]`. T001–T007 y T010 estaban ya aportadas por el núcleo compartido y por el merge de UC03.

### Phase 1: Setup

- [x] **T001** · `pom.xml`: verificar las dependencias web, validación, persistencia, test y ArchUnit requeridas, sin editar dependencias en silencio.
- [x] **T002** · `application.properties` con el perfil por defecto `dev` (el datasource vive en `application-dev`/`application-prod`) y `CorrelationIdFilter` (cabecera `X-Correlation-Id`), más la base de `ArchitectureTest` (reglas §3.4) — **ya satisfecho** por UC11/UC01 al llegar a UC04. **[ACT-11]**

### Phase 2: Foundational

- [x] **T003** · `DomainException` (`domain/exception`), base del árbol de excepciones del núcleo compartido.
- [x] **T004** · consumir por nombre `FailureRecorderPort` y `operational_failure`; el registro usa `REQUIRES_NEW`, `reservation_id` nullable, `payload_ref` referencial y procedimiento de reproceso.
- [x] **T005** · consumir por nombre `ReservationInformation`, `ReservationId`, `ReservationInformationRepository`, `FinancialParameters`, `FinancialParametersRepository` y `Money`; UC04 no los implementa ni los redeclara.
- [x] **T006** · VO de dominio `ReservationValue` (`rentalAmount`, `insuranceAmount`, `guaranteeDeposit`, `totalAmount` como `Money`) [SPEC "DesgloseValorReserva"; D-UC04-07]. El VO compartido `FrozenParameters` (general-plan §11) **no se usa en UC04** (los valores se resuelven del `FinancialParameters` que devuelve el repositorio); **no se persiste**: `reservation_information` guarda solo `frozen_parameters_id` (UUID) y la firma congelada de la transición es `withCalculated(ReservationValue, UUID, Instant)`. UC04 resuelve los valores con `FinancialParametersRepository.findById(...)` (D-27). **[ACT-04]**
- [x] **T007** · Extensión del puerto de UC03 (D-UC04-05) — **aportada por el merge de UC03; UC04 solo la consume**: método de transición `ReservationInformation.withCalculated(ReservationValue, UUID, Instant)` y operación `ReservationInformationRepository.saveCalculatedAmounts(...)` (firma publicada en «Firmas referenciadas por otros planes»); el adaptador JPA de UC03 implementa la escritura de los montos + `frozen_parameters_id` + `calculated_at` **sin** anular el bloque informativo, en transacción única con `pg_advisory_xact_lock(reservation_id)`. No escribe copias de parámetros (D-UC04-10). **[ACT-03] [ACT-11]**
- [x] **T008** · `ReservationValueCalculator` (`domain/service`): `ReservationValue calculate(ReservationInformation info, FinancialParameters params)`. Fórmulas de RF-003–RF-006 y días inclusivos; depósito = **10 % de la tarifa diaria vigente registrada** (general-plan D-29), con precisión interna de 4 decimales y salidas monetarias redondeadas `HALF_UP` a 2 decimales (`contracts/README.md` §3.1).
- [x] **T009** · Pruebas unitarias de VOs y del calculator: ejemplo del contrato (`900000.00/60000.00/30000.00/990000.00`), `start == end` (1 día), casos con 4 decimales y redondeo, depósito no multiplicado por días/pasajeros (RF-003–RF-006, RNF-002).
- [x] **T010** · `CalculateReservationValueUseCase` + `CalculateReservationValueCommand` (`ReservationId`) + `ReservationValueResult` (`reservationId` + los cuatro montos, porque el contrato §4 exige `reservation_id` en la respuesta) + excepciones de dominio `ReservationInfoNotFoundException`, `ReservationInfoIncompleteException` y `FinancialParametersNotConfiguredException` [RNF-001; general-plan §3.5]. **[ACT-11]**

### Phase 3: US1 — Flujo feliz de cálculo y persistencia (HU1; RF-001, RF-002, RF-003, RF-004, RF-005, RF-006, RF-007, RF-008; CE-001, CE-004)

- [x] **T011** · `CalculateReservationValueService`: bloqueo asesor de la reserva (regla 18) → `findByReservationId` → E3 (`404` si vacío) → si hay `frozen_parameters_id` (recálculo) resuelve esa versión con `FinancialParametersRepository.findById(...)`; si no, lee `FinancialParametersRepository.findCurrent()` (versión vigente) → E4 (`503` si no existe versión vigente o `insurance`/`commission` nulos, registra fallo) → verifica completitud → E6 (`422` si `base_rate` nula, registra fallo) → `ReservationValueCalculator.calculate(...)` → `saveCalculatedAmounts(...)` en la misma transacción; **no** invoca UC02 ni Flota; `calculated_at` desde un `java.time.Clock` inyectado. **[ACT-06]**
- [x] **T012** · `ReservationValueController` (`POST /api/v1/reservations/{reservation_id}/calculated-value`, sin cuerpo) + `ReservationValueResponse` (claves `snake_case`, montos string a 2 dec). **Sin** advice propio: las excepciones de dominio (`ReservationInfoNotFoundException`, `ReservationInfoIncompleteException`, `FinancialParametersNotConfiguredException`) ya se traducen en el `GlobalExceptionHandler` del núcleo compartido (*Problem Details* con `code`/`retryable` del catálogo §3.4). **Sin** reglas de seguridad — fuera de alcance, general-plan §7.1.
- [x] **T013** · Tests de contrato MockMvc del flujo feliz: `200` con el ejemplo del contrato (4 claves + `reservation_id`), aceptación de `X-Correlation-Id` y `400 VALIDATION_ERROR` con UUID inválido.
- [x] **T014** · Pruebas unitarias del servicio (Mockito + `InMemoryFailureRecorder` + `Clock` fijo): flujo feliz; una sola escritura; sin fila → `404`; info incompleta → `422` + registro; parámetros ausentes/nulos → `503` + registro; **sin llamadas a Flota**.
- [x] **T015** · Pruebas de integración del adaptador (Testcontainers PostgreSQL): `saveCalculatedAmounts` escribe los cuatro montos, `frozen_parameters_id` y `calculated_at`; una solicitud repetida con los mismos insumos produce el mismo desglose (idempotencia; el recálculo usa la versión referenciada, no la vigente); una nueva versión de `financial_parameters` no altera la referencia ni los valores resueltos; la lectura expone `frozenParametersId` y la versión referenciada se resuelve con `FinancialParametersRepository.findById(...)`.
- [x] **T016** · Pruebas de integración: `saveCalculatedAmounts` **preserva** el bloque informativo (fechas, `base_rate`, pasajeros, propietario, capacidad); tras un *upsert* de UC03 (columnas calculadas a `NULL`), una nueva llamada a UC04 recalcula y completa los montos con los parámetros vigentes.

### Phase 4: US1 — Errores, idempotencia y concurrencia (HU1; RF-008, RF-009; RNF-003; CE-002, CE-003)

- [x] **T017** · Tests de contrato de errores (MockMvc): `404 RESERVATION_INFO_NOT_FOUND` con `retryable: true`, `422 RESERVATION_INFO_INCOMPLETE`, `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, `500 INTERNAL_ERROR`. El contrato verifica **E2 antes de E3** (una petición con UUID inválido no invoca el caso de uso); el orden **E3 → E4 → E6** lo verifica `CalculateReservationValueServiceTest` (sin filas de parámetros cuando no hay reserva, sin completitud cuando faltan parámetros). **[ACT-10]**
- [x] **T018** · Registro de fallos (CE-003): con dobles, el `422` y el `503` quedan en `InMemoryFailureRecorder`; el `404` **no** registra (D-UC04-06); en ningún caso se escribe la fila.
- [x] **T019** · Concurrencia (general-plan D-15): `integration/ReservationValueConcurrencyIntegrationTest` (Testcontainers PostgreSQL) — el *upsert* de UC03 (mismo `reservation_id`) queda bloqueado mientras UC04 retiene el bloqueo asesor entre la lectura y la escritura; no hay pérdida de escritura ni invalidación parcial. **[ACT-10]**
- [x] **T020** · Determinismo (D-17): dos solicitudes consecutivas (incluso tras publicar una nueva versión de `financial_parameters` sin invalidación) devuelven el mismo desglose (el recálculo usa la **versión referenciada**) y la fila conserva `frozen_parameters_id` y los montos (D-UC04-01, D-UC04-10).
- [x] **T021** · Manejo del error no controlado → `500 INTERNAL_ERROR` en *Problem Details*, sin detalles internos (README §4.3 regla 9).

### Phase 5: Polish y criterios de éxito — **parcialmente compartido con la Fase general**

- [x] **T022** · **`ce001_totalIsExactlyTheSumOfTheThreeAmountsWithoutRoundingErrors`** (CE-001): parametrizada (ejemplo del contrato + tarifas/seguros de 4 decimales, varios días y pasajeros); el total es exactamente la suma de los tres montos a 2 decimales, cero discrepancias. **[ACT-10]**
- [x] **T023** · **`ce002_calculationUsesTheRegisteredRateWithoutQueryingFlota`** (CE-002): ArchUnit (sin `ProvideBaseRateUseCase`/`FleetRatePort`/`DynamicRatePolicy` en UC04) + `ReservationValueAcceptanceTest` con WireMock de Flota (cero peticiones; cambiar la tarifa del stub no altera el desglose). **[ACT-10]**
- [x] **T024** · **`ce003_unknownReservationIsAnsweredWithAControlledErrorAndNoPartialCalculation`** y **`ce003_missingParametersAreRecordedAndNothingIsCalculated`** (CE-003): reservas sin información registrada → error controlado sin cálculo parcial; parámetros sin versión → `503` registrado; los *Problem Details* y el `422` defensivo se cubren en `contract/UC04CalculatedValueContractTest` y en `CalculateReservationValueServiceTest`. **[ACT-10]**
- [x] **T025** · **`ce004_breakdownIsPersistedAndReadableForTheChargeWithoutRecalculation`** (CE-004): aceptación en memoria y **`ce004_breakdownIsPersistedWithTheReferencedVersionForTheCharge`** en PostgreSQL — tras el cálculo, la fila tiene los cuatro montos + `frozen_parameters_id` + `calculated_at` (los valores aplicados se resuelven de la versión referenciada) y una lectura por repositorio (UC05) obtiene el desglose sin recalcular. **[ACT-10]**
- [x] **T026** · ArchUnit (reglas §3.4 en `arch/ArchitectureTest` + específicas en `arch/Uc04ArchitectureTest`): `domain` sin Spring/JPA/Jackson y `application` solo con `domain` (base compartida); controller solo invoca `application.port.in`, sin entidades JPA fuera de `adapter.out.persistence` y sin dependencias de Flota/UC02/tarifa dinámica en UC04, con guarda contra reglas vacías (CE-002). **[ACT-10]**
- [x] **T027** · Documentación y referencias entre planes: documentar la firma de `ReservationInformationRepository.saveCalculatedAmounts` (§«Firmas referenciadas por otros planes»; `UC03` es el dueño del puerto) y registrar las decisiones `D-UC04-01`, `D-UC04-04`, `D-UC04-08` y `D-UC04-10`. No se edita ningún contrato ni SPEC sin acuerdo.
- [x] **T028** · revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-004: **ejecutado el 2026-10-28 — `Tests run: 1135, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS**, con la puerta JaCoCo de UC04 (`domain/service/*`, `domain/valueobject/ReservationValue`, `application/service/CalculateReservationValueService`) aprobada. **[ACT-11]**

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─┬─> T003 ─> T004 ─┐
      │         │                  │
      │         └──────────────────┴─> T005 ─> T006 ─> T007 ─> T008 ─> T009 ─> T010
      │                                                                    │
      ├──────────────────────────────────────────────────────> T011 ─> T012 ─> T013
      │                                                          │
      │                                                   T014 ─> T015 ─> T016
      │
      ├──────────────────────────────────────────────────> T017 ─> T018 ─> T019 ─> T020 ─> T021
      │
      └──────────────────────────────────────────────────> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027 ─> T028
```

- **Grupo 1 (T001–T010)**: Setup y Foundational de UC04 + extensión del puerto/entidad de UC03 (T007), calculator, command/result y excepciones. T004 consume el catálogo compartido con doble de prueba.
- **US1 flujo feliz (T011–T016)**: servicio → controller → contrato → servicio unitario → integración. Depende de la tabla `reservation_information` (V1, `UC03·T006`) y de `saveCalculatedAmounts` (T007).
- **Errores, idempotencia y concurrencia (T017–T021)**: después del flujo feliz; T019 depende de T007/T011.
- **Polish y CE (T022–T028)**: al final; T023/T026 dependen de T008/T011.
- **Riesgo de secuencia**: la seguridad está **fuera de alcance** (general-plan §7.1; D-UC04-09 aplicada); `saveCalculatedAmounts` (T007) respeta el catálogo único de puertos de UC03 y el mecanismo de bloqueo compartido (`pg_advisory_xact_lock` por reserva; general-plan D-15). **[ACT-12]**

## Notes

- **Cálculo determinista (D-17)**: UC04 recalcula siempre desde la información registrada y la versión de parámetros referenciada (`frozen_parameters_id`; la vigente si no hay referencia), con desglose determinista.
- **Redondeo (`contracts/README.md` §3.1)**: precisión interna de 4 decimales; salidas monetarias redondeadas `HALF_UP` a 2 decimales.
- **Depósito (general-plan D-29)**: 10 % de la tarifa diaria vigente registrada (la misma del alquiler, con la tarifa dinámica ya resuelta).

- El SPEC 04 **no** define una meta de rendimiento propia: rige «sin meta propia» (operación puntual síncrona **[CONV]**). La carrera con UC03 se resuelve con el bloqueo asesor por reserva (D-15) y el `404 RESERVATION_INFO_NOT_FOUND` con `retryable: true` (carrera con UC03). La seguridad está **fuera de alcance** (general-plan §7.1).
- **Determinismo (D-17)**: UC04 recalcula siempre desde la información registrada y la versión de parámetros referenciada (`frozen_parameters_id`; la vigente si no hay referencia); el contrato §3 regla 7 ya describe ese comportamiento (D-UC04-01). Los valores aplicados no se copian en la reserva (D-UC04-10). **[ACT-12]**
- **Redondeo (D-UC04-04)**: 4 decimales internos; las salidas monetarias se redondean `HALF_UP` a 2 decimales (`contracts/README.md` §3.1).
- **Depósito (D-UC04-03)**: 10 % de la tarifa diaria vigente registrada (general-plan D-29); UC04 no reconsulta Flota ni recalcula la tarifa (CE-002).
- **Sin migraciones propias**: UC04 usa las columnas calculadas de `reservation_information`; rige la migración inicial única (general-plan §4.2, D-31).
- `ReservationInformation` y `ReservationInformationRepository` son de **UC03**: UC04 los consume (la operación `saveCalculatedAmounts` ya estaba implementada, D-UC04-05); `ReservationId` es un VO del núcleo compartido. La firma se publica en «Firmas referenciadas por otros planes». **[ACT-12]**
- `FinancialParameters`/`FinancialParametersRepository` pertenecen al núcleo compartido: UC04 los consume por nombre.
- `FailureRecorderPort`/`operational_failure` son **compartidos**: UC04 los referencia por nombre y usa `InMemoryFailureRecorder` en pruebas; registra en 422/503, no en 404 (D-UC04-06).
- **Seguridad fuera de alcance** (general-plan §7.1, D-21; D-UC04-09): el sistema no implementa autenticación ni autorización; no se implementa `SecurityConfig` ni pruebas 401/403 y los contratos ya no documentan `401`/`403`. "Solo el Sistema de Reservas y Operaciones" es el usuario esperado.
- Etiquetas usadas: `[SPEC]` (SPEC 04 y contrato) y `[CONV]` (general-plan e inferido).
- **Bloqueo asesor** (regla 18, D-15): el servicio lo adquiere antes de leer y el adaptador lo re-adquiere al escribir; es reentrante en la misma transacción, por lo que no hay que duplicarlo en ningún otro punto. **[ACT-12]**
- **Salida monetaria**: el controller no formatea: `ReservationValueResponse` lleva `Money` y `MoneySerializer` (`infrastructure/config/web`) emite el string de 2 decimales HALF_UP del contrato. **[ACT-12]**
- **Vías defensivas** (D-UC04-02 y rama de comisión nula del 503): no son alcanzables con datos reales (`base_rate NOT NULL`, invariantes de `FinancialParameters`); se prueban con dobles en `CalculateReservationValueServiceTest`. **[ACT-12]**
- **`E1`/`415` no aplica**: la petición no lleva cuerpo, y `contracts/README.md` §3.2 solo exige `Content-Type` en `POST`/`PUT` con cuerpo. **[ACT-12]**

## Checklist de auto-revisión

> **Actualización 2026-10-28 [ACT-12]**: la revisión final se completó y todas las casillas se marcaron `[x]`; en el plan original estaban `[ ]`. La fecha de referencia del plan original (`2026-10-09`) se conserva en la cabecera.

- [x] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Esquema/Contratos, Estrategia de testing, fases con `T0NN`, Dependencies, Notes).
- [x] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [x] Fecha `2026-10-09` y enlace a `spec.md`.
- [x] Toda regla marcada `[SPEC]` o `[CONV]`.
- [x] Discrepancias `D-UC04-01` a `D-UC04-10` en «Discrepancias y decisiones» con decisión explícita y estado final (decididas o aplicadas).
- [x] Cada RF/RNF/CE/HU del SPEC 04 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce004_…`.
- [x] Esquema: UC04 **no** aporta DDL; solo escribe columnas existentes de `reservation_information`; rige la migración inicial única (general-plan §4.2, D-31).
- [x] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§11) aplicadas; sin Flota/UC02/tarifa dinámica en UC04 (CE-002).
- [x] «Firmas referenciadas por otros planes» declarado: `CalculateReservationValueUseCase` no se expone; la firma `saveCalculatedAmounts` del puerto de salida de UC03 ya estaba publicada y UC04 solo la consume. **[ACT-12]**
- [x] Referencias por nombre: `ReservationInformationRepository` (UC03, con `saveCalculatedAmounts`), `FinancialParametersRepository`, `ReservationLockPort`, `FailureRecorderPort`/`operational_failure` y VOs compartidos; sin coordinación por tareas externas. **[ACT-12]**
- [x] Seguridad fuera de alcance documentada (general-plan §7.1); sin `SecurityConfig` ni pruebas 401/403.
