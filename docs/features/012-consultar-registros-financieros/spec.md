# Especificación de Funcionalidad: UC12 - Consultar Registros Financieros

**Creado**: 2026-09-26

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar los registros financieros propios de forma paginada (Prioridad: P1)

Como Propietario, quiero consultar de forma paginada los registros de cobro, reembolso y dispersión relacionados con mis reservas, de manera que pueda revisar el detalle de mis operaciones sin acceder a información de otros propietarios.

**Por qué esta prioridad**: El Propietario necesita consultar cada operación financiera de sus reservas para verificar cobros, reembolsos y dispersiones. La paginación evita respuestas de tamaño no controlado y el alcance por propietario evita la exposición de información de terceros.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión asociados a reservas de varios propietarios, enviar al sistema una solicitud paginada desde un Propietario y validar que cada página contiene únicamente registros relacionados con reservas de dicho Propietario y los metadatos de paginación correspondientes.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta propia con paginación por defecto.
   - **Dado** que existen más de diez registros financieros relacionados con reservas del Propietario.
   - **Cuando** el Propietario solicita la primera página sin indicar un tamaño de página.
   - **Entonces** el sistema devuelve como máximo diez registros de su alcance, junto con el número de página, el tamaño utilizado, el total de registros y el total de páginas.

2. **Escenario**: Consulta propia con filtros.
   - **Dado** que existen registros de varios tipos, estados, propietarios y embarcaciones.
   - **Cuando** el Propietario solicita una página indicando filtros válidos.
   - **Entonces** el sistema devuelve únicamente los registros de sus reservas que coinciden con los filtros indicados.

### Historia de Usuario 2 - Consultar los registros financieros de la plataforma de forma paginada (Prioridad: P1)

Como Administrador Financiero, quiero consultar de forma paginada los registros de cobro, reembolso y dispersión de toda la plataforma, aplicando los filtros solicitados, de manera que pueda supervisar cada operación financiera individual.

**Por qué esta prioridad**: El Administrador Financiero necesita revisar el detalle global de las operaciones financieras y aislar registros mediante filtros. La paginación mantiene controlado el tamaño de las respuestas aunque el histórico de la plataforma crezca.

**Prueba Independiente**: Con registros de varias reservas, propietarios y embarcaciones, enviar al sistema una solicitud paginada desde un Administrador Financiero y validar que devuelve el alcance global, aplica los filtros indicados y entrega los metadatos de paginación correctos.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta global con paginación por defecto.
   - **Dado** que existen más de diez registros financieros en la plataforma.
   - **Cuando** el Administrador Financiero solicita una página sin indicar un tamaño de página.
   - **Entonces** el sistema devuelve como máximo diez registros globales y los metadatos de paginación correspondientes.

2. **Escenario**: Consulta global filtrada.
   - **Dado** que existen registros que no coinciden con los filtros solicitados.
   - **Cuando** el Administrador Financiero consulta por tipo de transacción, estado, propietario asociado o embarcación asociada.
   - **Entonces** el sistema devuelve únicamente los registros que coinciden con todos los filtros indicados.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si no existen registros dentro del alcance y filtros solicitados?**
  El sistema devuelve una página vacía junto con los metadatos de paginación, sin considerar la ausencia de registros como un error.

- **¿Qué sucede si el número de página solicitado excede el total de páginas disponibles?**
  El sistema responde con un error controlado indicando que la página solicitada no existe.

- **¿Qué sucede si el número de página o el tamaño de página son inválidos?**
  El sistema responde con un error controlado y no ejecuta la consulta.

- **¿Puede un Propietario consultar registros de otro propietario usando un filtro de propietario o de embarcación?**
  No. El sistema aplica primero el alcance del Propietario sobre sus reservas y después los filtros solicitados; ningún filtro puede ampliar dicho alcance.

- **¿Se incluyen registros cuyo estado aún no es definitivo?**
  Sí. El sistema devuelve el estado vigente de cada registro, incluidos los estados en curso, sin omitir operaciones pendientes.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Propietario o del Administrador Financiero una solicitud de consulta que incluya el número de página y, opcionalmente, el tamaño de página y los filtros.
- **RF-002**: El sistema DEBE aceptar filtros por tipo de transacción (cobro, reembolso o dispersión), estado, propietario asociado y embarcación asociada.
- **RF-003**: El sistema DEBE limitar las consultas del Propietario a los registros asociados a sus reservas y permitir al Administrador Financiero consultar los registros de toda la plataforma.
- **RF-004**: El sistema DEBE consultar los registros de cobro definidos en "Procesar cobro", los registros de reembolso definidos en "Reembolsar dinero a arrendatario" y los registros de dispersión definidos en "Liquidar fondos de alquiler".
- **RF-005**: El sistema DEBE incluir en cada registro devuelto el tipo de transacción, la reserva asociada, el propietario y la embarcación asociados, el monto y el estado vigente.
- **RF-006**: El sistema DEBE incluir la referencia externa de la Pasarela de Pago cuando se encuentre disponible.
- **RF-007**: El sistema DEBE devolver los resultados exclusivamente de forma paginada, incluyendo el número de página actual, el tamaño utilizado, el total de registros y el total de páginas.
- **RF-008**: El sistema DEBE utilizar diez registros como tamaño de página por defecto cuando el solicitante no lo indique.
- **RF-009**: El sistema DEBE responder con un error controlado cuando la página exceda el total disponible o cuando los parámetros de paginación sean inválidos.
- **RF-010**: El sistema NO DEBE crear, modificar ni eliminar registros financieros como parte de esta consulta.
- **RF-011**: El sistema DEBE exponer este caso de uso exclusivamente al Propietario y al Administrador Financiero.
- **RF-012**: El sistema NO DEBE consultar al Sistema de Gestión de Flota para resolver el alcance ni los filtros.
- **RF-013**: El sistema NO DEBE exportar archivos desde este caso de uso; la exportación corresponde a "Consultar informe financiero" (SPEC 13).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para recibir la solicitud de consulta y devolver cada página con sus metadatos.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar los montos de los registros devueltos.
- **RNF-003**: El sistema DEBE garantizar que la paginación de un mismo conjunto de datos no omita ni duplique registros entre páginas.

### Entidades Clave

- **RegistroDeCobro (Entidad, definida en SPEC 5)**: En este caso de uso es únicamente consultada y representa una operación de cobro de una reserva.
- **RegistroDeReembolso (Entidad, definida en SPEC 9)**: En este caso de uso es únicamente consultada y representa una operación de liberación o reembolso de una reserva.
- **RegistroDeDispersión (Entidad, definida en SPEC 10)**: En este caso de uso es únicamente consultada y representa una operación de captura o liquidación de fondos.
- **SolicitudConsultaRegistrosFinancieros (DTO)**: Información recibida desde el Propietario o el Administrador Financiero. Contiene el número de página, el tamaño opcional y los filtros por tipo, estado, propietario y embarcación.
- **RegistroFinancieroResultado (DTO)**: Representa el detalle de una operación individual. Contiene el tipo de transacción, la reserva, el propietario y la embarcación asociados, el monto, el estado y la referencia externa cuando exista.
- **PáginaDeRegistrosFinancierosResultado (DTO)**: Resultado que el sistema devuelve para cada solicitud. Contiene la lista de registros de la página y sus metadatos de paginación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance del Propietario, "100% de las páginas consultadas por un Propietario contienen únicamente registros relacionados con sus reservas, con cero (0) registros de otros propietarios expuestos en pruebas automatizadas".
- **CE-002**: Cobertura Global, "100% de las páginas consultadas por el Administrador Financiero corresponden al conjunto global de registros que coincide con los filtros indicados, sin omisiones ni duplicados detectados en pruebas automatizadas".
- **CE-003**: Consistencia de Paginación, "100% de las respuestas se entregan paginadas y el tamaño utilizado por defecto es de diez registros, con cero (0) respuestas que entreguen el conjunto completo en una sola página".
- **CE-004**: Integridad del Detalle, "100% de los registros devueltos incluyen el tipo de transacción, la reserva, el propietario, la embarcación, el monto y el estado; la referencia externa se incluye cuando está disponible".
- **CE-005**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones de registros financieros como resultado de este caso de uso y 0 archivos generados por la consulta".
