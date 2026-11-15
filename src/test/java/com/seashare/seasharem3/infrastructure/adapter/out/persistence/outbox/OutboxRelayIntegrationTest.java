package com.seashare.seasharem3.infrastructure.adapter.out.persistence.outbox;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.port.in.RelayOutboxUseCase;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración del patrón outbox contra PostgreSQL y RabbitMQ reales: la escritura es transaccional
 * (MANDATORY), el relay publica lo pendiente con confirmación y marca {@code published_at}.
 * Fuente: general-plan D-08 y UC11 plan T046. Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class OutboxRelayIntegrationTest {

    @Autowired
    private OutboxPort outboxPort;

    @Autowired
    private RelayOutboxUseCase relayOutboxUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void publishIsTransactionalAndRelayConfirmsTheMessageInTheBroker() {
        // La cola de UC07 ya tiene consumidor: se observa una copia en una cola de sonda propia,
        // enlazada a la misma routing key del exchange de eventos.
        String probeQueue = declareProbeQueue();
        String payload = "{\"reservation_id\":\"" + UUID.randomUUID() + "\"}";
        transactionTemplate.executeWithoutResult(status -> outboxPort.publish(AmqpNames.EXCHANGE_EVENTS,
                AmqpNames.ROUTING_STATUS_CHANGED, payload));
        UUID id = jdbcTemplate.queryForObject("SELECT id FROM outbox_message WHERE payload = ?", UUID.class, payload);

        int published = relayOutboxUseCase.relay();

        assertThat(published).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox_message WHERE id = ? AND published_at IS NOT NULL", Long.class, id))
                .isEqualTo(1L);
        Message received = receiveWithPayload(probeQueue, payload);
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void publishOutsideATransactionFailsBecauseItMustJoinTheCallerTransaction() {
        assertThatThrownBy(() -> outboxPort.publish(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_STATUS_CHANGED,
                "{\"probe\":true}"))
                .isInstanceOf(IllegalTransactionStateException.class);
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

    /** Cola temporal y auto-eliminable enlazada a la routing key de estado de reserva. */
    private String declareProbeQueue() {
        Queue queue = new Queue("test.outbox.probe." + UUID.randomUUID(), false, false, true);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(AmqpNames.EXCHANGE_EVENTS, true, false))
                .with(AmqpNames.ROUTING_STATUS_CHANGED));
        return queue.getName();
    }
}
