# Especificación de Funcionalidad: Obtener el Estado de la Reserva

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del estado de la reserva para procesos financieros (Prioridad: P1)

Como Módulo de Finanzas (Módulo 3), necesito obtener el estado actual de una reserva (ej. Disponible, Reservado, En Navegación, Cancelado) desde el Módulo de Operación (Módulo 2), para poder determinar si se deben aplicar reglas de liquidación, como cobrar penalidades por cancelación o liberar el depósito de garantía.

**Por qué esta prioridad**: Es el puente principal entre la operación física de los tiempos de la embarcación y los movimientos de dinero. Sin conocer el estado de la reserva, el Módulo 3 no sabe cuándo dispersar el dinero o si hubo un "No-Show".

**Prueba Independiente**: El Módulo de Finanzas envía una petición HTTP al Módulo de Reservas consultando el estado de una reserva específica por su ID. Se debe validar que la respuesta devuelva correctamente el estado actual de la reserva (ej. "Cancelado").

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa de estado "No-Show"
   - **Dado** una reserva que ha superado la ventana de tolerancia de 30 minutos sin presentación del arrendatario.
   - **Cuando** el Módulo de Finanzas consulta el estado de la reserva.
   - **Entonces** el Módulo de Reservas devuelve el estado "No-Show", permitiendo que el Módulo de Finanzas ejecute el cobro del 100% como compensación al anfitrión.

---

### Historia de Usuario 2 - Verificación del Bloqueo Temporal (TTL) (Prioridad: P2)

Como Módulo de Operación (Módulo 2), necesito validar con el Módulo de Finanzas si el pago de una reserva en estado "Reservado" ha sido confirmado antes de que expire su Time-To-Live (TTL) de 15 minutos, para confirmar la reserva o liberar el activo a estado "Disponible".

**Por qué esta prioridad**: El bloqueo de activos sin confirmación de pago impide que otros usuarios alquilen, generando pérdida de ingresos.

**Prueba Independiente**: Simular un TTL que está por expirar y consultar el estado del pago, verificando que si el pago falla, la embarcación vuelve a estado "Disponible".

**Escenarios de Aceptación**:

1. **Escenario**: Expiración del TTL sin pago confirmado
   - **Dado** una embarcación en estado "Reservado" durante 15 minutos.
   - **Cuando** expira el TTL y el Módulo 2 verifica el estado del pago con Finanzas, el cual resulta "No Pagado".
   - **Entonces** el Módulo 2 cambia el estado del activo a "Disponible" automáticamente.

### Casos Extremos (Edge Cases)

- ¿Qué sucede si hay pérdida de conexión entre los módulos en el minuto 14 del TTL?
- ¿Cómo se maneja un pago que entra con retraso justo cuando el estado se cambió a "Disponible"?
- ¿Qué pasa si el sistema de finanzas registra un pago pero el Módulo 2 marca un "No-Show" por error manual?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE exponer un *endpoint* para consultar el estado actual de una reserva (Módulo 2).
- **RF-002**: El sistema DEBE exponer un *endpoint* para verificar el estado de pago asociado a un ID de reserva (Módulo 3).
- **RF-003**: El Módulo 2 DEBE aplicar la regla de TTL de 15 minutos para retornar un activo a "Disponible" si no hay confirmación financiera.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Estado de Reserva]**: Representa la fase del ciclo de vida (Reservado, En Navegación, No-Show, Cancelado).
- **[Estado de Pago]**: Representa la situación transaccional (Pendiente, Pagado, Reembolsado).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Las liberaciones de embarcaciones por TTL expirado se completan en un margen de error menor a 2 segundos después del minuto 15.
- **CE-002**: 0% de casos de doble reserva debido a bloqueos fantasma o inconsistencias entre el estado de pago y la disponibilidad.