# Especificación de Funcionalidad: UC01 - Solicitar Estimación para Reserva

**Creado**: 2026-08-27 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Calcular estimaciones en lote delegando cálculos al sistema (Prioridad: P1)

Como Sistema de Reservas y Operaciones, al necesitar listar opciones de reserva, quiero enviar la lista de identificadores (`boat_ids`), sin fechas ni número de pasajeros, de manera que el sistema calcule siempre cada estimación con 1 día de duración, 1 pasajero y la fecha actual como fecha de evaluación de la tarifa, delegándole completamente la responsabilidad matemática.

**Por qué esta prioridad**: Mantiene la arquitectura limpia y la separación de responsabilidades, al mismo tiempo que es fundamental mostrar precios estimados desde la búsqueda inicial para la conversión.

**Prueba Independiente**: Enviar un *payload* simulado desde el Sistema de Reservas y Operaciones con únicamente la lista de identificadores (`boat_ids`), sin fechas ni número de pasajeros, validando que el sistema calcule con 1 día de duración, 1 pasajero y la fecha actual en la fórmula matemática, consulte al Sistema de Gestión de Flota y devuelva el arreglo de estimaciones. Validar además que un *payload* con fechas o número de pasajeros es rechazado con un error controlado.

**Escenarios de Aceptación**:
1. **Escenario**: Cálculo delegado exitoso de múltiples estimaciones en lote.
   - **Dado** que el Sistema de Reservas y Operaciones necesita mostrar precios para 10 embarcaciones en pantalla.
   - **Cuando** envía únicamente la lista de identificadores (`boat_ids`) al sistema, sin fechas ni número de pasajeros.
   - **Entonces** el sistema calcula cada estimación con 1 día de duración, 1 pasajero y la fecha actual como fecha de evaluación de la tarifa, y devuelve con éxito las estimaciones para las 10 embarcaciones.

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

- **¿Qué sucede si el Sistema de Reservas y Operaciones envía fechas, número de pasajeros o cualquier otro atributo distinto de `boat_ids` en la solicitud en lote?**
  La solicitud en lote admite únicamente la lista de identificadores. Cualquier otro campo (fechas, pasajeros u otros) la convierte en una solicitud inválida: el sistema responde con un error controlado y no ejecuta ningún cálculo. La duración (1 día), los pasajeros (1) y la fecha de evaluación (fecha actual) los fija el sistema.

- **¿Qué sucede cuando el Sistema de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable cuando el sistema solicita las tarifas base?**
  Conforme a RNF-003 y CE-004, sin las tarifas base provistas por el Sistema de Gestión de Flota el sistema no puede calcular ninguna estimación. No asume tarifas ni devuelve estimaciones parciales o inventadas: aplica un manejo de errores controlado (reintentos/*fallbacks*) y responde al Sistema de Reservas y Operaciones con un error controlado que indica que la estimación no pudo completarse en ese momento, evitando estados de carga infinitos o pantallas en blanco.

- **¿Qué sucede cuando las fechas solicitadas por el Sistema de Reservas y Operaciones incluyen formatos inválidos, fechas pasadas o fechas de fin que ocurren antes de las fechas de inicio?**
  El sistema valida el rango de fechas antes de calcular en la modalidad individual: un formato de fecha inválido, una fecha de inicio en el pasado o una fecha de fin anterior a la de inicio se tratan como solicitud inválida y el sistema responde con un error controlado, sin ejecutar ningún cálculo. Esta validación de rango aplica exclusivamente a la modalidad individual: la modalidad en lote no recibe fechas, y cualquier fecha o número de pasajeros recibido en el lote se rechaza con un error controlado.

- **¿Cómo maneja el sistema las estimaciones para una embarcación que actualmente no tiene una tarifa base configurada (nula o faltante) en el Sistema de Gestión de Flota?**
  Dado que el Sistema de Gestión de Flota es la fuente autoritativa de la tarifa, el sistema no puede estimar esa embarcación sin su valor. La excluye del resultado de la estimación en lote (o la marca como "sin estimación disponible"), de modo que el resto del lote se devuelve correctamente y el usuario nunca visualiza un precio asumido. Si la solicitud es individual para una embarcación sin tarifa, el sistema responde con un error controlado.

- **¿Qué sucede si la lista `boat_ids` enviada por el Sistema de Reservas y Operaciones está vacía o contiene IDs inexistentes?**
  Si la lista está vacía, el sistema devuelve un arreglo vacío de estimaciones, sin error (no existen elementos que estimar). Si contiene identificadores inexistentes, el sistema omite aquellos que el Sistema de Gestión de Flota no confirma como existentes y devuelve estimaciones únicamente para las embarcaciones reconocidas con tarifa base disponible.

- **¿Cómo se espera que el sistema maneje payloads inusualmente grandes (ej. solicitar estimaciones para 1,000 embarcaciones a la vez)?**
  Conforme a RF-006, el sistema aplica un límite máximo estricto de identificadores por solicitud (por ejemplo, máximo 50 o 100 embarcaciones). Una solicitud de 1,000 embarcaciones supera el umbral y es rechazada con un error controlado sin procesar el lote; el Sistema de Reservas y Operaciones debe partir la solicitud en lotes que respeten el límite.

- **¿Cómo se comporta el cálculo si la fecha de inicio y la fecha de fin coinciden?**
  La modalidad en lote no recibe fechas: calcula siempre con 1 día de duración, 1 pasajero y la fecha actual (RF-002). En la solicitud individual, una fecha de inicio igual a la fecha de fin representa una reserva de 1 día; solo se considera inválida una fecha de fin anterior a la fecha de inicio.

- **¿Qué sucede si la tarifa del seguro náutico por pasajero, o el porcentaje de incremento que requiere la fecha evaluada (fin de semana o temporada alta), aún no ha sido configurado mediante "Configurar parámetros financieros globales"?**
  Sin esos valores el sistema no puede presentar al arrendatario cuánto pagaría. Considera la información incompleta, no calcula ninguna estimación parcial ni asumida y responde al Sistema de Reservas y Operaciones con un error controlado que indica que la estimación no pudo completarse.


## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE devolver estimaciones precisas al Sistema de Reservas y Operaciones basándose en la lista solicitada de identificadores de embarcaciones.
- **RF-002**: El sistema DEBE calcular las estimaciones en lote utilizando la fórmula de precios establecida: `(tarifa base de la embarcación * duración en días) + (tarifa de seguro * número de pasajeros)`. *(Nota: en el lote la duración es siempre de 1 día, la cantidad de pasajeros es siempre 1 y la tarifa base corresponde siempre a la fecha actual; el lote solo admite la lista de `boat_ids`. La estimación no incluye el depósito de garantía ni penalidades o ajustes posteriores de la reserva.)*
- **RF-003**: El sistema DEBE calcular estimaciones individuales utilizando la misma fórmula de precios que las estimaciones en lote, aplicada a una sola embarcación, con el número de pasajeros recibido, la tarifa base correspondiente a la fecha de inicio recibida y la cantidad de días inclusiva entre la fecha de inicio y la fecha de fin.
- **RF-004**: El sistema DEBE exponer un *endpoint* para recibir solicitudes de estimación del Sistema de Reservas y Operaciones y procesarlas exitosamente.
- **RF-005**: El sistema DEBE consultar al Sistema de Gestión de Flota enviando una lista de IDs de embarcaciones para recuperar sus respectivas tarifas base.
- **RF-006**: El sistema DEBE establecer un límite máximo estricto de identificadores por cada solicitud de estimación en lote (por ejemplo, máximo 50 o 100 embarcaciones por *payload*), rechazando con un error adecuado aquellas peticiones que superen este umbral para proteger la memoria y evitar sobrecargas en el sistema.
- **RF-007**: El sistema DEBE rechazar con un error controlado toda solicitud de estimación en lote que incluya cualquier atributo distinto de la lista de identificadores (`boat_ids`) —como fechas de inicio o fin, número de pasajeros u otros—, sin ejecutar cálculo alguno, dado que la duración, los pasajeros y la fecha de evaluación del lote los fija el sistema.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs (Data Transfer Objects) para la comunicación con otros sistemas, mapeando únicamente los atributos de datos esenciales de los *payloads* del Sistema de Gestión de Flota y el Sistema de Reservas y Operaciones.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para todos los cálculos monetarios para garantizar la precisión y evitar errores de redondeo.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (ej. *timeouts*, *fallbacks*) para gestionar de manera elegante las fallas de comunicación de las APIs entre el sistema, el Sistema de Reservas y Operaciones y el Sistema de Gestión de Flota.

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **EmbarcaciónInfo (DTO)**: Información recibida desde el Sistema de Gestión de Flota, necesaria únicamente para el cálculo de este caso de uso. Contiene el identificador de la embarcación y su tarifa base. No se persiste dentro del sistema; se utiliza solo como insumo de la operación.
- **SolicitudEstimacionLote (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para la estimación en lote. Contiene únicamente la lista de identificadores de embarcaciones (`boat_ids`); no admite fechas ni número de pasajeros. No se persiste dentro del sistema; se utiliza solo como insumo de la operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de precisión en el cálculo con cero (0) errores de centavos/redondeo detectados en 100 pruebas de transacciones automatizadas, validando la correcta implementación de BigDecimal y la fórmula de precios".
- **CE-002**: Cumplimiento Arquitectónico, "Existen 0 operaciones matemáticas o de cálculo relacionadas con los precios en el código fuente del Sistema de Reservas y Operaciones, asegurando que el 100% de la renderización de precios es un mapeo directo de las respuestas de la API del sistema".
- **CE-003**: Negocio / Transparencia, "Los tickets de soporte al cliente y las disputas relacionadas con 'tarifas inesperadas' o 'depósitos de seguridad' representan menos del 2% del total de reservas dentro de los primeros 60 días del lanzamiento".
- **CE-004**: Resiliencia del Sistema, "El 100% de las fallas de red simuladas (ej. timeouts del Sistema de Gestión de Flota o tarifas no disponibles) desencadenan fallbacks elegantes en el Sistema de Reservas y Operaciones sin causar caídas de la aplicación, estados de carga infinitos o pantallas en blanco para el usuario".
- **CE-005**: Validación de Contrato, "el 100% de las solicitudes en lote que incluyan atributos distintos de `boat_ids` (fechas, número de pasajeros u otros) son rechazadas con un error controlado sin ejecutar ningún cálculo, y 0 estimaciones en lote se calculan con valores de fecha o pasajeros provistos en la solicitud".