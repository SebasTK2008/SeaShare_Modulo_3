package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.util.List;

/** Comando del lote de estimaciones (solo boat_ids). Fuente: UC01 plan T008 y contrato rest/UC01-estimacion-lote.md §3. */
public record EstimateBatchCommand(List<BoatId> boatIds) {
}
