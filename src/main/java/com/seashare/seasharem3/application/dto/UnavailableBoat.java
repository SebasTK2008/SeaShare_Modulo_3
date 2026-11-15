package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;

/**
 * Embarcacion sin estimacion reportada en {@code unavailable[]}.
 *
 * <p>{@link #NO_BASE_RATE} es el unico motivo: UC02 (y el contrato de Flota) no distingue entre una
 * embarcacion inexistente y una reconocida sin tarifa, ambas se omiten del resultado. UC01 reporta
 * toda ausencia con este motivo [decision A4, contrato rest/UC01-estimacion-lote.md §4].</p>
 */
public record UnavailableBoat(BoatId boatId, String reason) {

    /** Motivo unico de indisponibilidad en el lote. */
    public static final String NO_BASE_RATE = "NO_BASE_RATE";
}
