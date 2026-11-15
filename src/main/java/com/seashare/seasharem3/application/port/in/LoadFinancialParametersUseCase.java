package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.FinancialParametersResult;

/**
 * Puerto de entrada de la consulta de parámetros vigentes (UC11, interno).
 * Fuente: UC11 plan T030 y general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del caso de uso.
 */
public interface LoadFinancialParametersUseCase {

    FinancialParametersResult load();
}
