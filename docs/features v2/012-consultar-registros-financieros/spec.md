# Especificación de Funcionalidad: UC12 - Consultar Registros Financieros

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es invocado directamente por el Propietario y por el Administrador Financiero para consultar, de solo lectura, el historial de transacciones (cobros, reembolsos y dispersiones) registrado por "Procesar cobro" (SPEC 5), "Reembolsar dinero a arrendatario" (SPEC 9) y "Dispersar fondos de alquiler" (SPEC 10). El alcance de la consulta difiere según el actor: el **Propietario** únicamente accede a los registros asociados a las reservas de las embarcaciones que le pertenecen (consultando dicha pertenencia al Sistema de Gestión de Flota), mientras que el **Administrador Financiero** accede al histórico global de la plataforma, sin restricción. Las **penalidades por cancelación no constituyen un tipo de registro independiente**: se identifican mediante el origen (cancelación flexible, moderada, o tardía/No-Show) registrado dentro de `RegistroDeReembolso` o `RegistroDeDispersión`, según corresponda.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar el historial de transacciones financieras de las embarcaciones propias (Prioridad: P1)

Como el sistema, al recibir del Propietario una solicitud de consulta del historial de transacciones financieras, quiero identificar las embarcaciones que le pertenecen consultando al Sistema de Gestión de Flota, y devolver únicamente los registros de cobro, reembolso y dispersión asociados a las reservas de dichas embarcaciones, de manera que el Propietario pueda revisar el historial correspondiente exclusivamente a su propio alcance.

**Por qué esta prioridad**: Es el mecanismo con el que los propietarios verifican que los cobros, reembolsos y dispersiones asociados a sus embarcaciones se hayan procesado correctamente, sin necesitar ni obtener acceso a información financiera de otros propietarios.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión previamente creados para reservas de embarcaciones de varios propietarios distintos, enviar al sistema una solicitud de consulta desde un Propietario específico y validar que el sistema retorna únicamente los registros asociados a las embarcaciones que le pertenecen.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa del historial de transacciones de las embarcaciones propias.
   - **Dado** que un Propietario cuenta con reservas de sus embarcaciones que ya tienen registros de cobro, reembolso y/o dispersión.
   - **Cuando** el Propietario solicita al sistema su historial de transacciones financieras.
   - **Entonces** el sistema consulta al Sistema de Gestión de Flota las embarcaciones asociadas a ese Propietario y devuelve únicamente los registros de cobro, reembolso y dispersión correspondientes a las reservas de dichas embarcaciones.

---

### Historia de Usuario 2 - Consultar el historial global de transacciones financieras (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero una solicitud de consulta del historial de transacciones financieras, quiero devolver el conjunto completo de registros de cobro, reembolso y dispersión existentes en el sistema, sin restricción de alcance por propietario o embarcación, de manera que el Administrador Financiero pueda supervisar el histórico global de la plataforma.

**Por qué esta prioridad**: Es el mecanismo con el que el Administrador Financiero supervisa la totalidad de las transacciones de la plataforma, necesario para su labor de supervisión financiera transversal.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión previamente creados para reservas de múltiples propietarios, enviar al sistema una solicitud de consulta desde el Administrador Financiero y validar que el sistema retorna el conjunto completo de registros existentes, sin aplicar ningún filtro por propietario.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta exitosa del historial global de transacciones.
   - **Dado** que existen registros de cobro, reembolso y/o dispersión para reservas de distintos propietarios.
   - **Cuando** el Administrador Financiero solicita al sistema el historial de transacciones financieras.
   - **Entonces** el sistema devuelve el conjunto completo de registros existentes, sin aplicar ningún filtro de alcance por propietario o embarcación.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Gestión de Flota está caído, agota el tiempo de espera (*timeout*) o es inalcanzable al momento en que el sistema necesita determinar las embarcaciones que pertenecen al Propietario solicitante?**
  El sistema no puede determinar correctamente el alcance de la consulta sin esta información. Aplica un manejo de errores controlado y responde al Propietario indicando que la consulta no pudo completarse, sin asumir un alcance parcial ni exponer registros de embarcaciones no confirmadas como propias.

- **¿Qué sucede si no existen registros de cobro, reembolso o dispersión dentro del alcance de la consulta (por ejemplo, un Propietario sin transacciones aún, o una consulta del Administrador Financiero en un momento sin transacciones registradas)?**
  El sistema devuelve un historial vacío; la ausencia de transacciones dentro del alcance consultado no constituye una condición de error.

- **¿Qué sucede si el Propietario intenta consultar transacciones asociadas a una embarcación que no le pertenece?**
  El sistema excluye dichos registros del resultado devuelto, dado que el alcance de la consulta está limitado exclusivamente a las embarcaciones que el Sistema de Gestión de Flota confirma como propias de ese Propietario.

- **¿Se incluyen en el historial las transacciones cuyo resultado aún se encuentra en curso (por ejemplo, un cobro o un reembolso a la espera del resultado de la Pasarela de Pago)?**
  Sí. El sistema devuelve cada registro con su estado vigente al momento de la consulta (en curso, éxito o fallo, según corresponda al tipo de registro), sin omitir las transacciones que aún no tienen un resultado definitivo.

- **¿Se distinguen las penalidades por cancelación de las liquidaciones estándar dentro del historial devuelto?**
  El sistema no gestiona un tipo de registro separado para "penalidades": estas se identifican mediante el origen (cancelación flexible, moderada o tardía/No-Show) registrado dentro de `RegistroDeReembolso` o `RegistroDeDispersión`, visible como parte del detalle de cada registro consultado.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Propietario o del Administrador Financiero una solicitud de consulta del historial de transacciones financieras.
- **RF-002**: El sistema DEBE, cuando el solicitante sea el Propietario, consultar al Sistema de Gestión de Flota las embarcaciones que le pertenecen, y limitar el resultado devuelto exclusivamente a los registros de cobro, reembolso y dispersión asociados a las reservas de dichas embarcaciones.
- **RF-003**: El sistema DEBE, cuando el solicitante sea el Administrador Financiero, devolver el conjunto completo de registros de cobro, reembolso y dispersión existentes en el sistema, sin restricción de alcance.
- **RF-004**: El sistema DEBE recuperar, dentro del alcance correspondiente al solicitante, los registros de cobro (definidos en "Procesar cobro"), reembolso (definidos en "Reembolsar dinero a arrendatario") y dispersión (definidos en "Dispersar fondos de alquiler") asociados a las reservas dentro de dicho alcance.
- **RF-005**: El sistema DEBE incluir, en cada registro devuelto, el tipo de transacción (cobro, reembolso o dispersión), la reserva asociada, el monto, el estado de la transacción (en curso, éxito o fallo) y, cuando estén disponibles, la referencia externa provista por la Pasarela de Pago y el origen de la transacción (por ejemplo, cancelación flexible, cancelación moderada, cancelación tardía/No-Show, finalización sin incidentes, o el resultado correspondiente de una disputa de garantía).
- **RF-006**: El sistema DEBE devolver un historial vacío, sin error, cuando no existan registros de cobro, reembolso o dispersión dentro del alcance de la consulta.
- **RF-007**: El sistema DEBE exponer este caso de uso exclusivamente al Propietario y al Administrador Financiero.
- **RF-008**: El sistema NO DEBE crear, modificar ni eliminar ningún registro de cobro, reembolso o dispersión como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Propietario y el Administrador Financiero, tanto para recibir la solicitud de consulta como para devolver cada registro del historial.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar el monto incluido en cada registro devuelto.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) ante fallas de comunicación con el Sistema de Gestión de Flota al determinar el alcance de la consulta de un Propietario.

### Entidades Clave

- **RegistroDeCobro (Entidad, definida en SPEC 5)**: En este caso de uso es únicamente consultada, no creada ni modificada.
- **RegistroDeReembolso (Entidad, definida en SPEC 9)**: En este caso de uso es únicamente consultada, no creada ni modificada.
- **RegistroDeDispersión (Entidad, definida en SPEC 10)**: En este caso de uso es únicamente consultada, no creada ni modificada.
- **SolicitudConsultaRegistrosFinancieros (DTO)**: Información recibida desde el Propietario o el Administrador Financiero para esta operación. Su alcance de resolución depende del actor que la origina.
- **EmbarcacionesDelPropietario (DTO)**: Información recibida desde el Sistema de Gestión de Flota, utilizada únicamente para determinar el alcance de una consulta realizada por un Propietario. Contiene la lista de identificadores de las embarcaciones que le pertenecen. No se persiste dentro del sistema.
- **RegistroFinancieroResultado (DTO)**: Resultado que el sistema devuelve al solicitante, representando de forma unificada cada transacción (cobro, reembolso o dispersión) dentro del alcance de la consulta. Contiene el tipo de transacción, la reserva asociada, el monto, el estado, la referencia externa y el origen, cuando corresponda. No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance Correcto, "100% de las consultas realizadas por un Propietario devuelven exclusivamente registros asociados a sus propias embarcaciones, con cero (0) registros de otros propietarios expuestos, en pruebas automatizadas".
- **CE-002**: Cobertura Global, "100% de las consultas realizadas por el Administrador Financiero devuelven el conjunto completo de registros de cobro, reembolso y dispersión existentes, con cero (0) omisiones detectadas en pruebas automatizadas".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeCobro, RegistroDeReembolso o RegistroDeDispersión como resultado de la ejecución de este caso de uso".
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con el Sistema de Gestión de Flota, al determinar el alcance de una consulta realizada por un Propietario, son manejadas mediante fallbacks controlados, sin exponer registros fuera de alcance ni provocar fallos inconsistentes en la consulta".