package com.seashare.seasharem3.infrastructure.adapter.out.persistence.reservation;

import com.seashare.seasharem3.application.port.out.ReservationInformationRepository;
import com.seashare.seasharem3.application.port.out.ReservationLockPort;
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
 * (D-UC03-02) y escritura de montos de UC04. Fuente: UC03 plan T014/T015; UC04 plan T007; general-plan §4.
 * El adaptador adquiere el bloqueo asesor por reserva al inicio de su transacción (D-15), de modo que
 * el upsert de UC03 y el cálculo de UC04 se serializan por reserva; el bloqueo se libera al confirmar.
 */
@Component
public class ReservationInformationPersistenceAdapter implements ReservationInformationRepository {

    private final ReservationInformationJpaRepository repository;
    private final ReservationLockPort reservationLockPort;

    public ReservationInformationPersistenceAdapter(ReservationInformationJpaRepository repository,
            ReservationLockPort reservationLockPort) {
        this.repository = repository;
        this.reservationLockPort = reservationLockPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationInformation> findByReservationId(ReservationId reservationId) {
        return repository.findById(reservationId.value()).map(ReservationInformationPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public ReservationInformation save(ReservationInformation information) {
        reservationLockPort.lock(information.reservationId());
        ReservationInformationJpaEntity entity = repository.findById(information.reservationId().value())
                .orElseGet(ReservationInformationJpaEntity::new);
        entity.applyInformative(information);
        entity.invalidateCalculated();
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public ReservationInformation saveCalculatedAmounts(ReservationInformation information) {
        reservationLockPort.lock(information.reservationId());
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
