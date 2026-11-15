package com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement;

import com.seashare.seasharem3.domain.model.CommissionRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code commission_record}; tabla inmutable protegida por triggers (UC10). */
@Entity
@Table(name = "commission_record")
public class CommissionRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "settlement_record_id", nullable = false)
    private UUID settlementRecordId;

    @Column(name = "settlement_intent_id", nullable = false)
    private UUID settlementIntentId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "boat_id", nullable = false)
    private UUID boatId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommissionRecordJpaEntity() {
    }

    static CommissionRecordJpaEntity from(CommissionRecord record) {
        CommissionRecordJpaEntity entity = new CommissionRecordJpaEntity();
        entity.id = record.id();
        entity.settlementRecordId = record.settlementRecordId();
        entity.settlementIntentId = record.settlementIntentId();
        entity.reservationId = record.reservationId().value();
        entity.ownerId = record.ownerId().value();
        entity.boatId = record.boatId().value();
        entity.amount = record.amount().amount();
        entity.createdAt = record.createdAt();
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getSettlementRecordId() {
        return settlementRecordId;
    }

    UUID getSettlementIntentId() {
        return settlementIntentId;
    }

    UUID getReservationId() {
        return reservationId;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    UUID getBoatId() {
        return boatId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
