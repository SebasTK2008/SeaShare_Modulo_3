package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.RefundGatewayRequest;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;

import java.util.Optional;

/**
 * ACL de la pasarela de reembolso (UC09): métodos send y fetchRefund documentados en T005.
 * Fuente: UC09 plan T005 y contrato external/pasarela-comando-reembolso.md.
 * GAP: tipos de firma no publicados; se usan los DTOs del comando/resultado de la pasarela.
 */
public interface RefundGatewayPort {

    RefundGatewayResult send(RefundGatewayRequest request);

    Optional<RefundGatewayResult> fetchRefund(String externalReference);
}
