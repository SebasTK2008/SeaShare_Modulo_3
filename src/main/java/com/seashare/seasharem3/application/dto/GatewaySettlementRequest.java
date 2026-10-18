package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/**
 * Comando de dispersión a la pasarela.
 * Fuente: UC10 plan T006 y contrato external/pasarela-comando-liquidacion.md (disbursements[]: amount, collector_id, external_reference).
 * GAP: composición exacta del record no publicada; campos derivados del contrato externo.
 */
public record GatewaySettlementRequest(String collectorId, Money amount, String externalReference,
                                       String operationKey) {
}
