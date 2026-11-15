package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Tarifa base cruda de una embarcación tal como la publica Flota. {@code baseRate} se mantiene como
 * string para que el mapper sea el único punto que decide qué es una tarifa válida.
 * Fuente: contrato flota-consulta-tarifas-base.md §3.
 */
public record BoatBaseRateDto(
        @JsonProperty("boat_id") UUID boatId,
        @JsonProperty("base_rate") String baseRate) {
}
