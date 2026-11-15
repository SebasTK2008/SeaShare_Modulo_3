package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de comision inmutable, creado solo al confirmar una liquidacion estandar y relacionado
 * con su registro de dispersion. Fuente: SPEC 10 RF-014/CE-006; UC10 plan T007.
 */
public final class CommissionRecord {

    private final UUID id;
    private final UUID settlementRecordId;
    private final UUID settlementIntentId;
    private final ReservationId reservationId;
    private final OwnerId ownerId;
    private final BoatId boatId;
    private final Money amount;
    private final Instant createdAt;

    private CommissionRecord(UUID id, UUID settlementRecordId, UUID settlementIntentId, ReservationId reservationId,
            OwnerId ownerId, BoatId boatId, Money amount, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.settlementRecordId = Objects.requireNonNull(settlementRecordId, "settlementRecordId");
        this.settlementIntentId = Objects.requireNonNull(settlementIntentId, "settlementIntentId");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.boatId = Objects.requireNonNull(boatId, "boatId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        amount.requireNonNegative();
    }

    public static CommissionRecord create(UUID id, SettlementRecord settlementRecord, Money amount,
            Instant createdAt) {
        Objects.requireNonNull(settlementRecord, "settlementRecord");
        return new CommissionRecord(id, settlementRecord.id(), settlementRecord.settlementIntentId(),
                settlementRecord.reservationId(), settlementRecord.ownerId(), settlementRecord.boatId(), amount,
                createdAt);
    }

    public static CommissionRecord restore(UUID id, UUID settlementRecordId, UUID settlementIntentId,
            ReservationId reservationId, OwnerId ownerId, BoatId boatId, Money amount, Instant createdAt) {
        return new CommissionRecord(id, settlementRecordId, settlementIntentId, reservationId, ownerId, boatId,
                amount, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID settlementRecordId() {
        return settlementRecordId;
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

    public Instant createdAt() {
        return createdAt;
    }
}
