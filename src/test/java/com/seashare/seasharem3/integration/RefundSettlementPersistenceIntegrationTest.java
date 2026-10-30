package com.seashare.seasharem3.integration;

import com.seashare.seasharem3.TestcontainersConfiguration;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge.ChargeIntentPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge.ChargeRecordPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.deposit.DepositDispositionPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.parameters.FinancialParametersPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund.RefundIntentPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund.RefundRecordPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation.ReservationInformationPersistenceAdapter;
import com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement.SettlementPersistenceAdapter;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.IntegrationTestProperties;
import com.seashare.seasharem3.support.RetryProbeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracion de los adaptadores de UC09/UC10 y del CAS del deposito contra PostgreSQL real:
 * columnas nuevas, lecturas agregadas y registros inmutables.
 */
@SpringBootTest(properties = {IntegrationTestProperties.OUTBOX_RELAY_INTERVAL,
        IntegrationTestProperties.RETRY_INITIAL_INTERVAL, IntegrationTestProperties.RETRY_MULTIPLIER,
        IntegrationTestProperties.RETRY_MAX_INTERVAL})
@Import({TestcontainersConfiguration.class, RetryProbeConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class RefundSettlementPersistenceIntegrationTest {

    @Autowired
    private ReservationInformationPersistenceAdapter reservationAdapter;

    @Autowired
    private ChargeIntentPersistenceAdapter chargeIntentAdapter;

    @Autowired
    private ChargeRecordPersistenceAdapter chargeRecordAdapter;

    @Autowired
    private RefundIntentPersistenceAdapter refundIntentAdapter;

    @Autowired
    private RefundRecordPersistenceAdapter refundRecordAdapter;

    @Autowired
    private SettlementPersistenceAdapter settlementAdapter;

    @Autowired
    private DepositDispositionPersistenceAdapter depositAdapter;

    @Autowired
    private FinancialParametersPersistenceAdapter parametersAdapter;

    @Test
    void refundAndSettlementRecordsArePersistedAndReadable() {
        Fixture fixture = fixture();

        RefundIntent refund = RefundIntent.create(UUID.randomUUID(), fixture.reservation().reservationId(),
                RefundTrigger.DISPUTE_REJECTED, RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")),
                fixture.charge().id(), new IdempotencyKey("refund:DISPUTE_REJECTED:op-1"), UUID.randomUUID(),
                fixture.now());
        refundIntentAdapter.save(refund.transitionTo(RefundIntentStatus.IN_PROCESS)
                .transitionTo(RefundIntentStatus.COMPLETED).withExternalReference("rf-1"));
        RefundRecord refundRecord = RefundRecord.create(UUID.randomUUID(),
                refund.transitionTo(RefundIntentStatus.IN_PROCESS).transitionTo(RefundIntentStatus.COMPLETED)
                        .withExternalReference("rf-1"),
                fixture.reservation().ownerId(), fixture.reservation().boatId(),
                new Money(new BigDecimal("30000.00")), new Money(new BigDecimal("1000.00")), "accredited", "rf-1",
                fixture.now());
        refundRecordAdapter.save(refundRecord);

        assertThat(refundIntentAdapter.findByExternalReference("rf-1")).isPresent();
        assertThat(refundRecordAdapter.findByRefundIntentId(refund.id())).isPresent();
        assertThat(refundRecordAdapter.releasedAmountByReservationId(fixture.reservation().reservationId()))
                .hasValueSatisfying(money -> assertThat(money.amount()).isEqualByComparingTo("29000.00"));

        SettlementIntent settlement = SettlementIntent.create(UUID.randomUUID(),
                fixture.reservation().reservationId(), SettlementTrigger.COMPLETED,
                SettlementScope.STANDARD, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), new Money(new BigDecimal("30000.00")),
                fixture.charge().id(), new IdempotencyKey("settlement:COMPLETED:op-1"), null, fixture.now());
        settlementAdapter.save(settlement.transitionTo(SettlementStatus.IN_PROCESS)
                .transitionTo(SettlementStatus.COMPLETED).withExternalReference("st-1"));
        SettlementRecord settlementRecord = SettlementRecord.create(UUID.randomUUID(),
                settlement.transitionTo(SettlementStatus.IN_PROCESS).transitionTo(SettlementStatus.COMPLETED)
                        .withExternalReference("st-1"),
                fixture.reservation().ownerId(), fixture.reservation().boatId(),
                new Money(new BigDecimal("660000.00")), "standard settlement",
                new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                new Money(new BigDecimal("30000.00")), "st-1", fixture.now());
        settlementAdapter.save(settlementRecord);
        settlementAdapter.save(CommissionRecord.create(UUID.randomUUID(), settlementRecord,
                new Money(new BigDecimal("180000.00")), fixture.now()));

        assertThat(settlementAdapter.findByExternalReference("st-1")).isPresent();
        assertThat(settlementAdapter.findBySettlementIntentId(settlement.id())).isPresent();
        assertThat(settlementAdapter.findBySettlementRecordId(settlementRecord.id())).isPresent();
    }

    @Test
    void depositDispositionAppliesOnlyOneOutcome() {
        Fixture fixture = fixture();
        depositAdapter.save(DepositDisposition.create(fixture.reservation().reservationId(), fixture.now(), null,
                null, DepositDispositionStatus.HELD));

        assertThat(depositAdapter.compareAndSet(fixture.reservation().reservationId(),
                DepositDispositionStatus.HELD, DepositDisposition.create(fixture.reservation().reservationId(),
                        fixture.now(), null, null, DepositDispositionStatus.REFUND_REQUESTED))).isTrue();
        assertThat(depositAdapter.compareAndSet(fixture.reservation().reservationId(),
                DepositDispositionStatus.HELD, DepositDisposition.create(fixture.reservation().reservationId(),
                        fixture.now(), null, null, DepositDispositionStatus.SETTLEMENT_REQUESTED))).isFalse();
    }

    private Fixture fixture() {
        ReservationInformation information = ChargeFixtures.calculatedReservation();
        long version = parametersAdapter.findCurrent().map(p -> p.versionNumber() + 1).orElse(1L);
        FinancialParameters parameters = parametersAdapter.saveNewVersion(FinancialParameters.create(UUID.randomUUID(),
                version, new Percentage(new BigDecimal("20")), Money.ZERO, Percentage.ZERO, Percentage.ZERO, "test",
                ChargeFixtures.CREATED_AT));
        reservationAdapter.save(ReservationInformation.create(information.reservationId(), information.boatId(),
                information.baseRate(), information.baseRateParametersId(), information.startDate(),
                information.endDate(), information.passengers(), information.ownerId(), information.maxCapacity()));
        ReservationInformation calculated = reservationAdapter.saveCalculatedAmounts(information.withCalculated(
                new ReservationValue(new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                        new Money(new BigDecimal("30000.00")), new Money(new BigDecimal("990000.00"))),
                parameters.id(), ChargeFixtures.CREATED_AT));
        ChargeIntent charge = ChargeIntent.create(UUID.randomUUID(), calculated.reservationId(),
                new IdempotencyKey("charge:" + UUID.randomUUID()), calculated.totalAmount(),
                new PaymentMethodReference("token-123", "CREDIT_CARD", "4242"), ChargeFixtures.CREATED_AT)
                .transitionTo(ChargeIntentStatus.IN_PROCESS)
                .transitionTo(ChargeIntentStatus.CAPTURED)
                .withExternalReference("mp-payment-1");
        chargeIntentAdapter.save(charge);
        chargeRecordAdapter.save(com.seashare.seasharem3.domain.model.ChargeRecord.create(UUID.randomUUID(), charge,
                calculated, ChargeFixtures.CREATED_AT));
        return new Fixture(calculated, charge, ChargeFixtures.CREATED_AT);
    }

    private record Fixture(ReservationInformation reservation, ChargeIntent charge, java.time.Instant now) {
    }
}
