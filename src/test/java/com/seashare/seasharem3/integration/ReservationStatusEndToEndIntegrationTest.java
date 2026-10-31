package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Extremo a extremo de T025: el mensaje {@code COMPLETED} entra por la cola de UC07 y recorre el
 * servicio real de UC07, el log, UC10 ({@code RequestSettlementService}) y UC08
 * ({@code OpenDepositTrackingService}) con sus adaptadores reales.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class ReservationStatusEndToEndIntegrationTest {

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
    private ChargeIntentRepository chargeIntentRepository;

    @BeforeEach
    void setUp() {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> amqpAdmin.getQueueProperties(AmqpNames.QUEUE_RESERVATION_STATUS) != null);
        amqpAdmin.purgeQueue(AmqpNames.QUEUE_RESERVATION_STATUS, true);
    }

    @Test
    void completedStatusSettlesTheRentalAndHoldsTheDepositEndToEnd() {
        ReservationInformation reservation = createCapturedReservation();

        publishCompleted(reservation.reservationId());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(settlements(reservation.reservationId()))
                .isEqualTo(1));
        assertThat(depositDisposition(reservation.reservationId())).isEqualTo("HELD");
        assertThat(processedLogRows(reservation.reservationId())).isEqualTo(1);
        assertThat(failuresOf(reservation.reservationId())).isZero();
    }

    private ReservationInformation createCapturedReservation() {
        long version = parametersAdapter.findCurrent().map(parameters -> parameters.versionNumber() + 1).orElse(1L);
        FinancialParameters parameters = parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(), version, new Percentage(new BigDecimal("20")), Money.ZERO, Percentage.ZERO,
                Percentage.ZERO, "e2e-uc07", ChargeFixtures.CREATED_AT));

        ReservationInformation template = ChargeFixtures.calculatedReservation();
        ReservationInformation created = reservationAdapter.save(ReservationInformation.create(
                template.reservationId(), template.boatId(), template.baseRate(), parameters.id(),
                template.startDate(), template.endDate(), template.passengers(), template.ownerId(),
                template.maxCapacity()));
        ReservationInformation calculated = created.withCalculated(new ReservationValue(template.rentalAmount(),
                template.insuranceAmount(), template.depositAmount(), template.totalAmount()), parameters.id(),
                ChargeFixtures.CREATED_AT);
        ReservationInformation stored = reservationAdapter.saveCalculatedAmounts(calculated);
        chargeIntentRepository.save(ChargeFixtures.capturedIntent(stored, "ext-e2e-" + UUID.randomUUID()));
        return stored;
    }

    private void publishCompleted(ReservationId reservationId) {
        String body = """
                {"reservation_id":"%s","status":"COMPLETED","status_changed_at":"2026-12-05T14:30:00Z"}
                """.formatted(reservationId.value());
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId("uc07-e2e-" + UUID.randomUUID());
        rabbitTemplate.send(AmqpNames.EXCHANGE_EVENTS, AmqpNames.ROUTING_STATUS_CHANGED,
                new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private int settlements(ReservationId reservationId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from settlement_intent where reservation_id = ?",
                Integer.class, reservationId.value());
        return count == null ? 0 : count;
    }

    private String depositDisposition(ReservationId reservationId) {
        return jdbcTemplate.queryForObject("select disposition from deposit_disposition where reservation_id = ?",
                String.class, reservationId.value());
    }

    private int processedLogRows(ReservationId reservationId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from reservation_status_log where reservation_id = ? and processed_at is not null",
                Integer.class, reservationId.value());
        return count == null ? 0 : count;
    }

    private int failuresOf(ReservationId reservationId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from operational_failure where reservation_id = ?", Integer.class,
                reservationId.value());
        return count == null ? 0 : count;
    }
}
