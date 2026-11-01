package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.domain.model.FinancialRecord;

import java.util.List;

/**
 * Agregación de solo lectura sobre las 4 tablas inmutables para el informe (UC13).
 * Fuente: UC13 plan §Firmas referenciadas y T002.
 */
public interface FinancialReportQueryPort {

    List<FinancialRecord> query(GetFinancialReportCommand command);
}
