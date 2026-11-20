package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Body del POST /api/v1/estimates/individual.
 *
 * <p>El rango de fechas NO se valida aqui: {@code EstimateSingleService} lo comprueba contra
 * {@code ClockPort.today()} y responde {@code 400 INVALID_DATE_RANGE} (contrato individual §3).</p>
 *
 * Fuente: UC01 contrato rest/UC01-estimacion-individual.md §2-§3.
 */
@Schema(description = "Estimación informativa para una embarcación, fechas y pasajeros",
        requiredProperties = {"boat_id", "start_date", "end_date", "passengers"},
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SingleEstimateRequest(
        @Schema(description = "Embarcación a estimar", implementation = String.class, type = "string", format = "uuid",
                example = "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")
        @NotNull(message = "boat_id is required")
        @JsonProperty("boat_id")
        BoatId boatId,

        @Schema(description = "Inicio no anterior a hoy en la zona de negocio (seashare.timezone, por defecto America/Bogota); "
                + "determina la tarifa de toda la estancia",
                format = "date", example = "2026-12-20")
        @NotNull(message = "start_date is required")
        @JsonProperty("start_date")
        LocalDate startDate,

        @Schema(description = "Fin igual o posterior al inicio. Ambas fechas se cuentan: del 20 al 22 son 3 días",
                format = "date", example = "2026-12-22")
        @NotNull(message = "end_date is required")
        @JsonProperty("end_date")
        LocalDate endDate,

        @Schema(description = "Pasajeros; UC01 no comprueba la capacidad de la embarcación", minimum = "1", example = "4")
        @NotNull(message = "passengers is required")
        @Positive(message = "passengers must be at least 1")
        @JsonProperty("passengers")
        Integer passengers) {
}
