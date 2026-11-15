package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.util.UUID;

/**
 * Firma publicada por UC02 (plan §Firmas referenciadas): tarifa final ya ajustada por la tarifa dinámica.
 * parametersId: versión de financial_parameters usada en el ajuste; null si la fecha fue regular.
 */
public record BaseRateResult(BoatId boatId, BaseRate baseRate, UUID parametersId) {
}
