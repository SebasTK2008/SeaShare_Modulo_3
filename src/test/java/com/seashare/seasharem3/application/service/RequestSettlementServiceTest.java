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
        when(settlementIntentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findApprovedByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(reservationInformationRepository.findByReservationId(RESERVATION.reservationId()))
                .thenReturn(Optional.of(RESERVATION));
        when(financialParametersRepository.findById(RESERVATION.frozenParametersId()))
                .thenReturn(Optional.of(financialParameters()));
        when(openDepositTracking.open(any())).thenReturn(new DepositTrackingResult(RESERVATION.reservationId(),
                DepositDispositionStatus.SETTLEMENT_REQUESTED, false));
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
    void idempotentRequestReturnsTheExistingIntent() {
        SettlementIntent existing = SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), new Money(new BigDecimal("30000.00")), charge.id(),
                new com.seashare.seasharem3.domain.valueobject.IdempotencyKey("settlement:COMPLETED:op-5"), null,
                NOW);
        when(settlementIntentRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existing));

        SettlementRequestResult result = service.request(new SettlementCommand(RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, null, "op-5"));

        assertThat(result.settlementIntentId()).isEqualTo(existing.id());
        verify(settlementIntentRepository, never()).save(any());
    }

    private static FinancialParameters financialParameters() {
        return FinancialParameters.create(UUID.randomUUID(), 1, new Percentage(new BigDecimal("20")), Money.ZERO,
                Percentage.ZERO, Percentage.ZERO, "test", NOW);
    }
}
