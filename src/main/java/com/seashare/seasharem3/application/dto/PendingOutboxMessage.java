package com.seashare.seasharem3.application.dto;

import java.util.UUID;

/**
 * Mensaje pendiente de publicación leído de {@code outbox_message}.
 * Fuente: general-plan §4; UC11 plan T046. [CONV] núcleo compartido (DTO de lectura del relay, no un
 * command/result de caso de uso).
 */
public record PendingOutboxMessage(UUID id, String exchange, String routingKey, String payload) {
}
