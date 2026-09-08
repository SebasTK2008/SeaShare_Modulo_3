# Especificación de Funcionalidad: UC11 - Configurar Parámetros Financieros Globales

**Creado**: 2026-09-07

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Configurar parámetros de comisión, seguro y depósito (Prioridad: P1)

Como Administrador Financiero, quiero poder definir y actualizar el porcentaje de comisión de la plataforma, la tarifa plana del seguro náutico por pasajero y las reglas para el cálculo del depósito de garantía, para que los cálculos de cobro, reembolso y dispersión utilicen siempre los valores vigentes aprobados por la plataforma.

**Por qué esta prioridad**: Sin estos valores, el Módulo 3 no puede calcular correctamente los valores de alquiler, las comisiones a cobrar ni el depósito de garantía a retener, bloqueando por completo la operación financiera del negocio.

**Prueba Independiente**: Enviar al sistema una solicitud para actualizar la comisión a 15%, el seguro a $10 por pasajero y el depósito al 10% del valor del alquiler, y validar que los cálculos posteriores de otras cotizaciones o reservas reflejen inmediatamente estos nuevos valores.

**Escenarios de Aceptación**:

1. **Escenario**: Actualización exitosa de parámetros base.
   - **Dado** que el Administrador Financiero requiere ajustar la comisión y el seguro para el nuevo año.
   - **Cuando** envía los nuevos valores (ej. 15% de comisión, $10 de seguro) a través del sistema.
   - **Entonces** el sistema persiste estos nuevos parámetros globales de manera inmediata.

### Historia de Usuario 2 - Configurar rangos de fechas para tarifas dinámicas (Prioridad: P2)

Como Administrador Financiero, quiero poder configurar las fechas de inicio y fin de las temporadas altas, para que el sistema de finanzas aplique correctamente los incrementos de tarifa dinámica al proveer tarifas base y calcular reservas.

**Por qué esta prioridad**: Permite a la plataforma capitalizar sobre las épocas de alta demanda sin requerir cambios en el código.

**Prueba Independiente**: Enviar un rango de fechas de temporada alta y verificar que las cotizaciones que caen en ese rango reflejen el multiplicador configurado para temporada alta.

**Escenarios de Aceptación**:

1. **Escenario**: Definición de fechas de temporada alta.
   - **Dado** que se acerca el periodo vacacional de verano.
   - **Cuando** el Administrador Financiero configura del 1 de diciembre al 31 de marzo como temporada alta.
   - **Entonces** el sistema guarda estas fechas y las activa para todas las cotizaciones y cálculos que se superpongan con este periodo.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Administrador Financiero intenta configurar un porcentaje de comisión negativo o mayor al 100%?**
  El sistema rechaza la solicitud con un error de validación, asegurando que el porcentaje se mantenga en un rango lógico (0 - 100).

- **¿Qué sucede con las reservas que ya fueron cobradas o cotizadas de forma definitiva (UC04) si el Administrador Financiero cambia la tarifa del seguro náutico o el porcentaje de comisión?**
  Los cambios en los parámetros globales aplican únicamente para las transacciones y cálculos futuros. Las reservas ya calculadas y cobradas preservan los valores vigentes al momento de su creación en su registro financiero inmutable, garantizando la consistencia contable.

- **¿Qué sucede si las fechas de inicio y fin para la tarifa dinámica están invertidas (fecha fin anterior a fecha de inicio)?**
  El sistema rechaza la configuración con un error de validación, impidiendo guardar rangos de fechas inconsistentes.

- **¿Qué sucede si los parámetros financieros nunca han sido configurados (por ejemplo, en el primer despliegue del sistema)?**
  El sistema debe contar con un mecanismo de *fallback* o valores por defecto configurados a nivel de variables de entorno (o *seed* de base de datos) para garantizar que las cotizaciones y reservas no fallen por divisiones o multiplicaciones por valores nulos.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE exponer un mecanismo (endpoint/interfaz) accesible únicamente por el Administrador Financiero para actualizar los parámetros financieros.
- **RF-002**: El sistema DEBE permitir la configuración del porcentaje de comisión de la plataforma.
- **RF-003**: El sistema DEBE permitir la configuración de la tarifa plana de seguro náutico por pasajero.
- **RF-004**: El sistema DEBE permitir definir la regla de cálculo del depósito de garantía (ej. porcentaje del alquiler o monto fijo base).
- **RF-005**: El sistema DEBE almacenar estos parámetros de manera que estén disponibles globalmente para los módulos de cálculo financiero (UC01, UC04, UC10).
- **RF-006**: El sistema DEBE mantener un registro de auditoría (quién, cuándo y qué) por cada cambio realizado a los parámetros financieros.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE aplicar las validaciones de tipos de datos (`BigDecimal` para valores monetarios y porcentajes, fechas válidas).
- **RNF-002**: Las lecturas de los parámetros globales DEBEN estar altamente cacheadas en memoria para no penalizar el rendimiento de las cotizaciones en lote (UC01) que leen estos parámetros constantemente.

### Entidades Clave

- **ParametrosFinancierosGlobales (Entidad)**: Representa el conjunto de configuraciones actuales (comisión, seguro, reglas de depósito, fechas de temporada). Persistida por este caso de uso y leída por el resto del módulo financiero.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Aplicación Inmediata, "El 100% de las nuevas cotizaciones y cobros posteriores a una actualización de parámetros utilizan los nuevos valores en menos de 1 segundo tras la confirmación del guardado".
- **CE-002**: Consistencia Histórica, "Cero (0) modificaciones retroactivas en el historial de transacciones o registros financieros de reservas previas al momento de modificar un parámetro global".
- **CE-003**: Validación Estricta, "100% de los intentos de ingresar porcentajes inválidos (<0 o >100) son rechazados automáticamente por el sistema".