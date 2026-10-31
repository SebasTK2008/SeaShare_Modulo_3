package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import com.seashare.seasharem3.application.dto.ReservationStatusCommand;
import com.seashare.seasharem3.domain.exception.DomainException;
import com.seashare.seasharem3.domain.valueobject.ReservationStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Traduce el mensaje AMQP de UC07 al comando de aplicación.
 *
 * <p>Valida los campos obligatorios del contrato y convierte el estado a {@link ReservationStatus};
 * un estado desconocido, una fecha ausente o un {@code PENDING} sin token son mensajes inválidos
 * (error lógico, no una operación financiera). Fuente: contrato §2 y UC07 plan §Reglas 1-3.</p>
 */
@Component
public class ReservationStatusMessageMapper {

    public ReservationStatusCommand toCommand(ReservationStatusMessage message) {
        Objects.requireNonNull(message, "message");

        if (message.reservationId() == null) {
            throw new DomainException("reservation_id is required");
        }
        if (message.status() == null) {
            throw new DomainException("status is required");
        }
        if (message.statusChangedAt() == null) {
            throw new DomainException("status_changed_at is required");
        }

        ReservationStatus status = parseStatus(message.status());
        if (status == ReservationStatus.PENDING
                && (message.paymentTokenRef() == null || message.paymentTokenRef().isBlank())) {
            throw new DomainException("payment_token_ref is required when status is PENDING");
        }

        return new ReservationStatusCommand(message.reservationId(), status, message.statusChangedAt(),
                message.paymentTokenRef(), message.paymentMethodType(), immutableCopy(message.paymentMetadata()));
    }

    private static ReservationStatus parseStatus(String status) {
        try {
            return ReservationStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("status is not one of the ten recognized reservation states: " + status,
                    exception);
        }
    }

    private static Map<String, String> immutableCopy(Map<String, String> metadata) {
        return metadata == null ? null : Map.copyOf(metadata);
    }
}
