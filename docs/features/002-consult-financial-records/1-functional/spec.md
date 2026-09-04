# Especificación de Funcionalidad: UC02 - Consultar Registros Financieros

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta general y filtrado de registros financieros (Prioridad: P1)

Como Administrador Financiero, al ingresar al panel de finanzas en el Módulo 3, quiero consultar una lista paginada de todos los registros financieros y poder filtrarlos por fecha, tipo de operación (Ingreso/Egreso) y estado, para tener una visión clara del flujo de caja, comisiones retenidas y pagos pendientes.

**Contexto del Sistema (Flujo)**: 
1. El Administrador Financiero accede a la interfaz de reportes.
2. El frontend solicita al Módulo 3 el listado de registros pasando parámetros de paginación y filtros (rango de fechas, tipo y estado).
3. El Módulo 3 consulta la base de datos (ledger) y devuelve un arreglo de DTOs con la información de cada transacción.

**Por qué esta prioridad**: Es la herramienta principal para la auditoría contable y el monitoreo de la salud financiera de la plataforma.

**Prueba Independiente**: Enviar una petición HTTP GET autenticada como administrador al *endpoint* del Módulo 3, validando que retorne una lista paginada y que los filtros apliquen correctamente sobre el tipo de operación y rango de fechas.

**Escenarios de Aceptación**:
1. **Escenario**: Consulta de registros en un periodo específico.
   - **Dado** que el Administrador Financiero necesita revisar los movimientos de un periodo contable.
   - **Cuando** aplica un filtro de fechas y solicita la consulta.
   - **Entonces** el Módulo 3 devuelve una lista paginada únicamente con los registros de Ingreso y Egreso comprendidos dentro de ese rango temporal.

---

### Historia de Usuario 2 - Visualización detallada de la Matriz de Liquidación (Prioridad: P1)

Como Administrador Financiero, al seleccionar un registro financiero, quiero ver el desglose detallado de la Matriz de Liquidación, incluyendo Valor Alquiler Bruto, Comisión Plataforma y Pago al Propietario[cite: 6]. Esto es necesario para auditar cómo se dividieron los fondos de un ingreso, o cuánto se devolvió exactamente en un registro de egreso.

**Por qué esta prioridad**: Fundamental para dar soporte ante quejas de anfitriones sobre pagos o retenciones y garantizar la transparencia del reparto.

**Prueba Independiente**: Solicitar al Módulo 3 los detalles de un registro por su ID y validar que el JSON incluya todos los campos de la matriz de liquidación, cuadrando matemáticamente según si es un ingreso o un egreso[cite: 6].

**Escenarios de Aceptación**:
1. **Escenario**: Visualización del desglose de un ingreso.
   - **Dado** que el administrador visualiza un registro de tipo Ingreso.
   - **Cuando** el sistema solicita los detalles al Módulo 3.
   - **Entonces** se devuelve el valor bruto cobrado, deducción de comisión de plataforma, seguro náutico y el pago neto al propietario[cite: 6].

---

### Casos Extremos (Edge Cases)

- ¿Qué sucede si el Administrador Financiero solicita un rango de fechas demasiado amplio que podría saturar la base de datos?
- ¿Cómo se vincula visualmente un registro de **Egreso** (reembolso parcial) derivado de una cancelación moderada que implica una penalidad del 50% con su respectivo **Ingreso** original[cite: 6]?
- ¿Qué pasa si se consulta un registro asociado a una reserva cuyo cobro está "Procesando" y aún no se consolida en el ledger?
- ¿Cómo se refleja el movimiento financiero si una disputa por un Depósito de Garantía resulta a favor del propietario, transformando un monto retenido temporalmente en un pago efectivo[cite: 6]?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El Módulo 3 DEBE exponer un *endpoint* seguro para listar registros, requiriendo paginación obligatoria.
- **RF-002**: El sistema DEBE permitir filtrar por rango de fechas, estado, y tipo de operación (`INGRESO`, `EGRESO`).
- **RF-003**: El Módulo 3 DEBE devolver el desglose de la Matriz de Liquidación para cada transacción, donde los valores de un `EGRESO` deben representar salidas de dinero, tales como el reembolso del 100% por una cancelación flexible[cite: 6].
- **RF-004**: Todo registro de tipo `EGRESO` DEBE contener una referencia (ID) al registro de `INGRESO` original que está balanceando.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE validar estrictamente que solo usuarios autenticados con rol `Admin Financiero` consuman estos *endpoints*.
- **RNF-002**: El sistema DEBE proteger información sensible de pago en los DTOs.
- **RNF-003**: El sistema DEBE imponer un límite máximo estricto en el rango de fechas permitido por consulta para prevenir sobrecargas.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Registro Financiero (Ledger)]**: 
  - `id`: Identificador único.
  - `relatedBookingId`: Referencia a la reserva.
  - `parentRecordId`: Referencia a un ingreso original (usado solo si el registro es un Egreso).
  - `transactionType`: `INGRESO` (pagos recibidos) o `EGRESO` (reembolsos totales o parciales).
  - `status`: `Procesando`, `Pendiente a liquidar`, `Liquidado`, `En disputa de garantía`, `Anulado`.
  - Valores (BigDecimal): `grossValue`, `platformCommission`, `ownerPayout`, `insuranceFee`.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: El 95% de las consultas paginadas con filtros se resuelven en menos de 800ms.
- **CE-002**: Auditorías automatizadas demuestran 100% de coincidencia entre los montos de Ingresos/Egresos del Módulo 3 y la pasarela de pagos.