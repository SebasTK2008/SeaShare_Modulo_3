package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Body del {@code GET /api/v1/financial-parameters}: versión vigente + dos datos de solo lectura.
 * Los cuatro parámetros configurables se serializan con la escala interna de 4 decimales para
 * preservar exactamente el valor almacenado y la idempotencia del PUT (A1, D-UC11-12). El resto de
 * salidas monetarias del sistema conserva la escala de 2 decimales de contracts/README §3.1.
 * Fuente: UC11 plan T032; contrato {@code rest/UC11-obtener-parametros-financieros.md} §4 [CONV].
 */
@Schema(description = "Configuración vigente: conserva 4 decimales para permitir PUT → GET → PUT sin pérdida de precisión",
        requiredProperties = {"version_number", "platform_commission_percentage", "insurance_fee_per_passenger",
                "weekend_increase_percentage", "high_season_increase_percentage", "guarantee_deposit_rule", "high_season_windows"})
public record FinancialParametersResponse(

        @Schema(description = "Número secuencial de la versión vigente", format = "int64", example = "7")
        @JsonProperty("version_number")
        long versionNumber,

        @Schema(description = "Comisión de plataforma, 4 decimales", example = "15.0000")
        @JsonProperty("platform_commission_percentage")
        String platformCommissionPercentage,

        @Schema(description = "Seguro por pasajero en COP, 4 decimales", example = "15000.0000")
        @JsonProperty("insurance_fee_per_passenger")
        String insuranceFeePerPassenger,

        @Schema(description = "Incremento de fin de semana, 4 decimales", example = "10.0000")
        @JsonProperty("weekend_increase_percentage")
        String weekendIncreasePercentage,

        @Schema(description = "Incremento de temporada alta, 4 decimales", example = "25.0000")
        @JsonProperty("high_season_increase_percentage")
        String highSeasonIncreasePercentage,

        @Schema(description = "Regla informativa, no configurable por PUT", example = "10% de la tarifa base diaria")
        @JsonProperty("guarantee_deposit_rule")
        String guaranteeDepositRule,

        @Schema(description = "Ventanas fijas MM-dd→MM-dd y ventana móvil semana-santa; solo lectura",
                example = "[\"11-15→01-15\",\"06-01→07-30\",\"semana-santa\",\"10-05→10-12\"]")
        @JsonProperty("high_season_windows")
        List<String> highSeasonWindows) {
}
