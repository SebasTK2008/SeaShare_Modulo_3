# Especificación de Funcionalidad: UC13 - Consultar Balance Financiero

**Creado**: 2026-09-06 (v3 — eliminada la dimensión de año y la selección por número de período. Los períodos del balance son exclusivamente **quincenales, mensuales y trimestrales**, definidos por límites fijos de calendario, y el sistema ofrece al Administrador Financiero los **períodos cerrados más recientes** de cada periodicidad para que seleccione uno, sin rangos de fechas libres ni numeración asociada a un año)

> **Nota de trazabilidad**: Este caso de uso es invocado directamente por el Administrador Financiero para consultar, de solo lectura, el estado consolidado de las finanzas de la plataforma dentro de uno de los períodos fijos de calendario definidos por el sistema: **quincena, mes o trimestre**. El balance está compuesto por tres cifras: **fondos retenidos**, **comisiones acumuladas** y **depósitos en garantía pendientes de resolución**. Los períodos son únicamente quincenales, mensuales y trimestrales, definidos siempre por límites fijos de calendario y sin traslapes ni vacíos entre períodos consecutivos de la misma periodicidad; no existe la dimensión de "año" ni la selección de un rango de fechas libre. El sistema ofrece al Administrador Financiero los **períodos cerrados más recientes** de la periodicidad consultada (por ejemplo, los últimos 12 períodos cerrados), identificados por su rango de fechas, y el Administrador únicamente selecciona uno de los períodos ofrecidos. Este caso de uso consulta, sin modificarlos, los registros ya definidos por "Procesar cobro" (SPEC 5), "Reembolsar dinero a arrendatario" (SPEC 9) y "Liquidar fondos de alquiler" (SPEC 10) —incluyendo el monto de comisión efectivamente aplicado (`comisiónAplicada`)—, así como la información de reserva registrada por "Brindar información de reserva" (SPEC 3), "Solicitar el valor calculado de la reserva" (SPEC 4), "Brindar el estado de la reserva" (SPEC 7) y "Resolver disputa de garantía" (SPEC 8). A diferencia de "Consultar registros financieros" (SPEC 12), este caso de uso no expone el detalle transacción por transacción, sino tres cifras consolidadas, y se expone exclusivamente al Administrador Financiero (no al Propietario).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el balance financiero consolidado de la plataforma para un período cerrado reciente (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero una solicitud de balance financiero indicando una periodicidad (quincenal, mensual o trimestral) y seleccionando uno de los períodos cerrados ofrecidos por el sistema, quiero conocer las fechas de inicio y fin fijas de ese período y a partir de ellas calcular los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución, de manera que el Administrador Financiero pueda supervisar el estado consolidado de las finanzas de la plataforma para ese período fijo, sin indicar años ni definir manualmente rangos de fechas.

**Por qué esta prioridad**: Es el mecanismo con el que el Administrador Financiero supervisa, de forma consolidada y periódica, la salud financiera de la plataforma (cuánto dinero permanece retenido, cuánto ha generado la plataforma en comisiones y cuánto depósito de garantía sigue en disputa). Limitar los períodos a quincenas, meses y trimestres definidos por límites fijos de calendario, y ofrecer únicamente los períodos ya cerrados más recientes, garantiza que los balances de distintos períodos sean comparables entre sí, no se traslapen ni dejen vacíos, y siempre correspondan a períodos completos y verificables.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión previamente creados, y con reservas en distintos estados (incluyendo "completada con incidentes" sin disputa resuelta), enviar al sistema una solicitud de balance financiero para cada una de las tres periodicidades, seleccionando el período cerrado más reciente ofrecido, y validar que el sistema calcula correctamente las fechas de inicio y fin fijas de cada período y devuelve las tres cifras consolidadas correspondientes.

**Escenarios de Aceptación**:

1. **Escenario**: Balance financiero quincenal del período cerrado más reciente.
   - **Dado** que existen registros de cobro, reembolso y dispersión, así como reservas con depósitos de garantía pendientes de resolución.
   - **Cuando** el Administrador Financiero solicita el balance financiero con periodicidad quincenal y selecciona el período cerrado más reciente ofrecido por el sistema.
   - **Entonces** el sistema calcula internamente las fechas de inicio y fin fijas de dicha quincena y calcula y devuelve los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución correspondientes a ese período.

2. **Escenario**: Balance financiero mensual del período cerrado más reciente.
   - **Dado** que existen registros de cobro, reembolso y dispersión, así como reservas con depósitos de garantía pendientes de resolución.
   - **Cuando** el Administrador Financiero solicita el balance financiero con periodicidad mensual y selecciona el período cerrado más reciente ofrecido por el sistema.
   - **Entonces** el sistema calcula internamente las fechas de inicio y fin fijas de ese mes calendario y calcula y devuelve las mismas tres cifras consolidadas.

3. **Escenario**: Balance financiero trimestral del período cerrado más reciente.
   - **Dado** que existen registros de cobro, reembolso y dispersión, así como reservas con depósitos de garantía pendientes de resolución.
   - **Cuando** el Administrador Financiero solicita el balance financiero con periodicidad trimestral y selecciona el período cerrado más reciente ofrecido por el sistema.
   - **Entonces** el sistema calcula internamente las fechas de inicio y fin fijas de ese trimestre calendario y calcula y devuelve las mismas tres cifras consolidadas.

4. **Escenario**: Balance financiero de un período cerrado anterior (no el más reciente).
   - **Dado** que el sistema ofrece una lista acotada de períodos cerrados anteriores de la periodicidad seleccionada.
   - **Cuando** el Administrador Financiero selecciona, dentro de la lista ofrecida, un período distinto al más reciente.
   - **Entonces** el sistema calcula y devuelve las tres cifras consolidadas correspondientes exactamente a ese período seleccionado.

### Casos Extremos (Edge Cases)

- **¿Cómo se determinan exactamente los límites fijos de cada quincena, mes y trimestre?**
  Todos se definen por límites fijos de calendario, sin traslapes ni vacíos entre períodos consecutivos de la misma periodicidad:
  - **Quincenas**: cada uno de los meses del calendario se divide en dos quincenas fijas: la primera desde el día 1 hasta el día 15 del mes, y la segunda desde el día 16 hasta el último día de ese mes.
  - **Meses**: cada mes calendario.
  - **Trimestres**: períodos de tres meses consecutivos a partir de enero: trimestre 1 (enero-marzo), trimestre 2 (abril-junio), trimestre 3 (julio-septiembre) y trimestre 4 (octubre-diciembre).

- **¿Qué sucede con la segunda quincena de febrero en un año bisiesto?**
  Al definirse como "desde el día 16 hasta el último día del mes", la segunda quincena de febrero incluye automáticamente el 29 de febrero en los años bisiestos, sin requerir ninguna regla adicional.

- **¿Qué es un "período cerrado" y cuáles se ofrecen?**
  Un período está cerrado cuando su fecha de fin ya ocurrió; el período en curso, aún vigente, no es ofrecido. El sistema ofrece una lista acotada de los períodos cerrados más recientes de la periodicidad consultada (por ejemplo, los últimos 12 períodos cerrados), identificados por su rango de fechas.

- **¿Qué sucede si el Administrador Financiero selecciona un período que no pertenece a la lista de períodos cerrados ofrecidos (por ejemplo, un período aún en curso o un identificador inválido)?**
  El sistema no calcula ningún balance para una selección que no corresponda a un período cerrado ofrecido. Conforme a RNF-003, responde con un error controlado indicando que la selección no es válida, sin calcular un período inexistente.

- **¿Qué sucede si no existen registros de cobro, reembolso o dispersión, ni reservas con depósitos pendientes, dentro del alcance correspondiente a alguno de los tres componentes?**
  El sistema devuelve el valor cero para el componente correspondiente, sin que esto constituya un error; el balance puede devolverse con uno, dos o los tres componentes en cero.

- **¿Puede el Administrador Financiero consultar el balance de un período antiguo que ya no está dentro de la lista de períodos cerrados ofrecidos?**
  No. La consulta se limita a los períodos cerrados que el sistema ofrece (los más recientes de la periodicidad seleccionada); los períodos más antiguos quedan fuera del alcance de la consulta. No existe una dimensión de año ni un mecanismo de selección libre de fechas que permita acceder a ellos.

- **¿Un depósito de garantía que permanece pendiente de resolución durante varios períodos consecutivos se contabiliza en cada uno de esos períodos?**
  Sí. Al tratarse de una cifra vigente a la fecha de fin de cada período y no de un evento con fecha única, el mismo depósito pendiente puede aparecer contabilizado en varios balances consecutivos (de distintas quincenas, meses o trimestres), mientras la disputa correspondiente no haya sido resuelta.

- **¿Este caso de uso puede ser consultado por el Propietario, como ocurre con "Consultar registros financieros"?**
  No. Según el contexto (sección 3.5 y el diagrama de casos de uso), "Consultar balance financiero" está asociado exclusivamente al Administrador Financiero; el Propietario cuenta con "Consultar ingresos" y "Consultar registros financieros" (este último limitado a sus propios registros) para sus propias necesidades de supervisión.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Administrador Financiero una solicitud de balance financiero, indicando la periodicidad (quincenal, mensual o trimestral) y la selección de uno de los períodos cerrados ofrecidos por el sistema.
- **RF-002**: El sistema DEBE definir los períodos del balance exclusivamente por límites fijos de calendario, sin traslapes ni vacíos entre períodos consecutivos de la misma periodicidad, de acuerdo con las siguientes reglas fijas:
  - **Quincenas**: cada mes del calendario se divide en dos quincenas fijas: la primera (día 1 al día 15) y la segunda (día 16 al último día del mes).
  - **Meses**: cada mes calendario.
  - **Trimestres**: períodos de tres meses consecutivos a partir de enero (trimestre 1: enero-marzo; trimestre 2: abril-junio; trimestre 3: julio-septiembre; trimestre 4: octubre-diciembre).
- **RF-003**: El sistema DEBE ofrecer al Administrador Financiero una lista de los períodos **cerrados** más recientes de la periodicidad seleccionada (por ejemplo, los últimos 12 períodos cerrados), identificados por su rango de fechas; un período se considera cerrado cuando su fecha de fin ya ocurrió. El período en curso no se ofrece.
- **RF-004**: El sistema DEBE calcular los fondos retenidos como la suma de los montos cobrados exitosamente (`RegistroDeCobro`) correspondientes a reservas cuyo ciclo financiero permanezca abierto (sin un `RegistroDeReembolso` y/o `RegistroDeDispersión` exitoso que lo cierre) a la fecha de fin del período seleccionado.
- **RF-005**: El sistema DEBE calcular las comisiones acumuladas como la suma del monto de `comisiónAplicada` de las dispersiones exitosas (`RegistroDeDispersión`) registradas dentro del período seleccionado.
- **RF-006**: El sistema DEBE calcular los depósitos en garantía pendientes de resolución como la suma de los montos de depósito de garantía registrados (`InformaciónDeReserva`) de las reservas en estado "completada con incidentes" que, a la fecha de fin del período seleccionado, no cuenten con un resultado de "Resolver disputa de garantía" registrado.
- **RF-007**: El sistema DEBE devolver al Administrador Financiero el balance consolidado compuesto por los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución, junto con el período consultado (periodicidad y las fechas de inicio y fin fijas del período seleccionado).
- **RF-008**: El sistema DEBE devolver el valor cero para cualquiera de los tres componentes del balance cuando no existan registros dentro del alcance correspondiente, sin generar un error.
- **RF-009**: El sistema DEBE responder con un error controlado cuando la periodicidad indicada no sea quincenal, mensual o trimestral, o cuando el período seleccionado no sea un período cerrado ofrecido por el sistema.
- **RF-010**: El sistema DEBE exponer este caso de uso exclusivamente al Administrador Financiero.
- **RF-011**: El sistema NO DEBE crear, modificar ni eliminar ningún `RegistroDeCobro`, `RegistroDeReembolso`, `RegistroDeDispersión` o `InformaciónDeReserva` como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Administrador Financiero, tanto para recibir la solicitud de balance (periodicidad y selección del período cerrado ofrecido) como para devolver el resultado consolidado.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante una selección de período inválida (periodicidad no reconocida o período no ofrecido/no cerrado), evitando calcular un balance para un período inexistente.
- **RNF-004**: El sistema DEBE utilizar la fecha de registro de cada `RegistroDeCobro`, `RegistroDeReembolso` y `RegistroDeDispersión`, así como el estado vigente de cada `InformaciónDeReserva`, para determinar su inclusión dentro del período seleccionado o a la fecha de fin de dicho período, según corresponda a cada componente del balance (RF-004 a RF-006).

### Entidades Clave

- **RegistroDeCobro (Entidad, definida en SPEC 5)**: En este caso de uso es únicamente consultada, para calcular los fondos retenidos.
- **RegistroDeReembolso (Entidad, definida en SPEC 9)**: En este caso de uso es únicamente consultada, para determinar si el ciclo financiero de una reserva ya fue cerrado.
- **RegistroDeDispersión (Entidad, definida en SPEC 10, actualizada en v2)**: En este caso de uso es únicamente consultada, tanto para determinar si el ciclo financiero de una reserva ya fue cerrado (fondos retenidos) como para sumar el monto de `comisiónAplicada` (comisiones acumuladas).
- **InformaciónDeReserva (Entidad, definida en SPEC 3, actualizada en SPEC 4, SPEC 7 y SPEC 8)**: En este caso de uso es únicamente consultada, para identificar las reservas en estado "completada con incidentes" y su depósito de garantía registrado, a fin de calcular los depósitos pendientes de resolución.
- **SolicitudBalanceFinanciero (DTO)**: Información recibida desde el Administrador Financiero para esta operación. Contiene la periodicidad (quincenal, mensual o trimestral) y la selección del período cerrado, identificado por su rango de fechas o por su identificador dentro de la lista de períodos ofrecidos (RF-003). No incluye año ni rangos de fechas libres.
- **BalanceFinancieroResultado (DTO)**: Resultado que el sistema devuelve al Administrador Financiero. Contiene el período consultado (periodicidad y las fechas de inicio y fin fijas del período seleccionado), los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución. No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los balances calculados corresponden exactamente a la suma de los registros correspondientes a cada componente (fondos retenidos, comisiones acumuladas, depósitos en garantía pendientes de resolución), con cero (0) discrepancias detectadas en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-002**: Fijeza y Consistencia de Períodos, "100% de las quincenas, meses y trimestres ofrecidos por el sistema coinciden exactamente con los límites fijos de calendario definidos en RF-002 (sin traslapes ni vacíos entre períodos consecutivos de la misma periodicidad), con cero (0) discrepancias detectadas en pruebas automatizadas, incluyendo años bisiestos".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeCobro, RegistroDeReembolso, RegistroDeDispersión o InformaciónDeReserva como resultado de la ejecución de este caso de uso".
- **CE-004**: Exclusividad de Acceso, "0 solicitudes de balance financiero aceptadas por el sistema provenientes de un actor distinto al Administrador Financiero".
- **CE-005**: Resiliencia del Sistema, "100% de las solicitudes con una selección de período inválida (periodicidad no reconocida o período no cerrado/no ofrecido) son respondidas mediante un error controlado, y 100% de las consultas sin registros dentro del alcance de alguno de los tres componentes devuelven el valor cero para dicho componente sin generar un error".
- **CE-006**: Exactitud de Períodos Ofrecidos, "100% de los períodos ofrecidos por el sistema corresponden a períodos cerrados (su fecha de fin ya ocurrió) y son los más recientes de la periodicidad seleccionada, con cero (0) períodos en curso o fuera de la lista ofrecida calculados".