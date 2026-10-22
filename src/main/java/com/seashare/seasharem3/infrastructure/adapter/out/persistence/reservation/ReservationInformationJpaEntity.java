package com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation;

import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mapeo de {@code reservation_information} (general-plan §4; migración V1). El upsert de UC03 escribe
 * las columnas informativas y anula las calculadas ({@link #applyInformative} + {@link #invalidateCalculated});
 * UC04 escribe solo las calculadas ({@link #applyCalculated}). No sale del adaptador.
 */
@Entity
@Table(name = "reservation_information")
public class ReservationInformationJpaEntity {

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "boat_id", nullable = false)
    private UUID boatId;

    @Column(name = "base_rate", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseRate;

    @Column(name = "base_rate_parameters_id")
    private UUID baseRateParametersId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "passengers", nullable = false)
    private int passengers;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "rental_amount", precision = 18, scale = 4)
    private BigDecimal rentalAmount;

    @Column(name = "insurance_amount", precision = 18, scale = 4)
    private BigDecimal insuranceAmount;

    @Column(name = "deposit_amount", precision = 18, scale = 4)
    private BigDecimal depositAmount;

    @Column(name = "total_amount", precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "frozen_parameters_id")
    private UUID frozenParametersId;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    protected ReservationInformationJpaEntity() {
    }

    static ReservationInformationJpaEntity from(ReservationInformation information) {
        ReservationInformationJpaEntity entity = new ReservationInformationJpaEntity();
        entity.applyInformative(information);
        entity.applyCalculated(information);
        return entity;
    }

    /** Columnas informativas del upsert de UC03: tarifa base y datos de la reserva. */
    void applyInformative(ReservationInformation information) {
        this.reservationId = information.reservationId().value();
        this.boatId = information.boatId().value();
        this.baseRate = information.baseRate().amount().amount();
        this.baseRateParametersId = information.baseRateParametersId();
        this.startDate = information.startDate();
        this.endDate = information.endDate();
        this.passengers = information.passengers();
        this.ownerId = information.ownerId().value();
        this.maxCapacity = information.maxCapacity();
    }

    /** D-UC03-02: un upsert sobre una reserva ya calculada anula las columnas calculadas. */
    void invalidateCalculated() {
        this.rentalAmount = null;
        this.insuranceAmount = null;
        this.depositAmount = null;
        this.totalAmount = null;
        this.frozenParametersId = null;
        this.calculatedAt = null;
    }

    /** Columnas calculadas por UC04 (escritas juntas, D-27). */
    void applyCalculated(ReservationInformation information) {
        this.rentalAmount = amountOrNull(information.rentalAmount());
        this.insuranceAmount = amountOrNull(information.insuranceAmount());
        this.depositAmount = amountOrNull(information.depositAmount());
        this.totalAmount = amountOrNull(information.totalAmount());
        this.frozenParametersId = information.frozenParametersId();
        this.calculatedAt = information.calculatedAt();
    }

    private static BigDecimal amountOrNull(Money money) {
        return money != null ? money.amount() : null;
    }

    UUID getReservationId() {
        return reservationId;
    }

    UUID getBoatId() {
        return boatId;
    }

    BigDecimal getBaseRate() {
        return baseRate;
    }

    UUID getBaseRateParametersId() {
        return baseRateParametersId;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    int getPassengers() {
        return passengers;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    int getMaxCapacity() {
        return maxCapacity;
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

    BigDecimal getTotalAmount() {
        return totalAmount;
    }

    UUID getFrozenParametersId() {
        return frozenParametersId;
    }

    Instant getCalculatedAt() {
        return calculatedAt;
    }
}
