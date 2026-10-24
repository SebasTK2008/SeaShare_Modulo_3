package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;

/**
 * Puerto de entrada de la notificación de disputa (UC08).
 * Fuente: UC08 plan T007 y general-plan §3.5.
 * GAP: nombre del método no publicado; se refleja el patrón unidireccional de UC07.
 */
public interface HandleDisputeNotificationUseCase {

    void handle(DisputeNotificationCommand command);
}
