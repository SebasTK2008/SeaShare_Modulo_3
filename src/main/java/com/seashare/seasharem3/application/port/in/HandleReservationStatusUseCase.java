package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.ReservationStatusCommand;

/** Firma publicada por UC07 (plan §Puertos expuestos): solo lo invoca el listener de UC07. */
public interface HandleReservationStatusUseCase {

    void handle(ReservationStatusCommand command);
}
