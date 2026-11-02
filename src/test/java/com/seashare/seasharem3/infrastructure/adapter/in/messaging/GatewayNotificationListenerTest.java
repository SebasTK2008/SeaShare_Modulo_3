package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.port.in.GatewayWebhookHandler;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.GatewayNotificationMessageMapper;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpListenerFailurePolicy;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Pruebas del worker de notificaciones del webhook (UC05 T020): despacho por {@code type}, ack con
 * registro del fallo logico y propagacion del fallo transitorio para que la cola reintente.
 */
class GatewayNotificationListenerTest {

    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private final RecordingHandler handler = new RecordingHandler();
    private GatewayNotificationListener listener;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        listener = new GatewayNotificationListener(List.of(handler), new GatewayNotificationMessageMapper(),
                new AmqpListenerFailurePolicy(failures), jsonMapper);
    }

    private static Message message(String body, String contentType) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(contentType)
                .setMessageId(UUID.randomUUID().toString())
                .build();
    }

    private static String validBody() {
        return "{\"type\":\"payment\",\"action\":\"payment.updated\",\"data_id\":\"123456\"}";
    }

    @Test
    void dispatchesTheNotificationToTheHandlerOfItsType() {
        listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON));

        assertThat(handler.notifications).hasSize(1);
        assertThat(handler.notifications.get(0).type()).isEqualTo("payment");
        assertThat(handler.notifications.get(0).dataId()).isEqualTo("123456");
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void acknowledgesAndRecordsATypeWithoutHandler() {
        listener.onMessage(message("{\"type\":\"unknown\"}", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(handler.notifications).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsAMalformedBody() {
        listener.onMessage(message("not a json payload", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(handler.notifications).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsABodyWithoutType() {
        listener.onMessage(message("{\"action\":\"payment.updated\"}", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(handler.notifications).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsAMessageWithoutJsonContentType() {
        listener.onMessage(message(validBody(), "text/plain"));

        assertThat(handler.notifications).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsAMessageWithoutMessageId() {
        Message withoutId = MessageBuilder
                .withBody(validBody().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();

        listener.onMessage(withoutId);

        assertThat(handler.notifications).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsADomainFailure() {
        handler.failure = new DomainException("The payment intent no longer accepts results");

        listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON));

        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void propagatesATransientFailureSoTheQueueRetriesIt() {
        handler.failure = new GatewayCommunicationException("The payment gateway did not answer");

        assertThatExceptionOfType(GatewayCommunicationException.class)
                .isThrownBy(() -> listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON)));

        assertThat(failures.failures()).isEmpty();
    }

    /** Doble del manejador registrado por UC05 para el tipo {@code payment}. */
    private static final class RecordingHandler implements GatewayWebhookHandler {

        private final List<GatewayWebhookNotification> notifications = new ArrayList<>();

        private RuntimeException failure;

        @Override
        public String supportedType() {
            return "payment";
        }

        @Override
        public void handle(GatewayWebhookNotification notification) {
            if (failure != null) {
                throw failure;
            }
            notifications.add(notification);
        }
    }
}
