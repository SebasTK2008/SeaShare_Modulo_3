package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.CommissionRecordRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
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

/** Pruebas del registro de liquidacion: registro, comision atomica y estados no exitosos. */
class RegisterSettlementResultServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final SettlementRecordRepository settlementRecordRepository = mock(SettlementRecordRepository.class);
    private final CommissionRecordRepository commissionRecordRepository = mock(CommissionRecordRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final ChargeRecordQueryPort chargeRecordQueryPort = mock(ChargeRecordQueryPort.class);
    private final ReservationInformationRepository reservationInformationRepository = mock(
            ReservationInformationRepository.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private RegisterSettlementResultService service;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        service = new RegisterSettlementResultService(settlementIntentRepository, settlementRecordRepository,
                commissionRecordRepository, chargeIntentRepository, chargeRecordQueryPort,
                reservationInformationRepository, failureRecorder, Clock.fixed(NOW, ZoneOffset.UTC));
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        ChargeRecord chargeRecord = ChargeRecord.create(UUID.randomUUID(), charge, RESERVATION, NOW);
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(settlementRecordRepository.findBySettlementIntentId(any())).thenReturn(Optional.empty());
        when(settlementRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionRecordRepository.findBySettlementRecordId(any())).thenReturn(Optional.empty());
        when(commissionRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void standardSettlementCreatesRecordAndCommissionAtomically() {
        SettlementIntent intent = intent(SettlementScope.STANDARD, SettlementStatus.IN_PROCESS);
        when(settlementIntentRepository.findByExternalReference("st-1")).thenReturn(Optional.of(intent));

        service.register(new GatewaySettlementResult("approved", "st-1", "accredited",
                new Money(new BigDecimal("660000.00"))));

        var record = org.mockito.ArgumentCaptor.forClass(SettlementRecord.class);
        var commission = org.mockito.ArgumentCaptor.forClass(CommissionRecord.class);
        verify(settlementRecordRepository).save(record.capture());
        verify(commissionRecordRepository).save(commission.capture());
        assertThat(record.getValue().amount().amount()).isEqualByComparingTo("660000.00");
        assertThat(record.getValue().insuranceAmount().amount()).isEqualByComparingTo("60000.00");
        assertThat(commission.getValue().amount().amount()).isEqualByComparingTo("180000.00");
        assertThat(commission.getValue().settlementRecordId()).isEqualTo(record.getValue().id());
    }

    @Test
    void cancellationSettlementDoesNotCreateCommission() {
        SettlementIntent intent = intent(SettlementScope.CANCELLATION_COMPENSATION, SettlementStatus.IN_PROCESS);
        when(settlementIntentRepository.findByExternalReference("st-2")).thenReturn(Optional.of(intent));

        service.register(new GatewaySettlementResult("approved", "st-2", null,
                new Money(new BigDecimal("450000.00"))));

        verify(settlementRecordRepository).save(any());
        verify(commissionRecordRepository, never()).save(any());
    }

    @Test
    void rejectedSettlementOnlyUpdatesTheIntent() {
        SettlementIntent intent = intent(SettlementScope.STANDARD, SettlementStatus.IN_PROCESS);
        when(settlementIntentRepository.findByExternalReference("st-3")).thenReturn(Optional.of(intent));

        service.register(new GatewaySettlementResult("rejected", "st-3", "rejected_by_issuer", null));

        verify(settlementRecordRepository, never()).save(any());
        verify(commissionRecordRepository, never()).save(any());
        verify(settlementIntentRepository).save(any(SettlementIntent.class));
    }

    @Test
    void repeatedConfirmationDoesNotDuplicateTheCommission() {
        SettlementIntent intent = intent(SettlementScope.STANDARD, SettlementStatus.COMPLETED);
        SettlementRecord existing = SettlementRecord.create(UUID.randomUUID(), intent, RESERVATION.ownerId(),
                RESERVATION.boatId(), new Money(new BigDecimal("660000.00")), null,
                new Money(new BigDecimal("900000.00")), new Money(new BigDecimal("60000.00")),
                new Money(new BigDecimal("30000.00")), "st-1", NOW);
        when(settlementIntentRepository.findByExternalReference("st-1")).thenReturn(Optional.of(intent));
        when(settlementRecordRepository.findBySettlementIntentId(intent.id())).thenReturn(Optional.of(existing));
        when(commissionRecordRepository.findBySettlementRecordId(existing.id()))
                .thenReturn(Optional.of(mock(CommissionRecord.class)));

        service.register(new GatewaySettlementResult("approved", "st-1", null,
                new Money(new BigDecimal("660000.00"))));

        verify(settlementRecordRepository, never()).save(any());
        verify(commissionRecordRepository, never()).save(any());
    }

    private SettlementIntent intent(SettlementScope scope, SettlementStatus status) {
        SettlementIntent created = SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                scope == SettlementScope.STANDARD ? SettlementTrigger.COMPLETED
                        : SettlementTrigger.CANCELLED_MODERATE,
                scope, new Money(new BigDecimal(scope == SettlementScope.STANDARD ? "660000.00" : "450000.00")),
                scope == SettlementScope.STANDARD ? new Money(new BigDecimal("180000.00")) : Money.ZERO,
                new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("settlement:test:" + UUID.randomUUID()), null, NOW);
        SettlementIntent withStatus = created.transitionTo(SettlementStatus.IN_PROCESS);
        return status == SettlementStatus.COMPLETED ? withStatus.transitionTo(SettlementStatus.COMPLETED)
                .withExternalReference("st-1") : withStatus;
    }
}
