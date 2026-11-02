package com.seashare.seasharem3.domain.valueobject;

/**
 * Capacidad de la pasarela utilizada para una dispersión (UC10 RF-009, entidad
 * {@code IntenciónDeDispersión}).
 *
 * <p>No todos los recursos de la pasarela ofrecen la misma operación: el contrato de liquidación
 * usa {@code POST /v1/advanced_payments/{id}/disbursements}. La intención conserva cuál se usó para
 * que el resultado y la conciliación se interpreten contra la operación correcta.</p>
 */
public enum SettlementCapability {

    /** Disbursement sobre el pago avanzado del cobro original. */
    ADVANCED_PAYMENTS_DISBURSEMENT,

    /** Liquidación estándar sobre la cuenta del vendedor. */
    STANDARD_SETTLEMENT
}
