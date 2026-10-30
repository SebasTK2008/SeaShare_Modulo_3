package com.seashare.seasharem3.application.port.out;

import java.util.UUID;

/**
 * Publica el comando de envio de un reembolso en el outbox transaccional. El worker solo recibe
 * el identificador de la intencion y lee los datos de la base de datos.
 * Fuente: UC09 plan T008/T013.
 */
public interface RefundCommandPublisher {

    void publish(UUID refundIntentId);
}
