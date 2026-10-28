package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ReservationInformationTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 10);

    private static ReservationInformation create() {
        return create(START, END, 2, 4);
    }

    private static ReservationInformation create(LocalDate start, LocalDate end, int passengers, int maxCapacity) {
        return ReservationInformation.create(new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(new BigDecimal("350000.00"))), null, start, end, passengers,
                new OwnerId(UUID.randomUUID()), maxCapacity);
    }

    @Test
    void createsWithoutCalculatedAmounts() {
        ReservationInformation information = create();

        assertThat(information.isCalculated()).isFalse();
        assertThat(information.totalAmount()).isNull();
        assertThat(information.frozenParametersId()).isNull();
        assertThat(information.calculatedAt()).isNull();
    }

    @Test
    void acceptsSameStartAndEndDate() {
        ReservationInformation information = create(START, START, 2, 4);

        assertThat(information.startDate()).isEqualTo(information.endDate());
    }

    @Test
    void acceptsOnePassenger() {
        ReservationInformation information = create(START, END, 1, 4);

        assertThat(information.passengers()).isEqualTo(1);
    }

    @Test
    void acceptsPassengersAtCapacity() {
        ReservationInformation information = create(START, END, 4, 4);

        assertThat(information.passengers()).isEqualTo(information.maxCapacity());
    }

    @Test
    void rejectsInvertedDates() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, END, START, 2, new OwnerId(UUID.randomUUID()), 4));
    }

    @Test
    void rejectsPassengersOutOfRange() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, START, END, 5, new OwnerId(UUID.randomUUID()), 4));
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, START, END, 0, new OwnerId(UUID.randomUUID()), 4));
    }

    @Test
    void rejectsCapacityLessThanOne() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, START, END, 1, new OwnerId(UUID.randomUUID()), 0));
    }

    @Test
    void setsCalculatedAmountsAndValidatesTotal() {
        ReservationInformation information = create();
        ReservationValue value = new ReservationValue(new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("60000.00")), new Money(new BigDecimal("30000.00")),
                new Money(new BigDecimal("990000.00")));
        UUID parametersId = UUID.randomUUID();

        ReservationInformation calculated = information.withCalculated(value, parametersId, Instant.parse("2026-09-20T15:04:05Z"));

        assertThat(calculated.isCalculated()).isTrue();
        assertThat(calculated.rentalAmount()).isEqualTo(value.rentalAmount());
        assertThat(calculated.depositAmount()).isEqualTo(value.guaranteeDeposit());
        assertThat(calculated.frozenParametersId()).isEqualTo(parametersId);
        assertThat(information.isCalculated()).isFalse();
    }

    @Test
    void rejectsTotalInconsistentWithBreakdown() {
        ReservationInformation information = create();
        ReservationValue value = new ReservationValue(new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("60000.00")), new Money(new BigDecimal("30000.00")),
                new Money(new BigDecimal("1000000.00")));

        assertThatExceptionOfType(DomainException.class).isThrownBy(
                () -> information.withCalculated(value, UUID.randomUUID(), Instant.now()));
    }

    @Test
    void invalidatesCalculatedAmounts() {
        ReservationInformation information = create();
        ReservationValue value = new ReservationValue(new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("60000.00")), new Money(new BigDecimal("30000.00")),
                new Money(new BigDecimal("990000.00")));
        ReservationInformation calculated = information.withCalculated(value, UUID.randomUUID(),
                Instant.parse("2026-09-20T15:04:05Z"));

        ReservationInformation invalidated = calculated.invalidateCalculatedAmounts();

        assertThat(invalidated.isCalculated()).isFalse();
        assertThat(invalidated.rentalAmount()).isNull();
        assertThat(invalidated.insuranceAmount()).isNull();
        assertThat(invalidated.depositAmount()).isNull();
        assertThat(invalidated.totalAmount()).isNull();
        assertThat(invalidated.frozenParametersId()).isNull();
        assertThat(invalidated.calculatedAt()).isNull();
    }

    @Test
    void replacesBaseRateAndItsParametersVersion() {
        ReservationInformation information = create();
        UUID parametersId = UUID.randomUUID();

        ReservationInformation updated = information.withBaseRate(new BaseRate(new Money(new BigDecimal("400000.00"))),
                parametersId);

        assertThat(updated.baseRate().amount().amount()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(updated.baseRateParametersId()).isEqualTo(parametersId);
    }
}
