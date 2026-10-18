package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.FinancialRecordsPageResult;
import com.seashare.seasharem3.application.dto.QueryFinancialRecordsCommand;

/**
 * Consulta de solo lectura sobre la vista v_financial_records (UC12).
 * Fuente: UC12 plan T004 y §Firmas referenciadas.
 * GAP: firma no publicada; se deriva la consulta paginada con el command del caso de uso.
 */
public interface FinancialRecordQueryPort {

    FinancialRecordsPageResult query(QueryFinancialRecordsCommand command);
}
