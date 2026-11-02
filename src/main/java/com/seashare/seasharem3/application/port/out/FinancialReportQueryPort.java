package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.domain.model.ReportMetrics;

/**
 * Agregación de solo lectura sobre las 4 tablas inmutables para el informe (UC13).
 * Fuente: UC13 plan §Firmas referenciadas y T002.
 *
 * <p>El puerto expone únicamente la agregación: es la forma en que el servicio consume la fuente y
 * evita que la lógica de cálculo exista dos veces (en el dominio y en el SQL del adaptador).</p>
 */
public interface FinancialReportQueryPort {

    ReportMetrics aggregate(GetFinancialReportCommand command);
}
