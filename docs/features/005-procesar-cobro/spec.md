# Especificación de Funcionalidad: UC05 - Procesar Cobro

**Creado**: 2026-09-06 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Enviar la solicitud de cobro a la Pasarela de pago (Prioridad: P1)

Como el sistema, al recibir la solicitud de cobro asociada al pago que el Arrendatario inicia sobre una reserva con su valor total ya calculado y registrado y un medio de pago representado por una referencia segura de la Pasarela de pago, quiero solicitar a la Pasarela de pago una autorización o cobro por dicho valor —reteniendo internamente, como parte de ese monto, el depósito de garantía y el seguro náutico previamente registrados para la reserva—, de manera que quede registrada internamente la operación en curso mientras se espera su resultado.

**Por qué esta prioridad**: Sin el envío correcto del valor total a la Pasarela de pago no puede iniciarse ninguna transacción real; es el punto de partida obligatorio de todo el ciclo de cobro.

**Prueba Independiente**: Con una reserva que ya tiene un valor total calculado y registrado, enviar al sistema una solicitud de cobro y validar que este recupera el valor total registrado (incluyendo depósito y seguro como parte de ese monto), lo envía a la Pasarela de pago (simulada) y registra internamente la solicitud de cobro en curso.

**Escenarios de Aceptación**:

1. **Escenario**: Envío exitoso de la solicitud de cobro a la Pasarela de pago.
   - **Dado** que una reserva tiene un valor total previamente calculado y registrado (incluyendo el monto de alquiler, el seguro náutico y el depósito de garantía) y el Arrendatario proporciona un token o referencia segura de medio de pago emitido por la Pasarela de pago.
   - **Cuando** el Arrendatario inicia el pago de esa reserva.
   - **Entonces** el sistema recupera el valor total registrado, envía a la Pasarela de pago el monto (reteniendo internamente que dicho monto incluye el depósito de garantía y el seguro náutico) y la referencia segura del medio de pago, y registra internamente la solicitud de cobro en curso, a la espera del resultado de la transacción.

2. **Escenario**: Uso de un medio de pago tokenizado sin almacenar datos sensibles.
   - **Dado** que la Pasarela de pago ha tokenizado el medio de pago del Arrendatario.
   - **Cuando** el sistema recibe la solicitud de cobro.
   - **Entonces** el sistema utiliza únicamente el token o referencia segura para iniciar la operación y no recibe ni persiste el número completo, el código de seguridad ni otros datos sensibles del medio de pago.

---

### Historia de Usuario 2 - Registrar el resultado del cobro reportado por la Pasarela de pago (Prioridad: P1)

Como el sistema, al recibir de la Pasarela de pago el resultado de una operación de cobro previamente iniciada, quiero registrar su estado externo, detalle, montos y referencia, de manera que esta información quede disponible para "Solicitar confirmación de pago".

**Por qué esta prioridad**: El registro correcto y confiable del resultado reportado por la Pasarela de pago es lo que determina si una reserva fue efectivamente cobrada; ningún caso de uso posterior del ciclo de cobro (confirmación, dispersión de fondos, reembolsos) puede operar sobre un resultado incorrecto o ausente.

**Prueba Independiente**: Con una solicitud de cobro previamente enviada a la Pasarela de pago, simular la recepción de resultados aprobado, en proceso, rechazado, cancelado y expirado, y validar que el sistema registra el estado, detalle, montos y referencia externa sin confundir una respuesta técnica con aprobación financiera.

**Escenarios de Aceptación**:

1. **Escenario**: La Pasarela de pago reporta el cobro como exitoso.
   - **Dado** que existe una solicitud de cobro previamente enviada a la Pasarela de pago para una reserva.
   - **Cuando** la Pasarela de pago reporta al sistema que la transacción fue exitosa.
   - **Entonces** el sistema registra el estado reportado, el monto autorizado o cobrado, el detalle y la referencia externa. Solo si el estado es aprobado puede dejar disponible la operación como pago confirmado para "Solicitar confirmación de pago".

2. **Escenario**: La Pasarela de pago reporta el rechazo del cobro.
   - **Dado** que existe una solicitud de cobro previamente enviada a la Pasarela de pago para una reserva.
   - **Cuando** la Pasarela de pago reporta al sistema el rechazo de la transacción.
   - **Entonces** el sistema registra la transacción como fallida, sin marcar la reserva como cobrada, dejando este resultado disponible para "Solicitar confirmación de pago".

3. **Escenario**: La autorización previamente aprobada expira antes de ser capturada.
   - **Dado** que existe una autorización previamente aprobada para una reserva, con una vigencia limitada.
   - **Cuando** dicha vigencia se cumple sin que el negocio haya solicitado la captura.
   - **Entonces** el sistema registra el hecho de la expiración, no asume que los fondos permanecen disponibles, y ejecuta el flujo de recuperación definido en RF-014 (por ejemplo, marcar la operación como expirada y dejarla disponible para conciliación o para un nuevo intento de cobro), dejando este estado disponible para "Solicitar confirmación de pago".

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

- **¿Qué sucede si el token o la referencia segura del medio de pago es inválido, expiró o no puede ser utilizado por la Pasarela de pago?**
  El sistema no envía un cobro con datos incompletos ni asume que la operación fue rechazada financieramente. Registra el fallo técnico o de validación informado, sin marcar la reserva como cobrada, y deja el resultado disponible para "Solicitar confirmación de pago".

- **¿El sistema recibe o persiste el número completo de tarjeta, su código de seguridad o su fecha de vencimiento?**
  No. Esos datos son capturados y protegidos por la Pasarela de pago o sus componentes alojados. El sistema recibe únicamente un token o referencia segura y, cuando la pasarela lo permite, metadatos no sensibles como el tipo de medio y los últimos cuatro dígitos.

- **¿Qué sucede si una autorización previamente aprobada expira antes de que el negocio solicite su captura?**
  La autorización tiene una vigencia limitada definida por la Pasarela de pago. Si expira, el sistema no asume que los fondos siguen disponibles ni que la reserva permanece cobrada: registra internamente el hecho de la expiración y ejecuta el flujo de recuperación (RF-014), dejando la operación en un estado explícito de "expirada" disponible para "Solicitar confirmación de pago" y para conciliación posterior. Esto no constituye, por sí mismo, una reserva cobrada ni un cobro fallido por rechazo del arrendatario.

- **¿El monto enviado y retenido en la autorización/cobro incluye el depósito de garantía y el seguro náutico, o son operaciones separadas?**
  Es un único monto. El sistema retiene internamente, como parte del monto total autorizado o cobrado, tanto el depósito de garantía como el seguro náutico previamente registrados para la reserva (junto con el monto de alquiler); no se generan solicitudes separadas a la Pasarela de pago para estos componentes en el momento del cobro. La distinción de estos componentes se conserva en el registro interno (`RegistroDeCobro`) para su uso posterior en reembolsos y liquidaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir la solicitud de cobro correspondiente a una reserva, identificada mediante su identificador de reserva, junto con un token o referencia segura del medio de pago emitido por la Pasarela de pago y, cuando estén disponibles, su tipo y metadatos no sensibles.
- **RF-002**: El sistema DEBE recuperar el valor total previamente calculado y registrado internamente para dicha reserva por "Solicitar el valor calculado de la reserva", incluyendo el monto de alquiler, el monto del seguro náutico y el monto del depósito de garantía que lo componen.
- **RF-003**: El sistema DEBE enviar a la Pasarela de pago una solicitud de autorización o cobro por el valor total recuperado, según la capacidad configurada para la integración.
- **RF-004**: El sistema DEBE registrar internamente la solicitud de cobro en curso mientras se espera el resultado de la Pasarela de pago.
- **RF-005**: El sistema DEBE recibir de la Pasarela de pago el resultado de una operación de cobro previamente iniciada.
- **RF-006**: El sistema DEBE registrar el estado externo y su detalle, incluyendo los montos autorizado, capturado, liberado o cobrado cuando estén disponibles, la referencia externa y la fecha de actualización.
- **RF-007**: El sistema DEBE dejar disponible el resultado registrado de la transacción para ser consultado mediante "Solicitar confirmación de pago".
- **RF-008**: El sistema NO DEBE registrar ni reportar como exitosa una reserva cuya transacción fue rechazada o fallida según la Pasarela de pago.
- **RF-009**: El sistema DEBE responder con un error controlado si se solicita procesar el cobro de una reserva para la cual no existe un valor total previamente calculado.
- **RF-010**: El sistema DEBE registrar, como parte del `RegistroDeCobro`, el propietario y la embarcación asociados a la reserva (copiados de `InformaciónDeReserva`), de manera que dicho registro quede asociado a su propietario y pueda ser consultado en "Consultar registros financieros" (SPEC 12).
- **RF-011**: El sistema DEBE enviar a la Pasarela de pago el token o referencia segura del medio de pago, sin enviar ni persistir el número completo de tarjeta, el código de seguridad, la fecha de vencimiento ni otros datos sensibles equivalentes.
- **RF-012**: El sistema DEBE registrar, cuando la Pasarela de pago los proporcione, el tipo de medio de pago, los últimos cuatro dígitos u otros metadatos no sensibles y la referencia externa asociada, para permitir trazabilidad y presentación enmascarada sin exponer credenciales de pago.
- **RF-013**: El sistema DEBE responder con un error controlado y no marcar la reserva como cobrada cuando el token o referencia segura sea inválido, haya expirado o no pueda utilizarse por una falla técnica o de validación de la Pasarela de pago.
- **RF-014**: El sistema DEBE registrar la expiración de una autorización previamente aprobada que no fue capturada dentro de su vigencia, sin asumir que los fondos permanecen disponibles, y ejecutar un flujo de recuperación controlado (marcar la operación como expirada y dejarla disponible para conciliación o reintento), dejando este estado disponible para "Solicitar confirmación de pago".
- **RF-015**: El sistema DEBE retener, como parte del único monto autorizado o cobrado ante la Pasarela de pago, el depósito de garantía y el seguro náutico previamente registrados para la reserva, sin generar solicitudes separadas a la Pasarela de pago para dichos componentes en el momento del cobro, conservando internamente la distinción de cada componente en el `RegistroDeCobro`.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de pago, tanto para la solicitud de cobro como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto enviado a la Pasarela de pago y para el monto cobrado registrado.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*), idempotencia y conciliación posterior para el envío de la solicitud y la recepción de su resultado. Un timeout no permite concluir que la operación falló.
- **RNF-004**: El sistema DEBE aplicar minimización y protección de datos de pago: no debe persistir números completos de tarjeta, códigos de seguridad, fechas de vencimiento ni credenciales equivalentes; únicamente puede conservar tokens, referencias y metadatos no sensibles necesarios para la operación y su trazabilidad.
- **RNF-005**: El sistema DEBE monitorear la vigencia de cada autorización aprobada y disparar su flujo de recuperación (RF-014) al expirar, sin depender de una consulta manual del Administrador Financiero para detectarlo.

### Entidades Clave

- **RegistroDeCobro (Entidad)**: Estructura persistida para representar el ciclo de una operación de cobro sobre una reserva. Conserva una clave idempotente, el estado interno y externo (incluyendo el estado "expirada" para autorizaciones vencidas sin capturar), el detalle, los montos autorizado, capturado, liberado y cobrado —desglosados en monto de alquiler, seguro náutico y depósito de garantía—, la vigencia de la autorización, el token o referencia segura del medio de pago cuando sea necesario para la trazabilidad, los metadatos no sensibles disponibles, las referencias externas y el propietario y embarcación asociados. Nunca conserva datos sensibles completos del medio de pago. Una operación aprobada no implica que haya sido capturada; la captura, la liberación y la expiración se registran como transiciones posteriores. Es consumida por "Solicitar confirmación de pago" y "Consultar registros financieros".
- **SolicitudCobro (DTO)**: Información recibida para iniciar esta operación. Contiene el identificador de la reserva sobre la cual se debe procesar el cobro y el token o referencia segura del medio de pago, junto con su tipo y metadatos no sensibles cuando estén disponibles.
- **SolicitudCobroPasarela (DTO)**: Información enviada a la Pasarela de pago. Contiene el tipo de operación (autorización o cobro), el monto total (que incluye el monto de alquiler, el seguro náutico y el depósito de garantía), la referencia de la reserva, el token o referencia segura del medio de pago y una clave idempotente. No contiene el número completo de tarjeta, el código de seguridad ni la fecha de vencimiento.
- **ResultadoCobroPasarela (DTO)**: Información recibida desde la Pasarela de pago como resultado de una operación previamente iniciada. Contiene el estado externo (incluyendo "expirado" cuando aplique), su detalle, los montos disponibles y la referencia externa asignada por la Pasarela de pago.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de las operaciones aprobadas o capturadas conservan los montos autorizados y confirmados, con cero (0) discrepancias frente al valor calculado en pruebas automatizadas".
- **CE-002**: Trazabilidad, "100% de las solicitudes de cobro enviadas a la Pasarela de pago quedan registradas internamente, y 100% de los resultados recibidos actualizan dicho registro, dejándolo disponible para 'Solicitar confirmación de pago'".
- **CE-003**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de pago (timeouts, servicio inalcanzable), tanto al enviar la solicitud como al recibir el resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones en un estado indefinido".
- **CE-004**: Integridad del Cobro, "0% de las transacciones rechazadas o fallidas reportadas por la Pasarela de pago son registradas o reportadas como cobros exitosos".
- **CE-005**: Protección del Medio de Pago, "0 números completos de tarjeta, códigos de seguridad, fechas de vencimiento o credenciales equivalentes son persistidos por el sistema; 100% de las solicitudes utilizan tokens o referencias seguras emitidas por la Pasarela de pago".
- **CE-006**: Trazabilidad Enmascarada, "100% de los cobros que reciben metadatos no sensibles del medio de pago conservan correctamente su tipo, últimos cuatro dígitos o referencia externa disponible, sin exponer datos sensibles en consultas o registros financieros".
- **CE-007**: Manejo de Expiración, "100% de las autorizaciones aprobadas que expiran sin captura son registradas explícitamente como expiradas y disparan el flujo de recuperación de RF-014, con 0 casos en los que el sistema asuma fondos disponibles tras la expiración".