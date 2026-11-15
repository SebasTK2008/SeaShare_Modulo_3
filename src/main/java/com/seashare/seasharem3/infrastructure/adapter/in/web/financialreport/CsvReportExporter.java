package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.FinancialReportExportResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;

@Component
public class CsvReportExporter {

    private final FinancialReportResponseMapper responseMapper;

    public CsvReportExporter(FinancialReportResponseMapper responseMapper) {
        this.responseMapper = responseMapper;
    }

    public String export(FinancialReportExportResult result) {
        var report = responseMapper.toResponse(result.report());
        StringWriter output = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder()
                .setHeader("Fecha de Generacion", "Alcance", "Periodicidad", "Periodo",
                        "Neto Generado", "Total Cobros", "Total Reembolsos", "Total Dispersiones",
                        "Total Comisiones", "Count Charge", "Count Refund", "Count Settlement",
                        "Count Commission", "Periodo Anterior", "Variacion Absoluta",
                        "Variacion Porcentual")
                .get())) {
            var comparison = report.comparison();
            printer.printRecord(result.generatedAt(), report.scope(), report.periodicity(), report.period(),
                    report.netAmount(), report.totalCollections(), report.totalRefunds(),
                    report.totalSettlements(), report.totalCommissions(),
                    count(report.counts(), "CHARGE"), count(report.counts(), "REFUND"),
                    count(report.counts(), "SETTLEMENT"), count(report.counts(), "COMMISSION"),
                    comparison.previousPeriod(), comparison.absoluteVariation(),
                    comparison.percentageVariation());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not export financial report as CSV", exception);
        }
        return output.toString();
    }

    private static int count(java.util.Map<String, Integer> counts, String type) {
        return counts.getOrDefault(type, 0);
    }
}
