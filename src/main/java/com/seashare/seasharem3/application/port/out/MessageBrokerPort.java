package com.seashare.seasharem3.application.port.out;

/**
 * Publicación directa de payloads en el broker, usada exclusivamente por el relay del outbox
 * (los casos de uso publican vía {@link OutboxPort} dentro de su transacción).
 * Fuente: general-plan D-08 y §3.5. [CONV] núcleo compartido.
 */
public interface MessageBrokerPort {

    void publish(String exchange, String routingKey, String payload);
}
