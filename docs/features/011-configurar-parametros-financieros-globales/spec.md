# Especificación de Funcionalidad: UC11 - Configurar Parámetros Financieros Globales

**Creado**: 2026-09-06

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

### Historia de Usuario 2 - Configurar los porcentajes de tarifa dinámica (fin de semana y temporada alta) (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el porcentaje de incremento por fin de semana y el porcentaje de incremento por temporada alta, quiero persistir dichos valores como parámetros financieros globales vigentes, de manera que "Brindar tarifa base" pueda aplicarlos al calcular la tarifa dinámica de una embarcación (la vigencia de la temporada alta es determinada automáticamente por la regla de calendario de temporada alta definida en "Brindar tarifa base", SPEC 2 RF-006, no se configura en este caso de uso).

**Por qué esta prioridad**: Es la única fuente de los porcentajes que "Brindar tarifa base" necesita para aplicar la tarifa dinámica; sin esta configuración, dicho caso de uso no podría determinar cuánto ajustar la tarifa base de una embarcación.

**Prueba Independiente**: Enviar al sistema, como Administrador Financiero, ambos porcentajes de tarifa dinámica y validar que quedan persistidos como los valores vigentes, reemplazando cualquier configuración previa.

**Escenarios de Aceptación**:

1. **Escenario**: Configuración o ajuste del porcentaje de incremento por fin de semana.
   - **Dado** que el Administrador Financiero determina el porcentaje de incremento aplicable a la tarifa base durante los fines de semana y puentes festivos.
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

2. **Escenario**: Configuración o ajuste del porcentaje de incremento por temporada alta.
   - **Dado** que el Administrador Financiero determina el porcentaje de incremento aplicable a la tarifa base durante la temporada alta (cuyas ventanas de vigencia son determinadas automáticamente por la regla de calendario).
   - **Cuando** configura dicho porcentaje en el sistema.
   - **Entonces** el sistema persiste el nuevo porcentaje como el valor vigente, sobrescribiendo cualquier valor previamente configurado.

---

### Historia de Usuario 3 - Visualizar los parámetros financieros globales vigentes (Prioridad: P1)

Como el sistema, al recibir una solicitud del Administrador Financiero para consultar la configuración financiera, quiero devolver los parámetros globales vigentes y los valores calculados de solo lectura, de manera que la pantalla de configuración pueda mostrar el estado actual antes de editarlo.

**Por qué esta prioridad**: El Administrador Financiero debe conocer los valores vigentes antes de modificarlos. La pantalla también muestra el depósito de garantía y las ventanas de temporada alta, pero esos datos son derivados y no pueden editarse desde este caso de uso.

**Prueba Independiente**: Persistir una configuración financiera global, solicitar su visualización como Administrador Financiero y validar que el sistema devuelve los cuatro parámetros configurables, el depósito calculado como 10% de la tarifa base diaria y las ventanas de temporada alta calculadas por calendario.

**Escenarios de Aceptación**:

1. **Escenario**: Carga de la configuración vigente.
  - **Dado** que existe una entidad `ParámetrosFinancierosGlobales` vigente.
  - **Cuando** el Administrador Financiero abre la pantalla de configuración.
  - **Entonces** el sistema devuelve en un único DTO los porcentajes de comisión, seguro, fin de semana y temporada alta, junto con el depósito y el calendario como datos de solo lectura.

2. **Escenario**: Carga sin configuración previa.
  - **Dado** que todavía no existe una configuración financiera global.
  - **Cuando** el Administrador Financiero abre la pantalla de configuración.
  - **Entonces** el sistema informa que la configuración está pendiente y no inventa valores para ningún parámetro.

### Historia de Usuario 4 - Guardar o cancelar la configuración financiera global (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero una configuración financiera global completa, quiero validar y persistir todos los valores como una única actualización, de manera que la pantalla no deje parámetros parcialmente modificados.

**Por qué esta prioridad**: Los cuatro parámetros configurables se muestran y editan en una misma pantalla. Guardarlos de forma atómica evita que los cálculos posteriores utilicen una combinación parcial de valores nuevos y antiguos.

**Prueba Independiente**: Cargar una configuración vigente, modificar los cuatro parámetros, guardarla correctamente y validar que todos quedan actualizados juntos; después modificar los campos y cancelar, validando que la entidad conserva los valores anteriores.

**Escenarios de Aceptación**:

1. **Escenario**: Guardado completo y exitoso.
  - **Dado** que el Administrador Financiero ha editado valores válidos de comisión, seguro, incremento de fin de semana e incremento de temporada alta.
  - **Cuando** selecciona "Guardar".
  - **Entonces** el sistema valida y persiste los cuatro valores en la entidad lógica `ParámetrosFinancierosGlobales`, reemplazando la configuración vigente como una única operación atómica.

2. **Escenario**: Cancelación de cambios no guardados.
  - **Dado** que el Administrador Financiero modificó uno o más campos en la pantalla sin guardar.
  - **Cuando** selecciona "Cancelar".
  - **Entonces** el sistema descarta los cambios no persistidos y conserva la configuración vigente sin modificarla.

3. **Escenario**: Fallo durante el guardado.
  - **Dado** que los valores superan la validación, la persistencia falla o la actualización no puede completarse.
  - **Cuando** el Administrador Financiero selecciona "Guardar".
  - **Entonces** el sistema no actualiza ningún parámetro, informa un error controlado y conserva íntegramente la configuración anterior.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Administrador Financiero configura un nuevo valor para un parámetro que ya contaba con un valor previamente vigente (por ejemplo, actualizar el porcentaje de comisión ya configurado)?**
  El sistema sobrescribe los cuatro valores de la entidad lógica con la configuración completa recibida; este caso de uso no conserva un historial de valores anteriores.

- **¿Qué sucede si falla el guardado de uno de los parámetros?**
  La actualización es atómica: el sistema no persiste ninguno de los cuatro valores nuevos y conserva la configuración anterior completa.

- **¿Qué sucede con las reservas cuyo valor ya fue calculado por "Solicitar el valor calculado de la reserva" antes de que el Administrador Financiero actualice un parámetro financiero global?**
  Los valores ya registrados en la reserva, incluida la tarifa base usada y el depósito, no se modifican; los nuevos parámetros aplican únicamente a cálculos posteriores.

- **¿Cómo se determina la vigencia (fecha de inicio y fin) de la temporada alta?**
  La vigencia no se configura mediante este caso de uso: el sistema la determina automáticamente aplicando la regla de calendario de temporada alta definida en "Brindar tarifa base" (SPEC 2, RF-006, ventanas de fin de año, mitad de año, Semana Santa y semana de receso). "Brindar tarifa base" (SPEC 2) evalúa cada fecha contra dichas ventanas y, si corresponde, aplica el porcentaje de incremento de temporada alta configurado en este caso de uso.

- **¿Qué sucede si "Brindar tarifa base", "Solicitar el valor calculado de la reserva" o "Liquidar fondos de alquiler" necesitan un parámetro financiero global que aún no ha sido configurado por el Administrador Financiero?**
  Este caso de uso no define dicho tratamiento: cada caso de uso consumidor gestiona por sí mismo la ausencia del parámetro que necesita (por ejemplo, tratándola como información incompleta y registrando o respondiendo con un error controlado, según lo definido en sus propios requisitos).

- **¿Los umbrales que determinan el tipo de cancelación (flexible, moderada o tardía) se configuran mediante este caso de uso?**
  No. Dichos umbrales son determinados y gestionados por el Sistema de Reservas y Operaciones; el sistema únicamente recibe el resultado ya clasificado (el tipo de cancelación) a través de "Brindar el estado de la reserva", sin necesitar ni configurar los umbrales de tiempo que originan dicha clasificación.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de comisión de la plataforma aplicado sobre el monto de alquiler en la liquidación estándar.
- **RF-002**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) la tarifa del seguro náutico por pasajero.
- **RF-003**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de incremento de tarifa dinámica aplicable a los fines de semana y puentes festivos.
- **RF-004**: El sistema DEBE permitir al Administrador Financiero configurar (definir o ajustar) el porcentaje de incremento de tarifa dinámica aplicable a la temporada alta.
- **RF-005**: El sistema DEBE persistir los cuatro parámetros configurables como una única actualización de la entidad lógica `ParámetrosFinancierosGlobales`, sobrescribiendo la configuración previamente vigente solo cuando todos los valores hayan sido validados correctamente.
- **RF-006**: El sistema DEBE exponer los parámetros financieros globales vigentes para su consumo por "Brindar tarifa base" (porcentajes de tarifa dinámica), "Solicitar estimación para reserva" y "Solicitar el valor calculado de la reserva" (tarifa de seguro náutico) y "Liquidar fondos de alquiler" (porcentaje de comisión de la plataforma). El depósito no es configurable y se calcula conforme a la regla definida en "Solicitar el valor calculado de la reserva".
- **RF-007**: El sistema NO DEBE modificar los valores ya registrados en reservas previamente calculadas cuando se actualice un parámetro financiero global; los nuevos valores configurados aplican únicamente a los cálculos que se realicen después de la actualización. Los parámetros no tienen historial y su efecto no es retroactivo; los valores ya congelados en reservas calculadas se conservan inalterados.
- **RF-008**: El sistema DEBE exponer este caso de uso exclusivamente al Administrador Financiero.
- **RF-009**: El sistema DEBE devolver en una única respuesta los parámetros configurables vigentes y los datos derivados de solo lectura necesarios para la pantalla de configuración.
- **RF-010**: El sistema DEBE descartar cualquier cambio no persistido cuando el Administrador Financiero cancele la edición.
- **RF-011**: El sistema DEBE validar todos los valores antes de actualizar la entidad lógica y NO DEBE aplicar actualizaciones parciales.
- **RF-012**: El sistema DEBE permitir editar únicamente el porcentaje de comisión, la tarifa del seguro por pasajero y los porcentajes de incremento de fin de semana y temporada alta. El depósito de garantía y las ventanas de temporada alta DEBEN mostrarse como datos calculados de solo lectura.
- **RF-013**: El sistema DEBE validar que el porcentaje de comisión y ambos porcentajes de incremento sean valores entre 0% y 100%, que la tarifa del seguro sea un valor monetario mayor o igual a cero y que ningún campo obligatorio esté vacío, sea no numérico o tenga un formato monetario inválido. Ante una validación fallida, DEBE responder con un error controlado sin modificar la configuración vigente.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO general para cargar y guardar la configuración financiera global, mapeando los cuatro parámetros configurables. Los DTOs internos de cada grupo pueden utilizarse como detalle de implementación, pero no representan entidades ni contratos de persistencia separados.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el porcentaje de comisión de la plataforma, la tarifa del seguro náutico y los porcentajes de incremento de tarifa dinámica, garantizando una precisión interna de 4 decimales antes de cualquier redondeo final hacia la pasarela o reportes.
- **RNF-003**: El sistema DEBE persistir de forma atómica la configuración completa, de manera que "Brindar tarifa base", "Solicitar el valor calculado de la reserva" y "Liquidar fondos de alquiler" recuperen siempre una única versión coherente de los valores vigentes.

### Entidades Clave

- **ParámetrosFinancierosGlobales (Entidad)**: Estructura única gestionada y persistida internamente por el sistema para representar la configuración financiera vigente de la plataforma. Contiene el porcentaje de comisión, la tarifa del seguro por pasajero y los porcentajes de tarifa dinámica. Es creada y actualizada exclusivamente por este caso de uso, y consultada por "Brindar tarifa base", "Solicitar estimación para reserva", "Solicitar el valor calculado de la reserva" y "Liquidar fondos de alquiler".
- **SolicitudParámetrosFinancierosGlobales (DTO)**: Información recibida desde el Administrador Financiero para guardar la configuración. Contiene el porcentaje de comisión, la tarifa de seguro por pasajero, el incremento de fin de semana y el incremento de temporada alta. No se persiste tal cual; sus datos se validan y se utilizan para actualizar atómicamente la entidad `ParámetrosFinancierosGlobales`.
- **ParámetrosFinancierosGlobalesResultado (DTO)**: Información devuelta al cargar la pantalla. Contiene los cuatro parámetros configurables vigentes, el depósito calculado como 10% de la tarifa base diaria y las ventanas de temporada alta calculadas. El depósito y las ventanas son de solo lectura.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Consistencia de Configuración, "100% de las configuraciones globales guardadas por el Administrador Financiero persisten sus cuatro parámetros como una única versión coherente y quedan disponibles para 'Brindar tarifa base', 'Solicitar el valor calculado de la reserva' y 'Liquidar fondos de alquiler', con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "100% de las actualizaciones completas sobrescriben correctamente la configuración previamente vigente, sin actualizaciones parciales ni afectación de los valores ya registrados en reservas previamente calculadas".
- **CE-003**: Exclusividad de Acceso, "0 solicitudes de configuración de parámetros financieros globales aceptadas por el sistema provenientes de un actor distinto al Administrador Financiero, confirmando que la exposición de este caso de uso es exclusiva".
- **CE-004**: Integridad de Edición, "100% de las cancelaciones descartan los cambios no guardados y 100% de los guardados con datos inválidos conservan intacta la configuración anterior".