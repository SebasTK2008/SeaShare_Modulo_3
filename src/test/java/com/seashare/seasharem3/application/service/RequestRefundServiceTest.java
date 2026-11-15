package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.RefundRequestResult;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.RefundCommandPublisher;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.RefundCalculator;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

/** Pruebas de la solicitud de reembolso: intencion, idempotencia, datos faltantes y deposito. */
class RequestRefundServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final ChargeRecordQueryPort chargeRecordQueryPort = mock(ChargeRecordQueryPort.class);
    private final ReservationInformationRepository reservationInformationRepository = mock(
            ReservationInformationRepository.class);
    private final RefundCommandPublisher refundCommandPublisher = mock(RefundCommandPublisher.class);
    private final OpenDepositTrackingUseCase openDepositTracking = mock(OpenDepositTrackingUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private RequestRefundService service;
    private ChargeIntent charge;
    private ChargeRecord chargeRecord;

    @BeforeEach
    void setUp() {
        service = new RequestRefundService(refundIntentRepository, chargeIntentRepository, chargeRecordQueryPort,
                reservationInformationRepository, new RefundCalculator(), refundCommandPublisher, openDepositTracking,
                failureRecorder, Clock.fixed(NOW, ZoneOffset.UTC));
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        chargeRecord = ChargeRecord.create(UUID.randomUUID(), charge, RESERVATION, NOW);
        when(refundIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findApprovedByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.REFUND_REQUESTED, false, true));
    }

    @Test
    void createsAPendingIntentAndPublishesTheCommand() {
        RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_MODERATE, "op-1"));

        assertThat(result.refundIntentId()).isNotNull();
        assertThat(result.status()).isEqualTo(RefundIntentStatus.PENDING_SEND);
        verify(refundIntentRepository).save(any(RefundIntent.class));
        verify(refundCommandPublisher).publish(any(UUID.class));
        var saved = org.mockito.ArgumentCaptor.forClass(RefundIntent.class);
        verify(refundIntentRepository).save(saved.capture());
        assertThat(saved.getValue().amount().amount()).isEqualByComparingTo("480000.00");
        assertThat(saved.getValue().scope()).isEqualTo(RefundScope.HALF_RENTAL_PLUS_DEPOSIT);
        assertUuidV4(saved.getValue().idempotencyKey().value());
    }

    @Test
    void assignsADistinctUuidV4KeyToEachDistinctOperation() {
        service.request(command(RefundTrigger.CANCELLED_STRICT, "op-1"));
        service.request(command(RefundTrigger.CANCELLED_STRICT, "op-2"));

        var captor = org.mockito.ArgumentCaptor.forClass(RefundIntent.class);
        verify(refundIntentRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(RefundIntent::operationKey)
                .containsExactly("op-1", "op-2");
        assertThat(captor.getAllValues()).extracting(intent -> intent.idempotencyKey().value())
                .doesNotHaveDuplicates()
                .allSatisfy(RequestRefundServiceTest::assertUuidV4);
        verify(refundCommandPublisher, org.mockito.Mockito.times(2)).publish(any(UUID.class));
    }

    @Test
    void returnsTheExistingIntentWhenTheSameOperationArrivesAgain() {
        RefundIntent existing = RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                RefundTrigger.CANCELLED_STRICT, RefundScope.DEPOSIT_ONLY, RESERVATION.depositAmount(), charge.id(),
                "op-1", new com.seashare.seasharem3.domain.valueobject.IdempotencyKey(UUID.randomUUID().toString()),
                null, NOW);
        when(refundIntentRepository.findByReservationIdAndOperationKey(RESERVATION.reservationId(), "op-1"))
                .thenReturn(Optional.of(existing));

        RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_STRICT, "op-1"));

        assertThat(result.refundIntentId()).isEqualTo(existing.id());
        assertThat(result.status()).isEqualTo(existing.status());
        verify(refundIntentRepository, never()).save(any());
        verify(refundCommandPublisher, never()).publish(any(UUID.class));
        verify(openDepositTracking, never()).open(any());
    }

    @Test
    void doesNotCreateASecondIntentWhenAnotherOperationAlreadyRequestedTheDepositRefund() {
        RefundIntent first = RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                RefundTrigger.DISPUTE_REJECTED, RefundScope.DEPOSIT_ONLY, RESERVATION.depositAmount(), charge.id(),
                "dispute-event-1",
                new com.seashare.seasharem3.domain.valueobject.IdempotencyKey(UUID.randomUUID().toString()),
                UUID.randomUUID(), NOW);
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.REFUND_REQUESTED, false, false));
        when(refundIntentRepository.findLatestByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(first));

        RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_STRICT, "reservation:CANCELLED"));

        assertThat(result.refundIntentId()).isEqualTo(first.id());
        assertThat(result.status()).isNotEqualTo(RefundIntentStatus.FAILED);
        verify(refundIntentRepository, never()).save(any());
        verify(refundCommandPublisher, never()).publish(any(UUID.class));
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("INTERNAL_ERROR");
        assertThat(failureRecorder.failures().get(0).payloadRef()).contains("duplicate_deposit_outcome=REFUND_REQUESTED");
    }

    @Test
    void failsWhenTheDepositRefundWasRequestedButThereIsNoIntentToReturn() {
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.REFUND_REQUESTED, false, false));
        when(refundIntentRepository.findLatestByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.empty());

        RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_STRICT, "op-9"));

        assertThat(result.status()).isEqualTo(RefundIntentStatus.FAILED);
        verify(refundIntentRepository, never()).save(any());
    }

    @Test
    void recordsAFailureWithoutACapturedCharge() {
        when(chargeIntentRepository.findApprovedByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.empty());

        RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_FLEXIBLE, "op-2"));

        assertThat(result.status()).isEqualTo(RefundIntentStatus.FAILED);
        assertThat(result.refundIntentId()).isNull();
        verify(refundIntentRepository, never()).save(any());
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("CHARGE_INTENT_NOT_FOUND");
    }

    @Test
    void doesNotRefundWhenTheDepositWasAlreadySettled() {
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false, false));

        service.request(command(RefundTrigger.CANCELLED_STRICT, "op-3"));

        verify(refundIntentRepository, never()).save(any());
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("INTERNAL_ERROR");
    }

    @Test
    void retriesWhenThePreviousDepositRefundEndedWithoutRefundingMoney() {
        for (RefundIntentStatus failed : new RefundIntentStatus[] {RefundIntentStatus.REJECTED,
            RefundIntentStatus.CANCELLED, RefundIntentStatus.EXPIRED}) {
            RefundIntent previous = depositRefund("dispute-event-1").transitionTo(failed);
            when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(
                    RESERVATION.reservationId(), DepositDispositionStatus.REFUND_REQUESTED, false, false));
            when(refundIntentRepository.findLatestByReservationId(RESERVATION.reservationId()))
                    .thenReturn(Optional.of(previous));
            org.mockito.Mockito.clearInvocations(refundIntentRepository, refundCommandPublisher);

            RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_STRICT, "retry-" + failed));

            assertThat(result.refundIntentId()).as("retry after %s", failed).isNotNull()
                    .isNotEqualTo(previous.id());
            assertThat(result.status()).isEqualTo(RefundIntentStatus.PENDING_SEND);
            verify(refundIntentRepository).save(any(RefundIntent.class));
            verify(refundCommandPublisher).publish(any(UUID.class));
        }
    }

    @Test
    void doesNotRetryWhileThePreviousDepositRefundIsInFlightOrCompleted() {
        for (RefundIntentStatus ongoing : new RefundIntentStatus[] {RefundIntentStatus.PENDING_SEND,
            RefundIntentStatus.IN_PROCESS, RefundIntentStatus.COMMUNICATION_ERROR, RefundIntentStatus.COMPLETED}) {
            RefundIntent previous = depositRefund("dispute-event-1").transitionTo(ongoing);
            when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(
                    RESERVATION.reservationId(), DepositDispositionStatus.REFUND_REQUESTED, false, false));
            when(refundIntentRepository.findLatestByReservationId(RESERVATION.reservationId()))
                    .thenReturn(Optional.of(previous));
            org.mockito.Mockito.clearInvocations(refundIntentRepository, refundCommandPublisher);

            RefundRequestResult result = service.request(command(RefundTrigger.CANCELLED_STRICT, "again-" + ongoing));

            assertThat(result.refundIntentId()).as("no retry while %s", ongoing).isEqualTo(previous.id());
            verify(refundIntentRepository, never()).save(any());
            verify(refundCommandPublisher, never()).publish(any(UUID.class));
        }
    }

    private RefundIntent depositRefund(String operationKey) {
        return RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.DISPUTE_REJECTED,
                RefundScope.DEPOSIT_ONLY, RESERVATION.depositAmount(), charge.id(), operationKey,
                new com.seashare.seasharem3.domain.valueobject.IdempotencyKey(UUID.randomUUID().toString()),
                UUID.randomUUID(), NOW);
    }

    private static RefundRequestCommand command(RefundTrigger trigger, String operationKey) {
        return new RefundRequestCommand(RESERVATION.reservationId().value(), trigger, null, operationKey);
    }

    private static void assertUuidV4(String value) {
        UUID key = UUID.fromString(value);
        assertThat(key.version()).isEqualTo(4);
        assertThat(key.variant()).isEqualTo(2);
    }
}
