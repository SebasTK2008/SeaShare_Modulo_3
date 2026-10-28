package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.FinancialParametersCommand;
import com.seashare.seasharem3.application.dto.SaveFinancialParametersResult;

/**
 * Puerto de entrada del guardado versionado de parámetros (UC11, interno).
 * Fuente: UC11 plan T012/T017 y general-plan §3.5.
 * Devuelve solo la versión vigente; los datos derivados de solo lectura pertenecen a
 * {@code LoadFinancialParametersUseCase}.
 */
public interface SaveFinancialParametersUseCase {

    SaveFinancialParametersResult save(FinancialParametersCommand command);
}
