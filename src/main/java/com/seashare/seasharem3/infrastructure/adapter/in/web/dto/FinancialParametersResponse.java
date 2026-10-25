package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;

import java.util.List;

/**
 * Body del {@code GET /api/v1/financial-parameters}: versión vigente + dos datos de solo lectura.
 * Jackson serializa {@link Percentage} y {@link Money} como string decimal de 2 decimales HALF_UP
 * según los serializadores de {@code JacksonConfig} [SPEC RNF-002; contracts/README §3.1].
 * Fuente: UC11 plan T032; contrato {@code rest/UC11-obtener-parametros-financieros.md} §4 [CONV].
 */
public record FinancialParametersResponse(

        @JsonProperty("version_number")
        long versionNumber,

        @JsonProperty("platform_commission_percentage")
        Percentage platformCommissionPercentage,

        @JsonProperty("insurance_fee_per_passenger")
        Money insuranceFeePerPassenger,

        @JsonProperty("weekend_increase_percentage")
        Percentage weekendIncreasePercentage,

        @JsonProperty("high_season_increase_percentage")
        Percentage highSeasonIncreasePercentage,

        @JsonProperty("guarantee_deposit_rule")
        String guaranteeDepositRule,

        @JsonProperty("high_season_windows")
        List<String> highSeasonWindows) {
}

