package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;

import java.util.Map;
import java.util.Set;

/** Firma publicada en UC02 plan T015: devuelve solo las embarcaciones con tarifa válida. */
public interface FleetRatePort {

    Map<BoatId, BaseRate> findBaseRates(Set<BoatId> boatIds);
}
