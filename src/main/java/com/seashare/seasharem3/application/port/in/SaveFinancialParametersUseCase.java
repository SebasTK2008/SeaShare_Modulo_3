package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.FinancialParametersResult;

/**
 * Puerto de entrada del guardado versionado de parámetros (UC11, interno).
 * Fuente: UC11 plan T012/T017 y general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del caso de uso (devuelve la versión vigente).
 */
public interface SaveFinancialParametersUseCase {

    FinancialParametersResult save(FinancialParametersCommand command);
}
