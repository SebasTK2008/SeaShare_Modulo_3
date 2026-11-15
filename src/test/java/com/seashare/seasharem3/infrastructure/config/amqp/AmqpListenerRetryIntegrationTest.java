package com.seashare.seasharem3.infrastructure.config.amqp;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integración de la política de fallos de los listeners contra RabbitMQ y PostgreSQL reales: una falla
 * transitoria se reintenta hasta agotar la política, se registra en {@code operational_failure} con la
 * transacción independiente y el mensaje termina en la DLQ (contracts/README §4.4).
 * Requiere Docker; en local se omiten.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class AmqpListenerRetryIntegrationTest {

    /** Intentos del contrato: 1 inicial + max-retries=4 de application.properties. */
    private static final int CONTRACT_ATTEMPTS = 5;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AtomicInteger retryProbeAttempts;

    @Test
    void retriesTheContractAttemptsThenRecordsTheFailureAndRejectsToTheDlq() {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> amqpAdmin.getQueueProperties(RetryProbeConfiguration.DLQ) != null);

        UUID reservationId = UUID.randomUUID();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("reservation_id", reservationId.toString());
        Message message = new Message(("{\"reservation_id\":\"" + reservationId + "\"}").getBytes(StandardCharsets.UTF_8),
                properties);

        rabbitTemplate.send("", RetryProbeConfiguration.QUEUE, message);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(retryProbeAttempts.get()).isEqualTo(CONTRACT_ATTEMPTS));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(countFailures(reservationId)).isEqualTo(1L));

        Map<String, Object> failure = jdbcTemplate.queryForMap(
                "SELECT use_case, reason FROM operational_failure WHERE reservation_id = ?", reservationId);
        assertThat(failure)
                .containsEntry("use_case", "AMQP")
                .containsEntry("reason", "transient probe failure");
        await().atMost(Duration.ofSeconds(30))
                .until(() -> rabbitTemplate.receive(RetryProbeConfiguration.DLQ, 2000) != null);
    }

    private long countFailures(UUID reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operational_failure WHERE reservation_id = ?", Long.class, reservationId);
        return count != null ? count : 0L;
    }
}
