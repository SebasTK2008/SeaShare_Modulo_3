# Especificación de Funcionalidad: UC04 - Solicitar Confirmación de Pago

**Creado**: 2026-09-05

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Confirmación exitosa de fondos para asegurar la reserva (Prioridad: P1)

Como Módulo 2 (Reservas y Operaciones), tras el inicio del proceso de cobro, quiero solicitar la confirmación de pago a la Pasarela de Pago (o Módulo 3), para verificar que los fondos han sido retenidos o cobrados exitosamente y así cambiar el estado de la reserva a "Confirmada".

**Contexto del Sistema (Flujo)**: 
1. El arrendatario completa el flujo de *checkout* e ingresa sus datos de pago.
2. El Módulo 2 activa este caso de uso enviando una solicitud con el identificador de la transacción o reserva (`transaction_id` o `reservation_id`) hacia el sistema financiero (Pasarela/Módulo 3).
3. El sistema financiero verifica el estatus de la transacción con la entidad bancaria.
4. El Módulo 2 recibe una respuesta de "Aprobado" y procede a bloquear las fechas en el calendario, notificando al propietario y al arrendatario.

**Por qué esta prioridad**: Es el paso crítico que convierte una "intención de reserva" en una transacción vinculante. Sin esta confirmación, existe el riesgo de sobreventas (double-booking) o de proveer el servicio sin haber asegurado los fondos.

**Prueba Independiente**: Enviar un *payload* de consulta con un `transaction_id` válido desde el Módulo 2. Verificar que al recibir un estado `SUCCESS`, el Módulo 2 actualice la reserva a "Confirmada" y bloquee el calendario.

**Escenarios de Aceptación**:
1. **Escenario**: Pago aprobado y reserva confirmada.
   - **Dado** que un arrendatario ha intentado pagar una reserva.
   - **Cuando** el Módulo 2 solicita la confirmación de pago al sistema financiero.
   - **Entonces** recibe un estado de "Aprobado", la reserva cambia a estado "Confirmada" y se emiten las notificaciones correspondientes.

---

### Historia de Usuario 2 - Manejo de pago rechazado o fondos insuficientes (Prioridad: P1)

Como Módulo 2 (Reservas y Operaciones), si solicito la confirmación de pago y la transacción falló, quiero recibir un estado de "Rechazado" con su respectivo motivo, para poder liberar inmediatamente el calendario de la embarcación e instar al usuario a utilizar otro método de pago.

**Por qué esta prioridad**: Evita que el inventario (botes) quede bloqueado indefinidamente por transacciones fallidas, maximizando la disponibilidad y los ingresos de los propietarios.

**Prueba Independiente**: Simular una solicitud de confirmación desde el Módulo 2 para una transacción rechazada por el banco. Validar que el Módulo 2 cambie el estado de la reserva a "Pago Fallido", libere las fechas y muestre el error adecuado al arrendatario.

**Escenarios de Aceptación**:
1. **Escenario**: Confirmación de pago resulta en rechazo.
   - **Dado** que el Módulo 2 solicita el estado de una transacción en proceso.
   - **Cuando** el sistema financiero responde que el pago fue "Rechazado" (ej. fondos insuficientes).
   - **Entonces** el Módulo 2 cancela el bloqueo temporal del calendario, marca la reserva como "Pago Fallido" y permite al usuario reintentar.

---

### Casos Extremos (Edge Cases)

- ¿Qué sucede si la solicitud de confirmación de pago expira por un *timeout* del banco o de la pasarela de pagos?
- ¿Cómo actúa el Módulo 2 si recibe la confirmación de pago horas más tarde mediante un *Webhook* asíncrono, cuando la reserva ya había sido marcada como expirada?
- ¿Qué ocurre si la confirmación indica que se aprobó un monto diferente (pago parcial) al total esperado por la reserva?
- ¿Cómo se maneja el escenario donde el arrendatario cierra la aplicación justo en el momento en que el Módulo 2 está solicitando la confirmación?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 2 DEBE ser capaz de solicitar proactivamente (vía *polling* corto o esperando un *Webhook*) el estado final de una transacción financiera.
- **RF-002**: El Módulo 2 DEBE transicionar el estado de la reserva a "Confirmada" única y exclusivamente si recibe una confirmación de pago exitosa e íntegra.
- **RF-003**: El Módulo 2 DEBE liberar cualquier bloqueo temporal de fechas en el calendario si la confirmación de pago resulta en "Rechazado" o expira el tiempo límite (ej. 15 minutos).
- **RF-004**: El sistema DEBE asociar el identificador único de la transacción bancaria (`provider_transaction_id`) a la reserva una vez confirmada, para futuras referencias o devoluciones.

### Requisitos No Funcionales

- **RNF-001**: La comunicación DEBE estar encriptada (TLS 1.2+) y utilizar firmas digitales o tokens de autenticación para garantizar que la confirmación de pago proviene legítimamente de la pasarela autorizada.
- **RNF-002**: El Módulo 2 DEBE implementar idempotencia en la recepción de confirmaciones; procesar el mismo *Webhook* de confirmación de pago múltiples veces no debe generar notificaciones o cambios de estado duplicados.
- **RNF-003**: El sistema DEBE soportar latencias de red, implementando un mecanismo de reintentos con *backoff* exponencial al consultar el estado de la transacción.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Transaction / Transacción]**: Entidad que representa el intento de cobro. Atributos clave: `id`, `amount`, `currency`, `status` (PENDING, SUCCESS, FAILED), y `provider_reference`.
- **[Reservation / Reserva]**: Representa el acuerdo de alquiler. Atributo clave actualizado: `payment_status` y `operational_status`.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Cero Falsos Positivos, "El 100% de las reservas en estado 'Confirmada' cuentan con su correspondiente confirmación de pago validada y registrada en los logs de auditoría".
- **CE-002**: Tasa de Liberación de Inventario, "El 99.9% de los calendarios bloqueados temporalmente por pagos fallidos o abandonados se liberan automáticamente en un máximo de 15 minutos".
- **CE-003**: Resiliencia Webhook/Polling, "El sistema maneja con éxito las confirmaciones asíncronas, registrando menos del 0.5% de discrepancias de estado entre el Módulo 2 y la Pasarela de Pago durante cierres de mes".