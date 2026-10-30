package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de refund_intent (UC09). Fuente: UC09 plan T005/T008/T009.
 * {@code findByIdForUpdate} permite lecturas bloqueadas cuando el llamador comparte la transaccion;
 * la entidad JPA usa version optimista para impedir que webhook y worker se sobrescriban.
 */
public interface RefundIntentRepository {

    Optional<RefundIntent> findById(UUID id);

    Optional<RefundIntent> findByIdForUpdate(UUID id);

    RefundIntent save(RefundIntent refundIntent);

    Optional<RefundIntent> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    Optional<RefundIntent> findByExternalReference(String externalReference);

    List<RefundIntent> findByStatusIn(Collection<RefundIntentStatus> statuses);
}
