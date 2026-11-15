package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import jakarta.validation.constraints.NotNull;

/**
 * Body del {@code PUT /api/v1/financial-parameters}: los cuatro parámetros configurables.
 * Jackson deserializa {@link Percentage} y {@link Money} usando los deserializadores registrados en
 * {@code JacksonConfig} (string decimal → VO). Los deserializadores lanzarán
 * {@code HttpMessageNotReadableException} si el formato es inválido, que el {@code GlobalExceptionHandler}
 * convierte a {@code 400 VALIDATION_ERROR} [SPEC RF-013, RNF-001, RNF-002; contracts/README §3.1].
 *
 * <p>Los cuatro campos son obligatorios ({@code @NotNull}); un campo ausente o {@code null} en el JSON
 * produce {@code MethodArgumentNotValidException} → {@code 400 VALIDATION_ERROR} con el detalle del campo
 * [SPEC RF-013]. Los rangos (0–100 para porcentajes, ≥ 0 para el seguro) los validan los VOs y el
 * dominio; el {@code GlobalExceptionHandler} extrae el campo del path de Jackson o de
 * {@code FinancialParametersValidationException} y lo publica en {@code errors[]}
 * [SPEC RF-013, UC11 plan §Reglas 2].
 *
 * <p>Fuente: UC11 plan T018; contrato {@code rest/UC11-guardar-parametros-financieros.md} §2–§3 [CONV].
 */
public record FinancialParametersRequest(

        @NotNull(message = "platform_commission_percentage is required")
        @JsonProperty("platform_commission_percentage")
        Percentage platformCommissionPercentage,

        @NotNull(message = "insurance_fee_per_passenger is required")
        @JsonProperty("insurance_fee_per_passenger")
        Money insuranceFeePerPassenger,

        @NotNull(message = "weekend_increase_percentage is required")
        @JsonProperty("weekend_increase_percentage")
        Percentage weekendIncreasePercentage,

        @NotNull(message = "high_season_increase_percentage is required")
        @JsonProperty("high_season_increase_percentage")
        Percentage highSeasonIncreasePercentage) {
}

