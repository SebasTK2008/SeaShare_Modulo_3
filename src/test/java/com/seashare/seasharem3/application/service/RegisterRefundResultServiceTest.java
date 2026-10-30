package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
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

/** Pruebas del registro del resultado de reembolso y del monto neto liberado. */
class RegisterRefundResultServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final RefundRecordRepository refundRecordRepository = mock(RefundRecordRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final ChargeRecordQueryPort chargeRecordQueryPort = mock(ChargeRecordQueryPort.class);
    private final ReservationInformationRepository reservationInformationRepository = mock(
            ReservationInformationRepository.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private RegisterRefundResultService service;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        service = new RegisterRefundResultService(refundIntentRepository, refundRecordRepository,
                chargeIntentRepository, chargeRecordQueryPort, reservationInformationRepository, failureRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        ChargeRecord chargeRecord = ChargeRecord.create(UUID.randomUUID(), charge, RESERVATION, NOW);
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
        when(chargeRecordQueryPort.findByChargeIntentId(charge.id())).thenReturn(Optional.of(chargeRecord));
        when(refundIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refundRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completedRefundCreatesOneRecordWithTheNetReleasedAmount() {
        RefundIntent intent = completedIntent("rf-1");
        when(refundIntentRepository.findByExternalReference("rf-1")).thenReturn(Optional.of(intent));
        when(refundRecordRepository.findByRefundIntentId(intent.id())).thenReturn(Optional.empty());

        service.register(new RefundGatewayResult("approved", "rf-1", new Money(new BigDecimal("30000.00")),
                "accredited", new Money(new BigDecimal("1000.00"))));

        var captor = org.mockito.ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordRepository).save(captor.capture());
        assertThat(captor.getValue().amount().amount()).isEqualByComparingTo("30000.00");
        assertThat(captor.getValue().releasedAmount().amount()).isEqualByComparingTo("29000.00");
        assertThat(captor.getValue().transactionCost().amount()).isEqualByComparingTo("1000.00");
        assertThat(captor.getValue().refundIntentId()).isEqualTo(intent.id());
    }

    @Test
    void nonCompletedResultOnlyUpdatesTheIntent() {
        RefundIntent intent = pendingIntent();
        when(refundIntentRepository.findByExternalReference("rf-2")).thenReturn(Optional.of(intent));

        service.register(new RefundGatewayResult("rejected", "rf-2", null, "rejected_by_issuer", null));

        verify(refundRecordRepository, never()).save(any());
        verify(refundIntentRepository).save(any(RefundIntent.class));
    }

    @Test
    void repeatedCompletedResultDoesNotCreateASecondRecord() {
        RefundIntent intent = completedIntent("rf-1");
        when(refundIntentRepository.findByExternalReference("rf-1")).thenReturn(Optional.of(intent));
        when(refundRecordRepository.findByRefundIntentId(intent.id()))
                .thenReturn(Optional.of(mock(RefundRecord.class)));

        service.register(new RefundGatewayResult("approved", "rf-1", new Money(new BigDecimal("30000.00")),
                null, null));

        verify(refundRecordRepository, never()).save(any());
    }

    @Test
    void unknownResultIsRecordedAsFailureWithoutTouchingTheRecord() {
        when(refundIntentRepository.findByExternalReference("rf-3")).thenReturn(Optional.empty());

        service.register(new RefundGatewayResult("approved", "rf-3", null, null, null));

        verify(refundRecordRepository, never()).save(any());
        assertThat(failureRecorder.failures()).extracting(InMemoryFailureRecorder.RecordedFailure::reason)
                .containsExactly("REFUND_INTENT_NOT_FOUND");
    }

    private RefundIntent completedIntent(String externalReference) {
        return pendingIntent().transitionTo(RefundIntentStatus.IN_PROCESS)
                .transitionTo(RefundIntentStatus.COMPLETED).withExternalReference(externalReference);
    }

    private RefundIntent pendingIntent() {
        return RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.DISPUTE_REJECTED,
                RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("refund:DISPUTE_REJECTED:op-1"), UUID.randomUUID(), NOW);
    }
}
