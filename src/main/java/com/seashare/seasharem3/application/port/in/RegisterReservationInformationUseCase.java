package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.RegisterReservationInformationCommand;

/**
 * Puerto de entrada del registro de información de reserva (UC03), sin respuesta (RF-006).
 * Fuente: UC03 plan T010 y general-plan §3.5.
 *
 * <p>Firma publicada para el adaptador de mensajería de UC03:
 * {@code void register(RegisterReservationInformationCommand command)}. Es un caso de uso
 * unidireccional: no devuelve respuesta ni desglose al Sistema de Reservas y Operaciones.</p>
 */
public interface RegisterReservationInformationUseCase {

    void register(RegisterReservationInformationCommand command);
}
