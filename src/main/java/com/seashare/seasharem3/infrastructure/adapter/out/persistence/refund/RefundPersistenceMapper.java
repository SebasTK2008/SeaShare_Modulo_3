package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.RefundScope;
import com.seashare.seasharem3.domain.valueobject.RefundTrigger;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

/** Mapeo manual entre UC09 y sus entidades JPA (misma convencion que UC05/UC11). */
public final class RefundPersistenceMapper {

    private RefundPersistenceMapper() {
    }

    public static RefundIntentJpaEntity toEntity(RefundIntent intent) {
        return RefundIntentJpaEntity.from(intent);
    }

    public static RefundIntent toDomain(RefundIntentJpaEntity entity) {
        return RefundIntent.restore(entity.getId(), new ReservationId(entity.getReservationId()),
                RefundTrigger.valueOf(entity.getTrigger()), RefundScope.valueOf(entity.getScope()),
                new Money(entity.getAmount()), entity.getChargeIntentId(),
                new IdempotencyKey(entity.getIdempotencyKey()), entity.statusValue(), entity.getStatusDetail(),
                entity.getDisputeId(), entity.getExternalReference(), entity.getCreatedAt());
    }

    public static RefundRecordJpaEntity toEntity(RefundRecord record) {
        return RefundRecordJpaEntity.from(record);
    }

    public static RefundRecord toDomain(RefundRecordJpaEntity entity) {
        return RefundRecord.restore(entity.getId(), entity.getRefundIntentId(),
                new ReservationId(entity.getReservationId()), new OwnerId(entity.getOwnerId()),
                new BoatId(entity.getBoatId()), entity.getDisputeId(), new Money(entity.getAmount()),
                new Money(entity.getReleasedAmount()), entity.getDetail(),
                entity.getTransactionCost() == null ? null : new Money(entity.getTransactionCost()),
                entity.getExternalReference(), entity.getCreatedAt());
    }
}
