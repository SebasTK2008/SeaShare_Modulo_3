package com.seashare.seasharem3.application.port.in;

import java.util.UUID;

/**
 * Puerto propio de UC09 para el worker de envío (el worker es un adaptador y solo puede invocar port.in, D-UC09-05).
 * Fuente: UC09 plan T006/T013.
 * GAP: firma no publicada.
 */
public interface SendRefundToGatewayUseCase {

    void send(UUID refundIntentId);
}
