package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.BaseRateResult;
import com.seashare.seasharem3.application.dto.ProvideBaseRateCommand;
import com.seashare.seasharem3.application.port.in.ProvideBaseRateUseCase;
import com.seashare.seasharem3.domain.exception.BaseRateException;
import com.seashare.seasharem3.domain.exception.BaseRateException.BaseRateFailureReason;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Política de acuse de UC03 contra RabbitMQ y PostgreSQL reales: un fallo transitorio se reintenta
 * cinco veces, se registra en {@code operational_failure} y termina en la DLQ; un mensaje inválido se
 * confirma y se registra sin reintentos; un mensaje válido se persiste y se confirma.
 * Fuente: contracts/README §4.4 y UC03 plan T020.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class,
        ReservationInfoListenerRetryPolicyTest.StubUc02Configuration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationInfoListenerRetryPolicyTest {

    private static final UUID BOAT_UUID = UUID.fromString("3f2c1a54-8b3e-4d7a-9c10-5a2b7e6f1d01");
    private static final UUID OWNER_UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d");
    private static final BoatId BOAT = new BoatId(BOAT_UUID);
    private static final BaseRate BASE_RATE = new BaseRate(new Money(new BigDecimal("437500.0000")));
    private static final int CONTRACT_ATTEMPTS = 5;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubProvideBaseRateUseCase uc02;

    @BeforeEach
    void setUp() {
        uc02.reset();
        await().atMost(Duration.ofSeconds(10)).until(() -> amqpAdmin
                .getQueueProperties(AmqpNames.QUEUE_RESERVATION_INFO) != null
                && amqpAdmin.getQueueProperties(
                        AmqpNames.deadLetterQueue(AmqpNames.QUEUE_RESERVATION_INFO)) != null);
        amqpAdmin.purgeQueue(AmqpNames.QUEUE_RESERVATION_INFO, true);
        amqpAdmin.purgeQueue(AmqpNames.deadLetterQueue(AmqpNames.QUEUE_RESERVATION_INFO), true);
    }

    @Test
    void transientFailureIsRetriedFiveTimesThenRecordedAndSentToDlq() {
        uc02.failTransiently();
        UUID reservationId = UUID.randomUUID();

        rabbitTemplate.send(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_INFO_PROVIDED, message(reservationId));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(uc02.invocations()).isEqualTo(CONTRACT_ATTEMPTS));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(countFailures(reservationId)).isEqualTo(1L));

        Map<String, Object> failure = jdbcTemplate.queryForMap(
                "SELECT use_case, reason FROM operational_failure WHERE reservation_id = ?", reservationId);
        assertThat(failure)
                .containsEntry("use_case", "UC03")
                .containsEntry("reason", "fleet down");
        await().atMost(Duration.ofSeconds(30)).until(() -> rabbitTemplate
                .receive(AmqpNames.deadLetterQueue(AmqpNames.QUEUE_RESERVATION_INFO), 2000) != null);
    }

    @Test
    void invalidMessageIsAcknowledgedAndRecordedWithoutRetries() {
        UUID reservationId = UUID.randomUUID();

        rabbitTemplate.send(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_INFO_PROVIDED,
                messageWithoutOwner(reservationId));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(countFailures(reservationId)).isEqualTo(1L));
        assertThat(uc02.invocations()).isZero();
        assertThat(rabbitTemplate.receive(AmqpNames.deadLetterQueue(AmqpNames.QUEUE_RESERVATION_INFO), 500))
                .isNull();
    }

    @Test
    void validMessageIsPersistedAndAcknowledged() {
        uc02.respondWith(new BaseRateResult(BOAT, BASE_RATE, null));
        UUID reservationId = UUID.randomUUID();

        rabbitTemplate.send(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_INFO_PROVIDED, message(reservationId));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(countReservations(reservationId)).isEqualTo(1L));
        assertThat(uc02.invocations()).isEqualTo(1);
        assertThat(countFailures(reservationId)).isZero();
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_RESERVATION_INFO, 500)).isNull();
    }

    private long countFailures(UUID reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operational_failure WHERE reservation_id = ?", Long.class, reservationId);
        return count != null ? count : 0L;
    }

    private long countReservations(UUID reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservation_information WHERE reservation_id = ?", Long.class, reservationId);
        return count != null ? count : 0L;
    }

    private static Message message(UUID reservationId) {
        return message(reservationId, """
                {"reservation_id":"%s","boat_id":"%s","start_date":"2026-12-20","end_date":"2026-12-22",\
                "passengers":4,"owner_id":"%s","max_capacity":6}""".formatted(reservationId, BOAT_UUID, OWNER_UUID));
    }

    private static Message messageWithoutOwner(UUID reservationId) {
        return message(reservationId, """
                {"reservation_id":"%s","boat_id":"%s","start_date":"2026-12-20","end_date":"2026-12-22",\
                "passengers":4,"max_capacity":6}""".formatted(reservationId, BOAT_UUID));
    }

    private static Message message(UUID reservationId, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubUc02Configuration {

        @Bean
        @Primary
        StubProvideBaseRateUseCase stubProvideBaseRateUseCase() {
            return new StubProvideBaseRateUseCase();
        }
    }

    static final class StubProvideBaseRateUseCase implements ProvideBaseRateUseCase {

        private final AtomicInteger invocations = new AtomicInteger();
        private volatile Supplier<List<BaseRateResult>> response = () -> List.of();

        @Override
        public List<BaseRateResult> provideBaseRates(ProvideBaseRateCommand command) {
            invocations.incrementAndGet();
            return response.get();
        }

        void respondWith(BaseRateResult result) {
            response = () -> List.of(result);
        }

        void failTransiently() {
            response = () -> {
                throw new BaseRateException(BaseRateFailureReason.FLEET_UNAVAILABLE, "fleet down");
            };
        }

        int invocations() {
            return invocations.get();
        }

        void reset() {
            invocations.set(0);
            response = () -> List.of();
        }
    }
}
