package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.dto.ProcessChargeCommand;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.domain.exception.ReservationValueNotCalculatedException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import com.seashare.seasharem3.support.RecordingChargeCommandPublisher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProcessChargeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:04:05Z");
    private static final String OPERATION_KEY = "7f0f0f6a-0000|PENDING|2026-10-20T15:04:05Z";
    private static final Money TOTAL = new Money(new BigDecimal("990000.00"));

    private final InMemoryReservationInformationRepository reservations = new InMemoryReservationInformationRepository();
    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final RecordingChargeCommandPublisher publisher = new RecordingChargeCommandPublisher();
    private final ProcessChargeService service = new ProcessChargeService(reservations, intents, publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private UUID registerCalculatedReservation() {
        ReservationInformation information = ReservationInformation.create(new ReservationId(UUID.randomUUID()),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), 2, new OwnerId(UUID.randomUUID()), 4);
        reservations.save(information);
        reservations.saveCalculatedAmounts(information.withCalculated(new ReservationValue(
                new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                new Money(new BigDecimal("30000.00")), TOTAL), UUID.randomUUID(), NOW));
        return information.reservationId().value();
    }

    private UUID registerReservationWithoutCalculatedAmounts() {
        ReservationInformation information = ReservationInformation.create(new ReservationId(UUID.randomUUID()),
                new BoatId(UUID.randomUUID()), new BaseRate(new Money(new BigDecimal("350000.00"))), null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), 2, new OwnerId(UUID.randomUUID()), 4);
        reservations.save(information);
        return information.reservationId().value();
    }

    private static ProcessChargeCommand command(UUID reservationId, String operationKey) {
        return new ProcessChargeCommand(reservationId, "token-123", "CREDIT_CARD",
                Map.of("payer_email", "arrendatario@example.com", "last_four", "4242"), operationKey);
    }

    @Test
    void registersIntentAndPublishesCommandForCalculatedTotal() {
        UUID reservationId = registerCalculatedReservation();

        service.process(command(reservationId, OPERATION_KEY));

        ChargeIntent intent = intents.findLatestByReservationId(
                new ReservationId(reservationId)).orElseThrow();
        assertThat(intent.status()).isEqualTo(ChargeIntentStatus.PENDING_SEND);
        assertThat(intent.amount()).isEqualTo(TOTAL);
        assertThat(intent.paymentMethod().paymentTokenRef()).isEqualTo("token-123");
        assertThat(intent.paymentMethod().paymentMethodType()).isEqualTo("CREDIT_CARD");
        assertThat(intent.paymentMethod().lastFour()).isEqualTo("4242");
        assertThat(intent.createdAt()).isEqualTo(NOW);
        assertThat(publisher.publishedCount()).isEqualTo(1);
        assertThat(publisher.published().get(0).chargeIntentId()).isEqualTo(intent.id());
        assertThat(publisher.published().get(0).payerEmail()).isEqualTo("arrendatario@example.com");
    }

    @Test
    void chargesASingleAmountEqualToTheCalculatedTotal() {
        UUID reservationId = registerCalculatedReservation();

        service.process(command(reservationId, OPERATION_KEY));

        ChargeIntent intent = intents.findLatestByReservationId(
                new ReservationId(reservationId)).orElseThrow();
        assertThat(intent.amount()).isEqualTo(TOTAL);
        assertThat(intent.amount().amount()).isEqualByComparingTo(new BigDecimal("990000.0000"));
    }

    @Test
    void rejectsChargeWithoutCalculatedTotal() {
        UUID reservationId = registerReservationWithoutCalculatedAmounts();

        assertThatExceptionOfType(ReservationValueNotCalculatedException.class)
                .isThrownBy(() -> service.process(command(reservationId, OPERATION_KEY)));

        assertThat(intents.findLatestByReservationId(
                new ReservationId(reservationId))).isEmpty();
        assertThat(publisher.publishedCount()).isZero();
    }

    @Test
    void rejectsChargeForUnknownReservation() {
        assertThatExceptionOfType(ReservationValueNotCalculatedException.class)
                .isThrownBy(() -> service.process(command(UUID.randomUUID(), OPERATION_KEY)));

        assertThat(publisher.publishedCount()).isZero();
    }

    @Test
    void rejectsChargeWithoutPaymentToken() {
        UUID reservationId = registerCalculatedReservation();
        ProcessChargeCommand withoutToken = new ProcessChargeCommand(reservationId, "  ", "CREDIT_CARD",
                Map.of("payer_email", "arrendatario@example.com"), OPERATION_KEY);

        assertThatExceptionOfType(InvalidPaymentTokenException.class)
                .isThrownBy(() -> service.process(withoutToken));

        assertThat(publisher.publishedCount()).isZero();
    }

    @Test
    void publishesThePayerEmailOfTheCommand() {
        UUID reservationId = registerCalculatedReservation();
        ProcessChargeCommand withoutEmail = new ProcessChargeCommand(reservationId, "token-123", "CREDIT_CARD",
                Map.of(), OPERATION_KEY);

        service.process(withoutEmail);

        assertThat(publisher.publishedCount()).isEqualTo(1);
        assertThat(publisher.published().get(0).payerEmail()).isNull();
    }

    @Test
    void doesNotDuplicateIntentForTheSameOperationKey() {
        UUID reservationId = registerCalculatedReservation();

        service.process(command(reservationId, OPERATION_KEY));
        service.process(command(reservationId, OPERATION_KEY));

        assertThat(intents.findByStatusIn(List.of(ChargeIntentStatus.PENDING_SEND))).hasSize(1);
        assertThat(publisher.publishedCount()).isEqualTo(1);
    }

    @Test
    void allowsANewIntentForANewOperationKey() {
        UUID reservationId = registerCalculatedReservation();

        service.process(command(reservationId, OPERATION_KEY));
        service.process(command(reservationId, OPERATION_KEY + "-retry"));

        assertThat(intents.findByStatusIn(List.of(ChargeIntentStatus.PENDING_SEND))).hasSize(2);
        assertThat(publisher.publishedCount()).isEqualTo(2);
    }

    @Test
    void doesNotOpenANewIntentWhenTheReservationIsAlreadyCaptured() {
        UUID reservationId = registerCalculatedReservation();
        service.process(command(reservationId, OPERATION_KEY));
        ChargeIntent captured = intents.findByStatusIn(List.of(ChargeIntentStatus.PENDING_SEND)).get(0)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .transitionTo(ChargeIntentStatus.CAPTURED);
        intents.save(captured);

        service.process(command(reservationId, OPERATION_KEY + "-retry"));

        assertThat(intents.findByStatusIn(List.of(ChargeIntentStatus.PENDING_SEND))).isEmpty();
        assertThat(publisher.publishedCount()).isEqualTo(1);
    }

    @Test
    void keepsThePaymentTokenOutOfThePublishedCommand() {
        UUID reservationId = registerCalculatedReservation();

        service.process(command(reservationId, OPERATION_KEY));

        assertThat(Arrays.stream(ChargeCommand.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(name -> name.toLowerCase().contains("token"));
    }
}
