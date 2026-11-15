package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Optional;

/**
 * Persistencia de deposit_disposition (UC08) con desenlace unico por deposito (compare-and-set, D-34).
 * Fuente: UC08 plan T008/T008a.
 */
public interface DepositDispositionRepository {

    Optional<DepositDisposition> findByReservationId(ReservationId reservationId);

    DepositDisposition save(DepositDisposition disposition);

    /**
     * Aplica el desenlace si la fila sigue en {@code expected}; en la misma operacion conserva la
     * trazabilidad de la disputa y la fecha de finalizacion de la reserva.
     */
    boolean compareAndSet(ReservationId reservationId, DepositDispositionStatus expected,
                          DepositDisposition next);
}
