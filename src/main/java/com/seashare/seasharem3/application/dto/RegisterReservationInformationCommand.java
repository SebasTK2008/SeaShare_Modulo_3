package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.BoatId;
import com.seashare.seasharem3.domain.valueobject.OwnerId;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.LocalDate;

/** Los 7 campos obligatorios del mensaje de UC03 usando los VOs compartidos. Fuente: UC03 plan T008; contrato events/UC03-informacion-reserva.md §Body. */
public record RegisterReservationInformationCommand(ReservationId reservationId, BoatId boatId, LocalDate startDate,
                                                    LocalDate endDate, int passengers, OwnerId ownerId,
                                                    int maxCapacity) {
}
