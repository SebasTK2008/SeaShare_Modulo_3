package com.seashare.seasharem3.application.dto;

/**
 * Resultado técnico de la dispersión en la pasarela.
 * Fuente: UC10 plan T006 y contrato external/pasarela-comando-liquidacion.md (status, id).
 * GAP: composición exacta del record no publicada; campos derivados del contrato externo.
 */
public record GatewaySettlementResult(String status, String externalId) {
}
