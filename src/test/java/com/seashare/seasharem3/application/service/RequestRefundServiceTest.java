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
        when(refundIntentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(refundIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findApprovedByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.REFUND_REQUESTED, false));
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
        assertThat(saved.getValue().idempotencyKey().value()).isEqualTo("refund:CANCELLED_MODERATE:op-1");
    }

    @Test
    void doesNotCreateASecondIntentForTheSameOperation() {
        when(refundIntentRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(RefundIntent.class)));

        service.request(command(RefundTrigger.CANCELLED_STRICT, "op-1"));

        verify(refundIntentRepository, never()).save(any());
        verify(refundCommandPublisher, never()).publish(any());
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
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false));

        service.request(command(RefundTrigger.CANCELLED_STRICT, "op-3"));

        verify(refundIntentRepository, never()).save(any());
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("INTERNAL_ERROR");
    }

    private static RefundRequestCommand command(RefundTrigger trigger, String operationKey) {
        return new RefundRequestCommand(RESERVATION.reservationId().value(), trigger, null, operationKey);
    }
}
