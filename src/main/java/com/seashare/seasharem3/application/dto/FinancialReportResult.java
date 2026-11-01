package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.model.PeriodComparison;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Periodicity;
import com.seashare.seasharem3.domain.valueobject.ReportPeriod;
import com.seashare.seasharem3.domain.valueobject.ReportScope;

import java.util.Map;

/**
 * Informe financiero (neto sin doble comisión, totales, conteos y comparación con el período anterior).
 * Fuente: UC13 plan §Reglas 5–7 y 9; contrato rest/UC13-consultar-informe-financiero.md §4.
 */
public record FinancialReportResult(Periodicity periodicity, ReportPeriod period, boolean isClosed, ReportScope scope,
                                    Money netAmount, Money totalCollections, Money totalRefunds,
                                    Money totalSettlements, Money totalCommissions, Map<String, Integer> counts,
                                    PeriodComparison comparison) {
}
