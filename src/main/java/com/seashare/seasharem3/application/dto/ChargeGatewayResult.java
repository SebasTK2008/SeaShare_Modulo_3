package com.seashare.seasharem3.application.dto;

/**
 * Resultado técnico del cobro en la pasarela (= ResultadoCobroPasarela [SPEC]).
 * Fuente: UC05 plan T012 y contrato external/pasarela-comando-cobro.md (status, id, status_detail).
 * GAP: la composición exacta del record no está publicada; los campos se derivan del contrato externo.
 */
public record ChargeGatewayResult(String status, String externalId, String statusDetail) {
}
