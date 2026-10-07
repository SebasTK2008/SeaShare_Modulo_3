# Consistencias entre Módulo 2 y Módulo 3

> Contrato de consistencia entre el Módulo 2 (Reservas y Operaciones) y el Módulo 3 (Finanzas / "el sistema"). No es documentación general de SEA-SHARE: solo cubre lo que ambos módulos deben nombrar y entender igual. Fuentes: `sea-share.md` y `contexto-modulo3.md`.

## 1. Nombres y actores

| Concepto | Módulo 2 (`sea-share.md`) | Módulo 3 (`contexto-modulo3.md`) | Nombre que debe utilizarse |
| --- | --- | --- | --- |
| Arrendatario | "turistas" en la introducción general; "Arrendatario" en el detalle operativo (2.1, 2.2) | "Arrendatario" (único término, sin excepciones) | **Arrendatario** |
| Propietario | "Propietario" en la entidad Embarcación (Módulo 1); pero "**anfitrión**" en la regla de cancelación tardía (2.2: "compensación al anfitrión") | "Propietario" (único término, sin excepciones) | **Propietario** |
| Módulo 2 (como sistema) | "Módulo 2: Operación de Reservas, Tiempos y Cancelaciones" | "Sistema de Reservas y Operaciones" | Ambos son equivalentes; en interacciones con Finanzas usar **"Sistema de Reservas y Operaciones"** |
| Módulo 3 (como sistema) | "Módulo 3: Liquidación, Seguros y Dispersión de Fondos" | Se autodenomina **"el sistema"** | Equivalentes; en todo contenido de SPEC/Finanzas usar **"el sistema"**, nunca "Módulo 3" |

**Actores no compartidos** (existen solo del lado de Finanzas y no requieren nombre común con Módulo 2): Administrador Financiero, Pasarela de Pago. Módulo 2 no los menciona porque no interactúa directamente con ellos.

---

## 2. Reserva

Una reserva es el acuerdo entre Arrendatario y Propietario para el alquiler temporal de una embarcación. Módulo 2 es dueño del ciclo de vida y los tiempos de la reserva (TTL, ventanas de cancelación, umbral de No-Show); Módulo 3 es dueño de los cálculos y movimientos financieros asociados a cada transición de estado que se lo solicite.

- **Cuándo comienza**: la reserva como entidad nace cuando el Arrendatario oprime "Reservar" → estado **Iniciada**. Antes de eso (pantalla de exploración) solo existe una *intención de reserva* sin ID, cubierta por "Solicitar estimación para reserva" en modo lote.
- **Información que necesita Módulo 3 de Módulo 2**: identificador de reserva, embarcación, fecha de inicio, fecha de fin, pasajeros, propietario y capacidad máxima ("Brindar información de reserva"); el estado vigente de la reserva ("Brindar el estado de la reserva"); el estado de la disputa de garantía ("Brindar información de disputa de garantía").
- **Información que necesita Módulo 2 de Módulo 3**: estimación preliminar ("Solicitar estimación para reserva"), desglose y valor total definitivo ("Solicitar el valor calculado de la reserva"), y el resultado del cobro ("Solicitar confirmación de pago").
- **Qué ocurre al reservar**: → Iniciada; arranca el TTL de 15 min; la embarcación deja de listarse como disponible.
- **Qué ocurre al pagar**: el Arrendatario oprime "Confirmar pago" → Pendiente; esto habilita a Finanzas a ejecutar "Procesar cobro"; el TTL sigue corriendo desde "Iniciada" (no se reinicia).
- **Qué ocurre cuando el pago se confirma**: Módulo 2 consulta "Solicitar confirmación de pago"; solo un resultado aprobado y verificable avanza la reserva a Reservado.
- **Qué ocurre si expira**: si el TTL vence (iniciado en "Iniciada") sin confirmación exitosa, la reserva vuelve a Disponible; cualquier autorización pendiente en la pasarela debe cancelarse o quedar en conciliación (no se asume que el pago falló).
- **Qué ocurre si se cancela**: según la ventana de tiempo (ver tabla de estados).
- **Qué ocurre al finalizar**: el Propietario marca la reserva como Completada tras la devolución → Finanzas liquida alquiler + seguro de inmediato; el depósito de garantía queda pendiente hasta el resultado de la disputa de garantía.

No se detectó contradicción entre ambos documentos sobre el origen del TTL (ambos coinciden en que nace en "Iniciada" y no se reinicia en "Pendiente"); `contexto-modulo3.md` simplemente añade el detalle operación-a-operación que `sea-share.md` no desarrolla.

### Flujo de la reserva

```text
Disponible
   ↓ (Arrendatario oprime "Reservar")
Iniciada  ───────────────┐  (arranca TTL 15 min)
   ↓ (Arrendatario confirma pago)      │
Pendiente ── (mismo TTL, no se reinicia) │
   ↓ (Finanzas confirma pago)           │  TTL vence sin pago confirmado
Reservado                               ↓
   ↓ (check-in)                    Disponible
En Navegación
   ↓ (Propietario marca devuelta)
Completada
   ↓ (resultado de disputa de garantía — ver §4)
[depósito → Arrendatario]  o  [depósito → Propietario]
```

Rama de cancelación (únicamente puede iniciarse desde el estado Reservado. Ningún otro estado es válido para cancelar, y no se puede cancelar una vez la reserva está En Navegación):
```text
… → Cancelado Flexiblemente (>72h)          → Reembolso 100%
… → Cancelado Moderadamente (72h–24h)       → Reembolso 50% + dispersión 50% al Propietario
… → Cancelado Tardíamente / No-Show (<24h)  → Dispersión 100% al Propietario, sin reembolso
… → Cancelado por Anfitrión                  → Reembolso 100% al Arrendatario
```

### Estados de la reserva

| Estado | Qué significa | Qué ocurre para llegar a este estado |
| --- | --- | --- |
| Disponible | La embarcación no está asociada a ninguna reserva. | Estado inicial, o retorno tras vencer el TTL sin pago confirmado. |
| Iniciada | Arranca el bloqueo temporal (TTL) de 15 min; la embarcación deja de listarse. | El Arrendatario oprime "Reservar". |
| Pendiente | Continúa el mismo TTL (no se reinicia); habilita "Procesar cobro". | El Arrendatario oprime "Confirmar pago". |
| Reservado | Pago confirmado; reserva exitosa; aún sin uso. | Finanzas confirma el pago dentro del TTL. |
| En Navegación | Contrato activo; embarcación en uso. | Se realiza el check-in. |
| Completada | Alquiler y seguro se liquidan de inmediato; depósito queda pendiente. | El Propietario marca la reserva como completada tras la devolución. |
| Cancelado Flexiblemente | >72h de anticipación; reembolso 100% (menos costos transaccionales); sin dispersión. | El Arrendatario cancela con más de 72h de anticipación. |
| Cancelado Moderadamente | 72h–24h de anticipación; reembolso 50% + dispersión 50% al Propietario. | El Arrendatario cancela entre 72h y 24h de anticipación. |
| Cancelado Tardíamente / No-Show | <24h; sin reembolso; dispersión 100% al Propietario. | El Arrendatario cancela con <24h, o no se presenta 30 min después de la hora pactada. |
| Cancelado por Anfitrión | Reembolso 100% al Arrendatario; sin dispersión. | El anfitrión no puede asegurar la flota a tiempo. |

Ambos documentos reconocen exactamente los mismos 9 estados (contando las 3 variantes de cancelación por separado); no hay estados presentes en un módulo y ausentes en el otro.

---

## 3. Garantía (Depósito)

- **Qué es**: 10% de la tarifa base diaria de la embarcación.
- **Momento en la reserva**: se calcula y se congela en "Solicitar el valor calculado de la reserva" (antes de confirmar pago); se cobra junto con alquiler y seguro como **un único monto** en "Procesar cobro" (sin operación separada en la pasarela, aunque Módulo 3 conserva el desglose internamente); se retiene tras "Completada" hasta que se resuelve la disputa de garantía.
- **Qué módulo interviene**: Módulo 2 crea y gestiona la disputa (otorga la ventana para reportar daños y decide el resultado operativo); Módulo 3 solo ejecuta la consecuencia financiera (reembolso o liquidación total) a partir del estado recibido, usando montos que ya tiene registrados internamente — nunca recibe montos de Módulo 2.
- **Información que Módulo 2 debe enviar a Módulo 3**: únicamente identificador de reserva, identificador de disputa, estado (`PENDIENTE`/`RECHAZADO`/`COMPLETADO`), clave idempotente y fecha y hora de la transición de estado. Nunca motivos, montos ni instrucciones de pago.
- **Qué ocurre después de resolver**: el depósito se entrega **completo** al Arrendatario o **completo** al Propietario; no existe retención parcial en ningún caso.

---

## 4. Disputa de garantía

- **Qué se considera**: el proceso mediante el cual se decide si el depósito se devuelve al Arrendatario o se liquida al Propietario, según si se detectaron daños menores al regreso de la embarcación.
- **Cuándo se genera**: tras "Completada", dentro de la ventana que Módulo 2 concede al Propietario para reportar daños (`contexto-modulo3.md` fija esta ventana en 24 horas). La duración y vigencia de la disputa las administra exclusivamente el Módulo 2; Módulo 3 no ejecuta temporizadores ni cron jobs. Al vencer la ventana de 24 horas sin reclamo, el Módulo 2 informa el estado `RECHAZADO` a Módulo 3.
- **Quién interviene**: el Propietario (reporta o no reporta daños) y el Módulo 2 (crea y gestiona la disputa, incluida la evaluación de procedencia del reclamo).
- **Qué módulo la gestiona**: Módulo 2 gestiona la disputa por completo; Módulo 3 únicamente consume su resultado final y ejecuta la operación financiera correspondiente.
- **Estados** (definidos solo en `contexto-modulo3.md`; `sea-share.md` no usa el término "disputa" ni estos nombres):
  - `PENDIENTE`: existe o continúa en revisión; sin acción financiera.
  - `RECHAZADO`: el reclamo no procede (incluye ausencia de reclamo al vencer la ventana); depósito 100% al Arrendatario.
  - `COMPLETADO`: el reclamo procede; depósito 100% al Propietario.
- **Qué ocurre al resolverse**: Módulo 2 notifica el estado final a Módulo 3, que ejecuta el reembolso o la liquidación total con sus propios registros.
- **Efecto sobre reserva/garantía**: cierra el ciclo financiero del depósito de esa reserva. Mientras no exista `RECHAZADO` o `COMPLETADO`, el depósito se considera "pendiente de resolución" y puede seguir contabilizándose en reportes financieros sucesivos.

---

## 5. Interacciones entre módulos

| Acción / evento | Módulo que inicia | Módulo que recibe | Información relevante |
| --- | --- | --- | --- |
| Solicitar estimación para reserva | Módulo 2 | Módulo 3 | Lista de IDs de embarcación (fechas/pasajeros opcionales). |
| Brindar información de reserva | Módulo 2 | Módulo 3 | ID reserva, ID embarcación, fecha de inicio, fecha de fin, pasajeros, propietario, capacidad máxima. Unidireccional: Módulo 3 no responde. |
| Solicitar el valor calculado de la reserva | Módulo 2 | Módulo 3 | ID reserva → desglose (alquiler, seguro, depósito, total). |
| Procesar cobro | Módulo 2 (reserva en "Pendiente") | Módulo 3 | ID reserva, token/referencia segura de pago. |
| Solicitar confirmación de pago | Módulo 2 | Módulo 3 | ID reserva → estado del cobro. |
| Brindar el estado de la reserva | Módulo 2 | Módulo 3 | ID reserva, uno de los 10 estados, fecha y hora del estado. Unidireccional: Módulo 3 no responde ni notifica fallos a Módulo 2. |
| Brindar información de disputa de garantía | Módulo 2 | Módulo 3 | ID reserva, ID disputa, estado (`PENDIENTE`/`RECHAZADO`/`COMPLETADO`), clave idempotente y fecha y hora de transición. Unidireccional, sin motivos ni montos. |

---

