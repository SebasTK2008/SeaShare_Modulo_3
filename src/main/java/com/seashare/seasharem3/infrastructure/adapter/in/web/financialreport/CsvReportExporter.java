package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import org.springframework.stereotype.Component;

@Component
public class CsvReportExporter {

    private final FinancialReportResponseMapper responseMapper;

    public CsvReportExporter(FinancialReportResponseMapper responseMapper) {
        this.responseMapper = responseMapper;
    }

    public String export(FinancialReportExportResult result) {
        var report = responseMapper.toResponse(result.report());
        return "Fecha de Generacion,Alcance,Periodicidad,Periodo,Neto Generado,Total Cobros,"
                + "Total Reembolsos,Total Dispersiones,Total Comisiones\n"
                + result.generatedAt() + "," + report.scope() + "," + report.periodicity() + ","
                + report.period() + "," + report.netAmount() + ","
                + report.totalCollections() + "," + report.totalRefunds() + ","
                + report.totalSettlements() + "," + report.totalCommissions() + "\n";
    }
}
