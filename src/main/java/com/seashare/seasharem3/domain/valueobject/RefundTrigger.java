package com.seashare.seasharem3.domain.valueobject;

/**
 * Desencadenante de una intención de reembolso.
 * Fuente: UC09 plan T002 y §Puertos expuestos (`DISPUTA_RECHAZADO` es el valor fijado por el plan).
 */
public enum RefundTrigger {
    CANCELLED_FLEXIBLE,
    CANCELLED_MODERATE,
    CANCELLED_STRICT,
    CANCELLED_BY_HOST,
    DISPUTA_RECHAZADO
}
