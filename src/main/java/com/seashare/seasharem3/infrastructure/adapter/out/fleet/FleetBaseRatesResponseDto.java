package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta batch de Flota. Los campos desconocidos se ignoran para no romper el adaptador si Flota
 * agrega información no contractual. Fuente: contrato flota-consulta-tarifas-base.md §3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FleetBaseRatesResponseDto(
        @JsonProperty("rates") List<BoatBaseRateDto> rates) {
}
