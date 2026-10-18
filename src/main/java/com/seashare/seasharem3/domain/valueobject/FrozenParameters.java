package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Referencia a la versión de parámetros usada por el cálculo y valores aplicados resueltos de ella
 * (comisión % y seguro por pasajero); los valores no se copian en la reserva.
 * Fuente: UC04 plan §3.5 y general-plan D-27.
 */
public record FrozenParameters(UUID parametersId, Percentage commissionPct, Money insuranceFeePerPassenger) {

    public FrozenParameters {
        Objects.requireNonNull(parametersId, "parametersId");
        Objects.requireNonNull(commissionPct, "commissionPct");
        Objects.requireNonNull(insuranceFeePerPassenger, "insuranceFeePerPassenger");
    }
}
