package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/**
 * Solicitud de reembolso a la pasarela (= SolicitudReembolsoPasarela [SPEC]).
 * Fuente: UC09 plan T011 y contrato external/pasarela-comando-reembolso.md (POST /v1/payments/{id}/refunds, X-Idempotency-Key, amount explícito).
 * GAP: composición exacta del record no publicada; el monto nulo representa reembolso total según el contrato.
 */
public record RefundGatewayRequest(String originalChargeReference, Money amount, String operationKey) {
}
