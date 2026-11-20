package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body de respuesta del {@code PUT /api/v1/financial-parameters}: número de la versión guardada.
 * Facilita la trazabilidad de los cálculos posteriores [UC11 plan §Contratos API; general-plan D-24; CONV].
 * Fuente: contrato {@code rest/UC11-guardar-parametros-financieros.md} §4.
 */
@Schema(description = "Versión guardada o versión vigente si los cuatro valores no cambiaron",
        requiredProperties = {"version_number"})
public record SaveFinancialParametersResponse(

        @Schema(description = "Número secuencial de versión", format = "int64", example = "8")
        @JsonProperty("version_number")
        long versionNumber) {
}

