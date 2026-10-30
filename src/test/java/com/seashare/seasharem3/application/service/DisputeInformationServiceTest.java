package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.application.port.out.DisputeEventLogRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.model.DisputeEvent;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DisputeInformationServiceTest {

    private static final ReservationId RESERVATION = new ReservationId(UUID.randomUUID());
    private static final DisputeId DISPUTE = new DisputeId(UUID.randomUUID());
    private static final String EVENT_KEY = "v1.0-4a5b6c";
    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-23T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-12-23T10:00:01Z");

    private final DisputeEventLogRepository eventLog = mock(DisputeEventLogRepository.class);
    private final DepositDispositionRepository deposit = mock(DepositDispositionRepository.class);
    private final ReservationLockPort reservationLock = mock(ReservationLockPort.class);
    private final RequestRefundUseCase requestRefund = mock(RequestRefundUseCase.class);
    private final RequestSettlementUseCase requestSettlement = mock(RequestSettlementUseCase.class);
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();

    private DisputeInformationService service;

    @BeforeEach
    void setUp() {
        service = new DisputeInformationService(eventLog, deposit, reservationLock, failures, requestRefund,
                requestSettlement, Clock.fixed(NOW, ZoneOffset.UTC));
        when(eventLog.insertIfAbsent(any())).thenReturn(true);
        when(eventLog.markProcessed(any(), eq(NOW))).thenReturn(true);
    }

    @Test
    void pendingOnlyRecordsTheEvent() {
        service.handle(command(DisputeStatus.PENDING));

        DisputeEvent event = insertedEvent();
        verify(eventLog).markProcessed(event.id(), NOW);
        verifyNoInteractions(deposit, reservationLock, requestRefund, requestSettlement);
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void rejectedDelegatesToUc09WhenTheDepositIsHeld() {
        when(deposit.findByReservationId(RESERVATION)).thenReturn(Optional.of(disposition(DepositDispositionStatus.HELD)));

        service.handle(command(DisputeStatus.REJECTED));

        DisputeEvent event = insertedEvent();
        verify(reservationLock).lock(RESERVATION);
        verify(requestRefund).request(new RefundRequestCommand(RESERVATION.value(), RefundTrigger.DISPUTE_REJECTED,
                DISPUTE.value(), EVENT_KEY));
        verifyNoInteractions(requestSettlement);
        verify(eventLog).markProcessed(event.id(), NOW);
    }

    @Test
    void completedDelegatesToUc10WhenTheDepositIsHeld() {
        when(deposit.findByReservationId(RESERVATION)).thenReturn(Optional.of(disposition(DepositDispositionStatus.HELD)));

        service.handle(command(DisputeStatus.COMPLETED));

        DisputeEvent event = insertedEvent();
        verify(reservationLock).lock(RESERVATION);
        verify(requestSettlement).request(new SettlementCommand(RESERVATION, SettlementTrigger.DISPUTE_COMPLETED,
                SettlementScope.DEPOSIT, DISPUTE.value(), EVENT_KEY));
        verifyNoInteractions(requestRefund);
        verify(eventLog).markProcessed(event.id(), NOW);
    }

    @Test
    void noDepositDelegatesAndLetsUc09CreateTheTrackingRow() {
        when(deposit.findByReservationId(RESERVATION)).thenReturn(Optional.empty());

        service.handle(command(DisputeStatus.REJECTED));

        verify(requestRefund).request(new RefundRequestCommand(RESERVATION.value(), RefundTrigger.DISPUTE_REJECTED,
                DISPUTE.value(), EVENT_KEY));
        DisputeEvent event = insertedEvent();
        verify(eventLog).markProcessed(event.id(), NOW);
    }

    @Test
    void rejectedDoesNotRepeatAnAlreadyAppliedRefund() {
        when(deposit.findByReservationId(RESERVATION))
                .thenReturn(Optional.of(disposition(DepositDispositionStatus.REFUND_REQUESTED)));

        service.handle(command(DisputeStatus.REJECTED));

        verifyNoInteractions(requestRefund, requestSettlement);
        DisputeEvent event = insertedEvent();
        verify(eventLog).markProcessed(event.id(), NOW);
    }

    @Test
    void rejectedAgainstASettlementIsRecordedAsAConflict() {
        when(deposit.findByReservationId(RESERVATION))
                .thenReturn(Optional.of(disposition(DepositDispositionStatus.SETTLEMENT_REQUESTED)));

        service.handle(command(DisputeStatus.REJECTED));

        verifyNoInteractions(requestRefund, requestSettlement);
        assertThat(failures.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.useCase()).isEqualTo("UC08");
            assertThat(failure.payloadRef()).contains("SETTLEMENT_REQUESTED");
        });
        DisputeEvent event = insertedEvent();
        verify(eventLog).markProcessed(event.id(), NOW);
    }

    @Test
    void duplicateProcessedEventIsAcknowledgedWithoutProcessing() {
        when(eventLog.insertIfAbsent(any())).thenReturn(false);
        DisputeEvent stored = storedEvent(DisputeStatus.REJECTED, NOW);
        when(eventLog.findByKey(RESERVATION, DISPUTE, EVENT_KEY)).thenReturn(Optional.of(stored));

        service.handle(command(DisputeStatus.REJECTED));

        verifyNoInteractions(deposit, reservationLock, requestRefund, requestSettlement);
        verify(eventLog, never()).markProcessed(any(), any());
    }

    @Test
    void duplicateUnprocessedEventIsResumed() {
        when(eventLog.insertIfAbsent(any())).thenReturn(false);
        DisputeEvent stored = storedEvent(DisputeStatus.REJECTED, null);
        when(eventLog.findByKey(RESERVATION, DISPUTE, EVENT_KEY)).thenReturn(Optional.of(stored));
        when(deposit.findByReservationId(RESERVATION)).thenReturn(Optional.of(disposition(DepositDispositionStatus.HELD)));

        service.handle(command(DisputeStatus.REJECTED));

        verify(requestRefund).request(new RefundRequestCommand(RESERVATION.value(), RefundTrigger.DISPUTE_REJECTED,
                DISPUTE.value(), EVENT_KEY));
        verify(eventLog).markProcessed(stored.id(), NOW);
    }

    @Test
    void duplicateWithDifferentPayloadIsRecordedAsAConflict() {
        when(eventLog.insertIfAbsent(any())).thenReturn(false);
        DisputeEvent stored = storedEvent(DisputeStatus.COMPLETED, NOW);
        when(eventLog.findByKey(RESERVATION, DISPUTE, EVENT_KEY)).thenReturn(Optional.of(stored));

        service.handle(command(DisputeStatus.REJECTED));

        verifyNoInteractions(deposit, reservationLock, requestRefund, requestSettlement);
        assertThat(failures.failures()).singleElement().satisfies(failure ->
                assertThat(failure.payloadRef()).contains("event_key_reused"));
        verify(eventLog, never()).markProcessed(any(), any());
    }

    @Test
    void markProcessedFailurePropagatesForRetry() {
        when(eventLog.markProcessed(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.handle(command(DisputeStatus.PENDING)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("processed");
    }

    @Test
    void transientFailureFromUc09Propagates() {
        when(deposit.findByReservationId(RESERVATION)).thenReturn(Optional.of(disposition(DepositDispositionStatus.HELD)));
        doThrow(new IllegalStateException("database down")).when(requestRefund).request(any());

        assertThatThrownBy(() -> service.handle(command(DisputeStatus.REJECTED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database down");
    }

    private DisputeEvent insertedEvent() {
        ArgumentCaptor<DisputeEvent> captor = ArgumentCaptor.forClass(DisputeEvent.class);
        verify(eventLog).insertIfAbsent(captor.capture());
        return captor.getValue();
    }

    private static DisputeNotificationCommand command(DisputeStatus status) {
        return new DisputeNotificationCommand(RESERVATION, DISPUTE, status, STATUS_CHANGED_AT, EVENT_KEY);
    }

    private static DisputeEvent storedEvent(DisputeStatus status, Instant processedAt) {
        return new DisputeEvent(UUID.randomUUID(), RESERVATION, DISPUTE, EVENT_KEY, status, STATUS_CHANGED_AT,
                processedAt);
    }

    private static DepositDisposition disposition(DepositDispositionStatus status) {
        return DepositDisposition.create(RESERVATION, NOW.minusSeconds(60), DISPUTE.value(), null, status);
    }
}
