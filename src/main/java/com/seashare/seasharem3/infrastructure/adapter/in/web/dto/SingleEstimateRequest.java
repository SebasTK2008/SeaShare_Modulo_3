package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Body del POST /api/v1/estimates/individual.
 * Fuente: UC01 contrato est/UC01-estimacion-individual.md §2-§3.
 */
public record SingleEstimateRequest(
        @NotNull(message = "boat_id is required")
        @JsonProperty("boat_id")
        BoatId boatId,

        @NotNull(message = "start_date is required")
        @JsonProperty("start_date")
        LocalDate startDate,

        @NotNull(message = "end_date is required")
        @JsonProperty("end_date")
        LocalDate endDate,

        @NotNull(message = "passengers is required")
        @Positive(message = "passengers must be at least 1")
        @JsonProperty("passengers")
        Integer passengers) {
}
