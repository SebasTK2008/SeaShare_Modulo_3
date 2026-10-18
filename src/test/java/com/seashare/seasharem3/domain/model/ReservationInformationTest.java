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
        return ReservationInformation.create(new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(new BigDecimal("350000.00"))), null, START, END, 2,
                new OwnerId(UUID.randomUUID()), 4);
    }

    @Test
    void creaSinMontosCalculados() {
        ReservationInformation information = create();

        assertThat(information.isCalculated()).isFalse();
        assertThat(information.totalAmount()).isNull();
        assertThat(information.frozenParametersId()).isNull();
        assertThat(information.calculatedAt()).isNull();
    }

    @Test
    void rechazaFechasInvertidas() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, END, START, 2, new OwnerId(UUID.randomUUID()), 4));
    }

    @Test
    void rechazaPasajerosFueraDeRango() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, START, END, 5, new OwnerId(UUID.randomUUID()), 4));
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, START, END, 0, new OwnerId(UUID.randomUUID()), 4));
    }

    @Test
    void rechazaCapacidadMenorQueUno() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ReservationInformation.create(
                new ReservationId(UUID.randomUUID()), new BoatId(UUID.randomUUID()),
                new BaseRate(new Money(BigDecimal.TEN)), null, START, END, 1, new OwnerId(UUID.randomUUID()), 0));
    }

    @Test
    void fijaMontosCalculadosYValidaElTotal() {
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
    void rechazaTotalIncoherenteConElDesglose() {
        ReservationInformation information = create();
        ReservationValue value = new ReservationValue(new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("60000.00")), new Money(new BigDecimal("30000.00")),
                new Money(new BigDecimal("1000000.00")));

        assertThatExceptionOfType(DomainException.class).isThrownBy(
                () -> information.withCalculated(value, UUID.randomUUID(), Instant.now()));
    }

    @Test
    void invalidaLosMontosCalculados() {
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
    void reemplazaLaTarifaBaseYSuVersionDeParametros() {
        ReservationInformation information = create();
        UUID parametersId = UUID.randomUUID();

        ReservationInformation updated = information.withBaseRate(new BaseRate(new Money(new BigDecimal("400000.00"))),
                parametersId);

        assertThat(updated.baseRate().amount().amount()).isEqualByComparingTo(new BigDecimal("400000.0000"));
        assertThat(updated.baseRateParametersId()).isEqualTo(parametersId);
    }
}
