package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;

/**
 * Puerto de entrada de la notificacion de disputa (UC08).
 * Fuente: UC08 plan T007/T008 y general-plan §3.5.
 */
public interface HandleDisputeNotificationUseCase {

    void handle(DisputeNotificationCommand command);
}
