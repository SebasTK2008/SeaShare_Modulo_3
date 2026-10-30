package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data de {@code refund_record} (solo insercion y lectura). */
public interface RefundRecordJpaRepository extends JpaRepository<RefundRecordJpaEntity, UUID> {

    Optional<RefundRecordJpaEntity> findFirstByRefundIntentId(UUID refundIntentId);

    @Query("select sum(r.releasedAmount) from RefundRecordJpaEntity r where r.reservationId = :reservationId")
    Optional<BigDecimal> sumReleasedAmountByReservationId(@Param("reservationId") UUID reservationId);
}
