package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import com.seashare.seasharem3.application.port.out.ChargeRecordQueryPort;
import com.seashare.seasharem3.application.port.out.ChargeRecordRepository;
import com.seashare.seasharem3.domain.model.ChargeRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de {@code charge_record}: alta del registro de auditoria y consultas para UC10,
 * UC12 y UC13. La tabla es inmutable (triggers anti-mutacion en el esquema), de modo que el
 * adaptador solo inserta y nunca actualiza.
 * Fuente: UC05 plan T009/T010; general-plan §4.
 */
@Component
public class ChargeRecordPersistenceAdapter implements ChargeRecordRepository, ChargeRecordQueryPort {

    private final ChargeRecordJpaRepository repository;

    public ChargeRecordPersistenceAdapter(ChargeRecordJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ChargeRecord save(ChargeRecord chargeRecord) {
        return ChargePersistenceMapper.toDomain(
                repository.saveAndFlush(ChargePersistenceMapper.toEntity(chargeRecord)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeRecord> findByChargeIntentId(UUID chargeIntentId) {
        return repository.findByChargeIntentId(chargeIntentId).map(ChargePersistenceMapper::toDomain);
    }
}
