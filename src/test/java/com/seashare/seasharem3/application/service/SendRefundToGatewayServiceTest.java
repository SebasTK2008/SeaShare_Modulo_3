package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.RefundGatewayResult;
import com.seashare.seasharem3.application.port.in.RegisterRefundResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.RefundGatewayPort;
import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.RefundIntent;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas del envio del reembolso: reintento, rechazo de negocio y referencia externa. */
class SendRefundToGatewayServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final RefundIntentRepository refundIntentRepository = mock(RefundIntentRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final RefundGatewayPort refundGatewayPort = mock(RefundGatewayPort.class);
    private final RegisterRefundResultUseCase registerRefundResult = mock(RegisterRefundResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private SendRefundToGatewayService service;
    private RefundIntent intent;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        service = new SendRefundToGatewayService(refundIntentRepository, chargeIntentRepository, refundGatewayPort,
                registerRefundResult, failureRecorder);
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        intent = RefundIntent.create(UUID.randomUUID(), RESERVATION.reservationId(), RefundTrigger.CANCELLED_STRICT,
                RefundScope.DEPOSIT_ONLY, new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("refund:CANCELLED_STRICT:op-1"), null, NOW);
        when(refundIntentRepository.findByIdForUpdate(intent.id())).thenReturn(Optional.of(intent));
        when(refundIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
    }

    @Test
    void communicationFailureIsRetryableAndLeavesTheIntentInCommunicationError() {
        when(refundGatewayPort.send(any())).thenThrow(new GatewayCommunicationException("timeout"));

        assertThatThrownBy(() -> service.send(intent.id())).isInstanceOf(GatewayCommunicationException.class);

        var captor = org.mockito.ArgumentCaptor.forClass(RefundIntent.class);
        verify(refundIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RefundIntentStatus.COMMUNICATION_ERROR);
    }

    @Test
    void businessRejectionStopsTheRetries() {
        when(refundGatewayPort.send(any())).thenThrow(new GatewayBusinessRejectionException("invalid payment"));

        service.send(intent.id());

        var captor = org.mockito.ArgumentCaptor.forClass(RefundIntent.class);
        verify(refundIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RefundIntentStatus.REJECTED);
        verify(registerRefundResult, org.mockito.Mockito.never()).register(any());
    }

    @Test
    void successPersistsTheExternalReferenceBeforeRegisteringTheResult() {
        RefundGatewayResult result = new RefundGatewayResult("approved", "rf-1",
                new Money(new BigDecimal("30000.00")), null, null);
        when(refundGatewayPort.send(any())).thenReturn(result);

        service.send(intent.id());

        var captor = org.mockito.ArgumentCaptor.forClass(RefundIntent.class);
        verify(refundIntentRepository).save(captor.capture());
        assertThat(captor.getValue().externalReference()).isEqualTo("rf-1");
        verify(registerRefundResult).register(result);
    }
}
