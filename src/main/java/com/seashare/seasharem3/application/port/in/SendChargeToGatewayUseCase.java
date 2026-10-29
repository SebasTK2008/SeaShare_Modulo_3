package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.ChargeCommand;

/**
 * Puerto propio de UC05 para el worker de envío (el adaptador solo puede invocar port.in,
 * general-plan §3.4; D-UC05-03). El worker entrega el comando de la cola interna: la intención que
 * debe cobrar y el correo del pagador que exige el contrato de la pasarela; el token del medio de
 * pago se lee de la propia intención.
 * Fuente: UC05 plan T006/T014.
 */
public interface SendChargeToGatewayUseCase {

    void send(ChargeCommand command);
}
