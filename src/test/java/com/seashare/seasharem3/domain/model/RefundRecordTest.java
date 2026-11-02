package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
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
 * Pruebas del registro de reembolso (UC09 RF-006A/RF-007/RF-013): inmutabilidad, monto neto
 * liberado, copia de la disputa asociada y rechazo de un reembolso no confirmado.
 */
class RefundRecordTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-01T10:00:00Z");
    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final BoatId BOAT = new BoatId(UUID.randomUUID());
    private static final UUID DISPUTE = UUID.randomUUID();

    private static RefundIntent completedIntent(UUID disputeId) {
        return RefundIntent.create(UUID.randomUUID(), new ReservationId(UUID.randomUUID()),
                RefundTrigger.DISPUTE_REJECTED, RefundScope.DEPOSIT_ONLY,
                new Money(new BigDecimal("30000.00")), UUID.randomUUID(),
                new IdempotencyKey("refund:DISPUTE_REJECTED:op-1"), disputeId, CREATED_AT)
                .transitionTo(RefundIntentStatus.COMPLETED);
    }

    @Test
    void derivesTheReleasedAmountFromTheTransactionCost() {
        RefundRecord record = RefundRecord.create(UUID.randomUUID(), completedIntent(null), OWNER, BOAT,
                new Money(new BigDecimal("30000.00")), new Money(new BigDecimal("1500.00")),
                "Refund confirmed", "refund-1", CREATED_AT);

        assertThat(record.amount().amount()).isEqualByComparingTo("30000");
        assertThat(record.releasedAmount().amount()).isEqualByComparingTo("28500");
        assertThat(record.transactionCost().amount()).isEqualByComparingTo("1500");
    }

    @Test
    void theReleasedAmountMatchesTheAmountWhenThereIsNoCost() {
        RefundRecord record = RefundRecord.create(UUID.randomUUID(), completedIntent(null), OWNER, BOAT,
                new Money(new BigDecimal("30000.00")), null, "Refund confirmed", "refund-1", CREATED_AT);

        assertThat(record.releasedAmount().amount()).isEqualByComparingTo("30000");
    }

    @Test
    void keepsTheDisputeIdOfTheIntent() {
        RefundRecord record = RefundRecord.create(UUID.randomUUID(), completedIntent(DISPUTE), OWNER, BOAT,
                new Money(new BigDecimal("30000.00")), null, "Refund confirmed", "refund-1", CREATED_AT);

        assertThat(record.disputeId()).isEqualTo(DISPUTE);
    }

    @Test
    void rejectsARecordForANonCompletedRefund() {
        RefundIntent pending = RefundIntent.create(UUID.randomUUID(), new ReservationId(UUID.randomUUID()),
                RefundTrigger.CANCELLED_FLEXIBLE, RefundScope.FULL, new Money(new BigDecimal("30000.00")),
                UUID.randomUUID(), new IdempotencyKey("refund:CANCELLED_FLEXIBLE:op-2"), null, CREATED_AT);

        assertThatThrownBy(() -> RefundRecord.create(UUID.randomUUID(), pending, OWNER, BOAT,
                new Money(new BigDecimal("30000.00")), null, "Refund", "refund-1", CREATED_AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsACostGreaterThanTheRefundedAmount() {
        assertThatThrownBy(() -> RefundRecord.create(UUID.randomUUID(), completedIntent(null), OWNER, BOAT,
                new Money(new BigDecimal("30000.00")), new Money(new BigDecimal("30001.00")),
                "Refund", "refund-1", CREATED_AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void hasNoSetters() {
        assertThat(RefundRecord.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }
}
