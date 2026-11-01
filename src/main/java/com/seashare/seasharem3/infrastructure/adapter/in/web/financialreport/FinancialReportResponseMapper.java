package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport;

import com.seashare.seasharem3.application.dto.FinancialReportResult;
import com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.dto.FinancialReportResponse;
import org.springframework.stereotype.Component;

@Component
public class FinancialReportResponseMapper {

    public FinancialReportResponse toResponse(FinancialReportResult result) {
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
}
