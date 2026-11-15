package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.RefundIntent;
import com.seashare.seasharem3.domain.valueobject.RefundIntentStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

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

    /** Intención de la misma identidad de operación (D-37); evita un segundo reembolso por redelivery. */
    Optional<RefundIntent> findByReservationIdAndOperationKey(ReservationId reservationId, String operationKey);

    /** Intención de reembolso más reciente de la reserva (resuelve la solicitud duplicada del depósito). */
    Optional<RefundIntent> findLatestByReservationId(ReservationId reservationId);

    Optional<RefundIntent> findByExternalReference(String externalReference);

    List<RefundIntent> findByStatusIn(Collection<RefundIntentStatus> statuses);
}
