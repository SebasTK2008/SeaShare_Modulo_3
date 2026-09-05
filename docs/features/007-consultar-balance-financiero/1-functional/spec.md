# Especificación de Funcionalidad: UC07 - Consultar Balance financiero

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Cálculo del balance financiero (Prioridad: P1)

Como Administrador Financiero, quiero que el sistema calcule el balance financiero, tomando los ingresos y egresos de un periodo seleccionado (quincenal, mensual o trimestral), para poder procesar la información y prepararla para su visualización.

**Contexto del Sistema (Flujo)**: 
1. El Administrador Financiero accede al panel y define uno de los periodos disponibles.
2. El frontend solicita al sistema el cálculo.
3. El sistema suma los registros de ingresos y resta los egresos correspondientes a ese periodo, devolviendo los totales.

**Por qué esta prioridad**: Esencial para poder generar cualquier tipo de visualización o reporte.

**Prueba Independiente**: Solicitar el balance financiero con un periodo específico (por ejemplo, quincenal), validando que el sistema retorne el cálculo exacto de la operación de ingresos menos egresos.

**Escenarios de Aceptación**:
1. **Escenario**: Cálculo del balance financiero.
   - **Dado** que se requiere procesar la información contable de uno de los periodos disponibles.
   - **Cuando** se solicita el cálculo del balance financiero para ese periodo.
   - **Entonces** el sistema realiza el cálculo restando los egresos de los ingresos.

---

### Historia de Usuario 2 - Visualización del balance financiero (Prioridad: P1)

Como Administrador Financiero, quiero consultar el balance financiero dentro de un periodo (quincenal, mensual o trimestral), para analizar la comisión neta de la plataforma y el pago dispersado al propietario tras aplicar políticas de reembolsos y cancelaciones.

**Contexto del Sistema (Flujo)**: 
1. El Administrador Financiero accede al panel y selecciona uno de los periodos disponibles.
2. El frontend solicita al sistema los datos previamente calculados.
3. El sistema muestra la información en pantalla de forma estructurada.

**Por qué esta prioridad**: Esencial para conciliación contable macro, midiendo el impacto de las devoluciones en la rentabilidad.

**Prueba Independiente**: Visualizar el balance financiero con un periodo seleccionado, validando que la pantalla muestre los datos correctamente estructurados.

**Escenarios de Aceptación**:
1. **Escenario**: Visualización del balance financiero en pantalla.
   - **Dado** que el cálculo del balance financiero se ha completado exitosamente.
   - **Cuando** el administrador accede a la vista de resultados para ese periodo.
   - **Entonces** el sistema muestra para visualización: el balance financiero (ingresos vs egresos consolidados), las comisiones de la plataforma, los pagos dispersados a los propietarios y el flujo de caja real.

---

### Historia de Usuario 3 - Exportación del reporte de balance financiero (Prioridad: P2)

Como Administrador Financiero, quiero exportar el balance financiero y los registros que lo componen en un formato estándar (CSV), para interoperabilidad con software contable externo.

**Por qué esta prioridad**: Reduce trabajo manual y errores de transcripción en auditorías.

**Prueba Independiente**: Ejecutar la exportación y verificar que el archivo descargado contenga el balance financiero total y el detalle fila por fila de ingresos y egresos.

**Escenarios de Aceptación**:
1. **Escenario**: Descarga exitosa del reporte consolidado.
   - **Dado** que el balance financiero está calculado en pantalla.
   - **Cuando** el administrador quiere exportar los datos.
   - **Entonces** el sistema genera un CSV estructurado con el balance financiero consolidado y los registros individuales.

---

### Casos Extremos (Edge Cases)

- ¿Qué sucede si la consulta abarca un periodo sin registros financieros?
- ¿Cómo se reflejan en el balance financiero los registros de egreso generados en el periodo actual que corresponden a reembolsos de ingresos ocurridos en el periodo pasado?
- ¿Qué pasa si el volumen de registros masivos sobrepasa el *timeout* de la base de datos durante la agregación?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE exponer un *endpoint* para calcular el balance financiero exigiendo un periodo (quincenal, mensual o trimestral).
- **RF-002**: El sistema DEBE calcular el balance financiero procesando la lógica de partida doble: sumando montos de transacciones de ingreso y restando montos de transacciones de egreso.
- **RF-003**: El sistema DEBE agrupar de forma separada un total de "Fondos Retenidos" sumando transacciones en estado `Pendiente a liquidar` o `En disputa de garantía`.
- **RF-004**: El sistema DEBE permitir la exportación del balance financiero en formato CSV.

### Requisitos No Funcionales

- **RNF-001**: Utilizar `BigDecimal` para evitar pérdida de precisión en agregaciones masivas.
- **RNF-002**: Optimizar las consultas mediante índices en base de datos para garantizar eficiencia en cálculos de gran volumen.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Balance Financiero]**: Entidad DTO resultante de la consolidación de registros financieros:
  - `revenue`: Suma Ingresos - Suma Egresos.
  - `platformCommission`: Comisiones retenidas - Comisiones devueltas.
  - `ownerPayouts`: Pagos dispersados - Pagos extornados.
  - `totalRetainedFunds`: Total congelado temporalmente.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: El 95% de consultas de balance de periodos amplios responden en menos de 2 segundos.
- **CE-002**: Auditorías manuales demuestran 100% de coincidencia entre el Balance Financiero y la suma de los registros.