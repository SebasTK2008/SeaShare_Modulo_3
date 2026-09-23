# Especificación de Funcionalidad: UC14 - Consultar Ingresos

**Creado**: 2026-09-06 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar los ingresos propios de un período financiero, incluyendo compensaciones por penalidad (Prioridad: P1)

Como el sistema, al recibir del Propietario una solicitud de consulta de ingresos —indicando una periodicidad financiera, un período fijo y opcionalmente uno o más identificadores de embarcaciones propias— quiero calcular los ingresos brutos y netos a partir de las dispersiones exitosas cuyo propietario asociado coincida con el del solicitante (o únicamente las de las embarcaciones indicadas, cuando se filtre), incluyendo tanto la liquidación estándar como las compensaciones por penalidad de cancelación, de manera que el Propietario pueda revisar cuánto ha generado su flota sin necesitar el detalle transacción por transacción.

**Por qué esta prioridad**: Es el mecanismo con el que el propietario supervisa el desempeño financiero de sus reservas tras la dispersión de fondos, incluyendo el ingreso adicional que recibe cuando una reserva se cancela de forma moderada o tardía/No-Show. Dado que cada dispersión conserva su propietario asociado, el alcance se resuelve sin consultas externas al Sistema de Gestión de Flota.

**Prueba Independiente**: Con registros de dispersión previamente creados —incluyendo liquidaciones estándar y compensaciones por penalidad— para reservas de varios propietarios, enviar al sistema una solicitud de consulta de ingresos desde un Propietario específico, para cada periodicidad y período permitido, con y sin filtro de embarcaciones, y validar que el sistema devuelve las métricas correspondientes exclusivamente a los registros cuyo propietario asociado coincide con el del solicitante.

**Escenarios de Aceptación**:

0. **Escenario**: Consulta por periodicidad y período financiero fijo.
  - **Dado** que el sistema define períodos quincenales, mensuales y trimestrales mediante los mismos límites fijos de calendario establecidos en "Consultar balance financiero" (SPEC 13).
  - **Cuando** el Propietario solicita sus ingresos indicando una de esas periodicidades y selecciona un período ofrecido.
  - **Entonces** el sistema calcula los ingresos usando exclusivamente las dispersiones exitosas registradas dentro de las fechas de inicio y fin fijas del período seleccionado.

  - El período en curso puede consultarse como período parcial, siempre que el sistema lo identifique explícitamente como abierto y use como fecha de corte el momento de la consulta. No se lo debe tratar como un período cerrado ni comparar como si sus datos fueran definitivos.
  - Para un período cerrado, el sistema ofrece una lista acotada de los períodos más recientes, identificados por sus fechas fijas, de forma consistente con SPEC 13. El período en curso se ofrece separadamente como período parcial cuando corresponda.

1. **Escenario**: Comparación con el período anterior equivalente.
  - **Dado** que existe un período anterior de la misma periodicidad y con la misma duración calendario.
  - **Cuando** el Propietario consulta sus ingresos para un período.
  - **Entonces** el sistema devuelve también las métricas del período anterior equivalente y la variación absoluta y porcentual, sin mezclar registros entre períodos.

2. **Escenario**: Consulta de métricas brutas, netas y promedios.
  - **Dado** que existen dispersiones exitosas dentro del período consultado.
  - **Cuando** el Propietario consulta sus ingresos.
  - **Entonces** el sistema devuelve el total bruto, el total neto y los promedios bruto y neto por reserva con al menos una dispersión exitosa dentro del período.

3. **Escenario**: Consulta del total agregado de ingresos propios (sin filtro).
   - **Dado** que un Propietario cuenta con dispersiones exitosas asociadas a reservas de varias de sus embarcaciones.
   - **Cuando** el Propietario solicita al sistema el total de ingresos sin indicar ningún identificador de embarcación.
   - **Entonces** el sistema devuelve el total agregado de todas las dispersiones exitosas cuyo propietario asociado coincide con el del solicitante, sin consultar al Sistema de Gestión de Flota.

4. **Escenario**: Consulta del total agregado de ingresos filtrado a una o más embarcaciones específicas.
   - **Dado** que un Propietario indica explícitamente los identificadores de una o varias de sus propias embarcaciones.
   - **Cuando** el Propietario solicita al sistema el total de ingresos para dichas embarcaciones.
   - **Entonces** el sistema devuelve el total agregado de las dispersiones exitosas de dichas embarcaciones cuyo propietario asociado coincide con el del solicitante, excluyendo cualquier identificador que no tenga registros asociados a ese propietario.

5. **Escenario**: Inclusión de compensaciones por penalidad de cancelación dentro del total de ingresos.
   - **Dado** que una de las embarcaciones del Propietario tiene una dispersión exitosa originada por una compensación de cancelación moderada o tardía/No-Show.
   - **Cuando** el Propietario consulta su total de ingresos (con o sin filtro de embarcaciones) incluyendo dicha embarcación.
   - **Entonces** el sistema incluye el monto de dicha compensación dentro del total agregado devuelto.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si un registro de dispersión no tiene un propietario asociado (por ejemplo, un registro legado o inconsistente)?**
  El sistema no puede atribuir dicho registro a ningún Propietario. Conforme a RF-009, lo excluye del cálculo de ingresos de todo Propietario, registrando la inconsistencia internamente sin alterar el total del resto de los registros.

- **¿Qué sucede si el Propietario indica, dentro del filtro, un identificador de embarcación que no le pertenece?**
  El sistema considera "propias" únicamente las embarcaciones asociadas a dispersiones cuyo propietario coincida con el del solicitante. Un identificador de embarcación sin dispersiones de ese propietario se excluye del cálculo del total agregado y no aporta ningún monto, sin generar error.

- **¿Qué sucede si no existen dispersiones exitosas dentro del alcance de la consulta (por ejemplo, un Propietario sin ingresos aún, o embarcaciones filtradas sin dispersiones registradas)?**
  El sistema devuelve un total de cero, sin error; la ausencia de ingresos dentro del alcance consultado no constituye una condición de error.

- **¿Se incluyen en el total las dispersiones cuyo resultado aún se encuentra en curso o que hayan fallado?**
  No. Al tratarse de ingresos ya generados para el Propietario, el sistema únicamente contabiliza dentro del total agregado las dispersiones (`RegistroDeDispersión`) que se encuentren en estado exitoso, de forma consistente con el tratamiento de dispersiones exitosas definido en "Consultar balance financiero" (SPEC 13) para el cálculo de comisiones acumuladas.

- **¿Qué sucede si se consulta el período en curso?**
  El sistema devuelve un resultado parcial con corte en la fecha y hora de la consulta, incluyendo únicamente dispersiones exitosas registradas hasta ese momento. Debe identificar el período como abierto y no presentarlo como un resultado definitivo.

- **¿Cómo se compara un período con el anterior?**
  La comparación utiliza el período inmediatamente anterior de igual periodicidad y límites equivalentes. Si no existe información para el período anterior, sus métricas son cero y la variación porcentual se devuelve como no calculable, evitando una división por cero.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Propietario una solicitud de consulta de ingresos, indicando la periodicidad (quincenal, mensual o trimestral), un período financiero fijo y opcionalmente uno o más identificadores de embarcaciones a consultar.
- **RF-002**: El sistema DEBE aplicar a cada periodicidad los límites fijos de calendario definidos en "Consultar balance financiero" (SPEC 13): quincenas del día 1 al 15 y del día 16 al último día del mes, meses calendario y trimestres calendario.
- **RF-003**: El sistema DEBE ofrecer una lista acotada de períodos cerrados recientes para cada periodicidad y, separadamente, el período en curso como período parcial cuando corresponda; no debe ofrecer períodos futuros ni rangos libres.
- **RF-004**: El sistema DEBE, cuando no se indique ningún identificador de embarcación, calcular el resultado sobre la totalidad de los registros de dispersión (`RegistroDeDispersión`) exitosos cuyo propietario asociado coincida con el del Propietario solicitante, sin consultar al Sistema de Gestión de Flota.
- **RF-005**: El sistema DEBE, cuando se indiquen uno o más identificadores de embarcaciones, calcular el resultado únicamente sobre las dispersiones exitosas de dichas embarcaciones cuyo propietario asociado coincida con el del Propietario solicitante, excluyendo cualquier identificador sin dispersiones asociadas a ese propietario.
- **RF-006**: El sistema DEBE incluir únicamente dispersiones exitosas registradas dentro del período consultado y excluir dispersiones en curso, rechazadas, canceladas, expiradas o fallidas.
- **RF-007**: El sistema DEBE calcular el total neto como la suma de los montos confirmados de las dispersiones exitosas dentro del alcance y período consultados.
- **RF-008**: El sistema DEBE calcular el total bruto a partir de los montos brutos de alquiler conservados en las dispersiones exitosas dentro del alcance y período consultados.
- **RF-009**: El sistema DEBE calcular los promedios bruto y neto por reserva, considerando una sola vez cada reserva con al menos una dispersión exitosa dentro del período.
- **RF-010**: El sistema DEBE devolver, junto con las métricas, un desglose por embarcación y por origen de la dispersión (liquidación estándar o compensación por penalidad de cancelación).
- **RF-011**: El sistema DEBE devolver, cuando exista un período anterior equivalente, sus métricas y la variación absoluta y porcentual respecto del período consultado. Si el valor anterior es cero, la variación porcentual debe indicarse como no calculable.
- **RF-012**: El sistema DEBE devolver un resultado parcial con corte en el momento de la consulta cuando el período seleccionado sea el período en curso, identificándolo como abierto.
- **RF-013**: El sistema DEBE devolver valores cero, sin error, cuando no existan dispersiones exitosas dentro del alcance de la consulta.
- **RF-014**: El sistema DEBE exponer este caso de uso exclusivamente al Propietario.
- **RF-015**: El sistema NO DEBE crear, modificar ni eliminar ningún registro de dispersión como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.
- **RF-016**: El sistema DEBE excluir del cálculo de ingresos de todo Propietario los registros de dispersión sin propietario asociado.
- **RF-017**: El sistema NO DEBE consultar al Sistema de Gestión de Flota en ningún momento dentro de este caso de uso; el alcance y la validación de las embarcaciones se resuelven exclusivamente por el propietario asociado a cada `RegistroDeDispersión`.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Propietario, tanto para recibir la solicitud de consulta (con el filtro opcional de embarcaciones) como para devolver el resultado agregado.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar el total agregado y cada componente de su desglose.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante la presencia de registros de dispersión sin propietario asociado, excluyéndolos del cálculo de ingresos de todo Propietario sin provocar totales inconsistentes.
- **RNF-004**: El sistema DEBE utilizar la fecha de registro de la dispersión y los límites fijos del período para determinar su inclusión, usando la fecha y hora de consulta únicamente como corte para el período en curso.

### Entidades Clave

- **RegistroDeDispersión (Entidad, definida en SPEC 10)**: En este caso de uso es únicamente consultada, para calcular el total agregado de ingresos del Propietario. Su atributo de propietario asociado y el identificador de la embarcación de la reserva determinan el alcance de la consulta.
- **SolicitudConsultaIngresos (DTO)**: Información recibida desde el Propietario para esta operación. Contiene la periodicidad, el período fijo seleccionado y, opcionalmente, la lista de identificadores de embarcaciones a consultar; si se omite, el alcance es la totalidad de los registros del Propietario solicitante.
- **IngresosResultado (DTO)**: Resultado que el sistema devuelve al Propietario. Contiene la periodicidad, el período y su estado (cerrado o en curso), los totales bruto y neto, los promedios bruto y neto por reserva, el desglose por embarcación y origen, y, cuando corresponda, las métricas y variaciones del período anterior equivalente. No representa una entidad persistida.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance Correcto, "100% de las consultas de ingresos, con o sin filtro de embarcaciones, devuelven totales calculados exclusivamente a partir de `RegistroDeDispersión` cuyo propietario asociado coincide con el del solicitante, con cero (0) montos de otros propietarios incluidos, en pruebas automatizadas".
- **CE-002**: Precisión Financiera, "100% de los totales agregados devueltos corresponden exactamente a la suma de los RegistroDeDispersión exitosos dentro del alcance correspondiente, incluyendo las compensaciones por penalidad, con cero (0) discrepancias detectadas en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeDispersión como resultado de la ejecución de este caso de uso".
- **CE-004**: Consistencia del Alcance, "100% de los registros de dispersión sin propietario asociado son excluidos del cálculo de ingresos de todo Propietario, sin provocar totales inconsistentes".
- **CE-005**: Autonomía del Alcance, "0 consultas realizadas por este caso de uso al Sistema de Gestión de Flota, confirmando que el alcance y la validación de las embarcaciones se resuelven íntegramente mediante el propietario asociado a cada `RegistroDeDispersión`".
- **CE-006**: Consistencia Temporal, "100% de las consultas aplican exactamente los límites fijos de calendario de la periodicidad seleccionada, y las consultas del período en curso se identifican como parciales con corte en el momento de la consulta".
- **CE-007**: Exclusión de Estados No Exitosos, "100% de los resultados de ingresos excluyen dispersiones en curso, rechazadas, canceladas, expiradas o fallidas, sin importar que pertenezcan al propietario consultante".
- **CE-008**: Exactitud de Métricas, "100% de los resultados devuelven totales y promedios bruto y neto que coinciden con los importes históricos conservados en las dispersiones exitosas, contando cada reserva una sola vez para los promedios".
- **CE-009**: Comparación Equivalente, "100% de las comparaciones utilizan el período inmediatamente anterior de igual periodicidad y duración calendario, calculando correctamente la variación absoluta y evitando divisiones por cero en la variación porcentual".