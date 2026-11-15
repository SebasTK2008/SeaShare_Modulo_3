package com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code settlement_record} (solo insercion y lectura). */
public interface SettlementRecordJpaRepository extends JpaRepository<SettlementRecordJpaEntity, UUID> {

    Optional<SettlementRecordJpaEntity> findFirstBySettlementIntentId(UUID settlementIntentId);
}
