package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.PendingOutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lectura y confirmación de {@code outbox_message} para el relay.
 * Fuente: general-plan §4 y D-08; UC11 plan T046 (OutboxRelayJob). [CONV] núcleo compartido.
 */
public interface OutboxMessageRepository {

    /** Mensajes sin publicar más antiguos primero, hasta el límite indicado. */
    List<PendingOutboxMessage> findPending(int limit);

    /** Marca el mensaje como publicado después de confirmar la entrega al broker. */
    void markPublished(UUID id, Instant publishedAt);
}
