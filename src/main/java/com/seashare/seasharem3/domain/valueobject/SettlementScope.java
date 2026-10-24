package com.seashare.seasharem3.domain.valueobject;

/**
 * Alcance de una intención de liquidación.
 * Fuente: general-plan §4 (valores `STANDARD`, `CANCELLATION_COMPENSATION`, `DEPOSIT`).
 */
public enum SettlementScope {
    STANDARD,
    CANCELLATION_COMPENSATION,
    DEPOSIT
}
