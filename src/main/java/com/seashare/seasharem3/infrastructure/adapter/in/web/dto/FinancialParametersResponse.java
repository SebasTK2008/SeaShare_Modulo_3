package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body del {@code GET /api/v1/financial-parameters}: versión vigente + dos datos de solo lectura.
 * Los cuatro parámetros configurables se serializan con la escala interna de 4 decimales para
 * preservar exactamente el valor almacenado y la idempotencia del PUT (A1, D-UC11-12). El resto de
 * salidas monetarias del sistema conserva la escala de 2 decimales de contracts/README §3.1.
 * Fuente: UC11 plan T032; contrato {@code rest/UC11-obtener-parametros-financieros.md} §4 [CONV].
 */
public record FinancialParametersResponse(

        @JsonProperty("version_number")
        long versionNumber,

        @JsonProperty("platform_commission_percentage")
        String platformCommissionPercentage,

        @JsonProperty("insurance_fee_per_passenger")
        String insuranceFeePerPassenger,

        @JsonProperty("weekend_increase_percentage")
        String weekendIncreasePercentage,

        @JsonProperty("high_season_increase_percentage")
        String highSeasonIncreasePercentage,

        @JsonProperty("guarantee_deposit_rule")
        String guaranteeDepositRule,

        @JsonProperty("high_season_windows")
        List<String> highSeasonWindows) {
}
