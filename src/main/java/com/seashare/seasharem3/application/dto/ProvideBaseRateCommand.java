package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.time.LocalDate;
import java.util.List;

/** Firma publicada por UC02 (plan §Firmas referenciadas): lista obligatoria (vacía ⇒ sin consultar a Flota) y fecha evaluada obligatoria. */
public record ProvideBaseRateCommand(List<BoatId> boatIds, LocalDate evaluatedDate) {
}
