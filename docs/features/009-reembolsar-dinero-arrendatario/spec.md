# Especificación de Funcionalidad: UC09 - Reembolsar Dinero a Arrendatario

**Creado**: 2026-09-06 

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Solicitar el reembolso ante una cancelación de reserva (flexible o moderada) (Prioridad: P1)

Como el sistema, al ser invocado internamente por "Brindar el estado de la reserva" cuando el estado informado de una reserva corresponde a una cancelación flexible o moderada, quiero recuperar el valor de alquiler previamente registrado para esa reserva y calcular el monto a devolver según la ventana de cancelación informada, de manera que pueda solicitar a la Pasarela de Pago la devolución del monto correspondiente.

**Por qué esta prioridad**: Sin este cálculo y solicitud correctos, el arrendatario no recibiría el reembolso que le corresponde según la ventana de cancelación en la que se encuentre la reserva, incumpliendo directamente la lógica de cancelaciones definida para la plataforma.

**Prueba Independiente**: Con una reserva que cuenta con un valor de alquiler previamente calculado y registrado, invocar internamente el reembolso indicando cada una de las dos ventanas de cancelación (flexible y moderada) y validar que el sistema calcula el monto correspondiente (100% o 50% del valor de alquiler registrado) y lo solicita a la Pasarela de Pago.

**Escenarios de Aceptación**:

1. **Escenario**: Reembolso por cancelación flexible (>72h).
   - **Dado** que existe un valor de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada flexiblemente.
   - **Entonces** el sistema solicita la liberación del importe autorizado o, si ya fue capturado, un reembolso del 100% del valor de alquiler registrado. La operación queda pendiente hasta recibir confirmación externa.

2. **Escenario**: Reembolso por cancelación moderada (72h–24h).
   - **Dado** que existe un valor de alquiler previamente registrado para una reserva.
   - **Cuando** "Brindar el estado de la reserva" informa que la reserva fue cancelada moderadamente.
   - **Entonces** el sistema calcula el 50% del valor de alquiler registrado y solicita la liberación o el reembolso de dicho monto según el estado del cobro original. La operación queda pendiente hasta recibir confirmación externa.

---

### Historia de Usuario 2 - Solicitar el reembolso íntegro del depósito ante un resultado `RECHAZADO` de la disputa de garantía (Prioridad: P1)

Como el sistema, al recibir desde "Brindar Información de Disputa de Garantía" el estado `RECHAZADO` para una reserva —ya sea porque el propietario no reportó ningún reclamo dentro de la ventana de 24 horas, o porque el reclamo reportado fue evaluado como improcedente por el Módulo 2—, quiero recuperar el depósito de garantía previamente registrado (capturado) para esa reserva y solicitar a la Pasarela de Pago su reembolso íntegro, de manera que el depósito capturado sea devuelto al Arrendatario sin requerir ninguna intervención del Administrador Financiero.

**Por qué esta prioridad**: La mayoría de las reservas finalizan sin incidentes o con reclamos que no proceden, por lo que la liberación oportuna del depósito es indispensable para no retener el dinero del arrendatario cuando el Módulo 2 ya determinó que no corresponde retenerlo.

**Nota**: `RECHAZADO` es un único estado de la disputa (SPEC 8) que cubre ambas circunstancias de origen (ausencia de reclamo o reclamo improcedente); el tratamiento financiero que ejecuta este caso de uso es idéntico en ambos casos.

**Prueba Independiente**: Con una reserva que cuenta con un depósito de garantía capturado, recibir el estado `RECHAZADO` de una disputa —simulando por separado el origen "ausencia de reclamo" y el origen "reclamo improcedente"— y validar en ambos casos que el sistema solicita el reembolso del 100% del depósito registrado.

**Escenarios de Aceptación**:

1. **Escenario**: Reembolso por ausencia de reclamo al vencer la ventana de 24 horas.
   - **Dado** que existe un depósito de garantía capturado y registrado para una reserva.
   - **Cuando** "Brindar Información de Disputa de Garantía" informa `RECHAZADO` porque el Módulo 2 cerró la ventana de reporte sin registrar un reclamo.
   - **Entonces** el sistema solicita el reembolso íntegro del depósito capturado. La operación queda pendiente hasta recibir confirmación externa.

2. **Escenario**: Reembolso por disputa rechazada (el reclamo no procede).
   - **Dado** que existe un depósito de garantía capturado y registrado para una reserva.
   - **Cuando** "Brindar Información de Disputa de Garantía" informa `RECHAZADO` porque el Módulo 2 evaluó el reclamo como improcedente.
   - **Entonces** el sistema solicita el reembolso íntegro del depósito capturado. La operación queda pendiente hasta recibir confirmación externa.

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
  *(Punto abierto — ver nota al inicio de este documento)*. El sistema no debe inferir cómo se aplican los costos transaccionales. Solicita el monto definido por la política de negocio y registra cualquier costo o monto neto reportado por la Pasarela de Pago, sin asumir que esta lo absorbe o lo descuenta automáticamente. Pendiente de confirmación explícita del negocio antes de cerrar este punto como definitivo.

- **¿Qué diferencia existe entre el reembolso por ausencia de reclamo y el reembolso por una disputa con reclamo rechazado?**
  Ninguna en su tratamiento financiero: ambos son consecuencias del mismo estado `RECHAZADO` informado por "Brindar Información de Disputa de Garantía" (SPEC 8) y devuelven el 100% del depósito capturado. La diferencia es únicamente de origen operativo dentro del Módulo 2 (vencimiento de la ventana de 24 horas sin reporte, vs. evaluación explícita de un reclamo como improcedente); el Módulo 2 no distingue estos dos orígenes al informar el estado a Finanzas.

- **¿Qué sucede cuando la Pasarela de Pago está caída, agota el tiempo de espera (*timeout*) o es inalcanzable al momento de enviar la solicitud de reembolso?**
  Conforme a RNF-003, el sistema no asume ningún resultado; aplica un manejo de errores controlado, registra la solicitud de reembolso con un estado que refleje la falla de comunicación (sin marcarla como exitosa ni como rechazada por la Pasarela) y conserva dicha solicitud disponible para consulta posterior.

- **¿Qué sucede si este caso de uso es invocado para una reserva que no cuenta con el valor de alquiler o el depósito de garantía previamente registrado, según corresponda al origen de la solicitud?**
  El sistema no ejecuta ningún cálculo parcial ni envía una solicitud a la Pasarela de Pago con un monto asumido; registra internamente un fallo, dado que la validación de existencia de dicha información corresponde previamente a "Brindar el estado de la reserva" o a "Brindar Información de Disputa de Garantía".

- **¿Qué sucede si se recibe un resultado de disputa después de que el reembolso automático ya fue solicitado?**
  El sistema valida el estado de la operación idempotente. No solicita una segunda devolución ni una liquidación sobre un depósito ya reembolsado; registra la inconsistencia para conciliación.

- **¿Qué sucede si la Pasarela de Pago reporta más de una vez el resultado de la misma transacción de reembolso (por ejemplo, una notificación repetida)?**
  El contexto no define un mecanismo de deduplicación explícito. El sistema conserva el resultado ya registrado para esa solicitud de reembolso; una notificación repetida con el mismo resultado no altera el registro existente.

- **¿Qué sucede si se recibe más de una resolución para la misma disputa?**
  El sistema no sobrescribe la resolución aplicada ni solicita otra operación monetaria. Usa la clave idempotente y registra la inconsistencia para conciliación.

- **¿Este caso de uso admite retención parcial del depósito de garantía?**
  No. Conforme a SPEC 8 (canónica), el estado `COMPLETADO`/`RECHAZADO` de la disputa es siempre un tratamiento total sobre el 100% del depósito; no existe un sub-resultado de retención parcial. RF-005 de esta SPEC lo confirma explícitamente.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir, mediante invocación interna por cancelación o desde "Brindar Información de Disputa de Garantía", una solicitud de reembolso para una reserva específica junto con el origen `cancelación flexible`, `cancelación moderada` o `disputa rechazada` (estado `RECHAZADO`, sin distinguir su origen operativo dentro del Módulo 2).
- **RF-002**: El sistema DEBE, cuando el origen sea cancelación flexible, recuperar el valor de alquiler previamente registrado y solicitar la liberación del importe autorizado o el reembolso del 100% si ya fue capturado.
- **RF-003**: El sistema DEBE, cuando el origen sea cancelación moderada, calcular el 50% del valor de alquiler registrado y solicitar su liberación o reembolso según el estado del cobro original.
- **RF-004**: El sistema DEBE, cuando el origen sea una disputa `RECHAZADO` (ausencia de reclamo o reclamo improcedente), recuperar el depósito capturado y solicitar su reembolso íntegro.
- **RF-005**: El sistema NO DEBE admitir retención parcial ni calcular un monto de reembolso parcial para el depósito de garantía.
- **RF-006**: El sistema DEBE registrar internamente la solicitud de reembolso en curso, vinculada al cobro original y con una clave idempotente, mientras se espera el resultado de la Pasarela de Pago.
- **RF-007**: El sistema DEBE recibir de la Pasarela de Pago el resultado del reembolso, registrando el monto confirmado, el estado externo, su detalle y la referencia externa provista.
- **RF-008**: El sistema DEBE dejar disponible el resultado registrado del reembolso para ser consultado mediante "Consultar registros financieros".
- **RF-009**: El sistema NO DEBE registrar ni reportar como completado un reembolso rechazado, cancelado, expirado o en proceso según la Pasarela de Pago.
- **RF-010**: El sistema DEBE registrar un error controlado cuando la reserva no tenga depósito capturado y registrado.
- **RF-011**: El sistema DEBE distinguir el monto reembolsado del costo transaccional cobrado por la Pasarela de Pago.
- **RF-012**: El sistema DEBE registrar, como parte del `RegistroDeReembolso`, el propietario, la embarcación y la disputa asociada, incluyendo si el `RECHAZADO` se originó por ausencia de reclamo o por un reclamo evaluado como improcedente (dato meramente informativo/de trazabilidad, sin que cambie el tratamiento financiero aplicado).

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con la Pasarela de Pago, tanto para la solicitud de reembolso como para el resultado recibido.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto solicitado a la Pasarela de Pago y para el monto devuelto registrado.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto (*timeouts*, *fallbacks*) tanto para el envío de la solicitud de reembolso como para la recepción de su resultado, dado que "Consultar registros financieros" depende del resultado registrado por este caso de uso.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3 y actualizada en SPEC 4)**: En este caso de uso es consultada para recuperar el valor de alquiler registrado en cancelaciones o el depósito fijo capturado cuando el origen sea una disputa `RECHAZADO`.
- **RegistroDeReembolso (Entidad)**: Estructura persistida para representar el ciclo de una liberación o reembolso sobre una reserva. Conserva el origen, tipo de operación, monto solicitado, referencia del cobro original, clave idempotente, monto confirmado, estado externo, detalle y referencia externa. También conserva el propietario, la embarcación y la disputa asociados (incluyendo si el `RECHAZADO` se originó por ausencia de reclamo) y es consumida por "Consultar registros financieros".
- **SolicitudReembolso (DTO)**: Información recibida internamente desde "Brindar el estado de la reserva" o "Brindar Información de Disputa de Garantía". Contiene el identificador de la reserva y el origen (cancelación flexible, cancelación moderada o disputa `RECHAZADO`).
- **SolicitudReembolsoPasarela (DTO)**: Información enviada a la Pasarela de Pago. Contiene el tipo de operación (liberación o reembolso), el monto, la referencia del cobro original, la referencia de la reserva y la clave idempotente.
- **ResultadoReembolsoPasarela (DTO)**: Información recibida desde la Pasarela de Pago. Contiene el tipo de operación, el estado externo, su detalle, el monto confirmado y la referencia externa asignada.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los reembolsos de depósito solicitados por un resultado `RECHAZADO` de la disputa corresponden exactamente al 100% del depósito capturado, independientemente de si el origen fue ausencia de reclamo o reclamo improcedente".
- **CE-002**: Cumplimiento Arquitectónico, "0 decisiones sobre daños y 0 retenciones parciales son calculadas por este caso de uso".
- **CE-003**: Trazabilidad, "100% de las solicitudes de liberación o reembolso enviadas a la Pasarela de Pago quedan registradas internamente, y 100% de los resultados recibidos actualizan dicho registro, dejándolo disponible para 'Consultar registros financieros'".
- **CE-004**: Resiliencia del Sistema, "100% de las fallas de comunicación simuladas con la Pasarela de Pago, tanto al enviar la solicitud de reembolso como al recibir su resultado, son manejadas mediante fallbacks controlados, sin dejar transacciones de reembolso en un estado indefinido".
- **CE-005**: Consolidación de Historias, "0 Historias de Usuario duplicadas o con disparadores solapados (`RECHAZADO`) coexisten en esta SPEC, confirmando que el tratamiento de ambos orígenes del depósito rechazado está unificado en la Historia de Usuario 2".