# Especificación de Funcionalidad: UC03 - Brindar Información de Reserva

**Creado**: 2026-09-06 (v2 — el sistema captura, mediante una consulta al Sistema de Gestión de Flota, el propietario y la capacidad máxima de pasajeros de la embarcación, y los registra junto con la información de la reserva, de manera que cada registro financiero posterior quede asociado a su propietario; respuestas integradas a los casos extremos)

> **Nota de trazabilidad**: Este caso de uso es **unidireccional**: el Sistema de Reservas y Operaciones **envía** al sistema los datos específicos de una reserva (embarcación, cantidad de días y número de pasajeros) que el arrendatario ya ingresó. El sistema incluye obligatoriamente (`<<include>>`) a "Brindar tarifa base" (SPEC 2) para obtener la tarifa vigente de esa embarcación, y **registra internamente** esta información como preparación para el cálculo posterior. Adicionalmente, para que todo registro financiero de la reserva pueda asociarse a su propietario (necesario para "Consultar registros financieros" (SPEC 12) y "Consultar ingresos" (SPEC 14)), el sistema consulta al Sistema de Gestión de Flota el **propietario** y la **capacidad máxima de pasajeros** de la embarcación identificada, y los registra junto con el resto de la información de la reserva. El sistema **no devuelve ninguna respuesta ni desglose** a Reservas dentro de este caso de uso. El desglose completo de precio (incluyendo seguro náutico y depósito de garantía) que sí es devuelto a Reservas corresponde a "Solicitar el valor calculado de la reserva" (SPEC 4, pendiente), que utilizará la información aquí registrada.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Registrar internamente la información de una reserva específica (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones los datos específicos de una reserva (identificador de la reserva, embarcación, cantidad de días y número de pasajeros) que el arrendatario ya ingresó, quiero incluir "Brindar tarifa base" para obtener la tarifa vigente de esa embarcación, consultar al Sistema de Gestión de Flota el propietario y la capacidad máxima de pasajeros de la embarcación, y registrar internamente toda esta información, de manera que quede disponible para cuando Reservas solicite posteriormente el valor calculado de la reserva y para que los registros financieros posteriores queden asociados a su propietario.

**Por qué esta prioridad**: Es el paso que deja preparada, dentro del sistema, la información necesaria (tarifa vigente, cantidad de días, número de pasajeros y el propietario de la embarcación) para que el cálculo definitivo del valor de la reserva pueda ejecutarse correctamente cuando se solicite, y para que todo registro financiero asociado a la reserva pueda atribuirse a su propietario.

**Prueba Independiente**: Enviar desde el Sistema de Reservas y Operaciones el identificador de una reserva, el identificador de una embarcación específica, la cantidad de días y el número de pasajeros, y validar que el sistema incluye "Brindar tarifa base", consulta al Sistema de Gestión de Flota el propietario y la capacidad máxima de la embarcación, registra internamente la información recibida junto con la tarifa, el propietario y la capacidad obtenidos, y no devuelve ninguna respuesta a Reservas.

**Escenarios de Aceptación**:

1. **Escenario**: Registro exitoso de la información de una reserva específica.
   - **Dado** que el arrendatario ingresó una embarcación específica, una cantidad de días y un número de pasajeros para una reserva.
   - **Cuando** el Sistema de Reservas y Operaciones envía esos datos al sistema mediante "Brindar información de reserva".
   - **Entonces** el sistema incluye "Brindar tarifa base" para obtener la tarifa vigente de la embarcación, consulta al Sistema de Gestión de Flota el propietario y la capacidad máxima de pasajeros de la embarcación, registra internamente la información de la reserva (identificador de la reserva, tarifa base, cantidad de días, número de pasajeros, propietario y capacidad máxima de la embarcación), y no devuelve ninguna respuesta a Reservas.

### Casos Extremos (Edge Cases)

- **¿Qué sucede si el Sistema de Reservas y Operaciones envía nuevamente la información de una reserva ya registrada previamente por el sistema (por ejemplo, si el arrendatario modifica la cantidad de días o el número de pasajeros antes de confirmar)?**
  El sistema trata la notificación como una actualización (*upsert*): re-incluye "Brindar tarifa base" y re-consulta el propietario y la capacidad máxima de la embarcación, y la información más reciente recibida reemplaza la previamente registrada para esa misma reserva. El valor calculado posteriormente ("Solicitar el valor calculado de la reserva") siempre se basa en la versión vigente.

- **¿Qué sucede si "Brindar tarifa base" no puede completarse (por ejemplo, por falla de comunicación con el Sistema de Gestión de Flota) mientras el sistema intenta registrar la información de la reserva, dado que este caso de uso no devuelve una respuesta a Reservas que le permita notificar el fallo?**
  Al ser este caso de uso unidireccional, el sistema no tiene canal para notificar el fallo. Conforme a RNF-003, registra el fallo internamente y NO persiste una `InformaciónDeReserva` incompleta (sin tarifa base) que pudiera contaminar el cálculo posterior del valor de la reserva. Si ya existía una versión válida previamente registrada para esa reserva, la conserva. El mismo tratamiento aplica si la consulta del propietario o de la capacidad máxima de la embarcación al Sistema de Gestión de Flota falla.

- **¿Qué sucede si el número de pasajeros recibido excede la capacidad máxima de la embarcación?**
  La capacidad máxima se obtiene del Sistema de Gestión de Flota en la misma operación en la que se captura el propietario. Si el número de pasajeros la excede, el sistema trata la solicitud como inválida: no persiste la información y registra el fallo internamente, sin canal de respuesta hacia Reservas; si existía una versión válida previa, la conserva.

- **¿Qué sucede si la cantidad de días o el número de pasajeros recibidos son inválidos (valores negativos o cero)?**
  El sistema valida que la cantidad de días sea mayor que cero y que el número de pasajeros esté en el rango de 1 hasta la capacidad máxima de la embarcación. Ante valores negativos o cero se trata la solicitud como inválida: no se persiste la información y se registra el fallo internamente; si existía una versión válida previa para la misma reserva, esta se conserva.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Sistema de Reservas y Operaciones los datos específicos de la reserva: identificador de la reserva, identificador de la embarcación, cantidad de días y número de pasajeros.
- **RF-002**: El sistema DEBE incluir (`<<include>>`) a "Brindar tarifa base" utilizando el identificador de la embarcación recibido, para obtener su tarifa vigente.
- **RF-003**: El sistema DEBE consultar al Sistema de Gestión de Flota el propietario y la capacidad máxima de pasajeros de la embarcación identificada, en la misma operación de registro de la información de la reserva.
- **RF-004**: El sistema DEBE registrar internamente la información de la reserva (identificador de la reserva, tarifa base obtenida, cantidad de días, número de pasajeros, propietario y capacidad máxima de la embarcación) como preparación para el cálculo posterior en "Solicitar el valor calculado de la reserva" y para que los registros financieros posteriores queden asociados a su propietario.
- **RF-005**: El sistema DEBE validar que la cantidad de días sea mayor que cero y que el número de pasajeros esté entre 1 y la capacidad máxima de la embarcación, no registrando la información cuando estos valores sean inválidos.
- **RF-006**: El sistema NO DEBE devolver ninguna respuesta ni desglose de precio al Sistema de Reservas y Operaciones dentro de este caso de uso.
- **RF-007**: El sistema NO DEBE calcular ningún valor total (monto del alquiler, seguro náutico o depósito de garantía) dentro de este caso de uso.
- **RF-008**: El sistema DEBE exponer este caso de uso exclusivamente al Sistema de Reservas y Operaciones.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar un DTO para recibir la solicitud del Sistema de Reservas y Operaciones, mapeando únicamente los atributos esenciales (identificador de la reserva, embarcación, cantidad de días y número de pasajeros).
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para almacenar la tarifa base registrada internamente.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto ante fallas en la inclusión de "Brindar tarifa base" o en la consulta del propietario/capacidad máxima al Sistema de Gestión de Flota, registrando el fallo internamente dado que, al ser este caso de uso unidireccional, no existe un canal de respuesta directo hacia el Sistema de Reservas y Operaciones para notificarlo.

### Entidades Clave

- **InformaciónDeReserva (Entidad)**: Estructura gestionada y persistida internamente por el sistema para representar la información financiera preliminar de una reserva específica. Contiene el identificador de la reserva, el identificador de la embarcación, la tarifa base aplicada (obtenida de "Brindar tarifa base"), la cantidad de días, el número de pasajeros, el propietario de la embarcación y su capacidad máxima de pasajeros (ambos consultados al Sistema de Gestión de Flota). Es creada por este caso de uso y consumida posteriormente por "Solicitar el valor calculado de la reserva"; el propietario aquí registrado se copia a cada registro financiero (cobro, reembolso y dispersión) que se genere para la reserva, de manera que dichos registros queden asociados a su propietario.
- **SolicitudInformacionReserva (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para esta operación. Contiene el identificador de la reserva, el identificador de la embarcación, la cantidad de días y el número de pasajeros. No se persiste tal cual; sus datos se utilizan para construir o actualizar la entidad InformaciónDeReserva.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Trazabilidad Interna, "100% de las reservas notificadas mediante este caso de uso quedan registradas internamente con la tarifa base, la cantidad de días, el número de pasajeros, el propietario y la capacidad máxima de la embarcación correctamente almacenados y disponibles para 'Solicitar el valor calculado de la reserva' y para la asociación de los registros financieros a su propietario, con cero (0) discrepancias detectadas en pruebas automatizadas".
- **CE-002**: Cumplimiento Arquitectónico, "0 respuestas o desgloses de precio devueltos por este caso de uso al Sistema de Reservas y Operaciones, confirmando su naturaleza unidireccional".
- **CE-003**: Resiliencia del Sistema, "100% de las fallas simuladas en la inclusión de 'Brindar tarifa base' o en la consulta del propietario/capacidad máxima al Sistema de Gestión de Flota quedan registradas internamente mediante manejo de errores controlado, sin dejar información de reserva incompleta o corrupta".
