package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.OpenDepositTrackingCommand;
import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
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

/** Pruebas del UC08 minimo: apertura, idempotencia y conflicto de desenlace unico. */
class OpenDepositTrackingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final ReservationId RESERVATION = new ReservationId(UUID.randomUUID());

    private final DepositDispositionRepository repository = mock(DepositDispositionRepository.class);
    private final ReservationLockPort reservationLock = mock(ReservationLockPort.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();
    private OpenDepositTrackingService service;

    @BeforeEach
    void setUp() {
        service = new OpenDepositTrackingService(repository, reservationLock, failureRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsTheRowWhenItDoesNotExist() {
        when(repository.findByReservationId(RESERVATION)).thenReturn(Optional.empty());

        var result = service.open(command(DepositDispositionStatus.HELD));

        assertThat(result.created()).isTrue();
        assertThat(result.status()).isEqualTo(DepositDispositionStatus.HELD);
        verify(repository).save(any(DepositDisposition.class));
    }

    @Test
    void createsTheRowInHeldAndThenAppliesTheOutcome() {
        when(repository.findByReservationId(RESERVATION)).thenReturn(Optional.empty());
        when(repository.compareAndSet(any(), any(), any())).thenReturn(true);

        var result = service.open(command(DepositDispositionStatus.REFUND_REQUESTED));

        assertThat(result.created()).isTrue();
        assertThat(result.status()).isEqualTo(DepositDispositionStatus.REFUND_REQUESTED);
        verify(repository).save(any(DepositDisposition.class));
        verify(repository).compareAndSet(any(), any(), any());
    }

    @Test
    void isIdempotentWhenTheTargetIsAlreadyApplied() {
        when(repository.findByReservationId(RESERVATION))
                .thenReturn(Optional.of(disposition(DepositDispositionStatus.REFUND_REQUESTED)));

        var result = service.open(command(DepositDispositionStatus.REFUND_REQUESTED));

        assertThat(result.created()).isFalse();
        assertThat(result.status()).isEqualTo(DepositDispositionStatus.REFUND_REQUESTED);
        verify(repository, never()).compareAndSet(any(), any(), any());
    }

    @Test
    void appliesTheSingleTransitionFromHeld() {
        when(repository.findByReservationId(RESERVATION))
                .thenReturn(Optional.of(disposition(DepositDispositionStatus.HELD)));
        when(repository.compareAndSet(any(), any(), any())).thenReturn(true);

        var result = service.open(command(DepositDispositionStatus.REFUND_REQUESTED));

        assertThat(result.status()).isEqualTo(DepositDispositionStatus.REFUND_REQUESTED);
        verify(repository).compareAndSet(any(), any(), any());
    }

    @Test
    void rejectsAConflictingOutcomeAndRecordsTheInconsistency() {
        when(repository.findByReservationId(RESERVATION))
                .thenReturn(Optional.of(disposition(DepositDispositionStatus.SETTLEMENT_REQUESTED)));

        var result = service.open(command(DepositDispositionStatus.REFUND_REQUESTED));

        assertThat(result.status()).isEqualTo(DepositDispositionStatus.SETTLEMENT_REQUESTED);
        assertThat(failureRecorder.failures()).hasSize(1);
        verify(repository, never()).compareAndSet(any(), any(), any());
    }

    private static OpenDepositTrackingCommand command(DepositDispositionStatus target) {
        return new OpenDepositTrackingCommand(RESERVATION, "op-1", target, null, null, NOW);
    }

    private static DepositDisposition disposition(DepositDispositionStatus status) {
        return DepositDisposition.create(RESERVATION, NOW, null, null, status);
    }
}
