package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.model.FinancialRecord;
import com.seashare.seasharem3.domain.model.ReportMetrics;
import com.seashare.seasharem3.domain.valueobject.FinancialRecordType;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReportScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FinancialAggregator {

    public ReportMetrics aggregate(List<FinancialRecord> records, ReportScope scope) {
        Money collections = Money.ZERO;
        Money refunds = Money.ZERO;
        Money settlements = Money.ZERO;
        Money commissions = Money.ZERO;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (FinancialRecordType type : FinancialRecordType.values()) counts.put(type.name(), 0);
        for (FinancialRecord record : records) {
            counts.compute(record.recordType().name(), (key, value) -> value + 1);
            switch (record.recordType()) {
                case CHARGE -> collections = collections.add(record.amount());
                case REFUND -> refunds = refunds.add(record.amount());
                case SETTLEMENT -> settlements = settlements.add(record.amount());
                case COMMISSION -> commissions = commissions.add(record.amount());
            }
        }
        Money net = scope == ReportScope.OWNER ? settlements
                : collections.subtract(refunds).subtract(settlements);
        return new ReportMetrics(net, collections, refunds, settlements, commissions, counts);
    }
}
