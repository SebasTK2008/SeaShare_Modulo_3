package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.DepositDisposition;
import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Optional;

/**
 * Persistencia de deposit_disposition (UC08) con desenlace único por depósito (compare-and-set, D-34).
 * Fuente: UC08 plan T008/T008a.
 * GAP: firma no publicada; se derivan lectura y transición de estado.
 */
public interface DepositDispositionRepository {

    Optional<DepositDisposition> findByReservationId(ReservationId reservationId);

    boolean compareAndSet(ReservationId reservationId, DepositDispositionStatus expected,
                          DepositDispositionStatus next);
}
