package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de dispersion inmutable. Conserva el monto confirmado, el desglose usado y la intencion
 * que lo origino. Fuente: SPEC 10 RF-009A/RF-010/RF-015/RF-016; UC10 plan T007.
 */
public final class SettlementRecord {

    private final UUID id;
    private final UUID settlementIntentId;
    private final ReservationId reservationId;
    private final OwnerId ownerId;
    private final BoatId boatId;
    private final Money amount;
    private final String detail;
    private final Money rentalGrossAmount;
    private final Money insuranceAmount;
    private final Money depositAmount;
    private final String externalReference;
    private final Instant createdAt;

    private SettlementRecord(UUID id, UUID settlementIntentId, ReservationId reservationId, OwnerId ownerId,
            BoatId boatId, Money amount, String detail, Money rentalGrossAmount, Money insuranceAmount,
            Money depositAmount, String externalReference, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.settlementIntentId = Objects.requireNonNull(settlementIntentId, "settlementIntentId");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.boatId = Objects.requireNonNull(boatId, "boatId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.detail = detail;
        this.rentalGrossAmount = Objects.requireNonNull(rentalGrossAmount, "rentalGrossAmount");
        this.insuranceAmount = Objects.requireNonNull(insuranceAmount, "insuranceAmount");
        this.depositAmount = Objects.requireNonNull(depositAmount, "depositAmount");
        this.externalReference = externalReference;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        amount.requirePositive();
        rentalGrossAmount.requireNonNegative();
        insuranceAmount.requireNonNegative();
        depositAmount.requireNonNegative();
    }

    public static SettlementRecord create(UUID id, SettlementIntent intent, OwnerId ownerId, BoatId boatId,
            Money confirmedAmount, String detail, Money rentalGrossAmount, Money insuranceAmount,
            Money depositAmount, String externalReference, Instant createdAt) {
        Objects.requireNonNull(intent, "intent");
        if (intent.status() != SettlementStatus.COMPLETED) {
            throw new DomainException(
                    "A settlement record can only be created for a completed settlement, not for " + intent.status());
        }
        return new SettlementRecord(id, intent.id(), intent.reservationId(), ownerId, boatId, confirmedAmount,
                detail, rentalGrossAmount, insuranceAmount, depositAmount, externalReference, createdAt);
    }

    public static SettlementRecord restore(UUID id, UUID settlementIntentId, ReservationId reservationId,
            OwnerId ownerId, BoatId boatId, Money amount, String detail, Money rentalGrossAmount,
            Money insuranceAmount, Money depositAmount, String externalReference, Instant createdAt) {
        return new SettlementRecord(id, settlementIntentId, reservationId, ownerId, boatId, amount, detail,
                rentalGrossAmount, insuranceAmount, depositAmount, externalReference, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID settlementIntentId() {
        return settlementIntentId;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public OwnerId ownerId() {
        return ownerId;
    }

    public BoatId boatId() {
        return boatId;
    }

    public Money amount() {
        return amount;
    }

    public String detail() {
        return detail;
    }

    public Money rentalGrossAmount() {
        return rentalGrossAmount;
    }

    public Money insuranceAmount() {
        return insuranceAmount;
    }

    public Money depositAmount() {
        return depositAmount;
    }

    public String externalReference() {
        return externalReference;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
