# Especificación de Funcionalidad: UC08 - Brindar Información de Disputa de Garantía

**Creado**: 2026-09-24

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Recibir el estado de la disputa y ejecutar su consecuencia financiera (Prioridad: P1)

Como el sistema, al recibir desde el Módulo 2 el estado de una disputa de garantía asociado a una reserva, quiero consultar el depósito y las operaciones financieras registradas internamente para esa reserva y ejecutar el reembolso total al Arrendatario o la liquidación total al Propietario según el resultado informado, sin recibir ni calcular montos desde el Módulo 2.

**Por qué esta prioridad**: El Módulo 2 gestiona la disputa y decide su resultado operativo; el Módulo 3 aplica exclusivamente las consecuencias monetarias usando sus propios registros financieros.

**Prueba Independiente**: Con un depósito capturado y registrado, recibir notificaciones `PENDIENTE`, `RECHAZADO` y `COMPLETADO`, y validar que solo los estados finales disparan exactamente el reembolso total o la liquidación total.

**Estados de la disputa**:

- `PENDIENTE`: la disputa existe o continúa en revisión; no se ejecuta ninguna operación financiera.
- `RECHAZADO`: el reclamo no procede; el depósito debe devolverse completamente al Arrendatario. El motivo, cuando exista, es opcional y no forma parte del estado.
- `COMPLETADO`: el reclamo procede; el depósito debe liquidarse completamente al Propietario.

### Escenarios de Aceptación

1. **Escenario**: Disputa pendiente.
  - **Dado** que existe un depósito registrado para una reserva.
  - **Cuando** el Módulo 2 informa `PENDIENTE`.
  - **Entonces** el sistema registra la actualización sin reembolsar ni liquidar el depósito.

2. **Escenario**: Disputa rechazada y devolución al Arrendatario.
  - **Dado** que el Módulo 2 informa `RECHAZADO`.
  - **Cuando** el sistema recibe la notificación asociada a una reserva.
  - **Entonces** registra el estado y el motivo opcional, y solicita el reembolso total del depósito al Arrendatario.

3. **Escenario**: Disputa completada y liquidación al Propietario.
  - **Dado** que existe un depósito capturado y registrado.
  - **Cuando** el Módulo 2 informa `COMPLETADO`.
  - **Entonces** el sistema solicita la liquidación total del depósito al Propietario mediante "Liquidar fondos de alquiler".

4. **Escenario**: Ausencia de reclamo al vencer la ventana de 24 horas.
  - **Dado** que el Módulo 2 cerró la ventana de reporte sin registrar un reclamo.
  - **Cuando** informa `RECHAZADO`.
  - **Entonces** el sistema solicita el reembolso total del depósito al Arrendatario.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si se recibe `PENDIENTE` varias veces?** El sistema registra la notificación de forma idempotente y no ejecuta operaciones monetarias.
- **¿Qué sucede si se recibe `RECHAZADO` o `COMPLETADO` varias veces?** El sistema usa la clave idempotente y no duplica el reembolso o la liquidación ya solicitados.
- **¿Qué sucede si no existe un depósito registrado o el cobro original no fue capturado?** Registra un fallo controlado y no solicita reembolso ni liquidación con un monto asumido.
- **¿Qué sucede si se recibe una resolución completada más de una vez?** La clave idempotente y la asociación con la reserva impiden duplicar operaciones.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir desde el Módulo 2 una notificación asociada a una reserva y una disputa, sin recibir montos ni instrucciones de operación financiera.
- **RF-002**: El sistema DEBE reconocer únicamente los estados `PENDIENTE`, `RECHAZADO` y `COMPLETADO`.
- **RF-003**: `RECHAZADO` DEBE admitir un motivo opcional, sin hacer obligatorio dicho motivo ni asumir una causa única.
- **RF-004**: `PENDIENTE` NO DEBE ejecutar reembolso ni liquidación de garantía.
- **RF-005**: Ante `RECHAZADO`, el sistema DEBE recuperar internamente el depósito capturado y solicitar su reembolso total al Arrendatario.
- **RF-006**: Ante `COMPLETADO`, el sistema DEBE recuperar internamente el depósito capturado y solicitar su liquidación total al Propietario.
- **RF-007**: El sistema NO DEBE recibir ni requerir desde el Módulo 2 el monto del depósito, el monto a reembolsar, el monto a liquidar ni una instrucción técnica de pasarela.
- **RF-008**: El sistema DEBE aplicar idempotencia por reserva, disputa y versión o clave del evento.
- **RF-009**: El sistema DEBE exponer este caso de uso como consumidor del Módulo 2 y no como un caso de uso mediante el cual el Administrador Financiero resuelva disputas dentro del Módulo 3.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la notificación mínima de disputa.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para los montos recuperados de sus propios registros y enviados a la Pasarela de Pago.
- **RNF-003**: El sistema DEBE implementar idempotencia, control de concurrencia y manejo robusto de errores ante eventos repetidos, estados desconocidos o ausencia de información financiera interna.

### Entidades Clave

- **InformaciónDeReserva**: Se consulta para recuperar el depósito fijo registrado y el estado financiero de la reserva.
- **RegistroDeCobro**: Se consulta para confirmar que el pago que contiene el depósito fue capturado y para obtener la referencia original.
- **SolicitudInformaciónDisputa (DTO)**: Contiene el identificador de la reserva, el identificador de la disputa, el estado, la versión o clave idempotente y el motivo opcional cuando sea `RECHAZADO`. No contiene resultados monetarios ni un campo adicional de decisión.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: "100% de las notificaciones reconocidas corresponden a `PENDIENTE`, `RECHAZADO` o `COMPLETADO`; los estados desconocidos no ejecutan operaciones monetarias".
- **CE-002**: "100% de los estados `PENDIENTE` producen cero reembolsos y cero liquidaciones de garantía".
- **CE-003**: "100% de los estados `RECHAZADO` solicitan exactamente un reembolso total idempotente, y 100% de los estados `COMPLETADO` solicitan exactamente una liquidación total idempotente".
- **CE-004**: "0 montos o instrucciones técnicas de pago son recibidos desde el Módulo 2, y 100% de los importes ejecutados provienen de registros financieros internos del Módulo 3".
- **CE-005**: "100% de los eventos repetidos o concurrentes para una misma resolución evitan operaciones monetarias duplicadas".
