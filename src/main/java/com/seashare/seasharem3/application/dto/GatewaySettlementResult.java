package com.seashare.seasharem3.application.dto;

/**
 * Resultado técnico de la dispersión en la pasarela, único DTO de entrada para registrar el resultado (T005/T011).
 * Fuente: UC10 plan T006 y §Puerto interno (composición congelada), y contrato
 * external/pasarela-comando-liquidacion.md (status, id); {@code statusDetail} es el detalle del estado externo que
 * la intención persiste (columna {@code status_detail}, simétrica a charge/refund_intent) y llega nulo cuando la
 * respuesta síncrona no lo trae.
 */
public record GatewaySettlementResult(String status, String externalId, String statusDetail) {
}
