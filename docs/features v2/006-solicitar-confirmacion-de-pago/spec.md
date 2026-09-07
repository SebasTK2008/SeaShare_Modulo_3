# Especificación de Funcionalidad: UC06 - Solicitar Confirmación de Pago

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es invocado por el Sistema de Reservas y Operaciones para consultar el resultado de una transacción de cobro previamente registrada por "Procesar cobro" (SPEC 5). El sistema no vuelve a contactar a la Pasarela de pago en este caso de uso: se limita a devolver el estado del `RegistroDeCobro` ya existente (en proceso, éxito o fallo), permitiendo que el Sistema de Reservas y Operaciones actualice el estado de la reserva en consecuencia.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el resultado de un cobro previamente registrado (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones una solicitud de confirmación de pago para una reserva específica, quiero consultar el registro de cobro previamente creado por "Procesar cobro" para esa reserva, de manera que pueda devolver al Sistema de Reservas y Operaciones el estado actual de la transacción (en proceso, éxito o fallo), junto con el monto cobrado y la referencia externa cuando estén disponibles.

**Por qué esta prioridad**: El Sistema de Reservas y Operaciones depende de esta confirmación para actualizar el estado de la reserva (por ejemplo, para completar o revertir el bloqueo temporal del activo); sin una confirmación correcta, la reserva podría quedar en un estado inconsistente frente al cobro real.

**Prueba Independiente**: Con un registro de cobro previamente creado por "Procesar cobro" en distintos estados (en proceso, éxito, fallo), enviar al sistema una solicitud de confirmación de pago para esa reserva y validar que devuelve el estado correspondiente junto con el monto cobrado y la referencia externa cuando aplique.

**Escenarios de Aceptación**:

1. **Escenario**: Confirmación de un cobro exitoso.
   - **Dado** que el registro de cobro de una reserva fue marcado como exitoso por "Procesar cobro".
   - **Cuando** el Sistema de Reservas y Operaciones solicita la confirmación de pago de esa reserva.
   - **Entonces** el sistema devuelve el estado exitoso junto con el monto cobrado y la referencia externa provista por la Pasarela de pago.

2. **Escenario**: Confirmación de un cobro fallido.
   - **Dado** que el registro de cobro de una reserva fue marcado como fallido por "Procesar cobro".
   - **Cuando** el Sistema de Reservas y Operaciones solicita la confirmación de pago de esa reserva.
   - **Entonces** el sistema devuelve el estado fallido correspondiente, sin reportar un monto cobrado exitoso.

3. **Escenario**: Consulta de un cobro que aún está en proceso.
   - **Dado** que el registro de cobro de una reserva se encuentra en curso, a la espera del resultado de la Pasarela de pago.
   - **Cuando** el Sistema de Reservas y Operaciones solicita la confirmación de pago de esa reserva.
   - **Entonces** el sistema devuelve el estado "en proceso", sin un resultado definitivo de éxito o fallo.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Reservas y Operaciones solicita la confirmación de pago de una reserva para la cual no existe ningún registro de cobro (es decir, "Procesar cobro" nunca fue iniciado para esa reserva)?**
  Conforme a RF-005, el sistema no asume ningún estado; responde con un error controlado indicando que no existe un registro de cobro para esa reserva.

- **¿Qué sucede si se solicita la confirmación de pago mientras el registro de cobro aún se encuentra en estado "en proceso"?**
  Según RF-003, el sistema devuelve fielmente el estado "en proceso" registrado, sin inventar ni anticipar un resultado de éxito o fallo que la Pasarela de pago aún no ha reportado.

- **¿Qué sucede si el Sistema de Reservas y Operaciones solicita la confirmación de pago de la misma reserva más de una vez?**
  La consulta es de solo lectura sobre el registro existente (RF-002): cada solicitud devuelve el estado vigente en ese momento, sin generar ni modificar ningún registro adicional.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Sistema de Reservas y Operaciones una solicitud de confirmación de pago para una reserva específica, identificada mediante su identificador de reserva.
- **RF-002**: El sistema DEBE consultar el registro de cobro previamente creado por "Procesar cobro" asociado a esa reserva.
- **RF-003**: El sistema DEBE devolver al Sistema de Reservas y Operaciones el estado vigente de la transacción registrada (en proceso, éxito o fallo).
- **RF-004**: El sistema DEBE incluir en la respuesta el monto cobrado y la referencia externa provista por la Pasarela de pago cuando el estado del registro sea éxito o fallo.
- **RF-005**: El sistema DEBE responder con un error controlado cuando se solicita la confirmación de pago de una reserva para la cual no existe ningún registro de cobro.
- **RF-006**: El sistema NO DEBE contactar nuevamente a la Pasarela de pago dentro de este caso de uso; DEBE limitarse a consultar el registro ya existente.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Sistema de Reservas y Operaciones, tanto para recibir la solicitud como para devolver la confirmación.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar el monto cobrado incluido en la respuesta.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante solicitudes de confirmación para reservas sin registro de cobro, evitando respuestas ambiguas o inconsistentes al Sistema de Reservas y Operaciones.

### Entidades Clave

- **RegistroDeCobro (Entidad, definida en SPEC 5)**: En este caso de uso es únicamente consultada, no creada ni modificada. Su estado (en proceso, éxito o fallo), monto cobrado y referencia externa son la fuente de la respuesta devuelta.
- **SolicitudConfirmacionPago (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para esta operación. Contiene el identificador de la reserva cuya confirmación de pago se solicita.
- **ConfirmacionPagoResultado (DTO)**: Resultado que el sistema devuelve al Sistema de Reservas y Operaciones. Contiene el identificador de la reserva, el estado de la transacción (en proceso, éxito o fallo) y, cuando aplica, el monto cobrado y la referencia externa provista por la Pasarela de pago. No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Trazabilidad, "100% de las solicitudes de confirmación de pago devuelven un estado (en proceso, éxito o fallo) que coincide exactamente con el registro de cobro creado por 'Procesar cobro', con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "0 solicitudes realizadas por este caso de uso hacia la Pasarela de pago, confirmando que la confirmación se resuelve exclusivamente a partir del registro de cobro ya existente".
- **CE-003**: Resiliencia del Sistema, "100% de las solicitudes de confirmación para reservas sin registro de cobro son respondidas mediante un error controlado, sin provocar respuestas ambiguas o inconsistentes al Sistema de Reservas y Operaciones".
