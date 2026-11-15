package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.time.LocalDate;

/** Resultado de la estimación individual, con advertencia obligatoria. Fuente: UC01 plan T014 y contrato rest/UC01-estimacion-individual.md §4. */
public record SingleEstimateResult(BoatId boatId, LocalDate startDate, LocalDate endDate, int durationDays,
                                   int passengers, Money estimatedTotal, String warning) {
}
