package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;

import java.util.Optional;

/**
 * Persistencia de refund_intent (UC09).
 * Fuente: UC09 plan T005/T008/T009.
 * GAP: firmas no publicadas; se derivan guardado y búsqueda por clave idempotente.
 */
public interface RefundIntentRepository {

    RefundIntent save(RefundIntent refundIntent);

    Optional<RefundIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}
