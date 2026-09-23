# Especificación de Funcionalidad: UC09 - Reembolsar Dinero a Arrendatario

**Creado**: 2026-09-06 (v2 — actualizado tras la incorporación de los estados "completada sin incidentes" y "completada con incidentes" en el Módulo de Reservas y Operaciones)

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Solicitar el reembolso ante una cancelación de reserva (flexible o moderada) (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado de una reserva corresponde a una cancelación flexible o moderada, quiero recuperar el valor de alquiler previamente registrado para esa reserva y calcular el monto a devolver según la ventana de cancelación informada, de manera que pueda solicitar a la Pasarela de Pago la devolución del monto correspondiente.

**Por qué esta prioridad**: Sin este cálculo y solicitud correctos, el arrendatario no recibiría el reembolso que le corresponde según la ventana de cancelación en la que se encuentre la reserva, incumpliendo directamente la lógica de cancelaciones definida para la plataforma.

**Prueba Independiente**: Con una reserva que cuenta con un valor de alquiler previamente calculado y registrado, invocar internamente el reembolso indicando cada una de las dos ventanas de cancelación (flexible y moderada) y validar que el sistema calcula el monto correspondiente (100% o 50% del valor de alquiler registrado) y lo solicita a la Pasarela de Pago.

**Escenarios de Aceptación**:

1. **Escenario**: Reembolso por cancelación flexible (>72h).
   - **Dado** que existe un valor de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada flexiblemente.
  - **Entonces** el sistema solicita la liberación del importe autorizado o, si ya fue capturado, un reembolso del 100% del valor de alquiler registrado. La operación queda pendiente hasta recibir confirmación externa.

2. **Escenario**: Reembolso por cancelación moderada (72h–24h).
   - **Dado** que existe un valor de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada moderadamente.
  - **Entonces** el sistema calcula el 50% del valor de alquiler registrado y solicita la liberación o el reembolso de dicho monto según el estado del cobro original. La operación queda pendiente hasta recibir confirmación externa.

---

### Historia de Usuario 2 - Solicitar el reembolso del depósito de garantía ante la finalización de una reserva sin incidentes (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado de una reserva corresponde a "completada sin incidentes", quiero recuperar el depósito de garantía previamente registrado para esa reserva y solicitar a la Pasarela de Pago su devolución íntegra, de manera que el depósito quede liberado al arrendatario sin necesidad de que se ejecute una resolución de disputa.

**Por qué esta prioridad**: La mayoría de las reservas finalizan sin incidentes, por lo que la liberación oportuna del depósito es indispensable para no retener el dinero del arrendatario cuando no existe ningún reclamo sobre daños, sin depender de una evaluación del Administrador Financiero que en este caso no aplica.

**Prueba Independiente**: Con una reserva que cuenta con un depósito de garantía previamente registrado, invocar internamente el reembolso indicando que el estado de la reserva es "completada sin incidentes" y validar que el sistema solicita a la Pasarela de Pago la devolución del 100% del depósito registrado, sin invocar "Resolver disputa de garantía".

**Escenarios de Aceptación**:

1. **Escenario**: Liberación del depósito por finalización sin incidentes.
   - **Dado** que existe un depósito de garantía previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue completada sin incidentes.
  - **Entonces** el sistema solicita la liberación del importe autorizado correspondiente al depósito o, si fue capturado, su reembolso íntegro, sin ejecutar "Resolver disputa de garantía" para esa reserva. La operación queda pendiente hasta recibir confirmación externa.

---

### Historia de Usuario 3 - Solicitar el reembolso ante la resolución de una disputa de garantía (liberación total o parcial) (Prioridad: P1)

Como el sistema, al ser invocado mediante `<<extend>>` desde "Resolver disputa de garantía" —ejecutada por el Administrador Financiero sobre una reserva informada como "completada con incidentes"— cuando el resultado de la disputa favorece total o parcialmente al arrendatario, quiero recuperar el depósito de garantía previamente registrado (y el monto retenido cuando aplique) y calcular el monto a devolver, de manera que pueda solicitar a la Pasarela de Pago la devolución correspondiente.

**Por qué esta prioridad**: Sin este cálculo, el depósito de garantía quedaría sin liberarse correctamente al arrendatario cuando la disputa lo favorece, contradiciendo la decisión ya tomada por el Administrador Financiero.

**Prueba Independiente**: Con una reserva "completada con incidentes" que cuenta con un depósito de garantía previamente registrado, invocar el reembolso para cada uno de los dos resultados de disputa que favorecen al arrendatario (liberación total, retención parcial) y validar que el sistema calcula el monto correcto (100% del depósito, o el depósito registrado menos el monto retenido) y lo solicita a la Pasarela de Pago.

**Escenarios de Aceptación**:

1. **Escenario**: Reembolso por liberación total del depósito (el reclamo no procede).
   - **Dado** que existe un depósito de garantía previamente registrado para una reserva "completada con incidentes".
   - **Cuando** "Resolver disputa de garantía" extiende hacia este caso de uso indicando liberación total.
  - **Entonces** el sistema solicita la liberación o el reembolso del 100% del depósito de garantía registrado, según corresponda al estado del cobro original.

2. **Escenario**: Reembolso por retención parcial del depósito (el reclamo procede parcialmente).
   - **Dado** que existe un depósito de garantía previamente registrado y un monto retenido determinado por el Administrador Financiero.
   - **Cuando** "Resolver disputa de garantía" extiende hacia este caso de uso indicando retención parcial.
  - **Entonces** el sistema calcula el monto restante (depósito registrado menos el monto retenido) y solicita su liberación o reembolso según corresponda.

---

### Historia de Usuario 4 - Registrar el resultado del reembolso reportado por la Pasarela de Pago (Prioridad: P1)

Como el sistema, al recibir de la Pasarela de Pago el resultado de una operación de liberación o reembolso previamente solicitada, quiero registrar su estado, monto confirmado y referencia externa, de manera que esta información quede disponible para "Consultar registros financieros".

**Por qué esta prioridad**: El registro correcto y confiable del resultado del reembolso garantiza la trazabilidad financiera de la plataforma frente al arrendatario y evita reportar como devueltos reembolsos que la Pasarela de Pago no ejecutó efectivamente.

**Prueba Independiente**: Con una solicitud de liberación o reembolso previamente enviada a la Pasarela de Pago, simular resultados completado, en proceso, rechazado, cancelado y expirado, y validar que el sistema registra cada estado y no marca como completada una operación no confirmada.

**Escenarios de Aceptación**:

1. **Escenario**: La Pasarela de Pago reporta el reembolso como exitoso.
   - **Dado** que existe una solicitud de reembolso previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema que la devolución fue exitosa.
  - **Entonces** el sistema registra la operación como completada, con el monto confirmado, el detalle y la referencia externa provista, dejándola disponible para "Consultar registros financieros".

2. **Escenario**: La Pasarela de Pago reporta el rechazo o fallo del reembolso.
   - **Dado** que existe una solicitud de reembolso previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema el rechazo o fallo de la transacción.
  - **Entonces** el sistema registra el estado externo y su detalle, sin marcar la operación como completada, dejando este resultado disponible internamente.

### Casos Extremos (Edge Cases)

- **¿Quién aplica el descuento por costos transaccionales en una cancelación flexible (>72h)?**
  El sistema no debe inferir cómo se aplican los costos transaccionales. Solicita el monto definido por la política de negocio y registra cualquier costo o monto neto reportado por la Pasarela de Pago, sin asumir que esta lo absorbe o lo descuenta automáticamente.

- **¿Qué diferencia existe entre el reembolso por "completada sin incidentes" y el reembolso por liberación total del depósito tras una disputa de garantía?**
  Ambos resultan en la devolución del 100% del depósito de garantía registrado, pero difieren en su origen: "completada sin incidentes" es un reembolso directo, disparado por "Brindar el estado de la reserva" sin que exista evaluación alguna del Administrador Financiero ni ejecución de "Resolver disputa de garantía"; la liberación total, en cambio, es el resultado de una disputa evaluada por el Administrador Financiero sobre una reserva previamente informada como "completada con incidentes", en la que se determina que el reclamo no procede.

- **¿Qué sucede cuando la Pasarela de Pago está caída, agota el tiempo de espera (*timeout*) o es inalcanzable al momento de enviar la solicitud de reembolso?**
  Conforme a RNF-003, el sistema no asume ningún resultado; aplica un manejo de errores controlado, registra la solicitud de reembolso con un estado que refleje la falla de comunicación (sin marcarla como exitosa ni como rechazada por la Pasarela) y conserva dicha solicitud disponible para consulta posterior.

- **¿Qué sucede si este caso de uso es invocado para una reserva que no cuenta con el valor de alquiler o el depósito de garantía previamente registrado, según corresponda al origen de la solicitud?**
  El sistema no ejecuta ningún cálculo parcial ni envía una solicitud a la Pasarela de Pago con un monto asumido; registra internamente un fallo, dado que la validación de existencia de dicha información corresponde previamente a "Brindar el estado de la reserva" (RF-008) o a "Resolver disputa de garantía" (RF-007), que son los casos de uso que invocan esta operación.

- **¿Qué sucede si "Brindar el estado de la reserva" informa "completada sin incidentes" para una reserva que ya había sido informada como "completada con incidentes" (o viceversa)?**
  El contexto no define un mecanismo de corrección entre ambos estados de finalización. Al tratarse de clasificaciones mutuamente excluyentes del cierre de la reserva, informadas por el Sistema de Reservas y Operaciones, el sistema aplica el tratamiento correspondiente al estado más reciente informado, de forma equivalente al manejo ya definido para notificaciones repetidas o sucesivas de estado en "Brindar el estado de la reserva".

- **¿Qué sucede si la Pasarela de Pago reporta más de una vez el resultado de la misma transacción de reembolso (por ejemplo, una notificación repetida)?**
  El contexto no define un mecanismo de deduplicación explícito. El sistema conserva el resultado ya registrado para esa solicitud de reembolso; una notificación repetida con el mismo resultado no altera el registro existente.

- **¿Qué sucede si "Resolver disputa de garantía" resuelve nuevamente la disputa de una misma reserva, sobrescribiendo un resultado y un monto retenido previamente registrados?**
  El sistema no debe sobrescribir silenciosamente la resolución anterior ni solicitar otra operación monetaria. Debe rechazar la solicitud como duplicada o tratarla como una nueva resolución explícitamente autorizada, con una nueva clave idempotente y validación del saldo de depósito disponible.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir, mediante inclusión interna desde "Brindar el estado de la reserva" o extensión desde "Resolver disputa de garantía", una solicitud de reembolso para una reserva específica, identificada por su identificador de reserva, junto con el origen de la solicitud (cancelación flexible, cancelación moderada, finalización sin incidentes, liberación total del depósito o retención parcial del depósito).
- **RF-002**: El sistema DEBE, cuando el origen sea cancelación flexible, recuperar el valor de alquiler previamente registrado y solicitar la liberación del importe autorizado o el reembolso del 100% si ya fue capturado.
- **RF-003**: El sistema DEBE, cuando el origen sea cancelación moderada, calcular el 50% del valor de alquiler registrado y solicitar su liberación o reembolso según el estado del cobro original.
- **RF-004**: El sistema DEBE, cuando el origen sea finalización sin incidentes, recuperar el depósito registrado y solicitar su liberación o reembolso íntegro, sin requerir la ejecución previa de "Resolver disputa de garantía".
- **RF-005**: El sistema DEBE, cuando el origen sea liberación total del depósito, recuperar el depósito registrado y solicitar su liberación o reembolso íntegro según corresponda al cobro original.
- **RF-006**: El sistema DEBE, cuando el origen sea retención parcial, calcular el monto restante y solicitar su liberación o reembolso según el estado del cobro original.
- **RF-007**: El sistema DEBE registrar internamente la solicitud de liberación o reembolso en curso, vinculada al cobro original y con una clave idempotente, mientras se espera el resultado de la Pasarela de Pago.
- **RF-008**: El sistema DEBE recibir de la Pasarela de Pago el resultado de la operación, registrando el tipo de operación (liberación o reembolso), el monto confirmado, el estado externo, su detalle y la referencia externa provista.
- **RF-009**: El sistema DEBE dejar disponible el resultado registrado del reembolso para ser consultado mediante "Consultar registros financieros".
- **RF-010**: El sistema NO DEBE registrar ni reportar como completada una liberación o reembolso cuya operación fue rechazada, cancelada, expirada o quedó en proceso según la Pasarela de Pago.
- **RF-011**: El sistema DEBE responder con un error controlado, registrándolo internamente, cuando se invoque este caso de uso para una reserva sin el valor de alquiler o el depósito de garantía previamente registrado, según corresponda al origen de la solicitud.
- **RF-012**: El sistema DEBE distinguir el monto que la política de negocio ordena devolver del costo transaccional cobrado por la Pasarela de Pago. No DEBE asumir que la pasarela absorbe, devuelve o descuenta ese costo; la política aplicable debe estar configurada explícitamente.
- **RF-013**: El sistema DEBE registrar, como parte del `RegistroDeReembolso`, el propietario y la embarcación asociados a la reserva (copiados de `InformaciónDeReserva`), de manera que dicho registro quede asociado a su propietario y pueda ser consultado en "Consultar registros financieros" (SPEC 12).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de Pago, tanto para la solicitud de reembolso como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto solicitado a la Pasarela de Pago y para el monto devuelto registrado.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) tanto para el envío de la solicitud de reembolso como para la recepción de su resultado, dado que "Consultar registros financieros" depende del resultado registrado por este caso de uso.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3, actualizada en SPEC 4, SPEC 7 y SPEC 8)**: En este caso de uso es consultada para recuperar el valor de alquiler registrado (origen cancelación) o el depósito de garantía registrado junto con el monto retenido cuando aplique (origen finalización sin incidentes o disputa), a fin de calcular el monto a reembolsar.
- **RegistroDeReembolso (Entidad)**: Estructura persistida para representar el ciclo de una liberación o reembolso sobre una reserva. Conserva el origen, tipo de operación, monto solicitado, referencia del cobro original, clave idempotente, monto confirmado, estado externo, detalle y referencia externa. También conserva el propietario y la embarcación asociados y es consumida por "Consultar registros financieros".
- **SolicitudReembolso (DTO)**: Información recibida internamente desde "Brindar el estado de la reserva" o "Resolver disputa de garantía" para esta operación. Contiene el identificador de la reserva y el origen de la solicitud (cancelación flexible, cancelación moderada, finalización sin incidentes, liberación total del depósito o retención parcial del depósito).
- **SolicitudReembolsoPasarela (DTO)**: Información enviada a la Pasarela de Pago. Contiene el tipo de operación (liberación o reembolso), el monto, la referencia del cobro original, la referencia de la reserva y la clave idempotente.
- **ResultadoReembolsoPasarela (DTO)**: Información recibida desde la Pasarela de Pago. Contiene el tipo de operación, el estado externo, su detalle, el monto confirmado y la referencia externa asignada.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los montos de reembolso solicitados a la Pasarela de Pago corresponden exactamente al monto que corresponde según el origen de la solicitud (100%/50% del valor de alquiler, o depósito íntegro/restante), con cero (0) errores de cálculo detectados en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-002**: Cumplimiento Arquitectónico, "0 cálculos o aplicaciones de descuentos por costos transaccionales realizados por el sistema en las solicitudes de reembolso, y 0 ejecuciones de 'Resolver disputa de garantía' disparadas para reservas informadas como 'completada sin incidentes', confirmando que ambos flujos permanecen desacoplados".
- **CE-003**: Trazabilidad, "100% de las solicitudes de liberación o reembolso enviadas a la Pasarela de Pago quedan registradas internamente, y 100% de los resultados recibidos actualizan dicho registro, dejándolo disponible para 'Consultar registros financieros'".
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de Pago, tanto al enviar la solicitud de reembolso como al recibir su resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones de reembolso en un estado indefinido".
