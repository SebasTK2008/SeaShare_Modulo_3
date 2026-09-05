# SEA-SHARE — Glosario de Términos Unificado

> Objetivo: que los 3 módulos (Gestión de Flota, Reservas y Operaciones, Liquidación y Dispersión de Fondos) usen **el mismo vocabulario** para los mismos conceptos. Un término = un significado, en toda la documentación, diagramas y código.

---

## 1. Entidades y actores

| Término oficial | Definición | ❌ No usar como sinónimo |
| :--- | :--- | :--- |
| **Embarcación** | Cualquier vehículo náutico registrado en la plataforma (lancha, yate, catamarán, etc.), sin importar el tipo. | "Barco", "yate", "lancha" como término genérico — usar solo cuando se hable de la categoría específica. |
| **Anfitrión / Propietario** | Usuario dueño de una o más embarcaciones que las ofrece en alquiler. Ambos términos son intercambiables en este proyecto, pero se recomienda fijar uno solo para el código (sugerencia: **Propietario**, ya que es el que usa el diagrama de casos de uso). | "Dueño", "arrendador" |
| **Arrendatario** | Usuario turista que alquila una embarcación para uso recreativo/turístico. | "Cliente", "usuario final", "turista" (usar solo en texto narrativo, no en documentación técnica) |
| **Admin Financiero** | Rol administrativo que configura parámetros financieros globales y supervisa el balance de la plataforma. No opera reservas directamente. | "Administrador", "admin" a secas (ambiguo con otros posibles roles admin) |
| **Pasarela de pago** | Sistema externo (ej. pasarela tipo Stripe/PayU) que procesa cobros y devuelve confirmaciones de pago. | "Gateway", "procesador de pagos" |
| **Puerto de atraque** | Ubicación física (con coordenadas GPS) donde está amarrada la embarcación cuando está disponible. | "Marina", "muelle" (usar solo si se requiere distinguir tipos de infraestructura) |

---

## 2. Estados de una reserva

La **Reserva** es la entidad central que vincula a un Arrendatario con una Embarcación durante una ventana de tiempo determinada, y que atraviesa los siguientes estados (ciclo de vida oficial a usar en los 3 módulos):

| Estado | Definición | Disparado por | Transiciones posibles hacia |
| :--- | :--- | :--- | :--- |
| **Disponible** | La embarcación no tiene ninguna reserva activa sobre ella; puede ser cotizada y reservada por cualquier Arrendatario. | Estado inicial, o retorno tras expiración de TTL / cancelación completa. | En TTL |
| **En TTL** | La reserva fue iniciada pero está en la ventana de **Time-To-Live de 15 minutos**, esperando confirmación de pago. Si el pago no se confirma en ese lapso, la reserva regresa automáticamente a "Disponible". | Caso de uso "Procesar cobro" | Reservado (si el pago se confirma) / Disponible (si expira el TTL) |
| **Reservado** | El pago fue confirmado y el depósito de garantía retenido. La embarcación está bloqueada para ese Arrendatario en las fechas pactadas, pero aún no ha zarpado. | Confirmación exitosa de pago | En Navegación / Cancelado |
| **En Navegación** | El contrato está activo y la embarcación está fuera del puerto, en uso por el Arrendatario. | Inicio efectivo del alquiler (llegada del Arrendatario dentro de la ventana de tolerancia) | Disponible (al finalizar el alquiler y liquidar fondos) |
| **Cancelado** | La reserva fue anulada, ya sea por el Arrendatario, el Propietario, o por un No-Show (inasistencia tras 30 minutos de tolerancia). Dispara el cálculo de reembolso/penalidad según la ventana de cancelación (Módulo 2.2). | Acción explícita de cancelación o vencimiento de la ventana de tolerancia sin presentarse | Disponible (tras liquidar la penalidad/reembolso correspondiente) |

> ⚠️ Nota de coherencia: en `sea-share.md` el estado "En Mantenimiento/Limpieza" aplica a la **Embarcación** (Módulo 1), no a la **Reserva** (Módulo 2). Son dos ciclos de vida distintos y no deben confundirse: el estado de la Reserva describe el vínculo Arrendatario–Embarcación; el estado de la Embarcación describe la disponibilidad general del activo físico.

---

## 3. Términos financieros clave

### Alquiler bruto
Es el **monto total que paga el Arrendatario** por el servicio, antes de cualquier descuento o reparto. Se calcula como:

```
Alquiler Bruto = Tarifa base × Duración (+ ajustes por tarifa dinámica, si aplica)
```

No incluye todavía la resta de comisión ni de seguro — es el valor "de cara al turista".

### Alquiler neto
Es el **monto que efectivamente recibe el Propietario** después de que la plataforma descuenta su comisión y el costo del seguro náutico. Se calcula como:

```
Alquiler Neto (Pago al Propietario) = Alquiler Bruto − Comisión Plataforma − Seguro Náutico
```

En otras palabras: **Bruto** = lo que paga el turista. **Neto** = lo que recibe el anfitrión. La diferencia entre ambos es la comisión de la plataforma más el seguro.

### Comisión Plataforma
Porcentaje del Alquiler Bruto que la plataforma retiene como ingreso propio por intermediar la transacción. Es un **ingreso neto para SEA-SHARE**, no para el Propietario.

### Depósito de Garantía
Monto retenido temporalmente (dentro del cobro ya procesado, no es un cobro adicional) para cubrir posibles daños menores detectados al devolver la embarcación. Se libera al Arrendatario si no hay disputa, o se reparte según resolución del Admin Financiero si la hay.

### Seguro Náutico
Tarifa fija por pasajero, cobrada dentro del Alquiler Bruto, destinada a cubrir accidentes durante la navegación. Es un costo que se descuenta antes de calcular el Alquiler Neto (no es ingreso de la plataforma ni del propietario, sino de la cobertura de seguro).

### Penalidad por Cancelación
Monto que se retiene o cobra al Arrendatario cuando cancela fuera de la ventana flexible (>72h), según las reglas:
- **Flexible (>72h):** 0% de penalidad — reembolso 100%.
- **Moderada (72h–24h):** 50% de penalidad sobre el valor del alquiler.
- **Tardía / No-Show (<24h):** 100% de penalidad, que se dispersa íntegramente al Propietario como compensación.

### Balance Financiero
Es el **resumen consolidado de ingresos y egresos de la plataforma en un periodo de tiempo determinado** (por ejemplo: un día, una semana, un mes). No es el saldo de una sola reserva, sino la fotografía agregada de todos los movimientos. Debe incluir, como mínimo:

| Componente | Naturaleza | Ejemplo de origen |
| :--- | :--- | :--- |
| Ingresos brutos totales | Entrada | Suma de todos los "Procesar cobro" del periodo |
| Comisión de plataforma acumulada | Ingreso neto propio | Suma de comisiones retenidas |
| Egresos por dispersión a Propietarios | Salida | Suma de "Dispersar fondos al anfitrión" |
| Egresos por reembolsos a Arrendatarios | Salida | Suma de "Procesar reembolso a arrendatario" |
| Depósitos de garantía retenidos (no liberados) | Retención (pasivo, no ingreso ni egreso definitivo) | Suma de "Retener Depósito de Garantía" aún abiertos |
| **Balance neto del periodo** | Resultado | Ingresos brutos − Egresos totales − Retenciones aún pendientes |

> En otras palabras: cuando se hable de "consultar balance financiero", siempre debe entenderse como **cuántos ingresos y egresos hubo en un rango de fechas específico**, nunca como el saldo de una sola transacción.

### Tarifa Dinámica
Ajuste al alza o a la baja de la Tarifa Base según temporada o día de la semana (fines de semana). Se aplica **antes** de calcular el Alquiler Bruto.

### Ingreso vs. Egreso (uso consistente en los 3 módulos)
- **Ingreso:** cualquier movimiento de dinero que entra a la plataforma (ej. Procesar cobro).
- **Egreso:** cualquier movimiento de dinero que sale de la plataforma hacia un tercero (Propietario o Arrendatario) (ej. Dispersar fondos, reembolsos).
- **Retención:** dinero que ya ingresó pero no se ha reconocido como ingreso definitivo ni se ha dispersado (ej. Depósito de Garantía mientras no hay disputa resuelta).

---

## 4. Términos operativos (tiempos y ciclo de vida)

| Término | Definición |
| :--- | :--- |
| **TTL (Time-To-Live)** | Ventana de 15 minutos durante la cual una reserva iniciada permanece bloqueada esperando confirmación de pago, antes de liberarse automáticamente. |
| **Ventana de Tolerancia** | Periodo de 30 minutos tras la hora pactada de inicio del alquiler, dentro del cual el Arrendatario aún puede presentarse sin ser marcado como No-Show. |
| **No-Show** | Inasistencia del Arrendatario tras vencer la Ventana de Tolerancia. Se trata como Cancelación Tardía (<24h) para efectos de penalidad. |
| **Cotización** | Estimación de precio entregada al Arrendatario antes de comprometer una reserva. No implica bloqueo de la Embarcación. |

---

## 5. Reglas de coherencia entre módulos

1. **Módulo 1 (Flota)** habla del estado de la **Embarcación** (Disponible, Reservado, En Navegación, En Mantenimiento/Limpieza).
2. **Módulo 2 (Reservas)** habla del estado de la **Reserva** (Disponible, En TTL, Reservado, En Navegación, Cancelado) — son conceptualmente distintos aunque compartan nombres, y este glosario es la referencia única para evitar que un desarrollador confunda "Embarcación Reservada" con "Reserva en estado Reservado".
3. **Módulo 3 (Liquidación)** siempre debe referirse a los montos usando exactamente los términos: **Alquiler Bruto**, **Alquiler Neto**, **Comisión Plataforma**, **Depósito de Garantía**, **Seguro Náutico**, **Penalidad por Cancelación** y **Balance Financiero**, tal como se definen aquí — nunca usar sinónimos libres como "ganancia", "comisión de la app" o "dinero retenido" sin especificar a cuál de estos conceptos corresponde.
