package com.seashare.seasharem3.infrastructure.adapter.in.messaging;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import com.seashare.seasharem3.support.ChargeFixtures;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integracion del listener de UC08 contra RabbitMQ y PostgreSQL reales: un PENDING se persiste y se
 * confirma, un REJECTED delega una sola vez aunque el evento se reentregue, y un estado desconocido
 * se registra y se confirma sin reintentos.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class,
        DisputeNotificationListenerIntegrationTest.StubFinancialConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class DisputeNotificationListenerIntegrationTest {

    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-23T10:00:00Z");
    private static final UUID DISPUTE_UUID = UUID.fromString("d8e1f2a3-b4c5-6d7e-8f9a-0b1c2d3e4f5a");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Autowired
    private StubRequestRefundUseCase refund;

    @Autowired
    private StubRequestSettlementUseCase settlement;

    @BeforeEach
    void setUp() {
        refund.reset();
        settlement.reset();
        await().atMost(Duration.ofSeconds(10)).until(() -> amqpAdmin
                .getQueueProperties(AmqpNames.QUEUE_GUARANTEE_DISPUTE) != null
                && amqpAdmin.getQueueProperties(
                        AmqpNames.deadLetterQueue(AmqpNames.QUEUE_GUARANTEE_DISPUTE)) != null);
        amqpAdmin.purgeQueue(AmqpNames.QUEUE_GUARANTEE_DISPUTE, true);
        amqpAdmin.purgeQueue(AmqpNames.deadLetterQueue(AmqpNames.QUEUE_GUARANTEE_DISPUTE), true);
    }

    @Test
    void pendingEventIsPersistedAndAcknowledgedWithoutMovingMoney() {
        ReservationId reservationId = createReservation();

        publish(message(reservationId, "PENDING", "evt-pending"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(eventCount(reservationId)).isEqualTo(1));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(processedCount(reservationId)).isEqualTo(1));
        assertThat(refund.invocations()).isZero();
        assertThat(settlement.invocations()).isZero();
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_GUARANTEE_DISPUTE, 500)).isNull();
    }

    @Test
    void rejectedEventIsDelegatedOnceEvenIfItIsRedelivered() {
        ReservationId reservationId = createReservation();

        publish(message(reservationId, "REJECTED", "evt-rejected"));
        publish(message(reservationId, "REJECTED", "evt-rejected"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(refund.invocations()).isEqualTo(1));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(eventCount(reservationId)).isEqualTo(1));
        assertThat(refund.commands()).singleElement().satisfies(command -> {
            assertThat(command.reservationId()).isEqualTo(reservationId.value());
            assertThat(command.trigger()).isEqualTo(RefundTrigger.DISPUTE_REJECTED);
            assertThat(command.operationKey()).isEqualTo("evt-rejected");
        });
        assertThat(settlement.invocations()).isZero();
    }

    @Test
    void unknownStatusIsRecordedAndAcknowledgedWithoutRetries() {
        ReservationId reservationId = createReservation();

        publish(message(reservationId, "PARTIALLY_RELEASED", "evt-invalid"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(failureCount(reservationId)).isEqualTo(1));
        assertThat(eventCount(reservationId)).isZero();
        assertThat(refund.invocations()).isZero();
        assertThat(settlement.invocations()).isZero();
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_GUARANTEE_DISPUTE, 500)).isNull();
    }

    @Test
    void disputeForAReservationNotRegisteredByUc03IsAcknowledgedAndRecorded() {
        ReservationId unknownReservation = new ReservationId(UUID.randomUUID());

        publish(message(unknownReservation, "REJECTED", "evt-unknown-reservation"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(failureCount(unknownReservation)).isEqualTo(1));
        assertThat(eventCount(unknownReservation)).isZero();
        assertThat(refund.invocations()).isZero();
        assertThat(settlement.invocations()).isZero();
        assertThat(rabbitTemplate.receive(AmqpNames.QUEUE_GUARANTEE_DISPUTE, 500)).isNull();
    }

    @Test
    void transientFailureRollsBackTheEventAndEndsInDlq() {
        ReservationId reservationId = createReservation();
        refund.failTransiently();

        publish(message(reservationId, "REJECTED", "evt-transient"));

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(refund.invocations()).isEqualTo(5));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(failureCount(reservationId)).isEqualTo(1));
        assertThat(eventCount(reservationId)).isZero();
        await().atMost(Duration.ofSeconds(30)).until(() -> rabbitTemplate
                .receive(AmqpNames.deadLetterQueue(AmqpNames.QUEUE_GUARANTEE_DISPUTE), 2000) != null);
    }

    private ReservationId createReservation() {
        ReservationInformation info = ChargeFixtures.calculatedReservation();
        long version = parametersAdapter.findCurrent().map(parameters -> parameters.versionNumber() + 1).orElse(1L);
        FinancialParameters parameters = parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(), version, new Percentage(new BigDecimal("20")), Money.ZERO, Percentage.ZERO,
                Percentage.ZERO, "test", ChargeFixtures.CREATED_AT));
        ReservationInformation saved = reservationAdapter.save(ReservationInformation.create(info.reservationId(),
                info.boatId(), info.baseRate(), parameters.id(), info.startDate(), info.endDate(),
                info.passengers(), info.ownerId(), info.maxCapacity()));
        return saved.reservationId();
    }

    private void publish(Message message) {
        rabbitTemplate.send(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_DISPUTE_UPDATED, message);
    }

    private long eventCount(ReservationId reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dispute_event_log WHERE reservation_id = ?", Long.class,
                reservationId.value());
        return count != null ? count : 0L;
    }

    private long processedCount(ReservationId reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dispute_event_log WHERE reservation_id = ? AND processed_at IS NOT NULL",
                Long.class, reservationId.value());
        return count != null ? count : 0L;
    }

    private long failureCount(ReservationId reservationId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operational_failure WHERE reservation_id = ?", Long.class,
                reservationId.value());
        return count != null ? count : 0L;
    }

    private static Message message(ReservationId reservationId, String status, String eventKey) {
        String body = """
                {"reservation_id":"%s","dispute_id":"%s","status":"%s",\
                "status_changed_at":"%s","event_key":"%s"}""".formatted(reservationId.value(), DISPUTE_UUID,
                status, STATUS_CHANGED_AT, eventKey);
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubFinancialConfiguration {

        @Bean
        @Primary
        StubRequestRefundUseCase stubRequestRefundUseCase() {
            return new StubRequestRefundUseCase();
        }

        @Bean
        @Primary
        StubRequestSettlementUseCase stubRequestSettlementUseCase() {
            return new StubRequestSettlementUseCase();
        }
    }

    static final class StubRequestRefundUseCase implements RequestRefundUseCase {

        private final List<RefundRequestCommand> commands = new ArrayList<>();
        private final AtomicInteger invocations = new AtomicInteger();
        private volatile boolean failTransiently;

        @Override
        public void request(RefundRequestCommand command) {
            invocations.incrementAndGet();
            if (failTransiently) {
                throw new DataAccessResourceFailureException("transient refund failure");
            }
            commands.add(command);
        }

        List<RefundRequestCommand> commands() {
            return List.copyOf(commands);
        }

        int invocations() {
            return invocations.get();
        }

        void failTransiently() {
            failTransiently = true;
        }

        void reset() {
            commands.clear();
            invocations.set(0);
            failTransiently = false;
        }
    }

    static final class StubRequestSettlementUseCase implements RequestSettlementUseCase {

        private final List<SettlementCommand> commands = new ArrayList<>();

        @Override
        public SettlementRequestResult request(SettlementCommand command) {
            commands.add(command);
            return new SettlementRequestResult(UUID.randomUUID(), SettlementStatus.PENDING_SEND);
        }

        int invocations() {
            return commands.size();
        }

        void reset() {
            commands.clear();
        }
    }
}
