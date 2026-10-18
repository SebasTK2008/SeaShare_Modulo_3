package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;

/**
 * Agregación de solo lectura sobre las 4 tablas inmutables para el informe (UC13).
 * Fuente: UC13 plan §Firmas referenciadas y T002.
 * GAP: firma no publicada; se deriva la consulta con el command del caso de uso.
 */
public interface FinancialReportQueryPort {

    FinancialReportResult query(GetFinancialReportCommand command);
}
