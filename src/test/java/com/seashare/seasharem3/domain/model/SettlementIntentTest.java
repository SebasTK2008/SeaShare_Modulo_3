package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementCapability;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de la intención de dispersión (UC10 T007): estado inicial, capacidad de la pasarela
 * utilizada (RF-009), transiciones y terminales.
 */
class SettlementIntentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-01T10:00:00Z");
    private static final ReservationId RESERVATION = new ReservationId(UUID.randomUUID());

    private static SettlementIntent intent() {
        return SettlementIntent.create(UUID.randomUUID(), RESERVATION, SettlementTrigger.COMPLETED,
                SettlementScope.STANDARD, new Money(new BigDecimal("870000.00")),
                new Money(new BigDecimal("130000.00")), new Money(BigDecimal.ZERO), UUID.randomUUID(),
                new IdempotencyKey("settlement:COMPLETED:op-1"), null, CREATED_AT);
    }

    @Test
    void startsPendingSendWithTheCalculatedAmounts() {
        SettlementIntent intent = intent();

        assertThat(intent.status()).isEqualTo(SettlementStatus.PENDING_SEND);
        assertThat(intent.amount().amount()).isEqualByComparingTo("870000");
        assertThat(intent.commissionAmount().amount()).isEqualByComparingTo("130000");
    }

    @Test
    void recordsTheGatewayCapabilityUsed() {
        SettlementIntent intent = intent();

        assertThat(intent.capability()).isEqualTo(SettlementCapability.ADVANCED_PAYMENTS_DISBURSEMENT);
    }

    @Test
    void keepsTheGatewayCapabilityAcrossTransitions() {
        SettlementIntent completed = intent()
                .withExternalReference("disbursement-1")
                .transitionTo(SettlementStatus.COMPLETED);

        assertThat(completed.capability()).isEqualTo(SettlementCapability.ADVANCED_PAYMENTS_DISBURSEMENT);
        assertThat(completed.externalReference()).isEqualTo("disbursement-1");
    }

    @Test
    void allowsAnExplicitCapability() {
        SettlementIntent intent = SettlementIntent.create(UUID.randomUUID(), RESERVATION,
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("100.00")),
                Money.ZERO, Money.ZERO, UUID.randomUUID(),
                new IdempotencyKey("settlement:COMPLETED:op-2"), null,
                SettlementCapability.STANDARD_SETTLEMENT, CREATED_AT);

        assertThat(intent.capability()).isEqualTo(SettlementCapability.STANDARD_SETTLEMENT);
    }

    @Test
    void treatsTerminalStatesAsFinal() {
        SettlementIntent completed = intent().transitionTo(SettlementStatus.COMPLETED);

        assertThat(completed.isTerminal()).isTrue();
        assertThatThrownBy(() -> completed.transitionTo(SettlementStatus.IN_PROCESS))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsAConflictingExternalReference() {
        SettlementIntent withReference = intent().withExternalReference("disbursement-1");

        assertThatThrownBy(() -> withReference.withExternalReference("disbursement-2"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsANonPositiveAmount() {
        assertThatThrownBy(() -> SettlementIntent.create(UUID.randomUUID(), RESERVATION,
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, Money.ZERO, Money.ZERO, Money.ZERO,
                UUID.randomUUID(), new IdempotencyKey("settlement:key"), null, CREATED_AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void hasNoSetters() {
        assertThat(SettlementIntent.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }
}
