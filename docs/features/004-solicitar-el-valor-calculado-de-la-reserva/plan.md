# Implementation Plan: UC04 - Solicitar el Valor Calculado de la Reserva

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC04 es el caso de uso **síncrono** por el que el Sistema de Reservas y Operaciones solicita el **valor financiero definitivo** de una reserva identificada por su `reservation_id` [SPEC RF-001]. El sistema recupera la `InformaciónDeReserva` previamente registrada por UC03 (tarifa base, fechas, pasajeros, propietario y capacidad; RF-002) y calcula: **monto de alquiler** = `tarifa base registrada × días inclusivos` [RF-003], **seguro náutico** = `tarifa de seguro × pasajeros` [RF-004], **depósito de garantía** = `10 % de la tarifa diaria vigente registrada` (la misma del alquiler, con la dinámica ya resuelta) **sin** multiplicar por duración, pasajeros ni daño [RF-005, HU1] y **valor total** = alquiler + seguro + depósito, que es el monto que se cobra en "Procesar cobro" [RF-006]. Devuelve el desglose completo [RF-007] y **actualiza la fila interna** con los montos calculados, la **referencia a la versión usada** (`frozen_parameters_id`) y `calculated_at` —los valores aplicados (comisión % y tarifa de seguro por pasajero) se resuelven de esa versión y **no se copian** en la reserva—, dejándola disponible para UC05 [RF-008, CE-004].

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]. Adaptador de entrada REST (`POST /api/v1/reservations/{reservation_id}/calculated-value`, sin cuerpo) que invoca exclusivamente `application.port.in`; lógica de cálculo pura en `ReservationValueCalculator` (dominio, nombrado en general-plan §3.3); persistencia JPA sobre `reservation_information` (tabla de UC03) y lectura de la **versión vigente** de `financial_parameters` (tabla versionada de UC11) **por nombre** solo cuando la reserva no tiene referencia de versión (el recálculo determinista usa la versión referenciada, resuelta por el adaptador de UC03). Errores en *Problem Details* (RFC 9457) con el catálogo de `contracts/README.md` §3.4 y orden E1→E2→E3→E4→E6 (sin filas de autenticación: seguridad fuera de alcance; E5 **no aplica**: UC04 no llama a ninguna dependencia externa) [contrato UC04 §5; README §4.3].

**Decisiones clave del plan** (confirmadas con el responsable):
- **D-17 (general-plan)**: UC04 **recalcula siempre** a partir de la información registrada y de la **versión de parámetros referenciada** en la reserva (`frozen_parameters_id`; la vigente si no hay referencia), de modo que cada solicitud devuelve el mismo desglose (determinista) e idempotente [general-plan D-17; SPEC casos extremos].
- **Redondeo**: precisión interna de 4 decimales; las salidas monetarias se redondean `HALF_UP` a 2 decimales (`contracts/README.md` §3.1) [SPEC RNF-002].
- **Depósito**: 10 % de la tarifa diaria vigente registrada (la misma del alquiler, con la tarifa dinámica ya resuelta); general-plan D-29 [SPEC RF-005].
- **Concurrencia** (D-UC04-05): la lectura-modificación-escritura se ejecuta en una transacción única y se serializa con el bloqueo asesor por reserva (`pg_advisory_xact_lock`; general-plan D-15), compartido con el *upsert* de UC03.
- **Registro de fallos**: el SPEC 04 pide "registra el fallo" en los casos de información incompleta y de parámetros sin configurar → UC04 usa `FailureRecorderPort` **por nombre**. El `404` **no** registra (estado transitorio esperado; ver D-UC04-06).

UC04 corresponde a la **fase 5** de la hoja de ruta [general-plan §10]: depende de UC03 (crea `reservation_information` y publica su repositorio) y del núcleo compartido de parámetros financieros. **No depende directamente de UC02**: la tarifa vigente ya quedó registrada por UC03. **No persiste tablas nuevas ni define migraciones**: rige la migración inicial única (general-plan §4.2, D-31).

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
| **RNF-002** `BigDecimal` con precisión interna de 4 decimales | `Money` (de UC02), `ReservationValueCalculator` | T006, T008 | T009, T022 |
| **RNF-003** manejo de errores robusto (info ausente/incompleta, seguro no configurado) | `CalculateReservationValueService` + `FailureRecorderPort` + `GlobalExceptionHandler` (núcleo compartido) | T004, T011, T012 | T014, T018, T024 |
| **CE-001** precisión: total = suma exacta, cero errores de redondeo | `ReservationValueCalculator` + aceptación | T008 | **T022 `ce001_…`** |
| **CE-002** cálculo solo desde la información registrada (sin Flota / tarifa dinámica) | `ReservationValueCalculator`, `CalculateReservationValueService` + ArchUnit | T008, T011, T026 | **T023 `ce002_…`** |
| **CE-003** sin información registrada → error controlado | `CalculateReservationValueService`, `GlobalExceptionHandler` (núcleo compartido) | T011, T012 | **T024 `ce003_…`** |
| **CE-004** desglose disponible internamente para "Procesar cobro" | `saveCalculatedAmounts` + integración | T007, T011 | **T025 `ce004_…`** |
| **HU1** (P1) | Fases 3–5 | T010–T028 | T013–T028 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC04: Spring Web MVC, Validation, Jackson, Spring Data JPA, MapStruct, ArchUnit y Testcontainers PostgreSQL. Las dependencias se documentan en el setup común del repositorio, sin editar el `pom.xml` en silencio.
**Storage**: PostgreSQL 16+ — **escritura/lectura** de `reservation_information` (tabla de UC03/UC04; UC03 la crea y su adaptador resuelve el congelado con un join de solo lectura, UC04 la completa) y **solo lectura de la versión vigente** de `financial_parameters` (tabla versionada de UC11). `NUMERIC(18,4)` para dinero y porcentajes, `timestamptz` para instantes [SPEC general-plan §4]. **Sin tablas propias ni migraciones** (rige la migración inicial única, general-plan D-31)
**Messaging**: No aplica (REST síncrono; sin RabbitMQ en UC04) [contrato UC04]
**Testing**: JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers (PostgreSQL), ArchUnit [CONV]; los fallos se prueban con un doble `InMemoryFailureRecorder` del puerto compartido
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: Sin meta propia: se heredan los RNF del SPEC y los límites del plan general. Operación puntual síncrona, sin SLA específico **[CONV]**
**Constraints**: la precisión interna es de 4 decimales y las salidas monetarias se redondean `HALF_UP` a 2 decimales (`contracts/README.md` §3.1); el cálculo se basa **exclusivamente** en la información registrada por UC03, sin recalcular ni volver a consultar la tarifa base ni la dinámica (CE-002); parámetros congelados por reserva mediante la **referencia a la versión usada** (RF-008, D-27): los valores aplicados se resuelven de esa versión y **no se copian**; sin cálculos ni cambios parciales ante cualquier error (RNF-003, principio rector 4); una sola transacción por solicitud
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

Contratos/piezas de otros planes (solo referencia; **no se re-planifican**): plan de UC03 (`ReservationInformation`, `ReservationId`, `ReservationInformationRepository` y el esquema de la tabla en la migración inicial única) y núcleo compartido (`FinancialParameters`, `FinancialParametersRepository`, `ProblemDetailsConfig`, `DomainException`, `FailureRecorderPort`, `operational_failure`).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── model/
│   │   └── ReservationInformation.java              # de UC03·T010 — consumido; UC04 añade el método de transición (T007)
│   ├── valueobject/
│   │   ├── ReservationId.java                       # de UC03·T008 — consumido por nombre
│   │   ├── Money.java                               # de UC02·T006 — consumido por nombre [general-plan §11, D-11]
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
│   │   ├── ReservationInformationRepository.java    # de UC03·T014 — consumido; UC04 amplía con saveCalculatedAmounts (T007)
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
    └── adapter/out/persistence/
        └── (extensión del adaptador de UC03)        # T007/T016: implementa saveCalculatedAmounts en el adaptador de UC03
                                                      # (misma pieza de UC03; sin duplicar el adaptador)

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── ArchitectureTest.java                        # T002 (base) + T026 (reglas UC04)
├── domain/valueobject/
│   └── ReservationValueTest.java                    # T009 [CONV núcleo: la transición recibe UUID, no el VO]
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

`ReservationInformation`, `ReservationId` y `ReservationInformationRepository` son de **UC03** (dueño de `reservation_information`; UC03 la crea, UC04 la completa). `FinancialParameters`/`FinancialParametersRepository`, `Money`, `Percentage`, `FailureRecorderPort` y `operational_failure` pertenecen al núcleo/catálogo compartido y se consumen por nombre; UC04 no los implementa ni redeclara.

**Firmas referenciadas por otros planes** [general-plan §3.5]: UC04 **no expone puertos de entrada a otros planes** (`CalculateReservationValueUseCase` lo consume únicamente su adaptador REST). Como completador de `reservation_information`, UC04 **amplía la firma del puerto de salida** `ReservationInformationRepository` (cuyo dueño es UC03) con la operación que faltaba para guardar los montos calculados sin anular el bloque informativo. Firma publicada [CONV]:

```java
public interface ReservationInformationRepository {
    // --- puerto definido por UC03 (upsert con invalidación del bloque informativo) ---
    Optional<ReservationInformation> findByReservationId(ReservationId reservationId);
    ReservationInformation save(ReservationInformation information);

    // --- UC04·T007: completa montos + frozen_parameters_id + calculated_at ---
    //   NO anula el bloque informativo; transacción única con pg_advisory_xact_lock(reservationId);
    //   debe ejecutarse sobre la fila ya existente (si no existe, el servicio no la crea: responde 404).
    ReservationInformation saveCalculatedAmounts(ReservationInformation information);
}
```

Además, el dominio `ReservationInformation` (UC03) incorpora un **método de transición** para construir la versión con montos calculados (T007; coordinado con el plan de UC03):

```java
ReservationInformation withCalculated(ReservationValue value,
                                      UUID frozenParametersId,
                                      Instant calculatedAt);
```

[CONV núcleo, congelado] `ReservationInformation` persiste únicamente la **referencia** `frozen_parameters_id` (UUID, FK a `financial_parameters`); el VO compartido `FrozenParameters` (`parametersId`, `commissionPct`, `insuranceFeePerPassenger`, general-plan §11) representa los valores resueltos de esa versión y se obtiene con `FinancialParametersRepository.findById(...)` (D-27); no se copia en la reserva. La transición congelada recibe solo el UUID, no el VO.

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
9. **Congelar la referencia de versión** [SPEC RF-008; general-plan D-27]: al calcular se registra la **referencia a la versión vigente** de `financial_parameters` (la de mayor `version_number`, leída con `findCurrent()`) en `frozen_parameters_id` (FK a la fila usada); los valores aplicados (**comisión %** y **tarifa de seguro por pasajero**) **no se copian**: se resuelven de esa versión (la comisión % la usa UC10 en la liquidación; el seguro, este cálculo). No es retroactivo: un cambio posterior en UC11 (nueva versión) no altera la referencia ni los valores resueltos.
10. **Actualizar la información interna** [SPEC RF-008; contrato regla 6]: se escriben en `reservation_information` los cuatro montos, la referencia `frozen_parameters_id` y `calculated_at`, dejando el desglose disponible para UC05 **sin recalcular** (CE-004). La escritura ocurre en una **única transacción**; no se anula el bloque informativo (fechas, tarifa base, pasajeros, propietario, capacidad).
11. **Invalidación por UC03** [SPEC RF-008, casos extremos; UC03 casos extremos]: si UC03 vuelve a registrar la reserva, su *upsert* deja los montos calculados, `frozen_parameters_id` y `calculated_at` en `NULL`; UC04 **debe recalcular** antes de un nuevo cobro. UC04 siempre recalcula en cada solicitud, de modo que tras una invalidación una nueva llamada regenera el desglose con la versión vigente en ese momento (y su referencia).
12. **Solicitud repetida / determinismo** [SPEC casos extremos; general-plan D-17]: el cálculo se hace desde la misma información registrada y la **versión de parámetros referenciada** (`frozen_parameters_id`; la vigente solo si no hay referencia), por lo que repetir la solicitud devuelve el mismo desglose y la actualización interna es idempotente (sobrescribe con valores equivalentes). La mención del contrato a "se devuelven los ya registrados" es la **consecuencia** de D-17 (D-UC04-01).
13. **Sin consultar Flota ni recalcular la tarifa** [SPEC CE-002; general-plan §3.5]: `CalculateReservationValueService` y `ReservationValueCalculator` **no** dependen de `ProvideBaseRateUseCase`, `FleetRatePort` ni `DynamicRatePolicy`; solo usan la `base_rate` registrada.
14. **Información registrada incompleta** [SPEC RNF-003, casos extremos; contrato regla 10]: si la fila existe pero no permite calcular (p. ej. `base_rate` nula), ⇒ `422 RESERVATION_INFO_INCOMPLETE` (E6), **sin** cálculo parcial. Es una vía **defensiva** de integridad: el diseño de UC03 (columna `base_rate NOT NULL`) garantiza que no se persista información incompleta (D-UC04-02).
15. **Parámetros no configurados** [SPEC casos extremos; contrato regla 11]: si no existe **versión vigente** de `financial_parameters` o `insurance_fee_per_passenger`/`commission_pct` es nulo, ⇒ `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED` (E4); **no** se calcula ni el seguro ni un total parcial y **no** se escribe nada. Se requieren **ambos** valores en la versión que se referencia: el seguro para calcular y la comisión para que la versión referenciada quede completa (decisión aplicada). Un recálculo con referencia siempre resuelve la versión referenciada (FK; la tabla es append-only).
16. **Registro de fallos** [SPEC casos extremos; D-UC04-06]: los casos 422 (información incompleta) y 503 (parámetros no configurados) **registran el fallo** en `operational_failure` vía `FailureRecorderPort` (por nombre, D-UC04-08), porque el SPEC 04 lo exige textualmente. El `404` **no** registra: es un estado transitorio esperado (Reservas puede reintentar; carrera con UC03).
17. **Orden de validación** [CONV; README §4.3/§4.5]: `E1 → E2 → E3 → E4 → E6` (sin filas de autenticación: seguridad fuera de alcance; `E7` sin uso). La existencia de la reserva (E3) se evalúa antes de leer los parámetros (E4) y de comprobar la completitud (E6); una petición mal formada (E2) nunca llega a la base de datos. `E5` **no aplica** (UC04 no llama a ninguna dependencia externa).
18. **Concurrencia y atomicidad** [general-plan D-15]: la lectura-modificación-escritura se ejecuta en una **transacción única** y se serializa con un **bloqueo asesor de PostgreSQL por `reservation_id`** (`pg_advisory_xact_lock`), de modo que un *upsert* concurrente de UC03 no pierde la escritura de montos ni viceversa. Un fallo en cualquier paso deja la fila intacta (ni montos nuevos ni invalidación parcial).
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

La operación de UC04 sobre la tabla es `SELECT … FOR UPDATE`/`UPDATE` de esas columnas **sin** tocar el bloque informativo. El adaptador de UC03 ya anula estas columnas (incluida `frozen_parameters_id`) en su *upsert* (invalidación, `UC03·T016`); UC04 las completa con los montos, la **referencia a la versión usada** (`frozen_parameters_id`) y `calculated_at`. Los valores aplicados **no se escriben**: se resuelven de la versión referenciada al leer el agregado (join de solo lectura en el adaptador de UC03, D-27; `UC03·T015`). No hay índices ni columnas nuevas.

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
| 503 | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | Primer cálculo (sin referencia): no existe versión vigente de parámetros, o faltan la tarifa de seguro o la comisión (valor nulo) | Sí (cuando el Administrador Financiero configure) | [SPEC casos extremos] E4 |
| 500 | `INTERNAL_ERROR` | Error no previsto | Sí | [CONV] E8 |

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

Fuente: `general-plan.md` (Technical Context), contrato UC04 y SPEC 04. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %. Las pruebas de fallos usan `InMemoryFailureRecorder` (de `UC02·T004/T016`) como doble de `FailureRecorderPort` (D-UC04-08).

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | `ReservationValue` (la referencia congelada es `UUID frozenParametersId`; el VO `FrozenParameters` representa los valores resueltos), fórmula (RF-003–RF-006), días inclusivos, depósito sin multiplicadores, precisión y redondeo a 2 dec (RNF-002, D-UC04-04) | `domain/valueobject/ReservationValueTest`, `domain/service/ReservationValueCalculatorTest` |
| Unitario (`application`) | orquestación: 404 sin fila, 422 incompleta, 503 parámetros ausentes/nulos, escritura única, registro de fallos, determinismo (D-17) | `CalculateReservationValueServiceTest` (Mockito sobre `ReservationInformationRepository`, `FinancialParametersRepository`, `FailureRecorderPort` + `java.time.Clock` fijo) |
| Contrato / Web (MockMvc) | cuerpo y códigos del contrato: 200 feliz, 400, 404 `retryable`, 422, 503, 500 | `ReservationValueControllerContractTest` |
| Integración (Testcontainers PostgreSQL) | `saveCalculatedAmounts` escribe montos + `frozen_parameters_id` + `calculated_at`; repetición idempotente; una nueva versión de parámetros globales no altera la referencia ni los valores resueltos; la lectura expone `frozenParametersId` y UC04 resuelve los valores con `FinancialParametersRepository.findById(...)`; tras invalidación de UC03, UC04 recalcula; bloqueo asesor serializa UC04/UC03 | `ReservationValuePersistenceAdapterTest` |
| Aceptación | CE-001…CE-004 | `ReservationValueAcceptanceTest` |
| Arquitectura | reglas §3.4; CE-002 (sin Flota/UC02/tarifa dinámica) | `arch/ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-004)** — nóminal `ce00X_<descripcion>`:

- **`ce001_total_es_suma_exacta_sin_errores_de_redondeo`** (CE-001, T022): prueba parametrizada con el ejemplo del contrato y casos con tarifas/seguros de 4 decimales, distintos días inclusivos y pasajeros; verifica con `BigDecimal` que `total_amount == rental_amount + insurance_amount + guarantee_deposit_amount` exactamente y que cada monto quedó a 2 decimales HALF_UP, con cero discrepancias.
- **`ce002_calculo_solo_desde_informacion_registrada`** (CE-002, T023): (a) ArchUnit — UC04 no depende de `ProvideBaseRateUseCase`, `FleetRatePort` ni `DynamicRatePolicy`; (b) integración con WireMock de Flota — **cero** peticiones a Flota durante UC04 y el cambio de tarifa en el stub no altera el desglose (se usa la `base_rate` registrada).
- **`ce003_sin_informacion_registrada_respuesta_controlada`** (CE-003, T024): MockMvc/integración — solicitudes de reservas sin información registrada responden `404 RESERVATION_INFO_NOT_FOUND` con *Problem Details* completo, sin cálculo parcial y sin provocar fallos; el 100 % de los casos queda con error controlado. Se cubre además `422` (info incompleta) y `503` (parámetros ausentes).
- **`ce004_desglose_disponible_para_procesar_cobro`** (CE-004, T025): integración (Testcontainers) — tras un cálculo exitoso, la fila queda con los cuatro montos, `frozen_parameters_id` y `calculated_at` (los valores aplicados se resuelven de la versión referenciada); una lectura de UC05 (por repositorio) obtiene el desglose **sin recalcular**, en el 100 % de los casos.

Además: la regresión de consumidores **no forma parte de este plan** (UC05 lee la tabla por nombre; aquí solo se deja el desglose persistido).

## Discrepancias y decisiones

Registro de contradicciones detectadas al elaborar este plan y de las decisiones adoptadas para resolverlas. Los IDs `D-UC04-xx` son propios de este plan; los `D-xx` sin prefijo son decisiones del plan general (§9) o de los planes de UC02/UC03.

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC04-01** | El contrato describe la solicitud repetida como devolver "los ya registrados"; rige D-17: UC04 **recalcula siempre** desde la información registrada y la **versión de parámetros referenciada** (`frozen_parameters_id`; la vigente si no hay referencia) | Contrato UC04 §3 regla 7; general-plan D-17 | Rige D-17: UC04 recalcula siempre desde la información registrada y la versión referenciada (`frozen_parameters_id`; la vigente si no hay referencia); el efecto es determinista y equivalente a devolver los ya registrados | Decidida |
| **D-UC04-02** | El SPEC 04 asume que puede existir información registrada incompleta por una falla previa de UC03, pero el plan de UC03 **nunca** persiste información incompleta | SPEC 04 casos extremos vs plan UC03 (columna `base_rate NOT NULL` en el esquema, regla 10 de UC03) | `422 RESERVATION_INFO_INCOMPLETE` queda como vía **defensiva** de integridad; el caso normal de "aún no registrada" es `404`. Se conserva el código por estar en el catálogo | Decidida |
| **D-UC04-03** | La base del depósito de garantía quedó fijada transversalmente | SPEC 04 HU1/RF-005; general-plan D-29 | El depósito es el **10 % de la tarifa diaria vigente registrada** (la misma del alquiler, con la tarifa dinámica ya resuelta); UC04 no consulta Flota ni recalcula la tarifa base | Aplicada |
| **D-UC04-04** | RNF-002 exige precisión interna y el contrato ilustra montos con dos decimales | SPEC 04 RNF-002/CE-001; `contracts/README.md` §3.1 | Política del catálogo aplicada: `BigDecimal` con **4 decimales internos** y toda salida monetaria redondeada `HALF_UP` a **2 decimales**; CE-001 la verifica | Aplicada |
| **D-UC04-05** | La firma publicada por UC03 de `ReservationInformationRepository` no permite guardar montos: su `save` **anula** las columnas calculadas | `general-plan.md` §4 (tabla entidad de UC03/UC04) vs plan UC03 («Firmas referenciadas por otros planes») | UC04 **amplía** el mismo puerto con `saveCalculatedAmounts` (implementado en el adaptador de UC03; misma pieza de UC03). Se publica la firma en «Firmas referenciadas por otros planes». Concurrencia con `pg_advisory_xact_lock(reservation_id)` | Decidida |
| **D-UC04-06** | El SPEC 04 pide "registra el fallo" en los casos de info incompleta y seguro no configurado; no lo pide para "no existe información" | SPEC 04 casos extremos vs ausencia para el `404` | UC04 usa `FailureRecorderPort` por nombre en 422 y 503; el 404 **no** registra (estado transitorio esperado, reintentable). A diferencia de UC01, el SPEC 04 sí lo exige | Decidida |
| **D-UC04-07** | `DesgloseValorReserva`/`SolicitudValorReserva` (SPEC 04) no están en el lenguaje ubicuo §11 | SPEC 04 entidades clave vs general-plan §11 | `ReservationValue`/`ReservationValueResult` (resultado) y `CalculateReservationValueCommand` (solicitud); `InformaciónDeReserva` → `ReservationInformation` (ya en §11) | Decidida **[CONV]** |
| **D-UC04-08** | El registro de fallos es una pieza transversal | `general-plan.md` §3.5/§4 | UC04 consume `FailureRecorderPort` por nombre; no lo implementa ni redeclara. Su registro usa la transacción independiente y el procedimiento de reproceso del catálogo común | Aplicada |
| **D-UC04-09** | El SPEC/contrato fijan *quién* llama (solo Reservas); la seguridad está fuera de alcance | SPEC 04 RF-001; general-plan §7.1 y D-21 | **Seguridad fuera de alcance**: el sistema no implementa autenticación ni autorización; los contratos ya no documentan 401/403 y no hay `SecurityConfig` ni pruebas de seguridad | Aplicada |
| **D-UC04-10** | ¿Se copian los parámetros aplicados en la reserva o basta la referencia a la versión? Detectada redundancia entre `frozen_parameters_id` y las copias `commission_pct_applied`/`insurance_fee_per_passenger_applied` | `general-plan.md` §4/D-27 (versión por FK) vs plan UC03/UC04 (copias) | **Sin copias**: `frozen_parameters_id` es la única fuente y el adaptador de UC03 resuelve los valores con un **join de solo lectura** al leer el agregado (la lógica de cálculo no cambia: el recálculo usa la misma versión). Alternativas descartadas: mantener copias + FK (posible divergencia) y resolver por el puerto de UC11 desde cada consumidor (acoplamiento) | Decidida |

## Implementation Phases

> **Convención**: cada tarea `T0NN` es una unidad de trabajo granular y verificable de forma independiente; su casilla (`[ ]`) es el mecanismo de seguimiento. Las dependencias con otros casos de uso se expresan mediante puertos, firmas y tablas, no mediante numeración de tareas externas.

### Phase 1: Setup

- [ ] **T001** · `pom.xml`: verificar las dependencias web, validación, persistencia, test y ArchUnit requeridas, sin editar dependencias en silencio.
- [ ] **T002** · `application.properties` (datasource, `X-Correlation-Id`) y base de `ArchitectureTest` (reglas §3.4).

### Phase 2: Foundational

- [ ] **T003** · `DomainException` (`domain/exception`), base del árbol de excepciones del núcleo compartido.
- [ ] **T004** · consumir por nombre `FailureRecorderPort` y `operational_failure`; el registro usa `REQUIRES_NEW`, `reservation_id` nullable, `payload_ref` referencial y procedimiento de reproceso.
- [ ] **T005** · consumir por nombre `ReservationInformation`, `ReservationId`, `ReservationInformationRepository`, `FinancialParameters`, `FinancialParametersRepository` y `Money`; UC04 no los implementa ni los redeclara.
- [ ] **T006** · VO de dominio `ReservationValue` (`rentalAmount`, `insuranceAmount`, `guaranteeDeposit`, `totalAmount` como `Money`) [SPEC "DesgloseValorReserva"; D-UC04-07]. El VO compartido `FrozenParameters` (general-plan §11) representa los valores resueltos de la versión referenciada y se consume por nombre; **no se persiste**: `reservation_information` guarda solo `frozen_parameters_id` (UUID) y la firma congelada de la transición es `withCalculated(ReservationValue, UUID, Instant)`. UC04 resuelve los valores con `FinancialParametersRepository.findById(...)` (D-27).
- [ ] **T007** · Extensión del puerto de UC03 (D-UC04-05): método de transición `ReservationInformation.withCalculated(ReservationValue, UUID, Instant)` y ampliación del puerto `ReservationInformationRepository` con `saveCalculatedAmounts(...)` (firma publicada en «Firmas referenciadas por otros planes»); el adaptador JPA de UC03 implementa el método como `UPDATE` de los montos + `frozen_parameters_id` + `calculated_at` **sin** anular el bloque informativo, en transacción única con `pg_advisory_xact_lock(reservation_id)`. No escribe copias de parámetros (D-UC04-10).
- [ ] **T008** · `ReservationValueCalculator` (`domain/service`): `ReservationValue calculate(ReservationInformation info, FinancialParameters params)`. Fórmulas de RF-003–RF-006 y días inclusivos; depósito = **10 % de la tarifa diaria vigente registrada** (general-plan D-29), con precisión interna de 4 decimales y salidas monetarias redondeadas `HALF_UP` a 2 decimales (`contracts/README.md` §3.1).
- [ ] **T009** · Pruebas unitarias de VOs y del calculator: ejemplo del contrato (`900000.00/60000.00/30000.00/990000.00`), `start == end` (1 día), casos con 4 decimales y redondeo, depósito no multiplicado por días/pasajeros (RF-003–RF-006, RNF-002).
- [ ] **T010** · `CalculateReservationValueUseCase` + `CalculateReservationValueCommand` (`ReservationId`) + `ReservationValueResult` (los cuatro montos) + excepciones de dominio `ReservationInfoNotFoundException`, `ReservationInfoIncompleteException` y `FinancialParametersNotConfiguredException` [RNF-001; general-plan §3.5].

### Phase 3: US1 — Flujo feliz de cálculo y persistencia (HU1; RF-001, RF-002, RF-003, RF-004, RF-005, RF-006, RF-007, RF-008; CE-001, CE-004)

- [ ] **T011** · `CalculateReservationValueService`: `findByReservationId` → E3 (`404` si vacío) → si hay `frozen_parameters_id` (recálculo) resuelve esa versión con `FinancialParametersRepository.findById(...)`; si no, lee `FinancialParametersRepository.findCurrent()` (versión vigente) → E4 (`503` si no existe versión vigente o `insurance`/`commission` nulos, registra fallo) → verifica completitud → E6 (`422` si `base_rate` nula, registra fallo) → `ReservationValueCalculator.calculate(...)` → `saveCalculatedAmounts(...)` en transacción única con bloqueo asesor; **no** invoca UC02 ni Flota; `calculated_at` desde un `java.time.Clock` inyectado.
- [ ] **T012** · `ReservationValueController` (`POST /api/v1/reservations/{reservation_id}/calculated-value`, sin cuerpo) + `ReservationValueResponse` (claves `snake_case`, montos string a 2 dec). **Sin** advice propio: las excepciones de dominio (`ReservationInfoNotFoundException`, `ReservationInfoIncompleteException`, `FinancialParametersNotConfiguredException`) ya se traducen en el `GlobalExceptionHandler` del núcleo compartido (*Problem Details* con `code`/`retryable` del catálogo §3.4). **Sin** reglas de seguridad — fuera de alcance, general-plan §7.1.
- [ ] **T013** · Tests de contrato MockMvc del flujo feliz: `200` con el ejemplo del contrato (4 claves + `reservation_id`), aceptación de `X-Correlation-Id` y `400 VALIDATION_ERROR` con UUID inválido.
- [ ] **T014** · Pruebas unitarias del servicio (Mockito + `InMemoryFailureRecorder` + `Clock` fijo): flujo feliz; una sola escritura; sin fila → `404`; info incompleta → `422` + registro; parámetros ausentes/nulos → `503` + registro; **sin llamadas a Flota**.
- [ ] **T015** · Pruebas de integración del adaptador (Testcontainers PostgreSQL): `saveCalculatedAmounts` escribe los cuatro montos, `frozen_parameters_id` y `calculated_at`; una solicitud repetida con los mismos insumos produce el mismo desglose (idempotencia; el recálculo usa la versión referenciada, no la vigente); una nueva versión de `financial_parameters` no altera la referencia ni los valores resueltos; la lectura expone `frozenParametersId` y la versión referenciada se resuelve con `FinancialParametersRepository.findById(...)`.
- [ ] **T016** · Pruebas de integración: `saveCalculatedAmounts` **preserva** el bloque informativo (fechas, `base_rate`, pasajeros, propietario, capacidad); tras un *upsert* de UC03 (columnas calculadas a `NULL`), una nueva llamada a UC04 recalcula y completa los montos con los parámetros vigentes.

### Phase 4: US1 — Errores, idempotencia y concurrencia (HU1; RF-008, RF-009; RNF-003; CE-002, CE-003)

- [ ] **T017** · Tests de contrato de errores (MockMvc): `404 RESERVATION_INFO_NOT_FOUND` con `retryable: true`, `422 RESERVATION_INFO_INCOMPLETE`, `503 FINANCIAL_PARAMETERS_NOT_CONFIGURED`, `500 INTERNAL_ERROR`; verificación del orden E2 → E3 → E4 → E6.
- [ ] **T018** · Registro de fallos (CE-003): con dobles, el `422` y el `503` quedan en `InMemoryFailureRecorder`; el `404` **no** registra (D-UC04-06); en ningún caso se escribe la fila.
- [ ] **T019** · Concurrencia (general-plan D-15): prueba de integración con Testcontainers — un *upsert* de UC03 (bloqueo asesor del mismo `reservation_id`) y una solicitud de UC04 concurrentes se serializan; no hay pérdida de escritura ni invalidación parcial.
- [ ] **T020** · Determinismo (D-17): dos solicitudes consecutivas (incluso tras cambiar `financial_parameters` sin invalidación) devuelven el mismo desglose (el recálculo usa la **versión referenciada**) y la fila conserva `frozen_parameters_id` y los montos (D-UC04-01, D-UC04-10).
- [ ] **T021** · Manejo del error no controlado → `500 INTERNAL_ERROR` en *Problem Details*, sin detalles internos (README §4.3 regla 9).

### Phase 5: Polish y criterios de éxito — **parcialmente compartido con la Fase general**

- [ ] **T022** · **`ce001_total_es_suma_exacta_sin_errores_de_redondeo`** (CE-001): parametrizada (ejemplo del contrato + tarifas/seguros de 4 decimales, varios días y pasajeros); el total es exactamente la suma de los tres montos a 2 decimales, cero discrepancias.
- [ ] **T023** · **`ce002_calculo_solo_desde_informacion_registrada`** (CE-002): ArchUnit (sin `ProvideBaseRateUseCase`/`FleetRatePort`/`DynamicRatePolicy` en UC04) + integración con WireMock de Flota (cero peticiones; cambiar la tarifa del stub no altera el desglose).
- [ ] **T024** · **`ce003_sin_informacion_registrada_respuesta_controlada`** (CE-003): reservas sin información registrada → `404` con *Problem Details* completo, sin cálculo parcial; se cubren también `422` y `503`.
- [ ] **T025** · **`ce004_desglose_disponible_para_procesar_cobro`** (CE-004): integración — tras el cálculo, la fila tiene los cuatro montos + `frozen_parameters_id` + `calculated_at` (los valores aplicados se resuelven de la versión referenciada) y una lectura por repositorio (UC05) obtiene el desglose sin recalcular.
- [ ] **T026** · ArchUnit (reglas §3.4 y CE-002): `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `application.port.in`; el controller no toca repositorios; la entidad JPA no sale de `adapter.out.persistence`; sin dependencias de Flota/UC02/tarifa dinámica en UC04.
- [ ] **T027** · Documentación y referencias entre planes: documentar la firma de `ReservationInformationRepository.saveCalculatedAmounts` (§«Firmas referenciadas por otros planes»; `UC03` es el dueño del puerto) y registrar las decisiones `D-UC04-01`, `D-UC04-04`, `D-UC04-08` y `D-UC04-10`. No se edita ningún contrato ni SPEC sin acuerdo.
- [ ] **T028** · revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-004.

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
- **Riesgo de secuencia**: la seguridad está **fuera de alcance** (general-plan §7.1; D-UC04-09 aplicada); `saveCalculatedAmounts` (T007) debe respetar el catálogo único de puertos de UC03 y el mecanismo de bloqueo compartido (`pg_advisory_xact_lock` por reserva; general-plan D-15).

## Notes

- **Cálculo determinista (D-17)**: UC04 recalcula siempre desde la información registrada y la versión de parámetros referenciada (`frozen_parameters_id`; la vigente si no hay referencia), con desglose determinista.
- **Redondeo (`contracts/README.md` §3.1)**: precisión interna de 4 decimales; salidas monetarias redondeadas `HALF_UP` a 2 decimales.
- **Depósito (general-plan D-29)**: 10 % de la tarifa diaria vigente registrada (la misma del alquiler, con la tarifa dinámica ya resuelta).

- El SPEC 04 **no** define una meta de rendimiento propia: rige «sin meta propia» (operación puntual síncrona **[CONV]**). La carrera con UC03 se resuelve con el bloqueo asesor por reserva (D-15) y el `404 RESERVATION_INFO_NOT_FOUND` con `retryable: true` (carrera con UC03). La seguridad está **fuera de alcance** (general-plan §7.1).
- **Determinismo (D-17)**: UC04 recalcula siempre desde la información registrada y la versión de parámetros referenciada (`frozen_parameters_id`; la vigente si no hay referencia); la mención del contrato a "se devuelven los ya registrados" es la consecuencia (D-UC04-01). Los valores aplicados no se copian en la reserva (D-UC04-10).
- **Redondeo (D-UC04-04)**: 4 decimales internos; las salidas monetarias se redondean `HALF_UP` a 2 decimales (`contracts/README.md` §3.1).
- **Depósito (D-UC04-03)**: 10 % de la tarifa diaria vigente registrada (general-plan D-29); UC04 no reconsulta Flota ni recalcula la tarifa (CE-002).
- **Sin migraciones propias**: UC04 usa las columnas calculadas de `reservation_information`; rige la migración inicial única (general-plan §4.2, D-31).
- `ReservationInformation`/`ReservationId`/`ReservationInformationRepository` son de **UC03**: UC04 los consume y **amplía** el puerto con `saveCalculatedAmounts` (D-UC04-05); la firma se publica en «Firmas referenciadas por otros planes».
- `FinancialParameters`/`FinancialParametersRepository` pertenecen al núcleo compartido: UC04 los consume por nombre.
- `FailureRecorderPort`/`operational_failure` son **compartidos**: UC04 los referencia por nombre y usa `InMemoryFailureRecorder` en pruebas; registra en 422/503, no en 404 (D-UC04-06).
- **Seguridad fuera de alcance** (general-plan §7.1, D-21; D-UC04-09): el sistema no implementa autenticación ni autorización; no se implementa `SecurityConfig` ni pruebas 401/403 y los contratos ya no documentan `401`/`403`. "Solo el Sistema de Reservas y Operaciones" es el usuario esperado.
- Etiquetas usadas: `[SPEC]` (SPEC 04 y contrato) y `[CONV]` (general-plan e inferido).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, Esquema/Contratos, Estrategia de testing, fases con `T0NN`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-09` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]` o `[CONV]`.
- [ ] Discrepancias `D-UC04-01` a `D-UC04-10` en «Discrepancias y decisiones» con decisión explícita y estado final (decididas o aplicadas).
- [ ] Cada RF/RNF/CE/HU del SPEC 04 trazado a componente, tarea y prueba; pruebas `ce001_…`–`ce004_…`.
- [ ] Esquema: UC04 **no** aporta DDL; solo escribe columnas existentes de `reservation_information`; rige la migración inicial única (general-plan §4.2, D-31).
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§11) aplicadas; sin Flota/UC02/tarifa dinámica en UC04 (CE-002).
- [ ] «Firmas referenciadas por otros planes» declarado: `CalculateReservationValueUseCase` no se expone; se publica la ampliación `saveCalculatedAmounts` del puerto de salida de UC03.
- [ ] Referencias por nombre: `ReservationInformationRepository` (UC03, ampliado), `FinancialParametersRepository`, `FailureRecorderPort`/`operational_failure` y VOs compartidos; sin coordinación por tareas externas.
- [ ] Seguridad fuera de alcance documentada (general-plan §7.1); sin `SecurityConfig` ni pruebas 401/403.
