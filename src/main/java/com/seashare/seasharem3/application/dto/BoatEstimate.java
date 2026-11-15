package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;

/** Estimación individual de una embarcación. Fuente: UC01 plan T008. */
public record BoatEstimate(BoatId boatId, Money estimatedTotal) {
}
