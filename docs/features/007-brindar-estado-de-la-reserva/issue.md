# Problema pendiente detectado en SPEC 7 (UC07 - Brindar el Estado de la Reserva)

**Estado:** IMPORTANTE — pendiente de decisión de negocio/arquitectura.
**Certeza:** Probable (evidencia indirecta en el contexto; no hay una contradicción explícita palabra por palabra, pero sí un requisito del contexto que ninguna SPEC vigente cubre).

Este documento **no modifica ninguna SPEC**. Su único propósito es exponer con detalle un vacío detectado entre `contexto-modulo3.md` y las SPECs vigentes (principalmente SPEC 7 y SPEC 5), para discutirlo y decidir cómo resolverlo antes de tocar el archivo `docs/features/007-brindar-estado-de-la-reserva/spec.md`.

---

## 1. Qué dice el nuevo contexto

En `contexto-modulo3.md`, sección 2 ("Flujo Paso a Paso de una Reserva"), paso 5 ("Confirmación hacia Reservas"), se establece textualmente:

> "El sistema de Reservas solicita *'Solicitar confirmación de pago'* para conocer el estado vigente de la autorización o del cobro. Solo un estado aprobado y verificable permite avanzar la reserva. **Si no llega una confirmación dentro del estado de espera (pendiente), la reserva vuelve a 'Disponible' del lado de Reservas, sin asumir que el pago falló: cualquier autorización pendiente debe cancelarse o quedar bajo conciliación.**"

Es decir: cuando el bloqueo temporal de 15 minutos definido por el Módulo 2 (Reservas) expira sin que se haya confirmado el pago, la reserva vuelve al estado "Disponible" **del lado de Reservas**, pero el contexto exige que, del lado de Finanzas (el sistema), **cualquier autorización de cobro que haya quedado pendiente se cancele o quede marcada para conciliación** — es decir, exige una acción financiera concreta ante ese escenario.

## 2. Qué dice actualmente la SPEC vigente

### SPEC 7 (`docs/features/007-brindar-estado-de-la-reserva/spec.md`)

- **RF-002 / RF-008**: ante los estados "disponible", "reservado", "en navegación" o "pendiente", el sistema **reconoce el estado recibido sin ejecutar ninguna operación de reembolso ni de dispersión de fondos**. Textualmente, RF-008 dice: *"El sistema NO DEBE ejecutar 'Reembolsar dinero a arrendatario' ni una liquidación del depósito cuando el estado recibido sea disponible, reservado, en navegación, pendiente o completada."*
- Es decir: **recibir "disponible" nunca dispara ninguna acción financiera**, sin excepción, en la redacción actual.

### SPEC 5 (`docs/features/005-procesar-cobro/spec.md`)

- **RF-014**: cubre la expiración de una autorización **por vencimiento de su propia vigencia técnica ante la Pasarela de Pago** (por ejemplo, una autorización aprobada que nadie capturó dentro del tiempo que la Pasarela le permite vivir). Este es un disparador **interno y automático** del propio ciclo de vida de la autorización ante la Pasarela, monitoreado por el sistema (RNF-005), **no** una reacción a que Reservas informe nuevamente "disponible".

### La brecha

No existe en ninguna SPEC vigente un requisito que diga: *"cuando el sistema reciba el estado 'disponible' para una reserva que previamente estaba en 'pendiente' y tiene una autorización de cobro sin capturar, el sistema DEBE cancelar dicha autorización o marcarla para conciliación."*

- SPEC 7 excluye explícitamente cualquier acción financiera para el estado "disponible" (RF-008), sin ninguna excepción para este escenario.
- SPEC 5 solo actúa cuando la Pasarela reporta la expiración natural de la autorización (un evento distinto, con un disparador distinto — el timeout propio de la Pasarela, no la notificación de Reservas).

Esto significa que, tal como están escritas hoy las SPECs, **si el bloqueo temporal de Reservas vence antes de que expire la vigencia de la autorización ante la Pasarela**, no hay ningún requisito que ordene cancelar o conciliar esa autorización en el momento en que Reservas vuelve a "disponible". La autorización quedaría "viva" del lado de la Pasarela hasta que, eventualmente, expire por sí sola (RF-014 de SPEC 5) — lo cual podría no ser lo que el negocio espera, y contradice el espíritu de la frase del contexto ("sin asumir que los fondos siguen disponibles").

## 3. Preguntas concretas a resolver con el equipo

1. **¿Debe "disponible" dejar de ser un estado sin ninguna acción financiera?**
   ¿Se debe introducir una excepción a RF-008 para el caso específico de "disponible tras un 'pendiente' previo con autorización sin capturar"? Esto implicaría una nueva Historia de Usuario / RF en SPEC 7 (o una extensión hacia SPEC 5/6).

2. **¿"Disponible" siempre significa "el bloqueo temporal venció sin confirmación"?**
   El Módulo 3 no es dueño del estado operativo de la reserva (nota de alcance ya presente en SPEC 7). Recibir "disponible" podría originarse en más de un escenario del lado de Reservas (por ejemplo, un reinicio administrativo, no solo el vencimiento del bloqueo de 15 minutos). Si el sistema reaccionara siempre cancelando la autorización ante cualquier "disponible", ¿podría cancelar por error una autorización que en realidad sigue siendo válida para otro flujo?

3. **¿Dónde debe vivir esta responsabilidad?**
   Alternativas a evaluar:
   - **(a)** Agregar una excepción explícita en SPEC 7: recibir "disponible" para una reserva cuyo último estado registrado era "pendiente" y que tiene una autorización sin capturar, dispara la cancelación de dicha autorización (o su marcado para conciliación).
   - **(b)** Resolverlo enteramente dentro de SPEC 5/6, por ejemplo mediante un mecanismo de conciliación periódica independiente de las notificaciones de estado de Reservas (similar en espíritu a RF-014/RNF-005, pero disparado por el propio vencimiento del bloqueo temporal en vez de por la vigencia técnica de la Pasarela).
   - **(c)** Determinar que no es necesaria una acción explícita del sistema porque la propia vigencia de la autorización ante la Pasarela (RF-014) siempre expira razonablemente rápido y "cubre" el caso — y entonces la frase del contexto ("debe cancelarse o quedar bajo conciliación") ya estaría satisfecha por RF-014, sin necesitar un nuevo requisito. (Esto requeriría confirmar que la vigencia de la autorización de la Pasarela es siempre corta/comparable al bloqueo de 15 minutos, lo cual no está garantizado en el contexto ni en las SPECs.)

4. **¿Qué información necesitaría el sistema para actuar?**
   Si se opta por (a), ¿el Módulo 2 debe notificar explícitamente "disponible" incluso cuando la reserva nunca llegó a "pendiente" (por ejemplo, un bote que nunca se reservó)? ¿O el sistema solo debe reaccionar cuando el estado anterior registrado internamente era "pendiente"? Esto debe quedar explícito para no generar cancelaciones sobre reservas que jamás tuvieron cobro en curso.

## 4. Por qué esto quedó pendiente y no fue corregido directamente

Se trata de una decisión de diseño/negocio con varias alternativas razonables (no una simple corrección de redacción o terminología), y una elección equivocada podría introducir cancelaciones indebidas de autorizaciones válidas o, en el otro extremo, dejar fondos retenidos indefinidamente sin conciliar. Por eso se documenta aquí como punto abierto en vez de resolverse unilateralmente dentro de SPEC 7.

## 5. Evidencia

- `contexto-modulo3.md`, sección 2, paso 5 ("Confirmación hacia Reservas").
- `docs/features/007-brindar-estado-de-la-reserva/spec.md`, RF-002, RF-008.
- `docs/features/005-procesar-cobro/spec.md`, RF-014, RNF-005.