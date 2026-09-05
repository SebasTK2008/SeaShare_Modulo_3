# Glosario de Términos — SEA-SHARE
### Plataforma P2P de Alquiler Náutico

Este glosario reúne los conceptos clave usados en los tres módulos (Flota, Reservas y Liquidación) para que puedan interpretarse de forma consistente entre equipos.

---

## Actores y roles

| Término | Definición |
| :--- | :--- |
| **Anfitrión / Propietario** | Usuario dueño de una o más embarcaciones registradas en la plataforma. Recibe el pago neto de cada alquiler. |
| **Arrendatario / Turista** | Usuario que reserva y paga por el uso temporal de una embarcación. |
| **Admin Financiero** | Rol interno del staff de SEA-SHARE encargado de configurar parámetros financieros globales y resolver disputas (por ejemplo, de depósitos de garantía). |
| **Pasarela de Pago** | Sistema externo (actor) que ejecuta técnicamente los cobros, las confirmaciones de pago y las dispersiones de fondos. |
| **Módulo 1 (Gestión de Flota y Activos P2P)** | Componente que administra el inventario de embarcaciones: datos, categorización, ubicación y estado del activo. |
| **Módulo 2 (Operación de Reservas, Tiempos y Cancelaciones)** | Componente que gestiona el ciclo de vida de una reserva: bloqueos temporales, tolerancia de no-show y reglas de cancelación. |
| **Módulo 3 (Liquidación, Seguros y Dispersión de Fondos)** | Componente financiero que calcula y ejecuta los cobros, las liquidaciones y las dispersiones de dinero. |

---

## Embarcación y sus estados

**Embarcación (Activo):** entidad central del Módulo 1. Se identifica por UUID, nombre y matrícula legal; se categoriza por tipo (lancha, yate, catamarán) y capacidad; incluye ubicación (puerto de atraque) y amenidades (capitán, combustible).

**Estados del Activo** (ciclo de vida de la embarcación, Módulo 1):

| Estado | Significado |
| :--- | :--- |
| **Disponible** | La embarcación puede ser reservada. |
| **Reservado** | Bloqueada temporalmente mientras se confirma un pago. |
| **En Navegación** | Contrato activo; la embarcación está fuera del puerto con el arrendatario. |
| **En Mantenimiento / Limpieza** | Inhabilitada por reparaciones o adecuación; no disponible para alquiler. |

> Nota: el documento fuente define estos estados a nivel del **activo (embarcación)**, no como un campo separado de "estado de reserva". En la práctica, ambos están sincronizados: cuando una reserva pasa a estar bloqueada, confirmada o en curso, el estado de la embarcación cambia en consecuencia.

---

## Reserva

**Reserva:** solicitud de un Arrendatario para usar una embarcación durante un periodo determinado. Pasa, informalmente, por las siguientes fases dentro del sistema:

1. **Cotización:** se solicita el precio estimado (tarifa base × duración) antes de bloquear el activo.
2. **Bloqueo temporal (TTL):** al iniciarse la reserva, la embarcación queda en estado "Reservado" por un máximo de **15 minutos**. Si el pago no se confirma en ese lapso, vuelve a "Disponible".
3. **Confirmación de pago:** la Pasarela de Pago valida el cobro; si es exitoso, la reserva queda confirmada.
4. **En Navegación:** la reserva está activa y en curso.
5. **Cierre / Liquidación:** al finalizar, se liquidan los fondos y se dispersa el pago neto al Propietario.
6. **Cancelación / No-Show (alternativa a los pasos 3–5):** la reserva se cancela antes o durante la ejecución, aplicándose la regla de reembolso/penalidad correspondiente.

**TTL (Time-To-Live):** ventana de **15 minutos** durante la cual una reserva permanece bloqueada esperando confirmación de pago, antes de liberarse automáticamente.

**Ventana de Tolerancia:** margen de **30 minutos** después de la hora pactada dentro del cual el Arrendatario puede presentarse; superado ese tiempo, el sistema permite declarar un "No-Show".

**No-Show:** situación en la que el Arrendatario no se presenta dentro de la ventana de tolerancia. Se trata financieramente igual que una cancelación tardía (se cobra el 100% al arrendatario como compensación al anfitrión).

---

## Cancelaciones y reembolsos

| Tipo de cancelación | Ventana | Efecto económico |
| :--- | :--- | :--- |
| **Flexible** | Más de 72 horas antes | Reembolso del 100% (menos costos transaccionales). |
| **Moderada** | Entre 72 y 24 horas antes | Penalidad del 50% del valor del alquiler. |
| **Tardía / No-Show** | Menos de 24 horas antes | Se cobra el 100% como compensación al anfitrión. |

---

## Términos financieros (Módulo 3)

| Término | Definición |
| :--- | :--- |
| **Valor Alquiler Bruto** | Ingreso total pagado por el turista, calculado como Tarifa base × Duración. |
| **Tarifa Dinámica** | Precio ajustado según temporada o días de fin de semana; se define en el Módulo 1. |
| **Comisión de Plataforma** | Porcentaje del Valor Bruto que retiene SEA-SHARE como ingreso neto de la empresa. |
| **Seguro Náutico** | Tarifa fija por pasajero destinada a cubrir accidentes durante el alquiler. |
| **Depósito de Garantía** | Monto retenido temporalmente al Arrendatario para cubrir posibles daños menores detectados al devolver la embarcación. Se libera o se cobra tras resolver una eventual disputa. |
| **Pago al Propietario (Neto)** | Monto final dispersado al dueño de la embarcación: Valor Bruto − Comisión − Seguro. |
| **Penalidad por Cancelación** | Porcentaje retenido/cobrado al Arrendatario cuando cancela fuera de la ventana flexible, dispersado al Propietario según la regla del Módulo 2. |
| **Liquidación** | Proceso de calcular y separar el dinero cobrado entre comisión de la plataforma, seguro y pago neto al propietario. |
| **Dispersión de Fondos** | Transferencia efectiva del dinero (pago neto, reembolso o cobro de penalidad) hacia la cuenta final correspondiente (Propietario o Arrendatario). |
| **Cotización** | Estimación del costo de un alquiler antes de confirmar la reserva, basada en la tarifa base vigente. |

---

## Relación entre módulos

- **Módulo 1 → Módulo 3:** entrega la tarifa base usada para calcular el Valor Alquiler Bruto.
- **Módulo 2 → Módulo 3:** entrega el estado de la reserva y el tipo de cancelación, que determinan si corresponde liquidar, reembolsar o cobrar una penalidad.
- **Módulo 3 → Pasarela de Pago:** ejecuta el cobro real al Arrendatario y la dispersión real de fondos al Propietario.
