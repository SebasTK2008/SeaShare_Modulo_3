package com.seashare.seasharem3.domain.model;

import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de disputa de garantia recibido desde Reservas.
 *
 * <p>Es la fuente canonica del estado de la disputa: cada notificacion se conserva con su
 * {@code status}, {@code statusChangedAt} y clave idempotente. {@code processedAt} nulo indica que el
 * evento esta reclamado pero aun no confirmado; no nulo, que ya se proceso. Fuente: UC08 plan T004,
 * T008 y D-UC08-06.</p>
 */
public record DisputeEvent(
        UUID id,
        ReservationId reservationId,
        DisputeId disputeId,
        String eventKey,
        DisputeStatus status,
        Instant statusChangedAt,
        Instant processedAt) {

    private static final int MAX_EVENT_KEY_LENGTH = 128;

    public DisputeEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(disputeId, "disputeId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(statusChangedAt, "statusChangedAt");
        if (eventKey == null || eventKey.isBlank()) {
            throw new DomainException("eventKey is required");
        }
        if (eventKey.length() > MAX_EVENT_KEY_LENGTH) {
            throw new DomainException("eventKey cannot be longer than " + MAX_EVENT_KEY_LENGTH + " characters");
        }
    }

    /** Devuelve una copia del evento marcada como procesada. */
    public DisputeEvent markProcessed(Instant processedAt) {
        return new DisputeEvent(id, reservationId, disputeId, eventKey, status, statusChangedAt,
                Objects.requireNonNull(processedAt, "processedAt"));
    }

    /** Misma clave de negocio y mismo contenido; dos eventos con la misma clave no deben divergir. */
    public boolean samePayload(DisputeEvent other) {
        return other != null
                && status == other.status
                && statusChangedAt.equals(other.statusChangedAt);
    }
}
