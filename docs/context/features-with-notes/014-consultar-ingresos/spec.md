# Especificación de Funcionalidad: UC14 - Consultar Ingresos

**Creado**: 2026-09-06 (v2 — el alcance de la consulta se resuelve mediante el propietario asociado a cada `RegistroDeDispersión` —capturado en "Brindar información de reserva" (SPEC 3) y copiado al registro de dispersión—, eliminando la dependencia de consultar al Sistema de Gestión de Flota las embarcaciones del propietario)

> **Nota de trazabilidad**: Este caso de uso es invocado directamente por el Propietario para consultar, de solo lectura, el total agregado de ingresos generados por sus embarcaciones a partir de las dispersiones de fondos ya liquidadas por "Liquidar fondos de alquiler" (SPEC 10), incluyendo tanto los ingresos por alquileres finalizados sin incidentes como las compensaciones recibidas por penalidades de cancelación (cancelación moderada y cancelación tardía/No-Show). A diferencia de "Consultar registros financieros" (SPEC 12), que expone el detalle paginado transacción por transacción tanto al Propietario como al Administrador Financiero, este caso de uso se expone **exclusivamente al Propietario** y devuelve una **cifra agregada** de ingresos, calculada a partir de los registros de dispersión (`RegistroDeDispersión`) exitosos asociados a las reservas cuyos registros pertenecen al Propietario solicitante. El alcance se determina por el **propietario asociado** a cada `RegistroDeDispersión` (capturado en "Brindar información de reserva" (SPEC 3) y copiado al registro de dispersión en SPEC 10), por lo que este caso de uso **no consulta al Sistema de Gestión de Flota** para determinar ni validar las embarcaciones del Propietario. El Propietario puede consultar el agregado de la totalidad de sus registros por defecto, o filtrar la consulta indicando explícitamente los identificadores de una o varias de sus propias embarcaciones (considerándose propias aquellas asociadas a dispersiones cuyo propietario coincida con el solicitante).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el total agregado de ingresos propios, incluyendo compensaciones por penalidad (Prioridad: P1)

Como el sistema, al recibir del Propietario una solicitud de consulta de ingresos —indicando opcionalmente uno o más identificadores de embarcaciones propias— quiero calcular el total agregado de los montos de las dispersiones exitosas cuyo propietario asociado coincida con el del solicitante (o únicamente las de las embarcaciones indicadas, cuando se filtre), incluyendo tanto la liquidación estándar como las compensaciones por penalidad de cancelación, de manera que el Propietario pueda revisar cuánto ha generado su flota sin necesitar el detalle transacción por transacción.

**Por qué esta prioridad**: Es el mecanismo con el que el propietario supervisa el desempeño financiero de sus reservas tras la dispersión de fondos, incluyendo el ingreso adicional que recibe cuando una reserva se cancela de forma moderada o tardía/No-Show. Dado que cada dispersión conserva su propietario asociado, el alcance se resuelve sin consultas externas al Sistema de Gestión de Flota.

**Prueba Independiente**: Con registros de dispersión previamente creados —incluyendo liquidaciones estándar y compensaciones por penalidad— para reservas de varios propietarios, enviar al sistema una solicitud de consulta de ingresos desde un Propietario específico (con y sin filtro de embarcaciones) y validar que el sistema devuelve el total agregado correspondiente exclusivamente a los registros cuyo propietario asociado coincide con el del solicitante.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta del total agregado de ingresos propios (sin filtro).
   - **Dado** que un Propietario cuenta con dispersiones exitosas asociadas a reservas de varias de sus embarcaciones.
   - **Cuando** el Propietario solicita al sistema el total de ingresos sin indicar ningún identificador de embarcación.
   - **Entonces** el sistema devuelve el total agregado de todas las dispersiones exitosas cuyo propietario asociado coincide con el del solicitante, sin consultar al Sistema de Gestión de Flota.

2. **Escenario**: Consulta del total agregado de ingresos filtrado a una o más embarcaciones específicas.
   - **Dado** que un Propietario indica explícitamente los identificadores de una o varias de sus propias embarcaciones.
   - **Cuando** el Propietario solicita al sistema el total de ingresos para dichas embarcaciones.
   - **Entonces** el sistema devuelve el total agregado de las dispersiones exitosas de dichas embarcaciones cuyo propietario asociado coincide con el del solicitante, excluyendo cualquier identificador que no tenga registros asociados a ese propietario.

3. **Escenario**: Inclusión de compensaciones por penalidad de cancelación dentro del total de ingresos.
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

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Propietario una solicitud de consulta de ingresos, indicando opcionalmente uno o más identificadores de embarcaciones a consultar.
- **RF-002**: El sistema DEBE, cuando no se indique ningún identificador de embarcación, calcular el total agregado de ingresos sobre la totalidad de los registros de dispersión (`RegistroDeDispersión`) exitosos cuyo propietario asociado coincida con el del Propietario solicitante, sin consultar al Sistema de Gestión de Flota.
- **RF-003**: El sistema DEBE, cuando se indiquen uno o más identificadores de embarcaciones, calcular el total agregado de ingresos únicamente sobre las dispersiones exitosas de dichas embarcaciones cuyo propietario asociado coincida con el del Propietario solicitante, excluyendo del cálculo cualquier identificador de embarcación que no tenga dispersiones asociadas a ese propietario.
- **RF-004**: El sistema DEBE calcular el total agregado de ingresos como la suma de los montos de los registros de dispersión (`RegistroDeDispersión`) en estado exitoso dentro del alcance determinado en RF-002 o RF-003, incluyendo tanto las dispersiones por liquidación estándar (finalización sin incidentes) como las compensaciones por penalidad de cancelación (moderada y tardía/No-Show).
- **RF-005**: El sistema DEBE devolver, junto con el total agregado, un desglose de dicho total por embarcación y por origen de la dispersión (liquidación estándar o compensación por penalidad de cancelación).
- **RF-006**: El sistema DEBE devolver un total de cero, sin error, cuando no existan dispersiones exitosas dentro del alcance de la consulta.
- **RF-007**: El sistema DEBE exponer este caso de uso exclusivamente al Propietario.
- **RF-008**: El sistema NO DEBE crear, modificar ni eliminar ningún registro de dispersión como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.
- **RF-009**: El sistema DEBE excluir del cálculo de ingresos de todo Propietario los registros de dispersión sin propietario asociado (inconsistencias).
- **RF-010**: El sistema NO DEBE consultar al Sistema de Gestión de Flota en ningún momento dentro de este caso de uso; el alcance y la validación de las embarcaciones del Propietario se resuelven exclusivamente por el propietario asociado a cada `RegistroDeDispersión`.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Propietario, tanto para recibir la solicitud de consulta (con el filtro opcional de embarcaciones) como para devolver el resultado agregado.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar el total agregado y cada componente de su desglose.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante la presencia de registros de dispersión sin propietario asociado, excluyéndolos del cálculo de ingresos de todo Propietario sin provocar totales inconsistentes.

### Entidades Clave

- **RegistroDeDispersión (Entidad, definida en SPEC 10)**: En este caso de uso es únicamente consultada, para calcular el total agregado de ingresos del Propietario. Su atributo de propietario asociado y el identificador de la embarcación de la reserva determinan el alcance de la consulta.
- **SolicitudConsultaIngresos (DTO)**: Información recibida desde el Propietario para esta operación. Contiene, opcionalmente, la lista de identificadores de embarcaciones a consultar; si se omite, el alcance es la totalidad de los registros del Propietario solicitante.
- **IngresosResultado (DTO)**: Resultado que el sistema devuelve al Propietario. Contiene el total agregado de ingresos dentro del alcance consultado, junto con el desglose de dicho total por embarcación y por origen (liquidación estándar o compensación por penalidad). No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance Correcto, "100% de las consultas de ingresos, con o sin filtro de embarcaciones, devuelven totales calculados exclusivamente a partir de `RegistroDeDispersión` cuyo propietario asociado coincide con el del solicitante, con cero (0) montos de otros propietarios incluidos, en pruebas automatizadas".
- **CE-002**: Precisión Financiera, "100% de los totales agregados devueltos corresponden exactamente a la suma de los RegistroDeDispersión exitosos dentro del alcance correspondiente, incluyendo las compensaciones por penalidad, con cero (0) discrepancias detectadas en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeDispersión como resultado de la ejecución de este caso de uso".
- **CE-004**: Consistencia del Alcance, "100% de los registros de dispersión sin propietario asociado son excluidos del cálculo de ingresos de todo Propietario, sin provocar totales inconsistentes".
- **CE-005**: Autonomía del Alcance, "0 consultas realizadas por este caso de uso al Sistema de Gestión de Flota, confirmando que el alcance y la validación de las embarcaciones se resuelven íntegramente mediante el propietario asociado a cada `RegistroDeDispersión`".