package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Resultado del lote: cada ID único figura en estimates o unavailable, nunca en ambos",
        requiredProperties = {"evaluation_date", "duration_days", "passengers", "estimates", "unavailable"})
public record BatchEstimateResponse(
        @Schema(description = "Fecha actual de la petición en la zona de negocio", format = "date", example = "2026-10-03")
        @JsonProperty("evaluation_date") LocalDate evaluationDate,
        @Schema(description = "Duración fija de 1 día", allowableValues = {"1"}, example = "1")
        @JsonProperty("duration_days") int durationDays,
        @Schema(description = "Cantidad fija de 1 pasajero", allowableValues = {"1"}, example = "1")
        @JsonProperty("passengers") int passengers,
        @Schema(description = "Embarcaciones con estimación; vacío si no hay ninguna")
        @JsonProperty("estimates") List<EstimateItem> estimates,
        @Schema(description = "Embarcaciones inexistentes o sin tarifa; Flota no distingue esos motivos")
        @JsonProperty("unavailable") List<UnavailableItem> unavailable) {

    @Schema(name = "BatchEstimateItem", requiredProperties = {"boat_id", "estimated_total"})
    public record EstimateItem(
            @Schema(description = "Embarcación estimada", implementation = String.class, type = "string", format = "uuid",
                    example = "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")
            @JsonProperty("boat_id") BoatId boatId,
            @Schema(description = "Alquiler más seguro en COP, sin depósito; string de 2 decimales",
                    implementation = String.class, type = "string", example = "365000.00")
            @JsonProperty("estimated_total") Money estimatedTotal) {
    }

    @Schema(name = "BatchUnavailableItem", requiredProperties = {"boat_id", "reason"})
    public record UnavailableItem(
            @Schema(description = "Embarcación sin estimación", implementation = String.class, type = "string", format = "uuid",
                    example = "9a1b7c33-2e4f-4b58-8d6a-0c9e5f3a2b02")
            @JsonProperty("boat_id") BoatId boatId,
            @Schema(description = "Sin tarifa disponible en la respuesta de Flota", allowableValues = {"NO_BASE_RATE"},
                    example = "NO_BASE_RATE")
            @JsonProperty("reason") String reason) {
    }
}
