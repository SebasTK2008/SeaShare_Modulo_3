package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;

/**
 * Puerto de entrada del registro del resultado del reembolso (UC09), invocado por el despachador de webhook de UC05.
 * Fuente: UC09 plan T006/T020 y firma publicada en el plan de UC09.
 */
public interface RegisterRefundResultUseCase {

    void register(RefundGatewayResult result);
}
