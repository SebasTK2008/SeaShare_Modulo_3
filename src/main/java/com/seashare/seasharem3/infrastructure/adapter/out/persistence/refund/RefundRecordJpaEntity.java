package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import com.seashare.seasharem3.domain.model.RefundRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code refund_record}; tabla inmutable protegida por triggers (UC09). */
@Entity
@Table(name = "refund_record")
public class RefundRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "refund_intent_id", nullable = false)
    private UUID refundIntentId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "boat_id", nullable = false)
    private UUID boatId;

    @Column(name = "dispute_id")
    private UUID disputeId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "released_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal releasedAmount;

    @Column(name = "detail")
    private String detail;

    @Column(name = "transaction_cost", precision = 18, scale = 4)
    private BigDecimal transactionCost;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefundRecordJpaEntity() {
    }

    static RefundRecordJpaEntity from(RefundRecord record) {
        RefundRecordJpaEntity entity = new RefundRecordJpaEntity();
        entity.id = record.id();
        entity.refundIntentId = record.refundIntentId();
        entity.reservationId = record.reservationId().value();
        entity.ownerId = record.ownerId().value();
        entity.boatId = record.boatId().value();
        entity.disputeId = record.disputeId();
        entity.amount = record.amount().amount();
        entity.releasedAmount = record.releasedAmount().amount();
        entity.detail = record.detail();
        entity.transactionCost = record.transactionCost() == null ? null : record.transactionCost().amount();
        entity.externalReference = record.externalReference();
        entity.createdAt = record.createdAt();
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getRefundIntentId() {
        return refundIntentId;
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

    UUID getDisputeId() {
        return disputeId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    BigDecimal getReleasedAmount() {
        return releasedAmount;
    }

    String getDetail() {
        return detail;
    }

    BigDecimal getTransactionCost() {
        return transactionCost;
    }

    String getExternalReference() {
        return externalReference;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
