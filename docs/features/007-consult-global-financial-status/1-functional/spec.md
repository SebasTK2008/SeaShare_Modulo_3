# Especificación de Funcionalidad: UC07 - Consultar Estado Financiero Global

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Visualización del balance financiero agregado (Prioridad: P1)

Como Administrador Financiero, quiero consultar un balance global que cruce los Ingresos contra los Egresos dentro de un rango de fechas, para analizar la comisión neta de la plataforma y el pago dispersado al propietario tras aplicar políticas de reembolsos y cancelaciones[cite: 6].

**Contexto del Sistema (Flujo)**: 
1. El Administrador Financiero accede al panel y define un periodo temporal.
2. El frontend solicita al Módulo 3 el cálculo agregado.
3. El Módulo 3 suma los registros de `INGRESO`, resta los registros de `EGRESO` correspondientes a ese periodo, y devuelve los totales netos.

**Por qué esta prioridad**: Esencial para conciliación contable macro, midiendo el impacto de las devoluciones en la rentabilidad.

**Prueba Independiente**: Solicitar el balance global con un rango de fechas, validando que el JSON retorne el cálculo exacto de (Total Ingresos - Total Egresos) para todas las categorías.

**Escenarios de Aceptación**:
1. **Escenario**: Cálculo del balance global neto.
   - **Dado** que se requiere el cierre contable de un periodo.
   - **Cuando** se aplica el filtro de fechas.
   - **Entonces** el Módulo 3 calcula el total de valores netos restando los Egresos de los Ingresos, devolviendo el flujo de caja real de Valor Bruto, Comisiones Plataforma y Pagos al Propietario[cite: 6].

---

### Historia de Usuario 2 - Exportación del reporte de estado financiero (Prioridad: P2)

Como Administrador Financiero, quiero exportar el balance y los registros que lo componen en un formato estándar (CSV), para interoperabilidad con software contable externo.

**Por qué esta prioridad**: Reduce trabajo manual y errores de transcripción en auditorías.

**Prueba Independiente**: Ejecutar la exportación y verificar que el archivo descargado contenga el balance total y el detalle fila por fila de Ingresos y Egresos.

**Escenarios de Aceptación**:
1. **Escenario**: Descarga exitosa del reporte consolidado.
   - **Dado** que el balance global está calculado en pantalla.
   - **Cuando** el administrador exporta los datos.
   - **Entonces** el sistema genera un CSV estructurado con las sumatorias netas y los registros individuales.

---

### Casos Extremos (Edge Cases)

- ¿Qué sucede si la consulta abarca un periodo sin registros financieros?
- ¿Cómo se reflejan en el balance global los registros de `EGRESO` generados en el mes actual que corresponden a reembolsos de `INGRESOS` ocurridos el mes pasado?
- ¿Qué pasa si el volumen de registros masivos sobrepasa el *timeout* de la base de datos durante la agregación?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 3 DEBE exponer un *endpoint* para calcular el estado global exigiendo fechas de inicio y fin.
- **RF-002**: El sistema DEBE calcular valores netos procesando la lógica de partida doble: sumando montos de transacciones tipo `INGRESO` y restando montos de transacciones tipo `EGRESO`.
- **RF-003**: El sistema DEBE agrupar de forma separada un total de "Fondos Retenidos" sumando transacciones en estado `Pendiente a liquidar` o `En disputa de garantía`.
- **RF-004**: El sistema DEBE permitir la exportación del balance global en formato CSV.

### Requisitos No Funcionales

- **RNF-001**: Utilizar `BigDecimal` para evitar pérdida de precisión en agregaciones masivas.
- **RNF-002**: Optimizar las consultas mediante índices en base de datos para garantizar eficiencia en cálculos de gran volumen.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Balance Global]**: Entidad DTO resultante de la consolidación de registros financieros:
  - `netGrossRevenue`: Suma Ingresos Brutos - Suma Egresos Brutos.
  - `netPlatformCommission`: Comisiones retenidas - Comisiones devueltas.
  - `netOwnerPayouts`: Pagos dispersados - Pagos extornados.
  - `totalRetainedFunds`: Total congelado temporalmente.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: El 95% de consultas de balance de periodos amplios responden en menos de 2 segundos.
- **CE-002**: Auditorías manuales demuestran 100% de coincidencia entre los totales netos del Balance Global y la suma algebraica de Ingresos/Egresos.