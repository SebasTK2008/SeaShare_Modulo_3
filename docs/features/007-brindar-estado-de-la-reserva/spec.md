# Especificación de Funcionalidad: UC07 - Brindar el Estado de la Reserva

**Creado**: 2026-09-06 (v3 — se incorpora "iniciada" como el noveno estado reconocido y como el verdadero origen del bloqueo temporal (TTL) de 15 minutos; "pendiente" ya no origina el TTL, únicamente indica que este continúa vigente mientras se ejecuta la confirmación de pago, conforme a la actualización de `contexto-modulo3.md` y `sea-share.md`)

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reconocer estados operativos sin acción financiera, incluyendo el inicio del bloqueo temporal en el estado "iniciada" (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones la notificación del estado vigente de una reserva (disponible, iniciada, reservado, en navegación o pendiente), quiero reconocer dicho estado y no ejecutar ninguna operación de reembolso ni de dispersión de fondos, ya que estos estados no desencadenan ninguna acción financiera. En particular:

- Cuando el estado informado sea **iniciada**, este indica que el arrendatario acaba de oprimir "Reservar" y que comienza el bloqueo temporal (estado de espera) de 15 minutos.
- Cuando el estado informado sea **pendiente**, este indica que el arrendatario ya inició la confirmación de pago, dentro del mismo bloqueo temporal iniciado previamente en "iniciada"; esta transición **no reinicia** el TTL, que sigue corriendo desde su origen.

**Por qué esta prioridad**: Definir explícitamente qué estados no desencadenan ninguna acción financiera es tan importante como definir cuáles sí lo hacen, para evitar reembolsos o dispersiones involuntarias. Reconocer específicamente el estado "iniciada" permite al sistema identificar el momento exacto de origen del bloqueo temporal previo al cobro, y reconocer "pendiente" permite identificar que dicho bloqueo continúa vigente durante la confirmación de pago, sin necesidad de duplicar la lógica de tiempos, que reside en el Sistema de Reservas y Operaciones.

**Prueba Independiente**: Enviar al sistema la notificación de cada uno de los estados operativos (disponible, iniciada, reservado, en navegación, pendiente) para una reserva con registro financiero existente, y validar que el sistema no dispara "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" en ninguno de los casos.

**Escenarios de Aceptación**:

1. **Escenario**: Notificación de un estado operativo sin acción financiera.
   - **Dado** que existe un registro financiero para una reserva específica.
   - **Cuando** el Sistema de Reservas y Operaciones notifica al sistema que el estado vigente de la reserva es disponible, reservado o en navegación.
   - **Entonces** el sistema reconoce el estado recibido y no ejecuta ninguna operación de reembolso ni de dispersión de fondos.

2. **Escenario**: Notificación del estado "iniciada" como origen del bloqueo temporal.
   - **Dado** que existe un registro financiero para una reserva específica y el arrendatario acaba de oprimir "Reservar".
   - **Cuando** el Sistema de Reservas y Operaciones notifica al sistema que el estado vigente de la reserva es "iniciada".
   - **Entonces** el sistema reconoce el estado "iniciada" como el inicio del bloqueo temporal (TTL) de 15 minutos, sin ejecutar ninguna operación de reembolso ni de dispersión de fondos.

3. **Escenario**: Notificación del estado "pendiente" como continuación del bloqueo temporal ya iniciado.
   - **Dado** que existe un registro financiero para una reserva específica, el arrendatario ya inició la confirmación de pago y el bloqueo temporal comenzó previamente en "iniciada".
   - **Cuando** el Sistema de Reservas y Operaciones notifica al sistema que el estado vigente de la reserva es "pendiente".
   - **Entonces** el sistema reconoce el estado "pendiente" como continuación del mismo bloqueo temporal (sin reiniciar el TTL), sin ejecutar ninguna operación de reembolso ni de dispersión de fondos.

---

### Historia de Usuario 2 - Disparar la operación financiera correspondiente ante un estado de cancelación (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones el estado de cancelación de una reserva (cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show o cancelado por anfitrión), quiero ejecutar la operación de reembolso y/o dispersión de fondos que corresponde al estado informado.

**Por qué esta prioridad**: Es la aplicación directa de la lógica de cancelaciones y reembolsos del negocio; sin este disparo correcto, una reserva cancelada quedaría sin el reembolso o la compensación al propietario que le corresponde.

**Prueba Independiente**: Con reservas que cuentan con información y valor total previamente registrados, enviar al sistema cada uno de los cuatro estados de cancelación y validar que se ejecuta exactamente la operación esperada para cada estado.

**Escenarios de Aceptación**:

1. **Escenario**: Cancelación flexible (>72h).
   - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "cancelado flexiblemente".
  - **Entonces** el sistema solicita la liberación o el reembolso del 100% del valor total pagado, según el estado del cobro original, sin ejecutar "Liquidar fondos de alquiler". El resultado queda pendiente de confirmación externa.

2. **Escenario**: Cancelación moderada (72h–24h).
   - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "cancelado moderadamente".
  - **Entonces** el sistema solicita la liberación o el reembolso del 50% del valor total pagado y solicita la liquidación del 50% del valor del alquiler como compensación al propietario. Cada operación conserva su propio estado y resultado.

3. **Escenario**: Cancelación tardía / No-Show (<24h).
   - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa al sistema el estado "cancelado tardíamente" o "No-Show".
  - **Entonces** el sistema solicita únicamente la liquidación del 100% del valor del alquiler como compensación al propietario, sin solicitar reembolso. La solicitud no implica que los fondos ya hayan sido recibidos.

  4. **Escenario**: Cancelación por anfitrión.
    - **Dado** que existe información previamente registrada, incluyendo el valor total, para una reserva.
    - **Cuando** el Sistema de Reservas y Operaciones informa el estado "cancelado por anfitrión".
    - **Entonces** el sistema solicita la liberación o el reembolso del 100% del valor pagado al Arrendatario, según el estado del cobro original, sin ejecutar "Liquidar fondos de alquiler".

---

### Historia de Usuario 3 - Liquidar el alquiler al completar la reserva y esperar el estado de la disputa de garantía (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones el único estado de finalización "completada" para una reserva específica, quiero liquidar el valor correspondiente al alquiler y mantener pendiente únicamente el depósito de garantía, de manera que el Módulo 2 pueda informar posteriormente el estado de la disputa y el Módulo 3 ejecute su consecuencia financiera.

**Por qué esta prioridad**: La finalización de la reserva no implica por sí misma que exista o no un daño. El alquiler puede liquidarse al completar la reserva, mientras que el depósito permanece pendiente hasta recibir el estado de disputa.

**Prueba Independiente**: Informar el estado "completada" para una reserva con valor de alquiler, seguro y depósito registrados, y validar que el sistema ejecuta la liquidación estándar sin incluir el depósito, sin ejecutar aún el reembolso ni la liquidación del depósito.

**Escenarios de Aceptación**:

1. **Escenario**: Finalización única de la reserva.
  - **Dado** que existe información financiera registrada para una reserva.
  - **Cuando** el Sistema de Reservas y Operaciones informa el estado "completada".
  - **Entonces** el sistema solicita la liquidación estándar del alquiler al propietario (Valor Bruto menos Comisión de la Plataforma menos Seguro), mantiene el depósito pendiente y no ejecuta todavía ninguna operación sobre la garantía.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica el estado de una reserva para la cual no existe un registro financiero creado por "Brindar información de reserva"?**
  El sistema no puede determinar qué operación financiera ejecutar sobre una reserva de la que no tiene registro financiero interno. Dado que este caso de uso es unidireccional y no existe un canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo sin ejecutar operación alguna.

- **¿Qué sucede si el estado notificado corresponde a una cancelación (flexible, moderada, tardía/No-Show o por anfitrión), pero el registro financiero de la reserva no contiene un valor total previamente calculado por "Solicitar el valor calculado de la reserva"?**
  De forma análoga al caso anterior, el sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" sin un monto de referencia. Al no existir canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo.

- **¿Qué sucede si el estado notificado es "completada" pero el registro financiero de la reserva no contiene un depósito y/o un valor de alquiler?**
  El sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" con montos asumidos. Dado que ambas operaciones se disparan de forma conjunta para este estado, la ausencia de cualquiera de los dos montos requeridos impide ejecutar la operación correspondiente sobre información incompleta. De forma análoga a los casos anteriores, y al no existir canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo sin ejecutar liberaciones o liquidaciones parciales o inconsistentes.

- **¿Qué sucede si, tras recibir la notificación "completada", el Módulo 2 nunca informa el estado de la disputa?**
  El depósito permanece pendiente y el sistema registra la ausencia del evento esperado para su conciliación o escalamiento, sin reembolsarlo ni liquidarlo automáticamente.

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica el estado "iniciada" o "pendiente" para una reserva que ya había recibido esa misma notificación (notificación repetida)?**
  El contexto no define un mecanismo de deduplicación explícito para este caso de uso. Una notificación repetida con el mismo estado no desencadena ninguna operación financiera adicional, ya que ni "iniciada" ni "pendiente" disparan ninguna acción financiera por sí mismos.

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica más de una vez el mismo estado de cancelación o de finalización ("completada") para la misma reserva?**
  El contexto no define un mecanismo de deduplicación explícito para este caso de uso. La notificación repetida de un estado de cancelación o de finalización no debe disparar nuevamente las operaciones financieras ya ejecutadas. Este comportamiento de idempotencia deberá ser abordado en la implementación, registrando internamente si las operaciones ya fueron ejecutadas para esa reserva.

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica un estado distinto a los diez estados definidos en el contexto (disponible, iniciada, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, cancelado por anfitrión y completada)?**
  El sistema no ejecuta ninguna operación financiera asociada y registra internamente la inconsistencia.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Sistema de Reservas y Operaciones el estado vigente de una reserva específica, identificada mediante su identificador de reserva, correspondiente a uno de los siguientes estados: disponible, iniciada, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, cancelado por anfitrión o completada.
- **RF-002**: El sistema DEBE, al recibir la notificación de cualquiera de los estados operativos sin acción financiera (disponible, iniciada, reservado, en navegación o pendiente), reconocer el estado recibido sin ejecutar ninguna operación de reembolso ni de dispersión de fondos. El estado "iniciada" indica el origen del bloqueo temporal (estado de espera) de 15 minutos, que comienza cuando el arrendatario oprime "Reservar"; el estado "pendiente" indica que el arrendatario ya inició la confirmación de pago dentro de ese mismo bloqueo temporal, sin reiniciar el TTL. El sistema reconoce ambos estados como tales sin necesidad de persistir el estado operativo, cuya gestión corresponde al Módulo 2.
- **RF-003**: El sistema DEBE, cuando el estado recibido sea "cancelado flexiblemente", solicitar la liberación o el reembolso del 100% del valor total pagado, según el estado del cobro original.
- **RF-004**: El sistema DEBE, cuando el estado recibido sea "cancelado moderadamente", solicitar la liberación o el reembolso del 50% del valor total pagado y la liquidación del 50% del valor del alquiler como compensación al propietario, manteniendo resultados independientes.
- **RF-005**: El sistema DEBE, cuando el estado recibido sea "cancelado tardíamente" o "No-Show", solicitar únicamente la liquidación del 100% del valor del alquiler como compensación al propietario, sin solicitar reembolso.
- **RF-005A**: El sistema DEBE, cuando el estado recibido sea "cancelado por anfitrión", solicitar la liberación o el reembolso del 100% del valor pagado al Arrendatario, según el estado del cobro original, sin solicitar una dispersión.
- **RF-006**: El sistema DEBE, cuando el estado recibido sea "completada", solicitar la liquidación estándar del valor de alquiler al propietario, sin incluir el depósito, y dejar el depósito pendiente hasta recibir "Brindar información de disputa de garantía".
- **RF-007**: El sistema NO DEBE ejecutar una operación sobre el depósito únicamente por recibir el estado "completada".
- **RF-008**: El sistema NO DEBE ejecutar "Reembolsar dinero a arrendatario" ni una liquidación del depósito cuando el estado recibido sea disponible, iniciada, reservado, en navegación, pendiente o completada.
- **RF-009**: El sistema NO DEBE devolver ninguna respuesta al Sistema de Reservas y Operaciones dentro de este caso de uso.
- **RF-010**: El sistema DEBE registrar internamente cualquier fallo, estado pendiente o resultado no concluyente ocurrido al solicitar "Reembolsar dinero a arrendatario" y/o "Liquidar fondos de alquiler", dado que este caso de uso no cuenta con un canal de respuesta hacia el Sistema de Reservas y Operaciones.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la solicitud del Sistema de Reservas y Operaciones, mapeando únicamente los atributos esenciales (identificador de la reserva y estado informado).
- **RNF-002**: El sistema DEBE mantener el uso de `BigDecimal` para cualquier valor monetario de la reserva que sea consultado o referenciado al ejecutar las extensiones hacia "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler".
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante la ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado para la reserva, registrando el fallo internamente dado que, al ser este caso de uso unidireccional, no existe un canal de respuesta directo hacia el Sistema de Reservas y Operaciones para notificarlo.

### Entidades Clave

- **InformaciónDeReserva (Entidad, creada en SPEC 3, enriquecida en SPEC 4)**: Registro persistido por el Módulo 3 que contiene los datos financieros de la reserva (identificador, tarifa base, número de días, número de pasajeros, monto total calculado, depósito de garantía y monto del seguro). En este caso de uso es **consultada en modo lectura** para obtener los montos necesarios al ejecutar "Reembolsar dinero a arrendatario" y/o "Liquidar fondos de alquiler". El Módulo 3 **no persiste el estado operativo** de la reserva — ese pertenece al Módulo 2; únicamente utiliza el estado recibido como contexto de decisión dentro de la ejecución de este caso de uso.
- **SolicitudEstadoReserva (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para esta operación. Contiene el identificador de la reserva y el estado notificado, correspondiente a uno de los diez estados reconocidos (incluyendo "iniciada" y "cancelado por anfitrión"). No se persiste; se utiliza como contexto de decisión para determinar qué operación financiera ejecutar sobre la InformaciónDeReserva.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Corrección de Decisión Financiera por Estado, "100% de las notificaciones de estado recibidas desde el Sistema de Reservas y Operaciones desencadenan exactamente la operación financiera esperada (o ninguna operación, en el caso de estados sin acción financiera), con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento de la Lógica de Cancelaciones, "100% de las reservas notificadas con un estado de cancelación (flexible, moderada, tardía/No-Show o por anfitrión) disparan exactamente la operación de reembolso y/o dispersión correspondiente, sin ejecuciones adicionales ni faltantes, en pruebas automatizadas".
- **CE-003**: Cumplimiento Arquitectónico, "0 ejecuciones de operaciones sobre el depósito registradas por recibir el estado 'completada' o por estados operativos sin acción financiera".
- **CE-004**: Resiliencia del Sistema, "100% de los fallos simulados por ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado quedan registrados internamente mediante manejo de errores controlado, sin provocar ejecuciones parciales o inconsistentes hacia la Pasarela de pago".
- **CE-005**: Liquidación al Completar, "100% de las reservas informadas como 'completada' disparan exactamente la liquidación estándar del alquiler, sin incluir el depósito de garantía".
- **CE-006**: Separación de Responsabilidades en Disputas, "0 decisiones sobre daños o sobre el destino del depósito son tomadas por este caso de uso; todas dependen de la información posterior recibida mediante 'Brindar información de disputa de garantía'".
- **CE-007**: Fidelidad del Origen del Bloqueo Temporal, "100% de las notificaciones del estado 'iniciada' son reconocidas como el origen del bloqueo temporal (TTL) de 15 minutos, y 100% de las notificaciones del estado 'pendiente' son reconocidas como continuación de dicho bloqueo sin reiniciar el TTL, con cero (0) discrepancias detectadas en pruebas automatizadas".