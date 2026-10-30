package com.seashare.seasharem3.infrastructure.adapter.out.persistence.deposit;

import com.seashare.seasharem3.domain.model.DepositDisposition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code deposit_disposition} (UC08). El adaptador aplica el CAS; la entidad no sale del paquete. */
@Entity
@Table(name = "deposit_disposition")
public class DepositDispositionJpaEntity {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "dispute_id")
    private UUID disputeId;

    @Column(name = "dispute_status")
    private String disputeStatus;

    @Column(name = "disposition", nullable = false)
    private String disposition;

    protected DepositDispositionJpaEntity() {
    }

    static DepositDispositionJpaEntity from(DepositDisposition value) {
        DepositDispositionJpaEntity entity = new DepositDispositionJpaEntity();
        entity.reservationId = value.reservationId().value();
        entity.completedAt = value.completedAt();
        entity.disputeId = value.disputeId();
        entity.disputeStatus = value.disputeStatus() == null ? null : value.disputeStatus().name();
        entity.disposition = value.disposition().name();
        return entity;
    }

    UUID getReservationId() {
        return reservationId;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    UUID getDisputeId() {
        return disputeId;
    }

    String getDisputeStatus() {
        return disputeStatus;
    }

    String getDisposition() {
        return disposition;
    }
}
