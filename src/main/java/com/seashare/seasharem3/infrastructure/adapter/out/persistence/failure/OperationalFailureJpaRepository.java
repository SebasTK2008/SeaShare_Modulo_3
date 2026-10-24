package com.seashare.seasharem3.infrastructure.adapter.out.persistence.failure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Repositorio Spring Data de {@code operational_failure}. */
public interface OperationalFailureJpaRepository extends JpaRepository<OperationalFailureJpaEntity, UUID> {
}
