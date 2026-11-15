package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;

/**
 * Puerto de entrada del registro del resultado del cobro (UC05), invocado por webhook y respuesta técnica.
 * Fuente: UC05 plan T006/T018 y general-plan §3.5.
 * GAP: firma no publicada; se deriva el resultado de la pasarela como entrada.
 */
public interface RegisterChargeResultUseCase {

    void register(ChargeGatewayResult result);
}
