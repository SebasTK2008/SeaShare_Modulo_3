# Especificación de Funcionalidad: UC05 - Procesar Cobro

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso involucra dos actores distintos del diagrama de Módulo 3: el Arrendatario, cuyo pago sobre una reserva con valor total ya calculado y registrado por "Solicitar el valor calculado de la reserva" (SPEC 4) da origen a la solicitud de cobro, y la Pasarela de pago, que ejecuta la transacción y reporta su resultado al sistema. El resultado final registrado aquí es el que consulta posteriormente "Solicitar confirmación de pago" (SPEC 6, pendiente) para informar al Sistema de Reservas y Operaciones.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Enviar la solicitud de cobro a la Pasarela de pago (Prioridad: P1)

Como el sistema, al recibir la solicitud de cobro asociada al pago que el Arrendatario inicia sobre una reserva con su valor total ya calculado y registrado, quiero enviar dicho valor total a la Pasarela de pago para iniciar la transacción, de manera que quede registrada internamente la solicitud de cobro en curso mientras se espera el resultado de la Pasarela de pago.

**Por qué esta prioridad**: Sin el envío correcto del valor total a la Pasarela de pago no puede iniciarse ninguna transacción real; es el punto de partida obligatorio de todo el ciclo de cobro.

**Prueba Independiente**: Con una reserva que ya tiene un valor total calculado y registrado, enviar al sistema una solicitud de cobro y validar que este recupera el valor total registrado, lo envía a la Pasarela de pago (simulada) y registra internamente la solicitud de cobro en curso.

**Escenarios de Aceptación**:

1. **Escenario**: Envío exitoso de la solicitud de cobro a la Pasarela de pago.
   - **Dado** que una reserva tiene un valor total previamente calculado y registrado.
   - **Cuando** el Arrendatario inicia el pago de esa reserva.
   - **Entonces** el sistema recupera el valor total registrado, lo envía a la Pasarela de pago y registra internamente la solicitud de cobro en curso, a la espera del resultado de la transacción.

---

### Historia de Usuario 2 - Registrar el resultado del cobro reportado por la Pasarela de pago (Prioridad: P1)

Como el sistema, al recibir de la Pasarela de pago el resultado de una transacción de cobro previamente iniciada, quiero registrar dicho resultado (éxito o fallo) junto con la referencia externa provista, de manera que esta información quede disponible para "Solicitar confirmación de pago".

**Por qué esta prioridad**: El registro correcto y confiable del resultado reportado por la Pasarela de pago es lo que determina si una reserva fue efectivamente cobrada; ningún caso de uso posterior del ciclo de cobro (confirmación, dispersión de fondos, reembolsos) puede operar sobre un resultado incorrecto o ausente.

**Prueba Independiente**: Con una solicitud de cobro previamente enviada a la Pasarela de pago, simular la recepción de un resultado (éxito y, por separado, fallo) y validar que el sistema registra internamente el resultado correspondiente junto con la referencia externa, sin marcar como exitosas las transacciones fallidas.

**Escenarios de Aceptación**:

1. **Escenario**: La Pasarela de pago reporta el cobro como exitoso.
   - **Dado** que existe una solicitud de cobro previamente enviada a la Pasarela de pago para una reserva.
   - **Cuando** la Pasarela de pago reporta al sistema que la transacción fue exitosa.
   - **Entonces** el sistema registra la transacción como exitosa, con el monto cobrado y la referencia externa provista, dejándola disponible para "Solicitar confirmación de pago".

2. **Escenario**: La Pasarela de pago reporta el rechazo del cobro.
   - **Dado** que existe una solicitud de cobro previamente enviada a la Pasarela de pago para una reserva.
   - **Cuando** la Pasarela de pago reporta al sistema el rechazo de la transacción.
   - **Entonces** el sistema registra la transacción como fallida, sin marcar la reserva como cobrada, dejando este resultado disponible para "Solicitar confirmación de pago".

### Casos Extremos (Edge Cases)

- **¿Qué sucede cuando la Pasarela de pago está caída, agota el tiempo de espera (*timeout*) o es inalcanzable al momento de enviar la solicitud de cobro?**
  Conforme a RNF-003, el sistema no asume ningún resultado. Aplica un manejo de errores controlado, registra la solicitud de cobro con un estado que refleje la falla de comunicación (sin marcarla como exitosa ni como rechazada por la Pasarela) y deja esta condición disponible para "Solicitar confirmación de pago".

- **¿Qué sucede si se solicita procesar el cobro de una reserva para la cual no existe un valor total previamente calculado por "Solicitar el valor calculado de la reserva"?**
  De acuerdo con RF-009, el sistema no envía ninguna solicitud a la Pasarela de pago con un monto asumido; responde con un error controlado indicando que no existe un valor total registrado para esa reserva.

- **¿Qué sucede si la Pasarela de pago reporta el rechazo del cobro (por ejemplo, fondos insuficientes o medio de pago inválido)?**
  Según RF-006 y RF-008, el sistema registra la transacción como fallida junto con la referencia provista por la Pasarela de pago, y en ningún caso marca la reserva como cobrada exitosamente.

- **¿Qué sucede si el sistema recibe de la Pasarela de pago el resultado de una transacción para la cual no existe una solicitud de cobro previamente registrada?**
  El sistema no puede asociar dicho resultado a ninguna reserva conocida. Conforme a RNF-003, esta situación se trata como una falla de consistencia: el sistema registra el evento recibido sin poder vincularlo a una solicitud en curso, sin generar un registro de cobro exitoso.

- **¿Qué sucede si la Pasarela de pago reporta más de una vez el resultado de la misma transacción (por ejemplo, una notificación repetida)?**
  El contexto no define un mecanismo de deduplicación explícito. El sistema conserva el resultado ya registrado para esa solicitud de cobro; una notificación repetida con el mismo resultado no altera el registro existente.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir la solicitud de cobro correspondiente a una reserva, identificada mediante su identificador de reserva.
- **RF-002**: El sistema DEBE recuperar el valor total previamente calculado y registrado internamente para dicha reserva por "Solicitar el valor calculado de la reserva".
- **RF-003**: El sistema DEBE enviar a la Pasarela de pago una solicitud de cobro por el valor total recuperado.
- **RF-004**: El sistema DEBE registrar internamente la solicitud de cobro en curso mientras se espera el resultado de la Pasarela de pago.
- **RF-005**: El sistema DEBE recibir de la Pasarela de pago el resultado de una transacción de cobro previamente iniciada.
- **RF-006**: El sistema DEBE registrar internamente el resultado reportado por la Pasarela de pago, incluyendo el monto cobrado, el estado de la transacción (éxito o fallo) y la referencia externa provista.
- **RF-007**: El sistema DEBE dejar disponible el resultado registrado de la transacción para ser consultado mediante "Solicitar confirmación de pago".
- **RF-008**: El sistema NO DEBE registrar ni reportar como exitosa una reserva cuya transacción fue rechazada o fallida según la Pasarela de pago.
- **RF-009**: El sistema DEBE responder con un error controlado si se solicita procesar el cobro de una reserva para la cual no existe un valor total previamente calculado.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de pago, tanto para la solicitud de cobro como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto enviado a la Pasarela de pago y para el monto cobrado registrado.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) tanto para el envío de la solicitud de cobro como para la recepción de su resultado, dado que "Solicitar confirmación de pago" depende del resultado registrado por este caso de uso.

### Entidades Clave

- **RegistroDeCobro (Entidad)**: Estructura gestionada y persistida internamente por el sistema para representar el ciclo de una transacción de cobro sobre una reserva. Es creada por la Historia de Usuario 1 con el estado de la solicitud en curso, y actualizada por la Historia de Usuario 2 con el monto cobrado, el estado final de la transacción (éxito o fallo) y la referencia externa provista por la Pasarela de pago. Es consumida posteriormente por "Solicitar confirmación de pago".
- **SolicitudCobro (DTO)**: Información recibida para iniciar esta operación. Contiene el identificador de la reserva sobre la cual se debe procesar el cobro.
- **SolicitudCobroPasarela (DTO)**: Información enviada a la Pasarela de pago. Contiene el monto total a cobrar y la referencia de la reserva asociada.
- **ResultadoCobroPasarela (DTO)**: Información recibida desde la Pasarela de pago como resultado de una transacción previamente iniciada. Contiene el estado de la transacción (éxito o fallo) y la referencia externa asignada por la Pasarela de pago.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los cobros registrados como exitosos corresponden exactamente al valor total previamente calculado para la reserva, con cero (0) discrepancias de monto detectadas en pruebas automatizadas".
- **CE-002**: Trazabilidad, "100% de las solicitudes de cobro enviadas a la Pasarela de pago quedan registradas internamente, y 100% de los resultados recibidos (éxito o fallo) actualizan dicho registro, dejándolo disponible para 'Solicitar confirmación de pago'".
- **CE-003**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de pago (timeouts, servicio inalcanzable), tanto al enviar la solicitud como al recibir el resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones en un estado indefinido".
- **CE-004**: Integridad del Cobro, "0% de las transacciones rechazadas o fallidas reportadas por la Pasarela de pago son registradas o reportadas como cobros exitosos".
