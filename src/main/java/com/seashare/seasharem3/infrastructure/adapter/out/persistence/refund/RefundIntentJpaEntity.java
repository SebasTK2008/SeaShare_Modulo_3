package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code refund_intent} (UC09); no sale del paquete de persistencia. */
@Entity
@Table(name = "refund_intent")
public class RefundIntentJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "trigger", nullable = false)
    private String trigger;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "charge_intent_id", nullable = false)
    private UUID chargeIntentId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "status_detail")
    private String statusDetail;

    @Column(name = "dispute_id")
    private UUID disputeId;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected RefundIntentJpaEntity() {
    }

    static RefundIntentJpaEntity from(RefundIntent intent) {
        RefundIntentJpaEntity entity = new RefundIntentJpaEntity();
        entity.id = intent.id();
        entity.reservationId = intent.reservationId().value();
        entity.trigger = intent.trigger().name();
        entity.scope = intent.scope().name();
        entity.amount = intent.amount().amount();
        entity.chargeIntentId = intent.chargeIntentId();
        entity.idempotencyKey = intent.idempotencyKey().value();
        entity.disputeId = intent.disputeId();
        entity.createdAt = intent.createdAt();
        entity.apply(intent);
        return entity;
    }

    void apply(RefundIntent intent) {
        this.status = intent.status().name();
        this.statusDetail = intent.statusDetail();
        this.externalReference = intent.externalReference();
        this.disputeId = intent.disputeId();
    }

    UUID getId() {
        return id;
    }

    UUID getReservationId() {
        return reservationId;
    }

    String getTrigger() {
        return trigger;
    }

    String getScope() {
        return scope;
    }

    BigDecimal getAmount() {
        return amount;
    }

    UUID getChargeIntentId() {
        return chargeIntentId;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getStatus() {
        return status;
    }

    RefundIntentStatus statusValue() {
        return RefundIntentStatus.valueOf(status);
    }

    String getStatusDetail() {
        return statusDetail;
    }

    UUID getDisputeId() {
        return disputeId;
    }

    String getExternalReference() {
        return externalReference;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
