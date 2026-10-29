package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChargeRecordTest {

    private static final Money TOTAL = new Money(new BigDecimal("990000.00"));
    private static final Instant CREATED_AT = Instant.parse("2026-10-20T15:04:05Z");

    private static ReservationInformation calculatedReservation() {
        ReservationInformation information = ReservationInformation.create(new ReservationId(UUID.randomUUID()),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), 2, new OwnerId(UUID.randomUUID()), 4);
        return information.withCalculated(new ReservationValue(new Money(new BigDecimal("900000.00")),
                new Money(new BigDecimal("60000.00")), new Money(new BigDecimal("30000.00")), TOTAL),
                UUID.randomUUID(), CREATED_AT);
    }

    private static ChargeIntent capturedIntent(ReservationInformation reservation, Money amount) {
        return ChargeIntent.create(UUID.randomUUID(), reservation.reservationId(),
                new IdempotencyKey("reservation|PENDING|2026-10-20T15:04:05Z"), amount,
                new PaymentMethodReference("token-123", "credit_card", "4242"), CREATED_AT)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .transitionTo(ChargeIntentStatus.CAPTURED)
                .withExternalReference("1234567890");
    }

    @Test
    void copiesReservationOwnerBoatAndBreakdown() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(reservation, TOTAL);

        ChargeRecord record = ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT);

        assertThat(record.chargeIntentId()).isEqualTo(intent.id());
        assertThat(record.reservationId()).isEqualTo(reservation.reservationId());
        assertThat(record.ownerId()).isEqualTo(reservation.ownerId());
        assertThat(record.boatId()).isEqualTo(reservation.boatId());
        assertThat(record.amount()).isEqualTo(TOTAL);
        assertThat(record.rentalAmount()).isEqualTo(reservation.rentalAmount());
        assertThat(record.insuranceAmount()).isEqualTo(reservation.insuranceAmount());
        assertThat(record.depositAmount()).isEqualTo(reservation.depositAmount());
        assertThat(record.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void keepsOnlyNonSensitivePaymentMetadata() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(reservation, TOTAL);

        ChargeRecord record = ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT);

        assertThat(record.paymentMethodType()).isEqualTo("credit_card");
        assertThat(record.lastFour()).isEqualTo("4242");
        assertThat(record.externalReference()).isEqualTo("1234567890");
        assertThat(record.toString()).doesNotContain("token-123");
        assertThat(Arrays.stream(ChargeRecord.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("token"));
    }

    @Test
    void rejectsIntentThatIsNotCaptured() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent pending = ChargeIntent.create(UUID.randomUUID(), reservation.reservationId(),
                new IdempotencyKey("reservation|PENDING|2026-10-20T15:04:05Z"), TOTAL,
                new PaymentMethodReference("token-123", "credit_card", "4242"), CREATED_AT);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> ChargeRecord.create(UUID.randomUUID(), pending, reservation, CREATED_AT));
    }

    @Test
    void rejectsIntentOfAnotherReservation() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(calculatedReservation(), TOTAL);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT));
    }

    @Test
    void rejectsReservationWithoutCalculatedTotal() {
        ReservationInformation reservation = ReservationInformation.create(new ReservationId(UUID.randomUUID()),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), 2, new OwnerId(UUID.randomUUID()), 4);
        ChargeIntent intent = capturedIntent(reservation, TOTAL);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT));
    }

    @Test
    void rejectsAmountThatDoesNotMatchTheCalculatedTotal() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(reservation, new Money(new BigDecimal("1000000.00")));

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT));
    }

    @Test
    void exposesEveryValueOfTheCharge() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(reservation, TOTAL).withExternalReference("1234567890");

        ChargeRecord record = ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT);

        assertThat(record.id()).isNotNull();
        assertThat(record.chargeIntentId()).isEqualTo(intent.id());
        assertThat(record.reservationId()).isEqualTo(reservation.reservationId());
        assertThat(record.ownerId()).isEqualTo(reservation.ownerId());
        assertThat(record.boatId()).isEqualTo(reservation.boatId());
        assertThat(record.amount()).isEqualTo(TOTAL);
        assertThat(record.rentalAmount()).isEqualTo(reservation.rentalAmount());
        assertThat(record.insuranceAmount()).isEqualTo(reservation.insuranceAmount());
        assertThat(record.depositAmount()).isEqualTo(reservation.depositAmount());
        assertThat(record.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void comparesByValueAndKeepsItsIdentity() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(reservation, TOTAL).withExternalReference("1234567890");
        ChargeRecord record = ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT);
        ChargeRecord same = ChargeRecord.create(record.id(), intent, reservation, CREATED_AT);
        ChargeRecord other = ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT);

        assertThat(record).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(record).isNotEqualTo(other);
        assertThat(record).isNotEqualTo(intent);
        assertThat(record.toString()).contains(record.id().toString()).contains(TOTAL.amount().toString());
    }

    @Test
    void restoresARecordAlreadyPersisted() {
        ReservationInformation reservation = calculatedReservation();
        ChargeIntent intent = capturedIntent(reservation, TOTAL).withExternalReference("1234567890");
        ChargeRecord record = ChargeRecord.create(UUID.randomUUID(), intent, reservation, CREATED_AT);

        ChargeRecord restored = ChargeRecord.restore(record.id(), record.chargeIntentId(), record.reservationId(),
                record.ownerId(), record.boatId(), record.amount(), record.rentalAmount(),
                record.insuranceAmount(), record.depositAmount(), record.paymentMethodType(), record.lastFour(),
                record.externalReference(), record.createdAt());

        assertThat(restored).isEqualTo(record);
    }

    @Test
    void isImmutableWithoutSetters() {
        assertThat(Arrays.stream(ChargeRecord.class.getDeclaredFields()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(ChargeRecord.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void exposesNoMutableStateHolder() {
        assertThat(Arrays.stream(ChargeRecord.class.getDeclaredFields()).map(Field::getType))
                .noneMatch(type -> type.isArray() || Collection.class.isAssignableFrom(type));
    }
}
