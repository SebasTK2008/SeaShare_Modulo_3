package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

import java.util.UUID;

/**
 * Comando de dispersion a la pasarela.
 * Fuente: UC10 plan T006 y contrato external/pasarela-comando-liquidacion.md.
 * {@code collectorId} es el identificador numerico de la cuenta de Mercado Pago del Propietario.
 */
public record GatewaySettlementRequest(long collectorId, UUID reservationId, Money amount,
                                       String externalReference, String operationKey) {
}
