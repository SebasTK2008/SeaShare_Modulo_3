package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.ChargeGatewayRequest;
import com.seashare.seasharem3.application.dto.ChargeGatewayResult;

import java.util.Optional;

/**
 * ACL de la pasarela de cobro (UC05).
 * Fuente: UC05 plan T039 y contrato external/pasarela-comando-cobro.md.
 * GAP: métodos no publicados; send y fetchPayment (consulta del detalle) se derivan del flujo del worker y del webhook.
 */
public interface ChargeGatewayPort {

    ChargeGatewayResult send(ChargeGatewayRequest request);

    Optional<ChargeGatewayResult> fetchPayment(String externalReference);
}
