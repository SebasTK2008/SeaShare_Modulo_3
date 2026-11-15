package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;

import java.math.BigDecimal;

public record PeriodComparison(ReportPeriod previousPeriod, Money absoluteVariation,
                               BigDecimal percentageVariation) {
}
