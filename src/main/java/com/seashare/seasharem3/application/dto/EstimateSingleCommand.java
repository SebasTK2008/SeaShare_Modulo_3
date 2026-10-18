package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.time.LocalDate;

/** Comando de la estimación individual. Fuente: UC01 plan T014 y contrato rest/UC01-estimacion-individual.md §3. */
public record EstimateSingleCommand(BoatId boatId, LocalDate startDate, LocalDate endDate, int passengers) {
}
