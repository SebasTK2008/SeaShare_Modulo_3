package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.SettlementIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;

import java.util.Optional;

/**
 * Persistencia de settlement_intent (UC10), con la comisión conservada en la intención.
 * Fuente: UC10 plan T007/T008.
 * GAP: firmas no publicadas; se derivan guardado y búsqueda por clave idempotente.
 */
public interface SettlementIntentRepository {

    SettlementIntent save(SettlementIntent settlementIntent);

    Optional<SettlementIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}
