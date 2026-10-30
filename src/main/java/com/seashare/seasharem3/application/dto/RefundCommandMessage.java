package com.seashare.seasharem3.application.dto;

import java.util.UUID;

/** Mensaje interno de la cola de reembolsos: solo el identificador de la intencion. */
public record RefundCommandMessage(UUID refundIntentId) {
}
