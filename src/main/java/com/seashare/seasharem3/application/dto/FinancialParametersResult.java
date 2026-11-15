package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;

import java.util.List;

/**
 * Versión vigente (4 valores + version_number) más los 2 derivados de solo lectura.
 * Fuente: UC11 plan T030 y §Reglas 5; contrato rest/UC11-obtener-parametros-financieros.md §4.
 * GAP: composición exacta del record no publicada; tipos derivados.
 */
public record FinancialParametersResult(long versionNumber, Percentage commissionPct, Money insuranceFeePerPassenger,
                                        Percentage weekendIncreasePct, Percentage highSeasonIncreasePct,
                                        String guaranteeDepositRule, List<String> highSeasonWindows) {
}
