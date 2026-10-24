package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;

/**
 * Puerto de entrada del registro de información de reserva (UC03), sin respuesta (RF-006).
 * Fuente: UC03 plan T010 y general-plan §3.5.
 * GAP: nombre del método no publicado; derivado del caso de uso (retorno void documentado).
 */
public interface RegisterReservationInformationUseCase {

    void register(RegisterReservationInformationCommand command);
}
