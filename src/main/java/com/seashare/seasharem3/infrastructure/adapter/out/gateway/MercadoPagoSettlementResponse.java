package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Respuesta de Mercado Pago para una dispersion o su consulta. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoSettlementResponse(Long id, String status, String statusDetail, BigDecimal amount) {
}
