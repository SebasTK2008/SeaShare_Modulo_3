# Especificación de Funcionalidad: Solicitar Información de Pago

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta del desglose de pago para Checkout (Prioridad: P1)

Como Módulo de Reservas (Módulo 2), al momento en que el turista inicia el flujo de pago, necesito solicitar al Módulo de Finanzas (Módulo 3) la información financiera completa para el checkout, incluyendo la tarifa base calculada, comisiones, tarifas dinámicas, seguro náutico obligatorio por pasajero y depósito de garantía.

**Por qué esta prioridad**: Es indispensable mostrar al usuario el monto total real y exacto a pagar antes de capturar sus datos de tarjeta de crédito, garantizando transparencia.

**Prueba Independiente**: Solicitar al Módulo de Finanzas el desglose de pago para una reserva de 2 pasajeros en fin de semana. Validar que la respuesta incluya tarifa ajustada por fin de semana, seguro multiplicado por 2, y el depósito de garantía configurado, sumando el total correctamente.

**Escenarios de Aceptación**:

1. **Escenario**: Desglose exacto mostrado al cliente.
   - **Dado** que un turista decide confirmar una reserva para 4 personas y procede a pagar.
   - **Cuando** el frontend solicita la información de pago final al Módulo de Finanzas.
   - **Entonces** el sistema devuelve un objeto detallado con la tarifa base, tarifa de seguro (por 4 pasajeros), tarifa de la plataforma y depósito de garantía retenido temporalmente.

---

### Historia de Usuario 2 - Aplicación de Tarifas Dinámicas (Prioridad: P2)

Como Módulo de Finanzas (Módulo 3), quiero aplicar reglas de negocio automáticas de tarifas dinámicas (ej. recargos por fin de semana o temporada alta) cuando el Módulo de Reservas solicite la información de pago, para maximizar las ganancias del anfitrión y la plataforma de acuerdo al mercado.

**Por qué esta prioridad**: Permite el modelo de negocio colaborativo de Sea-Share donde los precios fluctúan según demanda.

**Prueba Independiente**: Solicitar información de pago para fechas en temporada alta y validar que se aplique el factor multiplicador correspondiente a la tarifa base original del Módulo 1.

**Escenarios de Aceptación**:

1. **Escenario**: Cobro adicional en fin de semana.
   - **Dado** que la reserva abarca días sábado y domingo.
   - **Cuando** el Módulo de Reservas solicita la información de pago.
   - **Entonces** el Módulo de Finanzas devuelve un Valor Alquiler Bruto incrementado por la tarifa dinámica de fin de semana configurada.

### Casos Extremos (Edge Cases)

- ¿Qué sucede si el número de pasajeros supera la capacidad máxima de la embarcación configurada en el Módulo 1?
- ¿Cómo se manejan los cupones de descuento sobre la comisión de la plataforma?
- ¿Qué pasa si las reglas de tarifas dinámicas se superponen (ej. Fin de semana + Temporada alta)?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 3 DEBE calcular el costo total sumando: `(Tarifa Dinámica * Duración) + (Seguro * Pasajeros) + Depósito de Garantía`.
- **RF-002**: El sistema DEBE exponer un desglose transparente de todos los cobros, distinguiendo montos finales de retenciones temporales.
- **RF-003**: El Módulo 3 DEBE validar la capacidad de pasajeros permitida contra el inventario (Módulo 1) para calcular el seguro náutico adecuadamente.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Desglose de Pago]**: DTO con `valorBruto`, `seguroNauticoTotal`, `depositoGarantia`, `totalAPagar`.
- **[Regla de Tarifa Dinámica]**: Factor de ajuste de precio (ej. `1.2x` para fines de semana).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: 100% de coincidencia matemática entre el desglose mostrado al usuario y los fondos efectivamente capturados en la pasarela de pagos.
- **CE-002**: Las peticiones de información de pago se responden en menos de 600ms para no afectar la conversión de reservas.

