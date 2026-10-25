package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;

import java.time.LocalDate;

public record SingleEstimateResponse(
        @JsonProperty("boat_id") BoatId boatId,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("duration_days") int durationDays,
        @JsonProperty("passengers") int passengers,
        @JsonProperty("estimated_total") Money estimatedTotal,
        @JsonProperty("warning") String warning) {
}
