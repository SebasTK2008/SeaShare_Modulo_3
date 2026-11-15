package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body de respuesta del {@code PUT /api/v1/financial-parameters}: número de la versión guardada.
 * Facilita la trazabilidad de los cálculos posteriores [UC11 plan §Contratos API; general-plan D-24; CONV].
 * Fuente: contrato {@code rest/UC11-guardar-parametros-financieros.md} §4.
 */
public record SaveFinancialParametersResponse(

        @JsonProperty("version_number")
        long versionNumber) {
}

