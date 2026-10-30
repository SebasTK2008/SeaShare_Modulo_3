package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import com.seashare.seasharem3.application.port.out.RefundRecordQueryPort;
import com.seashare.seasharem3.application.port.out.RefundRecordRepository;
import com.seashare.seasharem3.domain.model.RefundRecord;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA de {@code refund_record}: insercion inmutable y lectura agregada para UC06. */
@Component
public class RefundRecordPersistenceAdapter implements RefundRecordRepository, RefundRecordQueryPort {

    private final RefundRecordJpaRepository repository;

    public RefundRecordPersistenceAdapter(RefundRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RefundRecord save(RefundRecord refundRecord) {
        return RefundPersistenceMapper.toDomain(
                repository.saveAndFlush(RefundPersistenceMapper.toEntity(refundRecord)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundRecord> findByRefundIntentId(UUID refundIntentId) {
        return repository.findFirstByRefundIntentId(refundIntentId).map(RefundPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Money> releasedAmountByReservationId(ReservationId reservationId) {
        return repository.sumReleasedAmountByReservationId(reservationId.value()).map(Money::new);
    }
}
