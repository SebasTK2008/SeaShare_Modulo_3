package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.application.port.in.HandleReservationStatusUseCase;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integración del listener de UC07 contra RabbitMQ y PostgreSQL reales: un mensaje válido se delega
 * y se confirma, un mensaje inválido se registra y se confirma, y una falla transitoria termina en la
 * DLQ. Fuente: UC07 plan T009 y §Reglas 10.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, ReservationStatusListenerTest.StubUseCaseConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationStatusListenerTest {

    private static final String DEAD_LETTER_QUEUE = AmqpNames.deadLetterQueue(AmqpNames.QUEUE_RESERVATION_STATUS);
    private static final UUID TRANSIENT_FAILURE_RESERVATION =
            UUID.fromString("00000000-0000-0000-0000-00000000dead");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubHandleReservationStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase.reset();
        await().atMost(Duration.ofSeconds(10)).until(() -> amqpAdmin
                .getQueueProperties(AmqpNames.QUEUE_RESERVATION_STATUS) != null
                && amqpAdmin.getQueueProperties(DEAD_LETTER_QUEUE) != null);
        amqpAdmin.purgeQueue(AmqpNames.QUEUE_RESERVATION_STATUS, true);
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, true);
    }

    @Test
    void validStatusIsDelegatedAndAcknowledged() {
        UUID reservationId = UUID.randomUUID();
        String messageId = "uc07-valid-" + UUID.randomUUID();

        publish("""
                {"reservation_id":"%s","status":"COMPLETED","status_changed_at":"2026-12-05T14:30:00Z"}
                """.formatted(reservationId), messageId);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(useCase.commands()).hasSize(1));
        assertThat(useCase.commands()).singleElement().satisfies(command -> {
            assertThat(command.reservationId()).isEqualTo(reservationId);
            assertThat(command.status().name()).isEqualTo("COMPLETED");
        });
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_RESERVATION_STATUS, 500)).isNull();
        assertThat(failuresFor(messageId)).isZero();
    }

    @Test
    void unknownStatusIsRecordedAndAcknowledged() {
        UUID reservationId = UUID.randomUUID();
        String messageId = "uc07-unknown-" + UUID.randomUUID();

        publish("""
                {"reservation_id":"%s","status":"CANCELLED_MAYBE","status_changed_at":"2026-12-05T14:30:00Z"}
                """.formatted(reservationId), messageId);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(failuresFor(messageId)).isEqualTo(1));
        assertThat(useCase.commands()).isEmpty();
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_RESERVATION_STATUS, 500)).isNull();
        assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 500)).isNull();
    }

    @Test
    void pendingWithoutPaymentTokenIsRecordedAndAcknowledged() {
        UUID reservationId = UUID.randomUUID();
        String messageId = "uc07-no-token-" + UUID.randomUUID();

        publish("""
                {"reservation_id":"%s","status":"PENDING","status_changed_at":"2026-12-05T14:30:00Z"}
                """.formatted(reservationId), messageId);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(failuresFor(messageId)).isEqualTo(1));
        assertThat(useCase.commands()).isEmpty();
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_RESERVATION_STATUS, 500)).isNull();
    }

    @Test
    void transientFailureIsRetriedAndEndsInTheDeadLetterQueue() {
        String messageId = "uc07-transient-" + UUID.randomUUID();

        publish("""
                {"reservation_id":"%s","status":"COMPLETED","status_changed_at":"2026-12-05T14:30:00Z"}
                """.formatted(TRANSIENT_FAILURE_RESERVATION), messageId);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(useCase.attempts()).isEqualTo(5));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(receiveDeadLetter()).isNotNull());
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_RESERVATION_STATUS, 500)).isNull();
    }

    private Message receiveDeadLetter() {
        return rabbitTemplate.receive(DEAD_LETTER_QUEUE, 1000);
    }

    private int failuresFor(String messageId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from operational_failure where use_case = 'UC07' and payload_ref like ?",
                Integer.class, "%" + messageId + "%");
        return count == null ? 0 : count;
    }

    private void publish(String body, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(messageId);
        properties.setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
        rabbitTemplate.send(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_STATUS_CHANGED,
                new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubUseCaseConfiguration {

        @Bean
        @Primary
        StubHandleReservationStatusUseCase stubHandleReservationStatusUseCase() {
            return new StubHandleReservationStatusUseCase();
        }
    }

    /** Sustituye al servicio real: registra lo recibido y simula una falla transitoria. */
    static class StubHandleReservationStatusUseCase implements HandleReservationStatusUseCase {

        private final List<ReservationStatusCommand> commands = new CopyOnWriteArrayList<>();
        private final List<ReservationStatusCommand> attempts = new CopyOnWriteArrayList<>();

        @Override
        public void handle(ReservationStatusCommand command) {
            attempts.add(command);
            if (TRANSIENT_FAILURE_RESERVATION.equals(command.reservationId())) {
                throw new IllegalStateException("simulated database outage");
            }
            commands.add(command);
        }

        List<ReservationStatusCommand> commands() {
            return List.copyOf(commands);
        }

        int attempts() {
            return attempts.size();
        }

        void reset() {
            commands.clear();
            attempts.clear();
        }
    }
}
