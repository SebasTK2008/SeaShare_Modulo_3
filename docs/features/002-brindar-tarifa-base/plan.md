# Implementation Plan: UC02 - Brindar Tarifa Base

**Date**: 2026-10-09
**Spec**: [spec.md](spec.md)

## Summary

UC02 es el caso de uso **interno** donde reside exclusivamente la lógica de tarifa dinámica del sistema: al ser invocado mediante `<<include>>` por UC01 ("Solicitar estimación para reserva") o por UC03 ("Brindar información de reserva"), consulta al Sistema de Gestión de Flota la tarifa base de cada embarcación (la única fuente autoritativa, RF-004, **sin cachear**, general-plan D-19) y le aplica el ajuste dinámico vigente para la fecha evaluada —fin de semana o temporada alta— con la regla exacta de calendario de RF-006 (ventanas fijas + Semana Santa por Meeus/Jones/Butcher, RNF-004) y la coincidencia de condiciones resuelta por el **mayor** ajuste [SPEC HU1, casos extremos].

UC02 **no persiste nada propio, no tiene endpoint REST** (RF-003) y devuelve la tarifa final **única y exclusivamente** a la operación invocante. Expone un **único puerto de entrada batch** (`ProvideBaseRateUseCase`) cuya firma publica el bloque A para que UC01 y UC03 lo usen por `port.in` (regla de dependencia 4 del general-plan §3.4 y guía §3.2), con una sola consulta a Flota por invocación (contrato `flota-consulta-tarifas-base.md`). La tarifa final se entrega a **escala 4 sin redondeo** (RNF-002); el redondeo final hacia pantallas, la pasarela o reportes es responsabilidad del llamador (D-UC02-04).

La fecha evaluada viaja **siempre** en el comando (`evaluatedDate`, obligatorio): UC01 resuelve la «fecha actual» del modo lote con el `ClockPort` (puerto compartido intra-bloque A, publicado aquí) para que coincida exactamente con el `evaluation_date` que devuelve [contrato UC01 lote], y UC03/UC01-individual pasan la `start_date` recibida [SPEC RF-001, casos extremos]. Se leen los parámetros financieros (`financial_parameters`, dueño B/UC11) **solo cuando la fecha evaluada exige un porcentaje**; una fecha regular no los necesita [SPEC casos extremos, regla 14]. Ante cualquier falla (Flota caída/timeout, tarifa no disponible, porcentaje faltante) el sistema **registra el fallo** en `operational_failure` vía `FailureRecorderPort` (pieza compartida) **y** propaga una excepción de dominio `BaseRateException` con motivo y marca transitorio/permanente [SPEC casos extremos, RNF-003]; jamás devuelve una tarifa asumida ni un valor parcial [principio rector 4 del general-plan].

Las contradicciones detectadas contra los documentos de `docs/context/` (uso de tipo/categoría, puentes como temporada alta) se resuelven a favor del SPEC 02 y se registran como `D-UC02-01` y `D-UC02-02`. El calendario expone `isHighSeason(fecha)` y `windowsForYear(año)`; la segunda operación **responde la OQ-UC11-05** del plan de UC11 (bloque B la consume para mostrar `high_season_windows`). La pieza compartida `FailureRecorderPort`/`operational_failure` **está pendiente** en el plan de UC11 (D-UC02-11, ver sección «Discrepancias»): UC02 la referencia por nombre, no la implementa y usa un doble de prueba; para avanzar se requiere que UC11 (a rehacer) la incluya en su fase Fundacional.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** consultar a Flota por ID y evaluar la fecha de inicio/actual sin tipo ni categoría | `FleetRatePort`, `FleetRateRestClient`, `BoatBaseRateDto`, `ProvideBaseRateCommand`, `ProvideBaseRateService` | T015, T016, T018, T019 | T017, T021, T023 |
| **RF-002** aplicar `tarifa × (1 + %/100)` | `DynamicRatePolicy`, `FinancialParameters` (de UC11, consumido) | T011, T012, T016 | T012, T023 |
| **RF-003** retorno solo a la operación invocante (sin endpoint) | `ProvideBaseRateUseCase` + ArchUnit | T016, T022 | T022 |
| **RF-004** Flota única fuente autoritativa, sin caché | `FleetRateRestClient` | T019 | T021 |
| **RF-005** no delegar ni duplicar la tarifa dinámica | `DynamicRatePolicy`, `HighSeasonCalendar`, `EasterCalculator` + ArchUnit | T008, T009, T011, T022, T024 | T024 |
| **RF-006** calendario exacto de temporada alta | `HighSeasonCalendar`, `HighSeasonWindow` | T009, T010 | T010, T023 |
| **RF-007** no usar tipo/categoría | `BoatBaseRateDto`, `BaseRateResult` (sin esos campos) + prueba de reflexión | T013, T018, T022, T024 | T024 |
| **RNF-001** DTOs mínimos hacia Flota | `FleetBaseRatesRequestDto`, `BoatBaseRateDto`, `FleetBaseRatesResponseDto`, `FleetRateMapper` | T018 | T021 |
| **RNF-002** `BigDecimal`, precisión interna de 4 decimales | `Money`, `BaseRate` | T006, T007, T011, T012 | T007, T012, T023 |
| **RNF-003** manejo de errores (*timeouts*, *fallbacks*) | Resilience4j en `FleetRateRestClient`, `BaseRateException` | T014, T019, T020, T021 | T021, T025 |
| **RNF-004** Semana Santa determinística | `EasterCalculator` | T008, T010 | T010, T023 |
| **CE-001** precisión financiera | cálculo + tabla parametrizada | T010, T012, T023 | **T023 `ce001_…`** |
| **CE-002** arquitectura: 0 lógica duplicada / 0 tipo-categoría | ArchUnit + reflexión | T022, T024 | **T024 `ce002_…`** |
| **CE-003** resiliencia | WireMock + integración + doble de `FailureRecorderPort` | T021, T025 | **T025 `ce003_…`** |
| **HU1** (3 escenarios: regular, fin de semana, temporada alta) | Fases 3–5 | T008–T021 | T010, T012, T017, T021, T023 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC02: Spring Web (cliente `RestClient`), **Resilience4j** (`resilience4j-spring-boot3`: *timeout*, reintento, *circuit breaker*), MapStruct, ArchUnit y **WireMock** (pruebas del cliente Flota). **Ni Resilience4j ni WireMock están hoy en el `pom.xml`** (solo `data-jpa`, `postgresql`, `mapstruct`, `lombok-mapstruct-binding`, Testcontainers) → se coordina su alta dentro de la fase Setup Compartida (T001), sin editar el `pom.xml` en silencio [guía §6]
**Storage**: PostgreSQL 16+ (**sin tablas propias**): UC02 solo **lee** `financial_parameters` (tabla de UC11, dueño B) y **escribe** en `operational_failure` (pieza técnica compartida, ver D-UC02-11) [SPEC general-plan §4] — `NUMERIC(18,4)` para dinero y porcentajes, `timestamptz` para instantes
**Testing**: JUnit 5, AssertJ, Mockito, AssertJ, WireMock (contratos de Flota), Reactor de Resilience4j, ArchUnit, Awaitility (circuit breaker) [CONV]; el registro de fallas se prueba con un doble en memoria de `FailureRecorderPort` [CONV]
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: Sin meta propia del SPEC 02 `[NEEDS CLARIFICATION: OQ-UC02-02; el SPEC 02 no define metas de rendimiento para este caso de uso; hereda el SLA de Flota < 500 ms para lote de 50 embarcaciones — SPEC 01 HU3]`
**Constraints**: `BigDecimal` con precisión interna de 4 decimales **sin redondeo final en UC02** (RNF-002, D-UC02-04); Flota es la única fuente de la tarifa base y **no se cachea** (RF-004, D-19); sin tipo ni categoría de embarcación (RF-007); calendario exacto sin configuración manual de fechas (RF-006); nunca tarifa asumida ni valor parcial (RNF-003, casos extremos)
**Scale/Scope**: 1 puerto de entrada interno (solo batch, RF-003) [SPEC general-plan §3.5]; 1 contrato de salida a Flota [contrato `flota-consulta-tarifas-base.md`]; 7 RF + 4 RNF + 3 CE + 1 HU del SPEC 02

**Estado actual del repositorio (relevante para UC02)**: igual que en UC11 — existen `SeashareM3Application.java`, `application.properties` (solo `spring.application.name=seashare-m3`), `TestcontainersConfiguration`, `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** los paquetes `domain`/`application`/`infrastructure`, migraciones compartidas de `operational_failure`, `Money`/`BoatId`, ni los puertos `ClockPort`/`FailureRecorderPort`.

## Project Structure

### Documentation (this feature)

```text
docs/features/002-brindar-tarifa-base/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                        # Leyenda [SPEC]/[CONV]/[PEND], catálogo de errores §3.4, tablas §4
└── external/
    └── flota-consulta-tarifas-base.md                # Consulta por lote a Flota (única conexión externa de UC02)
```

Contratos de los **llamantes** (solo referencia de costura; no se re-planifican): `rest/UC01-estimacion-lote.md`, `rest/UC01-estimacion-individual.md`, `events/UC03-informacion-reserva.md`. Contrato de UC11 que UC02 alimenta: `rest/UC11-obtener-parametros-financieros.md` (campo `high_season_windows`).

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── model/
│   │   └── FinancialParameters.java                 # de UC11 (dueño B) — consumido, no implementado [general-plan §13]
│   ├── valueobject/
│   │   ├── Money.java                               # T006 [general-plan §3.3, D-11; compartido, no está en guía §3.1 → D-UC02-05]
│   │   ├── BoatId.java                              # T006 [general-plan §3.3; compartido, mismo caso]
│   │   ├── BaseRate.java                            # T006 [general-plan §13: "Tarifa base → BaseRate"]
│   │   └── HighSeasonWindow.java                    # T009 [CONV]
│   ├── service/
│   │   ├── EasterCalculator.java                    # T008 [SPEC RF-006, RNF-004; Meeus/Jones/Butcher]
│   │   ├── HighSeasonCalendar.java                  # T009 [SPEC RF-006; expone windowsForYear → responde OQ-UC11-05]
│   │   └── DynamicRatePolicy.java                   # T011 [SPEC RF-002, RF-005, casos extremos; nombre [CONV]]
│   └── exception/
│       ├── DomainException.java                     # T003 [general-plan §3.3; compartido con UC11·T004]
│       └── BaseRateException.java                   # T014 [SPEC RNF-003, casos extremos; nombre [CONV]]
├── application/
│   ├── port/in/
│   │   └── ProvideBaseRateUseCase.java              # T016 [SPEC RF-003; general-plan §3.5; firma publicada §Puertos expuestos]
│   ├── port/out/
│   │   ├── FleetRatePort.java                       # T015 [general-plan §3.5 UC02; no está en guía §3.1 → D-UC02-05]
│   │   ├── ClockPort.java                           # T005 [general-plan §3.3 "reloj"; compartido intra-bloque A para UC01]
│   │   ├── FinancialParametersRepository.java       # de UC11 (dueño B) — consumido por nombre [guía §3.1]
│   │   └── FailureRecorderPort.java                 # Compartido (se define en UC11·T001–T009) — por nombre; D-UC02-11
│   ├── service/
│   │   └── ProvideBaseRateService.java              # T016 [general-plan §3.5; RF-003]
│   └── dto/
│       ├── ProvideBaseRateCommand.java              # T013 [SPEC RNF-001; nombre [CONV]]
│       └── BaseRateResult.java                      # T013 [SPEC "TarifaBaseResultado"; nombre [CONV]]
└── infrastructure/
    ├── adapter/out/fleet/dto/
    │   ├── FleetBaseRatesRequestDto.java            # T018 [SPEC RNF-001; contrato Flota]
    │   ├── BoatBaseRateDto.java                     # T018 [SPEC "EmbarcacionInfo"; contrato Flota]
    │   ├── FleetBaseRatesResponseDto.java           # T018 [SPEC RNF-001; contrato Flota]
    │   └── FleetRateMapper.java                     # T018 [MapStruct; D-04 como referencia]
    ├── adapter/out/fleet/
    │   ├── FleetRateRestClient.java                 # T019 [RNF-003; Resilience4j; general-plan §7.2]
    │   └── FleetRateConfiguration.java              # T020 [general-plan §7.4; `seashare.fleet.*`]
    ├── adapter/out/persistence/?                    # (no aplica: UC02 no persiste; operacional_failure la escribe el adaptador compartido de UC11)
    └── config/
        └── SystemClock.java                         # T005 [implementation de ClockPort; seashare.timezone, general-plan D-12/OQ-04]

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── ArchitectureTest.java                        # T002 (base) + T022, T024 (reglas UC02)
├── domain/valueobject/
│   └── MoneyBoatIdBaseRateTest.java                 # T007
├── domain/service/
│   ├── EasterCalculatorTest.java                    # T010
│   ├── HighSeasonCalendarTest.java                  # T010
│   └── DynamicRatePolicyTest.java                   # T012
├── application/service/
│   ├── ProvideBaseRateServiceTest.java              # T017
│   └── BaseRateAcceptanceTest.java                  # T023 (ce001), T024 (ce002), T025 (ce003)
├── infrastructure/adapter/out/fleet/
│   └── FleetRateRestClientContractTest.java         # T021 (WireMock)
└── common/
    └── InMemoryFailureRecorder.java                 # T004/T016 (doble para pruebas; nombre [CONV])
```

`FinancialParameters` (dominio) y `FinancialParametersRepository` (puerto de salida) **son de UC11** (bloque B, guía §3.1): UC02 los consume **por nombre** y por `port.in`/`port.out`; no los implementa ni los prueba. `FailureRecorderPort`/tabla `operational_failure` son **compartidos** (guía §3.1) — ver **D-UC02-11** y la tarea **T004 (condicionada)**.

**Puertos expuestos** (guía `guia-para-planes-especificos.md` §3.1 y §3.4): UC02 es el primer caso de uso del bloque A y **publica** la firma que UC01 y UC03 deben copiar (guía §3.2: «A empieza UC02»):

```java
public interface ProvideBaseRateUseCase {
    /** Devuelve la tarifa base final (ya con tarifa dinámica) SOLO de las embarcaciones
     *  con tarifa disponible. Los IDs solicitados que no aparecen en el resultado no
     *  tienen tarifa (el llamador correlaciona por boat_id). Falla toda la invocación
     *  (BaseRateException) si Flota no responde o si falta un porcentaje requerido.
     *  Jamás devuelve una tarifa asumida ni valores parciales. */
    List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command);
}

public record ProvideBaseRateCommand(List<BoatId> boatIds,           // obligatorio; vacío ⇒ List.of() sin consultar a Flota
                                     LocalDate evaluatedDate) {}     // obligatorio; fecha de inicio o fecha actual (la resuelve el llamador)

public record BaseRateResult(BoatId boatId, BaseRate baseRate) {}    // tarifa final por unidad de tiempo, escala 4
```

Semántica acordada (D-UC02-09): la «fecha actual» del modo lote la resuelve **UC01** con `ClockPort.today()` y la pasa en el comando, para que coincida exactamente con el `evaluation_date` que UC01 devuelve; UC03 y UC01-individual pasan la `start_date`. **No se publican puertos de salida** (son internos o compartidos): `FleetRatePort`, `ClockPort` y `FinancialParametersRepository`/`FailureRecorderPort` quedan por nombre.

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) en un solo módulo Maven, con subpaquetes temáticos sin reglas entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas del §3.4 aplicadas aquí: `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.out` solo implementa `application.port.out`; los DTOs HTTP/Flota viven en `infrastructure.adapter.out` y `application` trabaja con `command`/`result` (RNF-001); ningún controller accede a un repositorio (**no hay controller en UC02**, RF-003). Identificadores en inglés (D-23) y lenguaje ubicuo §13: *Tarifa base / tarifa dinámica → `BaseRate` / `DynamicRatePolicy`*; `EmbarcacionInfo` y `TarifaBaseResultado` del SPEC 02 no están en §13 → nombres `BoatBaseRateDto` y `BaseRateResult` **[CONV; D-UC02-10]**.

## Reglas de negocio

Todas las reglas provienen del SPEC 02 (`spec.md`) salvo indicación. Ejemplos con tarifa base de Flota `350000.00`, incremento de fin de semana `10 %` y de temporada alta `25 %`.

1. **Fecha evaluada** [SPEC RF-001, casos extremos]: tarifa base de Flota × ajuste dinámico de la **fecha evaluada** = fecha de inicio (UC01-individual, UC03) o fecha actual (lote, resuelta por el llamador y pasada en `evaluatedDate`). La tarifa ajustada se usa para toda la duración de la reserva [SPEC 01 RF-003].
2. **Condición regular** [SPEC RF-002]: `Tarifa final = Tarifa base de Flota`, sin ajuste. Ejemplo: `2026-10-07` (miércoles) → `350000.0000`. No requiere ningún porcentaje configurado (casos extremos).
3. **Fin de semana** [SPEC RF-002]: `Tarifa final = base × (1 + %IncrementoFinDeSemana/100)`. El fin de semana es **sábado y domingo** en la zona horaria de negocio (`seashare.timezone`, `America/Bogota`, general-plan D-12/OQ-04) **[CONV; D-UC02-06; OQ-UC02-04]**. Ejemplo: `2026-10-10` (sábado) con 10 % → `350000.00 × 1.10 = 385000.0000`.
4. **Temporada alta** [SPEC RF-002, RF-006]: `Tarifa final = base × (1 + %IncrementoTemporadaAlta/100)` cuando la fecha evaluada cae en una de las ventanas de la regla 6. Ejemplo: `2027-01-15` (viernes) con 25 % → `350000.00 × 1.25 = 437500.0000`.
5. **Coincidencia de condiciones** [SPEC casos extremos]: si la fecha es fin de semana **y** temporada alta a la vez, se aplica el ajuste que produzca la **tarifa más alta** (equivalente a `max(porcentaje FE, porcentaje TA)`), determinista. Ejemplo: `2026-12-20` (domingo, ventana fin de año) con FE 15 % y TA 25 % → `437500.0000` (mayor).
6. **Calendario exacto de temporada alta** [SPEC RF-006]: las ventanas por año evaluado son:
   - *Fin de año*: del **15 de noviembre** al **15 de enero del año siguiente** (ambos inclusive). Interpretación de intervalo (D-UC02-03): todo `1–15 de enero` es temporada alta (cierra la ventana abierta el 15-nov del año anterior). Ejemplos: `2026-11-15` (domingo) → TA; `2026-11-14` (sábado) → solo FE → `385000.0000`; `2027-01-15` (viernes) → TA; `2027-01-16` (sábado) → solo FE → `385000.0000`.
   - *Mitad de año*: del **1 de junio** al **30 de julio** (inclusive). Ejemplos: `2026-07-30` (jueves) → TA; `2026-07-31` (viernes) → regular → `350000.0000`.
   - *Semana Santa*: **Jueves Santo y Viernes Santo** (dos días) de marzo/abril, derivados del Domingo de Resurrección calculado con **Meeus/Jones/Butcher** (RNF-004). Ejemplo 2026: Pascua `2026-04-05` (domingo) → Jueves Santo `2026-04-02`, Viernes Santo `2026-04-03` → ambos TA; `2026-04-01` (miércoles) regular; `2026-04-04` (sábado) y `2026-04-05` (domingo, Pascua) → solo FE.
   - *Semana de receso*: del **5 de octubre** al **12 de octubre** (inclusive). Ejemplos: `2026-10-12` → TA; `2026-10-13` (martes) → regular.
   - Las ventanas se **recalculan automáticamente cada año**; no se configuran manualmente (solo el porcentaje, vía UC11).
7. **Puentes festivos y fines de semana largos** [SPEC RF-006, casos extremos]: **no** son temporada alta; se evalúan como fin de semana (si el festivo cae en fin de semana) o como día regular; **a excepción de los días santos** (Jueves/Viernes Santo, que son temporada alta). Un festivo entre semana fuera de las ventanas → regular (ningún porcentaje).
8. **Pascua y año evaluado** [SPEC RF-006, RNF-004]: `EasterCalculator.easterSunday(año)` es una función pura de un año; las fechas `Jueves Santo = Pascua − 3 días` y `Viernes Santo = Pascua − 2 días` **[CONV: derivación de los días santos a partir de la Pascua, implícita en el SPEC]**.
9. **Flota: una consulta por invocación** [SPEC RF-005; contrato lote regla 3]: `ProvideBaseRateService` consulta al puerto **una sola vez** con la lista de IDs (deduplicados, D-UC02-08) y aplica la política por embarcación. Sin caché (D-19).
10. **Tarifa base nula o faltante** [SPEC casos extremos; contrato Flota regla 3]: la embarcación no se incluye en el resultado (el llamador correlaciona por `boat_id`) y **UC02 registra el fallo** en `operational_failure` (`reason = BASE_RATE_NOT_AVAILABLE`, transitorio = no). No se inventa un valor.
11. **Tarifa base inválida** [D-UC02-07, tabla de decisión regla 4]: una `base_rate` no numérica, **negativa o cero** en la respuesta de Flota es una **respuesta inválida de la dependencia** → `BaseRateException(FLEET_UNAVAILABLE)`, transitorio = sí; falla toda la invocación (nunca valores parciales).
12. **Flota caída, timeout o inalcanzable** [SPEC casos extremos, RNF-003; contratos UC01]: el puerto lanza `BaseRateException(FLEET_UNAVAILABLE)`; el servicio **registra el fallo** y **propaga** la excepción al llamador para que aplique su propio manejo (UC01 → `503 FLEET_UNAVAILABLE`; UC03 → reintento/DLQ según su plan). Nunca una tarifa asumida.
13. **Porcentaje faltante** [SPEC casos extremos]: si la fecha evaluada exige un porcentaje y no está configurado (fila ausente o campo `null`) → información incompleta → `BaseRateException(DYNAMIC_RATE_NOT_CONFIGURED)`, transitorio = sí (lo corrige el Administrador Financiero); se registra y se propaga. **Si la fecha es regular, ningún porcentaje es necesario** y la tarifa se obtiene aunque no exista configuración. En coincidencia FE+TA se requieren **ambos** porcentajes para poder aplicar el mayor (D-UC02-06, decisión adoptada).
14. **Lectura perezosa de parámetros** [SPEC casos extremos; decisión DUC-02-11/regla]: el servicio solo consulta `FinancialParametersRepository.find()` cuando la política indica que la fecha requiere ajuste (`DynamicRatePolicy.requiresAdjustment`); una fecha regular no lee el repositorio.
15. **Precisión y redondeo** [SPEC RNF-002, D-UC02-04]: todos los montos son `BigDecimal` y el resultado de cada operación se normaliza a **escala 4** (`setScale(4, HALF_UP)`); **UC02 no redondea a centavos**. El redondeo final hacia pantallas, la pasarela o reportes es del llamador. Ejemplo: `350000.0000 × 1.2500 = 437500.0000` (el response de UC01 muestra `437500.00`).

## Contratos

UC02 **no expone contratos REST** (RF-003): su entrada es el puerto interno `ProvideBaseRateUseCase` y su única conexión externa es la consulta a Flota. Fuente: `contracts/external/flota-consulta-tarifas-base.md` [SPEC; general-plan §5.1 C7, §6].

### Integración saliente: `POST /api/v1/fleet/base-rates` (Sistema → Flota)

- **Petición**: `{ "boat_ids": [UUID…] }` — un lote por invocación (contrato: la ruta es `[CONV]`; POST para enviar lista).
- **Respuesta 200**: `{ "rates": [ { "boat_id": UUID, "base_rate": "350000.00" } ] }` — `base_rate` string decimal; la ausencia de una embarcación o un valor nulo equivale a «sin tarifa» [contrato Flota regla 3].
- **SLA**: < 500 ms para lote de hasta 50 embarcaciones [SPEC 01 HU3; contrato Flota].
- **Respuesta inválida** (cuerpo inesperado, `base_rate` no numérica/negativa/cero, 4xx/5xx, timeout, circuito abierto) → `BaseRateException(FLEET_UNAVAILABLE)` [D-UC02-07, tabla de decisión `contracts/README.md` §4.2].

### Mapeo de `BaseRateFailureReason` → catálogo de errores de los llamantes

| `reason` (dominio) | Transitorio | Código catálogo (lo usan los llamantes, no UC02) | HTTP | Origen |
|---|---|---|---|---|
| `FLEET_UNAVAILABLE` | Sí | `FLEET_UNAVAILABLE` | 503 | [SPEC casos extremos; catálogo §3.4 fila E7] |
| `DYNAMIC_RATE_NOT_CONFIGURED` | Sí | `FINANCIAL_PARAMETERS_NOT_CONFIGURED` | 503 | [SPEC casos extremos; catálogo §3.4 fila E6] |
| `BASE_RATE_NOT_AVAILABLE` | No | UC01 lote: `unavailable[]`; UC01 individual: `BASE_RATE_NOT_AVAILABLE` 422 [CONV]; UC03: registro interno | — | [SPEC casos extremos; catálogo §3.4 fila E8] |

*Nota*: en el diseño batch adoptado, `BASE_RATE_NOT_AVAILABLE` **no se lanza** (se registra por embarcación y la ausencia del resultado comunica la indisponibilidad, D-UC02-08); `FLEET_UNAVAILABLE` y `DYNAMIC_RATE_NOT_CONFIGURED` se lanzan y fallan toda la invocación.

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), contrato Flota y SPEC 02. Cobertura objetivo **[CONV]**: dominio ≥ 90 %, aplicación ≥ 80 %. El registro de fallas se prueba con un **doble en memoria** de `FailureRecorderPort` (`InMemoryFailureRecorder`), de modo que CE-003 y las pruebas de `ProvideBaseRateService` **no dependen de la pieza compartida pendiente (D-UC02-11)**.

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain` VOs) | `Money`/`BoatId`/`BaseRate` (escala 4, igualdad, `BaseRate` rechaza ≤ 0) | `domain/valueobject/MoneyBoatIdBaseRateTest` |
| Unitario (`domain/service`) | `EasterCalculator` (Pascua de años conocidos), `HighSeasonCalendar` (límites de las 4 ventanas + enero + puentes), `DynamicRatePolicy` (regular/FE/TA/coincidencia/% faltante/escala 4) | `EasterCalculatorTest`, `HighSeasonCalendarTest`, `DynamicRatePolicyTest` |
| Unitario (`application`) | `ProvideBaseRateService` (Mockito sobre `FleetRatePort`, `FinancialParametersRepository`, `DynamicRatePolicy`, `InMemoryFailureRecorder`): una sola consulta a Flota, dedupe, lista vacía, embarcaciones sin tarifa, fecha regular sin parámetros, % faltante, Flota caída | `ProvideBaseRateServiceTest` |
| Contrato (WireMock) | `FleetRateRestClient` contra el contrato `flota-consulta-tarifas-base.md`: 200 feliz, tarifa nula/ausente, cuerpo inválido, 500, timeout (SLA) y circuito abierto | `FleetRateRestClientContractTest` |
| Aceptación | `ce001_…` (CE-001), `ce002_…` (CE-002), `ce003_…` (CE-003) | `BaseRateAcceptanceTest` |
| Arquitectura | Reglas §3.4 (CE-002): la tarifa dinámica vive solo en UC02; nadie fuera depende de `DynamicRatePolicy`; DTOs sin tipo/categoría | `arch/ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-003)** — nóminal `ce00X_<descripcion>`:

- **`ce001_tarifa_refleja_condicion_dinamica_vigente`** (CE-001, T023): tabla parametrizada (JUnit `@ParameterizedTest`) con las fechas de las reglas 2–8 (regular, FE, TA de las cuatro ventanas, límites 15-nov / 15-ene / 01-jun / 30-jul / 05-oct / 12-oct, enero de 1 a 15, días santos y Pascua 2024–2030, coincidencia FE+TA, puente festivo entre semana) y la tarifa esperada a **escala 4**, con cero discrepancias.
- **`ce002_tarifa_dinamica_solo_en_uc02_sin_tipo_ni_categoria`** (CE-002, T024): (a) ArchUnit — ninguna clase fuera de `domain.service` (tema `pricing` de UC02) depende de `DynamicRatePolicy`; (b) reflexión — `BoatBaseRateDto`, `FleetBaseRatesRequestDto`, `Remembering` y `BaseRateResult` no declaran ningún campo de tipo/categoría (cero atributos de clasificación).
- **`ce003_fallas_de_flota_manejadas_sin_tarifa_asumida`** (CE-003, T025): con WireMock — timeout (retardo > 1 s), puerto cerrado (stub fallido), cuerpo no JSON, `500`, y apertura del *circuit breaker*; ante cada caso el servicio lanza `BaseRateException(FLEET_UNAVAILABLE)` (o el llamado acepta la degradación controlada), **registra** en `InMemoryFailureRecorder` y **jamás devuelve una tarifa**; se verifica que ninguna invocación produce salida parcial.

Además: regresión de consumidores **fuera del alcance** de este plan (UC01 y UC03 son del bloque A pero con planes propios; aquí solo se publica la firma de `ProvideBaseRateUseCase`). La OQ-UC11-05 queda respondida con `HighSeasonCalendar.windowsForYear(año)`; la prueba del formato de `high_season_windows` es de UC11 (OQ-UC11-03).

## Discrepancias y puntos abiertos

Registro de contradicciones detectadas al elaborar este plan. **No se resuelven en silencio**: cada una indica la decisión para avanzar y qué requiere confirmación. Los IDs `D-UC02-xx` son propios de este plan; `D-xx` sin prefijo son decisiones del plan general (§11).

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC02-01** | El contexto dice que el sistema recibe el **tipo/categoría** de la embarcación desde Flota | `docs/context/contexto-modulo3.md` (l. 50 y 96) vs **SPEC 02 RF-007** y contrato Flota («No se envía el tipo o categoría») | Rige el SPEC: sin tipo/categoría; el DTO de Flota solo lleva `boat_id` + `base_rate`. Se avisa por el canal §6 | Decidida (SPEC prevalece) |
| **D-UC02-02** | El contexto incluye **puentes festivos y fines de semana largos** como temporada alta | `docs/context/sea-share.md` (l. 53) vs **SPEC 02 RF-006** («NO forman parte de esta regla») | Rige el SPEC: puentes/festivos se evalúan como fin de semana o día regular; únicos días santos = Jueves/Viernes Santo | Decidida (SPEC prevalece) |
| **D-UC02-03** | Ventana de fin de año que cruza el año (15-nov → 15-ene del año siguiente): las fechas de enero pertenecen a la ventana abierta el 15-nov del año **anterior** | SPEC RF-006 vs lectura «solo ventanas del año evaluado» | Interpretación por **intervalo**: se evalúan las ventanas del año de la fecha y las del año anterior; 1–15 de enero es temporada alta. Confirmada por el responsable | Decidida |
| **D-UC02-04** | RNF-002 exige 4 decimales internos «antes de cualquier redondeo final»; UC02 entrega una tarifa que no va directa a la pasarela/reportes | SPEC RNF-002 vs posible redondeo a centavos | UC02 **no redondea**: salida a escala 4; el redondeo final es del llamador | Decidida |
| **D-UC02-05** | `FleetRatePort` (y los VOs `Money`/`BoatId`) están en `general-plan.md` §3.3/§3.5 pero **no** en la tabla de piezas compartidas de la guía §3.1 | `general-plan.md` §3.3/§3.5 vs guía §3.1 | Proponer en el canal añadir a §3.1: `FleetRatePort` (dueño A/UC02; lo usan UC01 y UC03 por `port.in`) y los VOs compartidos `Money`/`BoatId` | Abierta (publicar en el canal) |
| **D-UC02-06** | El SPEC 02 no define qué es «fin de semana» ni la zona horaria de la «fecha actual» | SPEC 02 vs general-plan D-12/OQ-04 | Sábado y domingo en `America/Bogota` (vía `seashare.timezone`); `ClockPort.today()` para las pruebas | Decidida (ver OQ-UC02-04) |
| **D-UC02-07** | El SPEC 02 solo contempla tarifa «nula o faltante»; no define qué hacer con tarifas negativas, cero o no numéricas | SPEC 02 casos extremos vs tabla de decisión `contracts/README.md` §4.2 regla 4 | Tarifa nula/ausente = sin tarifa (E8); negativa/cero/no numérica = **respuesta inválida** → `FLEET_UNAVAILABLE` (E7); falla toda la invocación | Decidida |
| **D-UC02-08** | El SPEC 02 dice «registra el fallo Y propaga el error»: ¿duplicidad de registros en `operational_failure` (UC02 y el llamador)? | SPEC 02 casos extremos vs SPEC 03 CE-003 | UC02 registra **todas** sus fallas (una por embarcación sin tarifa; una por invocación en FLEET/DYNAMIC) y propaga; los llamadores registran solo las propias (con su `use_case`) | Decidida para UC02 |
| **D-UC02-09** | ¿Quién resuelve la «fecha actual» del modo lote (el SPEC la atribuye a UC02, pero UC01 devuelve `evaluation_date`)? | SPEC 02 RF-001 vs contrato UC01 lote | Consecuencia del diseño «solo batch»: `evaluatedDate` es obligatorio y lo resuelve el llamador (UC01 con `ClockPort.today()`), garantizando que coincida con `evaluation_date` | Decidida |
| **D-UC02-10** | Nombres de código de `EmbarcacionInfo` y `TarifaBaseResultado` (SPEC 02) no fijados en general-plan §13 | SPEC 02 entidades clave vs general-plan §13 | `BoatBaseRateDto` (infraestructura) y `BaseRateResult` (aplicación); el monto se modela con el VO `BaseRate` sobre `Money` | Decidida **[CONV]** |
| **D-UC02-11** | La guía §3.1 asigna `operational_failure`, `FailureRecorderPort` (y `outbox_message`/`OutboxPort`) a las tareas `UC11·T001`–`UC11·T009`, pero el plan de UC11 no las contiene | Guía §3.1 vs plan de UC11 (T004–T009) | UC02 no lo implementa: referencia el puerto **por nombre**, usa doble en pruebas y deja **T004 condicionada**. Se requiere que el plan de UC11 (en estado «Rehacer») incluya la migración de `operational_failure` + `FailureRecorderPort` (+ `outbox_message`/`OutboxPort` para bloques B y C) o que se cree una tarea técnica compartida registrada en §3.1 | Abierta — **pedir a Persona 2** (ver «Mensaje para el bloque B») |

## Preguntas abiertas (OQ-UC02-xx)

Preguntas que este plan no puede resolver con el SPEC 02, el plan general ni los contratos. Hasta que se confirmen rige la propuesta por defecto y las tareas afectadas quedan condicionadas.

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC02-01** | ¿Qué mecanismo de autenticación usa el sistema para llamar a Flota (mTLS, API key, token de servicio)? El contrato Flota no define `Authorization` ni el SPEC 02 lo menciona | T019 | Sin autenticación en primera iteración; credencial de servicio vía `seashare.fleet.*` si Flota la exige **[CONV]** | Abierta |
| **OQ-UC02-02** | ¿Existe una meta de rendimiento propia de UC02? El SPEC 02 no la define | Technical Context | Sin meta propia; hereda el SLA de Flota (< 500 ms / 50 embarcaciones) **[CONV]** | Abierta |
| **OQ-UC02-03** | ¿Cuál es el formato de `high_season_windows` en el contrato UC11 (hereda OQ-UC11-03)? | T026 (costura), contrato UC11 | UC02 entrega `HighSeasonWindow` tipadas (`type`, `startInclusive`, `endInclusive`) y **no** define el formato de strings; el mapeo lo decide UC11 | Abierta (de UC11) |
| **OQ-UC02-04** | ¿Cuál es la zona horaria de negocio para «fecha actual» y fin de semana (general-plan OQ-04)? | T005, T009, reglas 3 y 6 | `America/Bogota` en `seashare.timezone` **[CONV]** | Abierta (general) |
| **OQ-UC02-05** | ¿UC02 debe imponer un límite al tamaño del lote? El límite (50) es de UC01 (RF-006, D-22) | T016 | UC02 no impone límite; acepta el lote que el llamador le pase (una sola consulta a Flota) **[CONV]** | Abierta |
| **OQ-UC02-06** | ¿Cómo se nombra el puerto de reloj del general-plan §3.3 («reloj»)? | T005 | `ClockPort` con `LocalDate today()` **[CONV]** | Abierta |

**`[NEEDS CLARIFICATION]` consolidado:** OQ-UC02-01 a OQ-UC02-06.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). **«Compartido»** = misma tarea que una fase de la hoja de ruta general (`general-plan.md` §12): las fases Setup y Foundational se marcan «Compartido» y remiten a `UC11·T001`–`UC11·T009` (guía §3.4). Los bloques vecinos referencian estas tareas como `UC11·T0NN`.

### Phase 1: Setup — **Compartido** (con la Fase 1 general; se referencia como `UC11·T001`–`UC11·T003`)

- [ ] **T001** · Compartido: `pom.xml` — remite a `UC11·T001`. UC02 **requiere además** los artefactos `resilience4j-spring-boot3` y `wiremock-standalone` (o `wiremock-spring-boot`); como es el mismo archivo que edita `UC11·T001`, se coordina en el canal la ampliación sin duplicar ediciones (guía §6). · M: `none` · P: `pending`
- [ ] **T002** · Compartido: base de `ArchitectureTest` (reglas §3.4) y `application.properties`: datasource, Flyway, `seashare.timezone` (OQ-UC02-04) y las propiedades `seashare.fleet.*` (general-plan §7.4) — remite a `UC11·T003`. · M: `none` · P: `pending`

### Phase 2: Foundational — **Compartido** (con la Fase 2 general; se referencia como `UC11·T004`–`UC11·T009`)

- [ ] **T003** · Compartido: `DomainException` (`domain/exception`), base del árbol de excepciones — remite a `UC11·T004`. · M: `none` · P: `pending`
- [ ] **T004** · **Condicionada (D-UC02-11)**: consumir la pieza compartida `FailureRecorderPort` + tabla `operational_failure` **por nombre**; se define en `UC11·T001`–`UC11·T009` (guía §3.1), que hoy **no la contienen**. UC02 no la implementa; para que sus pruebas no se bloqueen, T016/T025 usan el doble `InMemoryFailureRecorder`. Desbloqueo: el plan de UC11 (a rehacer) la incluye o se crea una tarea técnica compartida registrada en §3.1. · M: `none` · P: `pending` (bloqueada por tercera parte)
- [ ] **T005** · `ClockPort` (`application/port/out`): `LocalDate today()` en la zona de negocio, + `SystemClock` (`infrastructure/config`) leyendo `seashare.timezone` [OQ-UC02-04; OQ-UC02-06]. Publicado para **UC01** (intra-bloque A); UC02 no lo invoca (D-UC02-09). · M: `none` · P: `pending`
- [ ] **T006** · VOs de dominio: `Money` (`BigDecimal`, escala 4, sin enforced de signo: es compartido), `BoatId` (UUID), `BaseRate` (con `Money`, **rechaza monto ≤ 0**). Compartidos dentro del bloque A (D-UC02-05). · M: `none` · P: `pending`
- [ ] **T007** · Pruebas de los VOs: escala/precisión, igualdad, `BaseRate` rechaza nulo/≤ 0, `BoatId` valida UUID (RNF-002). · M: `none` · P: `pending`

### Phase 3: US1 — Calendario de temporada alta y Semana Santa (HU1, RF-006, RNF-004, CE-001)

- [ ] **T008** · `EasterCalculator` (`domain/service`): `LocalDate easterSunday(int year)` con el **Algoritmo de Meeus/Jones/Butcher** (RNF-004), función pura sin dependencias; rechaza años < 1583 **[CONV]**. · M: `none` · P: `pending`
- [ ] **T009** · `HighSeasonWindow` (VO: `type` ∈ `YEAR_END`/`MID_YEAR`/`HOLY_WEEK`/`RECESS_WEEK`, `startInclusive`, `endInclusive`) y `HighSeasonCalendar` (`domain/service`): `boolean isHighSeason(LocalDate)` (evalúa ventanas del año de la fecha **y** del año anterior, D-UC02-03) y `List<HighSeasonWindow> windowsForYear(int year)` para la costura con UC11 (OQ-UC11-05). Sin configuración manual de fechas (RF-006). · M: `none` · P: `pending`
- [ ] **T010** · Pruebas unitarias del calendario: límites de las 4 ventanas (15-nov, 15-ene inclusive; 01-jun; 30-jul; 05-oct; 12-oct), enero 1–15, días santos 2024–2030 (Pascua conocida), puentes/festivos excluidos, coincidencia FE+TA, escala de fechas del SPEC (CE-001, RNF-004). · M: `none` · P: `pending`
- [ ] **T011** · `DynamicRatePolicy` (`domain/service`): `boolean requiresAdjustment(LocalDate)` (FE sábado/domingo en zona de negocio **o** temporada alta) y `BaseRate apply(BaseRate base, LocalDate fecha, FinancialParameters params)` — regular sin ajuste; FE y TA con la fórmula `base × (1 + %/100)`; coincidencia con **el mayor ajuste**; porcentaje requerido ausente → `BaseRateException(DYNAMIC_RATE_NOT_CONFIGURED)`; devuelve escala 4 sin redondeo final (RNF-002, regla 15). · M: `none` · P: `pending`
- [ ] **T012** · Pruebas de la política: los 3 escenarios de HU1, coincidencia (max), % faltante en cada condición (y ambos en coincidencia), fecha regular sin porcentaje, precisión escala 4 con porcentajes de 4 decimales (CE-001). · M: `none` · P: `pending`

### Phase 4: US1 — Puerto expuesto y servicio de aplicación (HU1, RF-001, RF-002, RF-003, RF-005)

- [ ] **T013** · `ProvideBaseRateCommand` (`List<BoatId> boatIds`, `LocalDate evaluatedDate` obligatorio) y `BaseRateResult` (`BoatId`, `BaseRate`) en `application/dto` [D-UC02-10]. · M: `none` · P: `pending`
- [ ] **T014** · `BaseRateException` (`domain/exception`, extiende `DomainException`): `BaseRateFailureReason` (`FLEET_UNAVAILABLE`, `DYNAMIC_RATE_NOT_CONFIGURED`, `BASE_RATE_NOT_AVAILABLE`) y `isTransient()` (verdadero en `FLEET_UNAVAILABLE` y `DYNAMIC_RATE_NOT_CONFIGURED`; falso en el registro por-embarcación). · M: `none` · P: `pending`
- [ ] **T015** · `FleetRatePort` (`application/port/out`): `Map<BoatId, BaseRate> findBaseRates(Set<BoatId> boatIds)` — devuelve **solo** las embarcaciones con tarifa válida; lanza `BaseRateException(FLEET_UNAVAILABLE)` ante falla de Flota (D-UC02-07). · M: `none` · P: `pending`
- [ ] **T016** · `ProvideBaseRateUseCase` + `ProvideBaseRateService`: dedupe de IDs; lista vacía → `List.of()` sin consultar a Flota; **una sola consulta** a `FleetRatePort`; lectura perezosa de `FinancialParametersRepository.find()` (solo si `DynamicRatePolicy.requiresAdjustment`); por cada embarcación sin tarifa → registro `BASE_RATE_NOT_AVAILABLE` en el `FailureRecorderPort` (por nombre, D-UC02-11); frente a `FLEET_UNAVAILABLE`/`DYNAMIC_RATE_NOT_CONFIGURED` → registra y **propaga** sin devolver nada; jamás una tarifa asumida. · M: `none` · P: `pending`
- [ ] **T017** · Pruebas unitarias del servicio (Mockito + `InMemoryFailureRecorder`): escenarios 1–3 de HU1; lote con varias embarcaciones (una sin tarifa no rompe las demás); lista vacía; IDs duplicados → una sola consulta; fecha regular sin fila de parámetros; % faltante; Flota caída (registro + propagación). · M: `none` · P: `pending`

### Phase 5: US1 — Adaptador a Flota (HU1, RF-004, RNF-001, RNF-003)

- [ ] **T018** · DTOs de infraestructura + `FleetRateMapper` (MapStruct): `FleetBaseRatesRequestDto` (`boat_ids`), `BoatBaseRateDto` (`boat_id`, `base_rate` string), `FleetBaseRatesResponseDto` (`rates`) y el mapeo string → `Money`/`BaseRate` **sin** ningún campo de tipo/categoría (RNF-001, RF-007). · M: `none` · P: `pending`
- [ ] **T019** · `FleetRateRestClient` (RestClient) con Resilience4j (general-plan §7.2): timeout de lectura 1 s, 1 reintento con *backoff* corto y *circuit breaker*; mapeo de caída/timeout/respuesta inválida → `BaseRateException(FLEET_UNAVAILABLE)`; tarifa no numérica/negativa/cero → respuesta inválida (D-UC02-07); tarifa nula/ausente → embarcación omitida del mapa. · M: `none` · P: `pending`
- [ ] **T020** · `FleetRateConfiguration` + propiedades `seashare.fleet.*` (base-url, timeouts, reintentos, umbrales del *circuit breaker*) [general-plan §7.4; OQ-UC02-01]. · M: `none` · P: `pending`
- [ ] **T021** · Pruebas de contrato del adaptador (WireMock, `FleetRateRestClientContractTest`): 200 con tarifas; embarcación sin `base_rate`; cuerpo inválido; `500`; timeout > 1 s; apertura del *circuit breaker* (todo → `FLEET_UNAVAILABLE`, RNF-003, CE-003). · M: `none` · P: `pending`

### Phase 6: Polish (CE-001, CE-002, CE-003 y costura)

- [ ] **T022** · ArchUnit (CE-002): ninguna clase fuera de `domain/service` (ni `application` ni `infrastructure`) usa `DynamicRatePolicy`; no hay controller llamando a Flota (RF-003); reglas §3.4 completas. · M: `none` · P: `pending`
- [ ] **T023** · **`ce001_tarifa_refleja_condicion_dinamica_vigente`** (CE-001): prueba de aceptación parametrizada (reglas 2–8) verificando la tarifa exacta a escala 4 para una batería de fechas (incluye Pascua 2024–2030 y los límites de las ventanas). · M: `none` · P: `pending`
- [ ] **T024** · **`ce002_tarifa_dinamica_solo_en_uc02_sin_tipo_ni_categoria`** (CE-002): ArchUnit sobre `DynamicRatePolicy` + reflexión sobre `BoatBaseRateDto`/`FleetBaseRatesRequestDto`/`BaseRateResult` (cero atributos de tipo/categoría). · M: `none` · P: `pending`
- [ ] **T025** · **`ce003_fallas_de_flota_manejadas_sin_tarifa_asumida`** (CE-003): integración `@SpringBootTest` + WireMock + `InMemoryFailureRecorder`: timeout, puerto cerrado, cuerpo inválido, 500 y circuito abierto → error controlado, registro del fallo y **cero tarifas devueltas**. · M: `none` · P: `pending`
- [ ] **T026** · Documentación y costuras: verificar la alineación con `flota-consulta-tarifas-base.md`; publicar en el canal la firma de `ProvideBaseRateUseCase` (guía §3.2) y las propuestas de `D-UC02-05` y `D-UC02-11`; confirmar con UC11 el formato de `high_season_windows` (OQ-UC02-03/OQ-UC11-03) y que `windowsForYear` responde su OQ-UC11-05. · M: `none` · P: `pending`
- [ ] **T027** · Compartido: revisar cobertura (dominio ≥ 90 %, aplicación ≥ 80 % **[CONV]**) y `./mvnw clean verify` final con las pruebas de aceptación CE-001…CE-003. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─┬─> T003 ──> T004* ──┐            (* condicionada: D-UC02-11)
      │         │                     │
      │         └─────────────────────┴─> T005 ─> T006 ─> T007
      │                                      │
      ├──────────────────────────> T008 ─> T009 ─> T010 ─┬─> T011 ─> T012
      │                                                   │
      ├───────────────────────────────────────────────────┴─> T013 ─> T014 ─> T015
      │                                                               │
      │                                                        T016 ─> T017
      │
      ├────────────────────────────────────────────────> T018 ─> T019 ─> T020 ─> T021
      │
      └────────────────────────────────────────────────> T022 ─> T023 ─> T024 ─> T025 ─> T026 ─> T027
```

- **Bloque 1 (T001–T007)**: Setup y Foundational compartidos (`UC11·T001`–`UC11·T009`). T004 (registro de fallas) está **condicionada a D-UC02-11**; como UC02 la consume con doble de prueba, no bloquea a T016/T017/T025.
- **US1 calendario (T008–T012)** → **puerto y servicio (T013–T017)** → **adaptador Flota (T018–T021)**: el servicio depende del calendario y de la política; el adaptador es independiente y puede desarrollarse en paralelo con T013–T017. **UC01 (incluye a UC02) no puede terminar hasta publicarse la firma (T016) y existir el servicio.**
- **Polish (T022–T027)**: después de toda la funcionalidad; T026 (costura con UC11) debería publicarse en el canal **lo antes posible** (guía §3.2: A publica la firma al empezar UC02).
- Riesgo de secuencia: T004 hereda el estado de UC11 (fase 3, «Rehacer»); si Persona 2 no asigna la pieza compartida, la alternativa acordada es crear una tarea técnica común en §3.1 **sin que cada UC la implemente por su cuenta** (guía §3.1, D-UC02-11).

## Notes

- El SPEC 02 **no** define: meta de rendimiento propia (OQ-UC02-02), autenticación hacia Flota (OQ-UC02-01), definición de fin de semana (D-UC02-06/OQ-UC02-04), ni el manejo de tarifas negativas/cero (D-UC02-07) → `[NEEDS CLARIFICATION]` (OQ-UC02-xx) con decisión por defecto registrada.
- **`docs/context/` contradice al SPEC 02** en dos puntos (tipo/categoría; puentes como temporada alta) → rige el SPEC (D-UC02-01, D-UC02-02); se avisa por el canal §6.
- Calendario: `isHighSeason` + `windowsForYear` son de UC2; **UC11 los consume** (OQ-UC11-05 queda respondida). El formato de `high_season_windows` es decisión de UC11 (OQ-UC11-03).
- `FinancialParameters`/`FinancialParametersRepository` son de UC11 (bloque B): UC02 los consume por nombre y **solo cuando la fecha lo exige** (lectura perezosa, regla 14).
- `FailureRecorderPort`/`operational_failure` son compartidos y **están pendientes en UC11** (D-UC02-11): UC02 los referencia por nombre, no los implementa y los sustituye por un doble en pruebas (T016, T025). El mensaje para el bloque B está al final de este documento.
- `FleetRatePort` y los VOs `Money`/`BoatId` no figuran en la guía §3.1 → propuesta de edición de la sección de costuras (D-UC02-05).
- Etiquetas usadas: `[SPEC]` (SPEC 02 y contratos), `[CONV]` (general-plan e inferido), `[PEND]`/`[NEEDS CLARIFICATION]` (sin definir).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, Reglas de negocio, contratos, fases con `T0NN`/`M`/`P`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-09` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]`, `[CONV]`, `[PEND]` o `[NEEDS CLARIFICATION]`.
- [ ] Contradicciones `D-UC02-01` a `D-UC02-11` en «Discrepancias y puntos abiertos» con decisión explícita, sin resolución silenciosa; `D-UC02-11` remite al mensaje para el bloque B.
- [ ] Preguntas abiertas definidas en el propio plan (OQ-UC02-01 a OQ-UC02-06), con remisión explícita a las OQ del general-plan y de UC11 cuando corresponden.
- [ ] Cada RF/RNF/CE/HU del SPEC 02 trazado a componente y tarea; pruebas `ce001_…`, `ce002_…`, `ce003_…`.
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§13) aplicadas; sin tipo/categoría en DTOs.
- [ ] UC02 sin tablas propias, sin migración propia, sin endpoint REST; solo consume `financial_parameters` (nombre) y `operational_failure` (nombre, pieza compartida).
- [ ] Puerto `ProvideBaseRateUseCase` publicado con su firma (guía §3.2); costuras con UC01/UC03 y UC11 declaradas.