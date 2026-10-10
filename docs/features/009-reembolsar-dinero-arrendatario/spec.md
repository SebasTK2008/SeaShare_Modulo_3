# Especificación de Funcionalidad: UC09 - Reembolsar Dinero a Arrendatario

**Creado**: 2026-09-06 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Solicitar el reembolso ante una cancelación de reserva (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado corresponde a una cancelación flexible, moderada, tardía o por anfitrión, quiero recuperar el valor registrado para la reserva y solicitar el monto que corresponde devolver.

**Por qué esta prioridad**: Sin este cálculo y solicitud correctos, el arrendatario no recibiría el reembolso que le corresponde según la ventana de cancelación en la que se encuentre la reserva, incumpliendo directamente la lógica de cancelaciones definida para la plataforma.

**Prueba Independiente**: Con una reserva que cuenta con los montos de alquiler, seguro y depósito previamente calculados y registrados, invocar internamente el reembolso indicando cada uno de los estados de cancelación (flexible, moderada, tardía y por anfitrión) y validar que el sistema calcula el monto correspondiente (100% del valor total pagado en flexible y por anfitrión; 50% del alquiler más el 100% del depósito en moderada; 100% del depósito en tardía) y lo solicita a la Pasarela de Pago.

**Escenarios de Aceptación**:

1. **Escenario**: Reembolso por cancelación flexible (>72h).
   - **Dado** que existe un valor total previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada flexiblemente.
   - **Entonces** el sistema solicita la liberación del importe autorizado o, si ya fue capturado, un reembolso del 100% del valor total pagado registrado (alquiler, seguro y depósito). La operación queda pendiente hasta recibir confirmación externa.

2. **Escenario**: Reembolso por cancelación moderada (72h–24h).
  - **Dado** que existen el monto de alquiler y el depósito previamente registrados para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada moderadamente.
  - **Entonces** el sistema calcula el 50% del monto de alquiler registrado, le suma el 100% del depósito registrado y solicita la liberación o el reembolso de dicho monto según el estado del cobro original. El seguro no se reembolsa. La operación queda pendiente hasta recibir confirmación externa.

3. **Escenario**: Reembolso por cancelación del anfitrión.
  - **Dado** que existe un valor total previamente registrado para una reserva.
  - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada por anfitrión.
  - **Entonces** el sistema solicita la liberación o el reembolso del 100% del valor pagado al Arrendatario, según el estado del cobro original.

4. **Escenario**: Reembolso del depósito por cancelación tardía (<24h).
  - **Dado** que existe un depósito previamente registrado para una reserva.
  - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada tardíamente.
  - **Entonces** el sistema solicita la liberación o el reembolso del 100% del depósito registrado, según el estado del cobro original, sin reembolsar el alquiler ni el seguro. La operación queda pendiente hasta recibir confirmación externa.

---

### Historia de Usuario 2 - Solicitar el reembolso íntegro del depósito ante un resultado `REJECTED` de la disputa de garantía (Prioridad: P1)

Como el sistema, al recibir desde "Brindar Información de Disputa de Garantía" el estado `REJECTED`, quiero recuperar el depósito de garantía previamente registrado y solicitar a la Pasarela de Pago su liberación o reembolso íntegro, según el estado del cobro original.

**Por qué esta prioridad**: La mayoría de las reservas finalizan sin incidentes o con reclamos que no proceden, por lo que la liberación oportuna del depósito es indispensable para no retener el dinero del arrendatario cuando el Sistema de Reservas y Operaciones ya determinó que no corresponde retenerlo.

**Prueba Independiente**: Con una reserva que cuenta con un depósito de garantía cobrado y registrado, recibir el estado `REJECTED` desde "Brindar Información de Disputa de Garantía" y validar que se solicita la liberación o el reembolso del 100% del depósito registrado.

**Escenarios de Aceptación**:

1. **Escenario**: Reembolso por disputa rechazada o ausencia de reclamo informada por el Sistema de Reservas y Operaciones.
   - **Dado** que existe un depósito de garantía cobrado y registrado para una reserva.
   - **Cuando** "Brindar Información de Disputa de Garantía" informa `REJECTED`.
   - **Entonces** el sistema solicita la liberación o el reembolso íntegro del depósito cobrado, según el estado del cobro original. La operación queda pendiente hasta recibir confirmación externa.

---

### Historia de Usuario 3 - Registrar el resultado del reembolso reportado por la Pasarela de Pago (Prioridad: P1)

Como el sistema, al recibir de la Pasarela de Pago el resultado de una operación de liberación o reembolso previamente solicitada, quiero registrar su estado, monto confirmado y referencia externa, de manera que esta información quede disponible para "Consultar registros financieros".

**Por qué esta prioridad**: El registro correcto y confiable del resultado del reembolso garantiza la trazabilidad financiera de la plataforma frente al arrendatario y evita reportar como devueltos reembolsos que la Pasarela de Pago no ejecutó efectivamente.

**Prueba Independiente**: Con una solicitud de liberación o reembolso previamente enviada a la Pasarela de Pago, simular resultados completado, en proceso, rechazado, cancelado y expirado, y validar que el sistema registra cada estado y no marca como completada una operación no confirmada.

**Escenarios de Aceptación**:

1. **Escenario**: La Pasarela de Pago reporta el reembolso como exitoso.
   - **Dado** que existe una solicitud de reembolso previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema que la devolución fue exitosa.
   - **Entonces** el sistema registra la operación como completada, con el monto confirmado, el detalle y la referencia externa provista, dejándola disponible para "Consultar registros financieros".

2. **Escenario**: La Pasarela de Pago reporta el rechazo o fallo del reembolso.
   - **Dado** que existe una solicitud de reembolso previamente enviada a la Pasarela de Pago para una reserva.
   - **Cuando** la Pasarela de Pago reporta al sistema el rechazo o fallo de la transacción.
   - **Entonces** el sistema registra el estado externo y su detalle, sin marcar la operación como completada, dejando este resultado disponible internamente.

### Casos Extremos (Edge Cases)

- **¿Quién aplica el descuento por costos transaccionales en una cancelación flexible (>72h)?**
  El sistema no calcula ni aplica ningún descuento por costos transaccionales: solicita a la Pasarela de Pago la liberación del importe autorizado o, si ya fue capturado, el reembolso del monto que fija la regla de negocio para el estado recibido (100% del valor total de la reserva registrado en cancelación flexible), conforme a RF-012. El sistema únicamente registra cualquier costo o monto neto que la Pasarela de Pago reporte, cuando lo reporte, sin asumir ni inferir cómo lo aplica.

- **¿Qué diferencia existe entre el reembolso por ausencia de reclamo (ventana de 24 horas vencida) y el reembolso por una disputa rechazada?**
  No existe una diferencia financiera ni de tratamiento en Finanzas: ambos llegan como el estado `REJECTED` notificado por el Sistema de Reservas y Operaciones y devuelven el 100% del depósito cobrado. El Sistema de Finanzas no distingue el motivo ni recibe un atributo adicional.

- **¿Qué sucede cuando la Pasarela de Pago está caída, agota el tiempo de espera (*timeout*) o es inalcanzable al momento de enviar la solicitud de reembolso?**
  Conforme a RNF-003, el sistema no asume ningún resultado; aplica un manejo de errores controlado, registra la solicitud de reembolso con un estado que refleje la falla de comunicación (sin marcarla como exitosa ni como rechazada por la Pasarela) y conserva dicha solicitud disponible para consulta posterior.

- **¿Qué sucede si este caso de uso es invocado para una reserva que no cuenta con el monto de alquiler o el depósito de garantía previamente registrado, según corresponda al estado o evento recibido?**
  El sistema no ejecuta ningún cálculo parcial ni envía una solicitud a la Pasarela de Pago con un monto asumido; registra internamente un fallo, dado que la validación de existencia de dicha información corresponde previamente a "Brindar el estado de la reserva" o a "Brindar Información de Disputa de Garantía".

- **¿Qué sucede si se recibe un resultado de disputa después de que el reembolso del depósito ya fue solicitado?**
  El sistema valida el estado de la operación idempotente. No solicita una segunda devolución ni una liquidación sobre un depósito ya reembolsado; registra la inconsistencia para conciliación.

- **¿Qué sucede si la Pasarela de Pago reporta más de una vez el resultado de la misma transacción de reembolso (por ejemplo, una notificación repetida)?**
  Este caso de uso no define un mecanismo de deduplicación explícito. El sistema conserva el resultado ya registrado para esa solicitud de reembolso; una notificación repetida con el mismo resultado no altera el registro existente.

- **¿Qué sucede si se recibe más de una resolución para la misma disputa?**
  El sistema no sobrescribe la resolución aplicada ni solicita otra operación monetaria. Usa la clave idempotente y registra la inconsistencia para conciliación.

- **¿Este caso de uso admite retención parcial del depósito de garantía?**
  No. Conforme a SPEC 8 (canónica), el estado `COMPLETED`/`REJECTED` de la disputa es siempre un tratamiento total sobre el 100% del depósito; no existe un sub-resultado de retención parcial. RF-005 de esta SPEC lo confirma explícitamente.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir, mediante invocación interna por estado de reserva o por el estado `REJECTED` notificado por "Brindar información de disputa de garantía", una solicitud de reembolso para una reserva específica sin recibir un atributo de origen.
- **RF-002**: El sistema DEBE, cuando el estado sea cancelado flexiblemente o cancelado por anfitrión, solicitar la liberación o el reembolso del 100% del valor total pagado (alquiler, seguro y depósito) según el estado del cobro original.
- **RF-003**: El sistema DEBE, cuando el estado sea cancelado moderadamente, calcular el 50% del monto de alquiler registrado, sumarle el 100% del depósito registrado y solicitar su liberación o reembolso según el estado del cobro original. El seguro no forma parte del monto reembolsado.
- **RF-003A**: El sistema DEBE, cuando el estado sea cancelado tardíamente, solicitar la liberación o el reembolso del 100% del depósito registrado según el estado del cobro original. El alquiler y el seguro no forman parte del monto reembolsado.
- **RF-004**: El sistema DEBE, ante el estado `REJECTED` notificado por "Brindar información de disputa de garantía", recuperar el depósito cobrado y registrado y solicitar su liberación o reembolso íntegro según el estado del cobro original.
- **RF-005**: El sistema NO DEBE admitir retención parcial ni calcular un monto de reembolso parcial para el depósito de garantía.
- **RF-006**: El sistema DEBE registrar internamente la `IntenciónDeReembolso` en curso, vinculada al cobro original y con una clave idempotente, mientras se espera el resultado de la Pasarela de Pago.
- **RF-006A**: El `RegistroDeReembolso` DEBE conservar la referencia a la `IntenciónDeReembolso` que lo originó (`intencionDeReembolsoId`), garantizando trazabilidad directa desde el registro inmutable hasta la operación operativa.
- **RF-007**: El sistema DEBE recibir de la Pasarela de Pago el resultado del reembolso y registrarlo (exitoso o fallido) actualizando siempre el estado en la `IntenciónDeReembolso`. Si la operación es exitosa, el sistema DEBE además generar el `RegistroDeReembolso` inmutable de auditoría con su fecha y hora de creación (fechaHoraCreación), monto confirmado, detalle y referencia externa.
- **RF-008**: El sistema DEBE dejar disponible el `RegistroDeReembolso` inmutable creado para ser consultado internamente mediante "Consultar registros financieros".
- **RF-009**: El sistema NO DEBE crear un `RegistroDeReembolso` para un reembolso rechazado, cancelado, expirado o en proceso según la Pasarela de Pago; en estos casos solo actualiza la `IntenciónDeReembolso`.
- **RF-010**: El sistema DEBE registrar un error controlado cuando la reserva no tenga depósito cobrado y registrado.
- **RF-011**: El sistema DEBE distinguir el monto reembolsado del costo transaccional reportado por la Pasarela de Pago, cuando esta lo reporte.
- **RF-012**: El sistema NO DEBE calcular ni aplicar ningún descuento por costos transaccionales sobre los montos de reembolso. El sistema solicita el monto que fija la regla de negocio y se limita a registrar el costo o monto neto que la Pasarela de Pago reporte, cuando lo reporte, sin inferirlo ni asumirlo.
- **RF-013**: El sistema DEBE registrar, como parte del `RegistroDeReembolso`, la reserva, el propietario, la embarcación y, cuando aplique, la disputa asociada, sin almacenar un atributo de origen. DEBE garantizar coherencia con la identidad de operación definida en SPEC 07.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de Pago, tanto para la solicitud de reembolso como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto solicitado a la Pasarela de Pago y para el monto devuelto registrado, garantizando una precisión interna de 4 decimales antes de cualquier redondeo final hacia la pasarela o reportes.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) tanto para el envío de la solicitud de reembolso como para la recepción de su resultado, dado que "Consultar registros financieros" depende del resultado registrado por este caso de uso.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3 y actualizada en SPEC 4)**: En este caso de uso es consultada para recuperar el valor total de la reserva, el monto de alquiler y el depósito registrados en cancelaciones, o el depósito fijo cobrado y registrado cuando el estado notificado corresponde a una disputa `REJECTED`.
- **IntenciónDeReembolso (Entidad)**: Estructura persistida para representar la operación operativa de una liberación o reembolso. Conserva el estado o evento desencadenante, tipo de operación, monto solicitado, referencia del cobro original, clave idempotente, el estado de la solicitud (en proceso, aprobado, fallido, etc.) y la referencia externa. Esta entidad es la fuente de la verdad para conocer el estado y resultado de la operación.
- **RegistroDeReembolso (Entidad Inmutable)**: Métrica financiera informativa creada como subproducto de auditoría únicamente cuando la Pasarela de Pago confirma el reembolso como exitoso. Conserva la fecha y hora de creación (fechaHoraCreación), el monto confirmado, el detalle, la referencia externa, la reserva, el propietario, la embarcación, la referencia obligatoria a la intención que lo originó (`intencionDeReembolsoId`) y, cuando aplique, la disputa asociada. No participa en cálculos de reembolso ni sustituye a `IntenciónDeReembolso`, que conserva los datos operativos. Es consumida por "Consultar registros financieros".
- **SolicitudReembolso (DTO)**: Información recibida internamente desde "Brindar el estado de la reserva" o "Brindar Información de Disputa de Garantía". Contiene el identificador de la reserva y el motivo técnico de ejecución como estado, no un atributo de origen financiero.
- **SolicitudReembolsoPasarela (DTO)**: Información enviada a la Pasarela de Pago. Contiene el tipo de operación (liberación o reembolso), el monto, la referencia del cobro original, la referencia de la reserva y la clave idempotente.
- **ResultadoReembolsoPasarela (DTO)**: Información recibida desde la Pasarela de Pago. Contiene el tipo de operación, el estado externo, su detalle, el monto confirmado y la referencia externa asignada.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los reembolsos de depósito solicitados por el estado `REJECTED` corresponden exactamente al 100% del depósito cobrado y registrado".
- **CE-002**: Cumplimiento Arquitectónico, "0 decisiones sobre daños y 0 retenciones parciales son calculadas por este caso de uso; 0 descuentos por costos transaccionales son calculados o aplicados por el sistema".
- **CE-003**: Trazabilidad, "100% de las solicitudes de liberación o reembolso enviadas a la Pasarela de Pago quedan registradas internamente, y 100% de los resultados recibidos actualizan dicho registro, dejándolo disponible para 'Consultar registros financieros'". Verificar trazabilidad intención a registro (RegistroDeReembolso vincula intencionDeReembolsoId).
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de Pago, tanto al enviar la solicitud de reembolso como al recibir su resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones de reembolso en un estado indefinido".
- **CE-005**: Consolidación de Historias, "0 Historias de Usuario duplicadas o con disparadores solapados (`REJECTED`) coexisten en esta SPEC, confirmando que el tratamiento de ambos orígenes del depósito rechazado está unificado en la Historia de Usuario 2".