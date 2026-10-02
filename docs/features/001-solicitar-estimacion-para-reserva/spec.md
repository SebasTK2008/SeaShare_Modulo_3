# Especificación de Funcionalidad: UC01 - Solicitar Estimación para Reserva

**Creado**: 2026-08-27 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Calcular estimaciones en lote delegando cálculos al sistema (Prioridad: P1)

Como Sistema de Reservas y Operaciones, al necesitar listar opciones de reserva, quiero enviar solicitudes de estimación en lote al sistema delegándole completamente la responsabilidad matemática, de manera que, si no se especifican fechas exactas, se asuma una duración por defecto de 1 día y la fecha actual como fecha de evaluación de la tarifa para calcular el costo estimado.

**Contexto del Sistema (Flujo)**: 
1. El Sistema de Reservas y Operaciones carga las opciones de reserva.
2. El Sistema de Reservas y Operaciones activa este caso de uso enviando una lista de identificadores (`boat_ids`) al sistema. Si aún no hay fechas seleccionadas, envía esta información vacía.
3. El sistema invoca el sub-caso "Brindar tarifa base" consultando la tarifa de cada embarcación al Sistema de Gestión de Flota.
4. El sistema realiza los cálculos asumiendo 1 día de duración, 1 pasajero y la fecha actual por defecto, y devuelve las estimaciones, permitiendo que el Sistema de Reservas y Operaciones actúe únicamente como consumidor de la API sin hacer operaciones locales.

**Por qué esta prioridad**: Mantiene la arquitectura limpia y la separación de responsabilidades, al mismo tiempo que es fundamental mostrar precios estimados desde la búsqueda inicial para la conversión.

**Prueba Independiente**: Enviar un *payload* simulado desde el Sistema de Reservas y Operaciones con una lista de IDs y sin fechas al sistema, validando que asuma el valor por defecto (1 día) y la fecha actual en la fórmula matemática, consulte al Sistema de Gestión de Flota y devuelva el arreglo de estimaciones.

**Escenarios de Aceptación**:
1. **Escenario**: Cálculo delegado exitoso de múltiples estimaciones con fecha por defecto.
   - **Dado** que el Sistema de Reservas y Operaciones necesita mostrar precios para 10 embarcaciones en pantalla.
   - **Cuando** envía la lista al sistema sin un rango de fechas definido.
   - **Entonces** el sistema asume 1 día de duración, 1 pasajero y la fecha actual por defecto, calcula con éxito las estimaciones para las 10 embarcaciones y las devuelve.

---

### Historia de Usuario 2 - Estimación individual delegada y advertencia de estimación (Prioridad: P1)

Como Sistema de Reservas y Operaciones, al solicitar los detalles de una embarcación específica, quiero delegar el cálculo exacto enviando el ID, el rango de fechas y el número de pasajeros al sistema, y que este me devuelva tanto el valor total como una bandera obligatoria de advertencia (*warning*) sobre el estimado, para yo simplemente renderizarla sin manejar lógica financiera ni de reglas de negocio.

**Por qué esta prioridad**: Mantiene el código del Sistema de Reservas y Operaciones libre de multiplicaciones de tarifas, y atiende un requisito crítico de negocio para evitar fricciones y quejas de los usuarios por posibles recargos.

**Prueba Independiente**: Verificar que al enviar fechas de inicio y fin y el número de pasajeros desde el Sistema de Reservas y Operaciones, el sistema devuelve la estimación exacta calculando los días de diferencia y utilizando la tarifa base correspondiente a la fecha de inicio, y adjunta un texto/flag de advertencia sobre la naturaleza del estimado.

**Escenarios de Aceptación**:
1. **Escenario**: Estimación delegada para una sola embarcación y retorno de aviso legal.
   - **Dado** que el Sistema de Reservas y Operaciones necesita la estimación exacta para las fechas de un viaje.
   - **Cuando** envía las fechas de inicio y fin, el número de pasajeros y el ID de la embarcación al sistema.
  - **Entonces** el sistema calcula el valor delegadamente a partir de las fechas enviadas, utilizando la tarifa base correspondiente a la fecha de inicio, e informa la cantidad de días inclusiva junto con una advertencia visible obligatoria que indica: "Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva".

---

### Historia de Usuario 3 - Provisión de tarifa base (Sistema de Gestión de Flota) (Prioridad: P2)

Como Sistema de Gestión de Flota (Inventario y Tarifas), quiero proporcionar la "tarifa base" por embarcación, para que el sistema pueda consumirla en lote sin crear cuellos de botella ni degradar mi rendimiento.

**Por qué esta prioridad**: El Sistema de Gestión de Flota es la fuente de la verdad para los precios. Si su respuesta es lenta, todo el flujo de estimaciones del sistema y la visualización en el Sistema de Reservas y Operaciones se verán gravemente afectados.

**Prueba Independiente**: Realizar pruebas de carga solicitando tarifas para 50 embarcaciones concurrentes desde el sistema al Sistema de Gestión de Flota, esperando tiempos de respuesta menores a 500ms.

**Escenarios de Aceptación**:
1. **Escenario**: El Sistema de Gestión de Flota devuelve la tarifa base en un tiempo óptimo
   - **Dado** una solicitud del sistema para consultar la tarifa base de múltiples embarcaciones
   - **Cuando** el Sistema de Gestión de Flota procesa la solicitud
   - **Entonces** devuelve correctamente la tarifa base para las embarcaciones solicitadas en menos de 500ms.

### Casos Extremos (Edge Cases)

- **¿Qué sucede cuando el Sistema de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable cuando el sistema solicita las tarifas base?**
  Conforme a RNF-003 y CE-004, sin las tarifas base provistas por el Sistema de Gestión de Flota el sistema no puede calcular ninguna estimación. No asume tarifas ni devuelve estimaciones parciales o inventadas: aplica un manejo de errores controlado (reintentos/*fallbacks*) y responde al Sistema de Reservas y Operaciones con un error controlado que indica que la estimación no pudo completarse en ese momento, evitando estados de carga infinitos o pantallas en blanco.

- **¿Qué sucede cuando las fechas solicitadas por el Sistema de Reservas y Operaciones incluyen formatos inválidos, fechas pasadas o fechas de fin que ocurren antes de las fechas de inicio?**
  El sistema valida el rango de fechas antes de calcular en la modalidad individual: un formato de fecha inválido, una fecha de inicio en el pasado o una fecha de fin anterior a la de inicio se tratan como solicitud inválida y el sistema responde con un error controlado, sin ejecutar ningún cálculo. En la modalidad en lote (sin fechas, pantalla principal) esta validación de rango no aplica, dado que se usan la duración por defecto (1 día) y la fecha actual como fecha de evaluación de la tarifa.

- **¿Cómo maneja el sistema las estimaciones para una embarcación que actualmente no tiene una tarifa base configurada (nula o faltante) en el Sistema de Gestión de Flota?**
  Dado que el Sistema de Gestión de Flota es la fuente autoritativa de la tarifa, el sistema no puede estimar esa embarcación sin su valor. La excluye del resultado de la estimación en lote (o la marca como "sin estimación disponible"), de modo que el resto del lote se devuelve correctamente y el usuario nunca visualiza un precio asumido. Si la solicitud es individual para una embarcación sin tarifa, el sistema responde con un error controlado.

- **¿Qué sucede si la lista `boat_ids` enviada por el Sistema de Reservas y Operaciones está vacía o contiene IDs inexistentes?**
  Si la lista está vacía, el sistema devuelve un arreglo vacío de estimaciones, sin error (no existen elementos que estimar). Si contiene identificadores inexistentes, el sistema omite aquellos que el Sistema de Gestión de Flota no confirma como existentes y devuelve estimaciones únicamente para las embarcaciones reconocidas con tarifa base disponible.

- **¿Cómo se espera que el sistema maneje payloads inusualmente grandes (ej. solicitar estimaciones para 1,000 embarcaciones a la vez)?**
  Conforme a RF-006, el sistema aplica un límite máximo estricto de identificadores por solicitud (por ejemplo, máximo 50 o 100 embarcaciones). Una solicitud de 1,000 embarcaciones supera el umbral y es rechazada con un error controlado sin procesar el lote; el Sistema de Reservas y Operaciones debe partir la solicitud en lotes que respeten el límite.

- **¿Cómo se comporta el cálculo si la fecha de inicio y la fecha de fin coinciden?**
  La modalidad en lote asume una duración por defecto de 1 día (RF-002) únicamente cuando el Sistema de Reservas y Operaciones no envía fechas. En la solicitud individual, una fecha de inicio igual a la fecha de fin representa una reserva de 1 día; solo se considera inválida una fecha de fin anterior a la fecha de inicio.

- **¿Qué sucede si la tarifa del seguro náutico por pasajero, o el porcentaje de incremento que requiere la fecha evaluada (fin de semana o temporada alta), aún no ha sido configurado mediante "Configurar parámetros financieros globales"?**
  Sin esos valores el sistema no puede presentar al arrendatario cuánto pagaría. Considera la información incompleta, no calcula ninguna estimación parcial ni asumida y responde al Sistema de Reservas y Operaciones con un error controlado que indica que la estimación no pudo completarse.


## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE devolver estimaciones precisas al Sistema de Reservas y Operaciones basándose en la lista solicitada de identificadores de embarcaciones.
- **RF-002**: El sistema DEBE calcular estimaciones en lote utilizando la fórmula de precios establecida: `(tarifa base de la embarcación * duración en días) + (tarifa de seguro * número de pasajeros)`. *(Nota: La duración por defecto es de 1 día; la cantidad de pasajeros por defecto es 1; y, al no especificarse fechas, la tarifa base corresponde a la fecha actual. La estimación no incluye el depósito de garantía ni penalidades o ajustes posteriores de la reserva.)*
- **RF-003**: El sistema DEBE calcular estimaciones individuales utilizando la misma fórmula de precios que las estimaciones en lote, aplicada a una sola embarcación, con el número de pasajeros recibido, la tarifa base correspondiente a la fecha de inicio recibida y la cantidad de días inclusiva entre la fecha de inicio y la fecha de fin.
- **RF-004**: El sistema DEBE exponer un *endpoint* para recibir solicitudes de estimación del Sistema de Reservas y Operaciones y procesarlas exitosamente.
- **RF-005**: El sistema DEBE consultar al Sistema de Gestión de Flota enviando una lista de IDs de embarcaciones para recuperar sus respectivas tarifas base.
- **RF-006**: El sistema DEBE establecer un límite máximo estricto de identificadores por cada solicitud de estimación en lote (por ejemplo, máximo 50 o 100 embarcaciones por *payload*), rechazando con un error adecuado aquellas peticiones que superen este umbral para proteger la memoria y evitar sobrecargas en el sistema.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs (Data Transfer Objects) para la comunicación con otros sistemas, mapeando únicamente los atributos de datos esenciales de los *payloads* del Sistema de Gestión de Flota y el Sistema de Reservas y Operaciones.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para todos los cálculos monetarios para garantizar la precisión y evitar errores de redondeo.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (ej. *timeouts*, *fallbacks*) para gestionar de manera elegante las fallas de comunicación de las APIs entre el sistema, el Sistema de Reservas y Operaciones y el Sistema de Gestión de Flota.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **EmbarcaciónInfo (DTO)**: Información recibida desde el Sistema de Gestión de Flota, necesaria únicamente para el cálculo de este caso de uso. Contiene el identificador de la embarcación y su tarifa base. No se persiste dentro del sistema; se utiliza solo como insumo de la operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de precisión en el cálculo con cero (0) errores de centavos/redondeo detectados en 100 pruebas de transacciones automatizadas, validando la correcta implementación de BigDecimal y la fórmula de precios".
- **CE-002**: Cumplimiento Arquitectónico, "Existen 0 operaciones matemáticas o de cálculo relacionadas con los precios en el código fuente del Sistema de Reservas y Operaciones, asegurando que el 100% de la renderización de precios es un mapeo directo de las respuestas de la API del sistema".
- **CE-003**: Negocio / Transparencia, "Los tickets de soporte al cliente y las disputas relacionadas con 'tarifas inesperadas' o 'depósitos de seguridad' representan menos del 2% del total de reservas dentro de los primeros 60 días del lanzamiento".
- **CE-004**: Resiliencia del Sistema, "El 100% de las fallas de red simuladas (ej. timeouts del Sistema de Gestión de Flota o tarifas no disponibles) desencadenan fallbacks elegantes en el Sistema de Reservas y Operaciones sin causar caídas de la aplicación, estados de carga infinitos o pantallas en blanco para el usuario".