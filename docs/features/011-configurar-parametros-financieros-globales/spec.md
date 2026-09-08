# Especificaci├│n de Funcionalidad: UC11 - Configurar Par├ímetros Financieros Globales

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es ejecutado directamente por el Administrador Financiero para definir o ajustar los par├ímetros financieros globales que rigen los c├ílculos del sistema: el **porcentaje de comisi├│n de la plataforma**, la **tarifa del seguro n├íutico por pasajero**, el **monto fijo del dep├│sito de garant├¡a**, y los **porcentajes de incremento de tarifa din├ímica** aplicables a fin de semana y a temporada alta, junto con la **vigencia (fecha de inicio y fin)** de esta ├║ltima. Los valores aqu├¡ configurados son consumidos por "Brindar tarifa base" (SPEC 2), "Solicitar el valor calculado de la reserva" (SPEC 4) y "Dispersar fondos de alquiler" (SPEC 10). Los **umbrales que determinan el tipo de cancelaci├│n** (flexible, moderada, tard├¡a/No-Show) **no** forman parte de los par├ímetros configurados por este caso de uso: dicha clasificaci├│n es determinada y gestionada por el Sistema de Reservas y Operaciones (M├│dulo 2), y el sistema ├║nicamente recibe el resultado ya clasificado (el tipo de cancelaci├│n) a trav├®s de "Brindar el estado de la reserva" (SPEC 7), sin necesitar ni configurar los umbrales de tiempo que la originan.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Configurar el porcentaje de comisi├│n de la plataforma y la tarifa del seguro n├íutico por pasajero (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el porcentaje de comisi├│n de la plataforma y/o la tarifa del seguro n├íutico por pasajero, quiero persistir dichos valores como par├ímetros financieros globales vigentes, de manera que "Solicitar el valor calculado de la reserva" y "Dispersar fondos de alquiler" puedan utilizarlos en sus c├ílculos.

**Por qu├® esta prioridad**: Son la base de la liquidaci├│n est├índar (Valor Bruto ÔêÆ Comisi├│n ÔêÆ Seguro) utilizada por m├║ltiples casos de uso del sistema; sin estos valores configurados, ning├║n c├ílculo financiero definitivo puede completarse.

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, un nuevo porcentaje de comisi├│n de la plataforma y una nueva tarifa de seguro n├íutico, y validar que ambos quedan persistidos como valores vigentes, reemplazando cualquier valor previamente configurado.

**Escenarios de Aceptaci├│n**:

1. **Escenario**: Configuraci├│n o ajuste del porcentaje de comisi├│n de la plataforma.
   - **Dado** que el Administrador Financiero determina el porcentaje de comisi├│n que debe regir los c├ílculos de liquidaci├│n.
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

2. **Escenario**: Configuraci├│n o ajuste de la tarifa del seguro n├íutico por pasajero.
   - **Dado** que el Administrador Financiero determina la tarifa de seguro n├íutico que debe regir los c├ílculos del valor de reserva.
   - **Cuando** configura dicha tarifa en el sistema.
   - **Entonces** el sistema persiste la nueva tarifa como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

---

### Historia de Usuario 2 - Configurar el monto del dep├│sito de garant├¡a (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el monto fijo del dep├│sito de garant├¡a, quiero persistir dicho valor como par├ímetro financiero global vigente, de manera que "Solicitar el valor calculado de la reserva" pueda utilizarlo al calcular el desglose de valor de una reserva.

**Por qu├® esta prioridad**: Sin un dep├│sito de garant├¡a configurado, ninguna reserva puede completar su valor calculado definitivo, ya que este monto es obligatorio en el desglose devuelto por "Solicitar el valor calculado de la reserva".

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, un nuevo monto de dep├│sito de garant├¡a y validar que queda persistido como el valor vigente, reemplazando cualquier monto previamente configurado.

**Escenarios de Aceptaci├│n**:

1. **Escenario**: Configuraci├│n o ajuste del monto del dep├│sito de garant├¡a.
   - **Dado** que el Administrador Financiero determina el monto fijo del dep├│sito de garant├¡a aplicable a las reservas.
   - **Cuando** configura dicho monto en el sistema.
   - **Entonces** el sistema persiste el nuevo monto como el valor vigente, sobrescribiendo cualquier monto previamente configurado.

---

### Historia de Usuario 3 - Configurar los porcentajes de tarifa din├ímica y la vigencia de la temporada alta (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el porcentaje de incremento por fin de semana, el porcentaje de incremento por temporada alta, y las fechas de inicio y fin de vigencia de la temporada alta, quiero persistir dichos valores como par├ímetros financieros globales vigentes, de manera que "Brindar tarifa base" pueda aplicarlos al calcular la tarifa din├ímica de una embarcaci├│n.

**Por qu├® esta prioridad**: Es la ├║nica fuente de los porcentajes y del intervalo de vigencia que "Brindar tarifa base" necesita para aplicar la tarifa din├ímica; sin esta configuraci├│n, dicho caso de uso no podr├¡a determinar cu├índo ni cu├ínto ajustar la tarifa base de una embarcaci├│n.

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, ambos porcentajes de tarifa din├ímica y un rango de fechas para la temporada alta, y validar que quedan persistidos como los valores vigentes, reemplazando cualquier configuraci├│n previa.

**Escenarios de Aceptaci├│n**:

1. **Escenario**: Configuraci├│n o ajuste del porcentaje de incremento por fin de semana.
   - **Dado** que el Administrador Financiero determina el porcentaje de incremento aplicable a la tarifa base durante los fines de semana.
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

2. **Escenario**: Configuraci├│n o ajuste del porcentaje de incremento por temporada alta.
   - **Dado** que el Administrador Financiero determina el porcentaje de incremento aplicable a la tarifa base durante la temporada alta.
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

3. **Escenario**: Configuraci├│n de la vigencia (fecha de inicio y fin) de la temporada alta.
   - **Dado** que el Administrador Financiero determina el periodo en el que estar├í vigente la temporada alta.
   - **Cuando** configura las fechas de inicio y fin correspondientes en el sistema.
   - **Entonces** el sistema persiste dicho rango de fechas como la vigencia vigente de la temporada alta, sobrescribiendo cualquier vigencia previamente configurada.

### Casos Extremos (Edge Cases)

- **┬┐Qu├® sucede si el Administrador Financiero configura un nuevo valor para un par├ímetro que ya contaba con un valor previamente vigente (por ejemplo, actualizar el porcentaje de comisi├│n ya configurado)?**
  El sistema sobrescribe el valor previamente vigente con el nuevo valor configurado; este caso de uso no conserva un historial de valores anteriores.

- **┬┐Qu├® sucede con las reservas cuyo valor ya fue calculado por "Solicitar el valor calculado de la reserva" antes de que el Administrador Financiero actualice un par├ímetro financiero global (por ejemplo, el porcentaje de comisi├│n, la tarifa de seguro n├íutico o el monto del dep├│sito de garant├¡a)?**
  Dado que dichos casos de uso registran internamente, en la informaci├│n de la reserva, los valores efectivamente utilizados al momento del c├ílculo, la actualizaci├│n posterior de un par├ímetro financiero global no modifica los montos ya registrados para esas reservas; el nuevo valor aplica ├║nicamente a los c├ílculos que se realicen despu├®s de la actualizaci├│n.

- **┬┐C├│mo se determina y actualiza la vigencia exacta (fecha de inicio y fin) de la temporada alta?**
  Mediante este caso de uso: el Administrador Financiero configura directamente el rango de fechas vigente para la temporada alta, el cual "Brindar tarifa base" utiliza para determinar si una fecha evaluada corresponde o no a dicha condici├│n.

- **┬┐Qu├® sucede si "Brindar tarifa base", "Solicitar el valor calculado de la reserva" o "Dispersar fondos de alquiler" necesitan un par├ímetro financiero global que a├║n no ha sido configurado por el Administrador Financiero?**
  Este caso de uso no define dicho tratamiento: cada caso de uso consumidor gestiona por s├¡ mismo la ausencia del par├ímetro que necesita (por ejemplo, trat├índola como informaci├│n incompleta y registrando o respondiendo con un error controlado, seg├║n lo definido en sus propios requisitos).

- **┬┐Los umbrales que determinan el tipo de cancelaci├│n (flexible, moderada, tard├¡a/No-Show) se configuran mediante este caso de uso?**
  No. Dichos umbrales son determinados y gestionados por el Sistema de Reservas y Operaciones (M├│dulo 2); el sistema ├║nicamente recibe el resultado ya clasificado (el tipo de cancelaci├│n) a trav├®s de "Brindar el estado de la reserva", sin necesitar ni configurar los umbrales de tiempo que originan dicha clasificaci├│n.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de comisi├│n de la plataforma aplicado sobre el monto de alquiler en la liquidaci├│n est├índar.
- **RF-002**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) la tarifa del seguro n├íutico por pasajero.
- **RF-003**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el monto fijo del dep├│sito de garant├¡a aplicable a las reservas.
- **RF-004**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de incremento de tarifa din├ímica aplicable a los fines de semana.
- **RF-005**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de incremento de tarifa din├ímica aplicable a la temporada alta.
- **RF-006**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) la vigencia (fecha de inicio y fecha de fin) de la temporada alta.
- **RF-007**: El sistema DEBE persistir cada par├ímetro financiero global configurado, sobrescribiendo el valor previamente vigente cuando el Administrador Financiero lo ajuste.
- **RF-008**: El sistema DEBE exponer los par├ímetros financieros globales vigentes para su consumo por "Brindar tarifa base" (porcentajes de tarifa din├ímica y vigencia de la temporada alta), "Solicitar el valor calculado de la reserva" (tarifa de seguro n├íutico y monto del dep├│sito de garant├¡a) y "Dispersar fondos de alquiler" (porcentaje de comisi├│n de la plataforma).
- **RF-009**: El sistema NO DEBE modificar los valores ya registrados en reservas previamente calculadas cuando se actualice un par├ímetro financiero global; los nuevos valores configurados aplican ├║nicamente a los c├ílculos que se realicen despu├®s de la actualizaci├│n.
- **RF-010**: El sistema DEBE exponer este caso de uso exclusivamente al Administrador Financiero.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para recibir del Administrador Financiero la solicitud de configuraci├│n, mapeando ├║nicamente los atributos esenciales de cada grupo de par├ímetros (comisi├│n y seguro; dep├│sito de garant├¡a; tarifas din├ímicas y vigencia de temporada alta).
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el porcentaje de comisi├│n de la plataforma, la tarifa del seguro n├íutico, el monto del dep├│sito de garant├¡a y los porcentajes de incremento de tarifa din├ímica.
- **RNF-003**: El sistema DEBE persistir de forma consistente cada par├ímetro configurado, de manera que "Brindar tarifa base", "Solicitar el valor calculado de la reserva" y "Dispersar fondos de alquiler" recuperen siempre el valor vigente m├ís reciente al momento de su consulta.

### Entidades Clave

- **Par├ímetrosFinancierosGlobales (Entidad)**: Estructura ├║nica gestionada y persistida internamente por el sistema para representar la configuraci├│n financiera vigente de la plataforma. Contiene el porcentaje de comisi├│n de la plataforma, la tarifa del seguro n├íutico por pasajero, el monto fijo del dep├│sito de garant├¡a, el porcentaje de incremento de tarifa din├ímica por fin de semana, el porcentaje de incremento de tarifa din├ímica por temporada alta, y la vigencia (fecha de inicio y fecha de fin) de la temporada alta. Es creada y actualizada exclusivamente por este caso de uso, y consultada por "Brindar tarifa base", "Solicitar el valor calculado de la reserva" y "Dispersar fondos de alquiler".
- **SolicitudConfiguraci├│nComisi├│nYSeguro (DTO)**: Informaci├│n recibida desde el Administrador Financiero para la Historia de Usuario 1. Contiene el porcentaje de comisi├│n de la plataforma y/o la tarifa del seguro n├íutico por pasajero. No se persiste tal cual; sus datos se utilizan para actualizar la entidad Par├ímetrosFinancierosGlobales.
- **SolicitudConfiguraci├│nDep├│sitoGarant├¡a (DTO)**: Informaci├│n recibida desde el Administrador Financiero para la Historia de Usuario 2. Contiene el monto fijo del dep├│sito de garant├¡a. No se persiste tal cual; sus datos se utilizan para actualizar la entidad Par├ímetrosFinancierosGlobales.
- **SolicitudConfiguraci├│nTarifaDin├ímica (DTO)**: Informaci├│n recibida desde el Administrador Financiero para la Historia de Usuario 3. Contiene el porcentaje de incremento por fin de semana, el porcentaje de incremento por temporada alta, y/o la fecha de inicio y fin de vigencia de la temporada alta. No se persiste tal cual; sus datos se utilizan para actualizar la entidad Par├ímetrosFinancierosGlobales.

## Criterios de ├ëxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Consistencia de Configuraci├│n, "100% de los par├ímetros financieros globales configurados por el Administrador Financiero quedan persistidos y disponibles para 'Brindar tarifa base', 'Solicitar el valor calculado de la reserva' y 'Dispersar fondos de alquiler', con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitect├│nico, "100% de las actualizaciones de un par├ímetro financiero global sobrescriben correctamente el valor previamente vigente, sin afectar los valores ya registrados en reservas previamente calculadas".
- **CE-003**: Exclusividad de Acceso, "0 solicitudes de configuraci├│n de par├ímetros financieros globales aceptadas por el sistema provenientes de un actor distinto al Administrador Financiero, confirmando que la exposici├│n de este caso de uso es exclusiva".
