package com.seashare.seasharem3.application.dto;

import java.time.LocalDate;
import java.util.List;

/** Resultado del lote de estimaciones. Fuente: UC01 plan T008. */
public record BatchEstimateResult(LocalDate evaluationDate, int durationDays, int passengers,
                                  List<BoatEstimate> estimates, List<UnavailableBoat> unavailable) {
}
