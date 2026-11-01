package com.seashare.seasharem3.domain.service;

import com.seashare.seasharem3.domain.model.PeriodComparison;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class VariationCalculator {

    public PeriodComparison compare(ReportPeriod previousPeriod, Money current, Money previous) {
        Money absolute = current.subtract(previous);
        BigDecimal percentage = previous.isZero() ? null
                : absolute.amount().multiply(BigDecimal.valueOf(100))
                .divide(previous.amount(), Money.INTERNAL_SCALE, RoundingMode.HALF_UP);
        return new PeriodComparison(previousPeriod, absolute, percentage);
    }
}
