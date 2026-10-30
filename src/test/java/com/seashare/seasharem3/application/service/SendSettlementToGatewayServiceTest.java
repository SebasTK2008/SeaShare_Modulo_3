package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.exception.GatewayBusinessRejectionException;
import com.seashare.seasharem3.domain.exception.GatewayCommunicationException;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas del envio de liquidaciones: reintento, rechazo, configuracion y referencia externa. */
class SendSettlementToGatewayServiceTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final long COLLECTOR_ID = 123456789L;

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final ChargeIntentRepository chargeIntentRepository = mock(ChargeIntentRepository.class);
    private final SettlementGatewayPort settlementGatewayPort = mock(SettlementGatewayPort.class);
    private final RegisterSettlementResultUseCase registerSettlementResult = mock(RegisterSettlementResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private SendSettlementToGatewayService service;
    private SettlementIntent intent;
    private ChargeIntent charge;

    @BeforeEach
    void setUp() {
        service = new SendSettlementToGatewayService(settlementIntentRepository, chargeIntentRepository,
                settlementGatewayPort, registerSettlementResult, failureRecorder, COLLECTOR_ID);
        charge = ChargeFixtures.capturedIntent(RESERVATION, "mp-payment-1");
        intent = SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), new Money(new BigDecimal("30000.00")), charge.id(),
                new IdempotencyKey("settlement:COMPLETED:op-1"), null, NOW);
        when(settlementIntentRepository.findByIdForUpdate(intent.id())).thenReturn(Optional.of(intent));
        when(settlementIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeIntentRepository.findById(charge.id())).thenReturn(Optional.of(charge));
    }

    @Test
    void communicationFailureIsRetryableAndLeavesTheIntentInCommunicationError() {
        when(settlementGatewayPort.send(any())).thenThrow(new GatewayCommunicationException("timeout"));

        assertThatThrownBy(() -> service.send(intent.id())).isInstanceOf(GatewayCommunicationException.class);

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SettlementStatus.COMMUNICATION_ERROR);
    }

    @Test
    void businessRejectionStopsTheRetries() {
        when(settlementGatewayPort.send(any())).thenThrow(new GatewayBusinessRejectionException("collector"));

        service.send(intent.id());

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SettlementStatus.REJECTED);
        verify(registerSettlementResult, never()).register(any());
    }

    @Test
    void successPersistsTheExternalReferenceBeforeRegisteringTheResult() {
        GatewaySettlementResult result = new GatewaySettlementResult("approved", "st-1", "accredited",
                new Money(new BigDecimal("660000.00")));
        when(settlementGatewayPort.send(any())).thenReturn(result);

        service.send(intent.id());

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().externalReference()).isEqualTo("st-1");
        verify(registerSettlementResult).register(result);
    }

    @Test
    void missingCollectorConfigurationIsRecordedAndRejected() {
        service = new SendSettlementToGatewayService(settlementIntentRepository, chargeIntentRepository,
                settlementGatewayPort, registerSettlementResult, failureRecorder, 0);

        service.send(intent.id());

        var captor = org.mockito.ArgumentCaptor.forClass(SettlementIntent.class);
        verify(settlementIntentRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SettlementStatus.REJECTED);
        assertThat(failureRecorder.failures()).hasSize(1);
        verify(settlementGatewayPort, never()).send(any());
    }
}
