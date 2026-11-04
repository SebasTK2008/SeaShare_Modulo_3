package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.domain.exception.GatewayResourceNotFoundException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas del handler del webhook {@code payment} de UC09. */
class RefundWebhookHandlerTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final String CHARGE_REFERENCE = "mp-payment-1";

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final RefundGatewayPort refundGatewayPort = mock(RefundGatewayPort.class);
    private final RegisterRefundResultUseCase registerRefundResult = mock(RegisterRefundResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private RefundWebhookHandler handler;
    private RefundIntent intent;

    @BeforeEach
    void setUp() {
        handler = new RefundWebhookHandler(refundIntentRepository, refundGatewayPort, registerRefundResult,
                failureRecorder);
        intent = RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.CANCELLED_STRICT,
                RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")), UUID.randomUUID(),
                new IdempotencyKey("refund:CANCELLED_STRICT:op-1"), null, NOW).withExternalReference("rf-1");
        when(refundIntentRepository.findByExternalReference("rf-1")).thenReturn(Optional.of(intent));
    }

    @Test
    void fetchesTheRefundsOfThePaymentAndRegistersTheLocalOnes() {
        RefundGatewayResult result = new RefundGatewayResult("approved", "rf-1",
                new Money(new BigDecimal("30000.00")), null, null);
        when(refundGatewayPort.fetchRefunds(CHARGE_REFERENCE)).thenReturn(List.of(result));

        handler.handle(new GatewayWebhookNotification("payment", "payment.updated", CHARGE_REFERENCE));

        verify(registerRefundResult).register(result);
        assertThat(failureRecorder.failures()).isEmpty();
    }

    @Test
    void recordsRefundsWithoutALocalIntentAsFailure() {
        RefundGatewayResult foreign = new RefundGatewayResult("approved", "rf-other",
                new Money(new BigDecimal("1000.00")), null, null);
        when(refundGatewayPort.fetchRefunds(CHARGE_REFERENCE)).thenReturn(List.of(foreign));

        handler.handle(new GatewayWebhookNotification("payment", "payment.updated", CHARGE_REFERENCE));

        verify(registerRefundResult, never()).register(org.mockito.ArgumentMatchers.any());
        assertThat(failureRecorder.failures()).hasSize(1);
    }

    @Test
    void propagatesAnUnknownPaymentSoTheListenerRecordsIt() {
        when(refundGatewayPort.fetchRefunds(CHARGE_REFERENCE))
                .thenThrow(new GatewayResourceNotFoundException("the payment does not exist"));

        assertThatThrownBy(() -> handler.handle(
                new GatewayWebhookNotification("payment", "payment.updated", CHARGE_REFERENCE)))
                .isInstanceOf(GatewayResourceNotFoundException.class);
    }

    @Test
    void recordsAMissingPaymentReferenceAsFailure() {
        handler.handle(new GatewayWebhookNotification("payment", "payment.updated", " "));

        assertThat(failureRecorder.failures()).hasSize(1);
        verify(refundGatewayPort, never()).fetchRefunds(org.mockito.ArgumentMatchers.any());
    }
}
