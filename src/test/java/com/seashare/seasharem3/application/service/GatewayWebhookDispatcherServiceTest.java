package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayWebhookDispatcherServiceTest {

    private final RecordingPublisher publisher = new RecordingPublisher();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RecordingHandler handler = new RecordingHandler("payment");
    private final GatewayWebhookDispatcherService dispatcher = new GatewayWebhookDispatcherService(
            List.of(handler), publisher, failures);

    @Test
    void publishesTheNotificationForATypeWithHandler() {
        GatewayWebhookNotification notification = new GatewayWebhookNotification("payment", "payment.updated", "987654");

        dispatcher.dispatch(notification);

        assertThat(publisher.notifications).containsExactly(notification);
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void recordsTheEventWhenThereIsNoHandlerForTheType() {
        GatewayWebhookNotification notification = new GatewayWebhookNotification("refund", "refund.updated", "987654");

        dispatcher.dispatch(notification);

        assertThat(publisher.notifications).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    private static final class RecordingPublisher
            implements com.seashare.seasharem3.application.port.out.GatewayNotificationPublisher {

        private final List<GatewayWebhookNotification> notifications = new ArrayList<>();

        @Override
        public void publish(GatewayWebhookNotification notification) {
            notifications.add(notification);
        }
    }

    private static final class RecordingHandler implements GatewayWebhookHandler {

        private final String type;

        private RecordingHandler(String type) {
            this.type = type;
        }

        @Override
        public String supportedType() {
            return type;
        }

        @Override
        public void handle(GatewayWebhookNotification notification) {
        }
    }
}
