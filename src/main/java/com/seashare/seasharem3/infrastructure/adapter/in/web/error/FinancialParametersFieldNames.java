package com.seashare.seasharem3.infrastructure.adapter.in.web.error;

import java.util.Map;

/**
 * Traduce los nombres de dominio de los cuatro parámetros financieros a las claves JSON del contrato.
 * El dominio no conoce el contrato REST; esta traducción vive en el punto único de errores.
 * Fuente: contrato rest/UC11-guardar-parametros-financieros.md §2.
 */
final class FinancialParametersFieldNames {

    private static final Map<String, String> JSON_BY_DOMAIN = Map.of(
            "commissionPct", "platform_commission_percentage",
            "insuranceFeePerPassenger", "insurance_fee_per_passenger",
            "weekendIncreasePct", "weekend_increase_percentage",
            "highSeasonIncreasePct", "high_season_increase_percentage",
            // Nombres de los componentes del record de entrada (Bean Validation los reporta así).
            "platformCommissionPercentage", "platform_commission_percentage",
            "weekendIncreasePercentage", "weekend_increase_percentage",
            "highSeasonIncreasePercentage", "high_season_increase_percentage");

    private FinancialParametersFieldNames() {
    }

    static String toJson(String domainField) {
        return JSON_BY_DOMAIN.getOrDefault(domainField, domainField);
    }
}
