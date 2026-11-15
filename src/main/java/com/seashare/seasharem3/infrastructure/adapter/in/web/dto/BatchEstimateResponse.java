package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.List;

public record BatchEstimateResponse(
        @JsonProperty("evaluation_date") LocalDate evaluationDate,
        @JsonProperty("duration_days") int durationDays,
        @JsonProperty("passengers") int passengers,
        @JsonProperty("estimates") List<EstimateItem> estimates,
        @JsonProperty("unavailable") List<UnavailableItem> unavailable) {

    public record EstimateItem(
            @JsonProperty("boat_id") BoatId boatId,
            @JsonProperty("estimated_total") Money estimatedTotal) {
    }

    public record UnavailableItem(
            @JsonProperty("boat_id") BoatId boatId,
            @JsonProperty("reason") String reason) {
    }
}
