package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Intencion de reembolso (operativa y mutable) de UC09; una por intento y ligada al cobro original.
 * Fuente: SPEC 9 RF-006/RF-006A; general-plan §4 y D-33; UC09 plan T002.
 */
public final class RefundIntent {

    private static final Map<RefundIntentStatus, Set<RefundIntentStatus>> ALLOWED_TRANSITIONS = Map.of(
            RefundIntentStatus.PENDING_SEND,
            EnumSet.of(RefundIntentStatus.IN_PROCESS, RefundIntentStatus.COMPLETED, RefundIntentStatus.REJECTED,
                    RefundIntentStatus.CANCELLED, RefundIntentStatus.EXPIRED,
                    RefundIntentStatus.COMMUNICATION_ERROR),
            RefundIntentStatus.IN_PROCESS,
            EnumSet.of(RefundIntentStatus.COMPLETED, RefundIntentStatus.REJECTED, RefundIntentStatus.CANCELLED,
                    RefundIntentStatus.EXPIRED, RefundIntentStatus.COMMUNICATION_ERROR),
            RefundIntentStatus.COMMUNICATION_ERROR,
            EnumSet.of(RefundIntentStatus.IN_PROCESS, RefundIntentStatus.COMPLETED, RefundIntentStatus.REJECTED,
                    RefundIntentStatus.CANCELLED, RefundIntentStatus.EXPIRED),
            RefundIntentStatus.COMPLETED, EnumSet.noneOf(RefundIntentStatus.class),
            RefundIntentStatus.REJECTED, EnumSet.noneOf(RefundIntentStatus.class),
            RefundIntentStatus.CANCELLED, EnumSet.noneOf(RefundIntentStatus.class),
            RefundIntentStatus.EXPIRED, EnumSet.noneOf(RefundIntentStatus.class));

    private final UUID id;
    private final ReservationId reservationId;
    private final RefundTrigger trigger;
    private final RefundScope scope;
    private final Money amount;
    private final UUID chargeIntentId;
    private final IdempotencyKey idempotencyKey;
    private final RefundIntentStatus status;
    private final String statusDetail;
    private final UUID disputeId;
    private final String externalReference;
    private final Instant createdAt;

    private RefundIntent(UUID id, ReservationId reservationId, RefundTrigger trigger, RefundScope scope, Money amount,
            UUID chargeIntentId, IdempotencyKey idempotencyKey, RefundIntentStatus status, String statusDetail,
            UUID disputeId, String externalReference, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.chargeIntentId = Objects.requireNonNull(chargeIntentId, "chargeIntentId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.status = Objects.requireNonNull(status, "status");
        this.statusDetail = statusDetail;
        this.disputeId = disputeId;
        this.externalReference = externalReference;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        amount.requirePositive();
    }

    public static RefundIntent create(UUID id, ReservationId reservationId, RefundTrigger trigger, RefundScope scope,
            Money amount, UUID chargeIntentId, IdempotencyKey idempotencyKey, UUID disputeId, Instant createdAt) {
        return new RefundIntent(id, reservationId, trigger, scope, amount, chargeIntentId, idempotencyKey,
                RefundIntentStatus.PENDING_SEND, null, disputeId, null, createdAt);
    }

    public static RefundIntent restore(UUID id, ReservationId reservationId, RefundTrigger trigger, RefundScope scope,
            Money amount, UUID chargeIntentId, IdempotencyKey idempotencyKey, RefundIntentStatus status,
            String statusDetail, UUID disputeId, String externalReference, Instant createdAt) {
        return new RefundIntent(id, reservationId, trigger, scope, amount, chargeIntentId, idempotencyKey, status,
                statusDetail, disputeId, externalReference, createdAt);
    }

    public RefundIntent transitionTo(RefundIntentStatus next) {
        Objects.requireNonNull(next, "next");
        if (next == status) {
            return this;
        }
        if (!ALLOWED_TRANSITIONS.get(status).contains(next)) {
            throw new DomainException("RefundIntent transition not allowed: " + status + " -> " + next);
        }
        return new RefundIntent(id, reservationId, trigger, scope, amount, chargeIntentId, idempotencyKey, next,
                statusDetail, disputeId, externalReference, createdAt);
    }

    public RefundIntent withExternalReference(String reference) {
        Objects.requireNonNull(reference, "reference");
        if (externalReference != null && !externalReference.equals(reference)) {
            throw new DomainException("RefundIntent already has an external_reference: " + externalReference);
        }
        return new RefundIntent(id, reservationId, trigger, scope, amount, chargeIntentId, idempotencyKey, status,
                statusDetail, disputeId, reference, createdAt);
    }

    public RefundIntent withStatusDetail(String detail) {
        return new RefundIntent(id, reservationId, trigger, scope, amount, chargeIntentId, idempotencyKey, status,
                detail, disputeId, externalReference, createdAt);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(status).isEmpty();
    }

    public boolean canTransitionTo(RefundIntentStatus next) {
        Objects.requireNonNull(next, "next");
        return next == status || ALLOWED_TRANSITIONS.get(status).contains(next);
    }

    public UUID id() {
        return id;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public RefundTrigger trigger() {
        return trigger;
    }

    public RefundScope scope() {
        return scope;
    }

    public Money amount() {
        return amount;
    }

    public UUID chargeIntentId() {
        return chargeIntentId;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public RefundIntentStatus status() {
        return status;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public UUID disputeId() {
        return disputeId;
    }

    public String externalReference() {
        return externalReference;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
