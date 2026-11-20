package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Estimación individual sin depósito de garantía ni ajustes posteriores",
        requiredProperties = {"boat_id", "start_date", "end_date", "duration_days", "passengers", "estimated_total", "warning"})
public record SingleEstimateResponse(
        @Schema(description = "Embarcación estimada", implementation = String.class, type = "string", format = "uuid",
                example = "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")
        @JsonProperty("boat_id") BoatId boatId,
        @Schema(description = "Fecha de inicio recibida", format = "date", example = "2026-12-20")
        @JsonProperty("start_date") LocalDate startDate,
        @Schema(description = "Fecha de fin recibida", format = "date", example = "2026-12-22")
        @JsonProperty("end_date") LocalDate endDate,
        @Schema(description = "Días inclusivos entre inicio y fin", minimum = "1", example = "3")
        @JsonProperty("duration_days") int durationDays,
        @Schema(description = "Pasajeros recibidos", minimum = "1", example = "4")
        @JsonProperty("passengers") int passengers,
        @Schema(description = "Alquiler más seguro náutico, en COP; string de 2 decimales",
                implementation = String.class, type = "string", example = "1372500.00")
        @JsonProperty("estimated_total") Money estimatedTotal,
        @Schema(description = "Advertencia obligatoria que el consumidor debe mostrar sin reinterpretarla",
                example = "Valor estimado. El valor incluye el seguro náutico, pero no incluye el depósito de garantía ni penalidades o ajustes derivados de cambios posteriores de la reserva")
        @JsonProperty("warning") String warning) {
}
