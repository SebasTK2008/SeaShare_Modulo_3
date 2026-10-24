package com.seashare.seasharem3.domain.valueobject;

/** Estados internos de una intención de liquidación. Fuente: general-plan §8. */
public enum SettlementStatus {
    PENDING_SEND,
    IN_PROCESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMMUNICATION_ERROR
}
