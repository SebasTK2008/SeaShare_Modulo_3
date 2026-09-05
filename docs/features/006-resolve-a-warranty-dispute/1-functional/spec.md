# Especificación de Funcionalidad: UC06 - resolver disputa de garantía

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Devolución completa del Depósito de Garantía (Prioridad: P1)

Como Sistema Financiero (Módulo 3), al completarse satisfactoriamente el alquiler sin reportes de daños por parte del anfitrión en el periodo de limpieza/mantenimiento, quiero procesar la devolución íntegra del 100% del depósito de garantía retenido temporalmente a la tarjeta del turista.

**Por qué esta prioridad**: Es el flujo feliz de la inmensa mayoría de las reservas. Retener dinero indebidamente por no tener este flujo causaría problemas legales y pérdida de usuarios.

**Prueba Independiente**: Simular una reserva finalizada. Iniciar la orden de liberación del depósito y validar en el ledger financiero que se registra un movimiento de `EGRESO` hacia el turista por el valor exacto del depósito, dejando el saldo retenido en 0.

**Escenarios de Aceptación**:

1. **Escenario**: Alquiler sin incidentes.
   - **Dado** una embarcación que regresa al estado "Disponible" tras su limpieza.
   - **Cuando** no hay reclamos de daños registrados en un plazo de 48 horas post-reserva.
   - **Entonces** el sistema libera el bloqueo en la tarjeta de crédito del turista por el valor total del depósito.

---

### Historia de Usuario 2 - Retención de garantía a favor del anfitrión por daños menores (Prioridad: P1)

Como Agente de Resolución / Administrador Financiero, necesito poder ejecutar un cobro parcial o total sobre el depósito de garantía retenido, basándome en una queja o evidencia de daño aportada por el anfitrión, liquidando el monto a su favor y devolviendo el remanente (si existe) al turista.

**Por qué esta prioridad**: Proteger los activos físicos de los propietarios es la base de confianza del marketplace. El módulo de finanzas debe poder ejecutar este cobro de forma segura.

**Prueba Independiente**: Tomar un depósito retenido de $500, ejecutar una resolución a favor del anfitrión por $200 de daños, y validar que se generan dos transacciones: dispersión de $200 al propietario y devolución de $300 al turista.

**Escenarios de Aceptación**:

1. **Escenario**: Cobro parcial de garantía por tapicería rota.
   - **Dado** una reserva con un depósito congelado de $500.
   - **Cuando** la resolución de una disputa determina daños menores a favor del propietario por $200.
   - **Entonces** el Módulo 3 captura los $500 y procesa la Matriz de Liquidación correspondiente, depositando $200 a la cuenta del anfitrión y reembolsando $300 al arrendatario.

### Casos Extremos (Edge Cases)

- ¿Qué sucede si el monto del daño reportado por el anfitrión excede el monto del depósito de garantía retenido?
- ¿Cómo se maneja la disputa si el turista solicita un "Chargeback" directamente con su banco emisor de la tarjeta?
- ¿Qué ocurre si la tarjeta del usuario expira durante el transcurso de una reserva de larga duración y se intenta devolver el depósito?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 3 DEBE tener la capacidad de ejecutar capturas parciales y liberaciones totales sobre el "Bloqueo Temporal" o depósito asociado a una reserva.
- **RF-002**: El sistema DEBE registrar toda resolución en el Ledger de Registros Financieros (UC02) reflejando claramente el destino de los fondos en estado de garantía.
- **RF-003**: El Módulo 3 DEBE requerir una justificación obligatoria o enlace a la evidencia almacenada cuando un Agente de Resolución ejecuta un cobro sobre la garantía.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Disputa de Garantía]**: Entidad que relaciona a la Reserva con el monto disputado, justificación y estado de la resolución.
- **[Movimiento de Ledger]**: Registro de la dispersión final de la garantía una vez resuelta (Ingresos/Egresos).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: 100% de los depósitos no disputados se liberan automáticamente en las 48 horas posteriores al término de la navegación.
- **CE-002**: Reducción de discrepancias contables relacionadas a garantías no conciliadas, manteniendo a cero las retenciones huérfanas o sin estado final por más de 30 días.

