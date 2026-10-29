package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.StubChargeGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SendChargeToGatewayServiceTest {

    private static final String PAYER_EMAIL = "arrendatario@example.com";

    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final StubChargeGateway gateway = new StubChargeGateway();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RecordingChargeResults results = new RecordingChargeResults();
    private final SendChargeToGatewayService service = new SendChargeToGatewayService(intents, gateway, results,
            failures);

    @Test
    void sendsTheSingleCalculatedAmountWithTheIdempotencyKeyOfTheAttempt() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation);
        intents.save(intent);
        gateway.withSendResult(new ChargeGatewayResult("approved", "987654", "accredited"));

        service.send(new ChargeCommand(intent.id(), PAYER_EMAIL));

        assertThat(gateway.requests()).hasSize(1);
        assertThat(gateway.lastRequest().transactionAmount()).isEqualTo(ChargeFixtures.TOTAL);
        assertThat(gateway.lastRequest().operationKey()).isEqualTo(intent.idempotencyKey().value());
        assertThat(gateway.lastRequest().token()).isEqualTo("token-123");
        assertThat(gateway.lastRequest().payerEmail()).isEqualTo(PAYER_EMAIL);
        assertThat(gateway.lastRequest().installments()).isEqualTo(1);
        assertThat(gateway.lastRequest().externalReference()).isEqualTo(reservation.reservationId().value().toString());
    }

    @Test
    void storesTheExternalReferenceAndRegistersTheResult() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation);
        intents.save(intent);
        gateway.withSendResult(new ChargeGatewayResult("approved", "987654", "accredited"));

        service.send(new ChargeCommand(intent.id(), PAYER_EMAIL));

        ChargeIntent stored = intents.findById(intent.id()).orElseThrow();
        assertThat(stored.externalReference()).isEqualTo("987654");
        assertThat(results.registered()).hasSize(1);
        assertThat(results.registered().get(0).status()).isEqualTo("approved");
    }

    @Test
    void rejectsTheIntentWithoutRetryingWhenTheGatewayRejectsTheToken() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation);
        intents.save(intent);
        gateway.withSendFailure(new InvalidPaymentTokenException("The payment gateway rejected the payment token"));

        service.send(new ChargeCommand(intent.id(), PAYER_EMAIL));

        ChargeIntent stored = intents.findById(intent.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ChargeIntentStatus.REJECTED);
        assertThat(stored.statusDetail()).contains("rejected the payment token");
        assertThat(results.registered()).isEmpty();
    }

    @Test
    void marksTheIntentAsCommunicationErrorAndRethrowsForTheQueueRetry() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation);
        intents.save(intent);
        gateway.withSendFailure(new GatewayCommunicationException("The payment gateway did not answer"));

        assertThatExceptionOfType(GatewayCommunicationException.class)
                .isThrownBy(() -> service.send(new ChargeCommand(intent.id(), PAYER_EMAIL)));

        ChargeIntent stored = intents.findById(intent.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ChargeIntentStatus.COMMUNICATION_ERROR);
    }

    @Test
    void doesNotSendACaptureWithoutThePayerEmail() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntent(reservation);
        intents.save(intent);

        service.send(new ChargeCommand(intent.id(), null));

        assertThat(gateway.requests()).isEmpty();
        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.REJECTED);
    }

    @Test
    void doesNotSendAChargeOfATerminalIntent() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.capturedIntent(reservation, "987654");
        intents.save(intent);

        service.send(new ChargeCommand(intent.id(), PAYER_EMAIL));

        assertThat(gateway.requests()).isEmpty();
    }

    @Test
    void recordsTheFailureWhenTheIntentDoesNotExist() {
        service.send(new ChargeCommand(UUID.randomUUID(), PAYER_EMAIL));

        assertThat(gateway.requests()).isEmpty();
        assertThat(failures.failures()).hasSize(1);
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
