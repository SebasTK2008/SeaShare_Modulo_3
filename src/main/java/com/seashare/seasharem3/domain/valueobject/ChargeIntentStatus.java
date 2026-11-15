package com.seashare.seasharem3.domain.valueobject;

/** Estados internos de una intención de cobro. Fuente: general-plan §8. */
public enum ChargeIntentStatus {
    PENDING_SEND,
    IN_PROCESS,
    CAPTURED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMMUNICATION_ERROR
}
