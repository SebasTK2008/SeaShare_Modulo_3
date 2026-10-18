package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.BatchEstimateResult;
import com.seashare.seasharem3.application.dto.EstimateBatchCommand;

/**
 * Puerto de entrada del lote de estimaciones (UC01).
 * Fuente: general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del nombre del caso de uso.
 */
public interface EstimateBatchUseCase {

    BatchEstimateResult estimate(EstimateBatchCommand command);
}
