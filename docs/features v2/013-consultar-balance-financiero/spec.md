# Especificación de Funcionalidad: UC13 - Consultar Balance Financiero

**Creado**: 2026-09-06 (v2 — ajustado para que el período del balance no sea un rango de fechas libre indicado por el Administrador Financiero, sino uno de un conjunto fijo de quincenas, meses o trimestres de calendario, definidos desde el primer día del año hasta el último. El Administrador Financiero selecciona la periodicidad y el número de período dentro de esa periodicidad y ese año; el sistema calcula internamente las fechas de inicio y fin fijas correspondientes.)

> **Nota de trazabilidad**: Este caso de uso es invocado directamente por el Administrador Financiero para consultar, de solo lectura, el estado consolidado de las finanzas de la plataforma dentro de un período fijo de calendario (quincenal, mensual o trimestral). El balance está compuesto por tres cifras: **fondos retenidos**, **comisiones acumuladas** y **depósitos en garantía pendientes de resolución**. A diferencia de un rango de fechas arbitrario, las quincenas, meses y trimestres están fijados de antemano, contados desde el primer día del año (1 de enero) hasta el último (31 de diciembre): el Administrador Financiero únicamente indica el año, la periodicidad y el número del período dentro de dicha periodicidad (por ejemplo, "quincena 6 de 2026", "mes 3 de 2026" o "trimestre 2 de 2026"), y el sistema calcula las fechas de inicio y fin fijas que le corresponden. Este caso de uso consulta, sin modificarlos, los registros ya definidos por "Procesar cobro" (SPEC 5), "Reembolsar dinero a arrendatario" (SPEC 9) y "Dispersar fondos de alquiler" (SPEC 10) —incluyendo, a partir de la versión 2 de esta última, el monto de comisión efectivamente aplicado (`comisiónAplicada`)—, así como la información de reserva registrada por "Brindar información de reserva" (SPEC 3), "Solicitar el valor calculado de la reserva" (SPEC 4), "Brindar el estado de la reserva" (SPEC 7) y "Resolver disputa de garantía" (SPEC 8). A diferencia de "Consultar registros financieros" (SPEC 12), este caso de uso no expone el detalle transacción por transacción, sino tres cifras consolidadas, y se expone exclusivamente al Administrador Financiero (no al Propietario).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el balance financiero consolidado de la plataforma para un período fijo de calendario (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero una solicitud de balance financiero indicando un año, una periodicidad (quincenal, mensual o trimestral) y el número de período correspondiente dentro de esa periodicidad, quiero calcular internamente las fechas de inicio y fin fijas de ese período de calendario, y a partir de ellas calcular los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución, de manera que el Administrador Financiero pueda supervisar el estado consolidado de las finanzas de la plataforma para ese período fijo, sin necesidad de definir manualmente un rango de fechas.

**Por qué esta prioridad**: Es el mecanismo con el que el Administrador Financiero supervisa, de forma consolidada y periódica, la salud financiera de la plataforma (cuánto dinero permanece retenido, cuánto ha generado la plataforma en comisiones y cuánto depósito de garantía sigue en disputa). Fijar quincenas, meses y trimestres de calendario, en vez de permitir rangos de fechas arbitrarios, garantiza que los balances de distintos períodos sean comparables entre sí y no se traslapen ni dejen vacíos entre un balance y el siguiente.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión previamente creados, y con reservas en distintos estados (incluyendo "completada con incidentes" sin disputa resuelta), enviar al sistema una solicitud de balance financiero indicando un año, una periodicidad y un número de período, y validar que el sistema calcula correctamente las fechas de inicio y fin fijas de ese período y devuelve las tres cifras consolidadas correspondientes.

**Escenarios de Aceptación**:

1. **Escenario**: Balance financiero para una quincena fija.
   - **Dado** que existen registros de cobro, reembolso y dispersión, así como reservas con depósitos de garantía pendientes de resolución.
   - **Cuando** el Administrador Financiero solicita el balance financiero indicando un año y el número de una quincena (por ejemplo, la quincena 6, correspondiente al 16 al 31 de marzo de ese año).
   - **Entonces** el sistema calcula internamente las fechas de inicio y fin fijas de esa quincena, y calcula y devuelve los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución correspondientes a dicho período.

2. **Escenario**: Balance financiero para un mes fijo.
   - **Dado** que existen registros de cobro, reembolso y dispersión, así como reservas con depósitos de garantía pendientes de resolución.
   - **Cuando** el Administrador Financiero solicita el balance financiero indicando un año y el número de un mes (por ejemplo, el mes 3, correspondiente a marzo de ese año).
   - **Entonces** el sistema calcula internamente las fechas de inicio y fin fijas de ese mes calendario, y calcula y devuelve las mismas tres cifras consolidadas.

3. **Escenario**: Balance financiero para un trimestre fijo.
   - **Dado** que existen registros de cobro, reembolso y dispersión, así como reservas con depósitos de garantía pendientes de resolución.
   - **Cuando** el Administrador Financiero solicita el balance financiero indicando un año y el número de un trimestre (por ejemplo, el trimestre 1, correspondiente a enero-marzo de ese año).
   - **Entonces** el sistema calcula internamente las fechas de inicio y fin fijas de ese trimestre calendario, y calcula y devuelve las mismas tres cifras consolidadas.

### Casos Extremos (Edge Cases)

- **¿Cómo se determinan exactamente los límites fijos de cada quincena, mes y trimestre?**
  Todos se cuentan desde el primer día del año (1 de enero) hasta el último (31 de diciembre), sin traslapes ni vacíos entre períodos consecutivos:
  - **Quincenas**: cada uno de los 12 meses del año se divide en dos quincenas fijas: la primera desde el día 1 hasta el día 15 del mes, y la segunda desde el día 16 hasta el último día de ese mes; esto resulta en 24 quincenas fijas por año.
  - **Meses**: los 12 meses calendario del año, de enero a diciembre.
  - **Trimestres**: 4 trimestres fijos por año, agrupando meses consecutivos a partir de enero: trimestre 1 (enero-marzo), trimestre 2 (abril-junio), trimestre 3 (julio-septiembre) y trimestre 4 (octubre-diciembre).

- **¿Qué sucede con la segunda quincena de febrero en un año bisiesto?**
  Al definirse como "desde el día 16 hasta el último día del mes", la segunda quincena de febrero incluye automáticamente el 29 de febrero en los años bisiestos, sin requerir ninguna regla adicional.

- **¿Qué sucede si el Administrador Financiero indica un número de período fuera de rango para la periodicidad seleccionada (por ejemplo, quincena 25, mes 13 o trimestre 5), o un año inválido?**
  El sistema no calcula ningún período con un número fuera de rango; conforme a RNF-003, responde con un error controlado indicando que el número de período o el año indicado no es válido.

- **¿Qué sucede si no existen registros de cobro, reembolso o dispersión, ni reservas con depósitos pendientes, dentro del alcance correspondiente a alguno de los tres componentes?**
  El sistema devuelve el valor cero para el componente correspondiente, sin que esto constituya un error; el balance puede devolverse con uno, dos o los tres componentes en cero.

- **¿Puede el Administrador Financiero consultar el balance de un período de un año anterior?**
  Sí. El cálculo de los límites fijos de quincena, mes o trimestre aplica de la misma manera para cualquier año indicado; no existe una restricción que limite la consulta al año en curso.

- **¿Un depósito de garantía que permanece pendiente de resolución durante varios períodos consecutivos se contabiliza en cada uno de esos períodos?**
  Sí. Al tratarse de una cifra vigente a la fecha de fin de cada período y no de un evento con fecha única, el mismo depósito pendiente puede aparecer contabilizado en varios balances consecutivos (de distintas quincenas, meses o trimestres), mientras la disputa correspondiente no haya sido resuelta.

- **¿Este caso de uso puede ser consultado por el Propietario, como ocurre con "Consultar registros financieros"?**
  No. Según el contexto (sección 3.5 y el diagrama de casos de uso), "Consultar balance financiero" está asociado exclusivamente al Administrador Financiero; el Propietario cuenta con "Consultar ingresos" y "Consultar registros financieros" (este último limitado a sus propias embarcaciones) para sus propias necesidades de supervisión.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Administrador Financiero una solicitud de balance financiero, indicando el año, la periodicidad (quincenal, mensual o trimestral) y el número de período correspondiente dentro de esa periodicidad y ese año.
- **RF-002**: El sistema DEBE calcular internamente la fecha de inicio y la fecha de fin fijas del período solicitado, contadas desde el primer día del año (1 de enero) hasta el último día del año (31 de diciembre), de acuerdo con las siguientes reglas fijas:
  - **Quincenas**: 24 quincenas fijas por año, dividiendo cada uno de los 12 meses en una primera quincena (día 1 al día 15) y una segunda quincena (día 16 al último día del mes).
  - **Meses**: los 12 meses calendario del año.
  - **Trimestres**: 4 trimestres fijos por año, agrupando meses consecutivos a partir de enero (trimestre 1: enero-marzo; trimestre 2: abril-junio; trimestre 3: julio-septiembre; trimestre 4: octubre-diciembre).
- **RF-003**: El sistema DEBE calcular los fondos retenidos como la suma de los montos cobrados exitosamente (`RegistroDeCobro`) correspondientes a reservas cuyo ciclo financiero permanezca abierto (sin un `RegistroDeReembolso` y/o `RegistroDeDispersión` exitoso que lo cierre) a la fecha de fin del período calculado en RF-002.
- **RF-004**: El sistema DEBE calcular las comisiones acumuladas como la suma del monto de `comisiónAplicada` de las dispersiones exitosas (`RegistroDeDispersión`) registradas dentro del período calculado en RF-002.
- **RF-005**: El sistema DEBE calcular los depósitos en garantía pendientes de resolución como la suma de los montos de depósito de garantía registrados (`InformaciónDeReserva`) de las reservas en estado "completada con incidentes" que, a la fecha de fin del período calculado en RF-002, no cuenten con un resultado de "Resolver disputa de garantía" registrado.
- **RF-006**: El sistema DEBE devolver al Administrador Financiero el balance consolidado compuesto por los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución, junto con el período fijo consultado (año, periodicidad, número de período y las fechas de inicio y fin calculadas).
- **RF-007**: El sistema DEBE devolver el valor cero para cualquiera de los tres componentes del balance cuando no existan registros dentro del alcance correspondiente, sin generar un error.
- **RF-008**: El sistema DEBE responder con un error controlado cuando el número de período indicado esté fuera de rango para la periodicidad seleccionada (quincena fuera de 1 a 24, mes fuera de 1 a 12, trimestre fuera de 1 a 4), o cuando el año indicado sea inválido.
- **RF-009**: El sistema DEBE exponer este caso de uso exclusivamente al Administrador Financiero.
- **RF-010**: El sistema NO DEBE crear, modificar ni eliminar ningún `RegistroDeCobro`, `RegistroDeReembolso`, `RegistroDeDispersión` o `InformaciónDeReserva` como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Administrador Financiero, tanto para recibir la solicitud de balance (año, periodicidad y número de período) como para devolver el resultado consolidado.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante un número de período fuera de rango o un año inválido, evitando calcular un período inexistente.
- **RNF-004**: El sistema DEBE utilizar la fecha de registro de cada `RegistroDeCobro`, `RegistroDeReembolso` y `RegistroDeDispersión`, así como el estado vigente de cada `InformaciónDeReserva`, para determinar su inclusión dentro del período fijo calculado o a la fecha de fin de dicho período, según corresponda a cada componente del balance (RF-003 a RF-005).

### Entidades Clave

- **RegistroDeCobro (Entidad, definida en SPEC 5)**: En este caso de uso es únicamente consultada, para calcular los fondos retenidos.
- **RegistroDeReembolso (Entidad, definida en SPEC 9)**: En este caso de uso es únicamente consultada, para determinar si el ciclo financiero de una reserva ya fue cerrado.
- **RegistroDeDispersión (Entidad, definida en SPEC 10, actualizada en v2)**: En este caso de uso es únicamente consultada, tanto para determinar si el ciclo financiero de una reserva ya fue cerrado (fondos retenidos) como para sumar el monto de `comisiónAplicada` (comisiones acumuladas).
- **InformaciónDeReserva (Entidad, definida en SPEC 3, actualizada en SPEC 4, SPEC 7 y SPEC 8)**: En este caso de uso es únicamente consultada, para identificar las reservas en estado "completada con incidentes" y su depósito de garantía registrado, a fin de calcular los depósitos pendientes de resolución.
- **SolicitudBalanceFinanciero (DTO)**: Información recibida desde el Administrador Financiero para esta operación. Contiene el año, la periodicidad (quincenal, mensual o trimestral) y el número de período dentro de esa periodicidad. No incluye fechas: estas son calculadas internamente por el sistema (RF-002).
- **BalanceFinancieroResultado (DTO)**: Resultado que el sistema devuelve al Administrador Financiero. Contiene el período consultado (año, periodicidad, número de período, y las fechas de inicio y fin fijas calculadas por el sistema), los fondos retenidos, las comisiones acumuladas y los depósitos en garantía pendientes de resolución. No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los balances calculados corresponden exactamente a la suma de los registros correspondientes a cada componente (fondos retenidos, comisiones acumuladas, depósitos en garantía pendientes de resolución), con cero (0) discrepancias detectadas en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-002**: Fijeza y Consistencia de Períodos, "100% de las quincenas, meses y trimestres calculados por el sistema coinciden exactamente con los límites fijos de calendario definidos en RF-002 (sin traslapes ni vacíos entre períodos consecutivos de la misma periodicidad), con cero (0) discrepancias detectadas en pruebas automatizadas, incluyendo años bisiestos".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeCobro, RegistroDeReembolso, RegistroDeDispersión o InformaciónDeReserva como resultado de la ejecución de este caso de uso".
- **CE-004**: Exclusividad de Acceso, "0 solicitudes de balance financiero aceptadas por el sistema provenientes de un actor distinto al Administrador Financiero".
- **CE-005**: Resiliencia del Sistema, "100% de las solicitudes con un número de período fuera de rango o un año inválido son respondidas mediante un error controlado, y 100% de las consultas sin registros dentro del alcance de alguno de los tres componentes devuelven el valor cero para dicho componente sin generar un error".