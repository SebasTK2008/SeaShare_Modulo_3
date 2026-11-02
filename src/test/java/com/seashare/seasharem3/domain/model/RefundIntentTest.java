package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de la intención de reembolso (UC09 T002): estado inicial, transiciones permitidas y
 * terminales, inmutabilidad de los datos del intento y unicidad de la referencia externa.
 */
class RefundIntentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-01T10:00:00Z");
    private static final ReservationId RESERVATION = new ReservationId(UUID.randomUUID());
    private static final Money AMOUNT = new Money(new BigDecimal("990000.00"));

    private static RefundIntent intent() {
        return RefundIntent.create(UUID.randomUUID(), RESERVATION, RefundTrigger.CANCELLED_FLEXIBLE,
                RefundScope.FULL, AMOUNT, UUID.randomUUID(),
                new IdempotencyKey("refund:CANCELLED_FLEXIBLE:op-1"), null, CREATED_AT);
    }

    @Test
    void startsPendingSendWithTheIntentData() {
        RefundIntent intent = intent();

        assertThat(intent.status()).isEqualTo(RefundIntentStatus.PENDING_SEND);
        assertThat(intent.amount()).isEqualTo(AMOUNT);
        assertThat(intent.reservationId()).isEqualTo(RESERVATION);
        assertThat(intent.createdAt()).isEqualTo(CREATED_AT);
        assertThat(intent.externalReference()).isNull();
    }

    @Test
    void allowsTheExpectedTransitionsFromPendingSend() {
        RefundIntent intent = intent();

        assertThat(intent.canTransitionTo(RefundIntentStatus.IN_PROCESS)).isTrue();
        assertThat(intent.canTransitionTo(RefundIntentStatus.COMPLETED)).isTrue();
        assertThat(intent.canTransitionTo(RefundIntentStatus.REJECTED)).isTrue();
        assertThat(intent.canTransitionTo(RefundIntentStatus.COMMUNICATION_ERROR)).isTrue();
    }

    @Test
    void keepsTheIntentDataWhenTheStatusChanges() {
        RefundIntent completed = intent().transitionTo(RefundIntentStatus.COMPLETED);

        assertThat(completed.status()).isEqualTo(RefundIntentStatus.COMPLETED);
        assertThat(completed.amount()).isEqualTo(AMOUNT);
        assertThat(completed.idempotencyKey().value()).isEqualTo("refund:CANCELLED_FLEXIBLE:op-1");
    }

    @Test
    void treatsTerminalStatesAsFinal() {
        RefundIntent completed = intent().transitionTo(RefundIntentStatus.COMPLETED);

        assertThat(completed.isTerminal()).isTrue();
        assertThat(completed.canTransitionTo(RefundIntentStatus.IN_PROCESS)).isFalse();
        assertThatThrownBy(() -> completed.transitionTo(RefundIntentStatus.IN_PROCESS))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void repeatingTheCurrentStatusIsIdempotent() {
        RefundIntent intent = intent();

        assertThat(intent.transitionTo(RefundIntentStatus.PENDING_SEND)).isSameAs(intent);
    }

    @Test
    void rejectsAConflictingExternalReference() {
        RefundIntent withReference = intent().withExternalReference("refund-1");

        assertThat(withReference.externalReference()).isEqualTo("refund-1");
        assertThat(withReference.withExternalReference("refund-1").externalReference()).isEqualTo("refund-1");
        assertThatThrownBy(() -> withReference.withExternalReference("refund-2"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsANonPositiveAmount() {
        assertThatThrownBy(() -> RefundIntent.create(UUID.randomUUID(), RESERVATION,
                RefundTrigger.CANCELLED_FLEXIBLE, RefundScope.FULL, new Money(BigDecimal.ZERO),
                UUID.randomUUID(), new IdempotencyKey("refund:key"), null, CREATED_AT))
                .isInstanceOf(DomainException.class);
    }
}
