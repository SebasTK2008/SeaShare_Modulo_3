# Especificación de Funcionalidad: UC08 - Proveer Tarifa Base

**Creado**: 2026-09-04

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consulta de tarifa base para cotizaciones masivas (Prioridad: P1)

Como Módulo de Finanzas (Módulo 3), necesito solicitar la tarifa base de múltiples embarcaciones al Módulo de Gestión de Flota (Módulo 1), para poder calcular y devolver las cotizaciones estimadas o exactas al Módulo de Reservas de forma eficiente.

**Por qué esta prioridad**: El cálculo del Valor Alquiler Bruto comienza con la tarifa base de la embarcación. Sin este dato del Módulo 1, el Módulo 3 no puede aplicar la fórmula de cobro ni las tarifas dinámicas.

**Prueba Independiente**: Enviar una lista de 50 IDs de embarcaciones desde el Módulo 3 al Módulo 1. Validar que la respuesta devuelva un listado de IDs junto con el valor monetario de su tarifa base actual en menos de 500ms.

**Escenarios de Aceptación**:

1. **Escenario**: Solicitud exitosa de tarifas base en lote.
   - **Dado** que el Módulo de Finanzas necesita calcular una cotización masiva para la pantalla de búsqueda.
   - **Cuando** envía un arreglo de `boat_ids` al Módulo de Gestión de Flota.
   - **Entonces** recibe como respuesta el ID del bote y su tarifa base, permitiéndole continuar con los cálculos financieros.

---

### Historia de Usuario 2 - Manejo de embarcaciones sin tarifa configurada (Prioridad: P2)

Como Módulo de Finanzas (Módulo 3), necesito manejar correctamente los casos en los que el Módulo de Gestión de Flota reporte que una embarcación no tiene tarifa configurada o no está disponible, para omitirla de la cotización final sin que el proceso general falle.

**Por qué esta prioridad**: Evita errores críticos en cascada y garantiza que no se muestren botes con precios en $0 o nulos en la interfaz de usuario, protegiendo los ingresos.

**Prueba Independiente**: Consultar la tarifa de un ID inexistente o de una embarcación recién creada sin precio asignado. Validar que el Módulo de Finanzas maneje el valor nulo o el error, excluyendo ese ID del resultado de la cotización final.

**Escenarios de Aceptación**:

1. **Escenario**: Respuesta con tarifa no disponible.
   - **Dado** una solicitud de tarifa para un bote en mantenimiento o sin precio asignado.
   - **Cuando** el Módulo 1 responde que no hay tarifa base disponible.
   - **Entonces** el Módulo 3 ignora ese bote y no lo incluye en el arreglo de cotizaciones final devuelto al Módulo de Reservas.

### Casos Extremos (Edge Cases)

- ¿Qué sucede si el Módulo de Gestión de Flota experimenta una caída y no responde a la solicitud de tarifas?
- ¿Cómo se manejan las diferencias de moneda si el Módulo 1 envía la tarifa en otra divisa (ej. EUR) pero la plataforma opera en USD?
- ¿Qué pasa si la lista de IDs enviada está vacía?

## Requisitos

### Requisitos Funcionales

- **RF-001**: El Módulo 1 DEBE exponer un *endpoint* de solo lectura optimizado para devolver tarifas base en lote basándose en un arreglo de IDs.
- **RF-002**: El Módulo 3 DEBE implementar un mecanismo de resiliencia (ej. *Circuit Breaker* o *Timeout*) para evitar bloqueos si el Módulo 1 se demora en responder.
- **RF-003**: El Módulo 1 DEBE enviar siempre valores numéricos positivos para la tarifa base; en caso de no existir, debe omitir el registro o indicar error.

### Requisitos No Funcionales

- **[Tarifa Base]**: DTO de respuesta que mapea `boatId` y `baseRate` (BigDecimal).

### Entidades Clave *(incluir si la funcionalidad involucra datos)*

- **[Boat / Bote]**: Representa una embarcación física disponible para reserva. Para este caso de uso específico, sus atributos clave son un identificador único (`id` de tipo Long) y una tarifa de precio (`baseRate` de tipo BigDecimal).

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: 99.9% de las consultas de tarifa base en lote se resuelven en menos de 500ms.
- **CE-002**: 0% de fallas en el Módulo de Reservas por culpa de tarifas nulas, gracias al manejo adecuado de los errores en el Módulo de Finanzas.