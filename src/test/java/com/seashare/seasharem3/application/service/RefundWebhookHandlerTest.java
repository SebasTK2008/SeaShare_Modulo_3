package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.support.ChargeFixtures;
import com.seashare.seasharem3.support.InMemoryFailureRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas del handler del webhook de reembolso. */
class RefundWebhookHandlerTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final RefundGatewayPort refundGatewayPort = mock(RefundGatewayPort.class);
    private final RegisterRefundResultUseCase registerRefundResult = mock(RegisterRefundResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private RefundWebhookHandler handler;
    private ChargeIntent charge;
    private RefundIntent intent;

    @BeforeEach
    void setUp() {
        handler = new RefundWebhookHandler(refundIntentRepository, chargeIntentRepository, refundGatewayPort,
                registerRefundResult, failureRecorder);
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        intent = RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.CANCELLED_STRICT,
                RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("refund:CANCELLED_STRICT:op-1"), null, NOW).withExternalReference("rf-1");
        when(refundIntentRepository.findByExternalReference("rf-1")).thenReturn(Optional.of(intent));
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
    }

    @Test
    void fetchesTheRefundAndRegistersTheResult() {
        RefundGatewayResult result = new RefundGatewayResult("approved", "rf-1",
                new Money(new BigDecimal("30000.00")), null, null);
        when(refundGatewayPort.fetchRefund("mp-payment-1", "rf-1")).thenReturn(Optional.of(result));

        handler.handle(new GatewayWebhookNotification("refund", "refund.updated", "rf-1"));

        verify(registerRefundResult).register(result);
    }

    @Test
    void unknownRefundIsRecordedAsFailure() {
        handler.handle(new GatewayWebhookNotification("refund", "refund.updated", "unknown"));

        assertThat(failureRecorder.failures()).hasSize(1);
    }
}
