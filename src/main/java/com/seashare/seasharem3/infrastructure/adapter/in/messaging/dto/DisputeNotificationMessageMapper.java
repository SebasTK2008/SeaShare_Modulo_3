package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import com.seashare.seasharem3.application.dto.DisputeNotificationCommand;
import com.seashare.seasharem3.domain.exception.DisputeNotificationValidationException;
import com.seashare.seasharem3.domain.valueobject.DisputeId;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Traduce el mensaje AMQP de UC08 al comando de aplicacion.
 *
 * <p>Valida los cinco campos del contrato y convierte el estado a {@link DisputeStatus}; un estado
 * desconocido o un campo ausente es un mensaje invalido (E2), no una operacion financiera.
 * Fuente: UC08 plan T006 y reglas 1-2.</p>
 */
@Component
public class DisputeNotificationMessageMapper {

    private static final int MAX_EVENT_KEY_LENGTH = 128;

    public DisputeNotificationCommand toCommand(DisputeNotificationMessage message) {
        Objects.requireNonNull(message, "message");

        if (message.reservationId() == null) {
            throw new DisputeNotificationValidationException("reservation_id is required");
        }
        if (message.disputeId() == null) {
            throw new DisputeNotificationValidationException("dispute_id is required");
        }
        if (message.status() == null) {
            throw new DisputeNotificationValidationException("status is required");
        }
        if (message.statusChangedAt() == null) {
            throw new DisputeNotificationValidationException("status_changed_at is required");
        }
        if (message.eventKey() == null || message.eventKey().isBlank()) {
            throw new DisputeNotificationValidationException("event_key is required");
        }
        if (message.eventKey().length() > MAX_EVENT_KEY_LENGTH) {
            throw new DisputeNotificationValidationException(
                    "event_key cannot be longer than " + MAX_EVENT_KEY_LENGTH + " characters");
        }

        return new DisputeNotificationCommand(new ReservationId(message.reservationId()),
                new DisputeId(message.disputeId()), parseStatus(message.status()), message.statusChangedAt(),
                message.eventKey());
    }

    private static DisputeStatus parseStatus(String status) {
        try {
            return DisputeStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new DisputeNotificationValidationException(
                    "status must be PENDING, REJECTED or COMPLETED: " + status, exception);
        }
    }
}
