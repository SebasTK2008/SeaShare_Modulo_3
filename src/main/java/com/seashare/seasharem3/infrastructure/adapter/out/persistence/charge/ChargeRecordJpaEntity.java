package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import com.seashare.seasharem3.domain.model.ChargeRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeo de {@code charge_record} (general-plan §4; migración V1, líneas 175-197). Tabla inmutable:
 * el esquema la protege con triggers que prohíben UPDATE y DELETE, de modo que la entidad solo se
 * inserta, nunca se modifica. No sale del adaptador.
 */
@Entity
@Table(name = "charge_record")
public class ChargeRecordJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "charge_intent_id", nullable = false)
    private UUID chargeIntentId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "boat_id", nullable = false)
    private UUID boatId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "rental_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal rentalAmount;

    @Column(name = "insurance_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal insuranceAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal depositAmount;

    @Column(name = "payment_method_type")
    private String paymentMethodType;

    @Column(name = "last_four", length = 4)
    private String lastFour;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChargeRecordJpaEntity() {
    }

    /**
     * Entidad nueva con todos los datos del registro. La tabla es inmutable: esta entidad solo se
     * inserta, nunca se modifica.
     */
    static ChargeRecordJpaEntity from(ChargeRecord record) {
        ChargeRecordJpaEntity entity = new ChargeRecordJpaEntity();
        entity.id = record.id();
        entity.chargeIntentId = record.chargeIntentId();
        entity.reservationId = record.reservationId().value();
        entity.ownerId = record.ownerId().value();
        entity.boatId = record.boatId().value();
        entity.amount = record.amount().amount();
        entity.rentalAmount = record.rentalAmount().amount();
        entity.insuranceAmount = record.insuranceAmount().amount();
        entity.depositAmount = record.depositAmount().amount();
        entity.paymentMethodType = record.paymentMethodType();
        entity.lastFour = record.lastFour();
        entity.externalReference = record.externalReference();
        entity.createdAt = record.createdAt();
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getChargeIntentId() {
        return chargeIntentId;
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

    BigDecimal getRentalAmount() {
        return rentalAmount;
    }

    BigDecimal getInsuranceAmount() {
        return insuranceAmount;
    }

    BigDecimal getDepositAmount() {
        return depositAmount;
    }

    String getPaymentMethodType() {
        return paymentMethodType;
    }

    String getLastFour() {
        return lastFour;
    }

    String getExternalReference() {
        return externalReference;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
