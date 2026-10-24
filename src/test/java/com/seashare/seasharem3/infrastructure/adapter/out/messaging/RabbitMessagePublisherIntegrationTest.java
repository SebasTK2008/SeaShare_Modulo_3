package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.port.out.MessageBrokerPort;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Integración de la publicación con acuse contra RabbitMQ real: entrega con confirmación en la cola
 * del comando y fallo explícito cuando el mensaje no es enrutable (mandatory + returns).
 * Fuente: general-plan §3.5/§7.2. Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class RabbitMessagePublisherIntegrationTest {

    @Autowired
    private MessageBrokerPort messageBrokerPort;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesWithConfirmationInTheChargeCommandQueue() {
        String payload = "{\"reservation_id\":\"" + UUID.randomUUID() + "\"}";

        messageBrokerPort.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_CHARGE_COMMAND, payload);

        Message received = receiveWithPayload(AmqpNames.QUEUE_CHARGE_COMMAND, payload);
        assertThat(received.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(received.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void unroutableMessageFailsExplicitlyInsteadOfBeingSilentlyDropped() {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThatThrownBy(() -> messageBrokerPort.publish(AmqpNames.EXCHANGE_COMMANDS,
                        "routing.key.without.binding", "{\"probe\":true}"))
                        .isInstanceOf(AmqpException.class)
                        .hasMessageContaining("not routable"));
    }

    private Message receiveWithPayload(String queue, String payload) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Message message = rabbitTemplate.receive(queue, 500);
            if (message != null && payload.equals(new String(message.getBody(), StandardCharsets.UTF_8))) {
                return message;
            }
        }
        throw new AssertionError("No message with the expected payload arrived to queue " + queue);
    }
}
