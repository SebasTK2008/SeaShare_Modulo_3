package com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code financial_parameters}; la vigente es la de mayor {@code version_number}. */
public interface FinancialParametersJpaRepository extends JpaRepository<FinancialParametersJpaEntity, UUID> {

    Optional<FinancialParametersJpaEntity> findFirstByOrderByVersionNumberDesc();

    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('seashare.financial_parameters.version'))",
            nativeQuery = true)
    void lockVersionAssignment();
}
