### 2. Proyecto: SEA-SHARE

```markdown
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
2.  **Reservado:** Bloqueo temporal por proceso de pago.
3.  **En Navegación:** Contrato activo y embarcación fuera del puerto.
4.  **En Mantenimiento/Limpieza:** Inhabilitada por reparaciones o adecuación.

---

### ⚓ Módulo 2: Operación de Reservas, Tiempos y Cancelaciones
Regula la interacción entre los usuarios y gestiona la disponibilidad crítica del inventario.

#### 2.1. El Ciclo de Reserva y Control de Tiempos (TTL)
*   **Bloqueo Temporal (TTL):** Al iniciar una reserva, el activo se bloquea por un **Time-To-Live de 15 minutos**. Si el pago no se confirma, vuelve a estado "Disponible".
*   **Ventana de Tolerancia:** Si el arrendatario no se presenta tras 30 minutos de la hora pactada, el sistema permite marcar un "No-Show".

#### 2.2. Lógica de Cancelaciones y Reembolsos
*   **Cancelación Flexible (>72h):** Reembolso del 100% (menos costos transaccionales).
*   **Cancelación Moderada (72h - 24h):** Penalidad del 50% del valor del alquiler.
*   **Cancelación Tardía / No-Show (<24h):** Se cobra el 100% como compensación al anfitrión.

---

### 💰 Módulo 3: Liquidación, Seguros y Dispersión de Fondos
Traduce la operación turística en datos financieros y distribuye el recaudo entre la plataforma y el propietario.

#### 3.1. Reglas de Negocio para el Cobro
*   **Tarifas Dinámicas:** Precios ajustados por temporada o fines de semana.
*   **Depósito de Garantía:** Monto retenido temporalmente para cubrir posibles daños menores detectados al regreso.
*   **Seguro Náutico:** Tarifa fija por pasajero para cobertura de accidentes.

#### 3.2. Matriz de Liquidación (Reparto de Ingresos)
| Concepto | Cálculo Aplicado | Observación |
| :--- | :--- | :--- |
| **Valor Alquiler Bruto** | Tarifa base x Duración | Ingreso total pagado por el turista. |
| **Comisión Plataforma** | - (Valor Bruto * % Comisión) | Ingreso neto para la empresa de software. |
| **Pago al Propietario** | (Valor Bruto - Comisión - Seguro) | Monto final dispersado al dueño. |
| **Penalidad por Cancelación**| Según regla del Módulo 2 | Se dispersa el porcentaje correspondiente al dueño. |
```