package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;

import java.util.Optional;

/**
 * ACL de la pasarela de liquidación (UC10).
 * Fuente: UC10 plan T006 y contrato external/pasarela-comando-liquidacion.md.
 * GAP: métodos no publicados; se derivan envío y consulta del detalle (webhook).
 */
public interface SettlementGatewayPort {

    GatewaySettlementResult send(GatewaySettlementRequest request);

    Optional<GatewaySettlementResult> fetch(String externalReference);
}
