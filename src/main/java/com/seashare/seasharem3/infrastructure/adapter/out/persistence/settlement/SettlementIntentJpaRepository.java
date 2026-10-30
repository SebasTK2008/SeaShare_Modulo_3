package com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code settlement_intent}. */
public interface SettlementIntentJpaRepository extends JpaRepository<SettlementIntentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SettlementIntentJpaEntity s where s.id = :id")
    Optional<SettlementIntentJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<SettlementIntentJpaEntity> findFirstByIdempotencyKey(String idempotencyKey);

    Optional<SettlementIntentJpaEntity> findFirstByExternalReference(String externalReference);

    List<SettlementIntentJpaEntity> findByStatusIn(Collection<String> statuses);
}
