package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.model.ChargeIntent;
import com.seashare.seasharem3.domain.valueobject.ChargeIntentStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de UC05 consumido por UC06, UC09 y UC10.
 * Fuente: UC05 plan T039; métodos documentados: findLatestByReservationId (UC06) y findApprovedByReservationId (UC09).
 * GAP: catálogo completo de métodos no publicado; save se deriva de la persistencia de intenciones.
 * [CONV] núcleo: findByExternalReference asocia el webhook de la pasarela (UC05/UC09/UC10 §Reglas) y
 * findByStatusIn alimenta las conciliaciones (UC05/UC09/UC10).
 */
public interface ChargeIntentRepository {

    Optional<ChargeIntent> findLatestByReservationId(ReservationId reservationId);

    Optional<ChargeIntent> findApprovedByReservationId(ReservationId reservationId);

    Optional<ChargeIntent> findByExternalReference(String externalReference);

    List<ChargeIntent> findByStatusIn(Collection<ChargeIntentStatus> statuses);

    ChargeIntent save(ChargeIntent chargeIntent);
}
