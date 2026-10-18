package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;

/**
 * Puerto de entrada de la consulta de registros financieros (UC12).
 * Fuente: UC12 plan §Firmas referenciadas y T011.
 * GAP: firma no publicada por el plan; derivada del caso de uso.
 */
public interface QueryFinancialRecordsUseCase {

    FinancialRecordsPageResult query(QueryFinancialRecordsCommand command);
}
