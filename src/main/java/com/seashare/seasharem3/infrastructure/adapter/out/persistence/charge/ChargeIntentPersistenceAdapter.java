package com.seashare.seasharem3.infrastructure.adapter.out.persistence.charge;

import com.seashare.seasharem3.application.port.out.ChargeIntentRepository;
import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA de {@code charge_intent}: alta del intento y actualizacion de su estado operativo.
 * Fuente: UC05 plan T009/T010; general-plan §4 (varias intenciones por reserva, unicidad por
 * {@code UNIQUE(idempotency_key)}).
 */
@Component
public class ChargeIntentPersistenceAdapter implements ChargeIntentRepository {

    private final ChargeIntentJpaRepository repository;

    public ChargeIntentPersistenceAdapter(ChargeIntentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeIntent> findById(UUID id) {
        return repository.findById(id).map(ChargePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeIntent> findLatestByReservationId(ReservationId reservationId) {
        return repository.findFirstByReservationIdOrderByCreatedAtDescIdDesc(reservationId.value())
                .map(ChargePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeIntent> findApprovedByReservationId(ReservationId reservationId) {
        return repository
                .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservationId.value(),
                        ChargeIntentStatus.CAPTURED.name())
                .map(ChargePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeIntent> findByExternalReference(String externalReference) {
        return repository.findFirstByExternalReference(externalReference).map(ChargePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChargeIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return repository.findFirstByIdempotencyKey(idempotencyKey.value())
                .map(ChargePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChargeIntent> findByStatusIn(Collection<ChargeIntentStatus> statuses) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        return repository.findByStatusIn(names).stream().map(ChargePersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public ChargeIntent save(ChargeIntent chargeIntent) {
        ChargeIntentJpaEntity entity = repository.findById(chargeIntent.id())
                .orElseGet(() -> ChargePersistenceMapper.toEntity(chargeIntent));
        entity.apply(chargeIntent);
        return ChargePersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }
}
