# Especificación de Funcionalidad: UC14 - Consultar Ingresos

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es invocado directamente por el Propietario para consultar, de solo lectura, el total agregado de ingresos generados por sus embarcaciones a partir de las dispersiones de fondos ya liquidadas por "Dispersar fondos de alquiler" (SPEC 10), incluyendo tanto los ingresos por alquileres finalizados sin incidentes como las compensaciones recibidas por penalidades de cancelación (cancelación moderada y cancelación tardía/No-Show). A diferencia de "Consultar registros financieros" (SPEC 12), que expone el detalle paginado transacción por transacción tanto al Propietario como al Administrador Financiero, este caso de uso se expone **exclusivamente al Propietario** y devuelve una **cifra agregada** de ingresos, calculada a partir de los registros de dispersión (`RegistroDeDispersión`) exitosos asociados a las reservas de las embarcaciones del Propietario solicitante. El Propietario puede consultar el agregado de la totalidad de sus embarcaciones por defecto, o filtrar la consulta indicando explícitamente los identificadores de una o varias de sus propias embarcaciones.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el total agregado de ingresos de las embarcaciones propias, incluyendo compensaciones por penalidad (Prioridad: P1)

Como el sistema, al recibir del Propietario una solicitud de consulta de ingresos —indicando opcionalmente uno o más identificadores de embarcaciones propias— quiero calcular el total agregado de los montos de las dispersiones exitosas asociadas a las reservas de dichas embarcaciones (o de la totalidad de sus embarcaciones si no se indica ningún identificador), incluyendo tanto la liquidación estándar como las compensaciones por penalidad de cancelación, de manera que el Propietario pueda revisar cuánto ha generado su flota sin necesitar el detalle transacción por transacción.

**Por qué esta prioridad**: Es el mecanismo con el que el propietario supervisa el desempeño financiero de sus embarcaciones tras la dispersión de fondos, incluyendo el ingreso adicional que recibe cuando una reserva se cancela de forma moderada o tardía/No-Show.

**Prueba Independiente**: Con registros de dispersión previamente creados —incluyendo liquidaciones estándar y compensaciones por penalidad— para reservas de embarcaciones de varios propietarios, enviar al sistema una solicitud de consulta de ingresos desde un Propietario específico (con y sin filtro de embarcaciones) y validar que el sistema devuelve el total agregado correspondiente exclusivamente a sus propias embarcaciones.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta del total agregado de ingresos de todas las embarcaciones propias (sin filtro).
   - **Dado** que un Propietario cuenta con dispersiones exitosas asociadas a reservas de varias de sus embarcaciones.
   - **Cuando** el Propietario solicita al sistema el total de ingresos sin indicar ningún identificador de embarcación.
   - **Entonces** el sistema consulta al Sistema de Gestión de Flota la totalidad de embarcaciones asociadas a ese Propietario y devuelve el total agregado de las dispersiones exitosas correspondientes a todas ellas.

2. **Escenario**: Consulta del total agregado de ingresos filtrado a una o más embarcaciones específicas.
   - **Dado** que un Propietario indica explícitamente los identificadores de una o varias de sus propias embarcaciones.
   - **Cuando** el Propietario solicita al sistema el total de ingresos para dichas embarcaciones.
   - **Entonces** el sistema valida contra el Sistema de Gestión de Flota que las embarcaciones indicadas le pertenecen, y devuelve el total agregado de las dispersiones exitosas correspondientes exclusivamente a esas embarcaciones.

3. **Escenario**: Inclusión de compensaciones por penalidad de cancelación dentro del total de ingresos.
   - **Dado** que una de las embarcaciones del Propietario tiene una dispersión exitosa originada por una compensación de cancelación moderada o tardía/No-Show.
   - **Cuando** el Propietario consulta su total de ingresos (con o sin filtro de embarcaciones) incluyendo dicha embarcación.
   - **Entonces** el sistema incluye el monto de dicha compensación dentro del total agregado devuelto.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable al momento en que el sistema necesita determinar o validar las embarcaciones que pertenecen al Propietario solicitante?**
  El sistema no puede determinar correctamente el alcance de la consulta sin esta información. Aplica un manejo de errores controlado y responde al Propietario indicando que la consulta no pudo completarse, sin asumir un alcance parcial ni calcular un total sobre embarcaciones no confirmadas como propias.

- **¿Qué sucede si el Propietario indica, dentro del filtro, un identificador de embarcación que no le pertenece?**
  El sistema excluye dicho identificador del cálculo del total agregado, dado que el alcance de la consulta está limitado exclusivamente a las embarcaciones que el Sistema de Gestión de Flota confirma como propias de ese Propietario, de forma consistente con el tratamiento definido en "Consultar registros financieros" (SPEC 12).

- **¿Qué sucede si no existen dispersiones exitosas dentro del alcance de la consulta (por ejemplo, un Propietario sin ingresos aún, o embarcaciones filtradas sin dispersiones registradas)?**
  El sistema devuelve un total de cero, sin error; la ausencia de ingresos dentro del alcance consultado no constituye una condición de error.

- **¿Se incluyen en el total las dispersiones cuyo resultado aún se encuentra en curso o que hayan fallado?**
  No. Al tratarse de ingresos ya generados para el Propietario, el sistema únicamente contabiliza dentro del total agregado las dispersiones (`RegistroDeDispersión`) que se encuentren en estado exitoso, de forma consistente con el tratamiento de dispersiones exitosas definido en "Consultar balance financiero" (SPEC 13) para el cálculo de comisiones acumuladas.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Propietario una solicitud de consulta de ingresos, indicando opcionalmente uno o más identificadores de embarcaciones a consultar.
- **RF-002**: El sistema DEBE, cuando no se indique ningún identificador de embarcación, consultar al Sistema de Gestión de Flota la totalidad de embarcaciones que pertenecen al Propietario solicitante, y calcular el total agregado de ingresos sobre dicha totalidad.
- **RF-003**: El sistema DEBE, cuando se indiquen uno o más identificadores de embarcaciones, validar contra el Sistema de Gestión de Flota que dichas embarcaciones pertenecen al Propietario solicitante, y calcular el total agregado de ingresos exclusivamente sobre las embarcaciones confirmadas como propias, excluyendo cualquier identificador que no le pertenezca.
- **RF-004**: El sistema DEBE calcular el total agregado de ingresos como la suma de los montos de los registros de dispersión (`RegistroDeDispersión`) en estado exitoso asociados a las reservas de las embarcaciones dentro del alcance determinado en RF-002 o RF-003, incluyendo tanto las dispersiones por liquidación estándar (finalización sin incidentes) como las compensaciones por penalidad de cancelación (moderada y tardía/No-Show).
- **RF-005**: El sistema DEBE devolver, junto con el total agregado, un desglose de dicho total por embarcación y por origen de la dispersión (liquidación estándar o compensación por penalidad de cancelación).
- **RF-006**: El sistema DEBE devolver un total de cero, sin error, cuando no existan dispersiones exitosas dentro del alcance de la consulta.
- **RF-007**: El sistema DEBE exponer este caso de uso exclusivamente al Propietario.
- **RF-008**: El sistema NO DEBE crear, modificar ni eliminar ningún registro de dispersión como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Propietario, tanto para recibir la solicitud de consulta (con el filtro opcional de embarcaciones) como para devolver el resultado agregado.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar el total agregado y cada componente de su desglose.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) ante fallas de comunicación con el Sistema de Gestión de Flota al determinar o validar las embarcaciones del Propietario.

### Entidades Clave

- **RegistroDeDispersión (Entidad, definida en SPEC 10)**: En este caso de uso es únicamente consultada, para calcular el total agregado de ingresos del Propietario.
- **SolicitudConsultaIngresos (DTO)**: Información recibida desde el Propietario para esta operación. Contiene, opcionalmente, la lista de identificadores de embarcaciones a consultar; si se omite, el alcance es la totalidad de embarcaciones del Propietario.
- **EmbarcacionesDelPropietario (DTO, definida en SPEC 12)**: Información recibida desde el Sistema de Gestión de Flota, reutilizada aquí para determinar o validar las embarcaciones que pertenecen al Propietario solicitante. No se persiste dentro del sistema.
- **IngresosResultado (DTO)**: Resultado que el sistema devuelve al Propietario. Contiene el total agregado de ingresos dentro del alcance consultado, junto con el desglose de dicho total por embarcación y por origen (liquidación estándar o compensación por penalidad). No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance Correcto, "100% de las consultas de ingresos, con o sin filtro de embarcaciones, devuelven totales calculados exclusivamente a partir de embarcaciones que pertenecen al Propietario solicitante, con cero (0) montos de embarcaciones ajenas incluidos, en pruebas automatizadas".
- **CE-002**: Precisión Financiera, "100% de los totales agregados devueltos corresponden exactamente a la suma de los RegistroDeDispersión exitosos dentro del alcance correspondiente, incluyendo las compensaciones por penalidad, con cero (0) discrepancias detectadas en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeDispersión como resultado de la ejecución de este caso de uso".
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con el Sistema de Gestión de Flota, al determinar o validar las embarcaciones del Propietario, son manejadas mediante fallbacks controlados, sin calcular totales sobre embarcaciones no confirmadas como propias".