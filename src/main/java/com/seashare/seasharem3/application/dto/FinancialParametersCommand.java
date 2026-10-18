package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;

/**
 * Los cuatro campos obligatorios de la configuración financiera.
 * Fuente: UC11 plan T012 y contrato rest/UC11-guardar-parametros-financieros.md §4.
 * GAP: tipos internos derivados (porcentajes 0–100 como Percentage y seguro como Money).
 */
public record FinancialParametersCommand(Percentage commissionPct, Money insuranceFeePerPassenger,
                                         Percentage weekendIncreasePct, Percentage highSeasonIncreasePct) {
}
