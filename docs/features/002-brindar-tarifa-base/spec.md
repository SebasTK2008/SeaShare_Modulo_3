# Especificación de Funcionalidad: UC02 - Brindar Tarifa Base

**Creado**: 2026-09-06  

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Obtener la tarifa base vigente aplicando la tarifa dinámica (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Solicitar estimación para reserva" o "Brindar información de reserva", quiero consultar al Sistema de Gestión de Flota la tarifa base específica de la embarcación solicitada (el precio fijado por su propietario y almacenado en dicho módulo, identificada por su identificador único), y aplicar sobre esa tarifa la regla de tarifa dinámica vigente (temporada alta —determinada automáticamente por la regla de calendario definida en el contexto—, fin de semana u otra condición configurada), de manera que el caso de uso que me invocó reciba la tarifa base por unidad de tiempo lista para ser utilizada en sus propios cálculos.

**Por qué esta prioridad**: Esta es la única función del sistema donde reside la lógica de tarifas dinámicas; tanto la estimación preliminar como el desglose final de precio dependen de que este valor sea correcto, ya que ningún otro caso de uso debe duplicar este cálculo.

**Prueba Independiente**: Invocar "Brindar tarifa base" con el identificador de una embarcación para distintas fechas (día regular, fin de semana, temporada alta) y validar que el sistema consulta al Sistema de Gestión de Flota la tarifa base registrada para esa embarcación específica (por su ID) y devuelve la tarifa ajustada según la condición vigente en cada caso, sin que el tipo o la categoría de la embarcación influyan en el resultado.

**Escenarios de Aceptación**:

1. **Escenario**: Tarifa regular (sin condición dinámica vigente).
   - **Dado** que la fecha evaluada no corresponde a fin de semana ni a temporada alta.
   - **Cuando** el sistema consulta al Sistema de Gestión de Flota la tarifa base registrada para esa embarcación específica.
   - **Entonces** el sistema devuelve la tarifa base sin ningún ajuste dinámico aplicado.

2. **Escenario**: Tarifa ajustada por fin de semana.
   - **Dado** que la fecha evaluada corresponde a un fin de semana.
   - **Cuando** el sistema aplica la tarifa dinámica vigente.
   - **Entonces** el sistema devuelve la tarifa base con el ajuste correspondiente a fin de semana.

3. **Escenario**: Tarifa ajustada por temporada alta.
   - **Dado** que la fecha evaluada corresponde a temporada alta según las ventanas exactas de calendario definidas más abajo.
   - **Cuando** el sistema aplica la tarifa dinámica vigente.
   - **Entonces** el sistema devuelve la tarifa base con el ajuste correspondiente a temporada alta.

**Regla de cálculo de la tarifa base final** (aplicación de la tarifa dinámica sobre la tarifa provista por el Sistema de Gestión de Flota):

- **Condición regular**: `Tarifa base final = Tarifa base provista por Gestión de Flota` (sin ajuste dinámico).
- **Fin de semana**: `Tarifa base final = Tarifa base × (1 + %IncrementoFinDeSemana / 100)`, usando el porcentaje configurado mediante "Configurar parámetros financieros globales".
- **Temporada alta**: `Tarifa base final = Tarifa base × (1 + %IncrementoTemporadaAlta / 100)`, usando el porcentaje configurado mediante "Configurar parámetros financieros globales", cuando la fecha evaluada caiga dentro de alguna de las ventanas de temporada alta definidas a continuación.
- **Coincidencia de condiciones**: cuando una misma fecha sea simultáneamente fin de semana y temporada alta, el sistema aplica el ajuste que resulte en la tarifa más alta.
- **Ninguna de estas reglas depende del tipo, categoría o cualquier otro atributo de clasificación de la embarcación**: el único insumo variable de la embarcación es su propia tarifa base (por ID), provista por el Sistema de Gestión de Flota.

### Regla de calendario de temporada alta (calendario exacto)

La vigencia de la temporada alta se deriva automáticamente aplicando, a cada año evaluado, las siguientes ventanas fijas:

- **Fin de año**: desde el **15 de noviembre** hasta el **15 de enero** del año siguiente.
- **Mitad de año**: desde el **1 de junio** hasta el **30 de julio** (inclusive).
- **Semana Santa**: los días santos de marzo o abril, calculados mediante el **Algoritmo de Meeus/Jones/Butcher** para determinar el Domingo de Resurrección de cada año evaluado, y a partir de esa fecha derivar la ventana de días santos correspondiente (Jueves y Viernes Santo, y los días inmediatamente adyacentes que el negocio considere parte de la Semana Santa).
- **Semana de receso**: del **5 de octubre** al **12 de octubre**.

Estas ventanas son fijas por calendario, se recalculan automáticamente cada año (incluyendo el cálculo de Semana Santa vía Meeus/Jones/Butcher) y no requieren configuración manual de fechas por parte del Administrador Financiero, quien únicamente configura el **porcentaje de incremento** aplicable.

Los puentes festivos y los fines de semana largos **no** se categorizan como temporada alta; el fin de semana se evalúa exclusivamente como su propia condición dinámica independiente (ver "Regla de cálculo de la tarifa base final"), sin que la condición de puente festivo agregue ningún ajuste adicional.

### Casos Extremos (Edge Cases)

- **¿Qué sucede cuando el Sistema de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable al momento en que el sistema solicita la tarifa base registrada para la embarcación?**
  Conforme a RNF-003, el sistema no asume ninguna tarifa base. Registra el fallo y propaga el error a la operación que lo invocó ("Solicitar estimación para reserva" o "Brindar información de reserva"), que aplica su propio manejo de *fallback* (según lo definido en los SPEC 1 y 3). En ningún caso se entrega una tarifa asumida ni un valor por defecto inventado.

- **¿Cómo debe comportarse el sistema cuando la embarcación consultada no tiene una tarifa base configurada (nula o faltante) en el Sistema de Gestión de Flota?**
  Dado que Gestión de Flota es la única fuente autoritativa de la tarifa base (RF-004), el sistema considera que no puede establecer la tarifa base final para esa embarcación. Registra el fallo y comunica a la operación invocante que la tarifa no está disponible, para que esta la excluya del resultado o la marque como "sin tarifa/estimación disponible", de forma consistente con el tratamiento definido en el SPEC 1, sin inventar un valor.

- **¿Qué tarifa dinámica debe prevalecer cuando una misma fecha coincide simultáneamente con más de una condición vigente (por ejemplo, fin de semana y temporada alta al mismo tiempo)?**
  Cuando más de una condición dinámica vigente coincide sobre la misma fecha, el sistema evalúa todas las condiciones aplicables y aplica aquella cuyo ajuste resulte en la tarifa más alta (la condición más favorable para la plataforma), de manera que el resultado sea siempre determinista y sin depender de un orden de evaluación arbitrario.

- **¿Sobre qué fecha debe evaluarse la tarifa dinámica cuando "Solicitar estimación para reserva" invoca este caso de uso en modo lote, sin fechas específicas (estimación general para la pantalla principal)?**
  En el modo lote sin fechas, la tarifa dinámica se evalúa sobre la fecha actual (el día en que se realiza la solicitud), reflejando las condiciones vigentes ese día. La estimación así obtenida es meramente informativa; al confirmar la reserva, "Solicitar el valor calculado de la reserva" recalcula con las fechas reales y su tarifa dinámica correspondiente.

- **¿Cómo determina el sistema si una fecha evaluada corresponde a temporada alta?**
  El sistema evalúa la fecha contra las ventanas exactas de temporada alta definidas arriba (fin de año: 15 de noviembre–15 de enero; mitad de año: 1 de junio–30 de julio; Semana Santa: calculada mediante el Algoritmo de Meeus/Jones/Butcher; y semana de receso: 5–12 de octubre). Si la fecha cae dentro de alguna de estas ventanas, aplica el porcentaje de incremento de temporada alta configurado por el Administrador Financiero mediante "Configurar parámetros financieros globales". La vigencia se deriva automáticamente de dicha regla de calendario —NO se configura manualmente ni se deriva de los datos de las reservas, del tipo de embarcación ni de su categoría— y los cambios derivados del calendario afectan únicamente los cálculos posteriores, nunca los valores ya aplicados a reservas existentes. Los puentes festivos y los fines de semana largos no forman parte de estas ventanas de temporada alta; únicamente el fin de semana se evalúa como su propia condición dinámica.

- **¿La tarifa dinámica depende del tipo o la categoría de la embarcación (lancha, yate, catamarán)?**
  No. El tipo y la categoría de la embarcación no son un insumo de este caso de uso ni de la regla de tarifa dinámica. El único dato variable de la embarcación utilizado aquí es su propia tarifa base (el precio fijado por su propietario), consultada al Sistema de Gestión de Flota por identificador único. La tarifa dinámica se aplica exclusivamente en función de la fecha evaluada (fin de semana / temporada alta), de forma idéntica para cualquier tipo o categoría de embarcación.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE, al ser invocado mediante `<<include>>`, consultar al Sistema de Gestión de Flota la tarifa base específica (el precio fijado por el propietario) de la embarcación solicitada, identificándola por su identificador único, **sin** requerir ni utilizar el tipo o la categoría de la embarcación para este cálculo.
- **RF-002**: El sistema DEBE aplicar la tarifa dinámica vigente (temporada alta, fin de semana u otra condición vigente) sobre la tarifa provista por el Sistema de Gestión de Flota para obtener la tarifa base final por unidad de tiempo, conforme a la regla de cálculo `tarifa base × (1 + porcentaje de incremento / 100)` cuando aplique una condición dinámica. Esta regla depende exclusivamente de la fecha evaluada.
- **RF-003**: El sistema DEBE devolver la tarifa base final exclusivamente a la operación interna que lo invocó ("Solicitar estimación para reserva" o "Brindar información de reserva"), sin exponer un *endpoint* directo para actores externos.
- **RF-004**: El sistema DEBE utilizar al Sistema de Gestión de Flota como única fuente autoritativa de la tarifa base de cada embarcación, dado que dicho valor es definido por el propietario y almacenado exclusivamente en ese módulo.
- **RF-005**: El sistema NO DEBE delegar ni duplicar en el Sistema de Gestión de Flota, ni en el Módulo de Reservas y Operaciones, el cálculo de la tarifa dinámica; esta lógica reside exclusivamente en este caso de uso.
- **RF-006**: El sistema DEBE determinar si la fecha evaluada corresponde a temporada alta aplicando la siguiente regla exacta de calendario, recalculada automáticamente para cada año evaluado:
  - Fin de año: 15 de noviembre – 15 de enero del año siguiente.
  - Mitad de año: 1 de junio – 30 de julio.
  - Semana Santa: calculada mediante el Algoritmo de Meeus/Jones/Butcher.
  - Semana de receso: 5 de octubre – 12 de octubre.

  sin requerir que el Administrador Financiero configure manualmente las fechas de inicio y fin de dicha condición. Los puentes festivos y los fines de semana largos NO forman parte de esta regla de temporada alta; únicamente el fin de semana constituye una condición dinámica adicional e independiente.
- **RF-007**: El sistema NO DEBE utilizar el tipo, la categoría ni ningún otro atributo de clasificación de la embarcación como insumo para determinar la tarifa base final o la aplicación de la tarifa dinámica.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Sistema de Gestión de Flota, mapeando únicamente los atributos esenciales (identificador de la embarcación y tarifa base) necesarios para el cálculo.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar la tarifa provista por el Sistema de Gestión de Flota, cualquier ajuste dinámico aplicado y la tarifa base final resultante.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) ante fallas de comunicación con el Sistema de Gestión de Flota, dado que tanto "Solicitar estimación para reserva" como "Brindar información de reserva" dependen de este caso de uso para completar su propio flujo.
- **RNF-004**: El sistema DEBE calcular la ventana de Semana Santa de cada año evaluado de forma determinística mediante el Algoritmo de Meeus/Jones/Butcher, sin depender de una tabla de fechas cargada manualmente.

### Entidades Clave

- **EmbarcaciónInfo (DTO)**: Información recibida desde el Sistema de Gestión de Flota, necesaria únicamente para el cálculo de este caso de uso. Contiene el identificador de la embarcación y su tarifa base (el precio fijado por el propietario para esa embarcación específica). No contiene ni requiere el tipo o categoría de la embarcación. No se persiste dentro del sistema; se utiliza solo como insumo de la operación.
- **TarifaBaseResultado (DTO)**: Resultado que el sistema devuelve a la operación invocante. Contiene el identificador de la embarcación y la tarifa base final por unidad de tiempo, ya con la tarifa dinámica aplicada. No representa una entidad persistida, sino el valor de retorno de esta función interna.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de las tarifas base devueltas reflejan correctamente la condición dinámica vigente (regular, fin de semana o temporada alta) para la fecha evaluada, calculada exactamente contra las ventanas de calendario de RF-006 (incluyendo el cálculo determinístico de Semana Santa vía Meeus/Jones/Butcher), con cero (0) errores de cálculo detectados en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "0 casos de uso distintos a 'Brindar tarifa base' contienen lógica de cálculo de tarifa dinámica en su código fuente, confirmando que esta responsabilidad reside exclusivamente en este caso de uso; y 0 cálculos de este caso de uso utilizan el tipo o la categoría de la embarcación como insumo".
- **CE-003**: Resiliencia del Sistema, "100% de las fallas simuladas de comunicación con el Sistema de Gestión de Flota (timeouts, datos inalcanzables) son manejadas mediante fallbacks controlados, sin provocar fallos en 'Solicitar estimación para reserva' ni en 'Brindar información de reserva'".