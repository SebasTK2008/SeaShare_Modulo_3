package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Información de reserva (entidad compartida por UC03/UC04).
 * Fuente: general-plan §4, §3.3 y §11; UC03 plan T008; UC04 plan (montos calculados y D-27).
 * Inmutable: los cambios se expresan con copias (withCalculated/invalidate/withBaseRate).
 */
public final class ReservationInformation {

    private final ReservationId reservationId;
    private final BoatId boatId;
    private final BaseRate baseRate;
    private final UUID baseRateParametersId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int passengers;
    private final OwnerId ownerId;
    private final int maxCapacity;
    private final Money rentalAmount;
    private final Money insuranceAmount;
    private final Money depositAmount;
    private final Money totalAmount;
    private final UUID frozenParametersId;
    private final Instant calculatedAt;

    private ReservationInformation(ReservationId reservationId, BoatId boatId, BaseRate baseRate,
            UUID baseRateParametersId, LocalDate startDate, LocalDate endDate, int passengers, OwnerId ownerId,
            int maxCapacity, Money rentalAmount, Money insuranceAmount, Money depositAmount, Money totalAmount,
            UUID frozenParametersId, Instant calculatedAt) {
        this.reservationId = Objects.requireNonNull(reservationId, "reservationId");
        this.boatId = Objects.requireNonNull(boatId, "boatId");
        this.baseRate = Objects.requireNonNull(baseRate, "baseRate");
        this.baseRateParametersId = baseRateParametersId;
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.endDate = Objects.requireNonNull(endDate, "endDate");
        this.passengers = passengers;
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.maxCapacity = maxCapacity;
        this.rentalAmount = rentalAmount;
        this.insuranceAmount = insuranceAmount;
        this.depositAmount = depositAmount;
        this.totalAmount = totalAmount;
        this.frozenParametersId = frozenParametersId;
        this.calculatedAt = calculatedAt;
        validate();
    }

    /**
     * Crea la información de reserva sin montos calculados (estado del upsert de UC03).
     * Fuente: UC03 plan T008; general-plan §4 (montos NULL hasta UC04).
     */
    public static ReservationInformation create(ReservationId reservationId, BoatId boatId, BaseRate baseRate,
            UUID baseRateParametersId, LocalDate startDate, LocalDate endDate, int passengers, OwnerId ownerId,
            int maxCapacity) {
        return new ReservationInformation(reservationId, boatId, baseRate, baseRateParametersId, startDate, endDate,
                passengers, ownerId, maxCapacity, null, null, null, null, null, null);
    }

    /**
     * Reemplaza la tarifa base y la versión de parámetros usada por la tarifa dinámica (UC03);
     * conserva los montos calculados: el llamador decide la invalidación con {@link #invalidateCalculatedAmounts()}.
     * Fuente: general-plan §4 (columna base_rate_parameters_id).
     */
    public ReservationInformation withBaseRate(BaseRate newBaseRate, UUID newBaseRateParametersId) {
        Objects.requireNonNull(newBaseRate, "newBaseRate");
        return new ReservationInformation(reservationId, boatId, newBaseRate, newBaseRateParametersId, startDate,
                endDate, passengers, ownerId, maxCapacity, rentalAmount, insuranceAmount, depositAmount, totalAmount,
                frozenParametersId, calculatedAt);
    }

    /**
     * Fija los montos calculados por UC04 y la versión de parámetros congelada (D-27);
     * los valores no se copian de los parámetros: se resuelven de la versión referenciada.
     * Fuente: UC04 plan; general-plan §4 (escritos juntos).
     */
    public ReservationInformation withCalculated(ReservationValue value, UUID frozenParametersId,
            Instant calculatedAt) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(frozenParametersId, "frozenParametersId");
        Objects.requireNonNull(calculatedAt, "calculatedAt");
        return new ReservationInformation(reservationId, boatId, baseRate, baseRateParametersId, startDate, endDate,
                passengers, ownerId, maxCapacity, value.rentalAmount(), value.insuranceAmount(),
                value.guaranteeDeposit(), value.totalAmount(), frozenParametersId, calculatedAt);
    }

    /**
     * Anula los montos calculados, la versión congelada y la fecha de cálculo (upsert de UC03 sobre una
     * reserva ya calculada). Fuente: general-plan §4 y D-UC03-02.
     */
    public ReservationInformation invalidateCalculatedAmounts() {
        return new ReservationInformation(reservationId, boatId, baseRate, baseRateParametersId, startDate, endDate,
                passengers, ownerId, maxCapacity, null, null, null, null, null, null);
    }

    /** {@code true} si la reserva tiene los montos calculados registrados (UC04 ya calculó). */
    public boolean isCalculated() {
        return totalAmount != null;
    }

    private void validate() {
        if (endDate.isBefore(startDate)) {
            throw new DomainException("end_date no puede ser anterior a start_date: " + startDate + " – " + endDate);
        }
        if (maxCapacity < 1) {
            throw new DomainException("max_capacity debe ser al menos 1: " + maxCapacity);
        }
        if (passengers < 1 || passengers > maxCapacity) {
            throw new DomainException("passengers debe estar entre 1 y max_capacity: " + passengers);
        }
        boolean anyCalculated = rentalAmount != null || insuranceAmount != null || depositAmount != null
                || totalAmount != null || frozenParametersId != null || calculatedAt != null;
        boolean allCalculated = rentalAmount != null && insuranceAmount != null && depositAmount != null
                && totalAmount != null && frozenParametersId != null && calculatedAt != null;
        if (anyCalculated && !allCalculated) {
            throw new DomainException(
                    "Los montos calculados, frozen_parameters_id y calculated_at deben escribirse juntos");
        }
        if (allCalculated) {
            Money expectedTotal = rentalAmount.add(insuranceAmount).add(depositAmount);
            if (totalAmount.compareTo(expectedTotal) != 0) {
                throw new DomainException("total_amount debe ser alquiler + seguro + depósito: " + totalAmount
                        + " frente a " + expectedTotal);
            }
        }
    }

    public ReservationId reservationId() {
        return reservationId;
    }

    public BoatId boatId() {
        return boatId;
    }

    public BaseRate baseRate() {
        return baseRate;
    }

    /** Versión de parámetros usada por la tarifa dinámica; {@code null} si la fecha era regular. Fuente: D-24. */
    public UUID baseRateParametersId() {
        return baseRateParametersId;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public int passengers() {
        return passengers;
    }

    public OwnerId ownerId() {
        return ownerId;
    }

    public int maxCapacity() {
        return maxCapacity;
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

    public Money totalAmount() {
        return totalAmount;
    }

    /** Versión de parámetros congelada por UC04 (única fuente de comisión y seguro). Fuente: D-27. */
    public UUID frozenParametersId() {
        return frozenParametersId;
    }

    public Instant calculatedAt() {
        return calculatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReservationInformation that)) {
            return false;
        }
        return passengers == that.passengers && maxCapacity == that.maxCapacity
                && reservationId.equals(that.reservationId) && boatId.equals(that.boatId)
                && baseRate.equals(that.baseRate) && Objects.equals(baseRateParametersId, that.baseRateParametersId)
                && startDate.equals(that.startDate) && endDate.equals(that.endDate) && ownerId.equals(that.ownerId)
                && Objects.equals(rentalAmount, that.rentalAmount)
                && Objects.equals(insuranceAmount, that.insuranceAmount)
                && Objects.equals(depositAmount, that.depositAmount) && Objects.equals(totalAmount, that.totalAmount)
                && Objects.equals(frozenParametersId, that.frozenParametersId)
                && Objects.equals(calculatedAt, that.calculatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, boatId, baseRate, baseRateParametersId, startDate, endDate, passengers,
                ownerId, maxCapacity, rentalAmount, insuranceAmount, depositAmount, totalAmount, frozenParametersId,
                calculatedAt);
    }

    @Override
    public String toString() {
        return "ReservationInformation[reservationId=" + reservationId + ", boatId=" + boatId + ", startDate="
                + startDate + ", endDate=" + endDate + ", passengers=" + passengers + ", calculated=" + isCalculated()
                + "]";
    }
}
