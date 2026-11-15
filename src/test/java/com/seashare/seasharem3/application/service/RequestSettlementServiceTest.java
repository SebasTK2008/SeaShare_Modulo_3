package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.FinancialParametersRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.FinancialParameters;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.service.SettlementCalculator;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.Percentage;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de la solicitud de liquidacion: formula, comision, deposito y datos faltantes. */
class RequestSettlementServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final ChargeRecordQueryPort chargeRecordQueryPort = mock(ChargeRecordQueryPort.class);
    private final ReservationInformationRepository reservationInformationRepository = mock(
            ReservationInformationRepository.class);
    private final FinancialParametersRepository financialParametersRepository = mock(
            FinancialParametersRepository.class);
    private final SettlementCommandPublisher settlementCommandPublisher = mock(SettlementCommandPublisher.class);
    private final OpenDepositTrackingUseCase openDepositTracking = mock(OpenDepositTrackingUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private RequestSettlementService service;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        service = new RequestSettlementService(settlementIntentRepository, chargeIntentRepository,
                chargeRecordQueryPort, reservationInformationRepository, financialParametersRepository,
                new SettlementCalculator(), settlementCommandPublisher, openDepositTracking, failureRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        ChargeRecord chargeRecord = ChargeRecord.create(UUID.randomUUID(), charge, RESERVATION, NOW);
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findApprovedByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(reservationInformationRepository.findByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(RESERVATION));
        when(financialParametersRepository.findById(RESERVATION.frozenParametersId()))
                .thenReturn(Optional.of(financialParameters()));
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false, true));
    }

    @Test
    void standardSettlementSubtractsCommissionAndInsurance() {
        SettlementRequestResult result = service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, null, "op-1"));

        assertThat(result.status()).isEqualTo(SettlementStatus.PENDING_SEND);
        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().amount().amount()).isEqualByComparingTo("660000.00");
        assertThat(captor.getValue().commissionAmount().amount()).isEqualByComparingTo("180000.00");
        assertThat(captor.getValue().depositAmount().amount()).isEqualByComparingTo("30000.00");
        assertThat(captor.getValue().capability())
                .isEqualTo(com.seashare.seasharem3.domain.valueobject.SettlementCapability.STANDARD_SETTLEMENT);
        assertUuidV4(captor.getValue().idempotencyKey().value());
        verify(settlementCommandPublisher).publish(any(UUID.class));
    }

    @Test
    void cancellationCompensationDoesNotUseCommissionOrInsurance() {
        service.request(new SettlementCommand(RESERVATION.reservationId(), SettlementTrigger.CANCELLED_MODERATE,
                SettlementScope.CANCELLATION_COMPENSATION, null, "op-2"));

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().amount().amount()).isEqualByComparingTo("450000.00");
        assertThat(captor.getValue().commissionAmount()).isEqualTo(Money.ZERO);
        verify(financialParametersRepository, never()).findById(any());
    }

    @Test
    void disputeSettlementMarksTheDepositAsSettlementRequested() {
        service.request(new SettlementCommand(RESERVATION.reservationId(), SettlementTrigger.DISPUTE_COMPLETED,
                SettlementScope.DEPOSIT, UUID.randomUUID(), "op-3"));

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().amount().amount()).isEqualByComparingTo("30000.00");
        verify(openDepositTracking).open(any());
    }

    @Test
    void failedRequestReturnsFailedWithoutCreatingAnIntent() {
        when(chargeIntentRepository.findApprovedByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.empty());

        SettlementRequestResult result = service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, null, "op-4"));

        assertThat(result.status()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result.settlementIntentId()).isNull();
        verify(settlementIntentRepository, never()).save(any());
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("CHARGE_INTENT_NOT_FOUND");
    }

    @Test
    void assignsADistinctUuidV4KeyToEachDistinctOperation() {
        service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, null, "op-5"));
        service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, null, "op-6"));

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SettlementIntent::operationKey)
                .containsExactly("op-5", "op-6");
        assertThat(captor.getAllValues()).extracting(intent -> intent.idempotencyKey().value())
                .doesNotHaveDuplicates()
                .allSatisfy(RequestSettlementServiceTest::assertUuidV4);
        assertThat(captor.getAllValues()).extracting(SettlementIntent::capability)
                .containsOnly(com.seashare.seasharem3.domain.valueobject.SettlementCapability.STANDARD_SETTLEMENT);
    }

    @Test
    void returnsTheExistingIntentWhenTheSameOperationArrivesAgain() {
        SettlementCommand command = new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, null, "op-7");
        service.request(command);
        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        SettlementIntent existing = captor.getValue();
        when(settlementIntentRepository.findByReservationIdAndOperationKey(RESERVATION.reservationId(), "op-7"))
                .thenReturn(Optional.of(existing));

        SettlementRequestResult second = service.request(command);

        assertThat(second.settlementIntentId()).isEqualTo(existing.id());
        assertThat(second.status()).isEqualTo(existing.status());
        verify(settlementIntentRepository, org.mockito.Mockito.times(1)).save(any());
        verify(settlementCommandPublisher, org.mockito.Mockito.times(1)).publish(any(UUID.class));
    }

    @Test
    void doesNotCreateASecondDepositSettlementWhenAnotherOperationAlreadyRequestedIt() {
        service.request(new SettlementCommand(RESERVATION.reservationId(), SettlementTrigger.DISPUTE_COMPLETED,
                SettlementScope.DEPOSIT, UUID.randomUUID(), "dispute-event-1"));
        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        SettlementIntent first = captor.getValue();
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false, false));
        when(settlementIntentRepository.findLatestByReservationIdAndScope(RESERVATION.reservationId(),
                SettlementScope.DEPOSIT)).thenReturn(Optional.of(first));

        SettlementRequestResult second = service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, UUID.randomUUID(), "other-event"));

        assertThat(second.settlementIntentId()).isEqualTo(first.id());
        assertThat(second.status()).isNotEqualTo(SettlementStatus.FAILED);
        verify(settlementIntentRepository, org.mockito.Mockito.times(1)).save(any());
        verify(settlementCommandPublisher, org.mockito.Mockito.times(1)).publish(any(UUID.class));
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("INTERNAL_ERROR");
        assertThat(failureRecorder.failures().get(0).payloadRef())
                .contains("duplicate_deposit_outcome=SETTLEMENT_REQUESTED");
    }

    @Test
    void failsWhenTheDepositSettlementWasRequestedButThereIsNoIntentToReturn() {
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false, false));

        SettlementRequestResult result = service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, UUID.randomUUID(), "op-8"));

        assertThat(result.status()).isEqualTo(SettlementStatus.FAILED);
        verify(settlementIntentRepository, never()).save(any());
    }

    @Test
    void retriesTheDepositSettlementWhenThePreviousOneEndedWithoutSettling() {
        for (SettlementStatus failed : new SettlementStatus[] {SettlementStatus.REJECTED,
            SettlementStatus.CANCELLED, SettlementStatus.EXPIRED}) {
            SettlementIntent previous = previousDepositSettlement().transitionTo(failed);
            stubPreviousDepositSettlement(previous);

            SettlementRequestResult result = service.request(new SettlementCommand(RESERVATION.reservationId(),
                    SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, UUID.randomUUID(),
                    "retry-" + failed));

            assertThat(result.settlementIntentId()).as("retry after %s", failed).isNotNull()
                    .isNotEqualTo(previous.id());
            assertThat(result.status()).isEqualTo(SettlementStatus.PENDING_SEND);
            verify(settlementIntentRepository).save(any(SettlementIntent.class));
            verify(settlementCommandPublisher).publish(any(UUID.class));
        }
    }

    @Test
    void doesNotRetryWhileThePreviousDepositSettlementIsInFlightOrCompleted() {
        for (SettlementStatus ongoing : new SettlementStatus[] {SettlementStatus.PENDING_SEND,
            SettlementStatus.IN_PROCESS, SettlementStatus.COMMUNICATION_ERROR, SettlementStatus.COMPLETED}) {
            SettlementIntent previous = previousDepositSettlement().transitionTo(ongoing);
            stubPreviousDepositSettlement(previous);

            SettlementRequestResult result = service.request(new SettlementCommand(RESERVATION.reservationId(),
                    SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, UUID.randomUUID(),
                    "again-" + ongoing));

            assertThat(result.settlementIntentId()).as("no retry while %s", ongoing).isEqualTo(previous.id());
            verify(settlementIntentRepository, never()).save(any());
            verify(settlementCommandPublisher, never()).publish(any(UUID.class));
        }
    }

    private void stubPreviousDepositSettlement(SettlementIntent previous) {
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false, false));
        when(settlementIntentRepository.findLatestByReservationIdAndScope(RESERVATION.reservationId(),
                SettlementScope.DEPOSIT)).thenReturn(Optional.of(previous));
        org.mockito.Mockito.clearInvocations(settlementIntentRepository, settlementCommandPublisher);
    }

    private SettlementIntent previousDepositSettlement() {
        return SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.DISPUTE_COMPLETED, SettlementScope.DEPOSIT, RESERVATION.depositAmount(), Money.ZERO,
                RESERVATION.depositAmount(), charge.id(), "dispute-event-1",
                new com.seashare.seasharem3.domain.valueobject.IdempotencyKey(UUID.randomUUID().toString()),
                UUID.randomUUID(), NOW);
    }

    private static void assertUuidV4(String value) {
        UUID key = UUID.fromString(value);
        assertThat(key.version()).isEqualTo(4);
        assertThat(key.variant()).isEqualTo(2);
    }

    private static FinancialParameters financialParameters() {
        return FinancialParameters.create(UUID.randomUUID(), 1, new Percentage(new BigDecimal("20")), Money.ZERO,
                Percentage.ZERO, Percentage.ZERO, "test", NOW);
    }
}
