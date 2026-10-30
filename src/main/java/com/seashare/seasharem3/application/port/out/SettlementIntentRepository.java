package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de settlement_intent (UC10), con la comision conservada en la intencion.
 * Fuente: UC10 plan T007/T008. {@code findByIdForUpdate} permite lecturas bloqueadas cuando el
 * llamador comparte la transaccion; la entidad JPA usa version optimista para evitar sobrescrituras.
 */
public interface SettlementIntentRepository {

    Optional<SettlementIntent> findById(UUID id);

    Optional<SettlementIntent> findByIdForUpdate(UUID id);

    SettlementIntent save(SettlementIntent settlementIntent);

    Optional<SettlementIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Optional<SettlementIntent> findByExternalReference(String externalReference);

    List<SettlementIntent> findByStatusIn(Collection<SettlementStatus> statuses);
}
