package com.seashare.seasharem3.infrastructure.adapter.out.persistence.deposit;

import com.seashare.seasharem3.application.port.out.DepositDispositionRepository;
import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Adaptador JPA de {@code deposit_disposition}: alta y compare-and-set atomico (D-34). */
@Component
public class DepositDispositionPersistenceAdapter implements DepositDispositionRepository {

    private final DepositDispositionJpaRepository repository;

    public DepositDispositionPersistenceAdapter(DepositDispositionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DepositDisposition> findByReservationId(ReservationId reservationId) {
        return repository.findById(reservationId.value()).map(DepositDispositionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public DepositDisposition save(DepositDisposition disposition) {
        return toDomain(repository.saveAndFlush(DepositDispositionJpaEntity.from(disposition)));
    }

    @Override
    @Transactional
    public boolean compareAndSet(ReservationId reservationId, DepositDispositionStatus expected,
            DepositDisposition next) {
        return repository.compareAndSet(reservationId.value(), expected.name(), next.disposition().name(),
                next.disputeId(), next.disputeStatus() == null ? null : next.disputeStatus().name(),
                next.completedAt()) == 1;
    }

    private static DepositDisposition toDomain(DepositDispositionJpaEntity entity) {
        return DepositDisposition.restore(new ReservationId(entity.getReservationId()), entity.getCompletedAt(),
                entity.getDisputeId(),
                entity.getDisputeStatus() == null ? null : DisputeStatus.valueOf(entity.getDisputeStatus()),
                DepositDispositionStatus.valueOf(entity.getDisposition()));
    }
}
