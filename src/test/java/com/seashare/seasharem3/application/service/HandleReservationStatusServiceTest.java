package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.DepositTrackingResult;
import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.dto.ProcessChargeCommand;
import com.seashare.seasharem3.application.dto.RefundRequestCommand;
import com.seashare.seasharem3.application.dto.RefundRequestResult;
import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.application.dto.SettlementCommand;
import com.seashare.seasharem3.application.dto.SettlementRequestResult;
import com.seashare.seasharem3.application.port.in.OpenDepositTrackingUseCase;
import com.seashare.seasharem3.application.port.in.ProcessChargeUseCase;
import com.seashare.seasharem3.application.port.in.RequestRefundUseCase;
import com.seashare.seasharem3.application.port.in.RequestSettlementUseCase;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationStatusLogRepository;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.service.ReservationStatusPolicy;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.FinancialOperationType;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import com.seashare.seasharem3.support.NoOpReservationLockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas del servicio de UC07 (plan T005/T006/T013/T014): prerrequisitos, identidad única de
 * operación, delegación por estado y exclusión mutua del depósito.
 */
class HandleReservationStatusServiceTest {

    private static final Instant STATUS_CHANGED_AT = Instant.parse("2026-12-05T14:30:00Z");
    private static final Instant NOW = Instant.parse("2026-12-05T14:30:01Z");
    private static final String TOKEN = "tok_12345";
    private static final String PAYER_EMAIL = "arrendatario@example.com";

    private final ReservationInformationRepository reservationInformation =
            mock(ReservationInformationRepository.class);
    private final ReservationStatusLogRepository statusLog = mock(ReservationStatusLogRepository.class);
    private final ProcessChargeUseCase processCharge = mock(ProcessChargeUseCase.class);
    private final RequestRefundUseCase requestRefund = mock(RequestRefundUseCase.class);
    private final RequestSettlementUseCase requestSettlement = mock(RequestSettlementUseCase.class);
    private final OpenDepositTrackingUseCase openDepositTracking = mock(OpenDepositTrackingUseCase.class);
    private final NoOpReservationLockPort reservationLock = new NoOpReservationLockPort();
    private final InMemoryFailureRecorder failures = new InMemoryFailureRecorder();

    private final ReservationInformation calculatedReservation = ChargeFixtures.calculatedReservation();
    private final ReservationId reservationId = calculatedReservation.reservationId();

    private HandleReservationStatusService service;

    @BeforeEach
    void setUp() {
        service = new HandleReservationStatusService(reservationInformation, statusLog, reservationLock, failures,
                processCharge, requestRefund, requestSettlement, openDepositTracking, new ReservationStatusPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        when(statusLog.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(reservationInformation.findByReservationId(reservationId)).thenReturn(Optional.of(calculatedReservation));
        when(requestRefund.request(any())).thenReturn(
                new RefundRequestResult(UUID.randomUUID(), RefundIntentStatus.PENDING_SEND));
        when(requestSettlement.request(any())).thenReturn(
                new SettlementRequestResult(UUID.randomUUID(), SettlementStatus.PENDING_SEND));
        when(openDepositTracking.open(any())).thenReturn(
                new DepositTrackingResult(reservationId, DepositDispositionStatus.HELD, true));
    }

    @Test
    void operatingStateIsAcknowledgedWithoutTouchingTheDatabase() {
        service.handle(command(ReservationStatus.INITIATED));

        verifyNoInteractions(reservationInformation, statusLog, processCharge, requestRefund, requestSettlement,
                openDepositTracking);
        assertThat(reservationLock.locked()).isEmpty();
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void pendingDelegatesTheChargeWithThePaymentToken() {
        service.handle(pendingCommand());

        verify(processCharge).process(new ProcessChargeCommand(reservationId.value(), TOKEN, "CREDIT_CARD",
                Map.of("payer_email", PAYER_EMAIL), operationKey(ReservationStatus.PENDING)));
        verify(statusLog).markProcessed(any(), eq(NOW), eq("delegated"));
        verifyNoInteractions(requestRefund, requestSettlement, openDepositTracking);
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void missingReservationIsRecordedWithoutDelegating() {
        when(reservationInformation.findByReservationId(reservationId)).thenReturn(Optional.empty());

        service.handle(pendingCommand());

        assertThat(failures.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.reason()).isEqualTo("RESERVATION_INFO_NOT_FOUND"));
        verifyNoInteractions(processCharge, requestRefund, requestSettlement, openDepositTracking);
        verify(statusLog, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservationWithoutCalculatedAmountsIsRecordedWithoutDelegating() {
        ReservationInformation notCalculated = ReservationInformation.create(reservationId,
                calculatedReservation.boatId(), calculatedReservation.baseRate(), null,
                calculatedReservation.startDate(), calculatedReservation.endDate(),
                calculatedReservation.passengers(), calculatedReservation.ownerId(),
                calculatedReservation.maxCapacity());
        when(reservationInformation.findByReservationId(reservationId)).thenReturn(Optional.of(notCalculated));

        service.handle(command(ReservationStatus.CANCELLED_FLEXIBLE));

        assertThat(failures.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.reason()).isEqualTo("RESERVATION_INFO_INCOMPLETE"));
        verifyNoInteractions(requestRefund, requestSettlement, openDepositTracking);
    }

    @Test
    void duplicateOperationIsNotRepeated() {
        when(statusLog.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(false);

        service.handle(command(ReservationStatus.COMPLETED));

        verifyNoInteractions(requestRefund, requestSettlement, openDepositTracking);
        verify(statusLog, never()).markProcessed(any(), any(), any());
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void flexibleCancellationOnlyRequestsTheRefund() {
        service.handle(command(ReservationStatus.CANCELLED_FLEXIBLE));

        verify(requestRefund).request(new RefundRequestCommand(reservationId.value(),
                RefundTrigger.CANCELLED_FLEXIBLE, null, operationKey(ReservationStatus.CANCELLED_FLEXIBLE)));
        verifyNoInteractions(requestSettlement, openDepositTracking);
    }

    @Test
    void moderateCancellationRefundsAndCompensatesTheOwner() {
        service.handle(command(ReservationStatus.CANCELLED_MODERATE));

        verify(requestRefund).request(any(RefundRequestCommand.class));
        verify(requestSettlement).request(new SettlementCommand(reservationId,
                SettlementTrigger.CANCELLED_MODERATE, SettlementScope.CANCELLATION_COMPENSATION, null,
                operationKey(ReservationStatus.CANCELLED_MODERATE)));
        verifyNoInteractions(openDepositTracking);
        assertThat(recordedOperationTypes()).containsExactly(FinancialOperationType.REFUND,
                FinancialOperationType.SETTLEMENT);
    }

    @Test
    void strictCancellationCompensatesAndRefundsTheDeposit() {
        service.handle(command(ReservationStatus.CANCELLED_STRICT));

        verify(requestSettlement).request(new SettlementCommand(reservationId,
                SettlementTrigger.CANCELLED_STRICT, SettlementScope.CANCELLATION_COMPENSATION, null,
                operationKey(ReservationStatus.CANCELLED_STRICT)));
        verify(requestRefund).request(new RefundRequestCommand(reservationId.value(),
                RefundTrigger.CANCELLED_STRICT, null, operationKey(ReservationStatus.CANCELLED_STRICT)));
        assertThat(recordedOperationTypes()).containsExactly(FinancialOperationType.SETTLEMENT,
                FinancialOperationType.REFUND);
    }

    @Test
    void hostCancellationRefundsThePaidValue() {
        service.handle(command(ReservationStatus.CANCELLED_BY_HOST));

        verify(requestRefund).request(new RefundRequestCommand(reservationId.value(),
                RefundTrigger.CANCELLED_BY_HOST, null, operationKey(ReservationStatus.CANCELLED_BY_HOST)));
        verifyNoInteractions(requestSettlement, openDepositTracking);
    }

    @Test
    void completedSettlesStandardAndHoldsTheDepositAfterwards() {
        service.handle(command(ReservationStatus.COMPLETED));

        var order = inOrder(requestSettlement, openDepositTracking);
        order.verify(requestSettlement).request(new SettlementCommand(reservationId, SettlementTrigger.COMPLETED,
                SettlementScope.STANDARD, null, operationKey(ReservationStatus.COMPLETED)));
        order.verify(openDepositTracking).open(new OpenDepositTrackingCommand(reservationId,
                operationKey(ReservationStatus.COMPLETED), DepositDispositionStatus.HELD, null, null,
                STATUS_CHANGED_AT));
        assertThat(recordedOperationTypes()).containsExactly(FinancialOperationType.SETTLEMENT);
        assertThat(failures.failures()).isEmpty();
    }

    @Test
    void completedFailsTheWholeMessageWhenTheSettlementReturnsFailed() {
        when(requestSettlement.request(any())).thenReturn(
                new SettlementRequestResult(null, SettlementStatus.FAILED));

        assertThatThrownBy(() -> service.handle(command(ReservationStatus.COMPLETED)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("settlement returned FAILED");

        verifyNoInteractions(openDepositTracking);
        verify(statusLog, never()).markProcessed(any(), any(), any());
    }

    @Test
    void moderateCancellationRevertsTheMessageWhenTheSettlementFails() {
        when(requestSettlement.request(any())).thenReturn(
                new SettlementRequestResult(null, SettlementStatus.FAILED));

        assertThatThrownBy(() -> service.handle(command(ReservationStatus.CANCELLED_MODERATE)))
                .isInstanceOf(DomainException.class);

        // La reversión la aporta @Transactional; aquí solo se comprueba que la excepción propaga.
        verify(requestRefund).request(any(RefundRequestCommand.class));
        verifyNoInteractions(openDepositTracking);
    }

    @Test
    void flexibleCancellationFailsTheMessageWhenTheRefundReturnsFailed() {
        when(requestRefund.request(any())).thenReturn(new RefundRequestResult(null, RefundIntentStatus.FAILED));

        assertThatThrownBy(() -> service.handle(command(ReservationStatus.CANCELLED_FLEXIBLE)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("refund returned FAILED");

        verify(statusLog, never()).markProcessed(any(), any(), any());
    }

    @Test
    void completedKeepsAnAlreadyResolvedDepositWithoutFailingTheSettlement() {
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(reservationId,
                DepositDispositionStatus.REFUND_REQUESTED, false));

        service.handle(command(ReservationStatus.COMPLETED));

        verify(statusLog).markProcessed(any(), eq(NOW), eq("delegated"));
        assertThat(failures.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.payloadRef()).contains("deposit_disposition=REFUND_REQUESTED"));
    }

    @Test
    void aSecondPendingWithANewStatusChangedAtUsesANewOperationKey() {
        service.handle(pendingCommand());
        service.handle(new ReservationStatusCommand(reservationId.value(), ReservationStatus.PENDING,
                STATUS_CHANGED_AT.plusSeconds(60), TOKEN, "CREDIT_CARD", Map.of("payer_email", PAYER_EMAIL)));

        ArgumentCaptor<ProcessChargeCommand> captor = ArgumentCaptor.forClass(ProcessChargeCommand.class);
        verify(processCharge, org.mockito.Mockito.times(2)).process(captor.capture());
        assertThat(captor.getAllValues()).extracting(ProcessChargeCommand::operationKey)
                .containsExactly(operationKey(ReservationStatus.PENDING),
                        reservationId.value() + ":PENDING:" + STATUS_CHANGED_AT.plusSeconds(60));
    }

    @Test
    void locksTheReservationBeforeDelegating() {
        service.handle(command(ReservationStatus.CANCELLED_FLEXIBLE));

        assertThat(reservationLock.locked()).containsExactly(reservationId);
    }

    private ReservationStatusCommand pendingCommand() {
        return new ReservationStatusCommand(reservationId.value(), ReservationStatus.PENDING, STATUS_CHANGED_AT,
                TOKEN, "CREDIT_CARD", Map.of("payer_email", PAYER_EMAIL));
    }

    private ReservationStatusCommand command(ReservationStatus status) {
        return new ReservationStatusCommand(reservationId.value(), status, STATUS_CHANGED_AT, null, null, null);
    }

    private String operationKey(ReservationStatus status) {
        String base = reservationId.value() + ":" + status;
        return status == ReservationStatus.PENDING ? base + ":" + STATUS_CHANGED_AT : base;
    }

    private List<FinancialOperationType> recordedOperationTypes() {
        ArgumentCaptor<FinancialOperationType> captor = ArgumentCaptor.forClass(FinancialOperationType.class);
        verify(statusLog, org.mockito.Mockito.atLeastOnce()).insertIfAbsent(any(), any(), any(),
                captor.capture(), any(IdempotencyKey.class), any());
        return captor.getAllValues();
    }
}
