# Especificación de Funcionalidad: UC05 - Conigurar parametros financieros globales

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Configuración de porcentajes y tarifas fijas (Prioridad: P1)

Como Administrador de la Plataforma, necesito establecer y modificar los parámetros financieros globales en el Módulo 3 (Finanzas), tales como el porcentaje de comisión de la plataforma y el costo fijo del seguro náutico por pasajero, para que todos los cálculos de la Matriz de Liquidación se basen en valores actualizados y centralizados.

**Por qué esta prioridad**: Sin estas variables, el sistema no puede hacer la dispersión de fondos, no hay ingresos para la empresa (comisión), y no se pueden cobrar seguros.

**Prueba Independiente**: Enviar una petición autenticada como SuperAdmin para actualizar el porcentaje de comisión de 15% a 20%. Posteriormente, simular una cotización y validar que la plataforma retiene ahora el 20% del valor bruto.

**Escenarios de Aceptación**:

1. **Escenario**: Actualización exitosa del porcentaje de comisión.
   - **Dado** que la plataforma decide cambiar su modelo de negocio cobrando más comisión.
   - **Cuando** el Administrador guarda un nuevo valor de comisión (ej. 18%) en los parámetros iniciales.
   - **Entonces** las reservas creadas a partir de ese instante utilizan el 18% para el cálculo del ingreso neto de la plataforma y el pago al propietario.

---

### Historia de Usuario 2 - Definición de penalidades de cancelación (Prioridad: P2)

Como Administrador de la Plataforma, necesito poder ajustar los porcentajes de penalidad de la política de cancelaciones (Flexible >72h, Moderada 72h-24h, Tardía <24h), para asegurar la correcta liquidación de reembolsos al turista y compensaciones al anfitrión.

**Por qué esta prioridad**: Las políticas pueden evolucionar, requiriendo flexibilidad del sistema sin necesidad de cambios en el código duro.

**Prueba Independiente**: Cambiar la penalidad moderada de 50% a 40% y ejecutar una cancelación de prueba en el rango de 48h, validando que se devuelva el 60% al usuario y se disperse el 40% al anfitrión.

**Escenarios de Aceptación**:

1. **Escenario**: Modificación de ventana de cancelación.
   - **Dado** un cambio en la política comercial de Sea-Share.
   - **Cuando** el Administrador ajusta las variables de cancelación.
   - **Entonces** el Módulo 3 aplica las nuevas fórmulas de retención y reembolso para todas las cancelaciones procesadas en adelante.

### Casos Extremos (Edge Cases)

- ¿Qué sucede con las reservas existentes/pasadas si se cambia la comisión global en medio de su ciclo de vida? (Deben respetar los valores del momento de creación).
- ¿Qué pasa si el Administrador ingresa un porcentaje de comisión superior al 100% o valores negativos?
- ¿Cómo se audita qué administrador cambió un parámetro crítico y cuándo?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE proporcionar una interfaz o API para establecer parámetros globales: `% Comisión Plataforma`, `Costo Seguro por Pasajero`, `Penalidad Cancelación Moderada`, `Penalidad Cancelación Tardía`.
- **RF-002**: Las modificaciones a los parámetros DEBEN aplicarse únicamente a reservas creadas de forma posterior a la fecha del cambio; nunca retroactivamente.
- **RF-003**: El sistema DEBE guardar un historial/log de auditoría de los cambios de parámetros globales.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Configuración Financiera Global]**: Entidad que almacena llave-valor con vigencia de fechas (ej. `platform_commission`, `valid_from`, `valid_to`).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: 100% de inmutabilidad en los parámetros financieros aplicados a una reserva confirmada, garantizando que cambios posteriores en la configuración global no la afecten.
- **CE-002**: Cobertura de validación, garantizando que el 100% de los intentos de ingresar valores atípicos (menores a 0 o mayores a 100 en comisiones) son rechazados por la API.

