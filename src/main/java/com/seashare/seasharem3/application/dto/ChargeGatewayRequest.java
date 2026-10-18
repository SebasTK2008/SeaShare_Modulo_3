package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/**
 * Solicitud de cobro a la pasarela (= SolicitudCobroPasarela [SPEC]).
 * Fuente: UC05 plan T012 y contrato external/pasarela-comando-cobro.md.
 * GAP: la composición exacta del record no está publicada; los campos se derivan del contrato externo.
 */
public record ChargeGatewayRequest(Money transactionAmount, String token, String description, int installments,
                                   String paymentMethodId, String payerEmail, String externalReference) {
}
