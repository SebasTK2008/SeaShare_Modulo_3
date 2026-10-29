package com.seashare.seasharem3.infrastructure.config.amqp;

import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El recoverer traduce la cola al caso de uso dueño, extrae {@code reservation_id} (header o cuerpo) y
 * delega en la política, que registra el fallo y rechaza el mensaje hacia la DLQ.
 * Fuente: contracts/README §4.4. [CONV] núcleo compartido.
 */
class OperationalFailureMessageRecovererTest {

    private static final UUID RESERVATION_ID = UUID.fromString("7c4a1f2e-9b3d-4a6c-8e5f-1d2c3b4a5e60");

    private final InMemoryFailureRecorder recorder = new InMemoryFailureRecorder();
    private final OperationalFailureMessageRecoverer recoverer = new OperationalFailureMessageRecoverer(
            new AmqpListenerFailurePolicy(recorder), JsonMapper.builder().build());

    @Test
    void mapsKnownQueueToItsUseCaseAndReservationIdFromHeader() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(AmqpNames.QUEUE_CHARGE_COMMAND);
        properties.setHeader("reservation_id", RESERVATION_ID.toString());
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("broker glitch")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("UC05");
            assertThat(recorded.reservationId()).isEqualTo(RESERVATION_ID);
            assertThat(recorded.reason()).isEqualTo("broker glitch");
            assertThat(recorded.payloadRef()).isEqualTo("queue=" + AmqpNames.QUEUE_CHARGE_COMMAND);
        });
    }

    @Test
    void extractsReservationIdFromJsonBodyWhenHeaderIsAbsent() {
        String json = "{\"reservation_id\":\"" + RESERVATION_ID + "\",\"status\":\"COMPLETED\"}";
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(AmqpNames.QUEUE_RESERVATION_STATUS);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("database down")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("UC07");
            assertThat(recorded.reservationId()).isEqualTo(RESERVATION_ID);
        });
    }

    @Test
    void fallsBackToBodyWhenHeaderIsNotAUuid() {
        String json = "{\"reservation_id\":\"" + RESERVATION_ID + "\"}";
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(AmqpNames.QUEUE_GUARANTEE_DISPUTE);
        properties.setHeader("reservation_id", "not-a-uuid");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("gateway timeout")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(recorder.failures()).singleElement()
                .satisfies(recorded -> assertThat(recorded.reservationId()).isEqualTo(RESERVATION_ID));
    }

    @Test
    void includesMessageIdInPayloadRef() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(AmqpNames.QUEUE_RESERVATION_INFO);
        properties.setMessageId("msg-123");
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("fleet down")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("UC03");
            assertThat(recorded.payloadRef())
                    .isEqualTo("queue=" + AmqpNames.QUEUE_RESERVATION_INFO + ";messageId=msg-123");
        });
    }

    @Test
    void mapsTheGatewayNotificationQueueToUc05() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(AmqpNames.QUEUE_GATEWAY_NOTIFICATION);
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("gateway down")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("UC05");
            assertThat(recorded.payloadRef()).isEqualTo("queue=" + AmqpNames.QUEUE_GATEWAY_NOTIFICATION);
        });
    }

    @Test
    void unknownQueueAndMalformedBodyStillRejectAndRecordWithoutReservationId() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("finance.unknown.v1");
        Message message = new Message("not-json".getBytes(StandardCharsets.UTF_8), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("boom")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(recorder.failures()).singleElement().satisfies(recorded -> {
            assertThat(recorded.useCase()).isEqualTo("AMQP");
            assertThat(recorded.reservationId()).isNull();
            assertThat(recorded.payloadRef()).isEqualTo("queue=finance.unknown.v1");
        });
    }
}
