package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code charge_intent}. */
public interface ChargeIntentJpaRepository extends JpaRepository<ChargeIntentJpaEntity, UUID> {

    Optional<ChargeIntentJpaEntity> findFirstByReservationIdOrderByCreatedAtDescIdDesc(UUID reservationId);

    Optional<ChargeIntentJpaEntity> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(UUID reservationId,
            String status);

    Optional<ChargeIntentJpaEntity> findFirstByExternalReference(String externalReference);

    Optional<ChargeIntentJpaEntity> findFirstByIdempotencyKey(String idempotencyKey);

    List<ChargeIntentJpaEntity> findByStatusIn(Collection<String> statuses);
}
