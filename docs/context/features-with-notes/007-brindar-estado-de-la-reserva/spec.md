# Especificación de Funcionalidad: UC07 - Brindar el Estado de la Reserva

**Creado**: 2026-09-06 (v3 — corregido para reflejar en RF-006, en la nota de trazabilidad y en CE-005 la ejecución conjunta de "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler" ante el estado "completada sin incidentes", comportamiento que ya estaba previsto en la Historia de Usuario 3 y su escenario de aceptación pero que no estaba reflejado en el requisito funcional correspondiente)

> **Nota de trazabilidad**: Este caso de uso es invocado por el Sistema de Reservas y Operaciones para informar al sistema el estado vigente de una reserva específica, correspondiente a uno de los **nueve estados reconocidos**: disponible, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, **completada sin incidentes** o **completada con incidentes**, permitiéndole mantener sincronizado el ciclo de vida operativo de la reserva con la operación del pago y la dispersión de fondos. El estado **pendiente** corresponde específicamente al momento en el que el arrendatario inicia la confirmación de pago de la reserva, es decir, al bloqueo temporal (estado de espera (pendiente)) de 15 minutos definido por el Sistema de Reservas y Operaciones (Módulo 2, 2.1), durante el cual se espera el resultado de "Procesar cobro" (SPEC 5). Cuando el estado informado corresponde a una cancelación, el sistema utiliza la información previamente registrada de la reserva (tarifa, días, pasajeros y valor total, registrados por "Brindar información de reserva" (SPEC 3) y "Solicitar el valor calculado de la reserva" (SPEC 4)) para ejecutar, según la ventana de cancelación vigente, "Reembolsar dinero a arrendatario" (SPEC 9) y/o "Liquidar fondos de alquiler" (SPEC 10). Cuando el estado informado es **"completada sin incidentes"**, el sistema utiliza el depósito de garantía y el valor de alquiler previamente registrados para ejecutar directamente "Reembolsar dinero a arrendatario" (SPEC 9), liberando el 100% del depósito, **y** "Liquidar fondos de alquiler" (SPEC 10), liquidando al propietario el valor de alquiler correspondiente (Valor Bruto menos Comisión de la Plataforma menos Seguro), sin requerir ninguna intervención del Administrador Financiero. Cuando el estado informado es **"completada con incidentes"**, el sistema únicamente actualiza el estado registrado de la reserva, dejándola disponible para que el Administrador Financiero evalúe la evidencia y ejecute, por su propia iniciativa, "Resolver disputa de garantía" (SPEC 8); este caso de uso no dispara por sí mismo dicha resolución ni ninguna operación de reembolso o dispersión de fondos. Este caso de uso es **unidireccional**: el sistema no devuelve ninguna respuesta al Sistema de Reservas y Operaciones.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registrar el estado operativo de la reserva, incluyendo el inicio del bloqueo temporal por confirmación de pago (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones el estado vigente de una reserva (disponible, reservado, en navegación o pendiente), quiero registrar/actualizar internamente dicho estado en la información previamente registrada de esa reserva, de manera que el ciclo de vida operativo de la reserva se mantenga sincronizado con el estado financiero, sin ejecutar ninguna operación de reembolso ni de dispersión de fondos. En particular, cuando el estado informado sea **pendiente**, este indica que el arrendatario acaba de iniciar la confirmación de pago y que la reserva se encuentra dentro del bloqueo temporal (estado de espera (pendiente)) de 15 minutos, ventana durante la cual se espera que se ejecute "Procesar cobro" sobre esa misma reserva.

**Por qué esta prioridad**: Es la base que permite al sistema mantener actualizado, en todo momento, el estado de cada reserva frente al que reacciona el resto de casos de uso del ciclo de cobro y liquidación. Reconocer específicamente el estado pendiente permite al sistema mantener sincronizado el momento en que se inicia el bloqueo temporal previo al cobro, sin necesidad de duplicar dicha lógica de tiempos, que reside en el Sistema de Reservas y Operaciones.

**Prueba Independiente**: Con una reserva que cuenta con información previamente registrada, enviar al sistema cada uno de los estados operativos (disponible, reservado, en navegación, pendiente) y validar que el sistema actualiza el estado registrado internamente sin disparar "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler".

**Escenarios de Aceptación**:

1. **Escenario**: Registro de un estado operativo sin bloqueo temporal ni acción financiera adicional.
   - **Dado** que existe información previamente registrada para una reserva específica.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema que el estado vigente de la reserva es disponible, reservado o en navegación.
   - **Entonces** el sistema actualiza internamente el estado registrado de la reserva y no ejecuta ninguna operación de reembolso ni de dispersión de fondos.

2. **Escenario**: Registro del estado "pendiente" como inicio del bloqueo temporal por confirmación de pago.
   - **Dado** que existe información previamente registrada para una reserva específica y el arrendatario acaba de iniciar la confirmación de pago.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema que el estado vigente de la reserva es "pendiente".
   - **Entonces** el sistema actualiza internamente el estado registrado de la reserva a "pendiente", reflejando el inicio del bloqueo temporal (estado de espera (pendiente)) de 15 minutos, sin ejecutar ninguna operación de reembolso ni de dispersión de fondos.

---

### Historia de Usuario 2 - Disparar la operación financiera correspondiente ante un estado de cancelación (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones el estado de cancelación de una reserva (cancelado flexiblemente, cancelado moderadamente o cancelado tardíamente/No-Show), quiero ejecutar la operación de reembolso y/o dispersión de fondos que corresponde a la ventana de cancelación informada, de manera que el arrendatario y el propietario reciban el tratamiento financiero definido para cada tipo de cancelación.

**Por qué esta prioridad**: Es la aplicación directa de la lógica de cancelaciones y reembolsos del negocio; sin este disparo correcto, una reserva cancelada quedaría sin el reembolso o la compensación al propietario que le corresponde.

**Prueba Independiente**: Con reservas que cuentan con información y valor total previamente registrados, enviar al sistema cada uno de los tres estados de cancelación y validar que se ejecuta exactamente la combinación de operaciones esperada para cada ventana.

**Escenarios de Aceptación**:

1. **Escenario**: Cancelación flexible (>72h).
   - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "cancelado flexiblemente".
   - **Entonces** el sistema ejecuta "Reembolsar dinero a arrendatario" por el 100% del valor del alquiler (menos costos transaccionales), sin ejecutar "Liquidar fondos de alquiler".

2. **Escenario**: Cancelación moderada (72h–24h).
   - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "cancelado moderadamente".
   - **Entonces** el sistema ejecuta "Reembolsar dinero a arrendatario" por el 50% del valor del alquiler y "Liquidar fondos de alquiler" por el 50% restante como compensación al propietario.

3. **Escenario**: Cancelación tardía / No-Show (<24h).
   - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "cancelado tardíamente" o "No-Show".
   - **Entonces** el sistema ejecuta únicamente "Liquidar fondos de alquiler" por el 100% del valor del alquiler como compensación al propietario, sin ejecutar ningún reembolso.

---

### Historia de Usuario 3 - Ejecutar el reembolso del depósito de garantía y la liquidación del alquiler ante la finalización de una reserva sin incidentes (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones el estado "completada sin incidentes" para una reserva específica, quiero ejecutar "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler" indicando este origen, de manera que el depósito de garantía previamente registrado sea liberado íntegramente al arrendatario y el dinero correspondiente al propietario sea liquidado, sin requerir la intervención del Administrador Financiero.

**Por qué esta prioridad**: La mayoría de las reservas finalizan sin incidentes, por lo que liberar automáticamente el depósito y liquidar el alquiler ante este estado evita retener innecesariamente el dinero del arrendatario y del propietario, y evita someter al Administrador Financiero a evaluaciones de disputas inexistentes.

**Prueba Independiente**: Con una reserva que cuenta con un depósito de garantía y un valor de alquiler previamente registrados, informar al sistema el estado "completada sin incidentes" y validar que el sistema ejecuta tanto "Reembolsar dinero a arrendatario" como "Liquidar fondos de alquiler" indicando el origen correspondiente, sin ejecutar "Resolver disputa de garantía".

**Escenarios de Aceptación**:

1. **Escenario**: Liberación automática del depósito y liquidación del alquiler ante finalización sin incidentes.
   - **Dado** que existe información previamente registrada, incluyendo el depósito de garantía y el valor de alquiler, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "completada sin incidentes".
   - **Entonces** el sistema ejecuta "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler" indicando el origen "finalización sin incidentes", liberando el 100% del depósito de garantía registrado y liquidando al propietario el valor de alquiler correspondiente (Valor Bruto menos Comisión de la Plataforma menos Seguro), sin ejecutar "Resolver disputa de garantía".

---

### Historia de Usuario 4 - Registrar la finalización de una reserva con incidentes, habilitando la resolución de disputa de garantía (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones el estado "completada con incidentes" para una reserva específica, quiero actualizar internamente el estado registrado de dicha reserva, de manera que quede disponible para que el Administrador Financiero pueda evaluar y ejecutar posteriormente "Resolver disputa de garantía" sobre el depósito de garantía previamente registrado, sin que este caso de uso dispare por sí mismo ninguna operación de reembolso o dispersión de fondos.

**Por qué esta prioridad**: Es el paso que deja constancia, dentro del ciclo de vida de la reserva, de que existen posibles daños por evaluar, sin adelantar ningún resultado financiero que corresponde determinar exclusivamente al Administrador Financiero.

**Prueba Independiente**: Con una reserva que cuenta con información previamente registrada, informar al sistema el estado "completada con incidentes" y validar que el sistema actualiza el estado registrado, sin ejecutar "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler".

**Escenarios de Aceptación**:

1. **Escenario**: Registro de la finalización con incidentes sin acción financiera automática.
   - **Dado** que existe información previamente registrada para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "completada con incidentes".
   - **Entonces** el sistema actualiza internamente el estado registrado de la reserva a "completada con incidentes", dejándola disponible para que el Administrador Financiero ejecute "Resolver disputa de garantía", sin ejecutar por sí mismo ninguna operación de reembolso ni de dispersión de fondos.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Reservas y Operaciones informa el estado de una reserva para la cual no existe información previamente registrada por "Brindar información de reserva"?**
  El sistema no puede sincronizar un estado sobre una reserva de la que no tiene registro interno. Dado que este caso de uso es unidireccional y no existe un canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo, sin actualizar ningún estado ni ejecutar operación alguna.

- **¿Qué sucede si el estado informado corresponde a una cancelación (flexible, moderada o tardía/No-Show), pero la reserva no cuenta con un valor total previamente calculado y registrado por "Solicitar el valor calculado de la reserva"?**
  De forma análoga al caso anterior, el sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" sobre una reserva sin valor total registrado. Al no existir canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo.

- **¿Qué sucede si el estado informado es "completada sin incidentes" pero la reserva no cuenta con un depósito de garantía y/o un valor de alquiler previamente registrados?**
  El sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" con un monto asumido. Dado que ambas operaciones se disparan de forma conjunta para este estado, la ausencia de cualquiera de los dos montos requeridos (depósito de garantía o valor de alquiler) impide ejecutar la operación correspondiente sobre información incompleta. De forma análoga a los casos anteriores, y al no existir canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo, sin ejecutar una liberación de depósito o una liquidación de alquiler parciales o inconsistentes.

- **¿Qué sucede si, estando la reserva en estado "completada con incidentes", el Administrador Financiero nunca ejecuta "Resolver disputa de garantía"?**
  El contexto no define un mecanismo de expiración ni de escalamiento automático para este caso. El estado de la reserva permanece registrado como "completada con incidentes" hasta que el Administrador Financiero evalúe la evidencia y ejecute dicha resolución.

- **¿Qué sucede si el Sistema de Reservas y Operaciones informa el estado "pendiente" para una reserva que ya se encontraba en estado "pendiente" (por ejemplo, una notificación repetida del inicio del bloqueo temporal)?**
  El contexto no define un mecanismo de deduplicación explícito para este caso de uso. El estado registrado ya refleja el inicio del bloqueo temporal, por lo que una notificación repetida con el mismo estado no altera el estado ya registrado.

- **¿Qué sucede si el Sistema de Reservas y Operaciones informa más de una vez el mismo estado de cancelación, o de finalización ("completada sin incidentes"/"completada con incidentes"), para la misma reserva?**
  El contexto no define un mecanismo de deduplicación explícito para este caso de uso. El estado registrado ya refleja la condición vigente de la reserva, por lo que una notificación repetida con el mismo estado no altera el estado ya registrado ni vuelve a disparar las operaciones financieras correspondientes.

- **¿Qué sucede si el Sistema de Reservas y Operaciones informa un estado distinto a los nueve estados definidos en el contexto (disponible, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, completada sin incidentes, completada con incidentes)?**
  El sistema únicamente reconoce estos nueve estados como valores válidos. Ante un estado no reconocido, no actualiza el estado registrado de la reserva ni ejecuta ninguna operación asociada, registrando internamente la inconsistencia dado que no existe un canal de respuesta hacia el Sistema de Reservas y Operaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Sistema de Reservas y Operaciones el estado vigente de una reserva específica, identificada mediante su identificador de reserva, correspondiente a uno de los siguientes estados: disponible, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, completada sin incidentes o completada con incidentes.
- **RF-002**: El sistema DEBE registrar/actualizar internamente, en la información previamente registrada de la reserva, el estado recibido, manteniendo sincronizado el ciclo de vida operativo de la reserva con el estado financiero. Cuando el estado recibido sea "pendiente", este registro representa el inicio del bloqueo temporal (estado de espera (pendiente)) de 15 minutos originado por la confirmación de pago iniciada por el arrendatario.
- **RF-003**: El sistema DEBE, cuando el estado recibido sea "cancelado flexiblemente", ejecutar "Reembolsar dinero a arrendatario" indicando la ventana de cancelación flexible, correspondiente a un reembolso del 100% del valor del alquiler (menos costos transaccionales).
- **RF-004**: El sistema DEBE, cuando el estado recibido sea "cancelado moderadamente", ejecutar "Reembolsar dinero a arrendatario" indicando la ventana de cancelación moderada (reembolso del 50%) y "Liquidar fondos de alquiler" indicando la misma ventana, para la dispersión del 50% restante como compensación al propietario.
- **RF-005**: El sistema DEBE, cuando el estado recibido sea "cancelado tardíamente" o "No-Show", ejecutar únicamente "Liquidar fondos de alquiler" indicando dicha ventana, correspondiente a la dispersión del 100% del valor del alquiler como compensación al propietario, sin ejecutar ningún reembolso.
- **RF-006**: El sistema DEBE, cuando el estado recibido sea "completada sin incidentes", ejecutar "Reembolsar dinero a arrendatario" indicando el origen "finalización sin incidentes" (correspondiente a la liberación del 100% del depósito de garantía previamente registrado) **y** ejecutar "Liquidar fondos de alquiler" indicando el mismo origen "finalización sin incidentes" (correspondiente a la liquidación estándar del valor de alquiler al propietario: Valor Bruto menos Comisión de la Plataforma menos Seguro), sin ejecutar "Resolver disputa de garantía".
- **RF-007**: El sistema DEBE, cuando el estado recibido sea "completada con incidentes", actualizar internamente el estado registrado de la reserva, dejándola disponible para que el Administrador Financiero ejecute "Resolver disputa de garantía", sin ejecutar por sí mismo ninguna operación de reembolso ni de dispersión de fondos.
- **RF-008**: El sistema NO DEBE ejecutar "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" cuando el estado recibido sea disponible, reservado, en navegación, pendiente o completada con incidentes.
- **RF-009**: El sistema NO DEBE devolver ninguna respuesta al Sistema de Reservas y Operaciones dentro de este caso de uso.
- **RF-010**: El sistema DEBE registrar internamente cualquier fallo ocurrido al intentar sincronizar el estado de una reserva o al intentar ejecutar "Reembolsar dinero a arrendatario"/"Liquidar fondos de alquiler" (por ejemplo, ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado), dado que este caso de uso no cuenta con un canal de respuesta hacia el Sistema de Reservas y Operaciones.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la solicitud del Sistema de Reservas y Operaciones, mapeando únicamente los atributos esenciales (identificador de la reserva y estado informado).
- **RNF-002**: El sistema DEBE mantener el uso de `BigDecimal` para cualquier valor monetario de la reserva que sea consultado o referenciado al ejecutar las extensiones hacia "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler".
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante la ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado para la reserva, registrando el fallo internamente dado que, al ser este caso de uso unidireccional, no existe un canal de respuesta directo hacia el Sistema de Reservas y Operaciones para notificarlo.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3, actualizada en SPEC 4 y en este caso de uso)**: En este caso de uso es consultada y actualizada, incorporando el estado de la reserva recibido (incluyendo "pendiente" como marca del inicio del bloqueo temporal, y "completada sin incidentes"/"completada con incidentes" como marcas de finalización), de manera que quede disponible para determinar si corresponde ejecutar "Reembolsar dinero a arrendatario" y/o "Liquidar fondos de alquiler", o si corresponde habilitar "Resolver disputa de garantía".
- **SolicitudEstadoReserva (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para esta operación. Contiene el identificador de la reserva y el estado informado, correspondiente a uno de los nueve estados reconocidos. No se persiste tal cual; sus datos se utilizan para actualizar la entidad InformaciónDeReserva.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Sincronización de Estados, "100% de los estados informados por el Sistema de Reservas y Operaciones, incluyendo el estado pendiente y los estados de finalización (completada sin incidentes/con incidentes), quedan reflejados en la información registrada de la reserva correspondiente, con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento de la Lógica de Cancelaciones, "100% de las reservas notificadas con un estado de cancelación (flexible, moderada o tardía/No-Show) disparan exactamente la combinación de operaciones de reembolso y/o dispersión correspondiente a su ventana, sin ejecuciones adicionales ni faltantes, en pruebas automatizadas".
- **CE-003**: Cumplimiento Arquitectónico, "0 ejecuciones de 'Reembolsar dinero a arrendatario' o 'Liquidar fondos de alquiler' registradas para estados no relacionados con cancelación ni con finalización sin incidentes (disponible, reservado, en navegación, pendiente, completada con incidentes)".
- **CE-004**: Resiliencia del Sistema, "100% de los fallos simulados por ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado quedan registrados internamente mediante manejo de errores controlado, sin provocar ejecuciones parciales o inconsistentes hacia la Pasarela de pago".
- **CE-005**: Automatización de Liquidación Sin Incidentes, "100% de las reservas informadas como 'completada sin incidentes' disparan exactamente la ejecución conjunta de 'Reembolsar dinero a arrendatario' (liberando el 100% del depósito de garantía registrado) y 'Liquidar fondos de alquiler' (liquidando el valor de alquiler correspondiente al propietario), sin intervención del Administrador Financiero, en pruebas automatizadas".
- **CE-006**: Separación de Responsabilidades en Disputas, "0 ejecuciones de 'Reembolsar dinero a arrendatario' o de 'Liquidar fondos de alquiler' disparadas por este caso de uso para reservas informadas como 'completada con incidentes', confirmando que dicha decisión permanece exclusivamente a cargo del Administrador Financiero mediante 'Resolver disputa de garantía'".
