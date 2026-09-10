# Especificación de Funcionalidad: UC12 - Consultar Registros Financieros

**Creado**: 2026-09-06 (v3 — el alcance de la consulta de un Propietario se resuelve mediante el atributo de propietario asociado a cada registro financiero —capturado en "Brindar información de reserva" (SPEC 3) y copiado a los registros de cobro, reembolso y dispersión—, eliminando la dependencia de consultar al Sistema de Gestión de Flota las embarcaciones del propietario. Se mantiene la entrega siempre paginada definida en la versión v2)

> **Nota de trazabilidad**: Este caso de uso es invocado directamente por el Propietario y por el Administrador Financiero para consultar, de solo lectura y de forma paginada, el historial de transacciones (cobros, reembolsos y dispersiones) registrado por "Procesar cobro" (SPEC 5), "Reembolsar dinero a arrendatario" (SPEC 9) y "Liquidar fondos de alquiler" (SPEC 10). El alcance de la consulta difiere según el actor: el **Propietario** únicamente accede a los registros cuyo **propietario asociado** coincida con el suyo, mientras que el **Administrador Financiero** accede al histórico global de la plataforma, sin restricción. El propietario asociado a cada registro financiero es capturado en "Brindar información de reserva" (SPEC 3) y copiado a los registros de cobro, reembolso y dispersión cuando estos se crean; por lo tanto, este caso de uso **no consulta al Sistema de Gestión de Flota** para determinar el alcance de un Propietario. En ambos casos, el sistema entrega los registros divididos en páginas: en ningún caso responde con el conjunto completo de registros en una sola respuesta. Las **penalidades por cancelación no constituyen un tipo de registro independiente**: se identifican mediante el origen (cancelación flexible, moderada, o tardía/No-Show) registrado dentro de `RegistroDeReembolso` o `RegistroDeDispersión`, según corresponda.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Consultar de forma paginada el historial de transacciones financieras propias (Prioridad: P1)

Como el sistema, al recibir del Propietario una solicitud paginada de consulta del historial de transacciones financieras, quiero recuperar los registros de cobro, reembolso y dispersión cuyo propietario asociado coincida con el del solicitante, y devolverlos página por página, de manera que el Propietario pueda revisar el historial correspondiente exclusivamente a su propio alcance, sin recibir el conjunto completo de registros en una única respuesta.

**Por qué esta prioridad**: Es el mecanismo con el que los propietarios verifican que los cobros, reembolsos y dispersiones asociados a sus reservas se hayan procesado correctamente, sin necesitar ni obtener acceso a información financiera de otros propietarios. Dado que cada registro financiero conserva su propietario asociado (SPEC 3), el alcance se resuelve sin depender de consultas externas al Sistema de Gestión de Flota. La paginación evita respuestas de tamaño no controlado a medida que el historial de un propietario crece.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión previamente creados para reservas cuya información registrada corresponde a varios propietarios distintos, enviar al sistema una solicitud de consulta paginada desde un Propietario específico y validar que el sistema retorna únicamente los registros de esa página cuyo propietario asociado coincide con el del solicitante, junto con los metadatos de paginación correctos.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta paginada exitosa del historial de transacciones propias.
   - **Dado** que un Propietario cuenta con más registros de cobro, reembolso y/o dispersión asociados a él como propietario que los que caben en una página.
   - **Cuando** el Propietario solicita al sistema una página de su historial de transacciones financieras.
   - **Entonces** el sistema devuelve únicamente los registros de la página solicitada cuyo propietario asociado coincide con el del solicitante, junto con el número de página, el tamaño de página, el total de registros dentro de su alcance y el total de páginas.

2. **Escenario**: Consulta de una página posterior del historial propio.
   - **Dado** que el historial del Propietario abarca varias páginas.
   - **Cuando** el Propietario solicita una página distinta a la primera.
   - **Entonces** el sistema devuelve los registros correspondientes exclusivamente a esa página, sin repetir ni omitir registros respecto a las demás páginas de su historial.

---

### Historia de Usuario 2 - Consultar de forma paginada el historial global de transacciones financieras (Prioridad: P1)

Como el sistema, al recibir del Administrador Financiero una solicitud paginada de consulta del historial de transacciones financieras, quiero devolver, página por página, el conjunto completo de registros de cobro, reembolso y dispersión existentes en el sistema, sin restricción de alcance por propietario o embarcación, de manera que el Administrador Financiero pueda supervisar el histórico global de la plataforma sin recibir todos los registros en una sola respuesta.

**Por qué esta prioridad**: Es el mecanismo con el que el Administrador Financiero supervisa la totalidad de las transacciones de la plataforma, necesario para su labor de supervisión financiera transversal. La paginación es indispensable dado que el histórico global no tiene restricción de alcance y puede crecer sin límite.

**Prueba Independiente**: Con registros de cobro, reembolso y dispersión previamente creados para reservas de múltiples propietarios, enviar al sistema una solicitud de consulta paginada desde el Administrador Financiero y validar que el sistema retorna únicamente los registros de la página solicitada, sin aplicar ningún filtro por propietario, junto con los metadatos de paginación correctos.

**Escenarios de Aceptación**:

1. **Escenario**: Consulta paginada exitosa del historial global de transacciones.
   - **Dado** que existen más registros de cobro, reembolso y/o dispersión para reservas de distintos propietarios que los que caben en una página.
   - **Cuando** el Administrador Financiero solicita al sistema una página del historial de transacciones financieras.
   - **Entonces** el sistema devuelve únicamente los registros correspondientes a esa página, sin aplicar ningún filtro de alcance por propietario o embarcación, junto con el número de página, el tamaño de página, el total de registros existentes y el total de páginas.

2. **Escenario**: Consulta de una página posterior del historial global.
   - **Dado** que el historial global abarca varias páginas.
   - **Cuando** el Administrador Financiero solicita una página distinta a la primera.
   - **Entonces** el sistema devuelve los registros correspondientes exclusivamente a esa página, sin repetir ni omitir registros respecto a las demás páginas del historial global.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si un registro financiero no tiene un propietario asociado (por ejemplo, un registro legado o inconsistente)?**
  El sistema no puede atribuir dicho registro a ningún Propietario. Conforme a RF-013, lo excluye del alcance de las consultas de todo Propietario y únicamente puede aparecer en el histórico global del Administrador Financiero, registrando la inconsistencia internamente sin alterar la consulta del resto de los registros.

- **¿Qué sucede si no existen registros de cobro, reembolso o dispersión dentro del alcance de la consulta (por ejemplo, un Propietario sin transacciones aún, o una consulta del Administrador Financiero en un momento sin transacciones registradas)?**
  El sistema devuelve una página vacía, sin error; la ausencia de transacciones dentro del alcance consultado no constituye una condición de error.

- **¿Qué sucede si el número de página solicitado excede el total de páginas disponibles dentro del alcance de la consulta?**
  El sistema devuelve una página vacía junto con los metadatos de paginación correspondientes (total de registros y total de páginas), sin considerar esto una condición de error.

- **¿Qué sucede si el número de página o el tamaño de página indicado por el solicitante es inválido (por ejemplo, negativo, cero o no numérico)?**
  El sistema responde con un error controlado indicando que los parámetros de paginación no son válidos, sin ejecutar la consulta.

- **¿Puede un Propietario consultar o recibir registros financieros de otros propietarios (por ejemplo, de embarcaciones o reservas ajenas)?**
  No. El alcance de la consulta de un Propietario se resuelve exclusivamente por el propietario asociado a cada registro financiero: los registros cuyo propietario no coincida con el del solicitante nunca se incluyen en su resultado, sin importar la embarcación asociada a la reserva, por lo que no existe mecanismo que permita a un Propietario acceder a información de terceros.

- **¿Se incluyen en el historial las transacciones cuyo resultado aún se encuentra en curso (por ejemplo, un cobro o un reembolso a la espera del resultado de la Pasarela de Pago)?**
  Sí. El sistema devuelve cada registro con su estado vigente al momento de la consulta (en curso, éxito o fallo, según corresponda al tipo de registro), sin omitir las transacciones que aún no tienen un resultado definitivo.

- **¿Se distinguen las penalidades por cancelación de las liquidaciones estándar dentro del historial devuelto?**
  El sistema no gestiona un tipo de registro separado para "penalidades": estas se identifican mediante el origen (cancelación flexible, moderada o tardía/No-Show) registrado dentro de `RegistroDeReembolso` o `RegistroDeDispersión`, visible como parte del detalle de cada registro consultado.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Propietario o del Administrador Financiero una solicitud de consulta del historial de transacciones financieras, incluyendo el número de página y, opcionalmente, el tamaño de página deseado.
- **RF-002**: El sistema DEBE, cuando el solicitante sea el Propietario, limitar el resultado devuelto exclusivamente a los registros de cobro, reembolso y dispersión cuyo propietario asociado coincida con el identificador del Propietario solicitante, sin consultar al Sistema de Gestión de Flota para determinar dicho alcance.
- **RF-003**: El sistema DEBE, cuando el solicitante sea el Administrador Financiero, calcular el conjunto completo de registros de cobro, reembolso y dispersión existentes en el sistema, sin restricción de alcance, y entregarlo paginado conforme a los parámetros de paginación recibidos en la solicitud.
- **RF-004**: El sistema DEBE recuperar, dentro del alcance correspondiente al solicitante (propietario asociado igual al solicitante, o histórico global para el Administrador Financiero), los registros de cobro (definidos en "Procesar cobro"), reembolso (definidos en "Reembolsar dinero a arrendatario") y dispersión (definidos en "Liquidar fondos de alquiler").
- **RF-005**: El sistema DEBE incluir, en cada registro devuelto, el tipo de transacción (cobro, reembolso o dispersión), la reserva asociada, el propietario y la embarcación asociados al registro, el monto, el estado de la transacción (en curso, éxito o fallo) y, cuando estén disponibles, la referencia externa provista por la Pasarela de Pago y el origen de la transacción (por ejemplo, cancelación flexible, cancelación moderada, cancelación tardía/No-Show, finalización sin incidentes, o el resultado correspondiente de una disputa de garantía).
- **RF-006**: El sistema DEBE devolver una página vacía, sin error, cuando no existan registros dentro del alcance de la consulta o cuando el número de página solicitado no contenga registros dentro de dicho alcance.
- **RF-007**: El sistema DEBE exponer este caso de uso exclusivamente al Propietario y al Administrador Financiero.
- **RF-008**: El sistema NO DEBE crear, modificar ni eliminar ningún registro de cobro, reembolso o dispersión como parte de la ejecución de este caso de uso, al tratarse de una operación exclusivamente de consulta.
- **RF-009**: El sistema DEBE entregar los registros de este caso de uso exclusivamente de forma paginada, sin exponer en ninguna respuesta el conjunto completo de registros dentro del alcance de una sola vez.
- **RF-010**: El sistema DEBE aplicar un tamaño de página por defecto (por ejemplo, 20 registros) cuando el solicitante no indique explícitamente un tamaño de página, y DEBE responder con un error controlado cuando el número de página o el tamaño de página indicados sean inválidos (por ejemplo, negativos, cero o no numéricos).
- **RF-011**: El sistema DEBE incluir en cada respuesta, junto con los registros correspondientes a la página solicitada, los metadatos de paginación: número de página actual, tamaño de página utilizado, total de registros dentro del alcance de la consulta y total de páginas resultantes.
- **RF-012**: El sistema NO DEBE consultar al Sistema de Gestión de Flota en ningún momento dentro de este caso de uso; el alcance de la consulta de un Propietario se determina exclusivamente por el propietario asociado a cada registro financiero.
- **RF-013**: El sistema DEBE excluir del alcance de las consultas de todo Propietario los registros financieros sin propietario asociado (inconsistencias), los cuales únicamente pueden ser visibles en el histórico global del Administrador Financiero.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Propietario y el Administrador Financiero, tanto para recibir la solicitud de consulta (incluyendo los parámetros de paginación) como para devolver cada página del historial junto con sus metadatos.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para representar el monto incluido en cada registro devuelto.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante la presencia de registros financieros sin propietario asociado, excluyéndolos del alcance de las consultas de Propietario sin alterar el histórico global del Administrador Financiero ni provocar respuestas inconsistentes.
- **RNF-004**: El sistema DEBE garantizar la consistencia de la paginación (sin omitir ni duplicar registros entre páginas consecutivas) para un mismo conjunto de datos dentro del alcance correspondiente a cada solicitante.

### Entidades Clave

- **RegistroDeCobro (Entidad, definida en SPEC 5)**: En este caso de uso es únicamente consultada, no creada ni modificada. Su atributo de propietario asociado determina si pertenece al alcance de una consulta realizada por un Propietario.
- **RegistroDeReembolso (Entidad, definida en SPEC 9)**: En este caso de uso es únicamente consultada, no creada ni modificada. Su atributo de propietario asociado determina si pertenece al alcance de una consulta realizada por un Propietario.
- **RegistroDeDispersión (Entidad, definida en SPEC 10)**: En este caso de uso es únicamente consultada, no creada ni modificada. Su atributo de propietario asociado determina si pertenece al alcance de una consulta realizada por un Propietario.
- **SolicitudConsultaRegistrosFinancieros (DTO)**: Información recibida desde el Propietario o el Administrador Financiero para esta operación. Contiene el número de página y, opcionalmente, el tamaño de página deseado. Su alcance de resolución depende del actor que la origina.
- **RegistroFinancieroResultado (DTO)**: Representa, de forma unificada, cada transacción (cobro, reembolso o dispersión) dentro del alcance de la consulta. Contiene el tipo de transacción, la reserva asociada, el propietario y la embarcación asociados al registro, el monto, el estado, la referencia externa y el origen, cuando corresponda. No representa una entidad persistida.
- **PáginaDeRegistrosFinancierosResultado (DTO)**: Resultado que el sistema devuelve al solicitante para cada solicitud. Contiene la lista de `RegistroFinancieroResultado` correspondiente a la página solicitada, junto con el número de página actual, el tamaño de página utilizado, el total de registros dentro del alcance y el total de páginas. No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Alcance Correcto, "100% de las páginas consultadas por un Propietario contienen exclusivamente registros cuyo propietario asociado coincide con el del solicitante, con cero (0) registros de otros propietarios expuestos, en pruebas automatizadas".
- **CE-002**: Cobertura Global, "100% de las páginas consultadas por el Administrador Financiero, reconstruidas en su conjunto, corresponden exactamente al total de registros de cobro, reembolso y dispersión existentes, con cero (0) omisiones ni duplicados detectados en pruebas automatizadas".
- **CE-003**: Integridad de Solo Lectura, "0 modificaciones, creaciones o eliminaciones registradas sobre RegistroDeCobro, RegistroDeReembolso o RegistroDeDispersión como resultado de la ejecución de este caso de uso".
- **CE-004**: Consistencia de Alcance, "100% de los registros financieros sin propietario asociado son excluidos del alcance de las consultas de Propietario, permaneciendo visibles únicamente en el histórico global del Administrador Financiero, sin provocar fallos inconsistentes en la consulta".
- **CE-005**: Autonomía del Alcance, "0 consultas realizadas por este caso de uso al Sistema de Gestión de Flota, confirmando que el alcance de las consultas se resuelve íntegramente mediante el propietario asociado a cada registro financiero".
- **CE-006**: Consistencia de Paginación, "100% de las respuestas de este caso de uso se entregan paginadas conforme a RF-009, sin que en ningún caso se exponga el conjunto completo de registros en una única respuesta, y 100% de los metadatos de paginación (total de registros y total de páginas) corresponden exactamente al alcance evaluado, con cero (0) discrepancias detectadas en pruebas automatizadas".