package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Optional;

/**
 * Puerto de UC05 consumido por UC06, UC09 y UC10.
 * Fuente: UC05 plan T039; métodos documentados: findLatestByReservationId (UC06) y findApprovedByReservationId (UC09).
 * GAP: catálogo completo de métodos no publicado; save se deriva de la persistencia de intenciones.
 */
public interface ChargeIntentRepository {

    Optional<ChargeIntent> findLatestByReservationId(ReservationId reservationId);

    Optional<ChargeIntent> findApprovedByReservationId(ReservationId reservationId);

    ChargeIntent save(ChargeIntent chargeIntent);
}
