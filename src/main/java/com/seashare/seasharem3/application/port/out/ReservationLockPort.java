package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.valueobject.ReservationId;

/** Firma publicada en UC11 plan §Núcleo compartido: bloqueo asesor único por reserva (D-15). */
public interface ReservationLockPort {

    void lock(ReservationId reservationId);
}
