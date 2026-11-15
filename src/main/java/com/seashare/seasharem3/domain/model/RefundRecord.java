package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de reembolso: metrica financiera inmutable creada solo cuando la pasarela confirma el
 * reembolso. Conserva la intencion que lo origino, el monto confirmado y el monto neto liberado
 * ({@code amount - transaction_cost}). Fuente: SPEC 9 RF-006A/RF-007/RF-011/RF-013; UC09 plan T002.
 */
public final class RefundRecord {

    private final UUID id;
    private final UUID refundIntentId;
    private final ReservationId reservationId;
    private final OwnerId ownerId;
    private final BoatId boatId;
    private final UUID disputeId;
    private final Money amount;
    private final Money releasedAmount;
    private final String detail;
    private final Money transactionCost;
    private final String externalReference;
    private final Instant createdAt;

    private RefundRecord(UUID id, UUID refundIntentId, ReservationId reservationId, OwnerId ownerId, BoatId boatId,
            UUID disputeId, Money amount, Money releasedAmount, String detail, Money transactionCost,
            String externalReference, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.refundIntentId = Objects.requireNonNull(refundIntentId, "refundIntentId");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.boatId = Objects.requireNonNull(boatId, "boatId");
        this.disputeId = disputeId;
        this.amount = Objects.requireNonNull(amount, "amount");
        this.releasedAmount = Objects.requireNonNull(releasedAmount, "releasedAmount");
        this.detail = detail;
        this.transactionCost = transactionCost;
        this.externalReference = externalReference;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        amount.requirePositive();
        releasedAmount.requireNonNegative();
        if (releasedAmount.isGreaterThan(amount)) {
            throw new DomainException("released_amount cannot exceed amount: " + releasedAmount + " > " + amount);
        }
    }

    /**
     * Crea el registro a partir de una intencion completada. El monto neto se deriva del costo
     * reportado por la pasarela; si no hay costo, coincide con el monto confirmado.
     */
    public static RefundRecord create(UUID id, RefundIntent intent, OwnerId ownerId, BoatId boatId,
            Money confirmedAmount, Money transactionCost, String detail, String externalReference,
            Instant createdAt) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(boatId, "boatId");
        Objects.requireNonNull(confirmedAmount, "confirmedAmount");
        Objects.requireNonNull(createdAt, "createdAt");
        if (intent.status() != RefundIntentStatus.COMPLETED) {
            throw new DomainException(
                    "A refund record can only be created for a completed refund, not for " + intent.status());
        }
        Money cost = transactionCost == null ? Money.ZERO : transactionCost;
        cost.requireNonNegative();
        Money released = confirmedAmount.subtract(cost);
        if (released.isNegative()) {
            throw new DomainException("The transaction cost cannot exceed the refunded amount: " + cost);
        }
        return new RefundRecord(id, intent.id(), intent.reservationId(), ownerId, boatId, intent.disputeId(),
                confirmedAmount, released, detail, transactionCost, externalReference, createdAt);
    }

    public static RefundRecord restore(UUID id, UUID refundIntentId, ReservationId reservationId, OwnerId ownerId,
            BoatId boatId, UUID disputeId, Money amount, Money releasedAmount, String detail, Money transactionCost,
            String externalReference, Instant createdAt) {
        return new RefundRecord(id, refundIntentId, reservationId, ownerId, boatId, disputeId, amount, releasedAmount,
                detail, transactionCost, externalReference, createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID refundIntentId() {
        return refundIntentId;
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

    public UUID disputeId() {
        return disputeId;
    }

    public Money amount() {
        return amount;
    }

    public Money releasedAmount() {
        return releasedAmount;
    }

    public String detail() {
        return detail;
    }

    public Money transactionCost() {
        return transactionCost;
    }

    public String externalReference() {
        return externalReference;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
