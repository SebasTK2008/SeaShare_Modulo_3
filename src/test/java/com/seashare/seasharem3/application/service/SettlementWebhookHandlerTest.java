package com.seashare.seasharem3.application.service;

import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.port.in.RegisterSettlementResultUseCase;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas del handler del webhook de liquidacion. */
class SettlementWebhookHandlerTest {

    private static final ReservationInformation RESERVATION = ChargeFixtures.calculatedReservation();
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final SettlementIntentRepository settlementIntentRepository = mock(SettlementIntentRepository.class);
    private final SettlementGatewayPort settlementGatewayPort = mock(SettlementGatewayPort.class);
    private final RegisterSettlementResultUseCase registerSettlementResult = mock(RegisterSettlementResultUseCase.class);
    private final InMemoryFailureRecorder failureRecorder = new InMemoryFailureRecorder();

    private SettlementWebhookHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SettlementWebhookHandler(settlementIntentRepository, settlementGatewayPort,
                registerSettlementResult, failureRecorder);
        SettlementIntent intent = SettlementIntent.create(UUID.randomUUID(), RESERVATION.reservationId(),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("660000.00")),
                new Money(new BigDecimal("180000.00")), new Money(new BigDecimal("30000.00")), UUID.randomUUID(),
                new IdempotencyKey("settlement:COMPLETED:op-1"), null, NOW).withExternalReference("st-1");
        when(settlementIntentRepository.findByExternalReference("st-1")).thenReturn(Optional.of(intent));
    }

    @Test
    void fetchesTheSettlementAndRegistersTheResult() {
        GatewaySettlementResult result = new GatewaySettlementResult("approved", "st-1", null,
                new Money(new BigDecimal("660000.00")));
        when(settlementGatewayPort.fetch("st-1")).thenReturn(Optional.of(result));

        handler.handle(new GatewayWebhookNotification("merchant_order", "merchant_order.updated", "st-1"));

        verify(registerSettlementResult).register(result);
    }

    @Test
    void unknownSettlementIsRecordedAsFailure() {
        handler.handle(new GatewayWebhookNotification("merchant_order", "merchant_order.updated", "unknown"));

        assertThat(failureRecorder.failures()).hasSize(1);
    }
}
