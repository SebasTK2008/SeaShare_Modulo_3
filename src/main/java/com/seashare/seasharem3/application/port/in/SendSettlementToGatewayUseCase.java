package com.seashare.seasharem3.application.port.in;

import java.util.UUID;

/**
 * Puerto propio de UC10 para el worker de envío (el worker es un adaptador y solo puede invocar port.in,
 * general-plan §3.4). Fuente: UC10 plan T009/T010 y §Puerto interno (firma congelada).
 */
public interface SendSettlementToGatewayUseCase {

    void send(UUID settlementIntentId);
}
