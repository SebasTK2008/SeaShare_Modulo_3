package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.SettlementStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de settlement_intent (UC10), con la comisión conservada en la intención.
 * Fuente: UC10 plan T007/T008.
 * GAP: firmas no publicadas; se derivan guardado y búsqueda por clave idempotente.
 * [CONV] núcleo: findByExternalReference asocia el webhook de liquidación y findByStatusIn alimenta la conciliación.
 */
public interface SettlementIntentRepository {

    Optional<SettlementIntent> findById(UUID id);

    SettlementIntent save(SettlementIntent settlementIntent);

    Optional<SettlementIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Optional<SettlementIntent> findByExternalReference(String externalReference);

    List<SettlementIntent> findByStatusIn(Collection<SettlementStatus> statuses);
}
