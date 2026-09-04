# Especificación de Funcionalidad: UC02 - Consultar Registros Financieros

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta general y filtrado de registros financieros (Prioridad: P1)

Como Administrador Financiero, al ingresar al panel de finanzas en el Módulo 3 (Liquidación, Seguros y Dispersión de Fondos), quiero consultar una lista paginada de todos los registros financieros y poder filtrarlos por fecha y estado, para tener una visión clara de los ingresos brutos, comisiones retenidas y pagos pendientes de dispersar.

**Contexto del Sistema (Flujo)**: 
1. El Administrador Financiero accede a la interfaz de reportes.
2. El frontend solicita al Módulo 3 el listado de registros pasando parámetros de paginación y filtros (ej. rango de fechas y estados específicos).
3. El Módulo 3 consulta la base de datos de registros financieros y devuelve un arreglo de DTOs con la información resumida de cada transacción.

**Por qué esta prioridad**: Es la herramienta principal para la auditoría contable y el monitoreo de la salud financiera de la plataforma de economía colaborativa.

**Prueba Independiente**: Enviar una petición HTTP GET autenticada como administrador al *endpoint* de registros financieros del Módulo 3, validando que retorne una lista paginada y que los filtros de fecha y estado restrinjan correctamente los resultados.

**Escenarios de Aceptación**:
1. **Escenario**: Consulta de registros con rango de fechas exitoso.
   - **Dado** que el Administrador Financiero necesita revisar los ingresos del último mes.
   - **Cuando** aplica un filtro de fecha de los últimos 30 días y solicita la consulta.
   - **Entonces** el Módulo 3 devuelve una lista paginada únicamente con los registros en estado "Liquidado" o "Pendiente a liquidar" dentro de ese rango de fechas.

---

### Historia de Usuario 2 - Visualización detallada de la Matriz de Reparto por reserva (Prioridad: P1)

Como Administrador Financiero, al seleccionar un registro financiero específico, quiero ver el desglose detallado de la Matriz de Reparto (Valor Bruto, Comisión de Plataforma, Seguro Náutico, Penalidades y Pago al Propietario), para auditar transacciones individuales, resolver disputas de garantía o justificar cobros.

**Por qué esta prioridad**: Es fundamental para dar soporte de nivel 2 ante quejas de los anfitriones sobre pagos o retenciones, y para garantizar la transparencia del reparto de ingresos.

**Prueba Independiente**: Solicitar al Módulo 3 los detalles de un registro financiero por su ID y validar que el JSON de respuesta incluya todos los campos desglosados de la matriz, y que la suma matemática cuadre perfectamente (Valor Bruto - Comisión - Seguro = Pago al Propietario).

**Escenarios de Aceptación**:
1. **Escenario**: Visualización del desglose matemático de una reserva completada.
   - **Dado** que el administrador hace clic en el ID de un registro financiero específico.
   - **Cuando** el sistema solicita los detalles al Módulo 3.
   - **Entonces** se devuelve un objeto con el detalle exacto: Valor Alquiler Bruto, deducción del porcentaje de Comisión, tarifa retenida de Seguro y el Monto Neto dispersado al propietario.

---

### Casos Extremos (Edge Cases)

- ¿Qué sucede si el Administrador Financiero solicita un rango de fechas demasiado amplio (ej. 5 años de registros) que podría saturar la memoria o el tiempo de respuesta de la base de datos?
- ¿Cómo se muestran en el reporte los registros financieros correspondientes a reservas con "Cancelación Flexible" que se encuentran en estado "Reembolsado" (100%) y no generaron comisión?
- ¿Qué pasa si se consulta el registro de una reserva que se encuentra en un estado transitorio como "Procesando Cobro" (comunicación activa con la pasarela)?
- ¿Cómo se refleja en el registro financiero si el estado es "En disputa de garantía" debido a un reclamo por daños al finalizar el viaje?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 3 DEBE exponer un *endpoint* seguro para listar los registros financieros, requiriendo parámetros de paginación obligatorios (ej. `page` y `size`).
- **RF-002**: El sistema DEBE permitir filtrar la lista de registros por rango de fechas (fecha de inicio y fecha de fin) y obligatoriamente por los siguientes estados financieros: `Pendiente a liquidar`, `Liquidado`, `Cancelado`, `Reembolsado` y `En disputa de garantía`.
- **RF-003**: El Módulo 3 DEBE devolver para transacciones individuales el desglose de la Matriz de Reparto establecida: Valor Alquiler Bruto, Comisión Plataforma, Tarifa de Seguro, Penalidad por Cancelación (si aplica) y Pago al Propietario.
- **RF-004**: El sistema DEBE identificar claramente en el registro financiero si la fuente de los fondos proviene de una reserva normal completada, de un "No-Show" o de una penalidad por cancelación moderada/tardía.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE validar estrictamente los roles y permisos, asegurando que solo usuarios autenticados con el rol de `Admin Financiero` puedan consumir estos *endpoints*.
- **RNF-002**: El sistema DEBE proteger la información sensible, asegurando que los DTOs de respuesta no expongan datos crudos de tarjetas de crédito o credenciales de la pasarela de pagos, cumpliendo con estándares de seguridad.
- **RNF-003**: El sistema DEBE imponer un límite máximo estricto en las consultas por rango de fechas (por ejemplo, un máximo de 6 meses por solicitud) para prevenir tiempos de espera excesivos y sobrecarga en la base de datos.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Registro Financiero]**: Representa el estado y desglose financiero de una transacción en la plataforma. Sus atributos clave incluyen:
  - `id`: Identificador único.
  - `bookingId`: Referencia a la reserva que originó el registro.
  - `status`: Estado del ciclo de vida financiero. Los valores permitidos son:
    - **Pendiente a liquidar**: El cobro fue exitoso pero los fondos aún no se dispersan al anfitrión.
    - **Liquidado**: Los fondos ya fueron transferidos/dispersados al anfitrión.
    - **Cancelado**: La reserva fue anulada (puede derivar en penalidades a favor del anfitrión).
    - **Reembolsado**: El dinero fue devuelto en su totalidad al arrendatario.
    - **En disputa de garantía**: Los fondos del depósito de garantía están congelados por un reclamo de daños en revisión.
    - **Procesando Cobro**: (Estado transitorio) La plataforma está esperando confirmación de la pasarela de pagos.
  - Valores monetarios (tipo BigDecimal): `grossValue` (Valor Bruto), `platformCommission` (Comisión), `ownerPayout` (Pago a Propietario), `insuranceFee` (Seguro).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Rendimiento de Consulta, "El 95% de las consultas de listados paginados de registros financieros con filtros de un mes deben resolverse y responderse en menos de 800ms".
- **CE-002**: Integridad Contable, "Auditorías automatizadas demuestran 100% de coincidencia entre los montos mostrados en los registros del Módulo 3 y las dispersiones reales ejecutadas en la pasarela de pagos".
- **CE-003**: Usabilidad y Eficiencia Operativa, "El equipo de soporte/administración financiera reduce en un 40% el tiempo promedio de resolución de disputas de pagos con anfitriones gracias a la visualización clara de los estados y el desglose en el registro financiero".