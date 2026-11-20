package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Estimación de un lote: solo se acepta boat_ids", requiredProperties = {"boat_ids"},
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record BatchEstimateRequest(
        @ArraySchema(arraySchema = @Schema(description = "Acepta lista vacía y duplicados (se deduplican). "
                + "Máximo seashare.estimates.max-batch-size, por defecto 50; el límite cuenta los IDs recibidos."),
                schema = @Schema(implementation = String.class, type = "string", format = "uuid",
                        example = "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01"))
        @NotNull(message = "boat_ids is required")
        @JsonProperty("boat_ids")
        List<@NotNull(message = "boat_ids must not contain null elements") BoatId> boatIds) {
}
