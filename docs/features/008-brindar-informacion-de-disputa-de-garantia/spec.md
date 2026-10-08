# Especificación de Funcionalidad: UC08 - Brindar Información de Disputa de Garantía

**Creado**: 2026-09-24 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Recibir el estado de la disputa y ejecutar su consecuencia financiera (Prioridad: P1)

Como el sistema, al recibir desde el Sistema de Reservas y Operaciones el estado de una disputa de garantía asociado a una reserva, quiero consultar el depósito y las operaciones financieras registradas internamente para esa reserva y ejecutar el reembolso total al Arrendatario o la liquidación total al Propietario según el resultado informado, sin recibir ni calcular montos desde el Sistema de Reservas y Operaciones.

**Por qué esta prioridad**: El Sistema de Reservas y Operaciones gestiona la disputa y decide su resultado operativo; el sistema aplica exclusivamente las consecuencias monetarias usando sus propios registros financieros.

**Prueba Independiente**: Con un depósito cobrado y registrado, recibir notificaciones `PENDIENTE`, `RECHAZADO` y `COMPLETADO`, y validar que solo los estados finales disparan exactamente el reembolso total o la liquidación total.

**Estados de la disputa**:

- `PENDIENTE`: la disputa existe o continúa en revisión; no se ejecuta ninguna operación financiera.
- `RECHAZADO`: el reclamo no procede; el depósito debe devolverse completamente al Arrendatario. Este estado no contiene ni requiere un motivo operativo.
- `COMPLETADO`: el reclamo procede; el depósito debe liquidarse completamente al Propietario.

### Escenarios de Aceptación

1. **Escenario**: Disputa pendiente.
   - **Dado** que existe un depósito registrado para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones informa `PENDIENTE`.
   - **Entonces** el sistema registra la actualización sin reembolsar ni liquidar el depósito.

2. **Escenario**: Disputa rechazada y devolución al Arrendatario.
   - **Dado** que el Sistema de Reservas y Operaciones informa `RECHAZADO`.
   - **Cuando** el sistema recibe la notificación asociada a una reserva.
   - **Entonces** registra el estado y solicita la liberación o el reembolso total del depósito al Arrendatario, según el estado del cobro original.

3. **Escenario**: Disputa completada y liquidación al Propietario.
   - **Dado** que existe un depósito cobrado y registrado.
   - **Cuando** el Sistema de Reservas y Operaciones informa `COMPLETADO`.
   - **Entonces** el sistema solicita la liquidación total del depósito al Propietario mediante "Liquidar fondos de alquiler".

4. **Escenario**: Ausencia de disputa al vencer la ventana de 24 horas informada desde el Sistema de Reservas y Operaciones.
   - **Dado** que transcurrieron 24 horas desde la finalización de una reserva sin reclamos reportados en el Sistema de Reservas y Operaciones.
   - **Cuando** el Sistema de Reservas y Operaciones notifica el estado `RECHAZADO` para la garantía.
   - **Entonces** el sistema registra la notificación y solicita la liberación o el reembolso total del depósito al Arrendatario, según el estado del cobro original.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si se recibe `PENDIENTE` varias veces?** El sistema registra la notificación de forma idempotente y no ejecuta operaciones monetarias.
- **¿Qué sucede si se recibe `RECHAZADO` o `COMPLETADO` varias veces?** El sistema usa la clave idempotente y no duplica el reembolso o la liquidación ya solicitados.
- **¿Qué sucede si no existe un depósito registrado o el cobro original no fue cobrado?** Registra un fallo controlado y no solicita reembolso ni liquidación con un monto asumido.
- **¿Qué sucede si se recibe una resolución completada más de una vez?** La clave idempotente y la asociación con la reserva impiden duplicar operaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir desde el Sistema de Reservas y Operaciones, y únicamente, el identificador de la reserva, el identificador de la disputa, el estado (`PENDIENTE`, `RECHAZADO` o `COMPLETADO`), la fecha y hora en que ocurrió la transición al estado informado y una versión o clave idempotente del evento. El sistema NO DEBE recibir ni requerir motivos, orígenes, montos u otros atributos.
- **RF-002**: El sistema DEBE reconocer únicamente los estados `PENDIENTE`, `RECHAZADO` y `COMPLETADO`.
- **RF-003**: `RECHAZADO` NO DEBE requerir ni persistir un motivo u origen operativo.
- **RF-004**: `PENDIENTE` NO DEBE ejecutar reembolso ni liquidación de garantía.
- **RF-005**: Ante `RECHAZADO`, el sistema DEBE recuperar internamente el depósito cobrado y registrado y solicitar su liberación o reembolso total al Arrendatario, según el estado del cobro original.
- **RF-006**: Ante `COMPLETADO`, el sistema DEBE recuperar internamente el depósito cobrado y registrado y solicitar su liquidación total al Propietario.
- **RF-007**: El sistema NO DEBE recibir ni requerir desde el Sistema de Reservas y Operaciones el monto del depósito, el monto a reembolsar, el monto a liquidar ni una instrucción técnica de pasarela.
- **RF-008**: El sistema DEBE aplicar idempotencia por reserva, disputa y versión o clave del evento. También DEBE reforzar exclusión mutua y coherencia con los flujos definidos en SPEC 07, 09 y 10.
- **RF-009**: El sistema DEBE exponer este caso de uso exclusivamente como consumidor de las notificaciones enviadas por el Sistema de Reservas y Operaciones y NO DEBE ejecutar cron jobs, temporizadores internos ni tareas en segundo plano para verificar el vencimiento de la ventana de 24 horas o el estado de las disputas.
- **RF-009A**: El sistema DEBE procesar la notificación con estado `RECHAZADO` enviada por el Sistema de Reservas y Operaciones al vencer la ventana de 24 horas sin disputa, solicitando la liberación o el reembolso total del depósito al Arrendatario sin requerir un atributo de origen.
- **RF-010**: El sistema NO DEBE reconocer sub-resultados adicionales (por ejemplo, liberación o retención parcial) dentro de `COMPLETADO` o `RECHAZADO`; ambos estados son tratamientos totales sobre el 100% del depósito cobrado y registrado, conforme a la regla de negocio vigente ("Se entrega completo al Arrendatario o completo al Propietario").

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la notificación mínima de disputa.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para los montos recuperados de sus propios registros y enviados a la Pasarela de Pago.
- **RNF-003**: El sistema DEBE implementar idempotencia, control de concurrencia y manejo robusto de errores ante eventos repetidos, estados desconocidos o ausencia de información financiera interna.

### Entidades Clave

- **InformaciónDeReserva**: Se consulta para recuperar el depósito fijo registrado y el estado financiero de la reserva.
- **RegistroDeCobro**: Se consulta para confirmar que el pago que contiene el depósito fue cobrado y para obtener la referencia original.
- **SolicitudInformaciónDisputa (DTO)**: Contiene el identificador de la reserva, el identificador de la disputa, el estado, la versión o clave idempotente y la fecha y hora en que ocurrió la transición al estado informado. No contiene motivo, origen, resultados monetarios ni un campo adicional de decisión. La fecha y hora representa el momento del cambio de estado, no el momento de recepción.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: "100% de las notificaciones reconocidas corresponden a `PENDIENTE`, `RECHAZADO` o `COMPLETADO`; los estados desconocidos no ejecutan operaciones monetarias".
- **CE-002**: "100% de los estados `PENDIENTE` producen cero reembolsos y cero liquidaciones de garantía".
- **CE-003**: "100% de los estados `RECHAZADO` solicitan exactamente una liberación o reembolso total idempotente, y 100% de los estados `COMPLETADO` solicitan exactamente una liquidación total idempotente".
- **CE-004**: "0 montos o instrucciones técnicas de pago son recibidos desde el Sistema de Reservas y Operaciones, y 100% de los importes ejecutados provienen de registros financieros internos del sistema".
- **CE-005**: "100% de los eventos repetidos o concurrentes para una misma resolución evitan operaciones monetarias duplicadas".
- **CE-006**: "0 sub-resultados de tipo `LIBERAR_DEPOSITO`/`RETENER_DEPOSITO` o cualquier retención parcial son procesados por este caso de uso, confirmando que es la única y canónica SPEC 8 vigente para la disputa de garantía". Verificar exclusión mutua y coherencia con SPEC 07, 09 y 10.