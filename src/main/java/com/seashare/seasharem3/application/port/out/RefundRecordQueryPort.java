package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.util.Optional;

/**
 * Lectura de refund_record para UC06. Suma los montos netos liberados confirmados de una reserva.
 * Fuente: UC09 plan D-UC09-07 y UC06 plan D-UC06-04.
 */
public interface RefundRecordQueryPort {

    Optional<Money> releasedAmountByReservationId(ReservationId reservationId);
}
