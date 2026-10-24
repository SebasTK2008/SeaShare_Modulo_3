package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;

/** Embarcación sin tarifa base reportada en unavailable[]. Fuente: UC01 plan T008 y contrato rest/UC01-estimacion-lote.md §4. */
public record UnavailableBoat(BoatId boatId, String reason) {
}
