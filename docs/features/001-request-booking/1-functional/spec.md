# Especificación de Funcionalidad: UC01 - Solicitar Cotización de Reserva

**Creado**: 2026-08-27

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Calcular cotizaciones en lote delegando cálculos al módulo de Finanzas (Prioridad: P1)

Como Módulo de Reservas (Gestión de Reservas), al necesitar listar opciones de reserva, quiero enviar solicitudes de cotización en lote al módulo de Finanzas delegándole completamente la responsabilidad matemática, de manera que, si no se especifican fechas exactas, se asuma una duración por defecto de 1 día para calcular el costo estimado.

**Contexto del Sistema (Flujo)**: 
1. El Módulo de Reservas carga las opciones de reserva.
2. El Módulo de Reservas activa este caso de uso enviando una lista de identificadores (`boat_ids`) al módulo de Finanzas. Si aún no hay fechas seleccionadas, envía esta información vacía.
3. El módulo de Finanzas invoca el sub-caso "Proveer tarifa base" consultando la tarifa de cada bote al Módulo de Gestión de Flota.
4. El módulo de Finanzas realiza los cálculos asumiendo 1 día de duración por defecto y devuelve las cotizaciones, permitiendo que el Módulo de Reservas actúe únicamente como consumidor de la API sin hacer operaciones locales.

**Por qué esta prioridad**: Mantiene la arquitectura limpia y la separación de responsabilidades, al mismo tiempo que es fundamental mostrar precios estimados desde la búsqueda inicial para la conversión.

**Prueba Independiente**: Enviar un *payload* simulado desde el Módulo de Reservas con una lista de IDs y sin fechas al módulo de Finanzas, validando que asuma el valor por defecto (1 día) en la fórmula matemática, consulte al Módulo de Gestión de Flota y devuelva el arreglo de cotizaciones.

**Escenarios de Aceptación**:
1. **Escenario**: Cálculo delegado exitoso de múltiples cotizaciones con fecha por defecto.
   - **Dado** que el Módulo de Reservas necesita mostrar precios para 10 botes en pantalla.
   - **Cuando** envía la lista al módulo de Finanzas sin un rango de fechas definido.
   - **Entonces** el módulo de Finanzas asume 1 día de duración y 1 pasajero por defecto, calcula con éxito las cotizaciones para los 10 botes y las devuelve.

---

### Historia de Usuario 2 - Cotización individual delegada y advertencia de estimación (Prioridad: P1)

Como Módulo de Reservas (Gestión de Reservas), al solicitar los detalles de un bote específico, quiero delegar el cálculo exacto enviando el ID y el rango de fechas al módulo de Finanzas, y que este me devuelva tanto el valor total como una bandera obligatoria de advertencia (*warning*) sobre el estimado, para yo simplemente renderizarla sin manejar lógica financiera ni de reglas de negocio.

**Por qué esta prioridad**: Mantiene el código del Módulo de Reservas libre de multiplicaciones de tarifas, y atiende un requisito crítico de negocio para evitar fricciones y quejas de los usuarios por posibles recargos.

**Prueba Independiente**: Verificar que al enviar fechas de inicio y fin desde el Módulo de Reservas, el módulo de Finanzas devuelve la cotización exacta calculando los días de diferencia y adjunta un texto/flag de advertencia sobre la naturaleza del estimado.

**Escenarios de Aceptación**:
1. **Escenario**: Cotización delegada para un solo bote y retorno de aviso legal.
   - **Dado** que el Módulo de Reservas necesita la cotización exacta para las fechas de un viaje.
   - **Cuando** envía las fechas de inicio y fin junto con el ID del bote al módulo de Finanzas.
   - **Entonces** el módulo de Finanzas calcula el valor delegadamente a partir de las fechas enviadas y devuelve el precio junto con una advertencia visible obligatoria que indica: "Valor estimado. No incluye cargos adicionales ni depósito de seguridad".

---

### Historia de Usuario 3 - Provisión de tarifa base (Módulo de Gestión de Flota) (Prioridad: P2)

Como Módulo de Gestión de Flota (Inventario y Tarifas), quiero proporcionar la "tarifa base" por bote, para que el módulo de Finanzas pueda consumirla en lote sin crear cuellos de botella ni degradar mi rendimiento.

**Por qué esta prioridad**: El Módulo de Gestión de Flota es la fuente de la verdad para los precios. Si su respuesta es lenta, todo el flujo de cotizaciones del módulo de Finanzas y la visualización en el Módulo de Reservas se verán gravemente afectados.

**Prueba Independiente**: Realizar pruebas de carga solicitando tarifas para 50 botes concurrentes desde el módulo de Finanzas al Módulo de Gestión de Flota, esperando tiempos de respuesta menores a 500ms.

**Escenarios de Aceptación**:
1. **Escenario**: El Módulo de Gestión de Flota devuelve la tarifa base en un tiempo óptimo
   - **Dado** una solicitud del módulo de Finanzas para consultar la tarifa base de múltiples botes
   - **Cuando** el Módulo de Gestión de Flota procesa la solicitud
   - **Entonces** devuelve correctamente la tarifa base para los botes solicitados en menos de 500ms.

### Casos Extremos (Edge Cases)

- ¿Qué sucede cuando el Módulo de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable cuando el módulo de Finanzas solicita las tarifas base?
- ¿Qué sucede cuando las fechas solicitadas por el Módulo de Reservas incluyen formatos inválidos, fechas pasadas o fechas de fin que ocurren antes de las fechas de inicio?
- ¿Cómo maneja el sistema las cotizaciones para un bote que actualmente no tiene una tarifa base configurada (nula o faltante) en el Módulo de Gestión de Flota?
- ¿Qué sucede si la lista `boat_ids` enviada por el Módulo de Reservas está vacía o contiene IDs inexistentes?
- ¿Cómo se espera que el sistema maneje payloads inusualmente grandes (ej. solicitar cotizaciones para 1,000 botes a la vez)?
- ¿Cómo se comporta el cálculo si la duración de reserva solicitada es de 0 días?


## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El módulo de Finanzas DEBE devolver cotizaciones precisas al Módulo de Reservas basándose en la lista solicitada de identificadores de botes.
- **RF-002**: El sistema DEBE calcular cotizaciones en lote utilizando la fórmula de precios establecida: `(tarifa base del bote * duración en días) + (tarifa de seguro * número de pasajeros)`. *(Nota: La duración por defecto es de 1 día; la cantidad de pasajeros por defecto es 1).*
- **RF-003**: El sistema DEBE calcular cotizaciones individuales utilizando la misma fórmula de precios que las cotizaciones en lote, aplicada a un solo bote.
- **RF-004**: El módulo de Finanzas DEBE exponer un *endpoint* para recibir solicitudes de cotización del Módulo de Reservas y procesarlas exitosamente.
- **RF-005**: El módulo de Finanzas DEBE consultar al Módulo de Gestión de Flota enviando una lista de IDs de botes para recuperar sus respectivas tarifas base.
- **RF-006**: El módulo de Finanzas DEBE establecer un límite máximo estricto de identificadores por cada solicitud de cotización en lote (por ejemplo, máximo 50 o 100 botes por *payload*), rechazando con un error adecuado aquellas peticiones que superen este umbral para proteger la memoria y evitar sobrecargas en el sistema.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs (Data Transfer Objects) para la comunicación entre módulos, mapeando únicamente los atributos de datos esenciales de los *payloads* del Módulo de Gestión de Flota y el Módulo de Reservas.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para todos los cálculos monetarios para garantizar la precisión y evitar errores de redondeo.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (ej. *timeouts*, *fallbacks*) para gestionar de manera elegante las fallas de comunicación de las APIs entre los Módulos 1, 2 y 3.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Boat / Bote]**: Representa una embarcación física disponible para reserva. Para este caso de uso específico, sus atributos clave son un identificador único (`id` de tipo Long) y una tarifa de precio (`baseRate` de tipo BigDecimal).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de precisión en el cálculo con cero (0) errores de centavos/redondeo detectados en 100 pruebas de transacciones automatizadas, validando la correcta implementación de BigDecimal y la fórmula de precios".
- **CE-002**: Cumplimiento Arquitectónico, "Existen 0 operaciones matemáticas o de cálculo relacionadas con los precios en el código fuente del Módulo de Reservas, asegurando que el 100% de la renderización de precios es un mapeo directo de las respuestas de la API del módulo de Finanzas".
- **CE-003**: Negocio / Transparencia, "Los tickets de soporte al cliente y las disputas relacionadas con 'tarifas inesperadas' o 'depósitos de seguridad' representan menos del 2% del total de reservas dentro de los primeros 60 días del lanzamiento".
- **CE-004**: Resiliencia del Sistema, "El 100% de las fallas de red simuladas (ej. timeouts del Módulo de Gestión de Flota o tarifas no disponibles) desencadenan fallbacks elegantes en el Módulo de Reservas sin causar caídas de la aplicación, estados de carga infinitos o pantallas en blanco para el usuario".