package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.CalculateReservationValueCommand;
import com.seashare.seasharem3.application.dto.ReservationValueResult;

/**
 * Puerto de entrada del cálculo del valor de la reserva (UC04).
 * Fuente: UC04 plan T010 y general-plan §3.5.
 * GAP: firma no publicada por el plan; derivada del nombre del caso de uso.
 */
public interface CalculateReservationValueUseCase {

    ReservationValueResult calculate(CalculateReservationValueCommand command);
}
