package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code refund_intent}. */
public interface RefundIntentJpaRepository extends JpaRepository<RefundIntentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundIntentJpaEntity r where r.id = :id")
    Optional<RefundIntentJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<RefundIntentJpaEntity> findFirstByIdempotencyKey(String idempotencyKey);

    Optional<RefundIntentJpaEntity> findFirstByExternalReference(String externalReference);

    List<RefundIntentJpaEntity> findByStatusIn(Collection<String> statuses);
}
