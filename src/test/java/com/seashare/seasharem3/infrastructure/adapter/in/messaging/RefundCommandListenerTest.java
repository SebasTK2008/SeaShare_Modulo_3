package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.port.in.SendRefundToGatewayUseCase;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
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
 * Pruebas del worker de reembolsos (UC09): cuerpo del contrato, ack con registro del fallo lógico y
 * propagación del fallo transitorio para que la cola reintente.
 */
class RefundCommandListenerTest {

    private static final String REFUND_INTENT_ID = "6f4c1f0e-0f1a-4d5b-9c7a-2b8e3f1d5a90";

    private final RecordingSender sender = new RecordingSender();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private RefundCommandListener listener;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        listener = new RefundCommandListener(sender, new AmqpListenerFailurePolicy(failures), jsonMapper);
    }

    private static Message message(String body, String contentType) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(contentType)
                .setMessageId(UUID.randomUUID().toString())
                .build();
    }

    private static String validBody() {
        return "{\"refund_intent_id\":\"" + REFUND_INTENT_ID + "\"}";
    }

    @Test
    void delegatesTheCommandToTheWorker() {
        listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON));

        assertThat(sender.ids).hasSize(1);
        assertThat(sender.ids.get(0)).isEqualTo(UUID.fromString(REFUND_INTENT_ID));
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void acknowledgesAndRecordsAMalformedBody() {
        listener.onMessage(message("not a json payload", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(sender.ids).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsABodyWithoutRefundIntentId() {
        listener.onMessage(message("{}", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(sender.ids).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsAMessageWithoutJsonContentType() {
        listener.onMessage(message(validBody(), "text/plain"));

        assertThat(sender.ids).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsADomainFailure() {
        sender.failure = new DomainException("The refund intent is not refundable");

        listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON));

        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void propagatesATransientFailureSoTheQueueRetriesIt() {
        sender.failure = new GatewayCommunicationException("The payment gateway did not answer");

        assertThatExceptionOfType(GatewayCommunicationException.class)
                .isThrownBy(() -> listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON)));

        assertThat(failures.failures()).isEmpty();
    }

    /** Doble del puerto de entrada del worker de reembolsos. */
    private static final class RecordingSender implements SendRefundToGatewayUseCase {

        private final List<UUID> ids = new ArrayList<>();

        private RuntimeException failure;

        @Override
        public void send(UUID refundIntentId) {
            if (failure != null) {
                throw failure;
            }
            ids.add(refundIntentId);
        }
    }
}
