package com.seashare.seasharem3.domain.valueobject;

/**
 * Capacidad del libro de liquidaciones utilizada por una intención (UC10 RF-009, entidad
 * {@code IntenciónDeDispersión}).
 *
 * <p>En esta entrega la liquidación es un libro interno simulado, por lo que la única capacidad
 * válida es {@code STANDARD_SETTLEMENT} (D-UC10-10). El mercado de pagos real queda como fase
 * futura no implementada; no se usa ningún recurso de dispersión de Mercado Pago.</p>
 */
public enum SettlementCapability {

    /** Liquidación estándar registrada en el libro interno simulado. */
    STANDARD_SETTLEMENT
}
