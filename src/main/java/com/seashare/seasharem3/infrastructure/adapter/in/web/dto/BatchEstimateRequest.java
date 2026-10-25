package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body del POST /api/v1/estimates/batch.
 * Solo acepta boat_ids; cualquier campo extra produce 400 VALIDATION_ERROR.
 * Fuente: UC01 contrato est/UC01-estimacion-lote.md §2-§3.
 */
public record BatchEstimateRequest(
        @NotNull(message = "boat_ids is required")
        @Size(max = 50, message = "batch size exceeds maximum of 50")
        @JsonProperty("boat_ids")
        List<BoatId> boatIds) {
}
