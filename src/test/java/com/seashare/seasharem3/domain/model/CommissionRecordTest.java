package com.seashare.seasharem3.domain.model;

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

/**
 * Pruebas del registro de comisión (UC10 RF-014/CE-006): se crea a partir del registro de
 * dispersión y conserva la trazabilidad de la intención y del registro que lo originan.
 */
class CommissionRecordTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-01T10:00:00Z");

    private static SettlementRecord settlementRecord() {
        OwnerId owner = new OwnerId(UUID.randomUUID());
        SettlementIntent completed = SettlementIntent.create(UUID.randomUUID(),
                new ReservationId(UUID.randomUUID()), SettlementTrigger.COMPLETED, SettlementScope.STANDARD,
                new Money(new BigDecimal("870000.00")), new Money(new BigDecimal("130000.00")),
                new Money(BigDecimal.ZERO), UUID.randomUUID(),
                "settlement:COMPLETED:op-1", new IdempotencyKey("settlement:COMPLETED:op-1"), null, CREATED_AT)
                .transitionTo(SettlementStatus.COMPLETED);
        return SettlementRecord.create(UUID.randomUUID(), completed, owner, new BoatId(UUID.randomUUID()),
                completed.amount(), "Settlement confirmed", new Money(new BigDecimal("1000000.00")),
                new Money(new BigDecimal("60000.00")), Money.ZERO, "disbursement-1", CREATED_AT);
    }

    @Test
    void copiesTheTraceabilityFromTheSettlementRecord() {
        SettlementRecord settlement = settlementRecord();

        CommissionRecord commission = CommissionRecord.create(UUID.randomUUID(), settlement,
                new Money(new BigDecimal("130000.00")), CREATED_AT);

        assertThat(commission.settlementRecordId()).isEqualTo(settlement.id());
        assertThat(commission.settlementIntentId()).isEqualTo(settlement.settlementIntentId());
        assertThat(commission.reservationId()).isEqualTo(settlement.reservationId());
        assertThat(commission.ownerId()).isEqualTo(settlement.ownerId());
        assertThat(commission.boatId()).isEqualTo(settlement.boatId());
        assertThat(commission.amount().amount()).isEqualByComparingTo("130000");
    }

    @Test
    void hasNoSetters() {
        assertThat(CommissionRecord.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set")
                        && Character.isUpperCase(method.getName().charAt(3)));
    }
}
