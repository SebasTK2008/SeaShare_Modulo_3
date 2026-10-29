package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import com.seashare.seasharem3.support.InMemoryChargeRecordRepository;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.InMemoryReservationInformationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterChargeResultServiceTest {

    private static final String EXTERNAL_REFERENCE = "987654";

    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final InMemoryChargeRecordRepository records = new InMemoryChargeRecordRepository();
    private final InMemoryReservationInformationRepository reservations = new InMemoryReservationInformationRepository();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RegisterChargeResultService service = new RegisterChargeResultService(intents, records, records,
            reservations, failures, Clock.systemUTC());

    private ChargeIntent storeIntent(ReservationInformation reservation) {
        ChargeIntent intent = ChargeFixtures.pendingIntentWithReference(reservation, EXTERNAL_REFERENCE);
        intents.save(intent);
        return intent;
    }

    private ReservationInformation registerCalculatedReservation() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        reservations.save(reservation);
        reservations.saveCalculatedAmounts(reservation);
        return reservation;
    }

    @Test
    void capturesTheChargeAndCreatesTheAuditRecord() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        ChargeIntent stored = intents.findById(intent.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ChargeIntentStatus.CAPTURED);
        assertThat(stored.statusDetail()).isEqualTo("accredited");
        assertThat(records.count()).isEqualTo(1);
        assertThat(records.findByChargeIntentId(intent.id()).orElseThrow().amount())
                .isEqualTo(ChargeFixtures.TOTAL);
        assertThat(records.findByChargeIntentId(intent.id()).orElseThrow().ownerId())
                .isEqualTo(reservation.ownerId());
    }

    @Test
    void keepsTheAmountsOfTheCalculatedValueInTheAuditRecord() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        assertThat(records.findByChargeIntentId(intent.id()).orElseThrow().rentalAmount())
                .isEqualTo(reservation.rentalAmount());
        assertThat(records.findByChargeIntentId(intent.id()).orElseThrow().insuranceAmount())
                .isEqualTo(reservation.insuranceAmount());
        assertThat(records.findByChargeIntentId(intent.id()).orElseThrow().depositAmount())
                .isEqualTo(reservation.depositAmount());
    }

    @Test
    void doesNotCreateAnAuditRecordForARejectedCharge() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("rejected", EXTERNAL_REFERENCE, "cc_rejected_insufficient_amount"));

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.REJECTED);
        assertThat(records.count()).isZero();
    }

    @Test
    void registersTheExpirationWithoutAssumingFunds() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("expired", EXTERNAL_REFERENCE, "expired"));

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.EXPIRED);
        assertThat(records.count()).isZero();
    }

    @Test
    void keepsTheChargeInProcessWithoutCreatingAnAuditRecord() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("in_process", EXTERNAL_REFERENCE, "pending_review"));

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.IN_PROCESS);
        assertThat(records.count()).isZero();
    }

    @Test
    void isIdempotentWhenTheSameResultIsRepeated() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));
        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        assertThat(records.count()).isEqualTo(1);
        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.CAPTURED);
    }

    @Test
    void keepsTheRegisteredDetailWhenTheRepeatedResultHasNoDetail() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));
        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, null));

        assertThat(intents.findById(intent.id()).orElseThrow().statusDetail()).isEqualTo("accredited");
    }

    @Test
    void recordsTheFailureWhenThereIsNoAssociatedIntent() {
        service.register(new ChargeGatewayResult("approved", "999999", "accredited"));

        assertThat(records.count()).isZero();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void recordsTheFailureWhenTheGatewayReportsAnUnknownStatus() {
        ReservationInformation reservation = registerCalculatedReservation();
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("something_else", EXTERNAL_REFERENCE, "detail"));

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.PENDING_SEND);
        assertThat(records.count()).isZero();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void keepsTheCapturedIntentWhenTheReservationHasNoCalculatedAmountsLeft() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        reservations.save(reservation);
        ChargeIntent intent = storeIntent(reservation);

        service.register(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.CAPTURED);
        assertThat(records.count()).isZero();
        assertThat(failures.failures()).hasSize(1);
    }
}
