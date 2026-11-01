package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.application.dto.GetFinancialReportCommand;
import com.seashare.seasharem3.application.exception.ApiFieldError;
import com.seashare.seasharem3.application.exception.ContractException;
import com.seashare.seasharem3.application.exception.ErrorCode;
import com.seashare.seasharem3.application.port.in.ExportFinancialReportUseCase;
import com.seashare.seasharem3.application.port.in.GetFinancialReportUseCase;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.dto.FinancialReportResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-report")
public class FinancialReportController {

    private final GetFinancialReportUseCase getUseCase;
    private final ExportFinancialReportUseCase exportUseCase;
    private final CsvReportExporter exporter;

    public FinancialReportController(GetFinancialReportUseCase getUseCase,
                                     ExportFinancialReportUseCase exportUseCase,
                                     CsvReportExporter exporter) {
        this.getUseCase = getUseCase;
        this.exportUseCase = exportUseCase;
        this.exporter = exporter;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FinancialReportResponse> get(
            @RequestParam String scope, @RequestParam String periodicity,
            @RequestParam(name = "owner_id", required = false) String ownerId,
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(toResponse(getUseCase.get(
                command(scope, periodicity, ownerId, period))));
    }

    @GetMapping(path = "/export", produces = "text/csv")
    public ResponseEntity<String> export(
            @RequestParam String scope, @RequestParam String periodicity,
            @RequestParam(name = "owner_id", required = false) String ownerId,
            @RequestParam String period) {
        var result = exportUseCase.export(command(scope, periodicity, ownerId, period));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("informe_financiero_" + period + ".csv").build());
        return ResponseEntity.ok().headers(headers).body(exporter.export(result));
    }

    private static GetFinancialReportCommand command(String scope, String periodicity,
                                                      String ownerId, String period) {
        Periodicity parsedPeriodicity = parseEnum(periodicity, Periodicity.class, "periodicity");
        return new GetFinancialReportCommand(parsedPeriodicity,
                parseEnum(scope, ReportScope.class, "scope"), parseOwner(ownerId),
                period == null ? null : new ReportPeriod(parsedPeriodicity, period));
    }

    private static FinancialReportResponse toResponse(FinancialReportResult result) {
        var comparison = result.comparison();
        return new FinancialReportResponse(result.periodicity().name(), result.period().value(),
                result.isClosed(), result.scope().name(), amount(result.netAmount()),
                amount(result.totalCollections()), amount(result.totalRefunds()),
                amount(result.totalSettlements()), amount(result.totalCommissions()), result.counts(),
                new FinancialReportResponse.Comparison(comparison.previousPeriod().value(),
                        amount(comparison.absoluteVariation()),
                        comparison.percentageVariation() == null ? null
                                : comparison.percentageVariation().setScale(2).toPlainString()));
    }

    private static String amount(com.seashare.seasharem3.domain.valueobject.Money money) {
        return money.toOutputAmount().toPlainString();
    }

    private static OwnerId parseOwner(String value) {
        if (value == null) return null;
        try {
            return new OwnerId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw validation("owner_id", "Value must be a UUID.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw validation(field, "Unsupported value.");
        }
    }

    private static ContractException validation(String field, String message) {
        return new ContractException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new ApiFieldError(field, message)));
    }
}
