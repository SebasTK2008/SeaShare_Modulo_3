package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;

import java.util.Optional;

/**
 * ACL de la pasarela de liquidacion (UC10).
 * Fuente: UC10 plan T006 y contrato external/pasarela-comando-liquidacion.md.
 * Firma publicada en el plan de UC10: envio y consulta del detalle para el webhook.
 */
public interface SettlementGatewayPort {

    GatewaySettlementResult send(GatewaySettlementRequest request);

    Optional<GatewaySettlementResult> fetch(String externalReference);
}
