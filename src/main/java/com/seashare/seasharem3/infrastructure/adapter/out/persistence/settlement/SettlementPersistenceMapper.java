package com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement;

import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

/** Mapeo manual entre UC10 y sus entidades JPA (misma convencion que UC05/UC11). */
public final class SettlementPersistenceMapper {

    private SettlementPersistenceMapper() {
    }

    public static SettlementIntentJpaEntity toEntity(SettlementIntent intent) {
        return SettlementIntentJpaEntity.from(intent);
    }

    public static SettlementIntent toDomain(SettlementIntentJpaEntity entity) {
        return SettlementIntent.restore(entity.getId(), new ReservationId(entity.getReservationId()),
                SettlementTrigger.valueOf(entity.getTrigger()), SettlementScope.valueOf(entity.getScope()),
                new Money(entity.getAmount()), new Money(entity.getCommissionAmount()),
                new Money(entity.getDepositAmount()), entity.getChargeIntentId(),
                new IdempotencyKey(entity.getIdempotencyKey()), entity.statusValue(), entity.getStatusDetail(),
                entity.getExternalReference(), entity.getDisputeId(), entity.getCreatedAt());
    }

    public static SettlementRecordJpaEntity toEntity(SettlementRecord record) {
        return SettlementRecordJpaEntity.from(record);
    }

    public static SettlementRecord toDomain(SettlementRecordJpaEntity entity) {
        return SettlementRecord.restore(entity.getId(), entity.getSettlementIntentId(),
                new ReservationId(entity.getReservationId()), new OwnerId(entity.getOwnerId()),
                new BoatId(entity.getBoatId()), new Money(entity.getAmount()), entity.getDetail(),
                new Money(entity.getRentalGrossAmount()), new Money(entity.getInsuranceAmount()),
                new Money(entity.getDepositAmount()), entity.getExternalReference(), entity.getCreatedAt());
    }

    public static CommissionRecordJpaEntity toEntity(CommissionRecord record) {
        return CommissionRecordJpaEntity.from(record);
    }

    public static CommissionRecord toDomain(CommissionRecordJpaEntity entity) {
        return CommissionRecord.restore(entity.getId(), entity.getSettlementRecordId(),
                entity.getSettlementIntentId(), new ReservationId(entity.getReservationId()),
                new OwnerId(entity.getOwnerId()), new BoatId(entity.getBoatId()), new Money(entity.getAmount()),
                entity.getCreatedAt());
    }
}
