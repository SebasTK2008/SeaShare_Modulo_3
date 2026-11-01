package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;

/**
 * Puerto de entrada de la exportación del informe (UC13), reutilizando el resultado común.
 * Fuente: UC13 plan T010/T011 y general-plan §3.5.
 */
public interface ExportFinancialReportUseCase {

    FinancialReportExportResult export(GetFinancialReportCommand command);
}
