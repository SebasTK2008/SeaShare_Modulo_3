package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code financial_parameters}; la vigente es la de mayor {@code version_number}. */
public interface FinancialParametersJpaRepository extends JpaRepository<FinancialParametersJpaEntity, UUID> {

    Optional<FinancialParametersJpaEntity> findFirstByOrderByVersionNumberDesc();
}
