# Sistema de Liquidación, Seguros y Dispersión de Fondos — SEA-SHARE

> Este documento describe, desde su propia perspectiva, el componente financiero de SEA-SHARE encargado de tarifar, cobrar, garantizar, liquidar y dispersar los fondos generados por los alquileres de embarcaciones. En adelante se referirá a sí mismo como **"el sistema"**.

---

## 1. Descripción General

El sistema es responsable de traducir cada operación turística de SEA-SHARE en un movimiento financiero controlado. Sus funciones principales son:

- **Calcular el valor de un alquiler** combinando la tarifa base de la embarcación (dinámica según temporada alta o fin de semana; la temporada alta se determina automáticamente por calendario) con la duración solicitada, el seguro náutico por pasajero y el depósito de garantía aplicable.
- **Procesar el cobro** al arrendatario una vez la reserva ha sido iniciada, en coordinación con una pasarela de pago externa.
- **Retener y arbitrar el depósito de garantía**, resolviendo disputas cuando se detectan daños al regreso de la embarcación.
- **Dispersar los fondos** entre la plataforma (comisión) y el propietario, una vez descontados comisión y seguro.  
- **Calcular estimaciones** para que el usuario pueda visualizar estimaciones o valores aproximados de cada reserva. 
-Aplicar las penalidades o reembolsos** que correspondan según la ventana de cancelación en la que se encuentre la reserva.
- **Exponer información financiera** (balances, ingresos, registros históricos) a los roles interesados: Propietarios y Administración Financiera.
- **Configurar los parámetros financieros globales** de la plataforma (porcentaje de comisión, tarifas de seguro, reglas de depósito, etc.).
- **Colaborar con los otros sistemas de SEA-SHARE**: consume datos de la embarcación provistos por el sistema de Gestión de Flota, y responde a las solicitudes de estimación, confirmación de pago y estado financiero que le hace el sistema de Reservas y Operaciones.

### Actores que interactúan con el sistema

| Actor | Rol respecto al sistema |
| :--- | :--- |
| **Arrendatario** | Origina el cobro de su reserva. |
| **Propietario** | Recibe la dispersión de fondos y consulta sus ingresos/registros. |
| **Administrador Financiero** | Supervisa balances, resuelve disputas de garantía y configura parámetros globales. |
| **Pasarela de Pago** | Sistema externo que ejecuta técnicamente cobros, reembolsos y dispersiones. |
| **Sistema de Reservas y Operaciones** | Solicita estimaciones, confirma pagos, consulta el estado financiero de una reserva y su valor calculado. |
| **Sistema de Gestión de Flota** | Provee la tarifa base de una  embarcación necesarios para calcular la tarifa dinamica de una reserva. |

### Supuestos de trazabilidad
Dado que algunas reglas de negocio no tienen un caso de uso dedicado en el diagrama, se asumen las siguientes correspondencias:
- El **seguro náutico** se calcula como parte de  "Solicitar el valor calculado de la reserva", no como un caso de uso independiente.
- La **penalidad por cancelación** (regla del sistema de Reservas) se resuelve mediante la combinación de "Solicitar el valor calculado de la reserva", "Reembolsar dinero a arrendatario" y "Liquidar fondos de alquiler", según la ventana de tiempo en la que se solicitó la cancelación.

- **En procesar cobro**  se tiene en cuenta que cuando el usuario hace efectivo el pago este se retiene en un escow en la pasarela de pago y se mantiene hasta que se termine la reserva y la disputa de la garantia..
- Los **balances financieros** estaran dados por un periodo quincenal, mensual o trimestral. 
- 
---

## 2. Flujo Paso a Paso de una Reserva (Participación del Sistema)

El sistema no inicia una reserva por sí mismo: reacciona a las solicitudes del sistema de Reservas y Operaciones y a las acciones del arrendatario. Su participación en el ciclo de vida completo de una reserva es la siguiente:

1. **Solicitud de estimación.** El sistema de Reservas pide una estimación para una posible reserva. El sistema ejecuta *"Solicitar estimación para reserva"*, que incluye obligatoriamente *"Brindar tarifa base"*, la cual a su vez consulta al sistema de Gestión de Flota el tipo y categoría de la embarcación para aplicar la tarifa dinámica correspondiente (temporada alta, determinada automáticamente por calendario, o fin de semana). Dicha estimación es meramente INFORMATIVA y puede verse reflejada para una unica embarcacion (con una fecha y cierto numero de pasajeros) o puede verse reflejada como una lista de estimaciones (para la pantalla principal en donde se veran las embarcaciones) con una fecha y numero de pasajeros  predeterminados (1 dia y 1 pasajero)

2. **Consolidación de la información de reserva.** Cuando el sistema de Reservas necesita mostrarle al usuario los detalles completos, el sistema ejecuta *"Brindar información de reserva"* (que también incluye "Brindar tarifa base"), entregando el desglose de precio que el arrendatario verá antes de confirmar.

3. **Cálculo del valor total.** Una vez el arrendatario decide reservar, el sistema de Reservas solicita el valor definitivo mediante *"Solicitar el valor calculado de la reserva"*: tarifa base × duración + seguro náutico por pasajero + depósito de garantía.

4. **Bloqueo temporal (estado de espera (pendiente)) y cobro.** Mientras la reserva está bloqueada por 15 minutos en el sistema de Reservas, el **Arrendatario** dispara *"Procesar cobro"*, que el sistema ejecuta junto con la **Pasarela de Pago**.

5. **Confirmación hacia Reservas.** El sistema de Reservas solicita *"Solicitar confirmación de pago"| para saber si el cobro fue exitoso. Si no hay confirmación dentro del estado de espera (pendiente), la reserva vuelve a "Disponible" del lado de Reservas; si se confirma, el sistema retiene internamente el depósito de garantía y el monto del seguro como parte del cobro ya procesado.

6. **Actualización de estado de la reserva** El sistema de reservas informa el estado de la reserva mediante *"Brindar el estado de la reserva"*, para que el sistema de finanzas dispare cierto caso de uso dependiendo del estado actual de una reserva.

7. **Cancelación (si aplica).** Si el arrendatario cancela, el sistema de Reservas recalcula el escenario mediante *"Solicitar el valor calculado de la reserva"* y, según la ventana de tiempo:
   - **Cancelacion flexible: >72h:** el sistema ejecuta *"Reembolsar dinero a arrendatario"* por el 100% (menos costos transaccionales).
   - **Cancelacion moderada: 72h–24h:** el sistema reembolsa el 50% y dispersa el 50% restante como compensación al propietario vía *"Liquidar fondos de alquiler"*.
   - **Cancelacion tardia: <24h / No-Show:** el sistema no reembolsa; dispersa el 100% como compensación al propietario.

8. **Regreso de la embarcación y depósito de garantía.** Si al finalizar la navegación se detectan daños, el **Administrador Financiero** ejecuta *"Resolver disputa de garantía"*. Según el resultado:
   - Si no procede el reclamo, el sistema extiende hacia *"Reembolsar dinero a arrendatario"* liberando el depósito.
   - Si procede, el sistema retiene el monto correspondiente y lo dispersa al propietario junto con el resto del pago.

9. **Liquidación final.** Superadas las etapas anteriores, el sistema ejecuta *"Liquidar fondos de alquiler"* vía la Pasarela de Pago: paga al propietario el valor bruto menos comisión de la plataforma y menos el seguro, respetando la matriz de liquidación.

10. **Supervisión continua.** En cualquier momento, el **Propietario** puede *"Consultar ingresos"* y *"Consultar registros financieros"*; el **Administrador Financiero** puede *"Consultar balance financiero"*, *"Consultar registros financieros"* y *"Configurar parámetros financieros globales"* (porcentaje de comisión, tarifa de seguro, reglas de depósito).

---

## 3. Casos de Uso del Sistema

### 3.1 Estimación y Cálculo de Tarifas

#### Definición de temporada alta (Regla de Negocio)

La **temporada alta** comprende los periodos del año con mayor flujo de viajeros, precios más altos en vuelos y alojamiento y mayor ocupación en los destinos. Para el contexto colombiano de SEA-SHARE, el sistema deriva automáticamente la vigencia de la temporada alta aplicando la siguiente regla de calendario a cada año evaluado:

- **Fin de año:** desde la segunda mitad de noviembre hasta mediados de enero del año siguiente.
- **Mitad de año:** los meses de junio y julio (vacaciones escolares y fiestas locales).
- **Semana Santa:** los días santos de marzo o abril.
- **Semana de receso:** la semana de descanso escolar en octubre.
- **Puentes festivos y fines de semana largos.**

La vigencia (fechas de inicio y fin) de la temporada alta **no se configura manualmente**: "Brindar tarifa base" determina si una fecha pertenece a temporada alta evaluando si cae dentro de alguna de estas ventanas. El Administrador Financiero únicamente configura el **porcentaje de incremento** de tarifa dinámica aplicable durante dicha condición, mediante "Configurar parámetros financieros globales". Los cambios derivados del calendario afectan únicamente los cálculos posteriores y nunca los valores ya aplicados a reservas existentes.

#### Brindar tarifa base
- **Actores:** Sistema de Gestión de Flota (provee datos de la embarcación); invocado internamente (`<<include>>`) por "Solicitar estimación para reserva" y "Brindar información de reserva".
- **Flujo:** El sistema recibe el tipo/categoría de la embarcación desde Gestión de Flota y aplica la tarifa dinámica vigente (temporada alta —determinada automáticamente por la regla de calendario—, fin de semana, etc.) para obtener la tarifa base por unidad de tiempo.
- **Regla de negocio asociada:** Tarifas Dinámicas (3.1).

#### Solicitar estimación para reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Ante una intención de reserva aún no confirmada, Reservas pide al sistema una estimación preliminar. El sistema incluye "Brindar tarifa base" y devuelve un estimado sin bloquear ningún activo.
- **Regla de negocio asociada:** Tarifas Dinámicas (3.1).

#### Brindar información de reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Cuando el arrendatario ingresa los datos  de una reserva específica, el sistema entrega el desglose completo de precio (incluyendo tarifa base vía `<<include>>`, numero de pasajeros y cantidad de dias  ).
- **Regla de negocio asociada:** Tarifas Dinámicas (3.1); Matriz de Liquidación (3.2).

#### Solicitar el valor calculado de la reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Reservas solicita el valor definitivo a cobrar (tarifa × duración + seguro + depósito de garantía), usado tanto para el cobro inicial como para recalcular reembolsos/penalidades en caso de cancelación teniendo en cuenta la tarifa dinamica.
- **Reglas de negocio asociadas:** Matriz de Liquidación (3.2); Lógica de Cancelaciones y Reembolsos (Módulo 2, 2.2).

---

### 3.2 Cobro y Confirmación de Pago

#### Procesar cobro
- **Actores:** Arrendatario (origina la solicitud); Pasarela de Pago (ejecuta la transacción).
- **Flujo:** El arrendatario confirma el pago dentro de la ventana de bloqueo temporal (estado de espera (pendiente)) de 15 minutos definida por Reservas). El sistema envía la orden de cobro a la Pasarela de Pago, reteniendo internamente el depósito de garantía y el seguro náutico como parte del monto cobrado.
- **Regla de negocio asociada:** Bloqueo Temporal (estado de espera (pendiente)) (Módulo 2, 2.1); Depósito de Garantía y Seguro Náutico (3.1).

#### Solicitar confirmación de pago
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** Reservas consulta al sistema si el cobro fue confirmado, para decidir si la reserva avanza de estado o si, al expirar el estado de espera (pendiente) sin confirmación, el activo vuelve a "Disponible".
- **Regla de negocio asociada:** Bloqueo Temporal estado de espera (pendiente) (Módulo 2, 2.1).

#### Brindar el estado de la reserva
- **Actores:** Sistema de Reservas y Operaciones.
- **Flujo:** El sistema de reservas informa el estado de una reserva (disponible, reservado, en navegación, pendiente,cancelado flexiblemente, cancelado tardiamente, cancelado moderadamente, completado con incidentes y completado sin incidentes) para que el sistema financiero mantenga sincronizado el ciclo de vida operativo de la reserva y la operacion del pago y dispersion de fondos.
cualquier tipo de estado cancelado dispara el caso de uso reembolsar dinero a arrendatario. El estado completado sin incidentes dispara el caso de uso dispersar fondos y reembolsar dinero. El estado completado con incidentes dispara el caso de uso resolver disputa.
- **Regla de negocio asociada:** Transversal a Matriz de Liquidación (3.2) y Cancelaciones (Módulo 2, 2.2).

---

### 3.3 Depósito de Garantía y Disputas

#### Resolver disputa de garantía
- **Actores:** Administrador Financiero.
- **Flujo:** Al detectarse posibles daños en la embarcación al finalizar la navegación, el Administrador Financiero evalúa la evidencia y determina si el depósito de garantía debe retenerse (total o parcialmente) o liberarse. Este caso de uso extiende (`<<extend>>`) hacia "Reembolsar dinero a arrendatario" cuando el resultado favorece al arrendatario.
- **Regla de negocio asociada:** Depósito de Garantía (3.1).

#### Reembolsar dinero a arrendatario
- **Actores:** Pasarela de Pago (ejecuta la devolución); disparado como extensión de "Resolver disputa de garantía" o como consecuencia de una cancelación dentro de la ventana flexible/moderada.
- **Flujo:** El sistema calcula el monto a devolver (100% del depósito si no hay daños; 100%, 50% o 0% del valor del alquiler según la ventana de cancelación) y ordena la devolución a través de la Pasarela de Pago.
- **Reglas de negocio asociadas:** Depósito de Garantía (3.1); Lógica de Cancelaciones y Reembolsos (Módulo 2, 2.2).

---

### 3.4 Dispersión de Fondos

#### Liquidar fondos de alquiler
- **Actores:** Pasarela de Pago (ejecuta la transferencia); beneficia al Propietario.
- **Flujo:** Una vez liquidada la reserva (o resuelta una penalidad por cancelación tardía/no-show), el sistema calcula el pago al propietario como *Valor Bruto − Comisión de la Plataforma − Seguro* y ordena la transferencia mediante la Pasarela de Pago. Las penalidades por cancelación tardía se dispersan íntegramente como compensación al propietario.
- **Regla de negocio asociada:** Matriz de Liquidación — Pago al Propietario y Penalidad por Cancelación (3.2).

---

### 3.5 Administración y Consultas Financieras

#### Configurar parámetros financieros globales
- **Actores:** Administrador Financiero.
- **Flujo:** El administrador define o ajusta los parámetros que rigen los cálculos del sistema: porcentaje de comisión de la plataforma, tarifa del seguro náutico por pasajero, reglas del depósito de garantía y porcentajes de incremento de tarifa dinámica por fin de semana y por temporada alta. La vigencia (fechas) de la temporada alta no se configura aquí: el sistema la determina automáticamente conforme a la regla de calendario definida en la sección 3.1.
- **Regla de negocio asociada:** Matriz de Liquidación — Comisión Plataforma (3.2); Reglas de Cobro (3.1).

#### Consultar registros financieros
- **Actores:** Administrador Financiero; Propietario.
- **Flujo:** Ambos roles pueden revisar el historial de transacciones (cobros, reembolsos, dispersiones, penalidades) asociadas a las reservas, con distintos niveles de alcance (el propietario ve solo sus propias embarcaciones; el administrador ve el histórico global).
- **Regla de negocio asociada:** Transversal a la Matriz de Liquidación (3.2).

#### Consultar balance financiero
- **Actores:** Administrador Financiero.
- **Flujo:** El administrador consulta el estado consolidado de las finanzas de la plataforma (fondos retenidos, comisiones acumuladas, depósitos en garantía pendientes de resolución).
- **Regla de negocio asociada:** Transversal a la Matriz de Liquidación (3.2).

#### Consultar ingresos
- **Actores:** Propietario.
- **Flujo:** El propietario revisa los ingresos generados por sus embarcaciones tras la dispersión de fondos, incluyendo penalidades recibidas por cancelaciones tardías.
- **Regla de negocio asociada:** Matriz de Liquidación — Pago al Propietario (3.2).

---

## 4. Resumen de Trazabilidad (Reglas de Negocio → Casos de Uso)

| Regla de negocio (sea-share.md) | Caso(s) de uso del sistema |
| :--- | :--- |
| Tarifas Dinámicas | Brindar tarifa base, Solicitar estimación para reserva, Brindar información de reserva |
| Depósito de Garantía | Resolver disputa de garantía, Reembolsar dinero a arrendatario |
| Seguro Náutico | Brindar tarifa base, Solicitar el valor calculado de la reserva *(implícito)* |
| Valor Alquiler Bruto | Solicitar el valor calculado de la reserva |
| Comisión Plataforma | Configurar parámetros financieros globales, Liquidar fondos de alquiler |
| Pago al Propietario | Liquidar fondos de alquiler, Consultar ingresos |
| Penalidad por Cancelación | Solicitar el valor calculado de la reserva, Reembolsar dinero a arrendatario, Liquidar fondos de alquiler |
