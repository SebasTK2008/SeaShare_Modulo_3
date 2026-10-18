package com.seashare.seasharem3.domain.valueobject;

/** Estados internos de una intención de reembolso. Fuente: general-plan §8. */
public enum RefundIntentStatus {
    PENDING_SEND,
    IN_PROCESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMMUNICATION_ERROR
}
