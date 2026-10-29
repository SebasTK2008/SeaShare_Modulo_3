package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de cobro: métrica financiera de auditoría, inmutable, creada únicamente cuando la
 * pasarela confirma la captura del cobro. Copia del propietario, la embarcación y el desglose del
 * valor calculado, y conserva la referencia a la intención que lo originó.
 *
 * <p>No se crea para {@code REJECTED}, {@code CANCELLED}, {@code EXPIRED}, {@code IN_PROCESS} ni
 * {@code COMMUNICATION_ERROR}: el registro existe solo para un cobro capturado. No guarda el token del
 * medio de pago, solo el tipo y los últimos cuatro dígitos.
 *
 * <p>Fuente: SPEC RF-004B, RF-006, RF-008, RF-010, RF-012 y RF-015; UC05 plan T003 y regla 6.</p>
 */
public final class ChargeRecord {

    private final UUID id;
    private final UUID chargeIntentId;
    private final ReservationId reservationId;
    private final OwnerId ownerId;
    private final BoatId boatId;
    private final Money amount;
    private final Money rentalAmount;
    private final Money insuranceAmount;
    private final Money depositAmount;
    private final String paymentMethodType;
    private final String lastFour;
    private final String externalReference;
    private final Instant createdAt;

    private ChargeRecord(UUID id, UUID chargeIntentId, ReservationId reservationId, OwnerId ownerId, BoatId boatId,
            Money amount, Money rentalAmount, Money insuranceAmount, Money depositAmount, String paymentMethodType,
            String lastFour, String externalReference, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.chargeIntentId = Objects.requireNonNull(chargeIntentId, "chargeIntentId");
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.boatId = Objects.requireNonNull(boatId, "boatId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.rentalAmount = Objects.requireNonNull(rentalAmount, "rentalAmount");
        this.insuranceAmount = Objects.requireNonNull(insuranceAmount, "insuranceAmount");
        this.depositAmount = Objects.requireNonNull(depositAmount, "depositAmount");
        this.paymentMethodType = paymentMethodType;
        this.lastFour = lastFour;
        this.externalReference = externalReference;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Crea el registro de auditoría a partir de la intención capturada y de la reserva que motivó el
     * cobro: el propietario, la embarcacion y el desglose se copian de la informacion de reserva y el
     * monto debe coincidir con el valor total calculado (CE-001: cero discrepancias).
     *
     * <p>Si la reserva ya no tiene el desglose calculado, o el monto cobrado difiere del valor
     * calculado, este método lanza {@link DomainException}. El cobro está confirmado por la pasarela
     * y el llamador no debe perderlo: debe registrar el fallo operativo y conservar la intención en
     * {@code CAPTURED} sin crear el registro de auditoría, en vez de abortar la transacción completa.</p>
     *
     * <p>Fuente: SPEC RF-004B, RF-006 y RF-010; UC05 plan T003 y regla 6.</p>
     */
    public static ChargeRecord create(UUID id, ChargeIntent intent, ReservationInformation reservation,
            Instant createdAt) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(createdAt, "createdAt");

        if (intent.status() != ChargeIntentStatus.CAPTURED) {
            throw new DomainException(
                    "A charge record can only be created for a captured charge, not for " + intent.status());
        }
        if (!intent.reservationId().equals(reservation.reservationId())) {
            throw new DomainException("The charge intent " + intent.id() + " belongs to reservation "
                    + intent.reservationId().value() + ", not to reservation "
                    + reservation.reservationId().value());
        }
        Money calculatedTotal = reservation.totalAmount();
        if (calculatedTotal == null) {
            throw new DomainException("The reservation " + reservation.reservationId().value()
                    + " has no calculated total to charge");
        }
        if (!intent.amount().equals(calculatedTotal)) {
            throw new DomainException("The charged amount " + intent.amount().amount()
                    + " does not match the calculated total " + calculatedTotal.amount()
                    + " of reservation " + reservation.reservationId().value());
        }

        return new ChargeRecord(id, intent.id(), reservation.reservationId(), reservation.ownerId(),
                reservation.boatId(), intent.amount(), reservation.rentalAmount(),
                reservation.insuranceAmount(), reservation.depositAmount(),
                intent.paymentMethod().paymentMethodType(), intent.paymentMethod().lastFour(),
                intent.externalReference(), createdAt);
    }

    /**
     * Rehidrata un registro ya persistido. No revalida los invariantes: la tabla es inmutable y la
     * base de datos es la fuente de verdad.
     * Fuente: UC05 plan T009 (persistencia).
     */
    public static ChargeRecord restore(UUID id, UUID chargeIntentId, ReservationId reservationId, OwnerId ownerId,
            BoatId boatId, Money amount, Money rentalAmount, Money insuranceAmount, Money depositAmount,
            String paymentMethodType, String lastFour, String externalReference, Instant createdAt) {
        return new ChargeRecord(id, chargeIntentId, reservationId, ownerId, boatId, amount, rentalAmount,
                insuranceAmount, depositAmount, paymentMethodType, lastFour, externalReference, createdAt);
    }

    public UUID id() {
        return id;
    }

    /** Intencion de cobro que origino el registro (trazabilidad directa, RF-004B). */
    public UUID chargeIntentId() {
        return chargeIntentId;
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

    /** Monto total cobrado (unico monto: alquiler + seguro + deposito, RF-015). */
    public Money amount() {
        return amount;
    }

    public Money rentalAmount() {
        return rentalAmount;
    }

    public Money insuranceAmount() {
        return insuranceAmount;
    }

    public Money depositAmount() {
        return depositAmount;
    }

    /** Tipo del medio de pago; metadato no sensible (RF-012). */
    public String paymentMethodType() {
        return paymentMethodType;
    }

    /** Ultimos cuatro digitos del medio de pago; metadato no sensible (RF-012, CE-006). */
    public String lastFour() {
        return lastFour;
    }

    /** Referencia externa del pago en la pasarela (RF-012). */
    public String externalReference() {
        return externalReference;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChargeRecord that)) {
            return false;
        }
        return id.equals(that.id) && chargeIntentId.equals(that.chargeIntentId)
                && reservationId.equals(that.reservationId) && ownerId.equals(that.ownerId)
                && boatId.equals(that.boatId) && amount.equals(that.amount)
                && rentalAmount.equals(that.rentalAmount) && insuranceAmount.equals(that.insuranceAmount)
                && depositAmount.equals(that.depositAmount)
                && Objects.equals(paymentMethodType, that.paymentMethodType)
                && Objects.equals(lastFour, that.lastFour)
                && Objects.equals(externalReference, that.externalReference) && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chargeIntentId, reservationId, ownerId, boatId, amount, rentalAmount,
                insuranceAmount, depositAmount, paymentMethodType, lastFour, externalReference, createdAt);
    }

    @Override
    public String toString() {
        return "ChargeRecord[id=" + id + ", chargeIntentId=" + chargeIntentId + ", reservationId=" + reservationId
                + ", amount=" + amount.amount() + "]";
    }
}
