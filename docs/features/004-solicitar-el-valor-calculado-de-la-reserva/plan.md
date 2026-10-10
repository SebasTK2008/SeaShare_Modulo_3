# Implementation Plan: UC04 - Solicitar el Valor Calculado de la Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC04 es el caso de uso **síncrono** por el que el Sistema de Reservas y Operaciones solicita el **valor financiero definitivo** de una reserva identificada por su `reservation_id` [SPEC RF-001]. El sistema recupera la `InformaciónDeReserva` previamente registrada por UC03 (tarifa base, fechas, pasajeros, propietario y capacidad; RF-002) y calcula: **monto de alquiler** = `tarifa base registrada × días inclusivos` [RF-003], **seguro náutico** = `tarifa de seguro × pasajeros` [RF-004], **depósito de garantía** = `10 % de la tarifa base diaria` **sin** multiplicar por duración, pasajeros ni daño [RF-005, HU1] y **valor total** = alquiler + seguro + depósito, que es el monto que se cobra en "Procesar cobro" [RF-006]. Devuelve el desglose completo [RF-007] y **actualiza la fila interna** con los montos calculados, los **parámetros congelados** (comisión % y tarifa de seguro por pasajero) y `calculated_at`, dejándola disponible para UC05 [RF-008, CE-004].

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. Adaptador de entrada REST (`POST /api/v1/reservations/{reservation_id}/calculated-value`, sin cuerpo) que invoca exclusivamente `application.port.in`; lógica de cálculo pura en `ReservationValueCalculator` (dominio, nombrado en general-plan §3.3); persistencia JPA sobre `reservation_information` (tabla de UC03) y lectura del singleton `financial_parameters` (tabla de UC11) **por nombre**. Errores en *Problem Details* (RFC 9457) con el catálogo de `contracts/README.md` §3.4 y orden E1→E2→E3→E4→E5→E6→E8 (E7 **no aplica**: UC04 no llama a ninguna dependencia externa) [contrato UC04 §5; README §4.3].

**Decisiones clave del plan** (confirmadas con el responsable):
- **D-17 (general-plan)**: UC04 **recalcula siempre** a partir de la información registrada y de los parámetros **congelados** en la reserva, de modo que cada solicitud devuelve el mismo desglose (determinista) e idempotente; esto **cierra OQ-07** [general-plan D-17; SPEC casos extremos].
- **Frontera de redondeo** (D-UC04-04): cada monto se calcula a escala 4 y se **redondea HALF_UP a 2 decimales al final del cálculo**; se persiste y se devuelve ese valor, de forma que `total_amount` es **exactamente** la suma de los tres componentes redondeados y es el monto que UC05 cobra [SPEC RNF-002; contrato ilustra 2 decimales].
- **Depósito** (D-UC04-03): el 10 % se aplica sobre la `base_rate` **registrada** (que ya incluye la tarifa dinámica de UC02); UC04 **no** vuelve a consultar ni recalcula la tarifa base [SPEC CE-002].
- **Concurrencia** (D-UC04-05, OQ-UC04-02): la lectura-modificación-escritura de UC04 se serializa con el *upsert* de UC03 mediante un **bloqueo asesor de PostgreSQL por `reservation_id`** (`pg_advisory_xact_lock`) dentro de una transacción única [general-plan D-15].
- **Registro de fallos**: el SPEC 04 pide "registra el fallo" en los casos de información incompleta y de parámetros sin configurar → UC04 usa `FailureRecorderPort` **por nombre** (pieza compartida pendiente en UC11, D-UC02-11). El `404` **no** registra (estado transitorio esperado; ver D-UC04-06).

UC04 corresponde a la **fase 5** de la hoja de ruta [general-plan §12]: depende de UC03 (crea `reservation_information` y publica su repositorio) y de UC11 (parámetros financieros, fase 3). **No depende directamente de UC02**: la tarifa vigente ya quedó registrada por UC03. **No persiste tablas nuevas ni aporta migración Flyway**: las columnas calculadas y los parámetros congelados ya forman parte de la migración inicial **V1** que aporta `UC03·T006` [decisión «V1 SIEMPRE», D-UC03-01].

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** recibir la solicitud con el `reservation_id` | `ReservationValueController`, `CalculateReservationValueCommand` | T010, T012 | T013, T014 |
| **RF-002** recuperar la información registrada por UC03 | `CalculateReservationValueService` (vía `ReservationInformationRepository.findByReservationId`) | T005, T011 | T014, T015 |
| **RF-003** alquiler = tarifa base × días inclusivos | `ReservationValueCalculator` | T008 | T009, T022 (CE-001) |
| **RF-004** seguro = tarifa de seguro × pasajeros | `ReservationValueCalculator` | T008 | T009, T022 |
| **RF-005** depósito = 10 % de la tarifa base diaria, sin multiplicadores | `ReservationValueCalculator` | T008 | T009, T022 |
| **RF-006** total = alquiler + seguro + depósito (monto de UC05) | `ReservationValueCalculator`, `CalculateReservationValueService` | T008, T011 | T022, T025 (CE-004) |
| **RF-007** devolver el desglose completo | `ReservationValueResponse`, `ReservationValueController` | T012 | T013 |
| **RF-008** congelar parámetros, actualizar la información interna e invalidación | `FrozenParameters`, transición en `ReservationInformation`, `saveCalculatedAmounts`, `CalculateReservationValueService` | T006, T007, T011 | T015, T016, T025 (CE-004) |
| **RF-009** error controlado si no hay información registrada | `CalculateReservationValueService`, `ReservationValueExceptionHandler` | T011, T012 | T017, T024 (CE-003) |
| **RNF-001** DTOs para la comunicación | `CalculateReservationValueCommand`, `ReservationValueResult`, `ReservationValueResponse` | T010, T012 | T013 |
| **RNF-002** `BigDecimal` con precisión interna de 4 decimales | `Money` (de UC02), `ReservationValueCalculator` | T006, T008 | T009, T022 |
| **RNF-003** manejo de errores robusto (info ausente/incompleta, seguro no configurado) | `CalculateReservationValueService` + `FailureRecorderPort` + `ReservationValueExceptionHandler` | T004, T011, T012 | T014, T018, T024 |
| **CE-001** precisión: total = suma exacta, cero errores de redondeo | `ReservationValueCalculator` + aceptación | T008 | **T022 `ce001_…`** |
| **CE-002** cálculo solo desde la información registrada (sin Flota / tarifa dinámica) | `ReservationValueCalculator`, `CalculateReservationValueService` + ArchUnit | T008, T011, T026 | **T023 `ce002_…`** |
| **CE-003** sin información registrada → error controlado | `CalculateReservationValueService`, `ReservationValueExceptionHandler` | T011, T012 | **T024 `ce003_…`** |
| **CE-004** desglose disponible internamente para "Procesar cobro" | `saveCalculatedAmounts` + integración | T007, T011 | **T025 `ce004_…`** |
| **HU1** (P1) | Fases 3–5 | T010–T028 | T013–T028 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC04: Spring Web MVC, Validation, Jackson, Spring Data JPA (ya en el `pom.xml`), MapStruct (ya, D-04), ArchUnit y Testcontainers (PostgreSQL ya declarado). **UC04 no requiere artefactos Maven nuevos**: los starters web/validation/test y ArchUnit se incorporan en la fase Setup compartida `UC11·T001`; el `pom.xml` se edita solo ahí, sin cambios en silencio
**Storage**: PostgreSQL 16+ — **escritura/lectura** de `reservation_information` (tabla de UC03/UC04; UC03 la crea, UC04 la completa) y **solo lectura** del singleton `financial_parameters` (tabla de UC11). `NUMERIC(18,4)` para dinero y porcentajes, `timestamptz` para instantes [SPEC general-plan §4]. **Sin tablas propias ni migración**
**Messaging**: No aplica (REST síncrono; sin RabbitMQ en UC04) [contrato UC04]
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (PostgreSQL), ArchUnit [CONV]; los fallos se prueban con el doble `InMemoryFailureRecorder` (de `UC02·T004/T016`), para no depender de la pieza compartida pendiente (D-UC02-11)
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: El SPEC 04 no define metas de rendimiento `[NEEDS CLARIFICATION: OQ-UC04-03; se buscó en spec.md 004, general-plan.md y el contrato UC04; no hay metas propias para esta operación]` **[CONV: operación puntual; sin meta específica]**
**Constraints**: `BigDecimal` con precisión interna de 4 decimales y redondeo final a 2 decimales por monto (RNF-002, D-UC04-04); el cálculo se basa **exclusivamente** en la información registrada por UC03, sin recalcular ni volver a consultar la tarifa base ni la dinámica (CE-002); parámetros congelados por reserva, no retroactivos (RF-008, D-27); sin cálculos ni cambios parciales ante cualquier error (RNF-003, principio rector 4); una sola transacción por solicitud
**Scale/Scope**: 1 endpoint REST (`POST /api/v1/reservations/{reservation_id}/calculated-value`) [contrato UC04]; 9 RF + 3 RNF + 4 CE + 1 HU del SPEC 04; 1 puerto de entrada (`CalculateReservationValueUseCase`) + 1 extensión de puerto de salida de UC03 (`saveCalculatedAmounts`) + 2 piezas consumidas por nombre (UC03, UC11)

**Estado actual del repositorio (relevante para UC04)**: igual que en UC11/UC02/UC03/UC01 — existen `SeashareM3Application.java`, `application.properties` (solo `spring.application.name=seashare-m3`), `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** los paquetes `domain`/`application`/`infrastructure`, la tabla `reservation_information`, ni las piezas de UC02/UC03/UC11 (`Money`, `ReservationInformation`, `ReservationInformationRepository`, `FinancialParametersRepository`, `FailureRecorderPort`).

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

Contratos/piezas de otros planes (solo referencia entre planes; **no se re-planifican**): plan de UC03 (`ReservationInformation`, `ReservationId`, `ReservationInformationRepository`, migración V1) y plan de UC11 (`FinancialParameters`, `FinancialParametersRepository`, `ProblemDetailsConfig`, `DomainException`, fases Setup/Foundational). `FailureRecorderPort`/`operational_failure` son **compartidas** y hoy **no están en el plan de UC11** (D-UC02-11 / D-UC04-08).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── model/
│   │   └── ReservationInformation.java              # de UC03·T010 — consumido; UC04 añade el método de transición (T007)
│   ├── valueobject/
│   │   ├── ReservationId.java                       # de UC03·T008 — consumido por nombre
│   │   ├── Money.java                               # de UC02·T006 — consumido por nombre [general-plan §13, D-11]
│   │   ├── Percentage.java                          # de UC11/general-plan §3.3 — consumido por nombre
│   │   ├── FrozenParameters.java                    # T006 [SPEC RF-008; general-plan §13]
│   │   └── ReservationValue.java                    # T006 [SPEC "DesgloseValorReserva"; nombre [CONV]]
│   ├── service/
│   │   └── ReservationValueCalculator.java          # T008 [SPEC RF-003…RF-006, RNF-002; general-plan §3.3]
│   └── exception/
│       ├── DomainException.java                     # de UC11·T004 (Compartido)
│       ├── ReservationInfoNotFoundException.java    # T010 [SPEC RF-009; nombre [CONV]]
│       ├── ReservationInfoIncompleteException.java  # T010 [SPEC RNF-003/casos extremos]
│       └── FinancialParametersNotConfiguredException.java  # T010 [SPEC casos extremos]
├── application/
│   ├── port/in/
│   │   └── CalculateReservationValueUseCase.java    # T010 [general-plan §3.5]
│   ├── port/out/
│   │   ├── ReservationInformationRepository.java    # de UC03·T014 — consumido; UC04 amplía con saveCalculatedAmounts (T007)
│   │   ├── FinancialParametersRepository.java       # de UC11·T007/T013 — consumido por nombre [general-plan §4]
│   │   └── FailureRecorderPort.java                 # Compartido (se define en UC11·T001–T009) — por nombre; D-UC04-08
│   ├── service/
│   │   └── CalculateReservationValueService.java    # T011 [general-plan §3.5]
│   └── dto/
│       ├── CalculateReservationValueCommand.java    # T010 [SPEC RNF-001; nombre [CONV]]
│       └── ReservationValueResult.java              # T010 [SPEC RNF-001; nombre [CONV]]
└── infrastructure/
    ├── adapter/in/web/
    │   ├── ReservationValueController.java          # T012 [CONV]
    │   ├── ReservationValueExceptionHandler.java    # T012 [CONV; Problem Details, README §3.3/§3.4]
    │   └── dto/
    │       └── ReservationValueResponse.java        # T012 [contrato UC04; snake_case, montos string 2 dec]
    └── adapter/out/persistence/
        └── (extensión del adaptador de UC03)        # T007/T016: implementa saveCalculatedAmounts en el adaptador de UC03
                                                      # (misma pieza de UC03; sin duplicar el adaptador)

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── ArchitectureTest.java                        # T002 (base) + T026 (reglas UC04)
├── domain/valueobject/
│   └── ReservationValueFrozenParametersTest.java    # T009
├── domain/service/
│   └── ReservationValueCalculatorTest.java          # T009
├── application/service/
│   ├── CalculateReservationValueServiceTest.java    # T014
│   └── ReservationValueAcceptanceTest.java          # T022 (ce001), T023 (ce002), T024 (ce003), T025 (ce004)
├── infrastructure/adapter/in/web/
│   └── ReservationValueControllerContractTest.java  # T013 (200/400), T017 (404/422/503/orden)
├── infrastructure/adapter/out/persistence/
│   └── ReservationValuePersistenceAdapterTest.java  # T015, T016, T019 (Testcontainers PostgreSQL)
└── common/
    └── InMemoryFailureRecorder.java                 # de UC02·T004/T016 — reutilizado por nombre [CONV]
```

`ReservationInformation`, `ReservationId` y `ReservationInformationRepository` son de **UC03** (dueño de `reservation_information` según `general-plan.md` §4 — «Entidad (UC03/UC04)»; UC03 la crea, UC04 la completa). `FinancialParameters`/`FinancialParametersRepository` son de **UC11** y se consumen por nombre. `Money` es de **UC02** (compartido entre UC01/UC02/UC03/UC04). `FailureRecorderPort`/`operational_failure` son **compartidas** y **están pendientes en el plan de UC11** (D-UC04-08): UC04 las referencia por nombre, no las implementa y usa el doble en pruebas.

**Firmas referenciadas por otros planes** [general-plan §3.5]: UC04 **no expone puertos de entrada a otros planes** (`CalculateReservationValueUseCase` lo consume únicamente su adaptador REST). Como completador de `reservation_information`, UC04 **amplía la firma del puerto de salida** `ReservationInformationRepository` (cuyo dueño es UC03) con la operación que faltaba para guardar los montos calculados sin anular el bloque informativo. Firma publicada [CONV]:

```java
public interface ReservationInformationRepository {
    // --- de UC03·T014 (upsert con invalidación del bloque informativo) ---
    Optional<ReservationInformation> findByReservationId(ReservationId reservationId);
    ReservationInformation save(ReservationInformation information);

    // --- UC04·T007: completa montos + parámetros congelados + calculated_at ---
    //   NO anula el bloque informativo; transacción única con pg_advisory_xact_lock(reservationId);
    //   debe ejecutarse sobre la fila ya existente (si no existe, el servicio no la crea: responde 404).
    ReservationInformation saveCalculatedAmounts(ReservationInformation information);
}
```

Además, el dominio `ReservationInformation` (UC03) incorpora un **método de transición** para construir la versión con montos calculados (T007; coordinado con el plan de UC03):

```java
ReservationInformation withCalculatedAmounts(ReservationValue value,
                                             FrozenParameters frozen,
                                             Instant calculatedAt);
```

Los demás planes siguen leyendo `reservation_information` **por nombre**; el consumo de UC05 es de lectura directa de las columnas calculadas, sin recalcular (CE-004).

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) en un solo módulo Maven, con subpaquetes temáticos sin reglas entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas del §3.4 aplicadas aquí: `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `application.port.in`; `infrastructure.adapter.out` solo implementa `application.port.out`; ningún controller accede a un repositorio; la entidad JPA no sale de `adapter.out.persistence`; los DTOs HTTP viven en `infrastructure.adapter.in` y `application` trabaja con `command`/`result` (RNF-001). Identificadores en inglés (D-23) y lenguaje ubicuo §13 (*InformaciónDeReserva → `ReservationInformation`*, *Depósito de garantía → `GuaranteeDeposit`*, *Parámetros congelados → `FrozenParameters`*). `DesgloseValorReserva` y `SolicitudValorReserva` del SPEC 04 no están en §13 → `ReservationValue` (dominio) / `ReservationValueResult` (aplicación) y `CalculateReservationValueCommand` (aplicación) **[CONV, D-UC04-07]**.

## Reglas de negocio

Todas las reglas provienen del SPEC 04 (`spec.md`), su contrato o el `general-plan.md`, salvo indicación. Ejemplo base del contrato UC04: `reservation_id = b7d0e2a1-…`, `base_rate = 300000.00`, reserva del día 10 al día 12 (3 días), `insurance_fee_per_passenger = 15000.00`, `passengers = 4`, `commission_pct = 20.00`.

1. **Solicitud** [SPEC RF-001; contrato §2]: `POST /api/v1/reservations/{reservation_id}/calculated-value`, **sin cuerpo**; la reserva se identifica solo por su identificador (UUID v4). Formato inválido ⇒ `400 VALIDATION_ERROR` (E4).
2. **Recuperar la información registrada** [SPEC RF-002]: se lee la `InformaciónDeReserva` de UC03 (`findByReservationId`). Si no existe ⇒ `404 RESERVATION_INFO_NOT_FOUND` (`retryable: true` por posible carrera con UC03, OQ-06) [SPEC RF-009; contrato §5].
3. **Días inclusivos** [SPEC RF-003, HU1; contrato regla 2]: `dias = end_date − start_date + 1`; `start_date == end_date` ⇒ 1 día; del día 10 al 12 ⇒ 3 días. UC03 garantiza `end_date >= start_date`, por lo que `dias >= 1` (no se revalida).
4. **Monto de alquiler** [SPEC RF-003, HU1]: `rental_amount = base_rate registrada × dias`. Es el precio **bruto** de uso de la embarcación; **no** incluye seguro, depósito ni comisión. Ejemplo: `300000.00 × 3 = 900000.00`.
5. **Monto del seguro náutico** [SPEC RF-004]: `insurance_amount = insurance_fee_per_passenger × passengers` (tarifa congelada de UC11). Ejemplo: `15000.00 × 4 = 60000.00`.
6. **Depósito de garantía** [SPEC RF-005, HU1; general-plan §3.5 UC04]: `guarantee_deposit = base_rate registrada × 10 %`; **no** se multiplica por duración, pasajeros ni daño reportado. La `base_rate` registrada ya es la tarifa final de UC02 (incluye tarifa dinámica): UC04 **no** consulta Flota ni recalcula la tarifa base [SPEC CE-002; decisión D-UC04-03]. El 10 % es una constante del caso de uso (no un parámetro configurable de UC11). Ejemplo: `300000.00 × 0.10 = 30000.00`.
7. **Valor total** [SPEC RF-006; contrato regla 5]: `total_amount = rental_amount + insurance_amount + guarantee_deposit`; es el monto que "Procesar cobro" (UC05) cobra al arrendatario. Ejemplo: `900000.00 + 60000.00 + 30000.00 = 990000.00`.
8. **Precisión y redondeo** [SPEC RNF-002; decisión D-UC04-04]: cada monto se calcula con `BigDecimal` a **escala 4** (precisión interna) y se redondea **HALF_UP a 2 decimales** al final del cálculo; se persiste y se devuelve ese valor. Así `total_amount` es **exactamente** la suma de los tres montos redondeados (CE-001) y coincide con lo que UC05 cobra. Ejemplo con 4 decimales: `base_rate = 300000.0050`, 3 días ⇒ alquiler calculado `900000.0150` → `900000.02`; seguro `15000.0050 × 4 = 60000.0200` → `60000.02`; depósito `30000.0005` → `30000.00`; total `990000.0355` → **se recalcula como suma de los redondeados** `900000.02 + 60000.02 + 30000.00 = 990000.04` (garantiza la igualdad exacta de CE-001).
9. **Congelar parámetros** [SPEC RF-008; general-plan D-27]: al calcular se congelan la **comisión %** (`commission_pct_applied`) y la **tarifa de seguro por pasajero** (`insurance_fee_per_passenger_applied`) vigentes en `financial_parameters`; se persisten en la reserva y **no son retroactivos**: un cambio posterior en UC11 no altera los valores ya congelados.
10. **Actualizar la información interna** [SPEC RF-008; contrato regla 6]: se escriben en `reservation_information` los cuatro montos, los dos parámetros congelados y `calculated_at`, dejando el desglose disponible para UC05 **sin recalcular** (CE-004). La escritura ocurre en una **única transacción**; no se anula el bloque informativo (fechas, tarifa base, pasajeros, propietario, capacidad).
11. **Invalidación por UC03** [SPEC RF-008, casos extremos; UC03 casos extremos]: si UC03 vuelve a registrar la reserva, su *upsert* deja los montos calculados y los parámetros congelados en `NULL`; UC04 **debe recalcular** antes de un nuevo cobro. UC04 siempre recalcula en cada solicitud, de modo que tras una invalidación una nueva llamada regenera el desglose con los parámetros vigentes en ese momento.
12. **Solicitud repetida / determinismo** [SPEC casos extremos; general-plan D-17]: como el cálculo se hace desde la misma información registrada y los parámetros **congelados**, repetir la solicitud devuelve el mismo desglose y la actualización interna es idempotente (sobrescribe con valores equivalentes). La mención del contrato a "se devuelven los ya registrados" es la **consecuencia** de D-17, que **cierra OQ-07** (D-UC04-01).
13. **Sin consultar Flota ni recalcular la tarifa** [SPEC CE-002; general-plan §3.5]: `CalculateReservationValueService` y `ReservationValueCalculator` **no** dependen de `ProvideBaseRateUseCase`, `FleetRatePort` ni `DynamicRatePolicy`; solo usan la `base_rate` registrada.
14. **Información registrada incompleta** [SPEC RNF-003, casos extremos; contrato regla 10]: si la fila existe pero no permite calcular (p. ej. `base_rate` nula), ⇒ `422 RESERVATION_INFO_INCOMPLETE` (E8), **sin** cálculo parcial. Es una vía **defensiva** de integridad: el diseño de UC03 (columna `base_rate NOT NULL`) garantiza que no se persista información incompleta (D-UC04-02).
15. **Parámetros no configurados** [SPEC casos extremos; contrato regla 11]: si no existe la fila de `financial_parameters` o `insurance_fee_per_passenger`/`commission_pct` es nulo, ⇒ `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` (E6); **no** se calcula ni el seguro ni un total parcial y **no** se escribe nada. Se requieren **ambos** valores: el seguro para calcular y la comisión para congelar (decisión confirmada).
16. **Registro de fallos** [SPEC casos extremos; D-UC04-06]: los casos 422 (información incompleta) y 503 (parámetros no configurados) **registran el fallo** en `operational_failure` vía `FailureRecorderPort` (por nombre, D-UC04-08), porque el SPEC 04 lo exige textualmente. El `404` **no** registra: es un estado transitorio esperado (Reservas puede reintentar; OQ-06).
17. **Orden de validación** [CONV; README §4.3/§4.5]: `E1 (401) → E2 (403) → E3 → E4 → E5 → E6 → E8`. La existencia de la reserva (E5) se evalúa antes de leer los parámetros (E6) y de comprobar la completitud (E8); una petición mal formada (E4) nunca llega a la base de datos. `E7` **no aplica** (UC04 no tiene dependencia externa).
18. **Concurrencia y atomicidad** [general-plan D-15; OQ-UC04-02]: la lectura-modificación-escritura se ejecuta en una **transacción única** y se serializa con un **bloqueo asesor de PostgreSQL por `reservation_id`** (`pg_advisory_xact_lock`), de modo que un *upsert* concurrente de UC03 no pierde la escritura de montos ni viceversa. Un fallo en cualquier paso deja la fila intacta (ni montos nuevos ni invalidación parcial).
19. **Sin persistencia propia ni migración** [general-plan §3.3/§4]: UC04 no crea tablas ni archivos Flyway; escribe columnas ya existentes de `reservation_information` (V1 aportada por UC03). Repetir la petición no añade filas.
20. **Seguridad** [PEND OQ-01; OQ-UC04-01]: los contratos declaran `401 UNAUTHENTICATED` y `403 FORBIDDEN` y que solo el Sistema de Reservas y Operaciones puede llamar [SPEC RF-001; contrato §5], pero el **mecanismo** no está definido. Esta fase **no implementa** `SecurityConfig` ni pruebas 401/403, por coherencia con UC01 y a la espera de la decisión transversal (D-UC04-09).

## Migración

**UC04 no aporta DDL.** Rige la decisión «**V1 SIEMPRE**» (D-UC03-01): existe una **única migración inicial V1**, que ya crea `financial_parameters` (`UC11·T002`) y `reservation_information` (`UC03·T006`, conforme a `general-plan.md` §4). La tabla ya contiene las columnas que UC04 escribe [general-plan §4]:

| Columna | Tipo | Origen | Uso en UC04 |
|---|---|---|---|
| `rental_amount` | `NUMERIC(18,4)` | DDL V1 de UC03 | `UPDATE` (monto de alquiler) |
| `insurance_amount` | `NUMERIC(18,4)` | DDL V1 de UC03 | `UPDATE` (seguro náutico) |
| `deposit_amount` | `NUMERIC(18,4)` | DDL V1 de UC03 | `UPDATE` (depósito de garantía) |
| `total_amount` | `NUMERIC(18,4)` | DDL V1 de UC03 | `UPDATE` (valor total) |
| `commission_pct_applied` | `NUMERIC(18,4)` | DDL V1 de UC03 | `UPDATE` (parámetro congelado) |
| `insurance_fee_per_passenger_applied` | `NUMERIC(18,4)` | DDL V1 de UC03 | `UPDATE` (parámetro congelado) |
| `calculated_at` | `TIMESTAMPTZ` | DDL V1 de UC03 | `UPDATE` (instante del cálculo) |

La operación de UC04 sobre la tabla es `SELECT … FOR UPDATE`/`UPDATE` de esas columnas **sin** tocar el bloque informativo. El adaptador de UC03 ya anula estas columnas en su *upsert* (invalidación, `UC03·T016`); UC04 las completa. No hay índices ni columnas nuevas.

## Contratos

Fuente: `contracts/rest/UC04-valor-calculado-reserva.md` y `contracts/README.md` §3. El SPEC 04 **no** fija la ruta: rige el contrato.

### `POST /api/v1/reservations/{reservation_id}/calculated-value`

**Petición**: sin cuerpo. Headers [README §3.2]: `Authorization` (obligatorio, [PEND] OQ-01), `Accept` (opcional), `X-Correlation-Id` (opcional; se genera si falta y se devuelve).

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
| 400 | `VALIDATION_ERROR` | `reservation_id` no es un UUID v4 válido | No | [CONV] E4 |
| 401 | `UNAUTHENTICATED` | Credencial ausente o inválida | No | [PEND] OQ-01 E1 |
| 403 | `FORBIDDEN` | El llamador no es el Sistema de Reservas y Operaciones | No | [SPEC RF-001] E2 |
| 404 | `RESERVATION_INFO_NOT_FOUND` | No existe información registrada para esa reserva | **Sí** (carrera con UC03, OQ-06) | [SPEC RF-009] E5 |
| 422 | `RESERVATION_INFO_INCOMPLETE` | La información registrada no permite calcular (p. ej. sin tarifa base) | No | [SPEC RNF-003] E8 |
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Faltan la tarifa de seguro o la comisión (fila ausente o valor nulo) | Sí (cuando el Administrador Financiero configure) | [SPEC casos extremos] E6 |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] E10 |

```json
{
  "type": "about:blank",
  "title": "Reserva sin información registrada",
  "status": 404,
  "detail": "No existe información registrada para la reserva b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10.",
  "code": "RESERVATION_INFO_NOT_FOUND",
  "retryable": true
}
```

**Mapeo de excepciones** (lo implementa `ReservationValueExceptionHandler`, T012):

| Origen (dominio/aplicación) | HTTP / `code` |
|---|---|
| `ReservationInfoNotFoundException` | 404 `RESERVATION_INFO_NOT_FOUND` (`retryable: true`) |
| `ReservationInfoIncompleteException` | 422 `RESERVATION_INFO_INCOMPLETE` |
| `FinancialParametersNotConfiguredException` | 503 `FINANCIAL_PARAMETERS_NOT_CONFIGURED` |
| UUID de ruta inválido (framework) | 400 `VALIDATION_ERROR` |
| Excepción no controlada | 500 `INTERNAL_ERROR` |

Todos los errores usan *Problem Details* (RFC 9457) con `code` y `retryable`, y `X-Correlation-Id` (§3.3). **Orden de validación**: E1 → E2 → E3 → E4 → E5 → E6 → E8 (E7 no aplica).

### Idempotencia y reintentos

Idempotente: devuelve siempre el mismo desglose mientras la información de la reserva no cambie; la actualización interna sobrescribe con valores equivalentes [SPEC casos extremos; general-plan D-17]. `RESERVATION_INFO_NOT_FOUND` es reintentable si UC03 aún está en cola (OQ-06). `FINANCIAL_PARAMETERS_NOT_CONFIGURED` es reintentable cuando el Administrador Financiero configure los parámetros.

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), contrato UC04 y SPEC 04. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %. Las pruebas de fallos usan `InMemoryFailureRecorder` (de `UC02·T004/T016`) para **no depender** de la pieza compartida pendiente (D-UC04-08).

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | `FrozenParameters`/`ReservationValue`, fórmula (RF-003–RF-006), días inclusivos, depósito sin multiplicadores, precisión y redondeo a 2 dec (RNF-002, D-UC04-04) | `domain/valueobject/ReservationValueFrozenParametersTest`, `domain/service/ReservationValueCalculatorTest` |
| Unitario (`application`) | orquestación: 404 sin fila, 422 incompleta, 503 parámetros ausentes/nulos, escritura única, registro de fallos, determinismo (OQ-07) | `CalculateReservationValueServiceTest` (Mockito sobre `ReservationInformationRepository`, `FinancialParametersRepository`, `FailureRecorderPort` + `java.time.Clock` fijo) |
| Contrato / Web (MockMvc) | cuerpo y códigos del contrato: 200 feliz, 400, 404 `retryable`, 422, 503, 500 | `ReservationValueControllerContractTest` |
| Integración (Testcontainers PostgreSQL) | `saveCalculatedAmounts` escribe montos + congelados + `calculated_at`; repetición idempotente; parámetros globales cambiados no alteran los congelados; tras invalidación de UC03, UC04 recalcula; bloqueo asesor serializa UC04/UC03 | `ReservationValuePersistenceAdapterTest` |
| Aceptación | CE-001…CE-004 | `ReservationValueAcceptanceTest` |
| Arquitectura | reglas §3.4; CE-002 (sin Flota/UC02/tarifa dinámica) | `arch/ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-004)** — nóminal `ce00X_<descripcion>`:

- **`ce001_total_es_suma_exacta_sin_errores_de_redondeo`** (CE-001, T022): prueba parametrizada con el ejemplo del contrato y casos con tarifas/seguros de 4 decimales, distintos días inclusivos y pasajeros; verifica con `BigDecimal` que `total_amount == rental_amount + insurance_amount + guarantee_deposit_amount` exactamente y que cada monto quedó a 2 decimales HALF_UP, con cero discrepancias.
- **`ce002_calculo_solo_desde_informacion_registrada`** (CE-002, T023): (a) ArchUnit — UC04 no depende de `ProvideBaseRateUseCase`, `FleetRatePort` ni `DynamicRatePolicy`; (b) integración con WireMock de Flota — **cero** peticiones a Flota durante UC04 y el cambio de tarifa en el stub no altera el desglose (se usa la `base_rate` registrada).
- **`ce003_sin_informacion_registrada_respuesta_controlada`** (CE-003, T024): MockMvc/integración — solicitudes de reservas sin información registrada responden `404 RESERVATION_INFO_NOT_FOUND` con *Problem Details* completo, sin cálculo parcial y sin provocar fallos; el 100 % de los casos queda con error controlado. Se cubre además `422` (info incompleta) y `503` (parámetros ausentes).
- **`ce004_desglose_disponible_para_procesar_cobro`** (CE-004, T025): integración (Testcontainers) — tras un cálculo exitoso, la fila queda con los cuatro montos, los dos parámetros congelados y `calculated_at`; una lectura de UC05 (por repositorio) obtiene el desglose **sin recalcular**, en el 100 % de los casos.

Además: la regresión de consumidores **no forma parte de este plan** (UC05 lee la tabla por nombre; aquí solo se deja el desglose persistido).

## Discrepancias y puntos abiertos

Registro de contradicciones detectadas al elaborar este plan. **No se resuelven en silencio**: cada una indica la decisión para avanzar y qué requiere confirmación. Los IDs `D-UC04-xx` son propios de este plan; los `D-xx` sin prefijo son decisiones del plan general (§11) o de los planes de UC02/UC03.

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC04-01** | La regla 7 del contrato cita "se devuelven los ya registrados (D-17, [PEND] OQ-07)", pero D-17 ya resolvió OQ-07 recalculando con los parámetros **congelados** | `UC04-valor-calculado-reserva.md` §3 regla 7 vs general-plan D-17 | Rige D-17: UC04 **recalcula siempre** desde la información registrada y los parámetros congelados; el efecto es equivalente a devolver los ya registrados. **OQ-07 queda cerrada** para UC04. Se propone corregir la redacción del contrato | Decidida (corrección de contrato propuesta) |
| **D-UC04-02** | El SPEC 04 asume que puede existir información registrada incompleta por una falla previa de UC03, pero el plan de UC03 **nunca** persiste información incompleta | SPEC 04 casos extremos vs plan UC03 (DDL `base_rate NOT NULL`, regla 10 de UC03) | `422 RESERVATION_INFO_INCOMPLETE` queda como vía **defensiva** de integridad; el caso normal de "aún no registrada" es `404`. Se conserva el código por estar en el catálogo | Decidida (confirmada) |
| **D-UC04-03** | El depósito es "10 % de la tarifa base diaria"; en UC02 "tarifa base" es la de Flota (sin dinámica), pero UC04 no puede reconsultarla | SPEC 04 HU1/RF-005 vs SPEC 02 RF-004 y SPEC 04 CE-002 | El 10 % se aplica sobre la `base_rate` **registrada** (ya con tarifa dinámica de UC02); "tarifa base diaria" = `base_rate` registrada. Confirmado por el responsable | Decidida (confirmada) |
| **D-UC04-04** | RNF-002 exige 4 decimales internos "antes de cualquier redondeo final"; el contrato ilustra 2 decimales y CE-001 exige total = suma exacta | SPEC 04 RNF-002/CE-001 vs ejemplo del contrato | Cada monto se calcula a escala 4 y se redondea **HALF_UP a 2 decimales** al final; se persiste y se devuelve ese valor; el total se forma como suma de los tres redondeados (igualdad exacta). UC05 cobra ese total | Decidida (confirmada) |
| **D-UC04-05** | La firma publicada por UC03 de `ReservationInformationRepository` no permite guardar montos: su `save` **anula** las columnas calculadas | `general-plan.md` §4 (tabla entidad de UC03/UC04) vs plan UC03 («Firmas referenciadas por otros planes») | UC04 **amplía** el mismo puerto con `saveCalculatedAmounts` (implementado en el adaptador de UC03; misma pieza de UC03). Se publica la firma en «Firmas referenciadas por otros planes». Concurrencia con `pg_advisory_xact_lock(reservation_id)` | Decidida (confirmada) |
| **D-UC04-06** | El SPEC 04 pide "registra el fallo" en los casos de info incompleta y seguro no configurado; no lo pide para "no existe información" | SPEC 04 casos extremos vs ausencia para el `404` | UC04 usa `FailureRecorderPort` por nombre en 422 y 503; el 404 **no** registra (estado transitorio esperado, reintentable). Difiera de UC01 (D-UC01-05) porque el SPEC 04 sí lo exige | Decidida (confirmada) |
| **D-UC04-07** | `DesgloseValorReserva`/`SolicitudValorReserva` (SPEC 04) no están en el lenguaje ubicuo §13 | SPEC 04 entidades clave vs general-plan §13 | `ReservationValue`/`ReservationValueResult` (resultado) y `CalculateReservationValueCommand` (solicitud); `InformaciónDeReserva` → `ReservationInformation` (ya en §13) | Decidida **[CONV]** |
| **D-UC04-08** | `general-plan.md` §3.5 asigna `FailureRecorderPort` y §4 define la tabla `operational_failure`, pero el plan de UC11 no incluye su implementación | `general-plan.md` §3.5/§4 vs plan UC11 (T004–T009) | UC04 **no** las implementa: las referencia por nombre, usa `InMemoryFailureRecorder` en pruebas y deja T004 condicionada. Desbloqueo: el plan de UC11 (a rehacer) las incluye | Abierta — hereda D-UC02-11 |
| **D-UC04-09** | El SPEC/contrato fijan *quién* llama (solo Reservas) pero no el mecanismo de autenticación; UC11 implementó `SecurityConfig` y UC01 lo difirió | SPEC 04 RF-001 vs general-plan §7.1 (OQ-01) y plan UC01 (OQ-UC01-01) | **Diferido** por coherencia con UC01: no se implementa `SecurityConfig` ni pruebas 401/403; los contratos siguen documentando 401/403. Ver OQ-UC04-01 | Abierta (diferida) |

## Preguntas abiertas (OQ-UC04-xx)

Preguntas que este plan no puede resolver con el SPEC 04, el plan general ni los contratos. Hasta que se confirmen rige la propuesta por defecto y las tareas afectadas quedan condicionadas.

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC04-01** | ¿Qué mecanismo de autenticación/autorización y quién es dueño del `SecurityConfig`? El SPEC fija *quién* llama, no *cómo* (equivale a OQ-01 del general-plan y a OQ-UC01-01) | T012 (401/403); D-UC04-09 | **Diferido**: no se implementa `SecurityConfig` ni pruebas 401/403 en esta fase, por coherencia con UC01; se resuelve en la revisión transversal de seguridad | Abierta (diferida) |
| **OQ-UC04-02** | ¿Cómo se serializa la carrera UC03 (*upsert* que invalida montos) ↔ UC04 (cálculo y escritura)? (general-plan OQ-06; heredada de OQ-UC03-05) | T007, T011, T019 | `pg_advisory_xact_lock` derivado del `reservation_id` dentro de la transacción única de UC04; UC03 adquiere el mismo bloqueo en su *upsert* | Abierta (propuesta adoptada) |
| **OQ-UC04-03** | ¿Existe una meta de rendimiento para el `POST` de UC04? El SPEC 04 no la define | Technical Context | Sin meta propia: operación puntual síncrona **[CONV]** | Abierta |
| **OQ-UC04-04** | ¿Es válido que el `404 RESERVATION_INFO_NOT_FOUND` sea `retryable: true`? (general-plan OQ-06: posible carrera con UC03) | T017; contrato §5 | Se mantiene `retryable: true` según el contrato y el catálogo, documentando la carrera con la cola de UC03 | Abierta |

**`[NEEDS CLARIFICATION]` consolidado:** OQ-UC04-01 a OQ-UC04-04.

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable de forma independiente; su casilla (`[ ]`) es el mecanismo de seguimiento (no se usan marcadores de módulo/aprobación). **«Compartido»** = misma tarea que una fase de la hoja de ruta del `general-plan.md` §12; las fases Setup y Foundational se marcan «Compartido» y remiten a `UC11·T001`–`UC11·T009`. Referencias a otros planes: `UC11·T0NN`, `UC03·T0NN`, `UC02·T0NN`.

### Phase 1: Setup — **Compartido** (con la Fase 1 general; se referencia como `UC11·T001`–`UC11·T003`)

- [ ] **T001** · Compartido: `pom.xml` — remite a `UC11·T001`. UC04 **no requiere artefactos nuevos** (web, validation, test y ArchUnit los incorpora `UC11·T001`; Spring Data JPA, Testcontainers-PostgreSQL y MapStruct ya están en el `pom.xml`).
- [ ] **T002** · Compartido: `application.properties` (datasource, Flyway, `X-Correlation-Id`) y base de `ArchitectureTest` (reglas §3.4) — remite a `UC11·T003`.

### Phase 2: Foundational — **Compartido + específicos de UC04** (los compartidos remiten a `UC11·T004`–`UC11·T009`)

- [ ] **T003** · Compartido: `DomainException` (`domain/exception`), base del árbol de excepciones — remite a `UC11·T004`.
- [ ] **T004** · **Condicionada (D-UC04-08, hereda D-UC02-11)**: consumir por nombre la pieza compartida `FailureRecorderPort` + tabla `operational_failure`; hoy no está en el plan de UC11. UC04 no la implementa; en pruebas usa `InMemoryFailureRecorder` (de `UC02·T004/T016`).
- [ ] **T005** · Consumir por nombre las piezas ya definidas en otros planes: `ReservationInformation`/`ReservationId` (`UC03·T010`/`UC03·T008`), `ReservationInformationRepository` (`UC03·T014`), `FinancialParameters`/`FinancialParametersRepository` (`UC11·T007`/`UC11·T013`) y `Money` (`UC02·T006`). UC04 **no** las implementa ni las prueba.
- [ ] **T006** · VOs de dominio: `FrozenParameters` (`commissionPct`, `insuranceFeePerPassenger`) [general-plan §13] y `ReservationValue` (`rentalAmount`, `insuranceAmount`, `guaranteeDeposit`, `totalAmount` como `Money`) [SPEC "DesgloseValorReserva"; D-UC04-07].
- [ ] **T007** · Extensión del puerto de UC03 (D-UC04-05): método de transición `ReservationInformation.withCalculatedAmounts(...)` y ampliación del puerto `ReservationInformationRepository` con `saveCalculatedAmounts(...)` (firma publicada en «Firmas referenciadas por otros planes»); el adaptador JPA de UC03 implementa el método como `UPDATE` de las columnas calculadas **sin** anular el bloque informativo, en transacción única con `pg_advisory_xact_lock(reservation_id)` (OQ-UC04-02).
- [ ] **T008** · `ReservationValueCalculator` (`domain/service`): `ReservationValue calculate(ReservationInformation info, FinancialParameters params)`. Fórmulas de RF-003–RF-006, `dias = end_date − start_date + 1`, depósito = 10 % de la `base_rate` registrada (constante del caso), cálculo a escala 4 y redondeo HALF_UP a 2 decimales por monto; `total` como suma de los tres redondeados (D-UC04-04, RNF-002).
- [ ] **T009** · Pruebas unitarias de VOs y del calculator: ejemplo del contrato (`900000.00/60000.00/30000.00/990000.00`), `start == end` (1 día), casos con 4 decimales y redondeo, depósito no multiplicado por días/pasajeros (RF-003–RF-006, RNF-002).
- [ ] **T010** · `CalculateReservationValueUseCase` + `CalculateReservationValueCommand` (`ReservationId`) + `ReservationValueResult` (los cuatro montos) + excepciones de dominio `ReservationInfoNotFoundException`, `ReservationInfoIncompleteException` y `FinancialParametersNotConfiguredException` [RNF-001; general-plan §3.5].

### Phase 3: US1 — Flujo feliz de cálculo y persistencia (HU1; RF-001, RF-002, RF-003, RF-004, RF-005, RF-006, RF-007, RF-008; CE-001, CE-004)

- [ ] **T011** · `CalculateReservationValueService`: `findByReservationId` → E5 (`404` si vacío) → lee `FinancialParametersRepository.find()` → E6 (`503` si ausente o `insurance`/`commission` nulos, registra fallo) → verifica completitud → E8 (`422` si `base_rate` nula, registra fallo) → `ReservationValueCalculator.calculate(...)` → `saveCalculatedAmounts(...)` en transacción única con bloqueo asesor; **no** invoca UC02 ni Flota; `calculated_at` desde un `java.time.Clock` inyectado.
- [ ] **T012** · `ReservationValueController` (`POST /api/v1/reservations/{reservation_id}/calculated-value`, sin cuerpo) + `ReservationValueResponse` (claves `snake_case`, montos string a 2 dec) + `ReservationValueExceptionHandler` (`@RestControllerAdvice`; *Problem Details* con `code`/`retryable` del catálogo §3.4, reutilizando `ProblemDetailsConfig` de `UC11·T008`; **sin** reglas de seguridad, diferidas en OQ-UC04-01).
- [ ] **T013** · Tests de contrato MockMvc del flujo feliz: `200` con el ejemplo del contrato (4 claves + `reservation_id`), aceptación de `X-Correlation-Id` y `400 VALIDATION_ERROR` con UUID inválido.
- [ ] **T014** · Pruebas unitarias del servicio (Mockito + `InMemoryFailureRecorder` + `Clock` fijo): flujo feliz; una sola escritura; sin fila → `404`; info incompleta → `422` + registro; parámetros ausentes/nulos → `503` + registro; **sin llamadas a Flota**.
- [ ] **T015** · Pruebas de integración del adaptador (Testcontainers PostgreSQL): `saveCalculatedAmounts` escribe los cuatro montos, los dos parámetros congelados y `calculated_at`; una solicitud repetida con los mismos insumos produce el mismo desglose (idempotencia); un cambio de `financial_parameters` no altera los congelados.
- [ ] **T016** · Pruebas de integración: `saveCalculatedAmounts` **preserva** el bloque informativo (fechas, `base_rate`, pasajeros, propietario, capacidad); tras un *upsert* de UC03 (columnas calculadas a `NULL`), una nueva llamada a UC04 recalcula y completa los montos con los parámetros vigentes.

### Phase 4: US1 — Errores, idempotencia y concurrencia (HU1; RF-008, RF-009; RNF-003; CE-002, CE-003)

- [ ] **T017** · Tests de contrato de errores (MockMvc): `404 RESERVATION_INFO_NOT_FOUND` con `retryable: true`, `422 RESERVATION_INFO_INCOMPLETE`, `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, `500 INTERNAL_ERROR`; verificación del orden E4 → E5 → E6 → E8.
- [ ] **T018** · Registro de fallos (CE-003): con dobles, el `422` y el `503` quedan en `InMemoryFailureRecorder`; el `404` **no** registra (D-UC04-06); en ningún caso se escribe la fila.
- [ ] **T019** · Concurrencia (OQ-UC04-02): prueba de integración con Testcontainers — un *upsert* de UC03 (bloqueo asesor del mismo `reservation_id`) y una solicitud de UC04 concurrentes se serializan; no hay pérdida de escritura ni invalidación parcial.
- [ ] **T020** · Determinismo / OQ-07 cerrada (D-17): dos solicitudes consecutivas (incluso tras cambiar `financial_parameters` sin invalidación) devuelven el mismo desglose y la fila conserva los parámetros **congelados** (D-UC04-01).
- [ ] **T021** · Manejo del error no controlado → `500 INTERNAL_ERROR` en *Problem Details*, sin detalles internos (README §4.3 regla 9).

### Phase 5: Polish y criterios de éxito — **parcialmente compartido con la Fase general**

- [ ] **T022** · **`ce001_total_es_suma_exacta_sin_errores_de_redondeo`** (CE-001): parametrizada (ejemplo del contrato + tarifas/seguros de 4 decimales, varios días y pasajeros); el total es exactamente la suma de los tres montos a 2 decimales, cero discrepancias.
- [ ] **T023** · **`ce002_calculo_solo_desde_informacion_registrada`** (CE-002): ArchUnit (sin `ProvideBaseRateUseCase`/`FleetRatePort`/`DynamicRatePolicy` en UC04) + integración con WireMock de Flota (cero peticiones; cambiar la tarifa del stub no altera el desglose).
- [ ] **T024** · **`ce003_sin_informacion_registrada_respuesta_controlada`** (CE-003): reservas sin información registrada → `404` con *Problem Details* completo, sin cálculo parcial; se cubren también `422` y `503`.
- [ ] **T025** · **`ce004_desglose_disponible_para_procesar_cobro`** (CE-004): integración — tras el cálculo, la fila tiene los cuatro montos + congelados + `calculated_at` y una lectura por repositorio (UC05) obtiene el desglose sin recalcular.
- [ ] **T026** · ArchUnit (reglas §3.4 y CE-002): `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `application.port.in`; el controller no toca repositorios; la entidad JPA no sale de `adapter.out.persistence`; sin dependencias de Flota/UC02/tarifa dinámica en UC04.
- [ ] **T027** · Documentación y referencias entre planes: documentar la firma de `ReservationInformationRepository.saveCalculatedAmounts` (§«Firmas referenciadas por otros planes»; `UC03` es el dueño del puerto) y registrar las discrepancias `D-UC04-01` (corrección de redacción del contrato §3 regla 7) y `D-UC04-08` (pieza compartida pendiente en UC11). No se edita ningún contrato ni SPEC sin acuerdo.
- [ ] **T028** · Compartido: revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-004.

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─┬─> T003 ─> T004* ─┐                (* condicionada: D-UC04-08 / D-UC02-11)
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

- **Grupo 1 (T001–T010)**: Setup y Foundational compartidos (`UC11·T001`–`UC11·T009`) + VOs, extensión del puerto/entidad (T007), calculator, command/result y excepciones. **T004 está condicionada a D-UC04-08** (pieza compartida pendiente en UC11); como UC04 la consume con un doble, no bloquea a T014/T018/T024.
- **US1 flujo feliz (T011–T016)**: servicio → controller → contrato → servicio unitario → integración. Depende de la tabla `reservation_information` (V1, `UC03·T006`) y de `saveCalculatedAmounts` (T007).
- **Errores, idempotencia y concurrencia (T017–T021)**: después del flujo feliz; T019 depende de T007/T011.
- **Polish y CE (T022–T028)**: al final; T023/T026 dependen de T008/T011.
- **Riesgo de secuencia**: la seguridad (401/403) está **diferida** (OQ-UC04-01; D-UC04-09); el `saveCalculatedAmounts` (T007) y el bloqueo asesor deben coordinarse con el adaptador de UC03 (misma pieza de UC03). **T004** depende de que el plan de UC11 incluya `FailureRecorderPort`/`operational_failure` (D-UC04-08).

## Notes

- El SPEC 04 **no** define: meta de rendimiento (OQ-UC04-03), mecanismo de autenticación (OQ-UC04-01) ni el detalle de la carrera con UC03 más allá del `retryable` (OQ-UC04-02/OQ-UC04-04) → `[NEEDS CLARIFICATION]`/`[PEND]` con propuesta por defecto registrada.
- **Determinismo (D-17 / OQ-07 cerrada)**: UC04 recalcula siempre desde la información registrada y los parámetros congelados; la mención del contrato a "se devuelven los ya registrados" es la consecuencia (D-UC04-01).
- **Redondeo (D-UC04-04)**: escala 4 interna → HALF_UP a 2 decimales por monto; el total es la suma exacta de los tres montos redondeados; UC05 cobra ese total.
- **Depósito (D-UC04-03)**: 10 % de la `base_rate` **registrada** (ya con tarifa dinámica de UC02); UC04 no reconsulta Flota ni recalcula la tarifa (CE-002).
- **Sin migración**: UC04 usa las columnas calculadas ya creadas por la migración inicial V1 de UC03 («V1 SIEMPRE», D-UC03-01).
- `ReservationInformation`/`ReservationId`/`ReservationInformationRepository` son de **UC03**: UC04 los consume y **amplía** el puerto con `saveCalculatedAmounts` (D-UC04-05); la firma se publica en «Firmas referenciadas por otros planes».
- `FinancialParameters`/`FinancialParametersRepository` son de **UC11**: UC04 los consume por nombre; requiere `insurance_fee_per_passenger` y `commission_pct` (RNF-008).
- `FailureRecorderPort`/`operational_failure` son **compartidos** y están **pendientes en UC11** (D-UC04-08): UC04 los referencia por nombre y usa `InMemoryFailureRecorder` en pruebas; registra en 422/503, no en 404 (D-UC04-06).
- **Seguridad diferida** (D-UC04-09 / OQ-UC04-01): no se implementa `SecurityConfig` ni pruebas 401/403 en esta fase, por coherencia con UC01; los contratos siguen documentando `401`/`403`.
- Etiquetas usadas: `[SPEC]` (SPEC 04 y contrato), `[CONV]` (general-plan e inferido), `[PEND]`/`[NEEDS CLARIFICATION]` (sin definir).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Migración/Contratos, Estrategia de testing, fases con `T0NN`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-09` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]`, `[CONV]`, `[PEND]` o `[NEEDS CLARIFICATION]`.
- [ ] Discrepancias `D-UC04-01` a `D-UC04-09` en «Discrepancias y puntos abiertos» con decisión explícita, sin resolución silenciosa; `D-UC04-08` remite a la pieza compartida pendiente.
- [ ] Preguntas abiertas definidas en el propio plan (OQ-UC04-01 a OQ-UC04-04), con remisión a las OQ del general (OQ-01, OQ-06, OQ-07) y a OQ-UC01-01/OQ-UC03-05 cuando corresponden.
- [ ] Cada RF/RNF/CE/HU del SPEC 04 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce004_…`.
- [ ] Migración: UC04 **no** aporta DDL; solo escribe columnas ya creadas por la V1 de UC03.
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§13) aplicadas; sin Flota/UC02/tarifa dinámica en UC04 (CE-002).
- [ ] «Firmas referenciadas por otros planes» declarado: `CalculateReservationValueUseCase` no se expone; se publica la ampliación `saveCalculatedAmounts` del puerto de salida de UC03.
- [ ] Referencias por nombre: `ReservationInformationRepository` (UC03, ampliado), `FinancialParametersRepository` (UC11), `FailureRecorderPort`/`operational_failure` (compartida, condicionada), fases Setup/Foundational (`UC11·T001`–`UC11·T009`).
- [ ] Seguridad diferida registrada como OQ-UC04-01/D-UC04-09, sin `SecurityConfig` ni pruebas 401/403.
