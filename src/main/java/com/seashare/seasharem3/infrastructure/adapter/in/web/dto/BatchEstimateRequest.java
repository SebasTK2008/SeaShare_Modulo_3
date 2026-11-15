package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body del POST /api/v1/estimates/batch.
 * Solo acepta boat_ids; cualquier campo extra produce 400 VALIDATION_ERROR.
 *
 * <p>El tamano maximo NO se valida aqui: es configurable
 * ({@code seashare.estimates.max-batch-size}) y lo aplica {@code EstimateBatchService} para poder
 * responder {@code 400 BATCH_SIZE_EXCEEDED} en lugar de un generico VALIDATION_ERROR.</p>
 *
 * <p>Fuente: UC01 contrato rest/UC01-estimacion-lote.md §2-§3 y §5.</p>
 */
public record BatchEstimateRequest(
        @NotNull(message = "boat_ids is required")
        @JsonProperty("boat_ids")
        List<@NotNull(message = "boat_ids must not contain null elements") BoatId> boatIds) {
}
