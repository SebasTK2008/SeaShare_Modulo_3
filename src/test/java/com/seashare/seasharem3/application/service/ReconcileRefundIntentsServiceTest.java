package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
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

/** Pruebas de conciliacion de reembolsos: consulta, fallo registrado y registro ya auditado. */
class ReconcileRefundIntentsServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final RefundRecordRepository refundRecordRepository = mock(RefundRecordRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final RefundGatewayPort refundGatewayPort = mock(RefundGatewayPort.class);
    private final RegisterRefundResultUseCase registerRefundResult = mock(RegisterRefundResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private ReconcileRefundIntentsService service;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        service = new ReconcileRefundIntentsService(refundIntentRepository, refundRecordRepository,
                chargeIntentRepository, refundGatewayPort, registerRefundResult, failureRecorder);
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
        when(refundIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsAFailureWhenTheIntentNeverReachedTheGateway() {
        RefundIntent intent = pendingIntent();
        when(refundIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));

        service.reconcile();

        verify(refundIntentRepository).save(any(RefundIntent.class));
        assertThat(failureRecorder.failures()).hasSize(1);
        verify(refundGatewayPort, never()).fetchRefund(any(), any());
    }

    @Test
    void fetchesAndRegistersARefundWithExternalReference() {
        RefundIntent intent = pendingIntent().withExternalReference("rf-1");
        RefundGatewayResult result = new RefundGatewayResult("approved", "rf-1",
                new Money(new BigDecimal("30000.00")), null, null);
        when(refundIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));
        when(refundGatewayPort.fetchRefund("mp-payment-1", "rf-1")).thenReturn(Optional.of(result));

        service.reconcile();

        verify(registerRefundResult).register(result);
    }

    @Test
    void skipsACompletedIntentThatAlreadyHasItsRecord() {
        RefundIntent intent = pendingIntent().transitionTo(RefundIntentStatus.IN_PROCESS)
                .transitionTo(RefundIntentStatus.COMPLETED).withExternalReference("rf-1");
        when(refundIntentRepository.findByStatusIn(any())).thenReturn(List.of(intent));
        when(refundRecordRepository.findByRefundIntentId(intent.id())).thenReturn(Optional.of(mock(RefundRecord.class)));

        service.reconcile();

        verify(refundGatewayPort, never()).fetchRefund(any(), any());
    }

    private RefundIntent pendingIntent() {
        return RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.DISPUTE_REJECTED,
                RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("refund:DISPUTE_REJECTED:op-1"), UUID.randomUUID(), NOW);
    }
}
