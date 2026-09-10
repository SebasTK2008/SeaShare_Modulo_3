# Especificación de Funcionalidad: UC07 - Brindar el Estado de la Reserva

**Creado**: 2026-09-06 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Reconocer estados operativos sin acción financiera, incluyendo el inicio del bloqueo temporal por confirmación de pago (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones la notificación del estado vigente de una reserva (disponible, reservado, en navegación o pendiente), quiero reconocer dicho estado y no ejecutar ninguna operación de reembolso ni de dispersión de fondos, ya que estos estados no desencadenan ninguna acción financiera. En particular, cuando el estado informado sea **pendiente**, este indica que el arrendatario acaba de iniciar la confirmación de pago y que la reserva se encuentra dentro del bloqueo temporal (estado de espera (pendiente)) de 15 minutos, ventana durante la cual se espera que se ejecute "Procesar cobro" sobre esa misma reserva.

> **Nota de alcance**: El Módulo 3 no es dueño del estado operativo de la reserva — ese pertenece al Módulo 2. El Módulo 3 recibe el estado como contexto de la notificación entrante para determinar si debe o no ejecutar una operación financiera. Para los estados disponible, reservado, en navegación y pendiente, la respuesta es simplemente no ejecutar ninguna operación financiera.

**Por qué esta prioridad**: Definir explícitamente qué estados no desencadenan ninguna acción financiera es tan importante como definir cuáles sí lo hacen, para evitar reembolsos o dispersiones involuntarias. Reconocer específicamente el estado pendiente permite al sistema identificar el momento de inicio del bloqueo temporal previo al cobro, sin necesidad de duplicar dicha lógica de tiempos, que reside en el Sistema de Reservas y Operaciones.

**Prueba Independiente**: Enviar al sistema la notificación de cada uno de los estados operativos (disponible, reservado, en navegación, pendiente) para una reserva con registro financiero existente, y validar que el sistema no dispara "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" en ninguno de los casos.

**Escenarios de Aceptación**:

1. **Escenario**: Notificación de un estado operativo sin acción financiera.
   - **Dado** que existe un registro financiero para una reserva específica.
   - **Cuando** el Sistema de Reservas y Operaciones notifica al sistema que el estado vigente de la reserva es disponible, reservado o en navegación.
   - **Entonces** el sistema reconoce el estado recibido y no ejecuta ninguna operación de reembolso ni de dispersión de fondos.

2. **Escenario**: Notificación del estado "pendiente" como inicio del bloqueo temporal por confirmación de pago.
   - **Dado** que existe un registro financiero para una reserva específica y el arrendatario acaba de iniciar la confirmación de pago.
   - **Cuando** el Sistema de Reservas y Operaciones notifica al sistema que el estado vigente de la reserva es "pendiente".
   - **Entonces** el sistema reconoce el estado "pendiente" como el inicio del bloqueo temporal (estado de espera (pendiente)) de 15 minutos, sin ejecutar ninguna operación de reembolso ni de dispersión de fondos.

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

### Historia de Usuario 4 - Reconocer la finalización de una reserva con incidentes sin ejecutar ninguna operación financiera automática (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones la notificación del estado "completada con incidentes" para una reserva específica, quiero reconocer dicho estado sin ejecutar ninguna operación de reembolso ni de dispersión de fondos, de manera que el registro financiero de la reserva (con el depósito de garantía registrado) quede disponible para que el Administrador Financiero lo consulte al ejecutar posteriormente "Resolver disputa de garantía".

**Por qué esta prioridad**: Es la separación explícita entre el momento en que se reporta el incidente (notificado por el Módulo 2) y el momento en que el Administrador Financiero toma una decisión financiera. El Módulo 3 no anticipa ningún resultado: solo reconoce que no debe ejecutar ninguna operación financiera automática ante este estado.

**Prueba Independiente**: Enviar al sistema la notificación del estado "completada con incidentes" para una reserva con registro financiero existente (incluyendo depósito de garantía registrado), y validar que el sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler".

**Escenarios de Aceptación**:

1. **Escenario**: Notificación de finalización con incidentes sin acción financiera automática.
   - **Dado** que existe un registro financiero para una reserva, incluyendo el depósito de garantía registrado.
   - **Cuando** el Sistema de Reservas y Operaciones notifica al sistema el estado "completada con incidentes".
   - **Entonces** el sistema no ejecuta ninguna operación de reembolso ni de dispersión de fondos, dejando el registro financiero disponible para que el Administrador Financiero ejecute "Resolver disputa de garantía".

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica el estado de una reserva para la cual no existe un registro financiero creado por "Brindar información de reserva"?**
  El sistema no puede determinar qué operación financiera ejecutar sobre una reserva de la que no tiene registro financiero interno. Dado que este caso de uso es unidireccional y no existe un canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo sin ejecutar operación alguna.

- **¿Qué sucede si el estado notificado corresponde a una cancelación (flexible, moderada o tardía/No-Show), pero el registro financiero de la reserva no contiene un valor total previamente calculado por "Solicitar el valor calculado de la reserva"?**
  De forma análoga al caso anterior, el sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" sin un monto de referencia. Al no existir canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo.

- **¿Qué sucede si el estado notificado es "completada sin incidentes" pero el registro financiero de la reserva no contiene un depósito de garantía y/o un valor de alquiler?**
  El sistema no ejecuta "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" con montos asumidos. Dado que ambas operaciones se disparan de forma conjunta para este estado, la ausencia de cualquiera de los dos montos requeridos impide ejecutar la operación correspondiente sobre información incompleta. De forma análoga a los casos anteriores, y al no existir canal de respuesta hacia el Sistema de Reservas y Operaciones, el sistema registra internamente el fallo sin ejecutar liberaciones o liquidaciones parciales o inconsistentes.

- **¿Qué sucede si, tras recibir la notificación "completada con incidentes", el Administrador Financiero nunca ejecuta "Resolver disputa de garantía"?**
  El contexto no define un mecanismo de expiración ni de escalamiento automático para este caso. El registro financiero de la reserva permanece disponible para que el Administrador Financiero lo consulte cuando decida ejecutar la resolución de la disputa.

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica el estado "pendiente" para una reserva que ya había recibido esa misma notificación (notificación repetida del inicio del bloqueo temporal)?**
  El contexto no define un mecanismo de deduplicación explícito para este caso de uso. Una notificación repetida con el mismo estado no desencadena ninguna operación financiera adicional, ya que el estado "pendiente" no dispara ninguna acción por sí mismo.

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica más de una vez el mismo estado de cancelación, o de finalización ("completada sin incidentes"/"completada con incidentes"), para la misma reserva?**
  El contexto no define un mecanismo de deduplicación explícito para este caso de uso. La notificación repetida de un estado de cancelación o de finalización sin incidentes no debe disparar nuevamente las operaciones financieras ya ejecutadas. Este comportamiento de idempotencia deberá ser abordado en la implementación, registrando internamente si las operaciones ya fueron ejecutadas para esa reserva.

- **¿Qué sucede si el Sistema de Reservas y Operaciones notifica un estado distinto a los nueve estados definidos en el contexto (disponible, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, completada sin incidentes, completada con incidentes)?**
  El sistema únicamente reconoce estos nueve estados como valores válidos. Ante un estado no reconocido, no ejecuta ninguna operación financiera asociada, registrando internamente la inconsistencia dado que no existe un canal de respuesta hacia el Sistema de Reservas y Operaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Sistema de Reservas y Operaciones el estado vigente de una reserva específica, identificada mediante su identificador de reserva, correspondiente a uno de los siguientes estados: disponible, reservado, en navegación, pendiente, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente/No-Show, completada sin incidentes o completada con incidentes.
- **RF-002**: El sistema DEBE, al recibir la notificación de cualquiera de los estados operativos sin acción financiera (disponible, reservado, en navegación o pendiente), reconocer el estado recibido sin ejecutar ninguna operación de reembolso ni de dispersión de fondos. El estado "pendiente" indica el inicio del bloqueo temporal (estado de espera (pendiente)) de 15 minutos originado por la confirmación de pago iniciada por el arrendatario; el sistema lo reconoce como tal sin necesidad de persistir el estado operativo, cuya gestión corresponde al Módulo 2.
- **RF-003**: El sistema DEBE, cuando el estado recibido sea "cancelado flexiblemente", ejecutar "Reembolsar dinero a arrendatario" indicando la ventana de cancelación flexible, correspondiente a un reembolso del 100% del valor del alquiler (menos costos transaccionales).
- **RF-004**: El sistema DEBE, cuando el estado recibido sea "cancelado moderadamente", ejecutar "Reembolsar dinero a arrendatario" indicando la ventana de cancelación moderada (reembolso del 50%) y "Liquidar fondos de alquiler" indicando la misma ventana, para la dispersión del 50% restante como compensación al propietario.
- **RF-005**: El sistema DEBE, cuando el estado recibido sea "cancelado tardíamente" o "No-Show", ejecutar únicamente "Liquidar fondos de alquiler" indicando dicha ventana, correspondiente a la dispersión del 100% del valor del alquiler como compensación al propietario, sin ejecutar ningún reembolso.
- **RF-006**: El sistema DEBE, cuando el estado recibido sea "completada sin incidentes", ejecutar "Reembolsar dinero a arrendatario" indicando el origen "finalización sin incidentes" (correspondiente a la liberación del 100% del depósito de garantía previamente registrado) **y** ejecutar "Liquidar fondos de alquiler" indicando el mismo origen "finalización sin incidentes" (correspondiente a la liquidación estándar del valor de alquiler al propietario: Valor Bruto menos Comisión de la Plataforma menos Seguro), sin ejecutar "Resolver disputa de garantía".
- **RF-007**: El sistema DEBE, cuando el estado recibido sea "completada con incidentes", no ejecutar ninguna operación de reembolso ni de dispersión de fondos, dejando el registro financiero de la reserva disponible para que el Administrador Financiero ejecute "Resolver disputa de garantía".
- **RF-008**: El sistema NO DEBE ejecutar "Reembolsar dinero a arrendatario" ni "Liquidar fondos de alquiler" cuando el estado recibido sea disponible, reservado, en navegación, pendiente o completada con incidentes.
- **RF-009**: El sistema NO DEBE devolver ninguna respuesta al Sistema de Reservas y Operaciones dentro de este caso de uso.
- **RF-010**: El sistema DEBE registrar internamente cualquier fallo ocurrido al intentar ejecutar "Reembolsar dinero a arrendatario" y/o "Liquidar fondos de alquiler" (por ejemplo, ausencia de registro financiero para la reserva, de valor total calculado o de depósito de garantía registrado), dado que este caso de uso no cuenta con un canal de respuesta hacia el Sistema de Reservas y Operaciones.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la solicitud del Sistema de Reservas y Operaciones, mapeando únicamente los atributos esenciales (identificador de la reserva y estado informado).
- **RNF-002**: El sistema DEBE mantener el uso de `BigDecimal` para cualquier valor monetario de la reserva que sea consultado o referenciado al ejecutar las extensiones hacia "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler".
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante la ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado para la reserva, registrando el fallo internamente dado que, al ser este caso de uso unidireccional, no existe un canal de respuesta directo hacia el Sistema de Reservas y Operaciones para notificarlo.

### Entidades Clave

- **RegistroFinancieroDeReserva (Entidad, creada en SPEC 3, enriquecida en SPEC 4)**: Registro persistido por el Módulo 3 que contiene los datos financieros de la reserva (identificador, tarifa base, número de días, número de pasajeros, monto total calculado, depósito de garantía y monto del seguro). En este caso de uso es **consultada en modo lectura** para obtener los montos necesarios al ejecutar "Reembolsar dinero a arrendatario" y/o "Liquidar fondos de alquiler". El Módulo 3 **no persiste el estado operativo** de la reserva — ese pertenece al Módulo 2; únicamente utiliza el estado recibido como contexto de decisión dentro de la ejecución de este caso de uso.
- **SolicitudEstadoReserva (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para esta operación. Contiene el identificador de la reserva y el estado notificado, correspondiente a uno de los nueve estados reconocidos. No se persiste; se utiliza como contexto de decisión para determinar qué operación financiera ejecutar sobre el RegistroFinancieroDeReserva.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Corrección de Decisión Financiera por Estado, "100% de las notificaciones de estado recibidas desde el Sistema de Reservas y Operaciones desencadenan exactamente la operación financiera esperada (o ninguna operación, en el caso de estados sin acción financiera), con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento de la Lógica de Cancelaciones, "100% de las reservas notificadas con un estado de cancelación (flexible, moderada o tardía/No-Show) disparan exactamente la combinación de operaciones de reembolso y/o dispersión correspondiente a su ventana, sin ejecuciones adicionales ni faltantes, en pruebas automatizadas".
- **CE-003**: Cumplimiento Arquitectónico, "0 ejecuciones de 'Reembolsar dinero a arrendatario' o 'Liquidar fondos de alquiler' registradas para estados no relacionados con cancelación ni con finalización sin incidentes (disponible, reservado, en navegación, pendiente, completada con incidentes)".
- **CE-004**: Resiliencia del Sistema, "100% de los fallos simulados por ausencia de información previamente registrada, de valor total calculado o de depósito de garantía registrado quedan registrados internamente mediante manejo de errores controlado, sin provocar ejecuciones parciales o inconsistentes hacia la Pasarela de pago".
- **CE-005**: Automatización de Liquidación Sin Incidentes, "100% de las reservas informadas como 'completada sin incidentes' disparan exactamente la ejecución conjunta de 'Reembolsar dinero a arrendatario' (liberando el 100% del depósito de garantía registrado) y 'Liquidar fondos de alquiler' (liquidando el valor de alquiler correspondiente al propietario), sin intervención del Administrador Financiero, en pruebas automatizadas".
- **CE-006**: Separación de Responsabilidades en Disputas, "0 ejecuciones de 'Reembolsar dinero a arrendatario' o de 'Liquidar fondos de alquiler' disparadas por este caso de uso para reservas informadas como 'completada con incidentes', confirmando que dicha decisión permanece exclusivamente a cargo del Administrador Financiero mediante 'Resolver disputa de garantía'".
