# Sistema de Liquidación, Seguros y Dispersión de Fondos — SEA-SHARE

> Este documento describe, desde su propia perspectiva, el componente financiero de SEA-SHARE encargado de tarifar, cobrar, garantizar, liquidar y dispersar los fondos generados por los alquileres de embarcaciones. En adelante se referirá a sí mismo como **"el sistema"**.

---

## 1. Descripción General

El sistema es responsable de traducir cada operación turística de SEA-SHARE en un movimiento financiero controlado. Sus funciones principales son:

- **Calcular el valor de un alquiler** combinando la tarifa base de la embarcación (dinámica según temporada alta o fin de semana; la temporada alta se determina automáticamente por calendario) con la duración solicitada, el seguro náutico por pasajero y el depósito de garantía aplicable.
- **Procesar el cobro** al arrendatario una vez la reserva ha sido iniciada, en coordinación con una pasarela de pago externa.
- **Registrar y aplicar financieramente el depósito de garantía**, consumiendo desde el Módulo 2 el estado de la disputa cuando se detectan daños al regreso de la embarcación.
- **Solicitar la liquidación de fondos** entre la plataforma (comisión) y el propietario, una vez descontados comisión y seguro, dejando el resultado externo sujeto a las capacidades de la pasarela.  
- **Calcular estimaciones** para que el usuario pueda visualizar estimaciones o valores aproximados de cada reserva. 
-**Aplicar las penalidades o reembolsos** que correspondan según la ventana de cancelación en la que se encuentre la reserva.
- **Exponer información financiera** (balances, ingresos, registros históricos) a los roles interesados: Propietarios y Administración Financiera.
- **Configurar los parámetros financieros globales** de la plataforma (porcentaje de comisión, tarifas de seguro y porcentajes de tarifa dinámica).
- **Colaborar con los otros sistemas de SEA-SHARE**: consume datos de la embarcación provistos por el sistema de Gestión de Flota, y responde a las solicitudes de estimación, confirmación de pago y estado financiero que le hace el sistema de Reservas y Operaciones.

### Actores que interactúan con el sistema

| Actor | Rol respecto al sistema |
| :--- | :--- |
| **Arrendatario** | Origina el cobro de su reserva. |
| **Propietario** | Recibe la dispersión de fondos y consulta sus ingresos/registros. |
| **Administrador Financiero** | Supervisa balances y configura parámetros globales. La resolución operativa y administrativa de disputas pertenece al Módulo 2. |
| **Pasarela de Pago** | Sistema externo que ejecuta técnicamente cobros, reembolsos y dispersiones. |
| **Sistema de Reservas y Operaciones** | Solicita estimaciones, confirma pagos, consulta el estado financiero de una reserva y su valor calculado; también entrega al sistema el propietario y la capacidad máxima de pasajeros de la embarcación (obtenidos previamente por Reservas desde el Sistema de Gestión de Flota). |
| **Sistema de Gestión de Flota** | Provee la tarifa base de una embarcación necesaria para calcular la tarifa dinámica de una reserva. El sistema (Módulo 3) no lo consulta directamente para obtener el propietario o la capacidad máxima de pasajeros de una embarcación; ese dato llega siempre a través del Sistema de Reservas y Operaciones. |

### Supuestos de trazabilidad
Dado que algunas reglas de negocio no tienen un caso de uso dedicado en el diagrama, se asumen las siguientes correspondencias:
- El **seguro náutico** se calcula como parte de  "Solicitar el valor calculado de la reserva", no como un caso de uso independiente.
- La **penalidad por cancelación** (regla del sistema de Reservas) se resuelve mediante la combinación de "Solicitar el valor calculado de la reserva", "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler", según el estado de la reserva. La operación financiera depende de si la reserva pasa a `cancelado flexiblemente`, `cancelado moderadamente`, `cancelado tardíamente` o `cancelado por anfitrión`.

- **En procesar cobro** se utiliza, cuando la pasarela lo admite, un flujo de autorización y captura: la autorización representa la retención lógica del importe del alquiler y de la garantía, y la captura se solicita cuando el negocio determina el importe definitivo. Esto no constituye un escrow jurídico ni supone que toda pasarela pueda mantener una autorización hasta el final de una reserva. La autorización tiene una vigencia limitada; si expira, el sistema registra el hecho y ejecuta el flujo de recuperación definido, sin asumir que los fondos siguen disponibles.
- La pasarela es un ejecutor externo: puede aceptar una solicitud sin haber aprobado el pago, reportar estados posteriores, rechazar, cancelar o dejar una operación en proceso. El sistema conserva los estados externos y no marca una operación como completada hasta recibir una confirmación válida.
- Las solicitudes de cobro, captura, reembolso y liquidación son idempotentes. Cada operación conserva una identidad propia, su referencia externa y la relación con el cobro original para evitar duplicaciones durante reintentos o notificaciones repetidas.
- Los **informes financieros** se consultan por un período quincenal, mensual o trimestral. No aceptan rangos libres de fechas.
- El **deposito de garantia** es manejado por el modulo 2 (reservas y operaciones) y funciona de la siguiente manera: Al terminarse una reserva, es decir, cuando el propietario recibe el barco este confirma que la reserva ya se completo y a partir de ese momento tiene un lapso de 24 horas para verificar que no hubo ningun daño. esto se traduce a que cuando una reserva pasa a estado "completada" automaticamente deberá entregarse el dinero correspondiente al propietario pero EL DEPOSITO DE GARANTIA SE DEBERÁ RETENER POR 24 HORAS. 

El depósito se calcula como el 10% de la tarifa base diaria de la embarcación y el valor calculado se congela para la reserva. Si pasan 24 horas desde `completada` sin que exista una disputa, un evento automático solicita el reembolso total del depósito al Arrendatario. Si existe una disputa, el depósito permanece retenido hasta recibir su resolución.
---

## 2. Flujo Paso a Paso de una Reserva (Participación del Sistema)

El sistema no inicia una reserva por sí mismo: reacciona a las solicitudes del sistema de Reservas y Operaciones (modulo 2) y a las acciones del arrendatario. Su participación en el ciclo de vida completo de una reserva es la siguiente:

1. **Solicitud de estimación.** El sistema de Reservas pide una estimación para una posible reserva. El sistema ejecuta *"Solicitar estimación para reserva"*, que incluye obligatoriamente *"Brindar tarifa base"*, la cual a su vez consulta al sistema de Gestión de Flota el tipo y categoría de la embarcación para aplicar la tarifa dinámica correspondiente (temporada alta, determinada automáticamente por calendario, o fin de semana). Dicha estimación es meramente INFORMATIVA y puede verse reflejada para una unica embarcacion (con una fecha y cierto numero de pasajeros) o puede verse reflejada como una lista de estimaciones (para la pantalla principal en donde se veran las embarcaciones) con una fecha y numero de pasajeros  predeterminados (1 dia y 1 pasajero)

2. **Inicio formal de la reserva.** El arrendatario selecciona la embarcación y oprime "Reservar". La reserva pasa al estado "Iniciada" (ver sea-share.md, Módulo 2, 2.1) y comienza el TTL de 15 minutos; desde este momento la embarcación deja de listarse como disponible.

3. **Consolidación de la información de reserva.** Cuando el sistema de Reservas necesita mostrarle al usuario los detalles completos, el sistema ejecuta *"Brindar información de reserva"*, enviando el identificador de la reserva, la embarcación, la cantidad de días, el número de pasajeros, y el propietario y la capacidad máxima de pasajeros de la embarcación (datos que el sistema de Reservas obtuvo previamente del Sistema de Gestión de Flota). El sistema financiero, a su vez, incluye *"Brindar tarifa base"* para obtener la tarifa vigente y registra internamente toda esta información — sin consultar directamente al Sistema de Gestión de Flota — de manera que quede disponible para que *"Solicitar el valor calculado de la reserva"* entregue posteriormente el desglose de precio que el arrendatario verá antes de confirmar. Esto ocurre dentro del estado "Iniciada".

4. **Cálculo del valor total.** Una vez el arrendatario decide reservar, el sistema de Reservas solicita el valor definitivo mediante *"Solicitar el valor calculado de la reserva"*: tarifa base diaria × duración + seguro náutico por pasajero + depósito de garantía. El depósito corresponde al 10% de la tarifa base diaria de la embarcación y no depende de la duración ni del daño reportado. Esto ocurre dentro del estado "Iniciada", antes de que el arrendatario oprima "Confirmar pago".

5. **Confirmar pago y cobro.** El arrendatario oprime "Confirmar pago"; la reserva pasa al estado "Pendiente", lo cual habilita al sistema a ejecutar *"Procesar cobro"*. El TTL sigue corriendo desde que comenzó en "Iniciada" — esta transición no lo reinicia.

6. **Confirmación hacia Reservas.** El sistema de Reservas solicita *"Solicitar confirmación de pago"* para conocer el estado vigente de la autorización o del cobro. Solo un estado aprobado y verificable permite avanzar la reserva a "Reservado". Si el TTL expira (iniciado en "Iniciada") sin una confirmación de pago exitosa, la reserva vuelve a "Disponible" del lado de Reservas, sin asumir que el pago falló: cualquier autorización pendiente debe cancelarse o quedar bajo conciliación.

7. **Actualización de estado de la reserva** El sistema de reservas informa el estado de la reserva mediante *"Brindar el estado de la reserva"*, para que el sistema de finanzas dispare cierto caso de uso dependiendo del estado actual de una reserva.

8. **Cancelación (si aplica).** Si el arrendatario cancela, el sistema de Reservas recalcula el escenario mediante *"Solicitar el valor calculado de la reserva"* y, según la ventana de tiempo:
   - **Cancelacion flexible: >72h:** el sistema solicita la liberación o el reembolso del 100% según el estado del cobro y la política explícita de costos transaccionales.
   - **Cancelacion moderada: 72h–24h:** el sistema reembolsa el 50% y dispersa el 50% restante como compensación al propietario vía *"Liquidar fondos de alquiler"*.
   - **Cancelacion tardia: <24h / No-Show:** el sistema no reembolsa; dispersa el 100% como compensación al propietario.
   - **Cancelación por anfitrión:** el sistema reembolsa el 100% del valor pagado al Arrendatario.

9. **Finalización y disputa de garantía.** El sistema de Reservas informa la finalización de la reserva con el único estado `completada`. Finanzas liquida el alquiler y el seguro, pero deja pendiente el depósito. El Módulo 2 crea y gestiona la disputa, concede al propietario una ventana de 24 horas para reportar daños y luego informa a Finanzas únicamente el estado de disputa. Si el estado es `RECHAZADO`, Finanzas solicita el reembolso total al Arrendatario; si el estado es `COMPLETADO`, solicita la liquidación total al Propietario.

10. **Liquidación final.** Superadas las etapas anteriores (una reserva ya fue completada), el sistema calcula y solicita la captura y/o liquidación correspondiente vía la Pasarela de Pago: valor bruto menos comisión de la plataforma y menos el seguro, respetando la matriz de liquidación. La solicitud puede quedar pendiente o fallar; solo su confirmación externa permite informar que los fondos fueron efectivamente liquidados.

11. **Supervisión continua.** En cualquier momento, el **Propietario** y el **Administrador Financiero** pueden *"Consultar registros financieros"* y *"Consultar informe financiero"*. El alcance del Propietario se limita a sus reservas y el del Administrador cubre toda la plataforma. El Administrador también puede *"Configurar parámetros financieros globales"*.

---

## 3. Casos de Uso del Sistema

### 3.1 Estimación y Cálculo de Tarifas

#### Definición de temporada alta (Regla de Negocio)

La **temporada alta** comprende los periodos del año con mayor flujo de viajeros, precios más altos en vuelos y alojamiento y mayor ocupación en los destinos. Para el contexto colombiano de SEA-SHARE, el sistema deriva automáticamente la vigencia de la temporada alta aplicando la siguiente regla de calendario a cada año evaluado:

- **Fin de año:** desde el 15 de noviembre hasta el 15 de enero del año siguiente.
- **Mitad de año:** desde el 1 de junio y al 30 de julio (vacaciones escolares y fiestas locales).
- **Semana Santa:** los días santos de marzo o abril. (se deben calcular mediante el Algoritmo de Meeus/Jones/Butcher)
- **Semana de receso:** Del 5 al 12 de octubre.


La vigencia (fechas de inicio y fin) de la temporada alta **no se configura manualmente**: "Brindar tarifa base" determina si una fecha pertenece a temporada alta evaluando si cae dentro de alguna de estas ventanas. El Administrador Financiero únicamente configura el **porcentaje de incremento** de tarifa dinámica aplicable durante dicha condición, mediante "Configurar parámetros financieros globales". Los cambios derivados del calendario afectan únicamente los cálculos posteriores y nunca los valores ya aplicados a reservas existentes.

#### Brindar tarifa base
- **Actores:** Sistema de Gestión de Flota (provee datos de la embarcación); invocado internamente (`<<include>>`) por "Solicitar estimación para reserva" y "Brindar información de reserva".
- **Flujo:** El sistema recibe el tipo/categoría de la embarcación desde Gestión de Flota y aplica la tarifa dinámica vigente (temporada alta —determinada automáticamente por la regla de calendario—, fin de semana, etc.) para obtener la tarifa base por unidad de tiempo.
- **Regla de negocio asociada:** Tarifas Dinámicas (3.1).

#### Solicitar estimación para reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Ante una intención de reserva aún no confirmada (el arrendatario esta observando opciones), Reservas pide al sistema una estimación preliminar. El sistema incluye "Brindar tarifa base" y devuelve un estimado sin bloquear ningún activo.
- **Regla de negocio asociada:** Tarifas Dinámicas (3.1).

#### Brindar información de reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Cuando el arrendatario ingresa los datos de una reserva específica, el sistema de Reservas entrega dicha información (identificador de la reserva, embarcación, número de pasajeros, cantidad de días, propietario y capacidad máxima de pasajeros de la embarcación — estos dos últimos ya obtenidos por Reservas desde el Sistema de Gestión de Flota) y el sistema financiero incluye (`<<include>>`) a "Brindar tarifa base" para obtener la tarifa vigente de la embarcación, sin consultar directamente al Sistema de Gestión de Flota para el propietario o la capacidad máxima, de manera que quede registrada internamente toda la información necesaria para los cálculos de otro caso de uso.
- **Regla de negocio asociada:** Tarifas Dinámicas (3.1); Matriz de Liquidación (3.2).

#### Solicitar el valor calculado de la reserva
- **Actores:** Sistema de Reservas y Operaciones.
   - **Flujo:** Reservas solicita el valor definitivo a cobrar (tarifa base diaria × duración + seguro + depósito de garantía). El depósito se calcula como el 10% de la tarifa base diaria de la embarcación y el valor calculado se congela para la reserva.
- **Reglas de negocio asociadas:** Matriz de Liquidación (3.2); Lógica de Cancelaciones y Reembolsos (Módulo 2, 2.2).

---

### 3.2 Cobro y Confirmación de Pago

#### Procesar cobro
- **Actores:** Arrendatario (origina la solicitud); Pasarela de Pago (ejecuta la transacción).
- **Flujo:** El arrendatario confirma el pago (transición de la reserva a estado "Pendiente") dentro del TTL de 15 minutos que comenzó cuando la reserva pasó a estado "Iniciada" (Módulo 2, 2.1). El sistema puede solicitar a la pasarela una autorización por el valor calculado; la aceptación técnica de la solicitud no equivale a aprobación del pago.
- **Regla de negocio asociada:** Bloqueo Temporal (Módulo 2, 2.1); Depósito de Garantía y Seguro Náutico (3.1).

#### Solicitar confirmación de pago
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Reservas consulta al sistema si el cobro fue confirmado, para decidir si la reserva avanza a "Reservado" o si, al expirar el TTL (iniciado en "Iniciada") sin una confirmación de pago exitosa, el activo vuelve a "Disponible".
- **Regla de negocio asociada:** Bloqueo Temporal (Módulo 2, 2.1).

#### Brindar el estado de la reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** El sistema de Reservas informa el estado de una reserva (disponible, iniciada, pendiente, reservado, en navegación, completada, cancelado flexiblemente, cancelado moderadamente, cancelado tardíamente o cancelado por anfitrión). El estado "iniciada" es puramente informativo para Finanzas (no dispara ninguna acción financiera), al igual que "pendiente" y "reservado". El estado `completada` dispara la liquidación del alquiler y el seguro, sin incluir el depósito de garantía. El depósito queda pendiente hasta que Finanzas reciba una resolución de disputa; si no existe disputa después de 24 horas, un evento automático solicita su reembolso total.
- **Regla de negocio asociada:** Transversal a Matriz de Liquidación (3.2) y Cancelaciones (Módulo 2, 2.2).

---

### 3.3 Depósito de Garantía y Disputas

#### Brindar información de disputa de garantía
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** El Módulo 2 crea y gestiona la disputa después de la finalización de la reserva. El propietario dispone de 24 horas para reportar daños. El Módulo 2 informa a Finanzas únicamente el identificador de la reserva, el identificador de la disputa, el estado `PENDIENTE`, `RECHAZADO` o `COMPLETADO` y una versión o clave idempotente. No envía montos ni ejecuta operaciones financieras. `RECHAZADO` implica devolver el depósito al Arrendatario y `COMPLETADO` implica liquidarlo al Propietario. Si no existe una disputa al cumplirse las 24 horas, un evento automático solicita el reembolso total del depósito al Arrendatario; no se informa un origen ni un motivo operativo adicional.
- **Regla de negocio asociada:** Depósito de Garantía (3.1).

#### Reembolsar dinero a arrendatario
- **Actores:** Pasarela de Pago (ejecuta la devolución); disparado por cancelaciones aplicables o por "Brindar información de disputa de garantía" cuando el resultado sea liberar el depósito.
- **Flujo:** El sistema recupera de sus registros el monto correspondiente y ordena la devolución a través de la Pasarela de Pago. Para la garantía, la devolución es siempre del 100% del depósito capturado; no existe retención parcial.
- **Reglas de negocio asociadas:** Depósito de Garantía (3.1); Lógica de Cancelaciones y Reembolsos (Módulo 2, 2.2).

---

### 3.4 Dispersión de Fondos

#### Liquidar fondos de alquiler
- **Actores:** Pasarela de Pago (ejecuta la operación); beneficia al Propietario.
- **Flujo:** Al recibir `completada`, el sistema calcula el pago estándar como *Valor Bruto − Comisión de la Plataforma − Seguro* y lo solicita sin incluir el depósito. Cuando "Brindar información de disputa de garantía" informa `COMPLETADO`, el sistema recupera internamente el depósito fijo y solicita su liquidación total al Propietario. Esta liquidación es una consecuencia de negocio y puede ejecutarse mediante una operación consolidada o relacionada soportada por la integración, no necesariamente como una transferencia directa.
- **Regla de negocio asociada:** Matriz de Liquidación — Pago al Propietario y Penalidad por Cancelación (3.2).

---

### 3.5 Administración y Consultas Financieras

#### Configurar parámetros financieros globales
- **Actores:** Administrador Financiero.
- **Flujo:** El administrador define o ajusta el porcentaje de comisión de la plataforma, la tarifa del seguro náutico por pasajero y los porcentajes de incremento de tarifa dinámica por fin de semana y temporada alta. El depósito no se configura aquí: se determina automáticamente como el 10% de la tarifa base diaria de la embarcación. La vigencia de la temporada alta se determina automáticamente conforme a la regla de calendario.
- **Regla de negocio asociada:** Matriz de Liquidación — Comisión Plataforma (3.2); Reglas de Cobro (3.1).

#### Consultar registros financieros
- **Actores:** Administrador Financiero; Propietario.
- **Flujo:** Ambos roles pueden revisar cada registro de cobro, reembolso y dispersión de forma individual, con filtros y paginación. El Propietario ve únicamente registros relacionados con sus reservas; el Administrador ve los registros de toda la plataforma. El resultado incluye la referencia externa cuando exista.
- **Regla de negocio asociada:** Transversal a la Matriz de Liquidación (3.2).

#### Consultar informe financiero
- **Actores:** Administrador Financiero; Propietario.
- **Flujo:** Ambos consultan datos agregados de registros financieros por períodos quincenales, mensuales o trimestrales. El Propietario ve sus reservas y el Administrador ve toda la plataforma. El informe prioriza el neto generado por cobros, reembolsos y dispersiones confirmados; no es un balance global ni incluye costos operativos o depósitos pendientes sin registro financiero.
- **Regla de negocio asociada:** Transversal a la Matriz de Liquidación (3.2).

---

## 4. Resumen de Trazabilidad (Reglas de Negocio → Casos de Uso)

| Regla de negocio (sea-share.md) | Caso(s) de uso del sistema |
| :--- | :--- |
| Tarifas Dinámicas | Brindar tarifa base, Solicitar estimación para reserva, Brindar información de reserva |
| Depósito de Garantía | Brindar información de disputa de garantía, Reembolsar dinero a arrendatario, Liquidar fondos de alquiler |
| Seguro Náutico | Solicitar el valor calculado de la reserva |
| Valor Alquiler Bruto | Solicitar el valor calculado de la reserva |
| Comisión Plataforma | Configurar parámetros financieros globales, Liquidar fondos de alquiler |
| Pago al Propietario | Liquidar fondos de alquiler, Consultar informe financiero |
| Penalidad por Cancelación | Solicitar el valor calculado de la reserva, Reembolsar dinero a arrendatario, Liquidar fondos de alquiler, Consultar informe financiero |
