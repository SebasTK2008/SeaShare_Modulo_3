package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;

/**
 * Mapeo entre dominio y entidad JPA de parámetros financieros (UC11 T015).
 * Fuente: UC11 plan T015; general-plan D-04.
 * [CONV] nombre [CONV]; usa mapeo manual (dominio puro vs entidad) conforme al adaptador existente.
 */
public class FinancialParametersMapper {

    public static FinancialParametersJpaEntity toEntity(FinancialParameters params) {
        return FinancialParametersJpaEntity.from(params);
    }

    public static FinancialParameters toDomain(FinancialParametersJpaEntity entity) {
        return FinancialParameters.create(
                entity.getId(),
                entity.getVersionNumber(),
                new Percentage(entity.getCommissionPct()),
                new Money(entity.getInsuranceFeePerPassenger()),
                new Percentage(entity.getWeekendIncreasePct()),
                new Percentage(entity.getHighSeasonIncreasePct()),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
