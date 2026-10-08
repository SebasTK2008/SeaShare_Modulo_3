# Guía de trabajo: creación de los planes técnicos (plan.md) por bloques

SEA-SHARE · Módulo 3 (sistema financiero) · Versión 4 · 2026-10-08

Esta guía reparte **la redacción** de los planes técnicos entre tres personas. No reparte la implementación ni aísla a nadie: los bloques son una forma de no pisarse, pero los tres hablan durante todo el proceso. La arquitectura, el modelo de datos, los contratos, los estados y las decisiones ya están en `docs/technical-plan/general-plan.md`, que es la referencia común. Esta guía solo añade lo que `general-plan.md` no fija y que sí causa choques entre bloques (sección 3).

## 1. Quién hace qué

El revisor es quien hace la revisión cruzada ligera de la sección 5 (A revisa B, B revisa C, C revisa A). UC11 ya tiene plan, pero se rehace.

| Bloque | Orden | UC | Caso de uso | Responsable | Revisor | Estado |
| --- | --- | --- | --- | --- | --- | --- |
| A | 1 | UC02 | Brindar tarifa base | Persona 1 | Persona 3 | Pendiente |
| A | 2 | UC01 | Solicitar estimación para reserva | Persona 1 | Persona 3 | Pendiente |
| A | 3 | UC03 | Brindar información de reserva | Persona 1 | Persona 3 | Pendiente |
| A | 4 | UC04 | Solicitar el valor calculado de la reserva | Persona 1 | Persona 3 | Pendiente |
| B | 1 | UC11 | Configurar parámetros financieros globales | Persona 2 | Persona 1 | Rehacer |
| B | 2 | UC05 | Procesar cobro | Persona 2 | Persona 1 | Pendiente |
| B | 3 | UC06 | Solicitar confirmación de pago | Persona 2 | Persona 1 | Pendiente |
| B | 4 | UC09 | Reembolsar dinero a arrendatario | Persona 2 | Persona 1 | Pendiente |
| B | 5 | UC07 | Brindar el estado de la reserva | Persona 2 | Persona 1 | Pendiente |
| C | 1 | UC10 | Liquidar fondos de alquiler | Persona 3 | Persona 2 | Pendiente |
| C | 2 | UC08 | Brindar información de disputa de garantía | Persona 3 | Persona 2 | Pendiente |
| C | 3 | UC12 | Consultar registros financieros | Persona 3 | Persona 2 | Pendiente |
| C | 4 | UC13 | Consultar informe financiero | Persona 3 | Persona 2 | Pendiente |


## 2. Cómo trabajamos juntos

- **Regla de oro**: si tu plan toca algo de otro bloque (una tabla, un puerto, un estado), avisa en el canal *antes* de decidirlo, no al terminar.(**JAJJAJAJ fantasmeada pero lo voy a dejar**)
- **Kickoff de 30 minutos** los tres leen la sección 3, ajustan lo que haga falta y arrancan. No hay un documento aparte que confirmar.
- **Dos sincronizaciones cortas** (10–15 min): una cuando cada quien termina su primer plan, y otra antes de la consolidación.


## 3. Acuerdos de costura 

Esta sección es **un documento vivo**: cualquiera la edita cuando se acuerda algo en el canal, y avisa. Los nombres de puertos, tablas, estados y lenguaje ubicuo no se repiten aquí: están en `general-plan.md` §3.5, §4, §10 y §13.

### 3.1 Dueño de cada pieza compartida

El dueño es quien define su forma en su plan; los demás la referencian por nombre.

| Pieza | Dueño | Quién la usa desde otro bloque |
| --- | --- | --- |
| `reservation_information` (incluye montos y parámetros congelados, y la invalidación en el *upsert*) | A (UC03 la crea, UC04 la completa) | B: UC05, UC07, UC09. C: UC08, UC10 |
| `FinancialParametersRepository` y tabla `financial_parameters` | B (UC11) | A: UC01, UC02, UC04 |
| `charge_intent`, `charge_record` | B (UC05) | B: UC06, UC09. C: UC10, UC12, UC13 |
| `refund_intent`, `refund_record` | B (UC09) | C: UC12, UC13 |
| `settlement_intent`, `settlement_record`, `commission_record` | C (UC10) | C: UC12, UC13 |
| `deposit_disposition`, `dispute_event_log` | C (UC08) | B: UC07 (vía `OpenDepositTrackingUseCase`) |
| `reservation_status_log` | B (UC07) | — |
| Vista `v_financial_records` | C (UC12) | C: UC13 |
| `operational_failure`, `outbox_message`, `FailureRecorderPort`, `OutboxPort` | Compartido: se define en las tareas T001–T009 de UC11 y se reutiliza | Todos |

### 3.2 Puertos que cruzan bloques

Los nombres ya están en `general-plan.md` §3.5. Lo único que falta fijar son las **firmas** (parámetros y retorno). Cada dueño las publica en el canal en cuanto las define, y quien llama las copia en su plan.

| Puerto (`application.port.in`) | Dueño | Lo llama | Publicar firma cuando… |
| --- | --- | --- | --- |
| `ProcessChargeUseCase` | B (UC05) | UC07 | B termina UC05 |
| `RequestRefundUseCase` | B (UC09) | UC07, UC08 | B termina UC09 |
| `RequestSettlementUseCase` | C (UC10) | UC07, UC08 | C empieza UC10 (es el primero de C) |
| `OpenDepositTrackingUseCase` | C (UC08) | UC07 | C empieza UC08 |
| `ProvideBaseRateUseCase` | A (UC02) | UC01, UC03 | A empieza UC02 (es el primero de A) |



### 3.3 Migraciones Flyway

Para que las migraciones de tres personas no choquen de número, cada bloque usa un rango. El orden respeta las claves foráneas (C referencia a `charge_intent` de B).

| Rango | Bloque | Contenido |
| --- | --- | --- |
| `V1` | B (UC11) | `financial_parameters` y lo demás de Setup/Foundational |
| `V2x` | A | `reservation_information` |
| `V3x` | B | `charge_*`, `refund_*`, `reservation_status_log` |
| `V4x` | C | `settlement_*`, `commission_record`, `deposit_disposition`, `dispute_event_log`, vista |

Los números exactos se asignan en la consolidación; en cada plan basta con el nombre del archivo y el rango.

### 3.4 Convenciones comunes

- **IDs de discrepancias y preguntas abiertas**: `D-UC05-01`, `OQ-UC05-01`.
- **Referencias a tareas de otro plan**: `UC11·T005` (así no se confunden con las `T005` locales).
- **Etiquetas de cada regla**: `[SPEC]`, `[CONV]`, `[PEND]` o `[NEEDS CLARIFICATION]`.
- **Tareas compartidas**: las fases Setup y Foundational se marcan «Compartido» y remiten a `UC11·T001` a `UC11·T009`.

## 4. Procedimiento para crear cada plan

1. **Copiar el template**: `docs/templates/plan-template.md` a `docs/features/NNN-<nombre>/plan.md`. El template no se modifica.
2. **Abrir un chat nuevo por plan** con este contexto: el `spec.md` del caso de uso, sus contratos (`docs/technical-plan/contracts/`), `general-plan.md`, esta guía (sobre todo la sección 3), el template y el plan de UC11 ya corregido como ejemplo de estructura (mientras no esté, usar `plan-template.md` y `general-plan.md`).
3. **Usar este prompt** (cambiando `UCxx`):

   > Crea el plan.md del UCxx a partir de plan-template.md, usando spec.md como única fuente de verdad y los contratos adjuntos. Respeta general-plan.md y los acuerdos de costura de la guía (sección 3). Si encuentras requisitos ambiguos o contradicciones entre documentos, hazme preguntas antes de asumir.

4. **Escribir el plan** con estas reglas:
   - Lo que es de otro bloque se referencia por nombre de puerto o de tabla (sección 3), no se replanifica.
   - Las contradicciones y preguntas abiertas se registran con ID (sección 3.4), sin resolverlas en silencio, y se avisan en el canal si afectan a otro bloque.
   - Cada RF, RNF, CE y HU queda trazado a un componente y una tarea, y cada CE tiene su prueba `ceXXX_…`.
   - Si el plan expone un puerto que otro bloque llama, se añade una nota «Puertos expuestos» con su firma.
5. **Autorevisión**: pasar la lista del final del template, sin placeholders ni tareas de ejemplo.

## 5. Revisión cruzada ligera

El revisor no rehace el plan; comprueba tres cosas, con la lista «Revisión crítica del plan» de `sdd-guide` como apoyo:

1. **Costuras**: los puertos y tablas que el plan usa de otro bloque existen en la sección 3 y se llaman igual; los que expone están publicados.
2. **Completitud**: todos los RF, RNF, CE y HU tienen componente, tarea y prueba.
3. **Consistencia**: no contradice `general-plan.md` ni el plan del bloque vecino. Si hay duda, se pregunta en el canal; lo que quede abierto se registra como `D-` u `OQ-`.

Los comentarios se dejan en el canal o como comentarios en el archivo; el responsable los incorpora.

## 6. Qué cuidar

- **No implementar código ni modificar el `pom.xml`** en esta fase: solo se redactan planes.
- **No modificar el template.** Si hace falta un cambio, se propone en el canal.
- **SPEC, contratos y `general-plan.md`**: si encuentras un error, se registra como discrepancia y se avisa en el canal. Se corrige con acuerdo de los tres, y quien lo detecte puede hacer el cambio una vez acordado (no se edita en silencio, porque los otros planes dependen de ello). Los SPEC siguen siendo la única fuente de verdad.
- **Plan de otro bloque**: se puede comentar, pero lo edita su responsable.
- **Costuras**: cambiar la sección 3 es normal y barato: se edita, se avisa en el canal y listo.

## 7. Consolidación

Al terminar, una persona (rota por bloque) unifica discrepancias y preguntas abiertas de los 13 planes, asigna los números definitivos de migración, ajusta la hoja de ruta de `general-plan.md` §12 y marca el estado de cada plan en la tabla de la sección 1.