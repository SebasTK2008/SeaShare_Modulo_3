package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de Mercado Pago para un reembolso. {@code fee_details} es opcional; cuando aparece,
 * el costo transaccional se obtiene de la suma de sus montos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoRefundResponse(Long id, String status, String statusDetail, BigDecimal amount,
                                        List<FeeDetail> feeDetails) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeeDetail(BigDecimal amount) {
    }
}
