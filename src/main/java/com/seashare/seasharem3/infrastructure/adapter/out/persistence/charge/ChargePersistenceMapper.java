package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.PaymentMethodReference;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

/**
 * Mapeo entre el dominio de UC05 y sus entidades JPA.
 *
 * <p>[CONV] mapeo manual deliberado, siguiendo la decisión D-UC11-11 del núcleo: el dominio es
 * inmutable y las entidades no exponen constructores publicos, de modo que MapStruct exigiría
 * setters o un builder y debilitaría la encapsulacion. La conversion se hace con los factories de
 * rehidratacion del dominio ({@code restore}), que no revalidan invariantes porque la base de datos
 * es la fuente de verdad.</p>
 *
 * <p>Fuente: UC05 plan T009; general-plan §4.</p>
 */
public final class ChargePersistenceMapper {

    private ChargePersistenceMapper() {
    }

    public static ChargeIntentJpaEntity toEntity(ChargeIntent intent) {
        return ChargeIntentJpaEntity.from(intent);
    }

    public static ChargeIntent toDomain(ChargeIntentJpaEntity entity) {
        return ChargeIntent.restore(entity.getId(), new ReservationId(entity.getReservationId()),
                new IdempotencyKey(entity.getIdempotencyKey()), entity.statusValue(),
                new Money(entity.getAmount()),
                new PaymentMethodReference(entity.getPaymentTokenRef(), entity.getPaymentMethodType(),
                        entity.getLastFour()),
                entity.getExternalReference(), entity.getStatusDetail(), entity.getPayerEmail(),
                entity.getCreatedAt());
    }

    public static ChargeRecordJpaEntity toEntity(ChargeRecord record) {
        return ChargeRecordJpaEntity.from(record);
    }

    public static ChargeRecord toDomain(ChargeRecordJpaEntity entity) {
        return ChargeRecord.restore(entity.getId(), entity.getChargeIntentId(),
                new ReservationId(entity.getReservationId()), new OwnerId(entity.getOwnerId()),
                new BoatId(entity.getBoatId()), new Money(entity.getAmount()),
                new Money(entity.getRentalAmount()), new Money(entity.getInsuranceAmount()),
                new Money(entity.getDepositAmount()), entity.getPaymentMethodType(), entity.getLastFour(),
                entity.getExternalReference(), entity.getCreatedAt());
    }
}
