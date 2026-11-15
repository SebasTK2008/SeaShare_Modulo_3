package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.EstimateSingleCommand;
import com.seashare.seasharem3.application.dto.SingleEstimateResult;

/**
 * Puerto de entrada de la estimación individual (UC01).
 * Fuente: general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del nombre del caso de uso.
 */
public interface EstimateSingleUseCase {

    SingleEstimateResult estimate(EstimateSingleCommand command);
}
