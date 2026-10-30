package com.seashare.seasharem3.infrastructure.adapter.out.persistence.deposit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Repositorio Spring Data de {@code deposit_disposition}. */
public interface DepositDispositionJpaRepository extends JpaRepository<DepositDispositionJpaEntity, UUID> {

    @Modifying
    @Query("update DepositDispositionJpaEntity d set d.disposition = :next, d.disputeId = :disputeId, "
            + "d.disputeStatus = :disputeStatus, d.completedAt = :completedAt "
            + "where d.reservationId = :reservationId and d.disposition = :expected")
    int compareAndSet(@Param("reservationId") UUID reservationId, @Param("expected") String expected,
                      @Param("next") String next, @Param("disputeId") UUID disputeId,
                      @Param("disputeStatus") String disputeStatus, @Param("completedAt") Instant completedAt);
}
