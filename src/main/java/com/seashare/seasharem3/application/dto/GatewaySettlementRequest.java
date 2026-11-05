package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

import java.util.UUID;

/**
 * Comando de liquidacion al libro interno simulado de UC10.
 *
 * <p>No existe una dispersion real en Mercado Pago: el libro interno usa la capacidad
 * {@code STANDARD_SETTLEMENT} y conserva la reserva, el monto neto y la clave idempotente del
 * intento (D-UC10-10). El mercado de pagos real queda como fase futura no implementada.</p>
 */
public record GatewaySettlementRequest(UUID reservationId, Money amount, String idempotencyKey) {
}
