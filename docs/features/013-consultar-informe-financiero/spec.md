# Especificación de Funcionalidad: UC13 - Consultar Informe Financiero

**Creado**: 2026-09-26

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el informe financiero de la plataforma (Prioridad: P1)

Como Administrador Financiero, quiero consultar datos agregados de los registros financieros de toda la plataforma para una periodicidad y un período determinados, de manera que pueda supervisar el neto generado exclusivamente por las operaciones financieras que gestiona el sistema.

**Por qué esta prioridad**: El Administrador Financiero necesita una visión agregada de la actividad financiera de la plataforma. Este informe no representa un balance financiero global ni incluye costos operativos u otros egresos externos a los registros definidos por el sistema.

**Prueba Independiente**: Con registros de cobro, reembolso, dispersión y comisión confirmados en distintos períodos y asociados a varias reservas, solicitar el informe como Administrador Financiero para cada periodicidad y validar que las métricas corresponden al agregado global del período seleccionado sin contar la comisión dos veces.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta del último período cerrado.
   - **Dado** que el Administrador Financiero selecciona una periodicidad quincenal, mensual o trimestral.
   - **Cuando** solicita el informe sin seleccionar un período anterior.
   - **Entonces** el sistema devuelve el informe del último período cerrado disponible y la comparación con el período inmediatamente anterior equivalente.

2. **Escenario**: Consulta de un período anterior.
   - **Dado** que existen períodos anteriores para la periodicidad seleccionada.
   - **Cuando** el Administrador Financiero selecciona uno de esos períodos.
   - **Entonces** el sistema devuelve únicamente los datos agregados de ese período y su comparación con el período inmediatamente anterior equivalente.

### Historia de Usuario 2 - Consultar el informe financiero propio (Prioridad: P1)

Como Propietario, quiero consultar datos agregados de los registros financieros relacionados con mis reservas para una periodicidad y un período determinados, de manera que pueda conocer mis ganancias (el total de dispersiones confirmadas a mi favor) sin acceder a datos de otros propietarios.

**Por qué esta prioridad**: El Propietario necesita consultar sus resultados financieros de forma resumida por período, sin revisar cada operación individual. El alcance debe resolverse con la relación registrada entre cada operación financiera y la reserva o el propietario asociado.

**Prueba Independiente**: Con registros financieros relacionados con reservas de varios propietarios y distribuidos en distintos períodos, solicitar el informe como un Propietario y validar que las métricas solo incluyen los registros relacionados con sus reservas, incluyendo sus dispersiones confirmadas a favor, y que la comparación usa el período anterior equivalente.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta propia del último período cerrado.
   - **Dado** que el Propietario selecciona una periodicidad quincenal, mensual o trimestral.
   - **Cuando** solicita el informe sin seleccionar un período anterior.
   - **Entonces** el sistema devuelve los datos agregados del último período cerrado únicamente para sus reservas y la comparación con el período inmediatamente anterior equivalente.

2. **Escenario**: Consulta propia de un período anterior.
   - **Dado** que existen períodos anteriores para la periodicidad seleccionada.
   - **Cuando** el Propietario selecciona uno de esos períodos.
   - **Entonces** el sistema devuelve únicamente los datos agregados de sus reservas en ese período y la comparación con el período inmediatamente anterior equivalente.

### Historia de Usuario 3 - Exportar el informe financiero (Prioridad: P2)

Como Administrador Financiero o Propietario, quiero exportar el informe financiero consultado a un archivo `.csv` con los mismos datos agregados, período y alcance de la consulta, de manera que pueda conservarlo sin reconstruirlo a partir de los registros individuales.

**Por qué esta prioridad**: La exportación permite conservar o compartir el resultado agregado del período consultado. Debe usar exactamente el mismo cálculo y alcance de la consulta para evitar diferencias entre la información mostrada y el archivo generado.

**Prueba Independiente**: Solicitar la exportación de un informe como Administrador Financiero y como Propietario para un mismo tipo de período, validar que cada archivo contiene las métricas agregadas correspondientes, que el alcance del Propietario está restringido a sus reservas y que los valores coinciden con la consulta.

**Escenarios de Aceptación**:

1. **Escenario**: Exportación del informe de la plataforma.
   - **Dado** que el Administrador Financiero ha seleccionado una periodicidad y un período válidos.
   - **Cuando** solicita la exportación del informe.
   - **Entonces** el sistema genera un archivo `.csv` con los datos agregados de toda la plataforma, la periodicidad, el período, el solicitante y la fecha y hora de generación.

2. **Escenario**: Exportación del informe propio.
   - **Dado** que el Propietario ha seleccionado una periodicidad y un período válidos.
   - **Cuando** solicita la exportación del informe.
   - **Entonces** el sistema genera un archivo `.csv` con los datos agregados únicamente de sus reservas, la periodicidad, el período, el solicitante y la fecha y hora de generación.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si no existen registros financieros confirmados en el alcance y período seleccionado?**
  El sistema devuelve cero en las métricas monetarias y cero en las cantidades correspondientes, sin considerar esto una condición de error.

- **¿Cómo se definen los períodos del informe?**
  Las quincenas se definen del día 1 al día 15 y del día 16 al último día del mes; los meses corresponden al mes calendario; y los trimestres corresponden a enero-marzo, abril-junio, julio-septiembre y octubre-diciembre.

- **¿Qué sucede si el período seleccionado es el período en curso?**
  El sistema devuelve un informe parcial, identificado como abierto, con corte en el momento de la consulta. Los períodos anteriores se consideran cerrados.

- **¿Qué sucede si la periodicidad o el período seleccionado son inválidos?**
  El sistema responde con un error controlado y no calcula ni exporta el informe.

- **¿Se incluyen depósitos de garantía pendientes?**
  No. Un depósito pendiente que todavía no tenga un registro financiero confirmado no forma parte del informe. Solo se agrega cuando su reembolso o liquidación queda registrado como operación financiera confirmada.

- **¿Puede un Propietario consultar datos de reservas ajenas?**
  No. El sistema limita el informe a los registros financieros relacionados con las reservas del Propietario solicitante.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Administrador Financiero o del Propietario una solicitud que indique la periodicidad quincenal, mensual o trimestral y el período seleccionado.
- **RF-002**: El sistema DEBE definir las quincenas del día 1 al día 15 y del día 16 al último día del mes, los meses calendario y los trimestres calendario.
- **RF-003**: El sistema DEBE mostrar por defecto el último período cerrado de la periodicidad seleccionada y la comparación con el período inmediatamente anterior equivalente.
- **RF-004**: El sistema DEBE permitir seleccionar períodos anteriores de la periodicidad elegida y NO DEBE aceptar rangos libres de fechas.
- **RF-005**: El sistema DEBE calcular para el Administrador Financiero el agregado de todos los registros financieros confirmados de la plataforma que correspondan al período seleccionado.
- **RF-006**: El sistema DEBE calcular para el Propietario el agregado de los registros financieros confirmados relacionados con sus reservas que correspondan al período seleccionado.
- **RF-007**: El sistema DEBE agregar los cobros, reembolsos y dispersiones confirmados a partir de los montos registrados y la fecha de cada operación.
- **RF-008**: El sistema DEBE calcular para el Administrador Financiero el neto generado como el total de cobros confirmados menos el total de reembolsos confirmados y menos el total de dispersiones confirmadas, y para el Propietario sus ganancias como el total de dispersiones confirmadas a su favor (liquidación del alquiler, compensaciones por cancelación y depósitos liquidados), sin incluir costos operativos ni conceptos externos a los registros financieros definidos por el sistema.
- **RF-009**: El sistema DEBE incluir el total de comisiones representado por los `RegistroDeComisión` confirmados y la cantidad de registros agrupada por tipo. El total de comisiones es una métrica informativa independiente y NO DEBE sumarse ni restarse nuevamente en el cálculo del neto o de las ganancias, porque la comisión ya fue considerada al calcular la liquidación estándar.
- **RF-010**: El sistema DEBE incluir la variación absoluta y porcentual frente al período inmediatamente anterior equivalente. Si el valor del período anterior es cero, la variación porcentual DEBE indicarse como no calculable.
- **RF-011**: El sistema DEBE devolver un DTO con datos agregados y NO DEBE devolver mediante este caso de uso el detalle individual paginado definido en "Consultar registros financieros" (SPEC 12).
- **RF-012**: El sistema DEBE permitir al Administrador Financiero y al Propietario exportar el informe correspondiente a su alcance y período seleccionado en formato `.csv`.
- **RF-013**: El archivo `.csv` exportado DEBE contener los mismos datos agregados, periodicidad, período y alcance que la consulta correspondiente, y la exportación NO DEBE modificar registros financieros.
- **RF-014**: Los depósitos de garantía pendientes que no tengan un registro financiero confirmado NO DEBEN incluirse en las métricas del informe.
- **RF-015**: El sistema NO DEBE consultar al Sistema de Gestión de Flota para determinar el alcance del informe.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para recibir la solicitud, devolver el informe agregado y producir el archivo `.csv` de exportación.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar las métricas monetarias del informe y la comparación entre períodos.
- **RNF-003**: El sistema DEBE utilizar la fecha y hora de creación (fechaHoraCreación) del registro inmutable de cada operación financiera para determinar su inclusión en el período seleccionado.
- **RNF-004**: El sistema DEBE garantizar que la exportación y la consulta utilicen el mismo alcance, período y cálculo.

### Entidades Clave

- **RegistroDeCobro (Entidad Inmutable, definida en SPEC 5)**: En este caso de uso es únicamente consultada como métrica informativa de los cobros confirmados del período; no participa en cálculos de cobro.
- **RegistroDeReembolso (Entidad Inmutable, definida en SPEC 9)**: En este caso de uso es únicamente consultada como métrica informativa de los reembolsos confirmados del período; no participa en cálculos de reembolso.
- **RegistroDeDispersión (Entidad Inmutable, definida en SPEC 10)**: En este caso de uso es únicamente consultada como métrica informativa de las dispersiones confirmadas; no participa en cálculos de dispersión.
- **RegistroDeComisión (Entidad Inmutable, definida en SPEC 10)**: En este caso de uso es únicamente consultada como métrica informativa de las comisiones confirmadas; no participa en cálculos de cobro, reembolso o dispersión.
- **SolicitudInformeFinanciero (DTO)**: Información recibida del Administrador Financiero o del Propietario. Contiene la periodicidad y el período seleccionado.
- **InformeFinancieroResultado (DTO)**: Resultado agregado que contiene las métricas del período, el alcance, la periodicidad y la comparación con el período anterior equivalente.
- **ArchivoExportacionInformeFinanciero (DTO)**: Archivo `.csv` que contiene el informe agregado, la periodicidad, el período, el alcance, el solicitante y la fecha y hora de generación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance del Informe, "100% de los informes del Administrador Financiero agregan únicamente registros financieros de la plataforma y 100% de los informes del Propietario agregan únicamente registros relacionados con sus reservas, con cero (0) datos de otros alcances expuestos".
- **CE-002**: Precisión del Neto, "100% de los informes del Administrador Financiero calculan el neto generado como cobros confirmados menos reembolsos confirmados menos dispersiones confirmadas, y 100% de los informes del Propietario calculan sus ganancias como el total de dispersiones confirmadas a su favor, con el total de comisiones presentado como métrica informativa independiente y sin doble contabilización".
- **CE-003**: Consistencia de Periodicidad, "100% de los períodos quincenales, mensuales y trimestrales utilizan los límites fijos definidos y 0 solicitudes con rangos libres de fechas son aceptadas".
- **CE-004**: Comparación Temporal, "100% de las comparaciones utilizan el período inmediatamente anterior equivalente y las variaciones porcentuales con valor anterior cero se identifican como no calculables".
- **CE-005**: Exclusión de Garantías Pendientes, "100% de los depósitos de garantía pendientes sin registro financiero confirmado quedan excluidos del informe".
- **CE-006**: Fidelidad de la Exportación, "100% de los archivos `.csv` exportados contienen los mismos datos agregados, período y alcance que la consulta correspondiente, sin modificar registros financieros".