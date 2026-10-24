package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de refund_intent (UC09).
 * Fuente: UC09 plan T005/T008/T009.
 * GAP: firmas no publicadas; se derivan guardado y búsqueda por clave idempotente.
 * [CONV] núcleo: findByExternalReference asocia el webhook de reembolso y findByStatusIn alimenta la conciliación.
 */
public interface RefundIntentRepository {

    RefundIntent save(RefundIntent refundIntent);

    Optional<RefundIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Optional<RefundIntent> findByExternalReference(String externalReference);

    List<RefundIntent> findByStatusIn(Collection<RefundIntentStatus> statuses);
}
