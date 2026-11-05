package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.RefundRequestResult;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-37: UC07 y UC08 reclaman la misma operación financiera con identidades de negocio distintas
 * ({@code reservationId:status} frente al {@code event_key} de la disputa). La constraint
 * {@code UNIQUE(reservation_id, operation_key)} no las distingue; lo que impide el doble reembolso o la
 * doble liquidación es que solo la solicitud que aplica la transición del depósito crea la intención.
 * Se ejercitan los servicios reales de UC09/UC10, el seguimiento del depósito (UC08) y PostgreSQL.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class DepositOutcomeSingleIntentIntegrationTest {

    @Autowired
    private RequestRefundUseCase requestRefund;

    @Autowired
    private RequestSettlementUseCase requestSettlement;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Autowired
    private ChargeIntentRepository chargeIntentRepository;

    @Test
    void theSameOperationRepeatedCreatesASingleRefundIntent() {
        ReservationInformation reservation = capturedReservation();
        RefundRequestCommand command = new RefundRequestCommand(reservation.reservationId().value(),
                RefundTrigger.CANCELLED_STRICT, null, reservation.reservationId().value() + ":CANCELLED_STRICT");

        RefundRequestResult first = requestRefund.request(command);
        RefundRequestResult second = requestRefund.request(command);

        assertThat(first.status()).isEqualTo(RefundIntentStatus.PENDING_SEND);
        assertThat(second.refundIntentId()).isEqualTo(first.refundIntentId());
        assertThat(count("refund_intent", reservation)).isEqualTo(1);
    }

    @Test
    void aCancellationAndADisputeRejectionOverTheSameDepositCreateASingleRefundIntent() {
        ReservationInformation reservation = capturedReservation();
        UUID reservationId = reservation.reservationId().value();

        RefundRequestResult byDispute = requestRefund.request(new RefundRequestCommand(reservationId,
                RefundTrigger.DISPUTE_REJECTED, UUID.randomUUID(), "dispute-event-1"));
        RefundRequestResult byCancellation = requestRefund.request(new RefundRequestCommand(reservationId,
                RefundTrigger.CANCELLED_STRICT, null, reservationId + ":CANCELLED_STRICT"));

        assertThat(byDispute.status()).isEqualTo(RefundIntentStatus.PENDING_SEND);
        assertThat(byCancellation.refundIntentId()).isEqualTo(byDispute.refundIntentId());
        assertThat(byCancellation.status()).isNotEqualTo(RefundIntentStatus.FAILED);
        assertThat(count("refund_intent", reservation)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from operational_failure where reservation_id = ? and payload_ref like ?",
                Integer.class, reservationId, "%duplicate_deposit_outcome=REFUND_REQUESTED%")).isEqualTo(1);
    }

    @Test
    void twoDifferentDisputeEventsOverTheSameDepositCreateASingleSettlementIntent() {
        ReservationInformation reservation = capturedReservation();

        SettlementRequestResult first = requestSettlement.request(new SettlementCommand(reservation.reservationId(),
                SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, UUID.randomUUID(), "dispute-event-1"));
        SettlementRequestResult second = requestSettlement.request(new SettlementCommand(
                reservation.reservationId(), SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT,
                UUID.randomUUID(), "dispute-event-2"));

        assertThat(first.status()).isEqualTo(SettlementStatus.PENDING_SEND);
        assertThat(second.settlementIntentId()).isEqualTo(first.settlementIntentId());
        assertThat(second.status()).isNotEqualTo(SettlementStatus.FAILED);
        assertThat(count("settlement_intent", reservation)).isEqualTo(1);
    }

    @Test
    void aSettledDepositIsNeverRefundedAfterwards() {
        ReservationInformation reservation = capturedReservation();
        UUID reservationId = reservation.reservationId().value();
        SettlementRequestResult settlement = requestSettlement.request(new SettlementCommand(
                reservation.reservationId(), SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT,
                UUID.randomUUID(), "dispute-event-1"));

        RefundRequestResult refund = requestRefund.request(new RefundRequestCommand(reservationId,
                RefundTrigger.CANCELLED_STRICT, null, reservationId + ":CANCELLED_STRICT"));

        assertThat(settlement.status()).isEqualTo(SettlementStatus.PENDING_SEND);
        assertThat(refund.status()).isEqualTo(RefundIntentStatus.FAILED);
        assertThat(count("refund_intent", reservation)).isZero();
    }

    private int count(String table, ReservationInformation reservation) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table + " where reservation_id = ?",
                Integer.class, reservation.reservationId().value());
        return count == null ? 0 : count;
    }

    private ReservationInformation capturedReservation() {
        long version = parametersAdapter.findCurrent().map(parameters -> parameters.versionNumber() + 1).orElse(1L);
        FinancialParameters parameters = parametersAdapter.saveNewVersion(FinancialParameters.create(
                UUID.randomUUID(), version, new Percentage(new BigDecimal("20")), Money.ZERO, Percentage.ZERO,
                Percentage.ZERO, "e2e-deposit", ChargeFixtures.CREATED_AT));
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
}
