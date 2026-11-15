package com.seashare.seasharem3.infrastructure.adapter.out.fleet;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** Cuerpo mínimo de la consulta batch a Flota. Fuente: contrato flota-consulta-tarifas-base.md §2. */
public record FleetBaseRatesRequestDto(
        @JsonProperty("boat_ids") List<UUID> boatIds) {
}
