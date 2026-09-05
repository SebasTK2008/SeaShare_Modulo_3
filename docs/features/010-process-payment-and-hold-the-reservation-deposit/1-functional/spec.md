# Especificación de Funcionalidad: UC10 - Procesar Pago y Retener Depósito de Reserva

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Captura de fondos y retención de garantía (Prioridad: P1)

Como Módulo de Finanzas (Módulo 3), al momento en que el turista confirma su pago en la pasarela, necesito procesar el cobro del Valor Total (Alquiler + Seguro) y al mismo tiempo crear un "Bloqueo" (Hold / Autorización) en la tarjeta de crédito por el monto del Depósito de Garantía, para asegurar fondos en caso de daños a la embarcación.

**Por qué esta prioridad**: Es el evento transaccional crítico que convierte una intención en una reserva real. Si no se retiene la garantía, el propietario queda expuesto a pérdidas económicas por daños.

**Prueba Independiente**: Enviar un *payload* de pago simulado. Validar que la pasarela de pagos retorne una transacción exitosa (Charge) por el monto del alquiler, y una autorización (Hold) separada por el monto del depósito de garantía.

**Escenarios de Aceptación**:

1. **Escenario**: Pago y retención exitosos.
   - **Dado** que un turista ingresa datos válidos de su tarjeta para una reserva.
   - **Cuando** el Módulo de Finanzas procesa la transacción.
   - **Entonces** se debita de la tarjeta el costo total de la reserva, se congela (Hold) el monto del depósito de garantía, y se informa al Módulo de Reservas (Módulo 2) que el pago fue exitoso para que levante el TTL (Bloqueo Temporal).

---

### Historia de Usuario 2 - Liberación de inventario ante falla de pago (Prioridad: P1)

Como Módulo de Finanzas (Módulo 3), necesito notificar de inmediato al Módulo de Reservas (Módulo 2) si la transacción con tarjeta de crédito es rechazada, para que no asuma que la reserva es válida y el activo vuelva a estar disponible tras el TTL de 15 minutos.

**Por qué esta prioridad**: Evita el secuestro de inventario por tarjetas sin fondos, permitiendo que otro cliente legítimo pueda alquilar la embarcación.

**Prueba Independiente**: Simular una transacción con una tarjeta declinada (fondos insuficientes). Validar que el Módulo de Finanzas devuelva un error claro y registre el intento fallido sin emitir comprobantes de pago.

**Escenarios de Aceptación**:

1. **Escenario**: Tarjeta rechazada por fondos insuficientes.
   - **Dado** que un turista intenta pagar, pero su tarjeta no tiene cupo.
   - **Cuando** el procesador de pagos rechaza el cargo.
   - **Entonces** el Módulo 3 notifica el error de pago, la reserva permanece en estado "Reservado" (pendiente) y, si el turista no usa otra tarjeta antes de 15 minutos, el Módulo 2 libera la embarcación.

### Casos Extremos (Edge Cases)

- ¿Qué sucede si la tarjeta del usuario tiene fondos para el alquiler pero no tiene límite suficiente para realizar el "Hold" del Depósito de Garantía?
- ¿Cómo actúa el sistema ante caídas de servicio de la pasarela de pagos principal (ej. Stripe)? ¿Existe un *fallback*?
- ¿Qué pasa si ocurre un doble click en el botón de pago por parte del usuario? (Prevención de duplicidad).

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 3 DEBE procesar los pagos interactuando con una pasarela segura (ej. Stripe, PayPal), cumpliendo con normativas PCI-DSS (no almacenar números de tarjeta enteros).
- **RF-002**: El sistema DEBE ejecutar operaciones duales: `Charge` (cargo inmediato por el servicio) y `Hold`/`Auth` (congelamiento de fondos para la garantía).
- **RF-003**: El Módulo 3 DEBE implementar mecanismos de idempotencia en la creación de pagos para evitar cargos duplicados a un mismo ID de reserva.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Transacción de Pago]**: Registro con `transactionId`, `amountCharged`, `amountHeld`, `status` (SUCCESS, FAILED, DECLINED).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: 100% de las transacciones exitosas ejecutan el "Hold" del depósito de garantía; cero reservas confirmadas sin garantía activa.
- **CE-002**: Tasa de errores por cobros duplicados igual a 0%, garantizada por el uso de llaves de idempotencia en cada solicitud de pago.