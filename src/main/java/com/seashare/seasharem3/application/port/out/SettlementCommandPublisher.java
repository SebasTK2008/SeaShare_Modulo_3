package com.seashare.seasharem3.application.port.out;

import java.util.UUID;

/**
 * Publica el comando de envio de una liquidacion en el outbox transaccional.
 * Fuente: UC10 plan T010.
 */
public interface SettlementCommandPublisher {

    void publish(UUID settlementIntentId);
}
