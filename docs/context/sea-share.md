# SEA-SHARE: Plataforma P2P de Alquiler Náutico

Este sistema funciona como un marketplace de economía colaborativa donde propietarios de embarcaciones (Anfitriones) y turistas interactúan para el alquiler de vehículos náuticos con fines turísticos.

---

### 🛥️ Módulo 1: Gestión de Flota y Activos P2P
Este módulo digitaliza la infraestructura física de las embarcaciones, funcionando como la base de datos central de inventario comercial.

#### 1.1. Atributos de la Embarcación (Entidad)
*   **Identificación:** UUID, Nombre de la embarcación, Matrícula legal.
*   **Categorización:** Tipo (Lancha, Yates, Catamarán), Capacidad máxima de pasajeros.
*   **Ubicación y Amenidades:** Puerto de atraque (GPS), servicios incluidos (Capitán, Combustible).
*   **Propietario:** ID del usuario dueño del activo.

#### 1.2. Ciclo de Vida y Estados del Activo
1.  **Disponible:** Visible para alquiler.
2.  **Reservado:** Bloqueo temporal desde que la reserva pasa a estado "Iniciada" (ver Módulo 2, 2.1) hasta que finaliza, se cancela o expira.
3.  **En Navegación:** Contrato activo y embarcación fuera del puerto.
4.  **En Mantenimiento/Limpieza:** Inhabilitada por reparaciones o adecuación.

---

### ⚓ Módulo 2: Operación de Reservas, Tiempos y Cancelaciones
Regula la interacción entre los usuarios y gestiona la disponibilidad crítica del inventario.

#### 2.1. El Ciclo de Reserva y Control de Tiempos (TTL)

**Estados de la Reserva:**
1. **Disponible:** la embarcación no está asociada a ninguna reserva.
2. **Iniciada:** el arrendatario oprime "Reservar" y comienza a configurar los datos de la reserva. Aquí arranca el TTL de 15 minutos, y desde este momento la embarcación deja de listarse como disponible.
3. **Pendiente:** el arrendatario oprime "Confirmar pago" y comienza el proceso de pago; esta transición es la que habilita al módulo de Finanzas a ejecutar "Procesar cobro". El TTL sigue corriendo desde que comenzó en "Iniciada" (no se reinicia aquí).
4. **Reservado:** el pago fue confirmado y la reserva quedó exitosa; aún no comienza el uso de la embarcación.
5. **En Navegación:** se realizó el check-in y el arrendatario está usando la embarcación.
6. **Completada:** el arrendatario devolvió la embarcación y el propietario marca la reserva como completada.
7. **Cancelado Flexiblemente / Moderadamente / Tardíamente:** según la ventana de cancelación aplicada (ver 2.2).

*   **Bloqueo Temporal (TTL):** El TTL de 15 minutos comienza al pasar la reserva a estado "Iniciada", **no** al confirmar el pago. Si el pago no se confirma dentro de ese lapso (ya sea en "Iniciada" o en "Pendiente"), la reserva vuelve a "Disponible".
*   **Ventana de Tolerancia:** Si el arrendatario no se presenta tras 30 minutos de la hora pactada, el sistema permite marcar un "No-Show".

#### 2.2. Lógica de Cancelaciones y Reembolsos
*   **Cancelación Flexible (>72h):** Reembolso del 100% (menos costos transaccionales).
*   **Cancelación Moderada (72h - 24h):** Penalidad del 50% del valor del alquiler.
*   **Cancelación Tardía / No-Show (<24h):** Se cobra el 100% como compensación al anfitrión.

---

### 💰 Módulo 3: Liquidación, Seguros y Dispersión de Fondos
Traduce la operación turística en datos financieros y distribuye el recaudo entre la plataforma y el propietario.

#### 3.1. Reglas de Negocio para el Cobro
*   **Tarifas Dinámicas:** Precios ajustados por **temporada alta** o fines de semana.
    *   **Temporada Alta:** Periodos del año con mayor flujo de viajeros, precios más altos en vuelos y alojamiento y mayor ocupación en los destinos. Para Colombia, el sistema deriva automáticamente la vigencia aplicando una regla de calendario con las siguientes ventanas: **fin de año** (desde la segunda mitad de noviembre hasta mediados de enero del año siguiente), **mitad de año** (junio y julio), **Semana Santa** (los días santos de marzo o abril), **semana de receso** (la semana de descanso escolar de octubre) y **puentes festivos y fines de semana largos**. La vigencia se determina automáticamente; el Administrador Financiero únicamente configura el porcentaje de incremento aplicable.
*   **Depósito de Garantía:** Corresponde al 10% de la tarifa base diaria de la embarcación, cobrado junto con el valor total y retenido temporalmente para cubrir únicamente daños menores detectados al regreso. Se entrega completo al Arrendatario o completo al Propietario; los daños mayores quedan fuera del alcance de este sistema y corresponden al seguro de la flota o a procesos legales externos.
*   **Seguro Náutico:** Tarifa fija por pasajero para cobertura de accidentes.

#### 3.2. Matriz de Liquidación (Reparto de Ingresos)
| Concepto | Cálculo Aplicado | Observación |
| :--- | :--- | :--- |
| **Valor Alquiler Bruto** | Tarifa base x Duración | Ingreso total pagado por el turista. |
| **Comisión Plataforma** | - (Valor Bruto * % Comisión) | Ingreso neto para la empresa de software. |
| **Pago al Propietario** | (Valor Bruto - Comisión - Seguro) | Monto final dispersado al dueño. |
| **Penalidad por Cancelación**| Según regla del Módulo 2 | Se dispersa el porcentaje correspondiente al dueño. |