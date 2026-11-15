package com.seashare.seasharem3.domain.valueobject;

import java.util.Objects;

/**
 * Estados devueltos por la consulta de confirmación de pago.
 * Fuente: contrato {@code rest/UC06-confirmacion-pago.md} §4 y general-plan §8.
 */
public enum PaymentConfirmationStatus {
    IN_PROCESS,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    UNKNOWN;

    /**
     * Traduce el estado interno de la intención de cobro al vocabulario del contrato.
     * Fuente: general-plan §8; UC06 plan T001 y regla 3.
     */
    public static PaymentConfirmationStatus from(ChargeIntentStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case PENDING_SEND, IN_PROCESS -> IN_PROCESS;
            case CAPTURED -> APPROVED;
            case REJECTED -> REJECTED;
            case CANCELLED -> CANCELLED;
            case EXPIRED -> EXPIRED;
            case COMMUNICATION_ERROR -> UNKNOWN;
        };
    }
}
