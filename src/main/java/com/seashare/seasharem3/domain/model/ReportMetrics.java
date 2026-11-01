package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.valueobject.Money;

import java.util.Map;

public record ReportMetrics(Money netAmount, Money totalCollections, Money totalRefunds,
                            Money totalSettlements, Money totalCommissions,
                            Map<String, Integer> counts) {

    public ReportMetrics {
        counts = Map.copyOf(counts);
    }
}
