package com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation;

import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.domain.exception.ReservationInfoNotFoundException;
import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.BaseRate;
import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.ReservationValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adaptador JPA de {@code reservation_information}: upsert de UC03 que invalida las columnas calculadas
 * (D-UC03-02) y escritura de montos de UC04. Fuente: UC03 plan T008/T009; UC04 plan T007; general-plan §4.
 * El adaptador no toma el bloqueo asesor: lo adquiere el servicio dentro de su transacción (D-15).
 */
@Component
public class ReservationInformationPersistenceAdapter implements ReservationInformationRepository {

    private final ReservationInformationJpaRepository repository;

    public ReservationInformationPersistenceAdapter(ReservationInformationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationInformation> findByReservationId(ReservationId reservationId) {
        return repository.findById(reservationId.value()).map(ReservationInformationPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public ReservationInformation save(ReservationInformation information) {
        ReservationInformationJpaEntity entity = repository.findById(information.reservationId().value())
                .orElseGet(() -> ReservationInformationJpaEntity.from(information));
        entity.applyInformative(information);
        entity.invalidateCalculated();
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public ReservationInformation saveCalculatedAmounts(ReservationInformation information) {
        ReservationInformationJpaEntity entity = repository.findById(information.reservationId().value())
                .orElseThrow(() -> new ReservationInfoNotFoundException(
                        "reservation_information not found: " + information.reservationId().value()));
        entity.applyCalculated(information);
        return toDomain(repository.saveAndFlush(entity));
    }

    private static ReservationInformation toDomain(ReservationInformationJpaEntity entity) {
        ReservationInformation information = ReservationInformation.create(
                new ReservationId(entity.getReservationId()),
                new BoatId(entity.getBoatId()),
                new BaseRate(new Money(entity.getBaseRate())),
                entity.getBaseRateParametersId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getPassengers(),
                new OwnerId(entity.getOwnerId()),
                entity.getMaxCapacity());
        if (entity.getRentalAmount() != null) {
            information = information.withCalculated(
                    new ReservationValue(
                            new Money(entity.getRentalAmount()),
                            new Money(entity.getInsuranceAmount()),
                            new Money(entity.getDepositAmount()),
                            new Money(entity.getTotalAmount())),
                    entity.getFrozenParametersId(),
                    entity.getCalculatedAt());
        }
        return information;
    }
}
