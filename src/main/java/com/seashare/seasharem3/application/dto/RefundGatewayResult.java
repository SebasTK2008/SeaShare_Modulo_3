package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/**
 * Resultado técnico del reembolso en la pasarela (= ResultadoReembolsoPasarela [SPEC]).
 * Fuente: UC09 plan T011 y contrato external/pasarela-comando-reembolso.md (status, id, amount).
 * GAP: composición exacta del record no publicada; campos derivados del contrato externo.
 */
public record RefundGatewayResult(String status, String externalId, Money amount) {
}
