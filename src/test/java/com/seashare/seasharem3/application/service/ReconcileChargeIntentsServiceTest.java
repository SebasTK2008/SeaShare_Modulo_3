package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import com.seashare.seasharem3.support.InMemoryChargeRecordRepository;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.StubChargeGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileChargeIntentsServiceTest {

    private static final String EXTERNAL_REFERENCE = "987654";

    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final InMemoryChargeRecordRepository records = new InMemoryChargeRecordRepository();
    private final StubChargeGateway gateway = new StubChargeGateway();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RecordingChargeResults results = new RecordingChargeResults();
    private final ReconcileChargeIntentsService service = new ReconcileChargeIntentsService(intents, records,
            gateway, results, failures);

    @Test
    void queriesTheGatewayAndRegistersTheResultOfAnUnfinishedCharge() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .withExternalReference(EXTERNAL_REFERENCE);
        intents.save(intent);
        gateway.withFetchResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        service.reconcile();

        assertThat(results.registered()).hasSize(1);
        assertThat(results.registered().get(0).status()).isEqualTo("approved");
    }

    @Test
    void doesNotCloseTheIntentWhenTheGatewayDoesNotKnowThePayment() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .withExternalReference(EXTERNAL_REFERENCE);
        intents.save(intent);

        service.reconcile();

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.IN_PROCESS);
        assertThat(results.registered()).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void registersTheFailureOnlyOnceForTheSameIntent() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation)
                .transitionTo(ChargeIntentStatus.COMMUNICATION_ERROR);
        intents.save(intent);

        service.reconcile();
        service.reconcile();

        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void recreatesTheAuditRecordOfACapturedChargeWithoutRecord() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.capturedIntent(reservation, EXTERNAL_REFERENCE);
        intents.save(intent);
        gateway.withFetchResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        service.reconcile();

        assertThat(results.registered()).hasSize(1);
    }

    @Test
    void recordsTheFailureWhenTheIntentHasNoExternalReference() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation)
                .transitionTo(ChargeIntentStatus.COMMUNICATION_ERROR);
        intents.save(intent);

        service.reconcile();

        assertThat(failures.failures()).hasSize(1);
        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.COMMUNICATION_ERROR);
    }

    @Test
    void recordsTheFailureWhenTheGatewayDoesNotAnswer() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .withExternalReference(EXTERNAL_REFERENCE);
        intents.save(intent);
        gateway.withFetchFailure(new GatewayCommunicationException("The payment gateway did not answer"));

        service.reconcile();

        assertThat(failures.failures()).hasSize(1);
        assertThat(results.registered()).isEmpty();
    }

    @Test
    void ignoresTheCapturedChargesThatAlreadyHaveTheirAuditRecord() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.capturedIntent(reservation, EXTERNAL_REFERENCE);
        intents.save(intent);
        records.save(ChargeRecord.create(UUID.randomUUID(), intent, reservation, ChargeFixtures.CREATED_AT));

        service.reconcile();

        assertThat(results.registered()).isEmpty();
        assertThat(failures.failures()).isEmpty();
    }

    /** Doble del puerto de registro: los servicios no se invocan entre si, solo por su puerto. */
    private static final class RecordingChargeResults implements RegisterChargeResultUseCase {

        private final List<ChargeGatewayResult> registered = new ArrayList<>();

        @Override
        public void register(ChargeGatewayResult result) {
            registered.add(result);
        }

        List<ChargeGatewayResult> registered() {
            return List.copyOf(registered);
        }
    }
}
