package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import java.math.BigDecimal;

/** Cuerpo de {@code POST /v1/payments/{id}/refunds}. Un monto explicito permite reembolsos parciales. */
public record MercadoPagoRefundRequest(BigDecimal amount) {
}
