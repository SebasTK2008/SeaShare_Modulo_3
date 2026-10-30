package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import java.math.BigDecimal;
import java.util.List;

/** Cuerpo de {@code POST /v1/advanced_payments/{id}/disbursements}. */
public record MercadoPagoSettlementRequest(List<Disbursement> disbursements) {

    public record Disbursement(BigDecimal amount, long collectorId, String externalReference) {
    }
}
