package com.seashare.seasharem3.domain.valueobject;

/**
 * Estados internos de una intención de reembolso. Fuente: general-plan §8.
 *
 * <p>{@code FAILED} solo se usa como resultado de una solicitud que no pudo registrarse; nunca se
 * persiste como estado de una intención (mismo criterio que {@link SettlementStatus#FAILED}).</p>
 */
public enum RefundIntentStatus {
    PENDING_SEND,
    IN_PROCESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMMUNICATION_ERROR,
    FAILED
}
