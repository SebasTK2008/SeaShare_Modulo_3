# Especificación de Funcionalidad: UC11 - Configurar Parámetros Financieros Globales

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es ejecutado directamente por el Administrador Financiero para definir o ajustar los parámetros financieros globales que rigen los cálculos del sistema: el **porcentaje de comisión de la plataforma**, la **tarifa del seguro náutico por pasajero**, el **monto fijo del depósito de garantía**, y los **porcentajes de incremento de tarifa dinámica** aplicables a fin de semana y a temporada alta; la **vigencia de la temporada alta** (fecha de inicio y fin) es determinada **automáticamente** por el sistema conforme a la regla de calendario definida en el contexto del Módulo 3 (fin de año, mitad de año, Semana Santa, semana de receso y puentes/fines de semana largos), sin configuración manual de fechas. Los valores aquí configurados son consumidos por "Brindar tarifa base" (SPEC 2), "Solicitar el valor calculado de la reserva" (SPEC 4) y "Liquidar fondos de alquiler" (SPEC 10). Los **umbrales que determinan el tipo de cancelación** (flexible, moderada, tardía/No-Show) **no** forman parte de los parámetros configurados por este caso de uso: dicha clasificación es determinada y gestionada por el Sistema de Reservas y Operaciones (Módulo 2), y el sistema únicamente recibe el resultado ya clasificado (el tipo de cancelación) a través de "Brindar el estado de la reserva" (SPEC 7), sin necesitar ni configurar los umbrales de tiempo que la originan.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Configurar el porcentaje de comisión de la plataforma y la tarifa del seguro náutico por pasajero (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el porcentaje de comisión de la plataforma y/o la tarifa del seguro náutico por pasajero, quiero persistir dichos valores como parámetros financieros globales vigentes, de manera que "Solicitar el valor calculado de la reserva" y "Liquidar fondos de alquiler" puedan utilizarlos en sus cálculos.

**Por qué esta prioridad**: Son la base de la liquidación estándar (Valor Bruto − Comisión − Seguro) utilizada por múltiples casos de uso del sistema; sin estos valores configurados, ningún cálculo financiero definitivo puede completarse.

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, un nuevo porcentaje de comisión de la plataforma y una nueva tarifa de seguro náutico, y validar que ambos quedan persistidos como valores vigentes, reemplazando cualquier valor previamente configurado.

**Escenarios de Aceptación**:

1. **Escenario**: Configuración o ajuste del porcentaje de comisión de la plataforma.
   - **Dado** que el Administrador Financiero determina el porcentaje de comisión que debe regir los cálculos de liquidación.
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

2. **Escenario**: Configuración o ajuste de la tarifa del seguro náutico por pasajero.
   - **Dado** que el Administrador Financiero determina la tarifa de seguro náutico que debe regir los cálculos del valor de reserva.
   - **Cuando** configura dicha tarifa en el sistema.
   - **Entonces** el sistema persiste la nueva tarifa como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

---

### Historia de Usuario 2 - Configurar el monto del depósito de garantía (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el monto fijo del depósito de garantía, quiero persistir dicho valor como parámetro financiero global vigente, de manera que "Solicitar el valor calculado de la reserva" pueda utilizarlo al calcular el desglose de valor de una reserva.

**Por qué esta prioridad**: Sin un depósito de garantía configurado, ninguna reserva puede completar su valor calculado definitivo, ya que este monto es obligatorio en el desglose devuelto por "Solicitar el valor calculado de la reserva".

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, un nuevo monto de depósito de garantía y validar que queda persistido como el valor vigente, reemplazando cualquier monto previamente configurado.

**Escenarios de Aceptación**:

1. **Escenario**: Configuración o ajuste del monto del depósito de garantía.
   - **Dado** que el Administrador Financiero determina el monto fijo del depósito de garantía aplicable a las reservas.
   - **Cuando** configura dicho monto en el sistema.
   - **Entonces** el sistema persiste el nuevo monto como el valor vigente, sobrescribiendo cualquier monto previamente configurado.

---

### Historia de Usuario 3 - Configurar los porcentajes de tarifa dinámica (fin de semana y temporada alta) (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el porcentaje de incremento por fin de semana y el porcentaje de incremento por temporada alta, quiero persistir dichos valores como parámetros financieros globales vigentes, de manera que "Brindar tarifa base" pueda aplicarlos al calcular la tarifa dinámica de una embarcación (la vigencia de la temporada alta es determinada automáticamente por la regla de calendario definida en el contexto del Módulo 3, no se configura en este caso de uso).

**Por qué esta prioridad**: Es la única fuente de los porcentajes que "Brindar tarifa base" necesita para aplicar la tarifa dinámica; sin esta configuración, dicho caso de uso no podría determinar cuánto ajustar la tarifa base de una embarcación.

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, ambos porcentajes de tarifa dinámica y validar que quedan persistidos como los valores vigentes, reemplazando cualquier configuración previa.

**Escenarios de Aceptación**:

1. **Escenario**: Configuración o ajuste del porcentaje de incremento por fin de semana.
   - **Dado** que el Administrador Financiero determina el porcentaje de incremento aplicable a la tarifa base durante los fines de semana.
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

2. **Escenario**: Configuración o ajuste del porcentaje de incremento por temporada alta.
   - **Dado** que el Administrador Financiero determina el porcentaje de incremento aplicable a la tarifa base durante la temporada alta (cuyas ventanas de vigencia son determinadas automáticamente por la regla de calendario).
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Administrador Financiero configura un nuevo valor para un parámetro que ya contaba con un valor previamente vigente (por ejemplo, actualizar el porcentaje de comisión ya configurado)?**
  El sistema sobrescribe el valor previamente vigente con el nuevo valor configurado; este caso de uso no conserva un historial de valores anteriores.

- **¿Qué sucede con las reservas cuyo valor ya fue calculado por "Solicitar el valor calculado de la reserva" antes de que el Administrador Financiero actualice un parámetro financiero global (por ejemplo, el porcentaje de comisión, la tarifa de seguro náutico o el monto del depósito de garantía)?**
  Dado que dichos casos de uso registran internamente, en la información de la reserva, los valores efectivamente utilizados al momento del cálculo, la actualización posterior de un parámetro financiero global no modifica los montos ya registrados para esas reservas; el nuevo valor aplica únicamente a los cálculos que se realicen después de la actualización.

- **¿Cómo se determina la vigencia (fecha de inicio y fin) de la temporada alta?**
  La vigencia no se configura mediante este caso de uso: el sistema la determina automáticamente aplicando la regla de calendario de temporada alta definida en el contexto del Módulo 3 (ventanas de fin de año, mitad de año, Semana Santa, semana de receso y puentes/fines de semana largos). "Brindar tarifa base" (SPEC 2) evalúa cada fecha contra dichas ventanas y, si corresponde, aplica el porcentaje de incremento de temporada alta configurado en este caso de uso.

- **¿Qué sucede si "Brindar tarifa base", "Solicitar el valor calculado de la reserva" o "Liquidar fondos de alquiler" necesitan un parámetro financiero global que aún no ha sido configurado por el Administrador Financiero?**
  Este caso de uso no define dicho tratamiento: cada caso de uso consumidor gestiona por sí mismo la ausencia del parámetro que necesita (por ejemplo, tratándola como información incompleta y registrando o respondiendo con un error controlado, según lo definido en sus propios requisitos).

- **¿Los umbrales que determinan el tipo de cancelación (flexible, moderada, tardía/No-Show) se configuran mediante este caso de uso?**
  No. Dichos umbrales son determinados y gestionados por el Sistema de Reservas y Operaciones (Módulo 2); el sistema únicamente recibe el resultado ya clasificado (el tipo de cancelación) a través de "Brindar el estado de la reserva", sin necesitar ni configurar los umbrales de tiempo que originan dicha clasificación.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de comisión de la plataforma aplicado sobre el monto de alquiler en la liquidación estándar.
- **RF-002**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) la tarifa del seguro náutico por pasajero.
- **RF-003**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el monto fijo del depósito de garantía aplicable a las reservas.
- **RF-004**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de incremento de tarifa dinámica aplicable a los fines de semana.
- **RF-005**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de incremento de tarifa dinámica aplicable a la temporada alta.
- **RF-006**: El sistema DEBE persistir cada parámetro financiero global configurado, sobrescribiendo el valor previamente vigente cuando el Administrador Financiero lo ajuste.
- **RF-007**: El sistema DEBE exponer los parámetros financieros globales vigentes para su consumo por "Brindar tarifa base" (porcentajes de tarifa dinámica), "Solicitar el valor calculado de la reserva" (tarifa de seguro náutico y monto del depósito de garantía) y "Liquidar fondos de alquiler" (porcentaje de comisión de la plataforma).
- **RF-008**: El sistema NO DEBE modificar los valores ya registrados en reservas previamente calculadas cuando se actualice un parámetro financiero global; los nuevos valores configurados aplican únicamente a los cálculos que se realicen después de la actualización.
- **RF-009**: El sistema DEBE exponer este caso de uso exclusivamente al Administrador Financiero.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para recibir del Administrador Financiero la solicitud de configuración, mapeando únicamente los atributos esenciales de cada grupo de parámetros (comisión y seguro; depósito de garantía; porcentajes de tarifas dinámicas).
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el porcentaje de comisión de la plataforma, la tarifa del seguro náutico, el monto del depósito de garantía y los porcentajes de incremento de tarifa dinámica.
- **RNF-003**: El sistema DEBE persistir de forma consistente cada parámetro configurado, de manera que "Brindar tarifa base", "Solicitar el valor calculado de la reserva" y "Liquidar fondos de alquiler" recuperen siempre el valor vigente más reciente al momento de su consulta.

### Entidades Clave

- **ParámetrosFinancierosGlobales (Entidad)**: Estructura única gestionada y persistida internamente por el sistema para representar la configuración financiera vigente de la plataforma. Contiene el porcentaje de comisión de la plataforma, la tarifa del seguro náutico por pasajero, el monto fijo del depósito de garantía, el porcentaje de incremento de tarifa dinámica por fin de semana, el porcentaje de incremento de tarifa dinámica por temporada alta. Es creada y actualizada exclusivamente por este caso de uso, y consultada por "Brindar tarifa base", "Solicitar el valor calculado de la reserva" y "Liquidar fondos de alquiler".
- **SolicitudConfiguraciónComisiónYSeguro (DTO)**: Información recibida desde el Administrador Financiero para la Historia de Usuario 1. Contiene el porcentaje de comisión de la plataforma y/o la tarifa del seguro náutico por pasajero. No se persiste tal cual; sus datos se utilizan para actualizar la entidad ParámetrosFinancierosGlobales.
- **SolicitudConfiguraciónDepósitoGarantía (DTO)**: Información recibida desde el Administrador Financiero para la Historia de Usuario 2. Contiene el monto fijo del depósito de garantía. No se persiste tal cual; sus datos se utilizan para actualizar la entidad ParámetrosFinancierosGlobales.
- **SolicitudConfiguraciónTarifaDinámica (DTO)**: Información recibida desde el Administrador Financiero para la Historia de Usuario 3. Contiene el porcentaje de incremento por fin de semana y el porcentaje de incremento por temporada alta. No se persiste tal cual; sus datos se utilizan para actualizar la entidad ParámetrosFinancierosGlobales.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Consistencia de Configuración, "100% de los parámetros financieros globales configurados por el Administrador Financiero quedan persistidos y disponibles para 'Brindar tarifa base', 'Solicitar el valor calculado de la reserva' y 'Liquidar fondos de alquiler', con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "100% de las actualizaciones de un parámetro financiero global sobrescriben correctamente el valor previamente vigente, sin afectar los valores ya registrados en reservas previamente calculadas".
- **CE-003**: Exclusividad de Acceso, "0 solicitudes de configuración de parámetros financieros globales aceptadas por el sistema provenientes de un actor distinto al Administrador Financiero, confirmando que la exposición de este caso de uso es exclusiva".