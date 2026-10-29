package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.ChargeCommand;

/**
 * Publica el comando de cobro en la cola interna de UC05 dentro de la transacción del llamador
 * (patrón outbox: la publicación se confirma junto con la intención).
 *
 * <p>Es un puerto propio de UC05: mantiene el enrutado y la serialización del mensaje en la capa de
 * infraestructura, que es donde viven los detalles del broker, y deja el servicio de aplicación
 * libre de dependencias de serialización (general-plan §3.4-2).
 *
 * <p>Fuente: UC05 plan T008 y T014; general-plan D-08.</p>
 */
public interface ChargeCommandPublisher {

    void publish(ChargeCommand command);
}
