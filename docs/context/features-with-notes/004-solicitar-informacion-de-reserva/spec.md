# Especificación de Funcionalidad: UC04 - Solicitar el Valor Calculado de la Reserva

**Creado**: 2026-09-06

> **Nota de trazabilidad**: Este caso de uso es invocado por el Sistema de Reservas y Operaciones para obtener el desglose completo del valor de una reserva específica, previamente registrada mediante "Brindar información de reserva" (SPEC 3). El sistema **no vuelve a incluir** ("Brindar tarifa base" (SPEC 2) en este caso de uso: la tarifa base, ya ajustada dinámicamente, es reutilizada directamente desde la información registrada por SPEC 3. El desglose calculado aquí (monto de alquiler, seguro náutico y depósito de garantía) es el que queda disponible internamente para ser utilizado posteriormente por "Procesar cobro" (SPEC 5, pendiente).

## Escenarios de Usuario y Pruebas *(obligatorio)*

### Historia de Usuario 1 - Calcular y devolver el desglose completo del valor de una reserva registrada (Prioridad: P1)

Como el sistema, al recibir del Sistema de Reservas y Operaciones una solicitud del valor calculado de una reserva específica (identificada por su identificador de reserva), quiero recuperar la información previamente registrada para esa reserva (tarifa base vigente, cantidad de días y número de pasajeros) y calcular sobre ella el monto de alquiler, el monto del seguro náutico y el monto del depósito de garantía, de manera que pueda devolver al Sistema de Reservas y Operaciones el desglose completo del valor de la reserva junto con su valor total, dejando además esta información disponible internamente para el proceso de cobro posterior.

**Por qué esta prioridad**: Este caso de uso produce el valor financiero definitivo de la reserva —a diferencia de la estimación preliminar de "Solicitar estimación para reserva", que excluye explícitamente el depósito de garantía—, por lo que es el paso indispensable antes de que pueda ejecutarse cualquier cobro real al arrendatario.

**Prueba Independiente**: Registrar previamente la información de una reserva mediante "Brindar información de reserva" y luego enviar, desde el Sistema de Reservas y Operaciones, una solicitud del valor calculado para esa misma reserva, validando que el sistema recupera la información registrada, calcula correctamente el monto de alquiler, el seguro náutico y el depósito de garantía, y devuelve el desglose completo junto con el valor total.

**Escenarios de Aceptación**:

1. **Escenario**: Cálculo exitoso del desglose completo de una reserva con información previamente registrada.
   - **Dado** que existe información previamente registrada para una reserva específica (tarifa base vigente, cantidad de días y número de pasajeros).
   - **Cuando** el Sistema de Reservas y Operaciones solicita al sistema el valor calculado de esa reserva mediante su identificador.
   - **Entonces** el sistema calcula el monto de alquiler (tarifa base registrada por la cantidad de días registrada), el monto del seguro náutico (tarifa de seguro por el número de pasajeros registrado) y el monto del depósito de garantía, y devuelve al Sistema de Reservas y Operaciones el desglose completo junto con el valor total de la reserva.

### Casos Extremos (Edge Cases)

- **¿Qué sucede cuando el Sistema de Reservas y Operaciones solicita el valor calculado de una reserva para la cual no existe información previamente registrada por "Brindar información de reserva" (por ejemplo, porque dicho registro nunca se completó o falló)?**
  El sistema no intenta calcular ningún monto parcial. De acuerdo con RF-009, responde al Sistema de Reservas y Operaciones con un error controlado que indica que no existe información registrada para el identificador de reserva recibido.

- **¿Qué sucede si el Sistema de Reservas y Operaciones solicita el valor calculado de la misma reserva más de una vez?**
  Dado que el cálculo (RF-003 a RF-006) se realiza siempre a partir de la misma información registrada para esa reserva, el resultado es determinístico: el sistema recupera nuevamente dicha información, recalcula el monto de alquiler, el seguro náutico, el depósito de garantía y el valor total, y devuelve el mismo desglose. La actualización de la información interna (RF-008) es igualmente idempotente, ya que sobrescribe los montos calculados con valores equivalentes.

- **¿Cómo debe comportarse el sistema si el monto del depósito de garantía configurado en los parámetros financieros globales no está disponible o no ha sido definido al momento del cálculo?**
  Al no poder completar RF-005, el sistema considera la información necesaria para el cálculo como incompleta. Conforme a RNF-003, no debe continuar el cálculo con un valor asumido o parcial; en su lugar, registra el fallo y responde al Sistema de Reservas y Operaciones con un error controlado, de la misma forma que ante una reserva sin información registrada.

- **¿Qué sucede si la información registrada para la reserva está incompleta (por ejemplo, sin tarifa base) debido a una falla previa durante "Brindar información de reserva"?**
  Se trata igualmente de un caso de información incompleta cubierto por RNF-003: el sistema no debe ejecutar un cálculo parcial (por ejemplo, omitiendo el monto de alquiler). Debe tratar la solicitud como no calculable y responder con un error controlado al Sistema de Reservas y Operaciones, equivalente al tratamiento definido en RF-009.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **RF-001**: El sistema DEBE recibir del Sistema de Reservas y Operaciones una solicitud del valor calculado de una reserva específica, identificada mediante su identificador de reserva.
- **RF-002**: El sistema DEBE recuperar la información previamente registrada para dicha reserva (tarifa base vigente, cantidad de días y número de pasajeros), registrada mediante "Brindar información de reserva".
- **RF-003**: El sistema DEBE calcular el monto de alquiler multiplicando la tarifa base registrada por la cantidad de días registrada.
- **RF-004**: El sistema DEBE calcular el monto del seguro náutico multiplicando la tarifa de seguro establecida por el número de pasajeros registrado.
- **RF-005**: El sistema DEBE obtener el monto del depósito de garantía aplicable a la reserva a partir de los parámetros financieros globales configurados en el sistema.
- **RF-006**: El sistema DEBE calcular el valor total de la reserva como la suma del monto de alquiler, el monto del seguro náutico y el monto del depósito de garantía.
- **RF-007**: El sistema DEBE devolver al Sistema de Reservas y Operaciones el desglose completo del valor de la reserva, compuesto por el monto de alquiler, el monto del seguro náutico, el monto del depósito de garantía y el valor total.
- **RF-008**: El sistema DEBE actualizar la información interna previamente registrada de la reserva, incorporando los montos calculados, dejándola disponible para "Procesar cobro".
- **RF-009**: El sistema DEBE responder con un error controlado al Sistema de Reservas y Operaciones cuando se solicita el valor calculado de una reserva para la cual no existe información previamente registrada.

### Requisitos No Funcionales

- **RNF-001**: El sistema DEBE utilizar DTOs para la comunicación con el Sistema de Reservas y Operaciones, tanto para recibir la solicitud como para devolver el desglose del valor.
- **RNF-002**: El sistema DEBE utilizar `BigDecimal` para el monto de alquiler, el monto del seguro náutico, el monto del depósito de garantía y el valor total.
- **RNF-003**: El sistema DEBE implementar un manejo de errores robusto para los casos en que la información previamente registrada de la reserva no exista o esté incompleta, evitando cálculos parciales o inconsistentes.

### Entidades Clave

- **InformaciónDeReserva (Entidad, definida en SPEC 3)**: En este caso de uso es consultada y actualizada, incorporando el monto de alquiler, el monto del seguro náutico, el monto del depósito de garantía y el valor total calculados, de manera que quede disponible para "Procesar cobro".
- **SolicitudValorReserva (DTO)**: Información recibida desde el Sistema de Reservas y Operaciones para esta operación. Contiene el identificador de la reserva cuyo valor calculado se solicita.
- **DesgloseValorReserva (DTO)**: Resultado que el sistema devuelve al Sistema de Reservas y Operaciones. Contiene el identificador de la reserva, el monto de alquiler, el monto del seguro náutico, el monto del depósito de garantía y el valor total. No representa una entidad persistida, sino el valor de retorno de esta operación.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **CE-001**: Precisión Financiera, "100% de los valores totales calculados corresponden exactamente a la suma del monto de alquiler, el monto del seguro náutico y el monto del depósito de garantía, con cero (0) errores de redondeo detectados en pruebas automatizadas, validando la correcta implementación de BigDecimal".
- **CE-002**: Cumplimiento Arquitectónico, "100% de los cálculos de valor de reserva se basan exclusivamente en la información previamente registrada por 'Brindar información de reserva', sin recalcular ni volver a consultar la tarifa dinámica ni la tarifa base".
- **CE-003**: Resiliencia del Sistema, "100% de las solicitudes de valor calculado para reservas sin información previamente registrada son respondidas mediante un error controlado, sin provocar fallos ni respuestas inconsistentes al Sistema de Reservas y Operaciones".
- **CE-004**: Continuidad del Flujo Financiero, "100% de las reservas con valor calculado exitosamente quedan con el desglose disponible internamente, permitiendo que 'Procesar cobro' se ejecute sin necesidad de recalcular los montos".
