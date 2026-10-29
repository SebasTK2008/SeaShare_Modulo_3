package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.ChargeGatewayResult;
import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryChargeIntentRepository;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.StubChargeGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChargePaymentWebhookHandlerTest {

    private static final String EXTERNAL_REFERENCE = "987654";

    private final InMemoryChargeIntentRepository intents = new InMemoryChargeIntentRepository();
    private final StubChargeGateway gateway = new StubChargeGateway();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RecordingChargeResults results = new RecordingChargeResults();
    private final ChargePaymentWebhookHandler handler = new ChargePaymentWebhookHandler(intents, gateway, results,
            failures);

    @Test
    void handlesThePaymentType() {
        assertThat(handler.supportedType()).isEqualTo("payment");
    }

    @Test
    void associatesTheNotificationByExternalReferenceAndRegistersTheResult() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntentWithReference(reservation, EXTERNAL_REFERENCE);
        intents.save(intent);
        gateway.withFetchResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        handler.handle(new GatewayWebhookNotification("payment", "payment.updated", EXTERNAL_REFERENCE));

        assertThat(results.registered()).hasSize(1);
        assertThat(results.registered().get(0).status()).isEqualTo("approved");
    }

    @Test
    void recordsTheEventWhenThereIsNoAssociatedIntent() {
        gateway.withFetchResult(new ChargeGatewayResult("approved", EXTERNAL_REFERENCE, "accredited"));

        handler.handle(new GatewayWebhookNotification("payment", "payment.updated", EXTERNAL_REFERENCE));

        assertThat(results.registered()).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void doesNotCloseTheIntentWhenTheGatewayDoesNotKnowThePayment() {
        ReservationInformation reservation = ChargeFixtures.calculatedReservation();
        ChargeIntent intent = ChargeFixtures.pendingIntentWithReference(reservation, EXTERNAL_REFERENCE);
        intents.save(intent);

        handler.handle(new GatewayWebhookNotification("payment", "payment.updated", EXTERNAL_REFERENCE));

        assertThat(intents.findById(intent.id()).orElseThrow().status())
                .isEqualTo(ChargeIntentStatus.PENDING_SEND);
        assertThat(results.registered()).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    /** Doble del puerto de registro: los servicios no se invocan entre si, solo por su puerto. */
    private static final class RecordingChargeResults
            implements com.seashare.seasharem3.application.port.in.RegisterChargeResultUseCase {

        private final java.util.List<ChargeGatewayResult> registered = new java.util.ArrayList<>();

        @Override
        public void register(ChargeGatewayResult result) {
            registered.add(result);
        }

        java.util.List<ChargeGatewayResult> registered() {
            return java.util.List.copyOf(registered);
        }
    }
}
