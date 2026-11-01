package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FinancialRecordsResponse(
        List<FinancialRecordResponse> content,
        @JsonProperty("page_number") int pageNumber,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages) {

    public record FinancialRecordResponse(
            @JsonProperty("record_type") String recordType,
            @JsonProperty("reservation_id") String reservationId,
            @JsonProperty("owner_id") String ownerId,
            @JsonProperty("boat_id") String boatId,
            String amount,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("external_reference") String externalReference) {
    }
}
