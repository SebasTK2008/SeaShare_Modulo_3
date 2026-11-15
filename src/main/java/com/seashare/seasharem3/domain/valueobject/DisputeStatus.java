package com.seashare.seasharem3.domain.valueobject;

/** Estados canónicos de una disputa de garantía. Fuente: UC08 plan §Reglas 2 (solo PENDING, REJECTED, COMPLETED). */
public enum DisputeStatus {
    PENDING,
    REJECTED,
    COMPLETED
}
