package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.application.dto.ChargeCommand;
import com.seashare.seasharem3.application.port.in.SendChargeToGatewayUseCase;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.exception.InvalidPaymentTokenException;
import com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto.ChargeCommandMessageMapper;
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

class ChargeCommandListenerTest {

    private static final String CHARGE_INTENT_ID = "6f4c1f0e-0f1a-4d5b-9c7a-2b8e3f1d5a90";

    private final RecordingSender sender = new RecordingSender();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();
    private ChargeCommandListener listener;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        listener = new ChargeCommandListener(sender, new ChargeCommandMessageMapper(),
                new AmqpListenerFailurePolicy(failures), jsonMapper);
    }

    private static Message message(String body, String contentType) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(contentType)
                .setMessageId(UUID.randomUUID().toString())
                .build();
    }

    private static String validBody() {
        return "{\"charge_intent_id\":\"" + CHARGE_INTENT_ID + "\","
                + "\"payer_email\":\"arrendatario@example.com\"}";
    }

    @Test
    void delegatesTheCommandToTheWorker() {
        listener.onMessage(message(validBody(), MessageProperties.CONTENT_TYPE_JSON));

        assertThat(sender.commands).hasSize(1);
        assertThat(sender.commands.get(0).chargeIntentId()).isEqualTo(UUID.fromString(CHARGE_INTENT_ID));
        assertThat(sender.commands.get(0).payerEmail()).isEqualTo("arrendatario@example.com");
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void acknowledgesAndRecordsAMalformedBody() {
        listener.onMessage(message("not a json payload", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(sender.commands).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsABodyWithoutChargeIntentId() {
        listener.onMessage(message("{\"payer_email\":\"a@b.com\"}", MessageProperties.CONTENT_TYPE_JSON));

        assertThat(sender.commands).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsAMessageWithoutJsonContentType() {
        listener.onMessage(message(validBody(), "text/plain"));

        assertThat(sender.commands).isEmpty();
        assertThat(failures.failures()).hasSize(1);
    }

    @Test
    void acknowledgesAndRecordsAPermanentFailureOfTheCharge() {
        sender.failure = new InvalidPaymentTokenException("The payment gateway rejected the payment token");

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

    /** Doble del puerto de entrada del worker. */
    private static final class RecordingSender implements SendChargeToGatewayUseCase {

        private final List<ChargeCommand> commands = new ArrayList<>();

        private RuntimeException failure;

        @Override
        public void send(ChargeCommand command) {
            if (failure != null) {
                throw failure;
            }
            commands.add(command);
        }
    }
}
