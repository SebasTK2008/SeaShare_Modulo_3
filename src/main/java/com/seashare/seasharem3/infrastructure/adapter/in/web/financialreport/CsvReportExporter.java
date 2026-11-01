package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import com.seashare.seasharem3.domain.valueobject.Money;
import org.springframework.stereotype.Component;

@Component
public class CsvReportExporter {

    public String export(FinancialReportExportResult result) {
        var report = result.report();
        return "Fecha de Generacion,Alcance,Periodicidad,Periodo,Neto Generado,Total Cobros,"
                + "Total Reembolsos,Total Dispersiones,Total Comisiones\n"
                + result.generatedAt() + "," + report.scope() + "," + report.periodicity() + ","
                + report.period().value() + "," + amount(report.netAmount()) + ","
                + amount(report.totalCollections()) + "," + amount(report.totalRefunds()) + ","
                + amount(report.totalSettlements()) + "," + amount(report.totalCommissions()) + "\n";
    }

    private static String amount(Money money) {
        return money.toOutputAmount().toPlainString();
    }
}
