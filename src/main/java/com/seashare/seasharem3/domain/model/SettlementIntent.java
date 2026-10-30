package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Intencion de dispersion (operativa y mutable) de UC10. Conserva la comision calculada y el
 * deposito asociado. Fuente: SPEC 10 RF-004/RF-009/RF-014; general-plan §4 y D-33.
 */
public final class SettlementIntent {

    private static final Map<SettlementStatus, Set<SettlementStatus>> ALLOWED_TRANSITIONS = Map.of(
            SettlementStatus.PENDING_SEND,
            EnumSet.of(SettlementStatus.IN_PROCESS, SettlementStatus.COMPLETED, SettlementStatus.REJECTED,
                    SettlementStatus.CANCELLED, SettlementStatus.EXPIRED, SettlementStatus.COMMUNICATION_ERROR),
            SettlementStatus.IN_PROCESS,
            EnumSet.of(SettlementStatus.COMPLETED, SettlementStatus.REJECTED, SettlementStatus.CANCELLED,
                    SettlementStatus.EXPIRED, SettlementStatus.COMMUNICATION_ERROR),
            SettlementStatus.COMMUNICATION_ERROR,
            EnumSet.of(SettlementStatus.IN_PROCESS, SettlementStatus.COMPLETED, SettlementStatus.REJECTED,
                    SettlementStatus.CANCELLED, SettlementStatus.EXPIRED),
            SettlementStatus.COMPLETED, EnumSet.noneOf(SettlementStatus.class),
            SettlementStatus.REJECTED, EnumSet.noneOf(SettlementStatus.class),
            SettlementStatus.CANCELLED, EnumSet.noneOf(SettlementStatus.class),
            SettlementStatus.EXPIRED, EnumSet.noneOf(SettlementStatus.class),
            SettlementStatus.FAILED, EnumSet.noneOf(SettlementStatus.class));

    private final UUID id;
    private final ReservationId reservationId;
    private final SettlementTrigger trigger;
    private final SettlementScope scope;
    private final Money amount;
    private final Money commissionAmount;
    private final Money depositAmount;
    private final UUID chargeIntentId;
    private final IdempotencyKey idempotencyKey;
    private final SettlementStatus status;
    private final String statusDetail;
    private final String externalReference;
    private final UUID disputeId;
    private final Instant createdAt;

    private SettlementIntent(UUID id, ReservationId reservationId, SettlementTrigger trigger, SettlementScope scope,
            Money amount, Money commissionAmount, Money depositAmount, UUID chargeIntentId,
            IdempotencyKey idempotencyKey, SettlementStatus status, String statusDetail, String externalReference,
            UUID disputeId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.commissionAmount = Objects.requireNonNull(commissionAmount, "commissionAmount");
        this.depositAmount = Objects.requireNonNull(depositAmount, "depositAmount");
        this.chargeIntentId = Objects.requireNonNull(chargeIntentId, "chargeIntentId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.status = Objects.requireNonNull(status, "status");
        this.statusDetail = statusDetail;
        this.externalReference = externalReference;
        this.disputeId = disputeId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        amount.requirePositive();
        commissionAmount.requireNonNegative();
        depositAmount.requireNonNegative();
    }

    public static SettlementIntent create(UUID id, ReservationId reservationId, SettlementTrigger trigger,
            SettlementScope scope, Money amount, Money commissionAmount, Money depositAmount, UUID chargeIntentId,
            IdempotencyKey idempotencyKey, UUID disputeId, Instant createdAt) {
        return new SettlementIntent(id, reservationId, trigger, scope, amount, commissionAmount, depositAmount,
                chargeIntentId, idempotencyKey, SettlementStatus.PENDING_SEND, null, null, disputeId, createdAt);
    }

    public static SettlementIntent restore(UUID id, ReservationId reservationId, SettlementTrigger trigger,
            SettlementScope scope, Money amount, Money commissionAmount, Money depositAmount, UUID chargeIntentId,
            IdempotencyKey idempotencyKey, SettlementStatus status, String statusDetail, String externalReference,
            UUID disputeId, Instant createdAt) {
        return new SettlementIntent(id, reservationId, trigger, scope, amount, commissionAmount, depositAmount,
                chargeIntentId, idempotencyKey, status, statusDetail, externalReference, disputeId, createdAt);
    }

    public SettlementIntent transitionTo(SettlementStatus next) {
        Objects.requireNonNull(next, "next");
        if (next == status) {
            return this;
        }
        if (!ALLOWED_TRANSITIONS.get(status).contains(next)) {
            throw new DomainException("SettlementIntent transition not allowed: " + status + " -> " + next);
        }
        return new SettlementIntent(id, reservationId, trigger, scope, amount, commissionAmount, depositAmount,
                chargeIntentId, idempotencyKey, next, statusDetail, externalReference, disputeId, createdAt);
    }

    public SettlementIntent withExternalReference(String reference) {
        Objects.requireNonNull(reference, "reference");
        if (externalReference != null && !externalReference.equals(reference)) {
            throw new DomainException("SettlementIntent already has an external_reference: " + externalReference);
        }
        return new SettlementIntent(id, reservationId, trigger, scope, amount, commissionAmount, depositAmount,
                chargeIntentId, idempotencyKey, status, statusDetail, reference, disputeId, createdAt);
    }

    public SettlementIntent withStatusDetail(String detail) {
        return new SettlementIntent(id, reservationId, trigger, scope, amount, commissionAmount, depositAmount,
                chargeIntentId, idempotencyKey, status, detail, externalReference, disputeId, createdAt);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(status).isEmpty();
    }

    public boolean canTransitionTo(SettlementStatus next) {
        Objects.requireNonNull(next, "next");
        return next == status || ALLOWED_TRANSITIONS.get(status).contains(next);
    }

    public UUID id() {
        return id;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public SettlementTrigger trigger() {
        return trigger;
    }

    public SettlementScope scope() {
        return scope;
    }

    public Money amount() {
        return amount;
    }

    public Money commissionAmount() {
        return commissionAmount;
    }

    public Money depositAmount() {
        return depositAmount;
    }

    public UUID chargeIntentId() {
        return chargeIntentId;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public SettlementStatus status() {
        return status;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public String externalReference() {
        return externalReference;
    }

    public UUID disputeId() {
        return disputeId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
