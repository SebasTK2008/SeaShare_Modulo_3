package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
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
 * Pruebas del registro de dispersión (UC10 RF-009A/RF-010/RF-016): solo se crea con la liquidación
 * confirmada, conserva el desglose aplicado y la referencia a la intención que lo originó.
 */
class SettlementRecordTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-01T10:00:00Z");
    private static final OwnerId OWNER = new OwnerId(UUID.randomUUID());
    private static final BoatId BOAT = new BoatId(UUID.randomUUID());

    private static SettlementIntent intent() {
        return SettlementIntent.create(UUID.randomUUID(), new ReservationId(UUID.randomUUID()),
                SettlementTrigger.COMPLETED, SettlementScope.STANDARD, new Money(new BigDecimal("870000.00")),
                new Money(new BigDecimal("130000.00")), new Money(BigDecimal.ZERO), UUID.randomUUID(),
                new IdempotencyKey("settlement:COMPLETED:op-1"), null, CREATED_AT);
    }

    @Test
    void keepsTheBreakdownAndTheOriginatingIntent() {
        SettlementIntent completed = intent().transitionTo(SettlementStatus.COMPLETED);

        SettlementRecord record = SettlementRecord.create(UUID.randomUUID(), completed, OWNER, BOAT,
                completed.amount(), "Settlement confirmed", new Money(new BigDecimal("1000000.00")),
                new Money(new BigDecimal("60000.00")), Money.ZERO, "disbursement-1", CREATED_AT);

        assertThat(record.settlementIntentId()).isEqualTo(completed.id());
        assertThat(record.rentalGrossAmount().amount()).isEqualByComparingTo("1000000");
        assertThat(record.insuranceAmount().amount()).isEqualByComparingTo("60000");
        assertThat(record.amount().amount()).isEqualByComparingTo("870000");
    }

    @Test
    void rejectsARecordForANonCompletedSettlement() {
        SettlementIntent pending = intent();

        assertThatThrownBy(() -> SettlementRecord.create(UUID.randomUUID(), pending, OWNER, BOAT,
                pending.amount(), "Settlement", Money.ZERO, Money.ZERO, Money.ZERO, "disbursement-1", CREATED_AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsANegativeBreakdownComponent() {
        SettlementIntent completed = intent().transitionTo(SettlementStatus.COMPLETED);

        assertThatThrownBy(() -> SettlementRecord.create(UUID.randomUUID(), completed, OWNER, BOAT,
                completed.amount(), "Settlement", new Money(new BigDecimal("-1")), Money.ZERO, Money.ZERO,
                "disbursement-1", CREATED_AT))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void hasNoSetters() {
        assertThat(SettlementRecord.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")
                        && Character.isUpperCase(method.getName().charAt(3)));
    }
}
