package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record FinancialReportResponse(
        String periodicity, String period,
        @JsonProperty("is_closed") boolean isClosed,
        String scope, @JsonProperty("net_amount") String netAmount,
        @JsonProperty("total_collections") String totalCollections,
        @JsonProperty("total_refunds") String totalRefunds,
        @JsonProperty("total_settlements") String totalSettlements,
        @JsonProperty("total_commissions") String totalCommissions,
        Map<String, Integer> counts, Comparison comparison) {

    public record Comparison(@JsonProperty("previous_period") String previousPeriod,
                              @JsonProperty("absolute_variation") String absoluteVariation,
                              @JsonProperty("percentage_variation") String percentageVariation) {
    }
}
