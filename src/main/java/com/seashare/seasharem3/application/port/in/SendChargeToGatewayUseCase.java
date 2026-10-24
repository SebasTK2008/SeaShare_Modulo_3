package com.seashare.seasharem3.application.port.in;

import java.util.UUID;

/**
 * Puerto propio de UC05 para el worker de envío (el adaptador solo puede invocar port.in, general-plan §3.4; D-UC05-03).
 * Fuente: UC05 plan T006/T014.
 * GAP: firma no publicada.
 */
public interface SendChargeToGatewayUseCase {

    void send(UUID chargeIntentId);
}
