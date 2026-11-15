package com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement;

import com.seashare.seasharem3.domain.model.SettlementRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mapeo de {@code settlement_record}; tabla inmutable protegida por triggers (UC10). */
@Entity
@Table(name = "settlement_record")
public class SettlementRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

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

    @Column(name = "detail")
    private String detail;

    @Column(name = "rental_gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal rentalGrossAmount;

    @Column(name = "insurance_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal insuranceAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal depositAmount;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SettlementRecordJpaEntity() {
    }

    static SettlementRecordJpaEntity from(SettlementRecord record) {
        SettlementRecordJpaEntity entity = new SettlementRecordJpaEntity();
        entity.id = record.id();
        entity.settlementIntentId = record.settlementIntentId();
        entity.reservationId = record.reservationId().value();
        entity.ownerId = record.ownerId().value();
        entity.boatId = record.boatId().value();
        entity.amount = record.amount().amount();
        entity.detail = record.detail();
        entity.rentalGrossAmount = record.rentalGrossAmount().amount();
        entity.insuranceAmount = record.insuranceAmount().amount();
        entity.depositAmount = record.depositAmount().amount();
        entity.externalReference = record.externalReference();
        entity.createdAt = record.createdAt();
        return entity;
    }

    UUID getId() {
        return id;
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

    String getDetail() {
        return detail;
    }

    BigDecimal getRentalGrossAmount() {
        return rentalGrossAmount;
    }

    BigDecimal getInsuranceAmount() {
        return insuranceAmount;
    }

    BigDecimal getDepositAmount() {
        return depositAmount;
    }

    String getExternalReference() {
        return externalReference;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
