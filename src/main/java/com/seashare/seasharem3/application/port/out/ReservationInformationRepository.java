package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.ReservationInformation;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Optional;

/**
 * Firma publicada por UC03 (plan §Firmas referenciadas) y ampliada por UC04 (saveCalculatedAmounts).
 * El upsert invalida las columnas calculadas y frozen_parameters_id.
 */
public interface ReservationInformationRepository {

    Optional<ReservationInformation> findByReservationId(ReservationId reservationId);

    ReservationInformation save(ReservationInformation information);

    ReservationInformation saveCalculatedAmounts(ReservationInformation information);
}
