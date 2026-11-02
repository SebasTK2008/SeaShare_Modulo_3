package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.application.port.out.SettlementCommandPublisher;
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

/** Pruebas de conciliacion de liquidaciones. */
class ReconcileSettlementIntentsServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final SettlementRecordRepository settlementRecordRepository = mock(SettlementRecordRepository.class);
    private final SettlementGatewayPort settlementGatewayPort = mock(SettlementGatewayPort.class);
    private final RegisterSettlementResultUseCase registerSettlementResult = mock(RegisterSettlementResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();
    private final SettlementCommandPublisher settlementCommandPublisher = mock(SettlementCommandPublisher.class);

    private ReconcileSettlementIntentsService service;

    @BeforeEach
    void setUp() {
        service = new ReconcileSettlementIntentsService(settlementIntentRepository, settlementRecordRepository,
                settlementGatewayPort, registerSettlementResult, failureRecorder, settlementCommandPublisher);
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void republishesAnIntentThatNeverReachedTheGateway() {
        SettlementIntent intent = pendingIntent();
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));

        service.reconcile();

        verify(settlementCommandPublisher).publish(intent.id());
        assertThat(failureRecorder.failures()).isEmpty();
        verify(settlementIntentRepository, never()).save(any(SettlementIntent.class));
        verify(settlementGatewayPort, never()).fetch(any());
    }

    @Test
    void fetchesAndRegistersASettlementWithExternalReference() {
        SettlementIntent intent = pendingIntent().withExternalReference("st-1");
        GatewaySettlementResult result = new GatewaySettlementResult("approved", "st-1", null,
                new Money(new BigDecimal("660000.00")));
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));
        when(settlementGatewayPort.fetch("st-1")).thenReturn(Optional.of(result));

        service.reconcile();

        verify(registerSettlementResult).register(result);
    }

    @Test
    void skipsACompletedIntentThatAlreadyHasItsRecord() {
        SettlementIntent intent = pendingIntent().transitionTo(SettlementStatus.IN_PROCESS)
                .transitionTo(SettlementStatus.COMPLETED).withExternalReference("st-1");
        when(settlementIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));
        when(settlementRecordRepository.findBySettlementIntentId(intent.id()))
                .thenReturn(Optional.of(mock(SettlementRecord.class)));

        service.reconcile();

        verify(settlementGatewayPort, never()).fetch(any());
    }

    private SettlementIntent pendingIntent() {
        return SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), new Money(new BigDecimal("30000.00")), UUID.randomUUID(),
                new IdempotencyKey("settlement:COMPLETED:op-1"), null, NOW);
    }
}
