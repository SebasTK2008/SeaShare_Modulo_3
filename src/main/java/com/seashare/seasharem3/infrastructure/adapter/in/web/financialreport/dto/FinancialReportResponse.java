package com.seashare.seasharem3.infrastructure.adapter.in.web.financialreport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Agregados de registros confirmados e informe comparativo; importes en COP como strings de 2 decimales",
        requiredProperties = {"periodicity", "period", "is_closed", "scope", "net_amount", "total_collections",
                "total_refunds", "total_settlements", "total_commissions", "counts", "comparison"})
public record FinancialReportResponse(
        @Schema(description = "Periodicidad consultada", allowableValues = {"SEMI_MONTHLY", "MONTHLY", "QUARTERLY"},
                example = "MONTHLY")
        String periodicity,
        @Schema(description = "Período en formato canónico según la periodicidad", example = "2026-10")
        String period,
        @Schema(description = "Indica si el período ya finalizó", example = "true")
        @JsonProperty("is_closed") boolean isClosed,
        @Schema(description = "Alcance declarado, no autenticado", allowableValues = {"OWNER", "PLATFORM"}, example = "PLATFORM")
        String scope,
        @Schema(description = "PLATFORM: cobros - reembolsos - liquidaciones. OWNER: total de liquidaciones; puede ser negativo",
                example = "150000.00")
        @JsonProperty("net_amount") String netAmount,
        @Schema(description = "Total de cobros confirmados", example = "1150000.00")
        @JsonProperty("total_collections") String totalCollections,
        @Schema(description = "Total de reembolsos confirmados", example = "100000.00")
        @JsonProperty("total_refunds") String totalRefunds,
        @Schema(description = "Total de liquidaciones a propietarios", example = "900000.00")
        @JsonProperty("total_settlements") String totalSettlements,
        @Schema(description = "Total de comisiones, informativo; no se descuenta de nuevo del neto", example = "135000.00")
        @JsonProperty("total_commissions") String totalCommissions,
        @Schema(description = "Conteos por tipo de registro confirmado", example = "{\"CHARGE\":10,\"REFUND\":2,\"SETTLEMENT\":8,\"COMMISSION\":8}")
        Map<String, Integer> counts,
        @Schema(description = "Comparación con el período inmediatamente anterior de la misma periodicidad")
        Comparison comparison) {

    @Schema(name = "FinancialReportComparison", requiredProperties = {"previous_period", "absolute_variation", "percentage_variation"})
    public record Comparison(
            @Schema(description = "Período de comparación", example = "2026-09")
            @JsonProperty("previous_period") String previousPeriod,
            @Schema(description = "Variación absoluta del neto o ganancia, en COP; admite signo", example = "25000.00")
            @JsonProperty("absolute_variation") String absoluteVariation,
            @Schema(description = "Variación porcentual con 2 decimales; null si el valor anterior es cero",
                    types = {"string", "null"}, nullable = true, example = "20.00")
            @JsonProperty("percentage_variation") String percentageVariation) {
    }
}
