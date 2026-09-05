# Revisión de Specs vs. Diagrama de Casos de Uso (UC) y Reglas de Negocio

**Fecha de revisión**: 2026-09-04  
**Fuente UC**: `Sea-Share module.drawio.xml` — Diagrama "Modulo 3" (Gestión de Liquidación)  
**Fuente SDD**: `docs/features/001..004`  
**Fuente Contexto de Negocio**: `sea-share.md` — Documento general del proyecto Sea-Share

---

## Reglas de Negocio del Módulo 3 (según `sea-share.md`)

Estas son las reglas oficiales del proyecto contra las cuales se validan las specs:

### §3.1 Reglas de Negocio para el Cobro
- **Tarifas Dinámicas**: Precios ajustados por temporada o fines de semana.
- **Depósito de Garantía**: Monto retenido temporalmente para cubrir posibles daños menores detectados al regreso.
- **Seguro Náutico**: Tarifa fija por pasajero para cobertura de accidentes.

### §3.2 Matriz de Liquidación (Reparto de Ingresos)
| Concepto | Cálculo Aplicado | Observación |
| :--- | :--- | :--- |
| **Valor Alquiler Bruto** | Tarifa base × Duración | Ingreso total pagado por el turista. |
| **Comisión Plataforma** | − (Valor Bruto × % Comisión) | Ingreso neto para la empresa de software. |
| **Pago al Propietario** | (Valor Bruto − Comisión − Seguro) | Monto final dispersado al dueño. |
| **Penalidad por Cancelación** | Según regla del Módulo 2 | Se dispersa el porcentaje correspondiente al dueño. |

### §2.2 Lógica de Cancelaciones y Reembolsos (Módulo 2, consumida por Módulo 3)
- **Cancelación Flexible (>72h)**: Reembolso del 100% (menos costos transaccionales).
- **Cancelación Moderada (72h – 24h)**: Penalidad del 50% del valor del alquiler.
- **Cancelación Tardía / No-Show (<24h)**: Se cobra el 100% como compensación al anfitrión.

### §2.1 Control de Tiempos (responsabilidad del Módulo 2)
- **Bloqueo Temporal (TTL)**: Al iniciar una reserva, el activo se bloquea por **15 minutos**. Si el pago no se confirma, vuelve a "Disponible".
- **Ventana de Tolerancia**: Si el arrendatario no se presenta tras **30 minutos**, el sistema permite marcar un "No-Show".

---

## Resumen de Casos de Uso del Módulo 3 en el diagrama XML

A continuación se listan todos los casos de uso identificados en el diagrama de CU del Módulo 3, con sus actores y relaciones:

| # | Caso de Uso | Actores | Relaciones |
|---|------------|---------|------------|
| 1 | Solicitar cotización para reserva | Módulo 2 (Reservas) | `<<include>>` Brindar tarifa base |
| 2 | Brindar tarifa base | Módulo 1 | — (incluido desde UC1) |
| 3 | Procesar cobro y custodia de reserva | Pasarela de pago, Arrendatario | `<<include>>` Retener Depósito de Garantía |
| 4 | Retener Depósito de Garantía | — | — (incluido desde UC3) |
| 5 | Liquidar fondos de alquiler | Pasarela de pago, Arrendatario | `<<include>>` Dispersar pago Neto del anfitrión |
| 6 | Dispersar pago Neto del anfitrión | — | — (incluido desde UC5) |
| 7 | Ejecutar liquidación por cancelación | Pasarela de pago, Arrendatario, Módulo 2 | — |
| 8 | Resolver disputa de garantía | Admin. Financiero, Arrendatario, Propietario | — |
| 9 | Consultar registros financieros | Admin. Financiero | — |
| 10 | Configurar parámetros financieros globales | Admin. Financiero | — |
| 11 | Consultar valor del alquiler bruto | Propietario | `<<include>>` Brindar información de reserva |
| 12 | Brindar información de reserva | Módulo 2 | — (incluido desde UC11) |
| 13 | Brindar tipo de cancelación | Módulo 2 | — |
| 14 | Reembolsar dinero a arrendatario | Pasarela de pago | — |
| 15 | Recibir el estado de la reserva | Módulo 2 | — |
| 16 | Solicitar confirmación de pago | Módulo 2 | — |
| 17 | Consultar estado financiero global | Admin. Financiero | — |
| 18 | Consultar ingresos | Propietario | — |
| 19 | Consultar estado de garantía | Arrendatario, Propietario | — |

---

## Spec 001 — UC01: Solicitar Cotización de Reserva

**Archivo**: [`spec.md`](file:///C:/GitLocal/SeaShare_Modulo_3/docs/features/001-request-booking/1-functional/spec.md)  
**UC Correspondiente en XML**: *Solicitar cotización para reserva* → `<<include>>` *Brindar tarifa base*

### Errores y discrepancias encontradas

| # | Tipo | Descripción | Detalle |
|---|------|------------|---------|
| 1 | 🔴 **Nombre del UC incorrecto** | El título del spec dice **"Solicitar Cotización de Reserva"**, pero en el diagrama XML el UC se llama **"Solicitar cotización para reserva"**. | Debe estandarizarse el nombre para que coincida exactamente con el del diagrama UC. |
| 2 | 🔴 **Actor principal mal identificado** | La spec define al **"Módulo de Reservas"** como actor/iniciador. En el diagrama XML, el UC *Solicitar cotización para reserva* es invocado desde el nodo **MODULO 2 (RESERVAS Y OPERACIONES)**, que actúa como un sistema externo. Sin embargo, la spec no menciona al **Arrendatario** que en el diagrama es el actor humano que dispara indirectamente el flujo a través del Módulo 2. | La spec debería aclarar la cadena completa de actores: Arrendatario → Módulo 2 → Módulo 3 (este UC). |
| 3 | 🟡 **Nombre del sub-UC inconsistente** | La spec menciona el sub-caso **"Proveer tarifa base"** (HU1, paso 3). En el diagrama XML el UC incluido se llama **"Brindar tarifa base"**. | Usar la nomenclatura oficial del diagrama: "Brindar tarifa base". |
| 4 | 🟡 **Módulo referenciado con nombre incorrecto** | La spec se refiere al **"Módulo de Gestión de Flota"** como fuente de tarifas base. En el diagrama XML el módulo externo que provee tarifa base es **"MODULO 1"** (sin especificar "Gestión de Flota" en la página del Módulo 3). En la página del Módulo 1, se llama **"Gestión de embarcación"**, no "Gestión de Flota". | Alinear la nomenclatura: usar "Módulo 1 (Gestión de Embarcación)" en lugar de "Módulo de Gestión de Flota". |
| 5 | 🟡 **Falta relación con otros UC del diagrama** | El diagrama muestra que *Solicitar cotización para reserva* se encuentra dentro del contexto de UCs que también incluyen *Procesar cobro y custodia de reserva* y *Retener Depósito de Garantía* cuando viene del Módulo 2. La spec no menciona ninguna interacción ni dependencia con estos UCs relacionados. | Considerar agregar una nota sobre la relación con UC3 (Procesar cobro) como flujo posterior en el pipeline de reserva. |
| 6 | 🟢 **Fórmula de precios no reflejada en UC** | La spec define la fórmula `(tarifa base * duración) + (tarifa de seguro * pasajeros)` (RF-002), pero el diagrama UC no contiene detalles sobre fórmulas. | Esto es aceptable ya que los UC son de alto nivel, pero la fórmula debería ser validada contra el UC de *Solicitar información de pago* (spec 004) que también define una fórmula diferente. **Hay inconsistencia con la fórmula de la spec 004** (ver spec 004). |
| 7 | 🔴 **Fórmula no alineada con la Matriz de Liquidación del documento de contexto** | El documento `sea-share.md` (§3.2) define que el **Valor Alquiler Bruto = Tarifa base × Duración**. La spec 001 mezcla este concepto sumándole `(tarifa de seguro × pasajeros)` dentro de la misma cotización. Según el contexto oficial, el Seguro Náutico (§3.1) es una **tarifa fija por pasajero** que debería ser un concepto **separado** del Valor Alquiler Bruto, no sumado dentro de la misma fórmula de cotización. | La fórmula de la spec debería separar claramente: (1) `Valor Alquiler Bruto = tarifa base × duración`, (2) `Seguro Náutico = tarifa fija × pasajeros`, (3) `Total Cotización = Valor Alquiler Bruto + Seguro Náutico`. Esto permite mantener la trazabilidad con la Matriz de Liquidación oficial. |
| 8 | 🟡 **No menciona la Comisión de Plataforma** | El documento de contexto (§3.2) define que la **Comisión Plataforma** se deduce del Valor Bruto. La spec de cotización no menciona este concepto en absoluto, ni siquiera para indicar que es un dato interno no visible al usuario. | Considerar si la cotización estimada debe mostrar o no la comisión de plataforma. En cualquier caso, documentar la decisión explícitamente. |

---

## Spec 002 — UC02: Consultar Registros Financieros

**Archivo**: [`spec.md`](file:///C:/GitLocal/SeaShare_Modulo_3/docs/features/002-consult-financial-records/1-functional/spec.md)  
**UC Correspondiente en XML**: *Consultar registros financieros*

### Errores y discrepancias encontradas

| # | Tipo | Descripción | Detalle |
|---|------|------------|---------|
| 1 | ✅ **Nombre correcto** | El título "Consultar Registros Financieros" coincide correctamente con el UC del diagrama **"Consultar registros financieros"**. | Sin observaciones. |
| 2 | ✅ **Actor correcto** | La spec identifica al **Administrador Financiero** como actor principal, que coincide con el diagrama donde el actor **"Admin. Financiero"** está conectado a este UC. | Sin observaciones. |
| 3 | 🔴 **No tiene identificador UC consistente** | La spec se titula **"UC02"**, pero en el diagrama no hay numeración explícita de los UC. Esto en sí no es un error, pero **el UC del diagrama no muestra ninguna relación `<<include>>` ni `<<extend>>`** con otros UCs, mientras que la spec en la HU2 habla de "Matriz de Liquidación" que implica datos provenientes de los UCs de *Liquidar fondos de alquiler* y *Dispersar pago Neto del anfitrión*. | La spec debería indicar explícitamente que los datos de la Matriz de Liquidación provienen de las operaciones de los UCs #5 y #6 del diagrama, estableciendo una dependencia de datos. |
| 4 | 🟡 **Alcance más amplio que el UC** | El diagrama muestra *Consultar registros financieros* como un UC simple (elipse sin relaciones). Sin embargo, la spec define funcionalidades complejas como filtrado por tipo de operación, paginación, desglose de Matriz de Liquidación, y relación entre INGRESOS y EGRESOS. | Esto podría significar que el diagrama está incompleto (faltarían sub-UCs o includes) o que la spec está sobredimensionada respecto al UC original. Se recomienda agregar `<<include>>` en el diagrama hacia un sub-UC de "Consultar detalle de Matriz de Liquidación". |
| 5 | 🟡 **Falta mención del UC "Consultar estado financiero global"** | El diagrama tiene un UC separado llamado **"Consultar estado financiero global"** conectado al Admin. Financiero. La spec 002 no distingue entre este UC y el de "Consultar registros financieros". | Verificar si la spec 002 está mezclando responsabilidades con el UC "Consultar estado financiero global" del diagrama. Si son diferentes, necesitan specs separados. |
| 6 | 🟡 **Referencia a Depósito de Garantía sin UC** | En los Edge Cases, la spec menciona "disputa por un Depósito de Garantía", lo cual pertenece al UC **"Resolver disputa de garantía"**. La spec no aclara esta dependencia entre UCs. | Agregar referencia cruzada al UC de "Resolver disputa de garantía". |

---

## Spec 003 — Obtener el Estado de la Reserva

**Archivo**: [`spec.md`](file:///C:/GitLocal/SeaShare_Modulo_3/docs/features/003-get-the-reservation-status/1-functional/spec.md)  
**UC Correspondiente en XML**: *Recibir el estado de la reserva*

### Errores y discrepancias encontradas

| # | Tipo | Descripción | Detalle |
|---|------|------------|---------|
| 1 | 🔴 **Nombre del UC inconsistente** | La spec se titula **"Obtener el Estado de la Reserva"**, pero en el diagrama XML el UC se llama **"Recibir el estado de la reserva"**. | El verbo "Obtener" vs "Recibir" implica direcciones distintas. "Recibir" sugiere que el Módulo 3 recibe el dato pasivamente, mientras que "Obtener" sugiere que lo consulta activamente. Estandarizar con el diagrama: **"Recibir el estado de la reserva"**. |
| 2 | 🔴 **Falta el código UC en el título** | Las otras specs usan formato "UC01", "UC02". Esta spec **no tiene código UC** (dice solo "Obtener el Estado de la Reserva" sin "UC03"). | Asignar identificador UC03 para mantener consistencia con el resto de specs. |
| 3 | 🔴 **Confusión de dirección del flujo** | La HU1 dice que el **Módulo de Finanzas (Módulo 3) necesita obtener el estado** desde el Módulo 2. Sin embargo, en el diagrama XML, el UC **"Recibir el estado de la reserva"** está conectado **desde** el nodo MODULO 2 (RESERVAS Y OPERACIONES) **hacia** el Módulo 3. Esto significa que es el **Módulo 2 quien envía/proporciona** el estado al Módulo 3, no que el Módulo 3 lo consulta activamente. | La spec invierte la responsabilidad. Según el diagrama, el Módulo 2 envía el estado al Módulo 3, no al revés. Revisar si la arquitectura real es push (Módulo 2 → Módulo 3) o pull (Módulo 3 consulta Módulo 2). |
| 4 | 🔴 **HU2 describe responsabilidad del Módulo 2, no del Módulo 3** | La HU2 ("Verificación del Bloqueo Temporal TTL") describe un flujo donde el **Módulo 2** verifica con el Módulo 3 si el pago fue confirmado. Esto es una funcionalidad **del Módulo 2**, no del Módulo 3. El UC del Módulo 3 correspondiente sería **"Solicitar confirmación de pago"**. | La HU2 no debería estar en esta spec del Módulo 3. Debería ser una spec del Módulo 2, o bien estar en la spec del UC "Solicitar confirmación de pago" que sí aparece en el diagrama como UC del Módulo 3. |
| 5 | 🟡 **RF-001 asigna responsabilidad incorrecta** | RF-001 dice "El sistema DEBE exponer un endpoint para consultar el estado actual de una reserva **(Módulo 2)**". Pero esta spec es del Módulo 3. Si el requisito es del Módulo 2, no debería estar documentado en las specs del Módulo 3. | Separar los requisitos por módulo. Los RFs de esta spec deberían limitarse a lo que el Módulo 3 debe hacer internamente. |
| 6 | 🟡 **RF-003 asigna responsabilidad al Módulo 2** | RF-003 dice "El Módulo 2 DEBE aplicar la regla de TTL de 15 minutos". De nuevo, un requisito del Módulo 2 documentado en una spec del Módulo 3. | Mover al spec del Módulo 2 o indicar claramente que es un requisito de interfaz/contrato. |
| 7 | 🟡 **Falta relación con otros UCs** | El diagrama muestra que este UC está conectado dentro del contexto donde también aparecen *Brindar tipo de cancelación* y *Solicitar confirmación de pago*. La spec no menciona estos UCs relacionados. | Agregar contexto sobre los UCs vecinos para trazar dependencias. |

---

## Spec 004 — Solicitar Información de Pago

**Archivo**: [`spec.md`](file:///C:/GitLocal/SeaShare_Modulo_3/docs/features/004-request-payment-information/1-functional/spec.md)  
**UC Correspondiente en XML**: *Solicitar confirmación de pago* (posible correspondencia) / *Procesar cobro y custodia de reserva*

### Errores y discrepancias encontradas

| # | Tipo | Descripción | Detalle |
|---|------|------------|---------|
| 1 | 🔴 **Sin UC correspondiente directo en el diagrama** | La spec se titula **"Solicitar Información de Pago"**, pero **no existe un UC con ese nombre** en el diagrama XML del Módulo 3. Los UCs más cercanos son: **"Solicitar confirmación de pago"** y **"Procesar cobro y custodia de reserva"**. | La spec no tiene un mapeo 1:1 con ningún UC del diagrama. Esto es un error grave de trazabilidad. Se debe: (a) agregar el UC al diagrama, o (b) remapear la spec a un UC existente. |
| 2 | 🔴 **Falta el código UC en el título** | Al igual que la spec 003, esta spec **no tiene código UC** (no dice "UC04"). | Asignar identificador UC04 para mantener consistencia. |
| 3 | 🔴 **Fórmula inconsistente con Spec 001** | La spec 001 (RF-002) define la fórmula: `(tarifa base * duración) + (tarifa de seguro * pasajeros)`. La spec 004 (RF-001) define: `(Tarifa Dinámica * Duración) + (Seguro * Pasajeros) + Depósito de Garantía`. Son fórmulas **diferentes**: la 001 usa "tarifa base" y no incluye depósito de garantía; la 004 usa "tarifa dinámica" e incluye depósito de garantía. | Definir si la fórmula de la spec 001 es una estimación simplificada y la de la spec 004 es el cálculo real final. Si es así, documentarlo explícitamente. Si no, unificar las fórmulas. |
| 4 | 🟡 **Confusión entre "información de pago" y "confirmación de pago"** | La spec habla de "solicitar información de pago" (desglose pre-checkout), mientras que el UC del diagrama dice "Solicitar confirmación de pago" (verificar que se pagó). Son conceptos **completamente diferentes**: uno es pre-pago (mostrar desglose) y el otro es post-pago (confirmar transacción). | La spec posiblemente debería mapearse a un nuevo UC que no existe en el diagrama, o al UC *Procesar cobro y custodia de reserva* → `<<include>>` *Retener Depósito de Garantía*, que sería el flujo más cercano al desglose de pago pre-checkout. |
| 5 | 🟡 **Actor principal no alineado** | La spec identifica al **Módulo de Reservas (Módulo 2)** como iniciador. En el diagrama, el UC más parecido (*Procesar cobro y custodia de reserva*) tiene como actores a la **Pasarela de pago** y al **Arrendatario**, no directamente al Módulo 2. | Verificar cuál es el actor real. El flujo probablemente es: Arrendatario → Módulo 2 → Módulo 3 → Pasarela de pago. |
| 6 | 🟡 **Tarifas dinámicas no representadas en el diagrama** | La HU2 introduce el concepto de **"Tarifas Dinámicas"** (recargos por fin de semana, temporada alta), pero **no existe ningún UC** en el diagrama que represente esta funcionalidad. | Las tarifas dinámicas deberían estar reflejadas como un UC o como un `<<include>>` dentro del UC correspondiente en el diagrama. El diagrama necesita actualizarse. |
| 7 | 🟡 **Referencia al depósito de garantía sin trazar UC** | RF-001 incluye "Depósito de Garantía" en la fórmula, lo cual corresponde al UC **"Retener Depósito de Garantía"** en el diagrama. La spec no hace referencia explícita a este UC. | Agregar referencia cruzada: esta spec depende del UC "Retener Depósito de Garantía". |

---

## Resumen General de Errores

| Spec | 🔴 Críticos | 🟡 Moderados | 🟢 Menores | Total |
|------|------------|-------------|-----------|-------|
| 001 - Solicitar Cotización de Reserva | 2 | 3 | 1 | 6 |
| 002 - Consultar Registros Financieros | 1 | 3 | 0 | 4 |
| 003 - Obtener Estado de la Reserva | 4 | 3 | 0 | 7 |
| 004 - Solicitar Información de Pago | 3 | 4 | 0 | 7 |
| **Total** | **10** | **13** | **1** | **24** |

### Errores más críticos que requieren acción inmediata

1. **Spec 004 no tiene UC correspondiente en el diagrama** — Falta total de trazabilidad.
2. **Spec 003 invierte la dirección del flujo** — Confunde quién consulta a quién.
3. **Spec 003 documenta requisitos del Módulo 2 en una spec del Módulo 3** — HU2, RF-001 y RF-003 son del módulo equivocado.
4. **Fórmulas de precios inconsistentes entre Spec 001 y Spec 004** — Dos fórmulas diferentes para lo que debería ser un cálculo unificado.
5. **Nomenclatura inconsistente** — Nombres de UCs, módulos y sub-UCs difieren entre specs y diagrama.

### Recomendaciones generales

- Estandarizar los nombres de todos los UCs usando **exactamente** la nomenclatura del diagrama.
- Asignar códigos UC (UC01, UC02, UC03, UC04...) a **todas** las specs de forma consistente.
- Agregar el UC **"Solicitar información de pago"** (desglose pre-checkout) al diagrama, ya que es un concepto diferente a "Solicitar confirmación de pago".
- Revisar y unificar la fórmula de cálculo financiero entre las specs 001 y 004.
- Limitar cada spec a los requisitos funcionales **del Módulo 3**, moviendo los requisitos del Módulo 2 a su documentación correspondiente.
