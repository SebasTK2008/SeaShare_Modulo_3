package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Detalle del pago de Mercado Pago reducido a los reembolsos. Se usa como fallback cuando los
 * endpoints GET de reembolsos responden 405 en el entorno sandbox.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoPaymentRefundsResponse(List<MercadoPagoRefundResponse> refunds) {
}
