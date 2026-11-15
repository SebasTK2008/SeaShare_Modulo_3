package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;

/**
 * Puerto de entrada del registro del resultado de la liquidación (UC10), invocado por webhook/worker (T011).
 * Fuente: UC10 plan T011 y §Puerto interno (firma congelada); general-plan §3.5. [CONV] núcleo compartido: usa el
 * resultado de la pasarela como entrada, igual que {@link RegisterChargeResultUseCase} y
 * {@link RegisterRefundResultUseCase}.
 */
public interface RegisterSettlementResultUseCase {

    void register(GatewaySettlementResult result);
}
