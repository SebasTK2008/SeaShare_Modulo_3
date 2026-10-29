package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Intención de cobro (operativa y mutable) de UC05; una por intento (clave idempotente propia).
 * Fuente: general-plan §4, §8 y D-33; UC05 plan T003.
 * Inmutable: cada cambio de estado o dato externo devuelve una copia nueva.
 */
public final class ChargeIntent {

    /**
     * Transiciones permitidas [CONV] (los planes no publican tabla literal; se derivan de UC05 §Reglas 5-8):
     * PENDING_SEND → cualquier estado no terminal (el worker ya envió el cobro y la pasarela responde);
     * IN_PROCESS → CAPTURED | REJECTED | CANCELLED | EXPIRED | COMMUNICATION_ERROR;
     * COMMUNICATION_ERROR → IN_PROCESS | CAPTURED | REJECTED | CANCELLED | EXPIRED (resultado de conciliación);
     * CAPTURED, REJECTED, CANCELLED y EXPIRED son terminales. Repetir el mismo estado es idempotente.
     */
    private static final Map<ChargeIntentStatus, Set<ChargeIntentStatus>> ALLOWED_TRANSITIONS = Map.of(
            ChargeIntentStatus.PENDING_SEND,
            EnumSet.of(ChargeIntentStatus.IN_PROCESS, ChargeIntentStatus.CAPTURED, ChargeIntentStatus.REJECTED,
                    ChargeIntentStatus.CANCELLED, ChargeIntentStatus.EXPIRED,
                    ChargeIntentStatus.COMMUNICATION_ERROR),
            ChargeIntentStatus.IN_PROCESS,
            EnumSet.of(ChargeIntentStatus.CAPTURED, ChargeIntentStatus.REJECTED, ChargeIntentStatus.CANCELLED,
                    ChargeIntentStatus.EXPIRED, ChargeIntentStatus.COMMUNICATION_ERROR),
            ChargeIntentStatus.COMMUNICATION_ERROR,
            EnumSet.of(ChargeIntentStatus.IN_PROCESS, ChargeIntentStatus.CAPTURED, ChargeIntentStatus.REJECTED,
                    ChargeIntentStatus.CANCELLED, ChargeIntentStatus.EXPIRED),
            ChargeIntentStatus.CAPTURED, EnumSet.noneOf(ChargeIntentStatus.class),
            ChargeIntentStatus.REJECTED, EnumSet.noneOf(ChargeIntentStatus.class),
            ChargeIntentStatus.CANCELLED, EnumSet.noneOf(ChargeIntentStatus.class),
            ChargeIntentStatus.EXPIRED, EnumSet.noneOf(ChargeIntentStatus.class));

    private final UUID id;
    private final ReservationId reservationId;
    private final IdempotencyKey idempotencyKey;
    private final ChargeIntentStatus status;
    private final Money amount;
    private final PaymentMethodReference paymentMethod;
    private final String externalReference;
    private final String statusDetail;
    private final Instant createdAt;

    private ChargeIntent(UUID id, ReservationId reservationId, IdempotencyKey idempotencyKey,
            ChargeIntentStatus status, Money amount, PaymentMethodReference paymentMethod, String externalReference,
            String statusDetail, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.status = Objects.requireNonNull(status, "status");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod");
        this.externalReference = externalReference;
        this.statusDetail = statusDetail;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        amount.requirePositive();
    }

    /**
     * Crea la intención en {@code PENDING_SEND} (estado inicial al persistir el comando de UC07).
     * Fuente: UC05 plan §Reglas 4 y T003.
     */
    public static ChargeIntent create(UUID id, ReservationId reservationId, IdempotencyKey idempotencyKey,
            Money amount, PaymentMethodReference paymentMethod, Instant createdAt) {
        return new ChargeIntent(id, reservationId, idempotencyKey, ChargeIntentStatus.PENDING_SEND, amount,
                paymentMethod, null, null, createdAt);
    }

    /**
     * Rehidrata una intención ya persistida con su estado actual. No valida transiciones: el estado
     * almacenado proviene de la base de datos, que es la fuente de verdad.
     * Fuente: UC05 plan T009 (persistencia).
     */
    public static ChargeIntent restore(UUID id, ReservationId reservationId, IdempotencyKey idempotencyKey,
            ChargeIntentStatus status, Money amount, PaymentMethodReference paymentMethod, String externalReference,
            String statusDetail, Instant createdAt) {
        return new ChargeIntent(id, reservationId, idempotencyKey, status, amount, paymentMethod, externalReference,
                statusDetail, createdAt);
    }

    /**
     * Cambia el estado validando la transición [CONV]; repetir el estado actual es idempotente y no altera nada.
     * Fuente: UC05 plan §Reglas 5 y 8; general-plan §8.
     */
    public ChargeIntent transitionTo(ChargeIntentStatus next) {
        Objects.requireNonNull(next, "next");
        if (next == status) {
            return this;
        }
        if (!ALLOWED_TRANSITIONS.get(status).contains(next)) {
            throw new DomainException("ChargeIntent transition not allowed: " + status + " → " + next);
        }
        return new ChargeIntent(id, reservationId, idempotencyKey, next, amount, paymentMethod, externalReference,
                statusDetail, createdAt);
    }

    /**
     * Asocia el identificador externo del pago (data.id de la pasarela); se guarda una única vez
     * (el mismo valor es idempotente). Fuente: UC05 plan §Reglas 8.
     */
    public ChargeIntent withExternalReference(String reference) {
        Objects.requireNonNull(reference, "reference");
        if (externalReference != null && !externalReference.equals(reference)) {
            throw new DomainException("ChargeIntent already has an external_reference: " + externalReference);
        }
        return new ChargeIntent(id, reservationId, idempotencyKey, status, amount, paymentMethod, reference,
                statusDetail, createdAt);
    }

    /** Detalle técnico del estado externo publicado por UC05 (lo consume UC06). Fuente: D-UC05-02. */
    public ChargeIntent withStatusDetail(String detail) {
        return new ChargeIntent(id, reservationId, idempotencyKey, status, amount, paymentMethod, externalReference,
                detail, createdAt);
    }

    /** {@code true} si la intención alcanzó un estado sin transiciones salientes (D-33). */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(status).isEmpty();
    }

    /** {@code true} si la transición indicada está permitida (mismo estado incluido). Fuente: [CONV]. */
    public boolean canTransitionTo(ChargeIntentStatus next) {
        Objects.requireNonNull(next, "next");
        return next == status || ALLOWED_TRANSITIONS.get(status).contains(next);
    }

    public UUID id() {
        return id;
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public ChargeIntentStatus status() {
        return status;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethodReference paymentMethod() {
        return paymentMethod;
    }

    /** Id del pago en la pasarela; {@code null} hasta que la pasarela lo publique. Fuente: UC05 §Reglas 8. */
    public String externalReference() {
        return externalReference;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChargeIntent that)) {
            return false;
        }
        return id.equals(that.id) && reservationId.equals(that.reservationId)
                && idempotencyKey.equals(that.idempotencyKey) && status == that.status && amount.equals(that.amount)
                && paymentMethod.equals(that.paymentMethod)
                && Objects.equals(externalReference, that.externalReference)
                && Objects.equals(statusDetail, that.statusDetail) && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, reservationId, idempotencyKey, status, amount, paymentMethod, externalReference,
                statusDetail, createdAt);
    }

    @Override
    public String toString() {
        return "ChargeIntent[id=" + id + ", reservationId=" + reservationId + ", status=" + status + "]";
    }
}
