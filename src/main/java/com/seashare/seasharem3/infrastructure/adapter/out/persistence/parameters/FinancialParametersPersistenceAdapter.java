package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de {@code financial_parameters} (append-only): resuelve la vigente y las versiones
 * referenciadas por {@code frozen_parameters_id} (D-27) e inserta versiones nuevas.
 * La entidad implementa {@link org.springframework.data.domain.Persistable} para que Spring Data use
 * {@code persist} sin el {@code merge}/SELECT previo; la PK y {@code UNIQUE(version_number)} protegen
 * la concurrencia.
 * Fuente: UC11 plan T002/T012/T016; general-plan §4 y §4.2.
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
                .map(FinancialParametersMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FinancialParameters> findById(UUID parametersId) {
        return repository.findById(parametersId).map(FinancialParametersMapper::toDomain);
    }

    @Override
    @Transactional
    public FinancialParameters saveNewVersion(FinancialParameters params) {
        return FinancialParametersMapper.toDomain(repository.saveAndFlush(FinancialParametersMapper.toEntity(params)));
    }
}
