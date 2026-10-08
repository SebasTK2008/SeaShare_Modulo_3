# Implementation Plan: UC11 - Configurar Parámetros Financieros Globales

**Date**: 2026-10-08
**Spec**: [spec.md](spec.md)

## Summary

El Administrador Financiero define o ajusta los cuatro parámetros financieros globales de la plataforma —porcentaje de comisión de la plataforma, tarifa del seguro náutico por pasajero, porcentaje de incremento por fin de semana y porcentaje de incremento por temporada alta— y los consulta junto con dos datos derivados de solo lectura (la regla del depósito de garantía y las ventanas de temporada alta, estas últimas provistas por el `HighSeasonCalendar` de UC02). El sistema los persiste como **una única actualización atómica** sobre la fila singleton de `financial_parameters`, sin historial y sin efecto retroactivo sobre reservas ya calculadas [SPEC RF-005, RF-007, RNF-003, HU1–HU4].

Enfoque técnico: servicio backend Spring Boot con arquitectura hexagonal de tres capas (`domain` / `application` / `infrastructure`) bajo `com.seashare.seasharem3` [SPEC general-plan §3.2–§3.4]; adaptador de entrada REST (`GET`/`PUT`) que invoca exclusivamente `application.port.in`; adaptador de salida JPA sobre la tabla singleton; validación de rangos en dominio puro; errores en formato *Problem Details* (RFC 9457) según `contracts/README.md` §3.3; seguridad OAuth2 Resource Server con rol `ADMIN_FINANCIERO` **[PEND OQ-UC11-01]** (propuesta por defecto definida en el plan general §7.1). UC11 es la **fase 3** de la hoja de ruta general, anterior a UC01/UC02/UC04, los tres lectores directos del singleton; UC03, UC05, UC06, UC09 y UC10 usan los parámetros **congelados** en la reserva y no leen el singleton (general-plan D-27, §3.2) [SPEC general-plan §12, «Dependencias entre fases»]. Las preguntas abiertas de este caso de uso se definen en este mismo plan (sección «Preguntas abiertas», OQ-UC11-xx).

El diagrama de casos de uso (`docs/diagrams/module3-v2.drawio.xml`) asocia "Configurar parámetros financieros globales" **únicamente** con el actor *Admin Financiero*, sin relaciones `<<include>>` ni `<<extend>>`.

### Trazabilidad RF/RNF/CE/HU → componente / tarea

| Requisito | Componente (rutas en §Project Structure) | Tareas | Prueba |
|---|---|---|---|
| **RF-001** comisión configurable | `FinancialParameters.commissionPct`, `FinancialParametersRequest`, `SaveFinancialParametersUseCase`, `FinancialParametersController` (PUT) | T005, T012, T016, T018 | T010, T021 |
| **RF-002** tarifa de seguro configurable | `FinancialParameters.insuranceFeePerPassenger` (mismos componentes) | T005, T012, T016, T018 | T010 |
| **RF-003** incremento fin de semana configurable | `FinancialParameters.weekendIncreasePct` | T005, T020, T023 | T021, T022 |
| **RF-004** incremento temporada alta configurable | `FinancialParameters.highSeasonIncreasePct` | T005, T020, T023 | T021, T022 |
| **RF-005** una única actualización de la entidad lógica | `FinancialParametersService` + `FinancialParametersPersistenceAdapter` (fila `id=1`, transacción única) | T016, T017, T025, T040 | T022, T034, T035 |
| **RF-006** exponer parámetros a los consumidores | `application.port.out.FinancialParametersRepository` (puerto compartido) + `LoadFinancialParametersUseCase` | T013, T030, T033 | T034 (CE-001) |
| **RF-007** sin efecto retroactivo | UC11 solo escribe `financial_parameters` (alcance del adaptador) + ArchUnit | T035, T042 | T035 (CE-002), T042 |
| **RF-008** exclusivo del Administrador Financiero | `SecurityConfig` | T009 | T028 (CE-003) |
| **RF-009** una única respuesta con vigentes + derivados | `FinancialParametersResult`, `FinancialParametersResponse` | T030, T032 | T026 |
| **RF-010** descartar cambios no persistidos | Sin endpoint de cancelación (acción del cliente, `contracts/README.md` §2) | T036 | T036 (CE-004) |
| **RF-011** validar todo, sin actualizaciones parciales | Validación en dominio + transacción única | T004, T005, T017, T040 | T021, T037, T038 |
| **RF-012** 4 editables; depósito y ventanas solo lectura | `HighSeasonCalendar` (de UC02, solo consumido), `guarantee_deposit_rule`, `FinancialParametersResponse` | T029, T031, T032 | T026 |
| **RF-013** rangos y formatos → error controlado | Validación en `FinancialParameters` → `400 VALIDATION_ERROR` | T004, T005, T019, T024 | T006, T020, T021 |
| **RNF-001** DTO general de carga/guardado | `FinancialParametersRequest`/`FinancialParametersResponse` (HTTP) + `FinancialParametersCommand`/`FinancialParametersResult` (aplicación) | T012, T018, T030, T032 | T010, T026 |
| **RNF-002** `BigDecimal` | Campos de `FinancialParameters`; JSON como *string* decimal (README §3.1) | T005, T018, T032 | T006, T010, T020, T026 |
| **RNF-003** atómico y versión coherente | Fila singleton `id=1` + transacción única (D-24) | T016, T017, T025, T040 | T034, T037, T038 |
| **CE-001** consistencia de configuración | Lectura vía `FinancialParametersRepository` tras el guardado | T034 | **T034** |
| **CE-002** sobrescritura completa, sin parciales ni afectación de reservas | Adaptador limitado a `financial_parameters` + CHECK de BD | T035, T042 | **T035** |
| **CE-003** exclusividad de acceso | `SecurityConfig` | T009, T028 | **T028** |
| **CE-004** integridad de edición (cancelar / guardado inválido) | Validación previa + transacción + sin endpoint de cancelación | T004, T036 | **T036, T021** |
| **HU1** comisión y seguro (P1) | Fase 3 | T010–T019 | T010, T011 |
| **HU2** porcentajes de tarifa dinámica (P1) | Fase 4 | T020–T025 | T021, T022 |
| **HU3** visualizar vigentes + solo lectura (P1) | Fase 5 | T026–T033 | T026, T027 |
| **HU4** guardar / cancelar / fallo (P1) | Fase 6 | T034–T041 | T034–T038, T041 |

## Technical Context

**Language/Version**: Java 25 (`java.version` del `pom.xml`) [SPEC general-plan]
**Primary Dependencies**: Spring Boot 4.1.1 (parent del `pom.xml`). Para UC11: Spring Web MVC, Validation, Security (OAuth2 Resource Server), Flyway, MapStruct, ArchUnit. **Hoy no están en el `pom.xml`** (solo hay `data-jpa`, `postgresql`, `mapstruct`, `lombok-mapstruct-binding` y Testcontainers); los nombres exactos de los artefactos deben verificarse al agregarlos [SPEC general-plan: Technical Context]
**Storage**: PostgreSQL 16+ (`NUMERIC(18,4)` para dinero y porcentajes, `timestamptz` para instantes) [SPEC general-plan §4]; migraciones Flyway en `src/main/resources/db/migration` (**hoy no existen**)
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL — ya existe `src/test/java/com/seashare/seasharem3/TestcontainersConfiguration.java`), MockMvc, ArchUnit [CONV]. *Hoy falta `spring-boot-starter-test` en el `pom.xml`*
**Target Platform**: Contenedores Docker (Linux) [SPEC general-plan]
**Project Type**: Servicio backend único (hexagonal), sin frontend propio [SPEC general-plan]
**Performance Goals**: No definidos por el SPEC 11 `[NEEDS CLARIFICATION: OQ-UC11-02; se buscó en spec.md 011, general-plan.md §Technical Context y contracts/rest/UC11-*; el SPEC no fija metas de rendimiento para este caso de uso]`. Administración esporádica [CONV]
**Constraints**: `BigDecimal` en los cuatro campos [SPEC RNF-002]; guardado atómico sin actualizaciones parciales [SPEC RF-005, RF-011, RNF-003]; acceso exclusivo del Administrador Financiero [SPEC RF-008, CE-003]; singleton sin historial ni versión, último guardado gana [SPEC casos extremos + general-plan D-24]
**Scale/Scope**: 1 fila singleton (`id = 1`) en `financial_parameters` [SPEC general-plan §4]; 2 endpoints REST [SPEC contratos UC11]; 13 RF + 3 RNF + 4 CE + 4 HU del SPEC 11

**Estado actual del repositorio (relevante para UC11)**: existen `SeashareM3Application.java`, `application.properties` (solo `spring.application.name=seashare-m3`), `TestcontainersConfiguration` (imagen `postgres:latest`; se alinea a `postgres:16` [CONV] — ver D-UC11-11), `SeashareM3ApplicationTests` y `TestSeashareM3Application`. **No existen** los paquetes `domain`/`application`/`infrastructure`, migraciones, `Dockerfile`, `docker-compose.yml` ni reglas ArchUnit.

## Project Structure

### Documentation (this feature)

```text
docs/features/011-configurar-parametros-financieros-globales/
├── plan.md              # This file
└── spec.md              # Única fuente de verdad de este caso de uso
```

Contratos consumidos por este plan:

```text
docs/technical-plan/contracts/
├── README.md                                  # Leyenda [SPEC]/[CONV]/[PEND], Problem Details, catálogo §3.4
└── rest/
    ├── UC11-obtener-parametros-financieros.md # GET (HU3); ejemplo corregido (15-dic→15-nov, D-UC11-04)
    └── UC11-guardar-parametros-financieros.md # PUT (HU1, HU2, HU4)
```

### Source Code (repository root)

```text
src/main/java/com/seashare/seasharem3/
├── SeashareM3Application.java                       # ya existe
├── domain/
│   ├── model/
│   │   └── FinancialParameters.java                 # T005  [SPEC RF-001…RF-005, RF-013, RNF-002]
│   └── exception/
│       ├── DomainException.java                     # T004  [general-plan §3.3; nombre [CONV]]
│       └── FinancialParametersValidationException.java  # T004 [CONV]
├── application/
│   ├── port/in/
│   │   ├── LoadFinancialParametersUseCase.java      # T007, T030  [general-plan §3.5]
│   │   └── SaveFinancialParametersUseCase.java      # T007, T012  [general-plan §3.5]
│   ├── port/out/
│   │   └── FinancialParametersRepository.java       # T007, T013  [general-plan §3.5; RF-006]
│   ├── service/
│   │   └── FinancialParametersService.java          # T017, T031 [general-plan §3.5]
│   └── dto/
│       ├── FinancialParametersCommand.java          # T012  [SPEC RNF-001; nombre [CONV]]
│       └── FinancialParametersResult.java           # T030  [SPEC RNF-001, HU3; nombre [CONV]]
└── infrastructure/
    ├── adapter/in/web/
    │   ├── FinancialParametersController.java       # T018 (PUT), T032 (GET) [CONV]
    │   └── dto/
    │       ├── FinancialParametersRequest.java      # T018  # body del PUT con las claves del contrato [CONV]
    │       └── FinancialParametersResponse.java     # T032  # body del GET con las claves del contrato [CONV]
    ├── adapter/out/persistence/
    │   ├── FinancialParametersJpaEntity.java        # T014  [general-plan D-04]
    │   ├── FinancialParametersJpaRepository.java    # T014  [CONV]
    │   ├── FinancialParametersMapper.java           # T015  [general-plan D-04, MapStruct; nombre [CONV]]
    │   └── FinancialParametersPersistenceAdapter.java # T016, T033 [general-plan §3.4]
    └── config/
        ├── SecurityConfig.java                      # T009  [SPEC RF-008, CE-003; mecanismo PEND OQ-UC11-01]
        └── ProblemDetailsConfig.java                # T008, T039 [CONV: nombre [CONV]; RFC 9457]

src/main/resources/
└── db/migration/
    └── V1__create_financial_parameters_table.sql    # T002  [SPEC general-plan §4]

src/test/java/com/seashare/seasharem3/
├── arch/
│   └── ArchitectureTest.java                        # T003, T042 [general-plan §3.4]
├── contract/
│   ├── UC11GetFinancialParametersContractTest.java  # T026, T027 [CONV: nombre]
│   └── UC11SaveFinancialParametersContractTest.java # T010, T021, T036, T041 [CONV: nombre]
├── domain/
│   └── model/FinancialParametersTest.java           # T006, T020, T024
├── application/service/
│   ├── FinancialParametersServiceTest.java          # T011, T037
│   └── FinancialParametersConsistencyTest.java      # T034 (CE-001), T035 (CE-002) [CONV: nombre]
└── infrastructure/adapter/
    ├── in/web/FinancialParametersControllerSecurityTest.java  # T028 (CE-003)
    └── out/persistence/FinancialParametersPersistenceAdapterTest.java  # T019, T022, T038
```

`HighSeasonCalendar` (`domain/service`) **no forma parte de este caso de uso**: es del UC02 (general-plan §3.3 y §3.5). UC11 solo lo consume en `FinancialParametersService.load` (T029, T031) para poblar `high_season_windows`; no lo implementa ni lo prueba (OQ-UC11-05).

**Puertos expuestos** (guía `guia-para-planes-especificos.md` §3.1 y §3.4): `application.port.out.FinancialParametersRepository` es un puerto de **salida** de UC11 (dueño: bloque B) y los demás bloques lo referencian **por nombre**; UC11 publica su firma aquí. Firma [CONV]:

```java
public interface FinancialParametersRepository {
    Optional<FinancialParameters> find();                    // fila singleton id=1; vacío si aún no existe (→ 404)
    FinancialParameters save(FinancialParameters params);    // upsert/sobrescritura atómica de la fila id=1
}
```

Lo consumen solo los bloques que la guía §3.1 asigna: **A (UC01/UC02/UC04)**. Los puertos de entrada (`LoadFinancialParametersUseCase`, `SaveFinancialParametersUseCase`) son internos del bloque B y no se exponen a otros bloques. Otros bloques referencian las tareas compartidas de UC11 como `UC11·T0NN` (guía §3.4).

**Structure Decision**: servicio backend único con Arquitectura Hexagonal de tres capas (`domain`, `application`, `infrastructure`) dentro de un solo módulo Maven, con subpaquetes temáticos sin reglas de dependencia entre sí, bajo la raíz `com.seashare.seasharem3` [SPEC general-plan §3.2, §3.3, D-02, D-03]. Reglas aplicadas a este caso de uso [SPEC general-plan §3.4]: `domain` sin Spring/JPA/Jackson; `application` solo depende de `domain`; `infrastructure.adapter.in` solo invoca `application.port.in`; `infrastructure.adapter.out` solo implementa `application.port.out`; el controller nunca accede a un repositorio; la entidad JPA no sale de `adapter/out/persistence`; los DTOs HTTP viven en `infrastructure.adapter.in` y `application` trabaja con `command`/`result` (RNF-001). Identificadores en inglés según el lenguaje ubicuo (§13: *ParámetrosFinancierosGlobales → `FinancialParameters`*) y D-23.

## Reglas de negocio

Todas las reglas siguientes provienen del SPEC 11 (`spec.md`) salvo indicación.

1. **Parámetros configurables** [SPEC RF-001…RF-004, HU1, HU2]: `commissionPct` (comisión de la plataforma), `insuranceFeePerPassenger` (tarifa del seguro náutico por pasajero), `weekendIncreasePct` (incremento fin de semana), `highSeasonIncreasePct` (incremento temporada alta). Ejemplo ilustrativo: comisión `20` (porcentaje entero), seguro `8,50` (importe por pasajero) — *los valores exactos son responsabilidad del Administrador Financiero; el SPEC no fija valores por defecto*.
2. **Rangos y formatos de validación** [SPEC RF-013]: los tres porcentajes deben estar entre `0` y `100` (inclusive); la tarifa del seguro debe ser `>= 0`. Cuerpo con valores fuera de rango o no numéricos → `400 VALIDATION_ERROR` con el detalle de cada campo (`contracts/README.md` §3.3 y catálogo §3.4). Estos mismos límites (0–100 y ≥ 0) se replican como CHECK en la tabla (`general-plan.md` §4) y figuran en el contrato `UC11-guardar-parametros-financieros.md` **[CONV]**.
3. **Sobrescritura completa, sin actualizaciones parciales** [SPEC RF-005, RF-011, HU4]: el `PUT` envía los cuatro valores siempre obligatorios; si uno es inválido, no se persiste *ninguno* (transacción única sobre la fila `id = 1`). Ejemplo ilustrativo: comisión `20` válida pero seguro `-1` inválido → respuesta `400` y la comisión **no** cambia.
4. **Sin efecto retroactivo** [SPEC RF-007, CE-002, HU4]: el nuevo valor solo se aplica a cálculos posteriores; las reservas ya calculadas conservan sus importes. UC11 no escribe en ninguna otra tabla.
5. **Datos de solo lectura** [SPEC RF-012, HU3]:
   - `guarantee_deposit_rule` → regla textual `"10% de la tarifa base diaria"` (no es un importe editable; el 10% es dato de SPEC 2 / general-plan; la redacción exacta se deriva de ese dato **[PEND OQ-UC11-04: redacción literal no definida en el SPEC 11]**).
   - `high_season_windows` → ventanas de temporada alta que UC11 **obtiene** del `HighSeasonCalendar` de UC02 (regla 6); el contrato las declara como array de strings **[PEND OQ-UC11-03: formato exacto de cada elemento; ver D-UC11-04]**.
6. **Ventanas de temporada alta: dato consumido, no calculado por UC11** [SPEC RF-012; SPEC 2 RF-006]: el calendario es responsabilidad de UC02; UC11 solo lo invoca para mostrarlo en el `GET` y no lo reimplementa, no resuelve coincidencias con fines de semana ni configura fechas. Referencia (SPEC 2 RF-006, para contrastar con el resultado del calendario): 15-nov a 15-ene, 1-jun a 30-jul, Semana Santa (Jueves y Viernes Santo, Meeus/Jones/Butcher) y 5 a 12 de octubre; los puentes y fines de semana largos no son temporada alta. UC11 solo configura el **porcentaje** de incremento (`highSeasonIncreasePct`).
7. **Seguridad** [SPEC RF-008, CE-003, HU3/HU4]: solo el Administrador Financiero (`ADMIN_FINANCIERO`) puede consultar y guardar. Mecanismo concreto **[OQ-UC11-01: propuesta por defecto del general-plan §7.1 = OAuth2 Resource Server JWT con rol `ADMIN_FINANCIERO`; el SPEC 11 no lo especifica, por lo que las tareas de seguridad de este plan quedan condicionadas a su confirmación]**.
8. **Fila única, última escritura gana y primera configuración** [SPEC casos extremos, HU3 esc. 2; general-plan D-24]:
   - Existe una única fila lógica (`id = 1`); la última escritura gana; no hay `@Version` ni historial.
   - La migración V1 **no inserta fila inicial**, de modo que mientras nadie haya guardado, el `GET` responde `404 PARAMETERS_NOT_CONFIGURED` (HU3 esc. 2) y no se inventan valores.
   - La primera configuración es un `PUT` sobre la tabla vacía, resuelto como *upsert* (`INSERT ... ON CONFLICT DO UPDATE`, T016) **[CONV; ver D-UC11-12]**.
9. **No definido por el SPEC 11** `[NEEDS CLARIFICATION]`: metas de rendimiento (OQ-UC11-02); mecanismo de autenticación (OQ-UC11-01); formato de `high_season_windows` (OQ-UC11-03); redacción literal del depósito (OQ-UC11-04); provisión del calendario por UC02 (OQ-UC11-05); comportamiento ante concurrencia (por defecto último guardado gana, general-plan D-24; D-UC11-08).

## Contratos de API

Fuente: `contracts/rest/UC11-obtener-parametros-financieros.md` y `contracts/rest/UC11-guardar-parametros-financieros.md` [SPEC; general-plan §6]. El SPEC 11 **no** fija la ruta base: rigen los dos contratos, que usan `/api/v1/financial-parameters`.

### `GET /api/v1/financial-parameters`

Devuelve los 4 vigentes + `guarantee_deposit_rule` + `high_season_windows` en una única respuesta [SPEC RF-009].

- **200 OK** — ejemplo (ilustrativo):

```json
{
  "platform_commission_percentage": "20.00",
  "insurance_fee_per_passenger": "8.50",
  "weekend_increase_percentage": "10.00",
  "high_season_increase_percentage": "15.00",
  "guarantee_deposit_rule": "10% de la tarifa base diaria",
  "high_season_windows": ["11-15→01-15", "06-01→07-30", "semana-santa", "10-05→10-12"]
}
```

- **404 `PARAMETERS_NOT_CONFIGURED`** cuando la fila no existe (primera vez; HU3 escenario 2) [SPEC HU3].
- **401 / 500 `INTERNAL_ERROR`** (catálogo §3.4) si aplica [SPEC].
- Nota: los porcentajes se serializan como **string decimal** (RNF-002 + `contracts/README.md` §3.1). El formato exacto de `high_season_windows` **no está definido** → ver **OQ-UC11-03**.
- **Discrepancia con el contrato**: el ejemplo de `UC11-obtener-parametros-financieros.md` mostraba `"Fin de año (ej. 15-dic a 15-ene)"`, pero la ventana de fin de año del SPEC 2 RF-006 es del **15 de noviembre** al 15 de enero (el plan general y `contexto-modulo3.md` coinciden con el SPEC 2). Rige el SPEC 2; el ejemplo de ese contrato era incorrecto y **ya se corrigió** en esta revisión del plan (ver T044). El ejemplo JSON de este plan ya usa `11-15→01-15`. Ver **D-UC11-04**.

### `PUT /api/v1/financial-parameters`

Body: los **cuatro** valores, todos obligatorios (RNF-001) [SPEC]:

```json
{
  "platform_commission_percentage": "20.00",
  "insurance_fee_per_passenger": "8.50",
  "weekend_increase_percentage": "10.00",
  "high_season_increase_percentage": "15.00"
}
```

- **204 No Content** (contratos UC11).
- **400 `VALIDATION_ERROR`** — algún valor fuera de rango/no numérico; sin persistencia parcial [SPEC RF-013].
- **401/403** — no autenticado / rol distinto de `ADMIN_FINANCIERO` [SPEC RF-008, CE-003].
- **404 `PARAMETERS_NOT_CONFIGURED`** — `PUT` con fila ausente: no aplica (el `PUT` crea/actualiza; el 404 es de lectura).
- **500 `PARAMETERS_SAVE_FAILED`** — fallo de persistencia [SPEC catálogo `contracts/README.md` §3.4; el contrato de guardar ya lo lista].
- Errores en formato Problem Details (RFC 9457, `contracts/README.md` §3.3).

### Resumen de códigos (catálogo `contracts/README.md` §3.4)

| Código | HTTP | En catálogo | Uso en UC11 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | sí | PUT con valores inválidos |
| `PARAMETERS_NOT_CONFIGURED` | 404 | sí | GET sin fila |
| `PARAMETERS_SAVE_FAILED` | 500 | sí | fallo de persistencia en PUT |
| `INTERNAL_ERROR` | 500 | sí | error no controlado |

## Estrategia de testing

Fuente: `general-plan.md` (Technical Context), `sdd-guide.MD` ("pruebas que demuestran cada caso de aceptación") y los contratos UC11. Cobertura objetivo **[CONV]**: dominio ≥90 %, aplicación ≥80 %. Ningún test del repositorio cubre UC11 hoy (solo `SeashareM3ApplicationTests` y `TestSeashareM3Application`).

**Nivel de cada tipo de prueba:**

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario (`domain`) | rangos RF-013, inmutabilidad, `BigDecimal` RNF-002 | `domain/model/FinancialParametersTest` |
| Unitario (`application`) | orquestación RF-005/RF-011, propagación de errores, semántica 404; las ventanas de temporada alta se prueban con un doble de `HighSeasonCalendar` (la lógica del calendario se prueba en UC02) | `application/service/FinancialParametersServiceTest` (Mockito sobre `FinancialParametersRepository` y `HighSeasonCalendar`) |
| Contrato / Web (MockMvc + `@WebMvcTest` o Testcontainers) | cuerpos y códigos de los contratos UC11 | `contract/UC11SaveFinancialParametersContractTest`, `UC11GetFinancialParametersContractTest` |
| Integración (Testcontainers PostgreSQL) | migración V1 + CHECK de BD + fila singleton + atomicidad | `FinancialParametersPersistenceAdapterTest`, `FinancialParametersConsistencyTest` |
| Seguridad | 401/403 y rol `ADMIN_FINANCIERO` | `FinancialParametersControllerSecurityTest` |
| Arquitectura | reglas de capas §3.4 | `arch/ArchitectureTest` |

**Pruebas de aceptación (CE-001…CE-004)** — nóminal `ce00X_<descripcion>`:

- **`ce001_lectura_refleja_ultima_escritura`** (CE-001, T034): `PUT` válido → `GET` devuelve los cuatro valores persistidos; consistencia entre escritura y lectura.
- **`ce002_sobrescritura_completa_sin_parciales`** (CE-002, T035): `PUT` con un campo inválido → `400` y la fila conserva **todos** los valores anteriores (atomicidad); además el adaptador escribe únicamente en `financial_parameters`.
- **`ce003_solo_admin_financiero_accede`** (CE-003, T028): sin token → `401`; rol distinto → `403`; rol `ADMIN_FINANCIERO` → `200`.
- **`ce004_cancelar_no_genera_peticion_y_guardado_invalido_no_persiste`** (CE-004, T036): comportamiento de edición — no existe endpoint de cancelación (la cancelación es del cliente, `README` §2) y un `PUT` inválido no modifica la fila.

Además: prueba de regresión de consumidores **fuera del alcance** (RF-006 es responsabilidad de los consumidores UC01/UC02/UC04 —bloque A—; UC10 usa la comisión **congelada** en la reserva (general-plan D-27) y no lee el singleton; aquí solo se expone el puerto).

## Discrepancias y puntos abiertos

Registro de contradicciones detectadas al elaborar este plan. **No se resuelven en silencio**: cada una indica la decisión tomada para poder avanzar y lo que requiere confirmación. Los IDs `D-UC11-xx` son propios de este plan; los `D-xx` sin prefijo UC11 que se citan en el texto (p. ej. `general-plan D-24`) son las decisiones del plan general (§11).

| ID | Descripción | Fuentes en conflicto | Decisión para avanzar | Estado |
|---|---|---|---|---|
| **D-UC11-04** | El ejemplo del contrato `UC11-obtener…` decía "Fin de año (ej. 15-dic a 15-ene)"; la ventana real es 15-nov a 15-ene | `UC11-obtener-parametros-financieros.md` (ejemplo) vs SPEC 2 RF-006 (plan general y `contexto-modulo3.md` coinciden con el SPEC 2) | Rige el SPEC 2 (15 de noviembre); el ejemplo del contrato se corrigió en esta revisión y T044 solo verifica la alineación. El formato del array es una pregunta aparte (**OQ-UC11-03**) | Decidida; contrato ya corregido (T044 verifica) |
| **D-UC11-06** | Depósito como texto "regla" vs "calculado 10%" | SPEC RF-012 ("calculado") vs general-plan (regla textual) | texto literal en `guarantee_deposit_rule` (el monto depende de la embarcación) | Abierta: redacción literal (**OQ-UC11-04**) |
| **D-UC11-08** | Concurrencia no definida | general-plan D-24 (último gana) vs ausencia en SPEC 11 | último guardado gana, sin `@Version` | Abierta: confirmación |
| **D-UC11-09** | Nombres JSON distintos de columnas | contratos (`platform_commission_percentage`…) vs tabla (`commission_pct`…) | mapeo explícito en el mapper (el dominio no renombra) | Resuelta en diseño |
| **D-UC11-10** | RNF-001 pide "DTO general" de carga/guardado vs dos cuerpos HTTP distintos | SPEC 11 RNF-001 vs contratos GET/PUT | DTOs separados HTTP + command/result en aplicación | Abierta: confirmación |
| **D-UC11-11** | `TestcontainersConfiguration` usa `postgres:latest` vs `postgres:16` | repo actual vs PostgreSQL 16+ del general-plan | alinear a `postgres:16` al ampliar los tests (T001) | Abierta: confirmación |
| **D-UC11-12** | Caso extremo "primera configuración": HU3 exige `404` sin fila, pero RF-005 requiere fila única | HU3 vs RF-005 | migración sin fila inicial; `PUT` crea la fila (upsert) | Abierta: confirmación |

## Preguntas abiertas (OQ-UC11-xx)

Preguntas que este plan no puede resolver con el SPEC 11, el plan general ni los contratos. Hasta que se confirmen rige la propuesta por defecto y las tareas afectadas quedan condicionadas.

| ID | Pregunta | Afecta | Propuesta por defecto | Estado |
|---|---|---|---|---|
| **OQ-UC11-01** | ¿Qué mecanismo de autenticación y autorización se usa? El SPEC 11 fija *quién* puede usar el caso de uso (RF-008) pero no *cómo* se identifica (equivale a OQ-01 del plan general) | T009, T028; respuestas 401/403 | OAuth2 Resource Server (JWT) con rol `ADMIN_FINANCIERO` (general-plan §7.1) **[CONV]** | Abierta |
| **OQ-UC11-02** | ¿Existen metas de rendimiento para el `GET`/`PUT`? El SPEC 11 no las define | Technical Context | Sin meta específica; administración esporádica **[CONV]** | Abierta |
| **OQ-UC11-03** | ¿Cuál es el formato de cada elemento de `high_season_windows`? El contrato solo dice "array de strings" y su ejemplo mezcla fechas y etiquetas | T026, T032; contrato `UC11-obtener…` | Mantener array de strings: `MM-dd→MM-dd` para las ventanas fijas y `semana-santa` para la ventana móvil | Abierta |
| **OQ-UC11-04** | ¿Cuál es la redacción literal de `guarantee_deposit_rule`? El SPEC 11 solo pide mostrar el depósito como dato calculado de solo lectura | T026, T032 | `"10% de la tarifa base diaria"` (texto del contrato) | Abierta |
| **OQ-UC11-05** | ¿Cómo obtiene UC11 las ventanas de temporada alta, si `HighSeasonCalendar` es de UC02 y la hoja de ruta ejecuta UC11 (fase 3) antes que UC02 (fase 4)? ¿Expone el calendario las ventanas de un año, o solo evalúa una fecha? | T029, T031, T032 | UC11 solo consume `HighSeasonCalendar`; hasta que UC02 lo entregue, T031/T032 usan un doble de prueba y no se completan en producción. Si el calendario no lista ventanas, la ampliación de su API es de UC02, no de UC11 **[CONV]** | Abierta |

**`[NEEDS CLARIFICATION]` consolidado:** OQ-UC11-01 a OQ-UC11-05.

## Implementation Phases

> **Convención**: tarea `T0NN` · `M` = Módulo (`done`/`partial`/`pending`) · `P` = Aprobación (`approved`/`rejected`/`pending` · `none` si no aplica). "Compartido" = misma tarea que una fase de la hoja de ruta general (`general-plan.md` §12); no se duplica. Los bloques vecinos referencian estas tareas compartidas como `UC11·T0NN` (guía §3.4).

### Phase 1: Setup — **Compartido** (con la Fase 1 general; otros bloques lo referencian como `UC11·T001`–`UC11·T003`)

Dependencias mínimas del proyecto para que UC11 pueda existir (incluye lo marcado como `PENDING` en la fase 1 del general: Web, Validation, Security, Flyway, test).

- [ ] **T001** · Compartido: agregar al `pom.xml` los starters faltantes (`spring-boot-starter-web`, `validation`, `security` + OAuth2 Resource Server, `flyway`, `spring-boot-starter-test`, `spring-security-test`, ArchUnit) y alinear la imagen de Testcontainers a `postgres:16` en `TestcontainersConfiguration` (D-UC11-11). · M: `none` · P: `pending`
- [ ] **T002** · Compartido: primera migración Flyway `V1__create_financial_parameters_table.sql` con la tabla singleton de `general-plan` §4 (`id` entero fijo `= 1`; `commission_pct`, `insurance_fee_per_passenger`, `weekend_increase_pct`, `high_season_increase_pct` en `NUMERIC(18,4)`; `updated_at`, `updated_by`; CHECK 0–100 y ≥0; **sin fila inicial** — D-UC11-12). · M: `none` · P: `pending`
- [ ] **T003** · Compartido: `ArchitectureTest` con las reglas de `general-plan` §3.4 y seed `application.properties` (datasource, Flyway). · M: `none` · P: `pending`

### Phase 2: Foundational — **Compartido** (con la Fase 2 general; otros bloques lo referencian como `UC11·T004`–`UC11·T009`)

Prerrequisitos transversales sin los cuales ninguna tarea de UC11 es testeable.

- [ ] **T004** · `DomainException` + `FinancialParametersValidationException` (`domain/exception`), base de `ProblemDetailsConfig` (T008). · M: `none` · P: `pending`
- [ ] **T005** · `FinancialParameters` (dominio, inmutable, `BigDecimal`, validación de rangos de RF-013, `equals`/`hashCode`). · M: `none` · P: `pending`
- [ ] **T006** · Pruebas unitarias de dominio: rangos válidos/inválidos, null, formatos (RF-013, RNF-002). · M: `none` · P: `pending`
- [ ] **T007** · Puerto `FinancialParametersRepository` (out) y `SaveFinancialParametersUseCase`/`LoadFinancialParametersUseCase` (in) — los tres del general §3.5 que consume UC11 (RF-006). · M: `none` · P: `pending`
- [ ] **T008** · `ProblemDetailsConfig` (RFC 9457, `contracts/README.md` §3.3) con mapeo `VALIDATION_ERROR` 400, `PARAMETERS_NOT_CONFIGURED` 404, `PARAMETERS_SAVE_FAILED` 500, `INTERNAL_ERROR` 500. · M: `none` · P: `pending`
- [ ] **T009** · `SecurityConfig`: OAuth2 Resource Server + regla `ADMIN_FINANCIERO` sobre `/api/v1/financial-parameters` **[OQ-UC11-01: mecanismo por defecto del general §7.1; tarea condicionada a su confirmación]**. · M: `none` · P: `pending`

### Phase 3: US1 — Comisión de plataforma y tarifa de seguro (HU1, RF-001, RF-002, RNF-001/002, CE-001, CE-002, CE-004)

- [ ] **T010** · Test de contrato del `PUT` para los dos campos de US1: cuerpo válido → 204; string decimal aceptado (RNF-002). · M: `none` · P: `pending`
- [ ] **T011** · Prueba unitaria de `FinancialParametersService.save` con command de HU1 (Mockito sobre el puerto). · M: `none` · P: `pending`
- [ ] **T012** · `SaveFinancialParametersUseCase` + `FinancialParametersCommand` (los cuatro campos obligatorios, RNF-001). · M: `none` · P: `pending`
- [ ] **T013** · `FinancialParametersRepository` (out) — definido en T007; aquí se congelan sus métodos `save`/`findById` para consumidores RF-006. · M: `none` · P: `pending`
- [ ] **T014** · `FinancialParametersJpaEntity` + `FinancialParametersJpaRepository` sobre la tabla singleton (`@Table(name = "financial_parameters")`, `id = 1`). · M: `none` · P: `pending`
- [ ] **T015** · `FinancialParametersMapper` (MapStruct): dominio ↔ entidad, con mapeo D-UC11-09 (`platform_commission_percentage`→`commission_pct`…). · M: `none` · P: `pending`
- [ ] **T016** · `FinancialParametersPersistenceAdapter` (out): `save` atómico de la fila `id = 1`, **upsert** para la primera configuración (D-UC11-12) y degradación de errores a `PARAMETERS_SAVE_FAILED`. · M: `none` · P: `pending`
- [ ] **T017** · `FinancialParametersService.save`: valida → persiste en una transacción única → sin actualizaciones parciales (RF-005, RF-011). · M: `none` · P: `pending`
- [ ] **T018** · `FinancialParametersController` (`PUT`) + `FinancialParametersRequest` (claves JSON de los contratos). · M: `none` · P: `pending`
- [ ] **T019** · Pruebas de integración del adaptador (Testcontainers): persistencia real de los dos campos + CHECK de BD. · M: `none` · P: `pending`

### Phase 4: US2 — Porcentajes de tarifa dinámica (HU2, RF-003, RF-004, RF-011, RF-013)

- [ ] **T020** · Pruebas de dominio para `weekendIncreasePct`/`highSeasonIncreasePct`: límites 0, 100, fuera de rango, no numéricos (RF-013). · M: `none` · P: `pending`
- [ ] **T021** · Test de contrato: `PUT` con los 4 campos y un valor fuera de rango → `400 VALIDATION_ERROR` con detalle de campo y **sin persistencia** (CE-004). · M: `none` · P: `pending`
- [ ] **T022** · Prueba de integración del adaptador: sobrescritura completa de los cuatro valores en la fila única (RF-005). · M: `none` · P: `pending`
- [ ] **T023** · Completar `FinancialParametersRequest`/command con los dos porcentajes (si se añadieron por partes en HU1). · M: `none` · P: `pending`
- [ ] **T024** · Pruebas de dominio restantes de RF-013 (formatos no numéricos en los cuatro campos). · M: `none` · P: `pending`
- [ ] **T025** · Verificación de atomicidad en `FinancialParametersService` (RF-005): fallo de persistencia → no se reporta éxito. · M: `none` · P: `pending`

### Phase 5: US3 — Visualizar vigentes y datos de solo lectura (HU3, RF-009, RF-012, RF-006)

- [ ] **T026** · Test de contrato del `GET`: 200 con los 6 campos (4 editables + `guarantee_deposit_rule` + `high_season_windows`) y strings decimales (RF-009, RNF-002). · M: `none` · P: `pending`
- [ ] **T027** · Test de contrato del `GET` sin fila configurada → `404 PARAMETERS_NOT_CONFIGURED` (HU3 escenario 2). · M: `none` · P: `pending`
- [ ] **T028** · Prueba de seguridad CE-003: 401 sin token, 403 con rol distinto, 200 con `ADMIN_FINANCIERO` en `GET` y `PUT`. · M: `none` · P: `pending`
- [ ] **T029** · Consumir `HighSeasonCalendar` (de UC02, `domain/service`): inyectarlo en `FinancialParametersService` para obtener las ventanas de temporada alta (RF-012). UC11 no lo implementa ni prueba sus ventanas, Semana Santa ni puentes (eso es UC02); en las pruebas de UC11 se usa un doble que devuelve ventanas fijas. Condicionada a **OQ-UC11-05**. · M: `none` · P: `pending`
- [ ] **T030** · `LoadFinancialParametersUseCase` + `FinancialParametersResult` (4 vigentes + 2 derivados, RF-009, RNF-001). · M: `none` · P: `pending`
- [ ] **T031** · `FinancialParametersService.load`: combina fila singleton + ventanas del `HighSeasonCalendar` (T029) + regla del depósito; propaga `PARAMETERS_NOT_CONFIGURED`. · M: `none` · P: `pending`
- [ ] **T032** · `FinancialParametersController` (`GET`) + `FinancialParametersResponse` con las claves del contrato (D-UC11-04 pendiente de formato). · M: `none` · P: `pending`
- [ ] **T033** · `FinancialParametersPersistenceAdapter.load` (lectura de la fila; vacío → `Optional.empty`). · M: `none` · P: `pending`

### Phase 6: US4 — Guardar, cancelar y manejo de fallos (HU4, RF-005, RF-007, RF-010, RF-011, CE-001, CE-002, CE-004)

- [ ] **T034** · **`ce001_lectura_refleja_ultima_escritura`** (CE-001): `PUT` → `GET` devuelve lo persistido (Testcontainers). · M: `none` · P: `pending`
- [ ] **T035** · **`ce002_sobrescritura_completa_sin_parciales`** (CE-002): `PUT` inválido no modifica la fila; el adaptador solo escribe en `financial_parameters` (RF-007). · M: `none` · P: `pending`
- [ ] **T036** · **`ce004_…`** (CE-004): sin endpoint de cancelación (la cancelación no genera petición, `README` §2); `PUT` inválido → 400 sin cambios. · M: `none` · P: `pending`
- [ ] **T037** · Prueba unitaria de `FinancialParametersService` ante repositorio que lanza excepción → `PARAMETERS_SAVE_FAILED` (RF-011). · M: `none` · P: `pending`
- [ ] **T038** · Prueba de integración: fallo simulado de BD (constraint/CHECK) → 500 `PARAMETERS_SAVE_FAILED` y fila intacta. · M: `none` · P: `pending`
- [ ] **T039** · Manejo del error no controlado → Problem Details `INTERNAL_ERROR` (catálogo §3.4). · M: `none` · P: `pending`
- [ ] **T040** · Revisión de transaccionalidad: una única transacción por `PUT` (RF-005, RNF-003). · M: `none` · P: `pending`
- [ ] **T041** · Test de contrato del `PUT` completo con los cuatro campos (HU4, flujo feliz y 204). · M: `none` · P: `pending`

### Phase 7: Polish — **parcialmente compartido con Fase 7 (general)**

- [ ] **T042** · ArchUnit: `domain` sin Spring/JPA/Jackson; `application` sin JPA/MapStruct; controller no toca repositorios; entidad JPA no sale de `adapter/out/persistence`; **sin imports de repositorios UC01/UC02/UC04** (RF-007, alcance de datos). · M: `none` · P: `pending`
- [ ] **T043** · Compartido: revisar cobertura (dominio ≥90 %, aplicación ≥80 % [CONV]). · M: `none` · P: `pending`
- [ ] **T044** · Documentación/contratos: verificar que los contratos UC11 y el catálogo `contracts/README.md` §3.4 quedan alineados (códigos y rutas ya figuran en ambos) y que el ejemplo «15-dic a 15-ene» de `UC11-obtener-parametros-financieros.md` quedó corregido a 15-nov a 15-ene y con formato de ventanas por tokens (D-UC11-04, OQ-UC11-03). · M: `none` · P: `pending`
- [ ] **T045** · Compartido: `./mvnw clean verify` final + ejecución de tests de aceptación CE-001…CE-004. · M: `none` · P: `pending`

## Dependencies & Execution Order

```text
T001 ─┬─> T002 ─> T003 ─┬─> T004 ─> T005 ─> T006 ─┬─> T007 ─> T008 ─> T009
      │                │                           │
      │                └───────────────────────────┴─> T010 ─> T011 ─> T012 ─> T013
      │                                                                    │
      │                          T014 ─> T015 ─> T016 ─> T017 ─> T018 ─> T019
      │                                                                        │
      ├──────────────────────────────────────────────────> T020 ─> T021 ─> T022
      │                                                                        │
      ├──────────────────────────────────────────────────> T023 ─> T024 ─> T025
      │                                                                        │
      ├──────────────────────────────────────────────────> T026 ─> T027 ─> T028
      │                                                                        │
      ├──────────────────────────────────────────────────> T029 ─> T030 ─> T031 ─> T032 ─> T033
      │                                                                        │
      └──────────────────────────────────────────────────> T034 ─> T035 ─> T036 ─> T037 ─> T038 ─> T039 ─> T040 ─> T041
                                                                                          │
                                                        T042 ─> T043 ─> T044 ─> T045 <────┘
```

- **Bloque 1 (T001–T003)**: configuración compartida con la Fase 1 general — sin ella nada compila.
- **Bloque 2 (T004–T009)**: fundacional (excepciones, dominio, puertos, Problem Details, seguridad) — compartido con la Fase 2 general; T009 bloquea las pruebas CE-003.
- **US1 (T010–T019)** → **US2 (T020–T025)** → **US3 (T026–T033)** → **US4 (T034–T041)**: orden sugerido por prioridad P1 (HU1–HU4) y por dependencia (el `GET` necesita el servicio de US1/US2; US4 certifica la atomicidad de todo).
- **T042–T045**: solo después de toda la funcionalidad.
- Riesgo de secuencia: T009 (seguridad) queda condicionada a **OQ-UC11-01**; alternativa [CONV]: implementar con la propuesta por defecto del general §7.1 y sustituir tras confirmación.

## Notes

- El SPEC 11 **no** define metas de rendimiento, formato del array `high_season_windows`, redacción literal del depósito ni mecanismo de autenticación → `[NEEDS CLARIFICATION]` (OQ-UC11-01) y OQ-UC11-03, OQ-UC11-04, D-UC11-04, D-UC11-06.
- La primera configuración (HU3 esc. 2) exige `404` en lectura y creación en escritura → D-UC11-12 (migración sin fila + upsert en T016).
- `HighSeasonCalendar` es de UC02: UC11 solo lo consume (T029, T031). El ejemplo «15-dic a 15-ene» del contrato `UC11-obtener…` contradice al SPEC 2 (15 de noviembre); rige el SPEC 2 (D-UC11-04).
- Los consumidores del singleton (UC01/UC02/UC04, bloque A) **están fuera de este plan**: RF-006 solo expone el puerto; UC10 usa la comisión congelada (general-plan D-27), no lee el singleton; su ausencia se maneja en cada HU correspondiente.
- Etiquetas usadas: `[SPEC]` (SPEC 11 y contratos), `[CONV]` (general-plan, inferido), `[PEND]`/`[NEEDS CLARIFICATION]` (sin definir).

## Checklist de auto-revisión

- [ ] Estructura idéntica a `plan-template.md` (Summary con tabla de trazabilidad, Technical Context, Project Structure, fases con `T0NN`/`M`/`P`, Dependencies, Notes).
- [ ] Sin placeholders ni tareas de ejemplo; sin etiquetas "Option 1/2".
- [ ] Fecha `2026-10-08` y enlace a `spec.md`.
- [ ] Toda regla marcada `[SPEC]`, `[CONV]`, `[PEND]` o `[NEEDS CLARIFICATION]`.
- [ ] Contradicciones D-UC11-04, D-UC11-06, D-UC11-08 a D-UC11-12 en "Discrepancias y puntos abiertos" con decisión explícita, sin resolución silenciosa.
- [ ] Preguntas abiertas definidas en el propio plan (OQ-UC11-01 a OQ-UC11-05), sin remitir a secciones inexistentes del plan general.
- [ ] Numeración de tareas del árbol de código coherente con las fases; `HighSeasonCalendar` solo consumido, no implementado.
- [ ] Sin nombres de tablas/campos inventados: `financial_parameters` y columnas de `general-plan` §4; claves JSON de los contratos UC11.
- [ ] Cada RF/RNF/CE/HU del SPEC 11 trazado a componente y tarea.
- [ ] Pruebas CE-001…CE-004 con nombre `ce00X_…`.
- [ ] Reglas de arquitectura hexagonal (§3.4) y lenguaje ubicuo (§13) aplicadas.