# Matriz de QA — Módulo 3 (Finanzas) · feature/QA-initializer

Plan de trazabilidad de QA. Cada fila ancla un Test Case (TC) a **un escenario de aceptación** del spec, su Historia de Usuario (HU) y los requisitos que verifica. Los TC se redactan con la plantilla [`../templates/test-template.md`](../templates/test-template.md).

**Definición de "verificado por develop"**: todos los TC de esta matriz pasan al 100% (PASS). Cualquier FAIL abre un defecto vía `bugfix/*` → `develop` → `release/*` y bloquea el cierre.

## Convenciones

- **Flujo**: autoría en `develop` → ejecución en `release/0.1.0` → defectos en `bugfix/*` (PR a `develop` → merge/cherry-pick a release).
- **Un TC por escenario**: `TC-UCxx-HUy-En-<slug>.md` en [`test-cases/`](test-cases/). `En` = número de escenario dentro de su HU.
- **Estados del TC**: `Pendiente` (aún sin TC) → `Draft` (TC redactado) → `In Process` (ejecutando) → `Approved` (PASS en release).
- **Numeración local**: los `RF-xxx` / `CE-xxx` son **locales a cada spec** (el `RF-003` de UC09 no es el `RF-003` de UC10). La fila ya incluye el prefijo de UC.
- **Origen del mapeo**: los specs no contienen una tabla explícita HU→RF; la columna "RF/CE cubierto" se derivó del contenido de los escenarios y de la *Prueba Independiente*. Cuando el TC cubre casos extremos en su `Section 2`, los RF/CE correspondientes también se listan en la fila.
- **No ejecutar en develop**: los TC se crean en `Draft`; la verificación (Pass/Fail) ocurre únicamente en `release/0.1.0`.

### Modos de ejecución de las pruebas de pago

Los pagos tienen **dos modos obligatorios y complementarios** (detalle en
[`pagos-api-real.md`](pagos-api-real.md)):

- **A · Simulado (gate de CI):** WireMock, simulador y Testcontainers. Es el que ejecutan la suite
  automática y los TC de esta matriz; no requiere credenciales ni Internet.
- **B · API real de Mercado Pago (gate de release):** cobro aprobado/rechazado, webhook `payment`
  real, reembolso parcial/total, idempotencia y ausencia de datos sensibles, con credenciales
  `TEST-*` en sandbox MCO. Es **obligatorio** para aprobar UC05/UC09 en `release/0.1.0`; no se
  sustituye por el simulador. UC10 no aplica porque usa un libro interno simulado (D-UC10-18).

### Datos de prueba (alias → UUID)

`reservation_id` es UUID v4 en el schema y en los contratos. Los TC usan estos alias; en payloads, `reservation_id=` y SQL se debe usar el UUID, nunca el alias.

| Alias | UUID v4 |
|---|---|
| `R-100` | `b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10` |
| `R-200` | `0f1e2d3c-4b5a-4c6d-8e7f-9a0b1c2d3e4f` |
| `R-300` | `1a2b3c4d-5e6f-4a7b-9c8d-0e1f2a3b4c5d` |
| `R-400` | `2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e` |
| `R-401` | `3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f` |
| `R-402` | `4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f8a` |

> Excepción intencional: en UC06 las pruebas de `400 VALIDATION_ERROR` usan `R-100` sin UUID porque validan precisamente el formato.

## Matriz de trazabilidad

| TC ID | Spec (UC) | HU | Escenario (nº + título) | Pri | RF/CE cubierto | Nombre del TC | Estado | Resultado | Defecto |
|---|---|---|---|:---:|---|---|:---:|:---:|---|
| TC-UC01-HU1-E1 | UC01 · Estimaciones para reserva | HU1 | E1 Cálculo delegado exitoso de múltiples estimaciones en lote | P1 | RF-001, RF-002, RF-005, RF-006, RF-007, RNF-001, RNF-002, RNF-003 · CE-002, CE-004, CE-005 | [TC-UC01-HU1-E1-calculo-lote.md](test-cases/TC-UC01-HU1-E1-calculo-lote.md) | Draft | — | — |
| TC-UC01-HU2-E1 | UC01 | HU2 | E1 Estimación delegada para una sola embarcación y aviso legal | P1 | RF-002, RF-003, RF-004, RF-005, RNF-003 · CE-002, CE-004 | [TC-UC01-HU2-E1-estimacion-individual.md](test-cases/TC-UC01-HU2-E1-estimacion-individual.md) | Draft | — | — |
| TC-UC01-HU3-E1 | UC01 | HU3 | E1 Flota devuelve la tarifa base en tiempo óptimo (<500 ms) | P2 | RF-005, RF-006, RNF-001, RNF-003 · CE-004 | [TC-UC01-HU3-E1-tarifa-base-optima.md](test-cases/TC-UC01-HU3-E1-tarifa-base-optima.md) | Draft | — | — |
| TC-UC02-HU1-E1 | UC02 · Tarifa base | HU1 | E1 Tarifa regular (sin condición dinámica vigente) | P1 | RF-001, RF-002, RF-004, RF-007, RNF-002, RNF-003 · CE-002, CE-003 | [TC-UC02-HU1-E1-tarifa-regular.md](test-cases/TC-UC02-HU1-E1-tarifa-regular.md) | Draft | — | — |
| TC-UC02-HU1-E2 | UC02 | HU1 | E2 Tarifa ajustada por fin de semana | P1 | RF-002, RF-004, RF-006, RNF-002, RNF-003 · CE-003 | [TC-UC02-HU1-E2-tarifa-fin-semana.md](test-cases/TC-UC02-HU1-E2-tarifa-fin-semana.md) | Draft | — | — |
| TC-UC02-HU1-E3 | UC02 | HU1 | E3 Tarifa ajustada por temporada alta | P1 | RF-002, RF-004, RF-006, RNF-003, RNF-004 · CE-003 | [TC-UC02-HU1-E3-tarifa-temporada-alta.md](test-cases/TC-UC02-HU1-E3-tarifa-temporada-alta.md) | Draft | — | — |
| TC-UC03-HU1-E1 | UC03 · Información de reserva | HU1 | E1 Registro exitoso de la información de una reserva | P1 | RF-001 a RF-007, RNF-003 · CE-001 a CE-004 | [TC-UC03-HU1-E1-registro-info-reserva.md](test-cases/TC-UC03-HU1-E1-registro-info-reserva.md) | Draft | — | — |
| TC-UC04-HU1-E1 | UC04 · Valor calculado | HU1 | E1 Cálculo exitoso del desglose completo | P1 | RF-001 a RF-009, RNF-002, RNF-003 · CE-001, CE-002 | [TC-UC04-HU1-E1-valor-calculado.md](test-cases/TC-UC04-HU1-E1-valor-calculado.md) | Draft | — | — |
| TC-UC05-HU1-E1 | UC05 · Procesar cobro | HU1 | E1 Envío exitoso de la solicitud de cobro a la Pasarela | P1 | RF-001, RF-002, RF-004, RF-009, RF-011, RF-012, RF-013, RF-015, RNF-002, RNF-003 · (sin CE directo) | [TC-UC05-HU1-E1-envio-solicitud-cobro.md](test-cases/TC-UC05-HU1-E1-envio-solicitud-cobro.md) | Draft | — | — |
| TC-UC05-HU1-E2 | UC05 | HU1 | E2 Medio de pago tokenizado sin almacenar datos sensibles | P1 | RF-001, RF-004, RF-011, RNF-004 · (sin CE directo) | [TC-UC05-HU1-E2-medio-tokenizado.md](test-cases/TC-UC05-HU1-E2-medio-tokenizado.md) | Draft | — | — |
| TC-UC05-HU2-E1 | UC05 | HU2 | E1 La Pasarela reporta el cobro como exitoso | P1 | RF-004, RF-004B, RF-005, RF-006, RF-007, RF-008, RF-010, RF-012, RNF-002 · CE-004 | [TC-UC05-HU2-E1-resultado-exitoso.md](test-cases/TC-UC05-HU2-E1-resultado-exitoso.md) | Draft | — | — |
| TC-UC05-HU2-E2 | UC05 | HU2 | E2 La Pasarela reporta el rechazo del cobro | P1 | RF-004, RF-004A, RF-005, RF-006, RF-007, RF-008, RF-013, RNF-003 · CE-004 | [TC-UC05-HU2-E2-resultado-rechazo.md](test-cases/TC-UC05-HU2-E2-resultado-rechazo.md) | Draft | — | — |
| TC-UC05-HU2-E3 | UC05 | HU2 | E3 La autorización aprobada expira antes de la captura | P1 | RF-004, RF-004A, RF-005, RF-008, RF-014, RNF-003, RNF-005 · CE-004, CE-007 | [TC-UC05-HU2-E3-autorizacion-expirada.md](test-cases/TC-UC05-HU2-E3-autorizacion-expirada.md) | Draft | — | — |
| TC-UC06-HU1-E1 | UC06 · Confirmación de pago | HU1 | E1 Confirmación de un cobro exitoso | P1 | RF-001 a RF-006, RNF-002, RNF-003 · CE-001, CE-002 | [TC-UC06-HU1-E1-confirmacion-exitosa.md](test-cases/TC-UC06-HU1-E1-confirmacion-exitosa.md) | Draft | — | — |
| TC-UC06-HU1-E2 | UC06 | HU1 | E2 Confirmación de un cobro rechazado | P1 | RF-001 a RF-006, RNF-003 · CE-001 a CE-003 | [TC-UC06-HU1-E2-confirmacion-rechazo.md](test-cases/TC-UC06-HU1-E2-confirmacion-rechazo.md) | Draft | — | — |
| TC-UC06-HU1-E3 | UC06 | HU1 | E3 Consulta de un cobro aún en proceso | P1 | RF-001 a RF-006, RNF-003 · CE-001 a CE-003 | [TC-UC06-HU1-E3-cobro-en-proceso.md](test-cases/TC-UC06-HU1-E3-cobro-en-proceso.md) | Draft | — | — |
| TC-UC07-HU1-E1 | UC07 · Estado de la reserva | HU1 | E1 Estado operativo sin acción financiera (disponible/reservado/en navegación) | P1 | RF-001, RF-002, RF-008, RF-009, RNF-001 · CE-001, CE-003 | [TC-UC07-HU1-E1-estado-sin-accion.md](test-cases/TC-UC07-HU1-E1-estado-sin-accion.md) | Draft | — | — |
| TC-UC07-HU1-E2 | UC07 | HU1 | E2 Estado "iniciada" como origen del bloqueo temporal (TTL 15 min) | P1 | RF-001, RF-002, RF-002A, RF-008, RF-009, RNF-001, RNF-003 · CE-007 | [TC-UC07-HU1-E2-estado-iniciada.md](test-cases/TC-UC07-HU1-E2-estado-iniciada.md) | Draft | — | — |
| TC-UC07-HU1-E3 | UC07 | HU1 | E3 Estado "pendiente": continúa TTL y dispara el cobro | P1 | RF-001, RF-002A, RF-007, RF-008, RF-009, RF-009A, RNF-001, RNF-002, RNF-003 · CE-004, CE-007 | [TC-UC07-HU1-E3-estado-pendiente-cobro.md](test-cases/TC-UC07-HU1-E3-estado-pendiente-cobro.md) | Draft | — | — |
| TC-UC07-HU2-E1 | UC07 | HU2 | E1 Cancelación flexible (>72 h) | P1 | RF-001, RF-003, RF-009, RF-009A, RNF-003 · CE-004 | [TC-UC07-HU2-E1-cancelacion-flexible.md](test-cases/TC-UC07-HU2-E1-cancelacion-flexible.md) | Draft | — | — |
| TC-UC07-HU2-E2 | UC07 | HU2 | E2 Cancelación moderada (72 h – 24 h) | P1 | RF-001, RF-004, RF-009, RF-009A, RNF-002, RNF-003 · CE-002, CE-004 | [TC-UC07-HU2-E2-cancelacion-moderada.md](test-cases/TC-UC07-HU2-E2-cancelacion-moderada.md) | Draft | — | — |
| TC-UC07-HU2-E3 | UC07 | HU2 | E3 Cancelación tardía (<24 h) | P1 | RF-001, RF-005, RF-009, RF-009A, RNF-003 · CE-003, CE-004, CE-006 | [TC-UC07-HU2-E3-cancelacion-tardia.md](test-cases/TC-UC07-HU2-E3-cancelacion-tardia.md) | Draft | — | — |
| TC-UC07-HU2-E4 | UC07 | HU2 | E4 Cancelación por anfitrión | P1 | RF-001, RF-005A, RF-009, RF-009A, RF-010, RNF-003 · CE-004 | [TC-UC07-HU2-E4-cancelacion-anfitrion.md](test-cases/TC-UC07-HU2-E4-cancelacion-anfitrion.md) | Draft | — | — |
| TC-UC07-HU3-E1 | UC07 | HU3 | E1 Finalización única de la reserva | P1 | RF-001, RF-006, RF-007, RF-009, RF-009A, RF-009B, RNF-002, RNF-003 · CE-003, CE-004, CE-005, CE-006 | [TC-UC07-HU3-E1-finalizacion.md](test-cases/TC-UC07-HU3-E1-finalizacion.md) | Draft | — | — |
| TC-UC08-HU1-E1 | UC08 · Disputa de garantía | HU1 | E1 Disputa pendiente | P1 | RF-001, RF-002, RF-004, RF-005, RF-006, RF-007, RF-008, RF-009, RF-010, RNF-001, RNF-003 · CE-001, CE-002, CE-004, CE-005, CE-006 | [TC-UC08-HU1-E1-disputa-pendiente.md](test-cases/TC-UC08-HU1-E1-disputa-pendiente.md) | Draft | — | — |
| TC-UC08-HU1-E2 | UC08 | HU1 | E2 Disputa rechazada y devolución al Arrendatario | P1 | RF-001, RF-003, RF-005, RF-007, RF-008, RF-009, RF-010, RNF-003 · CE-001, CE-003, CE-004, CE-005, CE-006 | [TC-UC08-HU1-E2-disputa-rechazada.md](test-cases/TC-UC08-HU1-E2-disputa-rechazada.md) | Draft | — | — |
| TC-UC08-HU1-E3 | UC08 | HU1 | E3 Disputa completada y liquidación al Propietario | P1 | RF-001, RF-002, RF-006, RF-007, RF-008, RF-009, RF-010, RNF-003 · CE-001, CE-003, CE-004, CE-005, CE-006 | [TC-UC08-HU1-E3-disputa-completada.md](test-cases/TC-UC08-HU1-E3-disputa-completada.md) | Draft | — | — |
| TC-UC08-HU1-E4 | UC08 | HU1 | E4 Ausencia de disputa al vencer la ventana de 24 h | P1 | RF-001, RF-002, RF-003, RF-005, RF-008, RF-009, RF-009A, RF-010, RNF-001 · CE-001, CE-005, CE-006 | [TC-UC08-HU1-E4-disputa-ausente.md](test-cases/TC-UC08-HU1-E4-disputa-ausente.md) | Draft | — | — |
| TC-UC09-HU1-E1 | UC09 · Reembolsos | HU1 | E1 Reembolso por cancelación flexible (>72 h) | P1 | RF-001, RF-002, RF-006, RF-007, RF-008, RF-009, RF-010, RF-012, RNF-001, RNF-002, RNF-003 · CE-002 | [TC-UC09-HU1-E1-reembolso-flexible.md](test-cases/TC-UC09-HU1-E1-reembolso-flexible.md) | Draft | — | — |
| TC-UC09-HU1-E2 | UC09 | HU1 | E2 Reembolso por cancelación moderada (72 h – 24 h) | P1 | RF-001, RF-003, RF-005, RF-006, RF-007, RF-010, RF-011, RF-012, RNF-002, RNF-003 · CE-002 | [TC-UC09-HU1-E2-reembolso-moderado.md](test-cases/TC-UC09-HU1-E2-reembolso-moderado.md) | Draft | — | — |
| TC-UC09-HU1-E3 | UC09 | HU1 | E3 Reembolso por cancelación del anfitrión | P1 | RF-001, RF-002, RF-006, RF-007, RF-010, RF-012, RNF-003 · CE-002 | [TC-UC09-HU1-E3-reembolso-anfitrion.md](test-cases/TC-UC09-HU1-E3-reembolso-anfitrion.md) | Draft | — | — |
| TC-UC09-HU1-E4 | UC09 | HU1 | E4 Reembolso del depósito por cancelación tardía (<24 h) | P1 | RF-001, RF-003A, RF-005, RF-006, RF-007, RF-010, RNF-003 · CE-002 | [TC-UC09-HU1-E4-reembolso-tardio-deposito.md](test-cases/TC-UC09-HU1-E4-reembolso-tardio-deposito.md) | Draft | — | — |
| TC-UC09-HU2-E1 | UC09 | HU2 | E1 Reembolso por disputa `REJECTED` o ausencia de reclamo | P1 | RF-001, RF-004, RF-005, RF-006, RF-007, RF-010, RNF-002, RNF-003 · CE-001, CE-005 | [TC-UC09-HU2-E1-reembolso-disputa-rejected.md](test-cases/TC-UC09-HU2-E1-reembolso-disputa-rejected.md) | Draft | — | — |
| TC-UC09-HU3-E1 | UC09 | HU3 | E1 La Pasarela reporta el reembolso como exitoso | P1 | RF-006, RF-006A, RF-007, RF-008, RF-009, RF-011, RNF-002 · CE-003 | [TC-UC09-HU3-E1-resultado-exitoso.md](test-cases/TC-UC09-HU3-E1-resultado-exitoso.md) | Draft | — | — |
| TC-UC09-HU3-E2 | UC09 | HU3 | E2 La Pasarela reporta rechazo o fallo del reembolso | P1 | RF-006, RF-007, RF-008, RF-009, RNF-003 · CE-004 | [TC-UC09-HU3-E2-resultado-rechazo.md](test-cases/TC-UC09-HU3-E2-resultado-rechazo.md) | Draft | — | — |
| TC-UC10-HU1-E1 | UC10 · Liquidar fondos | HU1 | E1 Dispersión por cancelación moderada (50 % alquiler) | P1 | RF-001, RF-002, RF-007, RF-008, RF-009, RF-010, RF-012, RF-013, RF-014, RF-016, RNF-002, RNF-003 · CE-001, CE-002 | [TC-UC10-HU1-E1-dispersion-moderada.md](test-cases/TC-UC10-HU1-E1-dispersion-moderada.md) | Draft | — | — |
| TC-UC10-HU1-E2 | UC10 | HU1 | E2 Dispersión por cancelación tardía (100 % alquiler) | P1 | RF-001, RF-003, RF-007, RF-009, RF-010, RF-012, RF-013, RF-014, RF-016, RNF-002, RNF-003 · CE-001, CE-002 | [TC-UC10-HU1-E2-dispersion-tardia.md](test-cases/TC-UC10-HU1-E2-dispersion-tardia.md) | Draft | — | — |
| TC-UC10-HU2-E1 | UC10 | HU2 | E1 Liquidación estándar por reserva completada | P1 | RF-001, RF-004, RF-005, RF-008, RF-009, RF-010, RF-012, RF-013, RF-014, RF-016, RNF-002, RNF-003 · CE-001 | [TC-UC10-HU2-E1-liquidacion-estandar.md](test-cases/TC-UC10-HU2-E1-liquidacion-estandar.md) | Draft | — | — |
| TC-UC10-HU3-E1 | UC10 | HU3 | E1 Retención total del depósito | P1 | RF-001, RF-006, RF-008, RF-009, RF-010, RF-012, RF-013, RF-014, RNF-002, RNF-003 · CE-001, CE-002 | [TC-UC10-HU3-E1-liquidacion-deposito.md](test-cases/TC-UC10-HU3-E1-liquidacion-deposito.md) | Draft | — | — |
| TC-UC10-HU4-E1 | UC10 | HU4 | E1 El libro interno confirma la liquidación como exitosa | P1 | RF-009, RF-009A, RF-010, RF-011, RF-015, RF-016, RNF-002 · CE-003 | [TC-UC10-HU4-E1-resultado-exitoso.md](test-cases/TC-UC10-HU4-E1-resultado-exitoso.md) | Draft | — | — |
| TC-UC10-HU4-E2 | UC10 | HU4 | E2 El libro interno no confirma la liquidación | P1 | RF-009, RF-010, RF-012, RNF-003 · CE-004, CE-005 | [TC-UC10-HU4-E2-resultado-rechazo.md](test-cases/TC-UC10-HU4-E2-resultado-rechazo.md) | Draft | — | — |
| TC-UC10-HU5-E1 | UC10 | HU5 | E1 Registro de la comisión en una liquidación estándar | P1 | RF-004, RF-005, RF-009A, RF-010, RF-011, RF-012, RF-013, RF-014, RF-015, RF-016, RNF-002 · CE-006 | [TC-UC10-HU5-E1-comision-estandar.md](test-cases/TC-UC10-HU5-E1-comision-estandar.md) | Draft | — | — |
| TC-UC10-HU5-E2 | UC10 | HU5 | E2 Comisión en cero para dispersiones por penalidad | P1 | RF-001, RF-002, RF-003, RF-006, RF-007, RF-008, RF-009, RF-009A, RF-010, RF-012, RF-013, RF-014, RF-016 · CE-002 | [TC-UC10-HU5-E2-comision-cero.md](test-cases/TC-UC10-HU5-E2-comision-cero.md) | Draft | — | — |
| TC-UC11-HU1-E1 | UC11 · Parámetros financieros | HU1 | E1 Configuración del porcentaje de comisión | P1 | RF-001, RF-005, RF-006, RF-007, RF-009, RF-011, RF-012, RF-013, RNF-002 · CE-002 | [TC-UC11-HU1-E1-config-comision.md](test-cases/TC-UC11-HU1-E1-config-comision.md) | Draft | — | — |
| TC-UC11-HU1-E2 | UC11 | HU1 | E2 Configuración de la tarifa de seguro por pasajero | P1 | RF-002, RF-005, RF-006, RF-007, RF-009, RF-011, RF-012, RF-013, RNF-002 · CE-002 | [TC-UC11-HU1-E2-config-seguro.md](test-cases/TC-UC11-HU1-E2-config-seguro.md) | Draft | — | — |
| TC-UC11-HU2-E1 | UC11 | HU2 | E1 Configuración del incremento por fin de semana | P1 | RF-003, RF-005, RF-006, RF-007, RF-009, RF-011, RF-013 · CE-002 | [TC-UC11-HU2-E1-config-fin-semana.md](test-cases/TC-UC11-HU2-E1-config-fin-semana.md) | Draft | — | — |
| TC-UC11-HU2-E2 | UC11 | HU2 | E2 Configuración del incremento por temporada alta | P1 | RF-004, RF-005, RF-006, RF-007, RF-009, RF-011, RF-012, RF-013 · CE-002 | [TC-UC11-HU2-E2-config-temporada-alta.md](test-cases/TC-UC11-HU2-E2-config-temporada-alta.md) | Draft | — | — |
| TC-UC11-HU3-E1 | UC11 | HU3 | E1 Carga de la configuración vigente | P1 | RF-006, RF-009, RF-012, RF-013, RNF-001, RNF-002 · (sin CE directo) | [TC-UC11-HU3-E1-carga-vigente.md](test-cases/TC-UC11-HU3-E1-carga-vigente.md) | Draft | — | — |
| TC-UC11-HU3-E2 | UC11 | HU3 | E2 Carga sin configuración previa (no inventa valores) | P1 | RF-005, RF-009, RF-013 · (sin CE directo) | [TC-UC11-HU3-E2-carga-sin-config.md](test-cases/TC-UC11-HU3-E2-carga-sin-config.md) | Draft | — | — |
| TC-UC11-HU4-E1 | UC11 | HU4 | E1 Guardado completo y exitoso (versión atómica) | P1 | RF-005, RF-006, RF-007, RF-008, RF-009, RF-011, RF-012, RF-013, RNF-002, RNF-003 · CE-001, CE-002, CE-004 | [TC-UC11-HU4-E1-guardado-exitoso.md](test-cases/TC-UC11-HU4-E1-guardado-exitoso.md) | Draft | — | — |
| TC-UC11-HU4-E2 | UC11 | HU4 | E2 Cancelación de cambios no guardados | P1 | RF-005, RF-007, RF-010, RF-011 · CE-004 | [TC-UC11-HU4-E2-cancelar-cambios.md](test-cases/TC-UC11-HU4-E2-cancelar-cambios.md) | Draft | — | — |
| TC-UC11-HU4-E3 | UC11 | HU4 | E3 Fallo durante el guardado (sin cambios parciales) | P1 | RF-005, RF-007, RF-011, RF-013 · CE-002 | [TC-UC11-HU4-E3-fallo-guardado.md](test-cases/TC-UC11-HU4-E3-fallo-guardado.md) | Draft | — | — |
| TC-UC12-HU1-E1 | UC12 · Registros financieros | HU1 | E1 Consulta propia con paginación por defecto | P1 | RF-001, RF-003, RF-004, RF-005, RF-006, RF-007, RF-008, RF-009, RF-010, RF-011, RF-013, RNF-003 · CE-001, CE-003, CE-004, CE-005 | [TC-UC12-HU1-E1-paginacion-propia.md](test-cases/TC-UC12-HU1-E1-paginacion-propia.md) | Draft | — | — |
| TC-UC12-HU1-E2 | UC12 | HU1 | E2 Consulta propia con filtros | P1 | RF-002, RF-003, RF-004, RF-005, RF-006, RF-007, RF-009, RF-012, RNF-002 · CE-001 | [TC-UC12-HU1-E2-filtros-propios.md](test-cases/TC-UC12-HU1-E2-filtros-propios.md) | Draft | — | — |
| TC-UC12-HU2-E1 | UC12 | HU2 | E1 Consulta global con paginación por defecto | P1 | RF-003, RF-004, RF-005, RF-006, RF-007, RF-008, RF-009, RF-010, RF-011, RF-013, RNF-003 · CE-002, CE-003, CE-004, CE-005 | [TC-UC12-HU2-E1-paginacion-global.md](test-cases/TC-UC12-HU2-E1-paginacion-global.md) | Draft | — | — |
| TC-UC12-HU2-E2 | UC12 | HU2 | E2 Consulta global filtrada | P1 | RF-002, RF-003, RF-004, RF-005, RF-006, RF-007, RF-008, RF-009, RF-012, RNF-003 · CE-002, CE-004 | [TC-UC12-HU2-E2-filtros-global.md](test-cases/TC-UC12-HU2-E2-filtros-global.md) | Draft | — | — |
| TC-UC13-HU1-E1 | UC13 · Informe financiero | HU1 | E1 Informe de la plataforma del último período cerrado | P1 | RF-001, RF-002, RF-003, RF-004, RF-005, RF-007, RF-008, RF-009, RF-010, RF-011, RF-014, RNF-002, RNF-003 · CE-002, CE-003, CE-004, CE-005 | [TC-UC13-HU1-E1-informe-plataforma-ultimo.md](test-cases/TC-UC13-HU1-E1-informe-plataforma-ultimo.md) | Draft | — | — |
| TC-UC13-HU1-E2 | UC13 | HU1 | E2 Informe de la plataforma de un período anterior | P1 | RF-001, RF-002, RF-003, RF-004, RF-005, RF-007, RF-008, RF-009, RF-010, RNF-003 · CE-001 a CE-004 | [TC-UC13-HU1-E2-informe-plataforma-anterior.md](test-cases/TC-UC13-HU1-E2-informe-plataforma-anterior.md) | Draft | — | — |
| TC-UC13-HU2-E1 | UC13 | HU2 | E1 Informe propio del último período cerrado | P1 | RF-001, RF-003, RF-004, RF-005, RF-006, RF-007, RF-008, RF-009, RF-010, RF-011, RF-014, RF-015, RNF-003 · CE-001 a CE-005 | [TC-UC13-HU2-E1-informe-propio-ultimo.md](test-cases/TC-UC13-HU2-E1-informe-propio-ultimo.md) | Draft | — | — |
| TC-UC13-HU2-E2 | UC13 | HU2 | E2 Informe propio de un período anterior | P1 | RF-002, RF-003, RF-004, RF-006, RF-008, RF-009, RF-010, RNF-003 · CE-001 a CE-004 | [TC-UC13-HU2-E2-informe-propio-anterior.md](test-cases/TC-UC13-HU2-E2-informe-propio-anterior.md) | Draft | — | — |
| TC-UC13-HU3-E1 | UC13 | HU3 | E1 Exportación del informe de la plataforma (.csv) | P2 | RF-004, RF-009, RF-010, RF-011, RF-012, RF-013, RNF-002, RNF-004 · CE-003, CE-006 | [TC-UC13-HU3-E1-export-plataforma.md](test-cases/TC-UC13-HU3-E1-export-plataforma.md) | Draft | — | — |
| TC-UC13-HU3-E2 | UC13 | HU3 | E2 Exportación del informe propio (.csv) | P2 | RF-006, RF-008, RF-009, RF-010, RF-012, RF-013, RNF-002, RNF-004 · CE-001, CE-002, CE-006 | [TC-UC13-HU3-E2-export-propio.md](test-cases/TC-UC13-HU3-E2-export-propio.md) | Draft | — | — |

## Cobertura

- **13 specs / 30 HUs / 62 escenarios** → **62 TCs** (un TC por escenario).
- Prioridad P2: UC01-HU3 (rendimiento de Flota) y UC13-HU3 (exportación CSV).
- ⏳ La columna "RF/CE cubierto" se re-valida fila por fila al redactar el TC correspondiente (el mapeo se derivó de escenarios/Prueba Independiente, ver Convenciones).

## Hallazgos para la ejecución en release

Detectados al redactar los TC y contrastar spec ↔ plan ↔ contratos. No cambian los TC; son condiciones a tener en cuenta al ejecutar en `release/0.1.0`:

1. **UC05-HU2-E3 (autorización expira)**: el contrato vigente usa cobro directo con captura automática (D-32), sin autorización retenida. El escenario se materializa con el estado `expired` reportado por el webhook.
2. **UC06 (confirmación de pago)**: el `reservation_id` de la ruta debe ser UUID v4; usar el UUID de ejemplo del contrato (no la etiqueta `R-100`).
3. **UC01 (estimaciones)**: `wiremock/mappings/fleet-base-rates.json` solo trae 2 embarcaciones; cargar 10 (HU1) y 50 (HU3) vía `POST /__admin/mappings` antes de ejecutar.
4. **PostgreSQL**: `docker-compose.yml` publica el puerto host **5433** (el README menciona 5432). Usar 5433 desde el host en las consultas `psql`.
5. **UC11-HU3**: `GET /api/v1/financial-parameters` devuelve `guarantee_deposit_rule` como texto, no como monto (decisión D-UC11-02).
6. **UC10 (liquidaciones)**: `GATEWAY_SETTLEMENT_MODE` debe ser `simulated` (valor por defecto); no existe cuenta destino ni dispersión real. El libro interno devuelve `sim-settlement-<operationKey>` y crea el registro inmutable al procesar el comando (D-UC10-18).
7. **Montos de ejemplo**: los TC usan cifras ilustrativas distintas (`360.00/200.00/12.00` vs `900000.00/60000.00/30000.00`). Antes de ejecutar, fijar los valores de la versión vigente de parámetros del entorno.
8. **UC13 (exportación)**: el contrato no declara `406 NOT_ACCEPTABLE`, pero el catálogo E1 lo exige como caso de ejemplo; verificar la respuesta real en release y registrar defecto si difiere.
9. **UC02-HU1-E3 (festivos)**: el SPEC dice que los puentes/festivos se evalúan como fin de semana; el plan (D-UC02-11) y el código solo tratan sábado y domingo como fin de semana. Los TC siguen el plan; si se valida el texto literal del spec, registrar la divergencia.
10. **UC05 `payment_method_id`**: el adaptador omite el campo cuando UC07 informa un tipo genérico (`CREDIT_CARD`). La omisión está **[PENDIENTE SANDBOX]** (D-UC05-16): si Mercado Pago responde `4001`, el escenario B-09 de `pagos-api-real.md` falla y UC07 debe aportar la marca. Ejecutar B-09 antes de dar por aprobado el cobro.
11. **UC09 404/5xx y reembolsos ajenos**: el contrato de webhook §3.6 exige registrar en `operational_failure` el reembolso sin intención local, el `404` y el `5xx` agotado; los TC E1/E2 ya lo reflejan. Si el código aún los ignora, se registra como defecto, no se ajusta el TC.
