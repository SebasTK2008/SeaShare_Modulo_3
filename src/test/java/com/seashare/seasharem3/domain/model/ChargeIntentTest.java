package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChargeIntentTest {

    private static ChargeIntent create() {
        return ChargeIntent.create(UUID.randomUUID(), new ReservationId(UUID.randomUUID()),
                new IdempotencyKey("reservation|status|2026-10-01T15:04:05Z"),
                new Money(new BigDecimal("990000.00")),
                new PaymentMethodReference("token-123", "visa", "4242"),
                Instant.parse("2026-09-20T15:04:05Z"));
    }

    @Test
    void createsInPendingSend() {
        ChargeIntent intent = create();

        assertThat(intent.status()).isEqualTo(ChargeIntentStatus.PENDING_SEND);
        assertThat(intent.isTerminal()).isFalse();
        assertThat(intent.externalReference()).isNull();
        assertThat(intent.statusDetail()).isNull();
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatExceptionOfType(DomainException.class).isThrownBy(() -> ChargeIntent.create(UUID.randomUUID(),
                new ReservationId(UUID.randomUUID()), new IdempotencyKey("k"), Money.ZERO,
                new PaymentMethodReference("tok", null, null), Instant.now()));
    }

    @Test
    void allowsValidTransitionsAndIsIdempotentWithTheSameStatus() {
        ChargeIntent pending = create();

        ChargeIntent inProcess = pending.transitionTo(ChargeIntentStatus.IN_PROCESS);
        ChargeIntent captured = inProcess.transitionTo(ChargeIntentStatus.CAPTURED);

        assertThat(inProcess.status()).isEqualTo(ChargeIntentStatus.IN_PROCESS);
        assertThat(captured.status()).isEqualTo(ChargeIntentStatus.CAPTURED);
        assertThat(captured.isTerminal()).isTrue();
        assertThat(captured.transitionTo(ChargeIntentStatus.CAPTURED)).isSameAs(captured);
    }

    @Test
    void allowsCommunicationFailureAndItsReconciliation() {
        ChargeIntent pending = create();

        ChargeIntent failure = pending.transitionTo(ChargeIntentStatus.COMMUNICATION_ERROR);
        ChargeIntent captured = failure.transitionTo(ChargeIntentStatus.CAPTURED);

        assertThat(failure.status()).isEqualTo(ChargeIntentStatus.COMMUNICATION_ERROR);
        assertThat(failure.isTerminal()).isFalse();
        assertThat(captured.status()).isEqualTo(ChargeIntentStatus.CAPTURED);
    }

    @Test
    void allowsDirectCaptureFromPendingSendAndRejectsTransitionsFromTerminalStates() {
        ChargeIntent pending = create();

        ChargeIntent captured = pending.transitionTo(ChargeIntentStatus.CAPTURED);
        assertThat(captured.status()).isEqualTo(ChargeIntentStatus.CAPTURED);
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> captured.transitionTo(ChargeIntentStatus.REJECTED));
        assertThat(captured.canTransitionTo(ChargeIntentStatus.CAPTURED)).isTrue();
        assertThat(captured.canTransitionTo(ChargeIntentStatus.REJECTED)).isFalse();
        assertThat(captured.canTransitionTo(ChargeIntentStatus.IN_PROCESS)).isFalse();
    }

    @Test
    void reachesAnyNonTerminalStateFromPendingSend() {
        ChargeIntent pending = create();

        assertThat(pending.canTransitionTo(ChargeIntentStatus.IN_PROCESS)).isTrue();
        assertThat(pending.canTransitionTo(ChargeIntentStatus.CAPTURED)).isTrue();
        assertThat(pending.canTransitionTo(ChargeIntentStatus.REJECTED)).isTrue();
        assertThat(pending.canTransitionTo(ChargeIntentStatus.CANCELLED)).isTrue();
        assertThat(pending.canTransitionTo(ChargeIntentStatus.EXPIRED)).isTrue();
        assertThat(pending.canTransitionTo(ChargeIntentStatus.COMMUNICATION_ERROR)).isTrue();
    }

    @Test
    void comparesByValueAndKeepsItsIdentity() {
        ChargeIntent intent = create();
        ChargeIntent same = ChargeIntent.create(intent.id(), intent.reservationId(), intent.idempotencyKey(),
                intent.amount(), intent.paymentMethod(), intent.createdAt());

        assertThat(intent).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(intent).isNotEqualTo(intent.withStatusDetail("other"));
        assertThat(intent.toString()).contains(intent.id().toString());
    }

    @Test
    void restoresAnIntentAlreadyPersisted() {
        ChargeIntent intent = create().transitionTo(ChargeIntentStatus.IN_PROCESS).withExternalReference("1234567890");

        ChargeIntent restored = ChargeIntent.restore(intent.id(), intent.reservationId(), intent.idempotencyKey(),
                intent.status(), intent.amount(), intent.paymentMethod(), intent.externalReference(),
                intent.statusDetail(), intent.createdAt());

        assertThat(restored).isEqualTo(intent);
    }

    @Test
    void associatesExternalReferenceOnlyOnce() {
        ChargeIntent intent = create();

        ChargeIntent withReference = intent.withExternalReference("1234567890");

        assertThat(withReference.externalReference()).isEqualTo("1234567890");
        assertThat(withReference.withExternalReference("1234567890")).isEqualTo(withReference);
        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> withReference.withExternalReference("9999999999"));
    }

    @Test
    void attachesStatusDetailWithoutChangingStatus() {
        ChargeIntent intent = create().transitionTo(ChargeIntentStatus.IN_PROCESS);

        ChargeIntent detailed = intent.withStatusDetail("pending_review");

        assertThat(detailed.status()).isEqualTo(ChargeIntentStatus.IN_PROCESS);
        assertThat(detailed.statusDetail()).isEqualTo("pending_review");
    }
}
