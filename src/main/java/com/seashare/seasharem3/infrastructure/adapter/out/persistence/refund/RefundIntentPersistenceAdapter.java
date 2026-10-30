package com.seashare.seasharem3.infrastructure.adapter.out.persistence.refund;

import com.seashare.seasharem3.application.port.out.RefundIntentRepository;
import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA de {@code refund_intent}: alta, bloqueo pesimista y actualizacion de estado. */
@Component
public class RefundIntentPersistenceAdapter implements RefundIntentRepository {

    private final RefundIntentJpaRepository repository;

    public RefundIntentPersistenceAdapter(RefundIntentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundIntent> findById(UUID id) {
        return repository.findById(id).map(RefundPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<RefundIntent> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(RefundPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public RefundIntent save(RefundIntent refundIntent) {
        RefundIntentJpaEntity entity = repository.findById(refundIntent.id())
                .orElseGet(() -> RefundPersistenceMapper.toEntity(refundIntent));
        entity.apply(refundIntent);
        return RefundPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return repository.findFirstByIdempotencyKey(idempotencyKey.value())
                .map(RefundPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundIntent> findByExternalReference(String externalReference) {
        return repository.findFirstByExternalReference(externalReference)
                .map(RefundPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundIntent> findByStatusIn(Collection<RefundIntentStatus> statuses) {
        return repository.findByStatusIn(statuses.stream().map(Enum::name).toList()).stream()
                .map(RefundPersistenceMapper::toDomain).toList();
    }
}
