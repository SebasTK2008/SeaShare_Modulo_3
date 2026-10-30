package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.Money;

/**
 * Resultado tecnico de la dispersion en la pasarela, unico DTO de entrada para registrar el
 * resultado (UC10 T005/T011). {@code statusDetail} y {@code amount} son nullable cuando la
 * respuesta sincrona no los informa; en ese caso el registro usa el monto de la intencion.
 */
public record GatewaySettlementResult(String status, String externalId, String statusDetail, Money amount) {
}
