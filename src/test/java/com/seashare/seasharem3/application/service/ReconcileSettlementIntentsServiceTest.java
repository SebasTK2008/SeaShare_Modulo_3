package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de conciliacion interna de liquidaciones simuladas. */
class ReconcileSettlementIntentsServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String SIMULATED_DETAIL = "simulated internal settlement";

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final SettlementRecordRepository settlementRecordRepository = mock(SettlementRecordRepository.class);
    private final RegisterSettlementResultUseCase registerSettlementResult = mock(RegisterSettlementResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();
    private final SettlementCommandPublisher settlementCommandPublisher = mock(SettlementCommandPublisher.class);

    private ReconcileSettlementIntentsService service;

    @BeforeEach
    void setUp() {
        service = new ReconcileSettlementIntentsService(settlementIntentRepository, settlementRecordRepository,
                registerSettlementResult, failureRecorder, settlementCommandPublisher);
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void republishesAnIntentThatNeverReachedTheLedger() {
        SettlementIntent intent = pendingIntent();
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));

        service.reconcile();

        verify(settlementCommandPublisher).publish(intent.id());
        assertThat(failureRecorder.failures()).isEmpty();
        verify(settlementIntentRepository, never()).save(any(SettlementIntent.class));
        verify(registerSettlementResult, never()).register(any());
    }

    @Test
    void repairsAnIntentWithExternalReferenceByRegisteringTheSimulatedResult() {
        SettlementIntent intent = pendingIntent().withExternalReference("sim-settlement-op-1");
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));
        when(settlementRecordRepository.findBySettlementIntentId(intent.id())).thenReturn(Optional.empty());

        service.reconcile();

        var captor = ArgumentCaptor.forClass(GatewaySettlementResult.class);
        verify(registerSettlementResult).register(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("approved");
        assertThat(captor.getValue().externalId()).isEqualTo("sim-settlement-op-1");
        assertThat(captor.getValue().statusDetail()).isEqualTo(SIMULATED_DETAIL);
        assertThat(captor.getValue().amount().amount()).isEqualByComparingTo("660000.00");
        verify(settlementCommandPublisher, never()).publish(any());
    }

    @Test
    void skipsACompletedIntentThatAlreadyHasItsRecord() {
        SettlementIntent intent = completedIntent().withExternalReference("sim-settlement-op-1");
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));
        when(settlementRecordRepository.findBySettlementIntentId(intent.id()))
                .thenReturn(Optional.of(mock(SettlementRecord.class)));

        service.reconcile();

        verify(registerSettlementResult, never()).register(any());
        verify(settlementCommandPublisher, never()).publish(any());
        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void recordsFailureOnceWhenAnIntentWithoutReferenceIsNotPendingSend() {
        SettlementIntent intent = pendingIntent().transitionTo(SettlementStatus.IN_PROCESS);
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));

        service.reconcile();

        var captor = ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SettlementStatus.IN_PROCESS);
        assertThat(captor.getValue().statusDetail())
                .isEqualTo("The settlement was never confirmed by the internal ledger");
        assertThat(failureRecorder.failures()).hasSize(1);
        verify(settlementCommandPublisher, never()).publish(any());

        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(captor.getValue()));
        service.reconcile();

        assertThat(failureRecorder.failures()).hasSize(1);
        verify(settlementIntentRepository, org.mockito.Mockito.times(1)).save(any(SettlementIntent.class));
    }

    @Test
    void recordsAFailureForAnIntentWithoutExternalReferenceThatIsNotPendingSend() {
        SettlementIntent intent = pendingIntent().transitionTo(SettlementStatus.COMMUNICATION_ERROR);
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));

        service.reconcile();

        verify(settlementCommandPublisher, never()).publish(any());
        assertThat(failureRecorder.failures()).hasSize(1);
    }

    private SettlementIntent pendingIntent() {
        return SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), new Money(new BigDecimal("30000.00")), UUID.randomUUID(),
                "settlement:COMPLETED:op-1", new IdempotencyKey("settlement:COMPLETED:op-1"), null, NOW);
    }

    private SettlementIntent completedIntent() {
        return pendingIntent().transitionTo(SettlementStatus.IN_PROCESS)
                .transitionTo(SettlementStatus.COMPLETED);
    }
}
