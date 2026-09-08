# Especificación de Funcionalidad: UC10 - Dispersar Fondos de Alquiler

**Creado**: 2026-09-06 

> **Nota de trazabilidad**: Este caso de uso es invocado internamente por dos operaciones distintas del sistema: "Brindar el estado de la reserva" (SPEC 7), cuando el estado informado de una reserva corresponde a una **cancelación moderada**, a una **cancelación tardía/No-Show**, o a la **finalización de la reserva sin incidentes** ("completada sin incidentes"); y "Resolver disputa de garantía" (SPEC 8), en los **tres resultados posibles** de la disputa (liberación total, retención total o retención parcial), indicando en cada caso el monto del depósito de garantía retenido que corresponda (incluyendo el valor cero en la liberación total). El sistema no expone este caso de uso a ningún actor externo ni al Sistema de Reservas y Operaciones: es la Pasarela de Pago quien ejecuta técnicamente la transferencia de fondos al Propietario y reporta su resultado. El resultado registrado por este caso de uso queda disponible para "Consultar registros financieros" (SPEC 12), "Consultar ingresos" (SPEC 14, pendiente) y, a partir de esta versión, para "Consultar balance financiero" (SPEC 13), en lo relativo al monto de comisión efectivamente aplicado.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Dispersar fondos al propietario por penalidad de cancelación (moderada o tardía/No-Show) (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado de una reserva corresponde a una cancelación moderada o a una cancelación tardía/No-Show, quiero recuperar el monto de alquiler previamente registrado para esa reserva y calcular el porcentaje de compensación que corresponde según la ventana de cancelación informada, de manera que pueda solicitar a la Pasarela de Pago la transferencia de dicho monto al Propietario.

**Por qué esta prioridad**: Sin este cálculo y solicitud correctos, el propietario no recibiría la compensación que le corresponde por una cancelación moderada o tardía/No-Show, incumpliendo directamente la lógica de cancelaciones definida para la plataforma.

**Prueba Independiente**: Con una reserva que cuenta con un monto de alquiler previamente registrado, invocar internamente la dispersión indicando cada una de las dos ventanas de cancelación que la disparan (moderada y tardía/No-Show) y validar que el sistema calcula el monto correspondiente (50% o 100% del monto de alquiler registrado, sin ningún descuento adicional) y lo solicita a la Pasarela de Pago.

**Escenarios de Aceptación**:

1. **Escenario**: Dispersión por cancelación moderada (72h–24h).
   - **Dado** que existe un monto de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" invoca este caso de uso indicando que la reserva fue cancelada moderadamente.
   - **Entonces** el sistema calcula el 50% del monto de alquiler registrado y solicita dicho monto a la Pasarela de Pago como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico.

2. **Escenario**: Dispersión por cancelación tardía / No-Show (<24h).
   - **Dado** que existe un monto de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" invoca este caso de uso indicando que la reserva fue cancelada tardíamente o marcada como No-Show.
   - **Entonces** el sistema solicita a la Pasarela de Pago la transferencia del 100% del monto de alquiler registrado como compensación al Propietario, sin aplicar comisión de la plataforma ni descuento de seguro náutico.

---

### Historia de Usuario 2 - Ejecutar la liquidación estándar del valor de alquiler ante la finalización de una reserva sin incidentes (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado de una reserva corresponde a "completada sin incidentes", quiero recuperar el monto de alquiler y el monto del seguro náutico previamente registrados para esa reserva, calcular la comisión de la plataforma aplicando el porcentaje configurado en los parámetros financieros globales, y determinar el pago que corresponde liquidar al Propietario, de manera que pueda solicitar dicho monto a la Pasarela de Pago.

**Por qué esta prioridad**: La mayoría de las reservas finalizan sin incidentes, por lo que la liquidación oportuna y correcta del valor de alquiler al propietario es indispensable para el funcionamiento normal de la plataforma, sin depender de una evaluación del Administrador Financiero que en este caso no aplica.

**Prueba Independiente**: Con una reserva que cuenta con un monto de alquiler y un monto de seguro náutico previamente registrados, invocar internamente la dispersión indicando que el estado de la reserva es "completada sin incidentes" y validar que el sistema calcula el pago al propietario como (monto de alquiler − comisión de la plataforma − monto de seguro náutico) y lo solicita a la Pasarela de Pago, sin incluir ningún monto de depósito de garantía.

**Escenarios de Aceptación**:

1. **Escenario**: Liquidación estándar por finalización sin incidentes.
   - **Dado** que existe un monto de alquiler y un monto de seguro náutico previamente registrados para una reserva.
   - **Cuando** "Brindar el estado de la reserva" invoca este caso de uso indicando el origen "finalización sin incidentes".
   - **Entonces** el sistema calcula el pago al Propietario como el monto de alquiler registrado menos la comisión de la plataforma menos el monto del seguro náutico registrado, y solicita dicho monto a la Pasarela de Pago, sin incluir ningún monto de depósito de garantía, dado que este fue reembolsado íntegramente al arrendatario mediante "Reembolsar dinero a arrendatario" para ese mismo origen.

---

### Historia de Usuario 3 - Ejecutar la liquidación estándar consolidada con el tratamiento del depósito de garantía, ante la resolución de una disputa de garantía (Prioridad: P1)

Como el sistema, al ser invocado mediante la resolución de "Resolver disputa de garantía" para cualquiera de sus tres resultados posibles (liberación total, retención total o retención parcial), quiero recuperar el monto de alquiler y el monto del seguro náutico previamente registrados, calcular la comisión de la plataforma, determinar el pago estándar al Propietario y sumarle el monto del depósito de garantía retenido indicado (incluyendo el valor cero en la liberación total), de manera que pueda solicitar a la Pasarela de Pago una única transferencia consolidada.

**Por qué esta prioridad**: Sin esta ejecución consolidada, una reserva "completada con incidentes" quedaría sin ningún disparador que le pague al propietario el valor de alquiler que le corresponde, sin importar el resultado de la disputa sobre el depósito.

**Prueba Independiente**: Con una reserva que cuenta con un monto de alquiler, un monto de seguro náutico y un depósito de garantía previamente registrados, invocar internamente la dispersión indicando cada uno de los tres resultados posibles de la disputa y validar que el sistema calcula el pago estándar al propietario, le suma el monto de depósito retenido correspondiente en cada caso, y solicita el monto total consolidado a la Pasarela de Pago exactamente una vez.

**Escenarios de Aceptación**:

1. **Escenario**: Liberación total del depósito (el reclamo no procede).
   - **Dado** que "Resolver disputa de garantía" determinó la liberación total del depósito de garantía de una reserva.
   - **Cuando** dicho caso de uso invoca la dispersión indicando un monto de depósito retenido igual a cero.
   - **Entonces** el sistema solicita a la Pasarela de Pago únicamente el pago estándar al Propietario (monto de alquiler menos comisión menos seguro náutico), sin ningún monto adicional por depósito.

2. **Escenario**: Retención total del depósito (el reclamo procede totalmente).
   - **Dado** que "Resolver disputa de garantía" determinó la retención total del depósito de garantía de una reserva.
   - **Cuando** dicho caso de uso invoca la dispersión indicando el 100% del depósito de garantía registrado como monto retenido.
   - **Entonces** el sistema solicita a la Pasarela de Pago el pago estándar al Propietario más el 100% del depósito de garantía retenido, consolidados en una única transferencia.

3. **Escenario**: Retención parcial del depósito (el reclamo procede parcialmente).
   - **Dado** que "Resolver disputa de garantía" determinó la retención parcial del depósito de garantía de una reserva, indicando el monto a retener.
   - **Cuando** dicho caso de uso invoca la dispersión indicando el monto parcial retenido.
   - **Entonces** el sistema solicita a la Pasarela de Pago el pago estándar al Propietario más el monto parcial del depósito retenido, consolidados en una única transferencia.

---

### Historia de Usuario 4 - Registrar el resultado de la dispersión reportado por la Pasarela de Pago (Prioridad: P1)

Como el sistema, al recibir de la Pasarela de Pago el resultado de una transacción de dispersión previamente solicitada, quiero registrar dicho resultado (éxito o fallo) junto con el monto transferido y la referencia externa provista, de manera que esta información quede disponible para "Consultar registros financieros" y "Consultar ingresos".

**Por qué esta prioridad**: El registro correcto y confiable del resultado de la dispersión garantiza la trazabilidad financiera de la plataforma frente al propietario y evita reportar como liquidados pagos que la Pasarela de Pago no ejecutó efectivamente.

**Prueba Independiente**: Con una solicitud de dispersión previamente enviada a la Pasarela de Pago (sin importar su origen), simular la recepción de un resultado (éxito y, por separado, fallo) y validar que el sistema registra internamente el resultado correspondiente junto con la referencia externa, sin marcar como exitosas las dispersiones fallidas.

**Escenarios de Aceptación**:

1. **Escenario**: La Pasarela de Pago reporta la dispersión como exitosa.
   - **Dado** que existe una solicitud de dispersión previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema que la transferencia fue exitosa.
   - **Entonces** el sistema registra la dispersión como exitosa, con el monto transferido y la referencia externa provista, dejándola disponible para "Consultar registros financieros" y "Consultar ingresos".

2. **Escenario**: La Pasarela de Pago reporta el rechazo o fallo de la dispersión.
   - **Dado** que existe una solicitud de dispersión previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema el rechazo o fallo de la transacción.
   - **Entonces** el sistema registra la dispersión como fallida, sin marcarla como transferida exitosamente, dejando este resultado disponible internamente.

---

### Historia de Usuario 5 - Registrar el monto de comisión efectivamente aplicado en la liquidación estándar (Prioridad: P1) 

Como el sistema, al calcular la liquidación estándar del valor de alquiler al Propietario (orígenes "finalización sin incidentes" o resultado de disputa de garantía), quiero registrar, dentro del mismo `RegistroDeDispersión`, el monto de comisión de la plataforma efectivamente aplicado en ese cálculo, de manera que dicho valor quede disponible para "Consultar balance financiero" sin necesidad de recalcularlo posteriormente con el porcentaje de comisión que esté vigente en el momento de la consulta, el cual pudo haber cambiado desde entonces.

**Por qué esta prioridad**: "Configurar parámetros financieros globales" (SPEC 11) permite modificar el porcentaje de comisión en cualquier momento, y dicha modificación no afecta los cálculos ya realizados (RF-009 de SPEC 11). Si la comisión aplicada no se conserva junto con la dispersión en la que fue calculada, no existiría ninguna fuente confiable para reconstruir cuánta comisión generó realmente cada transacción histórica.

**Prueba Independiente**: Ejecutar la liquidación estándar para una reserva con un porcentaje de comisión vigente determinado, modificar posteriormente dicho porcentaje mediante "Configurar parámetros financieros globales", y validar que el monto de comisión registrado en el `RegistroDeDispersión` original permanece igual al calculado en el momento original, sin verse afectado por el nuevo porcentaje vigente.

**Escenarios de Aceptación**:

1. **Escenario**: Registro de la comisión aplicada en una liquidación estándar.
   - **Dado** que el sistema calculó la comisión de la plataforma como parte de una liquidación estándar (finalización sin incidentes o resultado de disputa de garantía).
   - **Cuando** el sistema registra el `RegistroDeDispersión` correspondiente.
   - **Entonces** el sistema incluye en dicho registro el monto de comisión efectivamente aplicado en ese cálculo, de forma inmutable frente a futuros cambios del porcentaje de comisión vigente.

2. **Escenario**: Comisión registrada en cero para dispersiones por penalidad de cancelación.
   - **Dado** que el sistema ejecuta una dispersión con origen cancelación moderada o cancelación tardía/No-Show.
   - **Cuando** el sistema registra el `RegistroDeDispersión` correspondiente.
   - **Entonces** el sistema registra el monto de comisión aplicado como cero, dado que RF-007 no aplica comisión de la plataforma a estos orígenes.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si este caso de uso es invocado para una reserva que no cuenta con el monto de alquiler o el monto del seguro náutico previamente registrados?**
  El sistema no ejecuta ningún cálculo parcial ni envía una solicitud a la Pasarela de Pago con un monto asumido; registra internamente un fallo, dado que la validación de existencia de dicha información corresponde previamente a "Brindar el estado de la reserva" o a "Resolver disputa de garantía", que son los casos de uso que invocan esta operación.

- **¿Qué sucede si el origen es un resultado de resolución de disputa de garantía pero no se indica el monto del depósito de garantía retenido (incluyendo el valor cero)?**
  Sin este dato el sistema no puede consolidar correctamente la liquidación estándar con el tratamiento del depósito. Trata la solicitud como incompleta y registra el fallo internamente, de forma equivalente al caso anterior, sin enviar una solicitud a la Pasarela de Pago con un monto asumido.

- **¿Por qué la dispersión por penalidad de cancelación (moderada o tardía/No-Show) no aplica el descuento de comisión de la plataforma ni de seguro náutico, a diferencia de la liquidación estándar?**
  La Matriz de Liquidación distingue "Penalidad por Cancelación" como un concepto propio, separado de "Pago al Propietario": el porcentaje correspondiente (50% o 100% del valor del alquiler) se dispersa íntegramente al propietario como compensación, sin pasar por el cálculo de comisión y seguro que sí aplica a la liquidación estándar de una reserva finalizada.

- **¿Qué diferencia existe entre la liquidación estándar dispersada por "finalización sin incidentes" y la dispersada tras la resolución de una disputa de garantía con liberación total del depósito?**
  En ambos casos el monto de depósito adicional es cero y el pago estándar al propietario se calcula de la misma manera, pero difieren en su origen: "finalización sin incidentes" es un disparo directo desde "Brindar el estado de la reserva", sin que exista evaluación alguna del Administrador Financiero ni ejecución de "Resolver disputa de garantía"; la liberación total, en cambio, es el resultado de una disputa evaluada por el Administrador Financiero sobre una reserva previamente informada como "completada con incidentes".

- **¿Qué sucede si el porcentaje de comisión de la plataforma configurado en los parámetros financieros globales no está disponible al momento de calcular la liquidación estándar?**
  Al no poder completar el cálculo de la comisión, el sistema considera la información necesaria para la liquidación como incompleta. No continúa el cálculo con un valor asumido o parcial; en su lugar, registra el fallo internamente, de la misma forma que ante la ausencia de monto de alquiler o de seguro náutico registrados.

- **¿Qué sucede cuando la Pasarela de Pago está caída, agota el tiempo de espera (*timeout*) o es inalcanzable al momento de enviar la solicitud de dispersión?**
  El sistema no asume ningún resultado; aplica un manejo de errores controlado, registra la solicitud de dispersión con un estado que refleje la falla de comunicación (sin marcarla como exitosa ni como rechazada por la Pasarela) y conserva dicha solicitud disponible para consulta posterior.

- **¿Qué sucede si la Pasarela de Pago reporta más de una vez el resultado de la misma transacción de dispersión (por ejemplo, una notificación repetida)?**
  El contexto no define un mecanismo de deduplicación explícito. El sistema conserva el resultado ya registrado para esa solicitud de dispersión; una notificación repetida con el mismo resultado no altera el registro existente.

- **¿Qué sucede si "Resolver disputa de garantía" resuelve nuevamente la disputa de una misma reserva, invocando otra vez la dispersión con un monto de depósito retenido distinto al previamente registrado?**
  Dado que dicho caso de uso aplica su tratamiento sobre el resultado más reciente informado, este caso de uso recalcula el monto total a dispersar a partir de la información vigente en ese momento (monto retenido actualizado) y registra una nueva solicitud de dispersión, solicitando nuevamente el monto correspondiente a la Pasarela de Pago. El monto de comisión aplicado se recalcula igualmente con el porcentaje vigente en ese nuevo momento y se registra en el nuevo `RegistroDeDispersión`, sin alterar el monto de comisión ya registrado en dispersiones previas de la misma reserva.

- **¿Por qué se registra explícitamente `comisiónAplicada = 0` en las dispersiones por penalidad de cancelación, en vez de dejar el campo vacío? 
  Porque RF-007 establece que estos orígenes no aplican comisión de la plataforma. Registrar explícitamente el valor cero, en lugar de dejarlo vacío o nulo, evita ambigüedad al sumar este campo en "Consultar balance financiero" (SPEC 13), que necesita agregar `comisiónAplicada` de todas las dispersiones exitosas dentro de un período sin distinguir su origen.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir, mediante invocación interna desde "Brindar el estado de la reserva" o desde "Resolver disputa de garantía", una solicitud de dispersión de fondos para una reserva específica, identificada por su identificador de reserva, junto con el origen de la solicitud (cancelación moderada, cancelación tardía/No-Show, finalización sin incidentes, o resultado de disputa de garantía: liberación total, retención total o retención parcial) y, cuando el origen sea un resultado de disputa de garantía, el monto del depósito de garantía retenido correspondiente (incluyendo el valor cero).
- **RF-002**: El sistema DEBE, cuando el origen sea cancelación moderada, calcular el 50% del monto de alquiler previamente registrado para la reserva y solicitar dicho monto a la Pasarela de Pago como compensación al Propietario.
- **RF-003**: El sistema DEBE, cuando el origen sea cancelación tardía o No-Show, solicitar a la Pasarela de Pago el 100% del monto de alquiler previamente registrado como compensación al Propietario.
- **RF-004**: El sistema DEBE calcular la comisión de la plataforma aplicando, sobre el monto de alquiler previamente registrado, el porcentaje de comisión configurado en los parámetros financieros globales, para su uso en el cálculo de la liquidación estándar (RF-005 y RF-006).
- **RF-005**: El sistema DEBE, cuando el origen sea finalización sin incidentes, calcular la liquidación estándar del valor de alquiler al Propietario (monto de alquiler registrado menos la comisión de la plataforma menos el monto del seguro náutico registrado) y solicitar dicho monto a la Pasarela de Pago, sin incluir ningún monto de depósito de garantía.
- **RF-006**: El sistema DEBE, cuando el origen sea un resultado de resolución de disputa de garantía, calcular la liquidación estándar del valor de alquiler al Propietario (monto de alquiler registrado menos la comisión de la plataforma menos el monto del seguro náutico registrado) y sumarle el monto del depósito de garantía retenido indicado (incluyendo el valor cero cuando el resultado sea liberación total), solicitando a la Pasarela de Pago el monto total consolidado en una única transferencia.
- **RF-007**: El sistema NO DEBE aplicar la comisión de la plataforma ni el descuento del monto de seguro náutico sobre los montos calculados por penalidad de cancelación (RF-002 y RF-003).
- **RF-008**: El sistema DEBE enviar a la Pasarela de Pago la solicitud de transferencia por el monto total calculado según el origen correspondiente.
- **RF-009**: El sistema DEBE registrar internamente la solicitud de dispersión en curso, indicando el origen y el monto solicitado, mientras se espera el resultado de la Pasarela de Pago.
- **RF-010**: El sistema DEBE recibir de la Pasarela de Pago el resultado de una transacción de dispersión previamente iniciada, registrando internamente el monto transferido, el estado de la transacción (éxito o fallo) y la referencia externa provista.
- **RF-011**: El sistema DEBE dejar disponible el resultado registrado de la dispersión para ser consultado mediante "Consultar registros financieros" y "Consultar ingresos".
- **RF-012**: El sistema NO DEBE registrar ni reportar como exitosa una dispersión cuya transacción fue rechazada o fallida según la Pasarela de Pago.
- **RF-013**: El sistema DEBE registrar internamente un fallo, sin ejecutar ningún cálculo parcial, cuando se invoque este caso de uso para una reserva sin el monto de alquiler, el monto de seguro náutico, o —cuando el origen sea disputa de garantía— el monto de depósito retenido previamente indicados, dado que este caso de uso no cuenta con un canal de respuesta hacia quien lo invoca.
- **RF-014**  El sistema DEBE registrar, como parte de `RegistroDeDispersión`, el monto de comisión de la plataforma efectivamente aplicado (calculado según RF-004) cuando el origen sea finalización sin incidentes o resultado de disputa de garantía, dejando dicho valor disponible, de forma inmutable frente a cambios posteriores del porcentaje de comisión vigente, para "Consultar balance financiero" (SPEC 13). Para los orígenes de penalidad de cancelación (cancelación moderada o cancelación tardía/No-Show), en los que RF-007 no aplica comisión, este campo DEBE registrarse explícitamente con el valor cero.
- **RF-015**: El sistema DEBE registrar, como parte del `RegistroDeDispersión`, el propietario y la embarcación asociados a la reserva (copiados de `InformaciónDeReserva`), de manera que dicho registro quede asociado a su propietario y pueda ser consultado en "Consultar registros financieros" (SPEC 12) y "Consultar ingresos" (SPEC 14).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de Pago, tanto para la solicitud de dispersión como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto de alquiler, la comisión de la plataforma, el monto del seguro náutico, el monto del depósito de garantía retenido, el monto solicitado a la Pasarela de Pago y el monto transferido registrado.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) tanto para el envío de la solicitud de dispersión como para la recepción de su resultado, dado que "Consultar registros financieros" y "Consultar ingresos" dependen del resultado registrado por este caso de uso.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3, actualizada en SPEC 4, SPEC 7 y SPEC 8)**: En este caso de uso es únicamente consultada, para recuperar el monto de alquiler y el monto del seguro náutico registrados (todos los orígenes), y el monto del depósito de garantía retenido (cuando el origen sea un resultado de disputa de garantía).
- **RegistroDeDispersión **: Estructura gestionada y persistida internamente por el sistema para representar el ciclo de una transacción de dispersión de fondos hacia el Propietario. Es creada con el origen de la solicitud (cancelación moderada, cancelación tardía/No-Show, finalización sin incidentes, o el resultado correspondiente de disputa de garantía), el monto solicitado a la Pasarela de Pago **y, a partir de esta versión, el monto de comisión de la plataforma efectivamente aplicado en ese cálculo (`comisiónAplicada`, registrado en cero para los orígenes de penalidad de cancelación)**; y es actualizada con el monto transferido, el estado final de la transacción (éxito o fallo) y la referencia externa provista por la Pasarela de Pago. Conserva también el propietario y la embarcación asociados a la reserva (copiados de `InformaciónDeReserva`), de manera que quede asociado a su propietario. Es consumida posteriormente por "Consultar registros financieros", "Consultar ingresos" y, en lo relativo a `comisiónAplicada`, por "Consultar balance financiero" (SPEC 13).
- **SolicitudDispersión (DTO)**: Información recibida internamente desde "Brindar el estado de la reserva" o "Resolver disputa de garantía" para esta operación. Contiene el identificador de la reserva, el origen de la solicitud y, cuando el origen sea un resultado de disputa de garantía, el monto del depósito de garantía retenido.
- **SolicitudDispersiónPasarela (DTO)**: Información enviada a la Pasarela de Pago. Contiene el monto total a transferir y la referencia de la reserva asociada.
- **ResultadoDispersiónPasarela (DTO)**: Información recibida desde la Pasarela de Pago como resultado de una transacción de dispersión previamente iniciada. Contiene el estado de la transacción (éxito o fallo) y la referencia externa asignada por la Pasarela de Pago.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los montos de dispersión solicitados a la Pasarela de Pago corresponden exactamente al monto que corresponde según el origen (50%/100% del monto de alquiler para penalidades, o liquidación estándar ± depósito retenido para finalización sin incidentes/disputa), con cero (0) errores de cálculo detectados en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-002**: Cumplimiento Arquitectónico, "0 aplicaciones de comisión de la plataforma o de descuento de seguro náutico registradas en dispersiones por penalidad de cancelación, y 100% de las liquidaciones estándar (finalización sin incidentes o disputa de garantía) descuentan exactamente la comisión y el seguro náutico correspondientes".
- **CE-003**: Trazabilidad, "100% de las solicitudes de dispersión enviadas a la Pasarela de Pago quedan registradas internamente, y 100% de los resultados recibidos (éxito o fallo) actualizan dicho registro, dejándolo disponible para 'Consultar registros financieros' y 'Consultar ingresos'".
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de Pago, tanto al enviar la solicitud de dispersión como al recibir su resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones de dispersión en un estado indefinido".
- **CE-005**: Integridad de la Dispersión, "0% de las transacciones rechazadas o fallidas reportadas por la Pasarela de Pago son registradas o reportadas como dispersiones exitosas".
- **CE-006**: Trazabilidad de Comisión, "100% de las dispersiones con origen finalización sin incidentes o resultado de disputa de garantía registran el monto de comisión efectivamente aplicado (`comisiónAplicada`), coincidiendo exactamente con el valor calculado en RF-004 en el momento de esa dispersión, con cero (0) discrepancias detectadas en pruebas automatizadas frente a cambios posteriores del porcentaje de comisión vigente, quedando dicho valor disponible para 'Consultar balance financiero'".