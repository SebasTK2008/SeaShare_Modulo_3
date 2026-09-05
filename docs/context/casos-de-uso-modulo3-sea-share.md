# Casos de Uso — Módulo 3: Liquidación, Seguros y Dispersión de Fondos
### SEA-SHARE: Plataforma P2P de Alquiler Náutico
### (Versión corregida, alineada con el diagrama UML revisado)

## 1. Introducción

El Módulo 3 es el motor financiero de SEA-SHARE: no crea dinero ni decide precios por sí mismo, sino que **traduce eventos operativos** (una cotización, un pago, una cancelación, un vencimiento de tiempo) **en movimientos de caja** — entradas, retenciones y salidas de dinero entre el Arrendatario, la plataforma y el Anfitrión.

Este módulo no trabaja solo. Depende de:
- **Módulo 1 (Gestión de Flota):** entrega la tarifa base de la embarcación.
- **Módulo 2 (Reservas y Operaciones):** entrega el estado de la reserva y el tipo de cancelación aplicable, que determinan qué regla financiera se ejecuta. Esta relación se documenta como una **dependencia entre módulos**, no como un actor — Módulo 2 no aparece en el diagrama de casos de uso como actor UML.

A continuación, cada caso de uso se describe agrupado según la **etapa del flujo de caja** a la que pertenece, no en el orden del diagrama, para que se entienda el recorrido del dinero de principio a fin.


---

## 2. Actores

| Actor | Tipo | Rol en el flujo de caja |
| :--- | :--- | :--- |
| **Arrendatario** | Humano (turista) | Origen del dinero: paga el alquiler, el seguro y el depósito. |
| **Propietario / Anfitrión** | Humano | Destino final del dinero: recibe el pago neto por sus embarcaciones. |
| **Pasarela de Pago** | Sistema externo | Ejecuta técnicamente el cobro, la confirmación y la dispersión de fondos. El sistema **le solicita** el cobro y la confirmación; ella **notifica** el resultado. |
| **Admin Financiero** | Humano (staff interno) | Configura reglas financieras, supervisa y resuelve disputas. No mueve dinero de un cobro puntual, pero define los parámetros bajo los que se mueve. |
| **Módulo 1 (Gestión de Flota)** | Sistema interno (dependencia) | Provee la tarifa base usada para calcular el valor bruto. |
| **Módulo 2 (Reservas y Operaciones)** | Sistema interno (dependencia) *[CORREGIDO]* | Provee el estado de la reserva y el tipo de cancelación que disparan la liquidación correspondiente. **Ya no se modela como actor** que dispara casos de uso directamente, sino como dependencia funcional entre módulos. |

---

## 3. Etapa 1 — Cotización y datos previos al cobro
*(Todavía no hay movimiento de dinero; se prepara la información para poder cobrarlo correctamente).*

### Solicitar cotización para reserva
- **Iniciado por:** Arrendatario (a través del flujo de reserva del Módulo 2).
- **Incluye:** Brindar tarifa base.
- **Descripción:** Antes de bloquear una reserva, se le pide al Módulo 3 cuánto costaría el alquiler.
- **Rol en el flujo de caja:** Ninguno todavía — es el paso que **calcula** el futuro valor bruto (Tarifa base × Duración), pero no mueve dinero.

### Brindar tarifa base
- **Provisto por:** Módulo 1 (Gestión de Flota).
- **Incluye:** Aplicar tarifa dinámica.
- **Descripción:** Módulo 1 entrega el precio base de la embarcación (que puede variar según tarifas dinámicas por temporada o fin de semana).
- **Rol en el flujo de caja:** Es el insumo numérico que alimenta todo cálculo posterior de valor bruto.


### Solicitar información final de reserva / Brindar información de reserva
- **Descripción:** Una vez que la reserva está por confirmarse, se solicita y se entrega el detalle final (fechas, embarcación, pasajeros) necesario para calcular el monto exacto a cobrar (incluyendo el seguro náutico por pasajero).
- **Rol en el flujo de caja:** Define los parámetros exactos del cobro que se ejecutará a continuación.

### Brindar el estado de la reserva
- **Provisto por:** Módulo 2 (dependencia, no actor).
- **Descripción:** Informa en qué estado se encuentra la reserva (En TTL, Reservado, En Navegación, Cancelado, etc.).
- **Rol en el flujo de caja:** No mueve dinero, pero es la señal que determina **si corresponde cobrar, liquidar o cancelar** en los pasos siguientes.

---

## 4. Etapa 2 — Cobro (entrada de dinero / *cash-in*)
*(El dinero sale del bolsillo del Arrendatario y entra al sistema).*

### Solicitar confirmación de pago
- **Actor:** Pasarela de Pago.
- **Extendido por:** Expirar TTL de reserva *(condicional — solo si el Temporizador dispara el vencimiento antes de recibir confirmación)*.
- **Descripción:** El sistema consulta a la pasarela si el pago del Arrendatario fue efectivamente aprobado.
- **Rol en el flujo de caja:** Es el "semáforo" que confirma que el dinero realmente entró antes de continuar el proceso (evita liquidar fondos que nunca se cobraron).


### Procesar cobro
- **Actor:** Pasarela de Pago.
- **Incluye:** Retener Depósito de Garantía, Cobrar seguro náutico.
- **Descripción:** Ejecuta el cargo real al Arrendatario por el valor del alquiler, el seguro náutico y, si aplica, el depósito de garantía.
- **Rol en el flujo de caja:** **Es la entrada de dinero (cash-in) propiamente dicha.** Todo lo que sigue en el módulo depende de que este caso de uso se ejecute con éxito.


### Consultar valor de un alquiler

* **Actor:** Propietario.
* **Incluye:** Brindar información de reserva.
* **Descripción:** Permite al Propietario, una vez finalizada una reserva, consultar dos cifras sobre esa reserva puntual: el **cobro total** realizado al arrendatario (Valor Alquiler Bruto) y la **ganancia neta** que le corresponde a él después de descontar la comisión de la plataforma y el seguro.
* **Rol en el flujo de caja:** Es una consulta informativa (no mueve dinero); le da al Propietario visibilidad sobre cuánto se cobró en total y cuánto de ese monto es efectivamente suyo.

---

## 5. Etapa 3 — Liquidación y dispersión (salida de dinero hacia el Anfitrión)
*(El dinero cobrado se reparte entre la plataforma y el Propietario).*

### Liquidar fondos de alquiler
- **Incluye:** Dispersar fondos al anfitrión.
- **Descripción:** Una vez confirmado el cobro, este caso de uso aplica la matriz de liquidación del Módulo 3: descuenta la comisión de la plataforma y el seguro del valor bruto.
- **Rol en el flujo de caja:** Es el paso de **cálculo de reparto** — convierte "dinero cobrado" en "dinero de la plataforma" + "dinero del propietario".

### Dispersar fondos al anfitrión
- **Actor:** Pasarela de Pago.
- **Incluido por:** Liquidar fondos de alquiler.
- **Extendido por:** Ejecutar liquidación por cancelación *(condicional — ver nota en la Etapa 4)*.
- **Descripción:** Transfiere efectivamente el monto neto (Valor Bruto − Comisión − Seguro) a la cuenta del Propietario.
- **Rol en el flujo de caja:** **Es la salida de dinero (cash-out)** hacia el Anfitrión; cierra el ciclo normal de una reserva sin incidentes.

---

## 6. Etapa 4 — Cancelaciones y reembolsos (reversión del flujo)
*(El dinero ya cobrado se redistribuye de forma distinta a la normal, según qué tan tarde se cancele).*

### Brindar tipo de cancelación
- **Provisto por:** Módulo 2 (dependencia, no actor).
- **Descripción:** Informa si la cancelación es Flexible (>72h), Moderada (72h–24h) o Tardía/No-Show (<24h).
- **Rol en el flujo de caja:** Es el dato clave que decide **qué porcentaje se reembolsa y cuál se retiene**, según la política definida en el Módulo 2.

### Ejecutar liquidación por cancelación
- **Incluye:** Brindar tipo de cancelación.
- **Extiende a:** Procesar reembolso a arrendatario, Dispersar fondos al anfitrión. *[CORREGIDO]*
- **Descripción:** Aplica la penalidad o el reembolso correspondiente al tipo de cancelación informado (100% de reembolso, 50% de penalidad, o 100% de cobro para el anfitrión).
- **Rol en el flujo de caja:** Es el equivalente a "Liquidar fondos de alquiler" pero para el escenario de cancelación: decide cuánto dinero se devuelve y cuánto se queda en la plataforma/anfitrión.



### Dispersar fondos a arrendatario
- **Actor:** Pasarela de Pago.
- **Extiende a:** Ejecutar liquidación por cancelación y liquidar fondos de alquiler.
- **Descripción:** Ejecuta la devolución efectiva de dinero al Arrendatario cuando la cancelación así lo determina (total o parcial, según la ventana de cancelación) y tambien devolucion de garantia.
- **Rol en el flujo de caja:** Es una **salida de dinero excepcional**, en dirección contraria al flujo normal (vuelve al Arrendatario en lugar de ir al Propietario).



---

## 7. Etapa 5 — Depósito de garantía (dinero retenido temporalmente)
*(Dinero que no es ingreso de nadie todavía: está "congelado" a la espera de una decisión).*

### Retener Depósito de Garantía
- **Incluido por:** Procesar cobro.
- **Descripción:** Bloquea temporalmente el monto de garantía cobrado al Arrendatario, para cubrir posibles daños detectados al devolver la embarcación. Si el viaje concluye sin incidentes, este monto se libera de vuelta al Arrendatario.
- **Rol en el flujo de caja:** No es ingreso ni egreso definitivo — es una **retención**, un estado intermedio de la caja.

### Resolver disputa de garantía
- **Actor:** Admin Financiero.
- **Incluye:** Consultar estado de garantía.
- **Descripción:** Decide, ante un reclamo, si el depósito retenido se libera al Arrendatario o se cobra (total o parcialmente) para compensar al Propietario.
- **Rol en el flujo de caja:** Convierte el dinero retenido en un **ingreso para el propietario** (si hay daño comprobado) o en una **devolución al arrendatario** (si no lo hay).

### Consultar estado de garantía
- **Actor:** Propietario (también relevante para el Arrendatario).
- **Incluido por:** Resolver disputa de garantía.
- **Descripción:** Permite consultar si el depósito de una reserva sigue retenido, fue liberado o fue cobrado.
- **Rol en el flujo de caja:** Es una consulta informativa; no mueve dinero.

---

## 8. Etapa 6 — Administración y trazabilidad (sin movimiento directo de dinero)
*(Casos de uso de configuración y consulta que sostienen todo lo anterior, pero no representan un movimiento de caja en sí mismos).*

| Caso de uso | Actor | Función |
| :--- | :--- | :--- |
| **Configurar parámetros financieros globales** | Admin Financiero | Define reglas generales (% de comisión, tarifas de seguro, reglas de depósito, tarifa dinámica) que luego usan los demás casos de uso. |
| **Consultar registros financieros** | Admin Financiero | Auditoría/histórico de todos los movimientos de caja del sistema. |
| **Consultar balance financiero** | Admin Financiero | Vista agregada de ingresos brutos, comisiones, egresos por dispersión/reembolso y retenciones pendientes, en un periodo determinado (ver definición exacta en el glosario). |
| **Consultar ingresos** | Propietario | Permite al Anfitrión ver cuánto ha recibido o tiene pendiente de recibir. |

---

## 9. Resumen visual del recorrido del dinero

```
Arrendatario                Plataforma (Módulo 3)                Propietario
     |                              |                                  |
     |--Procesar cobro------------->|  (cash-in: alquiler + seguro     |
     |                              |   náutico + depósito)            |
     |                              |--Liquidar fondos de alquiler     |
     |                              |   (separa comisión y seguro)     |
     |                              |--Dispersar fondos al anfitrión-->|  (cash-out normal)
     |                              |
     |<--Procesar reembolso---------|  (cash-out excepcional,
     |    a arrendatario                según tipo de cancelación)
     |
     |==Retener Depósito de Garantía=|  (retenido; se libera o se
     |   (estado intermedio)         |   cobra tras resolver disputa)
     |
     [Temporizador] --Expirar TTL-->|  (si no hay confirmación de pago
                                        en 15 min, no hay cash-in;
                                        la reserva vuelve a "Disponible")
```


