# Especificación de Funcionalidad: UC02 - Brindar Tarifa Base

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es invocado exclusivamente de forma interna, mediante relaciones `<<include>>`, por "Solicitar cotización para reserva" (SPEC 1) y por "Brindar información de reserva" (SPEC 3, pendiente). No es expuesto directamente a ningún actor externo del sistema de Reservas ni a los usuarios finales (Arrendatario, Propietario, Administrador Financiero).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Obtener la tarifa base vigente aplicando la tarifa dinámica (Prioridad: P1)

Como el sistema, al ser invocado internamente (`<<include>>`) por "Solicitar cotización para reserva" o "Brindar información de reserva", quiero consultar al Sistema de Gestión de Flota la tarifa base específica de la embarcación solicitada (el precio fijado por su propietario y almacenado en dicho módulo), y aplicar sobre esa tarifa la regla de tarifa dinámica vigente (temporada alta, fin de semana, u otra condición configurada), de manera que el caso de uso que me invocó reciba la tarifa base por unidad de tiempo lista para ser utilizada en sus propios cálculos.

**Por qué esta prioridad**: Esta es la única función del sistema donde reside la lógica de tarifas dinámicas; tanto la cotización preliminar como el desglose final de precio dependen de que este valor sea correcto, ya que ningún otro caso de uso debe duplicar este cálculo.

**Prueba Independiente**: Invocar "Brindar tarifa base" con el identificador de una embarcación para distintas fechas (día regular, fin de semana, temporada alta) y validar que el sistema consulta al Sistema de Gestión de Flota la tarifa base registrada para esa embarcación específica y devuelve la tarifa ajustada según la condición vigente en cada caso.

**Escenarios de Aceptación**:

1. **Escenario**: Tarifa regular (sin condición dinámica vigente).
   - **Dado** que la fecha evaluada no corresponde a fin de semana ni a temporada alta.
   - **Cuando** el sistema consulta al Sistema de Gestión de Flota la tarifa base registrada para esa embarcación específica.
   - **Entonces** el sistema devuelve la tarifa base sin ningún ajuste dinámico aplicado.

2. **Escenario**: Tarifa ajustada por fin de semana.
   - **Dado** que la fecha evaluada corresponde a un fin de semana.
   - **Cuando** el sistema aplica la tarifa dinámica vigente.
   - **Entonces** el sistema devuelve la tarifa base con el ajuste correspondiente a fin de semana.

3. **Escenario**: Tarifa ajustada por temporada alta.
   - **Dado** que la fecha evaluada corresponde a temporada alta.
   - **Cuando** el sistema aplica la tarifa dinámica vigente.
   - **Entonces** el sistema devuelve la tarifa base con el ajuste correspondiente a temporada alta.

### Casos Extremos (Edge Cases)

- ¿Qué sucede cuando el Sistema de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable al momento en que el sistema solicita la tarifa base registrada para la embarcación?
- ¿Cómo debe comportarse el sistema cuando la embarcación consultada no tiene una tarifa base configurada (nula o faltante) en el Sistema de Gestión de Flota?
- ¿Qué tarifa dinámica debe prevalecer cuando una misma fecha coincide simultáneamente con más de una condición vigente (por ejemplo, fin de semana y temporada alta al mismo tiempo)?
- ¿Sobre qué fecha debe evaluarse la tarifa dinámica cuando "Solicitar cotización para reserva" invoca este caso de uso en modo lote, sin fechas específicas (cotización general para la pantalla principal)?
- ¿Cómo se determina y actualiza la vigencia exacta (fechas de inicio y fin) de la temporada alta dentro del sistema?

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE, al ser invocado mediante `<<include>>`, consultar al Sistema de Gestión de Flota la tarifa base específica (el precio fijado por el propietario) de la embarcación solicitada, identificándola por su identificador único.
- **RF-002**: El sistema DEBE aplicar la tarifa dinámica vigente (temporada alta, fin de semana u otra condición vigente) sobre la tarifa provista por el Sistema de Gestión de Flota para obtener la tarifa base final por unidad de tiempo.
- **RF-003**: El sistema DEBE devolver la tarifa base final exclusivamente a la operación interna que lo invocó ("Solicitar cotización para reserva" o "Brindar información de reserva"), sin exponer un *endpoint* directo para actores externos.
- **RF-004**: El sistema DEBE utilizar al Sistema de Gestión de Flota como única fuente autoritativa de la tarifa base de cada embarcación, dado que dicho valor es definido por el propietario y almacenado exclusivamente en ese módulo.
- **RF-005**: El sistema NO DEBE delegar ni duplicar en el Sistema de Gestión de Flota, ni en el Módulo de Reservas y Operaciones, el cálculo de la tarifa dinámica; esta lógica reside exclusivamente en este caso de uso.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Sistema de Gestión de Flota, mapeando únicamente los atributos esenciales (identificador de la embarcación y tarifa base) necesarios para el cálculo.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar la tarifa provista por el Sistema de Gestión de Flota, cualquier ajuste dinámico aplicado y la tarifa base final resultante.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) ante fallas de comunicación con el Sistema de Gestión de Flota, dado que tanto "Solicitar cotización para reserva" como "Brindar información de reserva" dependen de este caso de uso para completar su propio flujo.

### Entidades Clave

- **EmbarcaciónInfo (DTO)**: Información recibida desde el Sistema de Gestión de Flota, necesaria únicamente para el cálculo de este caso de uso. Contiene el identificador de la embarcación y su tarifa base (el precio fijado por el propietario para esa embarcación específica). No se persiste dentro del sistema; se utiliza solo como insumo de la operación.
- **TarifaBaseResultado (DTO)**: Resultado que el sistema devuelve a la operación invocante. Contiene el identificador de la embarcación y la tarifa base final por unidad de tiempo, ya con la tarifa dinámica aplicada. No representa una entidad persistida, sino el valor de retorno de esta función interna.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de las tarifas base devueltas reflejan correctamente la condición dinámica vigente (regular, fin de semana o temporada alta) para la fecha evaluada, con cero (0) errores de cálculo detectados en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "0 casos de uso distintos a 'Brindar tarifa base' contienen lógica de cálculo de tarifa dinámica en su código fuente, confirmando que esta responsabilidad reside exclusivamente en este caso de uso".
- **CE-003**: Resiliencia del Sistema, "100% de las fallas simuladas de comunicación con el Sistema de Gestión de Flota (timeouts, datos inalcanzables) son manejadas mediante fallbacks controlados, sin provocar fallos en 'Solicitar cotización para reserva' ni en 'Brindar información de reserva'".
