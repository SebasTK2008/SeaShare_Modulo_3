package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de {@code financial_parameters} (append-only): resuelve la vigente y las versiones
 * referenciadas por {@code frozen_parameters_id} (D-27) e inserta versiones nuevas.
 * Fuente: UC11 plan T002/T012; general-plan §4 y §4.2.
 */
@Component
public class FinancialParametersPersistenceAdapter implements FinancialParametersRepository {

    private final FinancialParametersJpaRepository repository;

    public FinancialParametersPersistenceAdapter(FinancialParametersJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FinancialParameters> findCurrent() {
        return repository.findFirstByOrderByVersionNumberDesc()
                .map(FinancialParametersPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FinancialParameters> findById(UUID parametersId) {
        return repository.findById(parametersId).map(FinancialParametersPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public FinancialParameters saveNewVersion(FinancialParameters params) {
        if (repository.existsById(params.id())) {
            throw new IllegalStateException(
                    "financial_parameters is append-only: id already exists " + params.id());
        }
        return toDomain(repository.saveAndFlush(FinancialParametersJpaEntity.from(params)));
    }

    private static FinancialParameters toDomain(FinancialParametersJpaEntity entity) {
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
