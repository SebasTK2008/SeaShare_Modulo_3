package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página de operaciones confirmadas; no incluye intenciones pendientes o fallidas",
        requiredProperties = {"content", "page_number", "page_size", "total_elements", "total_pages"})
public record FinancialRecordsResponse(
        @Schema(description = "Registros en orden created_at DESC y record_id DESC; puede estar vacío")
        List<FinancialRecordResponse> content,
        @Schema(description = "Página actual, desde 1", minimum = "1", example = "1")
        @JsonProperty("page_number") int pageNumber,
        @Schema(description = "Tamaño de página utilizado", example = "10")
        @JsonProperty("page_size") int pageSize,
        @Schema(description = "Registros que cumplen los filtros", minimum = "0", format = "int64", example = "1")
        @JsonProperty("total_elements") long totalElements,
        @Schema(description = "Total de páginas; 0 si no hay registros", minimum = "0", example = "1")
        @JsonProperty("total_pages") int totalPages) {

    @Schema(name = "FinancialRecord", description = "Registro financiero inmutable",
            requiredProperties = {"record_type", "reservation_id", "owner_id", "boat_id", "amount", "created_at", "external_reference"})
    public record FinancialRecordResponse(
            @Schema(description = "Tipo de operación confirmada", allowableValues = {"CHARGE", "REFUND", "SETTLEMENT", "COMMISSION"},
                    example = "CHARGE")
            @JsonProperty("record_type") String recordType,
            @Schema(description = "Reserva asociada", format = "uuid", example = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")
            @JsonProperty("reservation_id") String reservationId,
            @Schema(description = "Propietario asociado", format = "uuid", example = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
            @JsonProperty("owner_id") String ownerId,
            @Schema(description = "Embarcación asociada", format = "uuid", example = "3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01")
            @JsonProperty("boat_id") String boatId,
            @Schema(description = "Monto en COP como string de 2 decimales", example = "990000.00")
            String amount,
            @Schema(description = "Instante de creación en UTC", format = "date-time", example = "2026-12-05T14:30:00Z")
            @JsonProperty("created_at") String createdAt,
            @Schema(description = "Referencia de la pasarela, o null si no está disponible",
                    types = {"string", "null"}, nullable = true, example = "TXN-987654321")
            @JsonProperty("external_reference") String externalReference) {
    }
}
