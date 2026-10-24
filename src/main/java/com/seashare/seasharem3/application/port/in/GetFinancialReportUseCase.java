package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;

/**
 * Puerto de entrada de la consulta de informe financiero (UC13).
 * Fuente: UC13 plan T008/T009 y general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del caso de uso.
 */
public interface GetFinancialReportUseCase {

    FinancialReportResult get(GetFinancialReportCommand command);
}
