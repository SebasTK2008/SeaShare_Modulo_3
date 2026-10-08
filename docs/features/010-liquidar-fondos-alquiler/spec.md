# Especificación de Funcionalidad: UC10 - Liquidar Fondos de Alquiler

**Creado**: 2026-09-06 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Dispersar fondos al propietario por estado de cancelación (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado corresponde a una cancelación moderada o tardía, quiero recuperar el monto de alquiler previamente registrado y calcular la compensación que corresponde según el estado.

**Por qué esta prioridad**: Sin este cálculo y solicitud correctos, el propietario no recibiría la compensación que le corresponde por una cancelación moderada o tardía, incumpliendo directamente la lógica de cancelaciones definida para la plataforma.

**Prueba Independiente**: Con una reserva que cuenta con un monto de alquiler previamente registrado, invocar internamente la dispersión para los estados cancelado moderadamente y cancelado tardíamente y validar que se solicita el monto correspondiente.

**Escenarios de Aceptación**:

1. **Escenario**: Dispersión por cancelación moderada (72h–24h).
   - **Dado** que existe un monto de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" invoca este caso de uso indicando el estado "cancelado moderadamente".
   - **Entonces** el sistema calcula el 50% del monto de alquiler registrado y solicita la captura y/o liquidación de dicho monto como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico. El registro permanece pendiente hasta la confirmación externa.

2. **Escenario**: Dispersión por cancelación tardía (<24h).
   - **Dado** que existe un monto de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" invoca este caso de uso indicando el estado "cancelado tardíamente".
   - **Entonces** el sistema solicita la captura y/o liquidación del 100% del monto de alquiler registrado como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico. La solicitud no implica que el propietario ya haya recibido los fondos.

---

### Historia de Usuario 2 - Ejecutar la liquidación estándar del monto de alquiler ante la finalización de una reserva (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado corresponde a "completada", quiero recuperar el monto de alquiler, el monto del seguro náutico y el depósito de garantía previamente registrados para esa reserva, calcular la comisión de la plataforma y determinar el pago que corresponde liquidar al Propietario, manteniendo el depósito asociado para su resolución posterior.

**Por qué esta prioridad**: La liquidación del alquiler ocurre al finalizar toda reserva completada; la disputa de garantía es un ciclo separado y no debe bloquear el pago correspondiente al alquiler.

**Prueba Independiente**: Con una reserva que cuenta con monto de alquiler, seguro náutico y depósito de garantía registrados, invocar internamente la dispersión indicando que el estado de la reserva es "completada" y validar que el sistema calcula el pago al Propietario como (monto de alquiler − comisión de la plataforma − monto de seguro náutico), conservando el depósito asociado para su resolución posterior.

**Escenarios de Aceptación**:

1. **Escenario**: Liquidación estándar por reserva completada.
   - **Dado** que existe un monto de alquiler y un monto de seguro náutico previamente registrados para una reserva.
   - **Cuando** "Brindar el estado de la reserva" invoca este caso de uso indicando el estado "completada".
   - **Entonces** el sistema calcula el pago al Propietario como el monto de alquiler registrado menos la comisión de la plataforma menos el monto del seguro náutico registrado, mantiene el depósito asociado a la operación y solicita la captura y/o liquidación del monto correspondiente a la Pasarela de Pago. La liquidación queda pendiente hasta su confirmación externa.

---

### Historia de Usuario 3 - Liquidar el depósito retenido tras una disputa completada (Prioridad: P1)

Como el sistema, al recibir desde "Brindar información de disputa de garantía" el estado `COMPLETADO`, quiero recuperar internamente el depósito fijo cobrado y registrado y solicitar su liquidación total al Propietario, sin recibir montos ni instrucciones de pago desde el Sistema de Reservas y Operaciones.

**Por qué esta prioridad**: La liquidación del alquiler ya ocurre al completar la reserva; esta operación se limita a aplicar la retención total del depósito decidida por la disputa.

**Prueba Independiente**: Con una reserva completada que cuenta con un depósito cobrado y registrado, recibir `COMPLETADO` desde la disputa y validar que el sistema solicita una liquidación idempotente por el depósito completo, vinculada al cobro original y a la disputa.

**Escenarios de Aceptación**:

1. **Escenario**: Retención total del depósito.
   - **Dado** que una disputa fue completada.
   - **Cuando** el sistema recibe la información de disputa.
   - **Entonces** solicita la liquidación del depósito completo al Propietario mediante una operación soportada por la integración configurada, sin asumir que es una transferencia directa.

---

### Historia de Usuario 4 - Registrar el resultado de la dispersión reportado por la Pasarela de Pago (Prioridad: P1)

Como el sistema, al recibir de la Pasarela de Pago el resultado de una operación de captura o liquidación previamente solicitada, quiero registrar su estado, monto confirmado, detalle y referencia externa, de manera que esta información quede disponible para "Consultar registros financieros" y "Consultar informe financiero".

**Por qué esta prioridad**: El registro correcto y confiable del resultado de la dispersión garantiza la trazabilidad financiera de la plataforma frente al propietario y evita reportar como liquidados pagos que la Pasarela de Pago no ejecutó efectivamente.

**Prueba Independiente**: Con una solicitud de captura o liquidación previamente enviada a la Pasarela de Pago, simular resultados completado, en proceso, rechazado, cancelado y expirado, y validar que el sistema registra cada estado sin marcar como completada una operación no confirmada.

**Escenarios de Aceptación**:

1. **Escenario**: La Pasarela de Pago reporta la dispersión como exitosa.
   - **Dado** que existe una solicitud de dispersión previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema que la transferencia fue exitosa.
   - **Entonces** el sistema registra la operación como completada, con el monto confirmado, el detalle y la referencia externa provista, dejándola disponible para "Consultar registros financieros" y "Consultar informe financiero".

2. **Escenario**: La Pasarela de Pago reporta el rechazo o fallo de la dispersión.
   - **Dado** que existe una solicitud de dispersión previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema el rechazo o fallo de la transacción.
   - **Entonces** el sistema registra el estado externo y su detalle, sin marcar la liquidación como completada, dejando este resultado disponible internamente.

---

### Historia de Usuario 5 - Registrar la comisión como un registro financiero independiente al confirmar la liquidación estándar (Prioridad: P1)

Como el sistema, al recibir la confirmación exitosa de la liquidación estándar de una reserva `completada`, quiero crear un `RegistroDeComisión` inmutable e independiente con el monto de comisión previamente calculado y conservado en la solicitud o intención de dispersión, de manera que la comisión quede disponible como una métrica financiera histórica sin recalcularla ni confundirla con la operación de pago al Propietario.

**Por qué esta prioridad**: La comisión se calcula y se cobra como parte de la operación financiera de la reserva antes de resolver la liquidación del alquiler y del depósito. La solicitud o intención conserva el valor calculado como dato operativo no inmutable; el registro inmutable solo debe crearse cuando la Pasarela confirma la dispersión estándar.

**Prueba Independiente**: Ejecutar una liquidación estándar con una comisión calculada en la `IntenciónDeDispersión`, simular una confirmación exitosa de la Pasarela y validar que se crean exactamente un `RegistroDeDispersión` y un `RegistroDeComisión` inmutables con el monto calculado, la reserva, el propietario y la embarcación asociados.

**Escenarios de Aceptación**:

1. **Escenario**: Registro de la comisión aplicada en una liquidación estándar.
   - **Dado** que el sistema calculó la comisión de la plataforma como parte de una liquidación estándar por finalización de reserva.
   - **Cuando** la Pasarela de Pago confirma exitosamente la liquidación estándar.
   - **Entonces** el sistema crea de forma atómica un `RegistroDeDispersión` y un `RegistroDeComisión` por el monto de comisión calculado en la intención, ambos inmutables y relacionados con la reserva, el propietario, la embarcación y la operación de dispersión correspondiente.

2. **Escenario**: Comisión registrada en cero para dispersiones por penalidad de cancelación.
   - **Dado** que el sistema ejecuta una dispersión por el estado de reserva cancelado moderadamente o cancelado tardíamente.
   - **Cuando** el sistema registra el `RegistroDeDispersión` correspondiente.
   - **Entonces** el sistema no genera ningún `RegistroDeComisión`, dado que RF-007 no aplica comisión de la plataforma a estos estados.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si este caso de uso es invocado para una reserva que no cuenta con el monto de alquiler o el monto del seguro náutico previamente registrados?**
   El sistema no ejecuta ningún cálculo parcial ni envía una solicitud a la Pasarela de Pago con un monto asumido; registra internamente un fallo, dado que la validación de existencia de dicha información corresponde previamente a "Brindar el estado de la reserva" o a "Brindar información de disputa de garantía".

- **¿Qué sucede si la disputa informa `COMPLETADO` pero no existe un depósito cobrado y registrado internamente?**
   El sistema trata la información como incompleta y registra el fallo sin enviar una solicitud a la Pasarela de Pago con un monto asumido.

- **¿Por qué la dispersión por penalidad de cancelación (moderada o tardía) no aplica el descuento de comisión de la plataforma ni de seguro náutico, a diferencia de la liquidación estándar?**
   La Matriz de Liquidación distingue "Penalidad por Cancelación" como un concepto propio, separado de "Pago al Propietario": el porcentaje correspondiente (50% o 100% del monto de alquiler) se dispersa íntegramente al propietario como compensación, sin pasar por el cálculo de comisión y seguro que sí aplica a la liquidación estándar de una reserva finalizada.

- **¿Qué diferencia existe entre la liquidación estándar al completar y la liquidación del depósito retenido?**
   La primera se ejecuta al recibir `completada` y calcula Valor Bruto menos Comisión menos Seguro, manteniendo asociado el depósito. La segunda se ejecuta únicamente al recibir `COMPLETADO` desde la disputa y liquida el depósito fijo completo como una operación separada o relacionada.

- **¿Qué sucede si el porcentaje de comisión de la plataforma configurado en los parámetros financieros globales no está disponible al momento de calcular la liquidación estándar?**
  Al no poder completar el cálculo de la comisión, el sistema considera la información necesaria para la liquidación como incompleta. No continúa el cálculo con un valor asumido o parcial; en su lugar, registra el fallo internamente, de la misma forma que ante la ausencia de monto de alquiler o de seguro náutico registrados.

- **¿Qué sucede cuando la Pasarela de Pago está caída, agota el tiempo de espera (*timeout*) o es inalcanzable al momento de enviar la solicitud de dispersión?**
  El sistema no asume ningún resultado; aplica un manejo de errores controlado, registra la solicitud de dispersión con un estado que refleje la falla de comunicación (sin marcarla como exitosa ni como rechazada por la Pasarela) y conserva dicha solicitud disponible para consulta posterior.

- **¿Qué sucede si la Pasarela de Pago reporta más de una vez el resultado de la misma operación de liquidación?**
   El sistema utiliza la clave idempotente y la referencia externa para deduplicar la notificación. Una repetición no crea otra liquidación ni altera un resultado ya confirmado.

- **¿Qué sucede si una confirmación exitosa, un reintento o una notificación duplicada intenta crear dos `RegistroDeComisión` para la misma liquidación estándar?**
   El sistema valida la identidad de la `IntenciónDeDispersión`, la reserva y la operación de dispersión confirmada antes de crear el registro. Si ya existe un `RegistroDeComisión` para esa liquidación, no crea otro ni modifica el existente; registra la repetición para conciliación. La creación del `RegistroDeDispersión` y del `RegistroDeComisión` debe ser atómica: ambos se crean o ninguno queda registrado.

- **¿Qué sucede si se recibe más de una notificación `COMPLETADO` desde la misma disputa?**
   La clave idempotente y la asociación con la reserva impiden crear una segunda liquidación del depósito ya aplicado. La repetición se registra para conciliación.

- **¿Por qué no se crea un `RegistroDeComisión` para las dispersiones por cancelación o para la liquidación del depósito?
   Porque la comisión se calcula y se cobra previamente en la operación de la reserva. El `RegistroDeComisión` solo informa la comisión calculada para la liquidación estándar confirmada y no participa en los cálculos de cobro, reembolso o dispersión.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir, mediante invocación interna desde "Brindar el estado de la reserva" o desde "Brindar información de disputa de garantía", una solicitud de dispersión para una reserva específica, junto con el estado de reserva o la resolución de disputa que la desencadena. No debe recibir montos financieros desde el Sistema de Reservas y Operaciones.
- **RF-002**: El sistema DEBE, cuando el estado sea cancelado moderadamente, calcular el 50% del monto de alquiler previamente registrado para la reserva y solicitarlo como compensación al Propietario.
- **RF-003**: El sistema DEBE, cuando el estado sea cancelado tardíamente, solicitar el 100% del monto de alquiler previamente registrado como compensación al Propietario.
- **RF-004**: El sistema DEBE conservar en la solicitud o intención de dispersión el monto de comisión calculado y cobrado previamente como parte de la operación de la reserva. Este valor es un dato operativo no inmutable y todavía no es un `RegistroDeComisión`.
- **RF-005**: El sistema DEBE, cuando el estado de la reserva sea `completada`, calcular la liquidación estándar del alquiler al Propietario y solicitarla utilizando la comisión calculada en la `IntenciónDeDispersión`. La operación debe conservar y considerar el depósito de garantía asociado a la reserva, aunque el destino del depósito se resuelva posteriormente mediante reembolso o liquidación.
- **RF-006**: El sistema DEBE, cuando reciba `COMPLETADO` desde una disputa, recuperar internamente el depósito cobrado y registrado y solicitar su liquidación total al Propietario mediante una operación consolidada o relacionada según la capacidad de la Pasarela de Pago.
- **RF-007**: El sistema NO DEBE aplicar la comisión de la plataforma ni el descuento del monto de seguro náutico sobre los montos calculados por penalidad de cancelación (RF-002 y RF-003).
- **RF-008**: El sistema DEBE enviar a la Pasarela de Pago la solicitud de captura y/o liquidación por el monto total calculado según el estado o resolución correspondiente, únicamente mediante una capacidad soportada por la integración configurada.
- **RF-009**: El sistema DEBE registrar internamente la `IntenciónDeDispersión` en curso, indicando el estado o resolución que la desencadenó, el monto, el monto de comisión calculado y cobrado previamente cuando corresponda, el depósito de garantía asociado, el cobro original, la capacidad utilizada y una clave idempotente.
- **RF-009A**: El `RegistroDeDispersión` DEBE conservar la referencia a la `IntenciónDeDispersión` que lo originó (`intencionDeDispersiónId`), garantizando trazabilidad directa y coherencia con el vínculo entre comisión e intención.
- **RF-010**: El sistema DEBE recibir de la Pasarela de Pago el resultado de la operación y registrarlo (exitoso o fallido) actualizando siempre el estado en la `IntenciónDeDispersión`. Si la operación es exitosa, el sistema DEBE generar el `RegistroDeDispersión` inmutable de auditoría con su fecha y hora de creación (fechaHoraCreación), monto confirmado, detalle, referencia externa y depósito asociado. Cuando la operación confirmada sea la liquidación estándar, DEBE crear atómicamente el `RegistroDeComisión` inmutable a partir del monto de comisión calculado en la intención.
- **RF-011**: El sistema DEBE dejar disponible el `RegistroDeDispersión` inmutable creado para ser consultado internamente mediante "Consultar registros financieros" y "Consultar informe financiero".
- **RF-012**: El sistema NO DEBE crear un `RegistroDeDispersión` para una dispersión cuya operación fue rechazada, cancelada, expirada o quedó en proceso según la Pasarela de Pago; en esos casos solo actualiza la `IntenciónDeDispersión`.
- **RF-013**: El sistema DEBE registrar internamente un fallo, sin ejecutar ningún cálculo parcial, cuando se invoque este caso de uso para una reserva sin el monto requerido en sus registros internos: alquiler, seguro o depósito cobrado y registrado, según el estado o resolución recibida.
- **RF-014**: El sistema DEBE conservar el monto de comisión calculado en la `IntenciónDeDispersión` para la liquidación estándar y, únicamente tras su confirmación exitosa, debe crear el `RegistroDeComisión` inmutable correspondiente. El monto de comisión no se registra como atributo de `RegistroDeDispersión`.
- **RF-015**: El sistema DEBE registrar, como parte del `RegistroDeDispersión` inmutable, la reserva, el propietario y la embarcación asociados, de manera que el registro pueda consultarse internamente en SPEC 12 y SPEC 13.

- **RF-016**: El sistema DEBE registrar, como parte del `RegistroDeDispersión` inmutable, el monto bruto de alquiler utilizado para el cálculo, el monto de seguro náutico aplicado y el monto de depósito de garantía asociado a la reserva. Toda dispersión debe conservar este componente de depósito, aunque la operación específica determine posteriormente si se reembolsa al Arrendatario o se liquida al Propietario. Estos valores deben conservarse aunque cambien posteriormente los parámetros financieros. Se DEBE garantizar coherencia con la identidad de operación definida en SPEC 07.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de Pago, tanto para la solicitud de dispersión como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto de alquiler, la comisión de la plataforma, el monto del seguro náutico, el monto del depósito de garantía retenido, el monto solicitado a la Pasarela de Pago y el monto transferido registrado, garantizando una precisión interna de 4 decimales antes de cualquier redondeo final hacia la pasarela o reportes.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) tanto para el envío de la solicitud de dispersión como para la recepción de su resultado, dado que "Consultar registros financieros" y "Consultar informe financiero" dependen del resultado registrado por este caso de uso.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3 y actualizada en SPEC 4)**: En este caso de uso es únicamente consultada, para recuperar el monto de alquiler, el monto del seguro y el depósito fijo cobrado y registrado cuando la solicitud se desencadena por una resolución de disputa.
- **IntenciónDeDispersión (Entidad)**: Estructura para representar la operación operativa en curso de captura o liquidación de fondos hacia el Propietario. Conserva el estado o resolución desencadenante, el monto solicitado, el cobro original, la capacidad utilizada, la clave idempotente y el estado actual de la solicitud (en curso, aprobado, fallido, etc.). Esta entidad es la fuente de la verdad para conocer el estado y resultado de la operación.
- **RegistroDeComisión (Entidad Inmutable)**: Métrica financiera informativa e inmutable creada únicamente después de que la Pasarela de Pago confirma exitosamente la liquidación estándar. No participa en cálculos de cobro o pago y no sustituye la comisión calculada en la intención. Conserva la fecha y hora de creación, el monto de comisión calculado, la reserva, el propietario y la embarcación a los que se asocia el descuento, el identificador de la `IntenciónDeDispersión` y la relación directa con el `RegistroDeDispersión` confirmado que la originó.
- **RegistroDeDispersión (Entidad Inmutable)**: Métrica financiera informativa e inmutable creada únicamente cuando la Pasarela de Pago confirma la captura o liquidación como exitosa. No participa en cálculos de cobro o pago; esos cálculos se realizan con la intención y las solicitudes internas. Conserva la fecha y hora de creación (fechaHoraCreación), el monto confirmado, la referencia externa, el monto bruto de alquiler, el monto de seguro náutico aplicado, el monto de depósito asociado, la reserva, el propietario, la embarcación relacionados y la referencia obligatoria a la intención que lo originó (`intencionDeDispersiónId`). Es consumida posteriormente por "Consultar registros financieros" y "Consultar informe financiero" (SPEC 13).
- **SolicitudDispersión (DTO)**: Información recibida internamente desde "Brindar el estado de la reserva" o "Brindar información de disputa de garantía". Contiene el identificador de la reserva, el estado o resolución desencadenante, el monto de comisión calculado y cobrado previamente cuando corresponda y el depósito de garantía asociado. El monto de comisión de la solicitud es operativo y no constituye todavía un registro inmutable.
- **SolicitudDispersiónPasarela (DTO)**: Información enviada a la Pasarela de Pago. Contiene el tipo de operación, el monto total, el componente de depósito de garantía asociado a la reserva, la referencia del cobro original, la referencia de la reserva y la clave idempotente.
- **ResultadoDispersiónPasarela (DTO)**: Información recibida desde la Pasarela de Pago como resultado de una operación previamente iniciada. Contiene el estado externo, su detalle, el monto confirmado y la referencia externa asignada por la Pasarela de Pago.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los montos de dispersión solicitados a la Pasarela de Pago corresponden exactamente al monto que corresponde según el estado o resolución (50%/100% del monto de alquiler para cancelaciones, liquidación estándar para estado completada o depósito completo para disputa completada), con cero (0) errores de cálculo detectados en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "0 `RegistroDeComisión` creados para dispersiones por cancelación o liquidaciones de depósito, y 100% de las liquidaciones estándar por estado completada utilizan exactamente la comisión calculada y el seguro correspondientes".
- **CE-003**: Trazabilidad, "100% de las solicitudes de liquidación enviadas a la Pasarela de Pago quedan registradas internamente, y 100% de los resultados recibidos actualizan dicho registro, dejándolo disponible para 'Consultar registros financieros' y 'Consultar informe financiero'". Verificar trazabilidad intención a registro (RegistroDeDispersión vincula intencionDeDispersiónId) y coherencia del vínculo entre comisión e intención.
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de Pago, tanto al enviar la solicitud de dispersión como al recibir su resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones de dispersión en un estado indefinido".
- **CE-005**: Integridad de la Dispersión, "0% de las transacciones rechazadas o fallidas reportadas por la Pasarela de Pago son registradas o reportadas como dispersiones exitosas".
- **CE-006**: Trazabilidad de Comisión, "100% de las liquidaciones estándar confirmadas crean exactamente un `RegistroDeComisión` inmutable relacionado con un único `RegistroDeDispersión`, la reserva, el propietario y la embarcación correspondientes, con un monto igual al calculado en la `IntenciónDeDispersión` y cero (0) duplicados detectados en pruebas automatizadas".