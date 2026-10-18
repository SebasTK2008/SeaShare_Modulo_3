package com.seashare.seasharem3.application.dto;

/**
 * Resultado de la liquidación informado por la pasarela para su registro.
 * Fuente: UC10 plan T005 (DTO SettlementResultCommand) y §Reglas 13 (reconciliación del webhook).
 * GAP: composición exacta no publicada; campos derivados del contrato external/pasarela-webhook-resultados.md.
 */
public record SettlementResultCommand(String externalReference, String gatewayStatus, String statusDetail) {
}
