package com.seashare.seasharem3.domain.valueobject;

/**
 * Estado de disposición del depósito de garantía (desenlace único por depósito).
 * Fuente: general-plan §4 (`HELD`, `REFUND_REQUESTED`, `SETTLEMENT_REQUESTED`).
 * GAP: documentación parcial; el catálogo completo solo aparece en esa columna.
 */
public enum DepositDispositionStatus {
    HELD,
    REFUND_REQUESTED,
    SETTLEMENT_REQUESTED
}
