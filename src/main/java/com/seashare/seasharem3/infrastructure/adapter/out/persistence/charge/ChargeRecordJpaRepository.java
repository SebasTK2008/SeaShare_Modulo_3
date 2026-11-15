package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code charge_record}. */
public interface ChargeRecordJpaRepository extends JpaRepository<ChargeRecordJpaEntity, UUID> {

    Optional<ChargeRecordJpaEntity> findByChargeIntentId(UUID chargeIntentId);
}
