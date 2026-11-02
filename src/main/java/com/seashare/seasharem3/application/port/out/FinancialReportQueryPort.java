package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.domain.model.FinancialRecord;
import com.seashare.seasharem3.domain.model.ReportMetrics;
import com.seashare.seasharem3.domain.service.FinancialAggregator;

import java.util.List;

/**
 * Agregación de solo lectura sobre las 4 tablas inmutables para el informe (UC13).
 * Fuente: UC13 plan §Firmas referenciadas y T002.
 */
public interface FinancialReportQueryPort {

    List<FinancialRecord> query(GetFinancialReportCommand command);

    /**
     * Agrega en el adaptador cuando la fuente lo permite. La implementación por defecto conserva
     * compatibilidad con dobles de prueba y adaptadores que aún exponen registros.
     */
    default ReportMetrics aggregate(GetFinancialReportCommand command) {
        return new FinancialAggregator().aggregate(query(command), command.scope());
    }
}
