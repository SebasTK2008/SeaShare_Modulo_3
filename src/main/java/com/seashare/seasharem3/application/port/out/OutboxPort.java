package com.seashare.seasharem3.application.port.out;

/** Firma publicada en UC11 plan §Núcleo compartido: publica en outbox_message dentro de la transacción del llamador. */
public interface OutboxPort {

    void publish(String exchange, String routingKey, String payload);
}
