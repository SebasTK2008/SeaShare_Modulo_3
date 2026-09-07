# Especificación de Funcionalidad: UC08 - Resolver Disputa de Garantía

**Creado**: 2026-09-06 (v3 — corregido para que este caso de uso ejecute siempre, en los tres resultados posibles de la disputa, "Dispersar fondos de alquiler" exactamente una vez, consolidando en una única operación la liquidación estándar del valor de alquiler al propietario junto con el monto del depósito de garantía retenido cuando aplique. En la versión v2, "Dispersar fondos de alquiler" solo se ejecutaba en retención total o parcial, y únicamente por el monto del depósito retenido, dejando sin disparador la liquidación estándar del valor de alquiler para una reserva "completada con incidentes". Esta versión resuelve ese vacío)

> **Nota de trazabilidad**: Este caso de uso es ejecutado directamente por el Administrador Financiero cuando, al finalizar la navegación, se detectan posibles daños en la embarcación. El Administrador Financiero evalúa la evidencia y determina el resultado de la disputa sobre el depósito de garantía previamente registrado para esa reserva (registrado por "Solicitar el valor calculado de la reserva", SPEC 4). Dado que una reserva "completada con incidentes" (SPEC 7) no dispara por sí misma ninguna operación financiera, es este caso de uso el único punto que habilita, para dicha reserva, tanto el tratamiento del depósito de garantía como la liquidación del valor de alquiler correspondiente al propietario. Por ello, este caso de uso ejecuta directamente "Dispersar fondos de alquiler" (SPEC 10) **en los tres resultados posibles de la disputa** (liberación total, retención total y retención parcial), indicando en cada caso el monto del depósito de garantía retenido que corresponda (siendo dicho monto igual a cero en el caso de liberación total). Es "Dispersar fondos de alquiler" quien, al ser invocado, calcula la liquidación estándar del valor de alquiler (Valor Bruto menos Comisión de la Plataforma menos Seguro) y le añade el monto del depósito retenido indicado, consolidando ambos conceptos en una única dispersión al propietario. Adicionalmente, según el diagrama de casos de uso del Módulo 3, este caso de uso extiende (`<<extend>>`) hacia "Reembolsar dinero a arrendatario" (SPEC 9) cuando el resultado favorece, total o parcialmente, al arrendatario (liberación total o retención parcial).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Aplicar el resultado de la disputa de garantía y consolidar la liquidación de la reserva (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero el resultado de la evaluación de una disputa de garantía sobre una reserva específica (liberación total, retención total o retención parcial del depósito), quiero recuperar el depósito de garantía previamente registrado para esa reserva, aplicar el tratamiento correspondiente sobre dicho depósito, extendiendo hacia "Reembolsar dinero a arrendatario" cuando el resultado favorezca total o parcialmente al arrendatario, y ejecutar "Dispersar fondos de alquiler" indicando el monto del depósito retenido correspondiente (incluyendo cero cuando el resultado sea liberación total), de manera que la reserva quede completamente liquidada —tanto en su depósito de garantía como en su valor de alquiler— de forma consistente con la decisión del Administrador Financiero.

**Por qué esta prioridad**: Sin la ejecución consolidada de esta liquidación, una reserva "completada con incidentes" quedaría sin ningún disparador que le pague al propietario el valor de alquiler que le corresponde, sin importar el resultado de la disputa sobre el depósito.

**Prueba Independiente**: Con una reserva "completada con incidentes" que cuenta con un depósito de garantía previamente registrado, enviar al sistema cada uno de los tres resultados posibles de la disputa (liberación total, retención total, retención parcial) y validar que el sistema aplica el tratamiento correspondiente sobre el depósito y ejecuta exactamente una vez "Dispersar fondos de alquiler" en los tres casos, con el monto del depósito retenido correcto en cada uno.

**Escenarios de Aceptación**:

1. **Escenario**: El reclamo no procede (sin daños que lo justifiquen) — liberación total del depósito.
   - **Dado** que existe un depósito de garantía previamente registrado para una reserva.
   - **Cuando** el Administrador Financiero determina que el reclamo no procede.
   - **Entonces** el sistema extiende hacia "Reembolsar dinero a arrendatario" por el 100% del depósito de garantía registrado, y ejecuta "Dispersar fondos de alquiler" indicando un monto de depósito retenido igual a cero, permitiendo que se liquide al propietario únicamente el valor de alquiler correspondiente.

2. **Escenario**: El reclamo procede totalmente — retención total del depósito.
   - **Dado** que existe un depósito de garantía previamente registrado para una reserva.
   - **Cuando** el Administrador Financiero determina que el reclamo procede totalmente, justificando la retención completa del depósito.
   - **Entonces** el sistema retiene el 100% del depósito de garantía registrado y ejecuta "Dispersar fondos de alquiler" indicando dicho monto retenido, de manera que el propietario reciba el valor de alquiler correspondiente junto con el 100% del depósito retenido, sin ejecutar "Reembolsar dinero a arrendatario".

3. **Escenario**: El reclamo procede parcialmente — retención parcial del depósito.
   - **Dado** que existe un depósito de garantía previamente registrado para una reserva.
   - **Cuando** el Administrador Financiero determina que el reclamo procede parcialmente, indicando el monto del depósito a retener.
   - **Entonces** el sistema retiene el monto determinado por el Administrador Financiero, ejecuta "Dispersar fondos de alquiler" indicando dicho monto retenido, de manera que el propietario reciba el valor de alquiler correspondiente junto con el monto retenido del depósito, y extiende hacia "Reembolsar dinero a arrendatario" por el monto restante del depósito.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Administrador Financiero intenta resolver la disputa de garantía de una reserva para la cual no existe un depósito de garantía previamente registrado (por ejemplo, porque "Solicitar el valor calculado de la reserva" nunca se completó para esa reserva)?**
  El sistema no realiza ninguna retención, extensión hacia "Reembolsar dinero a arrendatario" ni ejecución de "Dispersar fondos de alquiler" sobre una reserva sin información registrada. Conforme a RF-007, responde con un error controlado indicando que no existe un depósito de garantía registrado para el identificador de reserva recibido.

- **¿Qué sucede si el Administrador Financiero determina una retención parcial pero no informa el monto correspondiente a retener?**
  De acuerdo con RF-001, el monto a retener en una retención parcial es determinado y provisto por el Administrador Financiero como parte de su evaluación; sin este monto el sistema no puede aplicar el tratamiento de retención parcial ni indicar correctamente el monto retenido a "Dispersar fondos de alquiler". El sistema trata esta solicitud como incompleta y responde con un error controlado, de forma equivalente al tratamiento definido para la ausencia de depósito registrado.

- **¿Qué sucede si el Administrador Financiero resuelve más de una vez la disputa de garantía para la misma reserva?**
  El contexto no define un mecanismo que impida una nueva resolución sobre la misma reserva. Ante una nueva solicitud, el sistema aplica el mismo tratamiento (RF-003 a RF-006) sobre el resultado más reciente informado, sobrescribiendo el resultado y los montos previamente registrados para esa reserva, y ejecutando nuevamente "Dispersar fondos de alquiler" y/o "Reembolsar dinero a arrendatario" según corresponda al nuevo resultado.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Administrador Financiero el resultado de la evaluación de una disputa de garantía para una reserva específica, identificada mediante su identificador de reserva, indicando si el depósito de garantía debe liberarse totalmente, retenerse totalmente, o retenerse parcialmente junto con el monto a retener determinado por el Administrador Financiero.
- **RF-002**: El sistema DEBE recuperar el monto del depósito de garantía previamente registrado para dicha reserva, registrado mediante "Solicitar el valor calculado de la reserva".
- **RF-003**: El sistema DEBE, cuando el resultado sea liberación total (el reclamo no procede), extender hacia "Reembolsar dinero a arrendatario" por el 100% del depósito de garantía registrado, y ejecutar "Dispersar fondos de alquiler" indicando un monto de depósito retenido igual a cero.
- **RF-004**: El sistema DEBE, cuando el resultado sea retención total (el reclamo procede totalmente), retener el 100% del depósito de garantía registrado y ejecutar "Dispersar fondos de alquiler" indicando dicho monto retenido, sin ejecutar "Reembolsar dinero a arrendatario".
- **RF-005**: El sistema DEBE, cuando el resultado sea retención parcial (el reclamo procede parcialmente), retener el monto determinado por el Administrador Financiero, ejecutar "Dispersar fondos de alquiler" indicando dicho monto retenido, y extender hacia "Reembolsar dinero a arrendatario" por el monto restante del depósito de garantía.
- **RF-006**: El sistema DEBE actualizar internamente la información previamente registrada de la reserva, incorporando el resultado de la disputa y el monto del depósito retenido (incluyendo el valor cero, cuando el resultado sea liberación total), previo a la ejecución de "Dispersar fondos de alquiler".
- **RF-007**: El sistema DEBE responder con un error controlado cuando se solicite resolver la disputa de garantía de una reserva para la cual no existe un depósito de garantía previamente registrado.
- **RF-008**: El sistema DEBE exponer este caso de uso exclusivamente al Administrador Financiero.
- **RF-009**: El sistema DEBE ejecutar "Dispersar fondos de alquiler" exactamente una vez por cada resolución de disputa, en los tres resultados posibles (liberación total, retención total, retención parcial), indicando el monto del depósito de garantía retenido correspondiente en cada caso, de manera que la liquidación estándar del valor de alquiler al propietario quede consolidada, junto con el tratamiento del depósito de garantía, en una única operación de dispersión.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la solicitud del Administrador Financiero, mapeando únicamente los atributos esenciales (identificador de la reserva, resultado de la disputa y monto a retener cuando aplique).
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el depósito de garantía registrado, el monto retenido y el monto liberado.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante solicitudes de resolución de disputa para reservas sin depósito de garantía previamente registrado, evitando retenciones, extensiones o ejecuciones de dispersión sobre información inexistente.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3, actualizada en SPEC 4 y en este caso de uso)**: En este caso de uso es consultada (para recuperar el depósito de garantía registrado) y actualizada, incorporando el resultado de la disputa (liberado total, retenido total o retenido parcial) y el monto del depósito retenido (incluyendo cero cuando corresponda). Este monto retenido es el que se indica a "Dispersar fondos de alquiler" para que consolide, en una única operación, la liquidación estándar del valor de alquiler junto con el tratamiento del depósito de garantía.
- **SolicitudResolucionDisputa (DTO)**: Información recibida desde el Administrador Financiero para esta operación. Contiene el identificador de la reserva, el resultado de la disputa (liberación total, retención total o retención parcial) y, cuando el resultado sea retención parcial, el monto a retener determinado por el Administrador Financiero. No se persiste tal cual; sus datos se utilizan para actualizar la entidad InformaciónDeReserva.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión en el Manejo del Depósito, "100% de las resoluciones de disputa de garantía aplican exactamente el tratamiento correspondiente (liberación total, retención total o retención parcial) sobre el depósito previamente registrado, con cero (0) discrepancias de monto detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico y Consolidación de la Liquidación, "100% de las resoluciones de disputa de garantía, independientemente de su resultado (liberación total, retención total o retención parcial), ejecutan exactamente una vez 'Dispersar fondos de alquiler', indicando el monto del depósito retenido correspondiente (cero en el caso de liberación total), confirmando que la liquidación estándar del valor de alquiler para una reserva 'completada con incidentes' queda consolidada en esta única operación de dispersión, sin ejecuciones adicionales ni faltantes".
- **CE-003**: Resiliencia del Sistema, "100% de las solicitudes de resolución de disputa para reservas sin depósito de garantía previamente registrado son respondidas mediante un error controlado, sin provocar retenciones, extensiones ni ejecuciones de dispersión inconsistentes".