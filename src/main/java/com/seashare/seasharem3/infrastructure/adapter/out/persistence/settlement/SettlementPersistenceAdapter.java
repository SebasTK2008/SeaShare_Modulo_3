package com.seashare.seasharem3.infrastructure.adapter.out.persistence.settlement;

import com.seashare.seasharem3.application.port.out.CommissionRecordRepository;
import com.seashare.seasharem3.application.port.out.SettlementIntentRepository;
import com.seashare.seasharem3.application.port.out.SettlementRecordRepository;
import com.seashare.seasharem3.domain.model.CommissionRecord;
import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.model.SettlementRecord;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del agregado de UC10: intencion mutable y registros inmutables. */
@Component
public class SettlementPersistenceAdapter
        implements SettlementIntentRepository, SettlementRecordRepository, CommissionRecordRepository {

    private final SettlementIntentJpaRepository intentRepository;
    private final SettlementRecordJpaRepository recordRepository;
    private final CommissionRecordJpaRepository commissionRepository;

    public SettlementPersistenceAdapter(SettlementIntentJpaRepository intentRepository,
            SettlementRecordJpaRepository recordRepository, CommissionRecordJpaRepository commissionRepository) {
        this.intentRepository = intentRepository;
        this.recordRepository = recordRepository;
        this.commissionRepository = commissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementIntent> findById(UUID id) {
        return intentRepository.findById(id).map(SettlementPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<SettlementIntent> findByIdForUpdate(UUID id) {
        return intentRepository.findByIdForUpdate(id).map(SettlementPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public SettlementIntent save(SettlementIntent intent) {
        SettlementIntentJpaEntity entity = intentRepository.findById(intent.id())
                .orElseGet(() -> SettlementPersistenceMapper.toEntity(intent));
        entity.apply(intent);
        return SettlementPersistenceMapper.toDomain(intentRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return intentRepository.findFirstByIdempotencyKey(idempotencyKey.value())
                .map(SettlementPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementIntent> findByExternalReference(String externalReference) {
        return intentRepository.findFirstByExternalReference(externalReference)
                .map(SettlementPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementIntent> findByStatusIn(Collection<SettlementStatus> statuses) {
        return intentRepository.findByStatusIn(statuses.stream().map(Enum::name).toList()).stream()
                .map(SettlementPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public SettlementRecord save(SettlementRecord record) {
        return SettlementPersistenceMapper.toDomain(
                recordRepository.saveAndFlush(SettlementPersistenceMapper.toEntity(record)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementRecord> findBySettlementIntentId(UUID settlementIntentId) {
        return recordRepository.findFirstBySettlementIntentId(settlementIntentId)
                .map(SettlementPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public CommissionRecord save(CommissionRecord record) {
        return SettlementPersistenceMapper.toDomain(
                commissionRepository.saveAndFlush(SettlementPersistenceMapper.toEntity(record)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommissionRecord> findBySettlementRecordId(UUID settlementRecordId) {
        return commissionRepository.findFirstBySettlementRecordId(settlementRecordId)
                .map(SettlementPersistenceMapper::toDomain);
    }
}
