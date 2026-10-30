package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.RefundGatewayRequest;
import com.seashare.seasharem3.application.dto.RefundGatewayResult;

import java.util.Optional;

/**
 * ACL de la pasarela de reembolso (UC09).
 * Fuente: UC09 plan T005/T011/T022 y contrato external/pasarela-comando-reembolso.md.
 *
 * <p>La consulta de detalle recibe las dos referencias porque Mercado Pago expone el reembolso
 * dentro del pago original: {@code GET /v1/payments/{chargeReference}/refunds/{refundReference}}.</p>
 */
public interface RefundGatewayPort {

    RefundGatewayResult send(RefundGatewayRequest request);

    Optional<RefundGatewayResult> fetchRefund(String chargeReference, String refundReference);
}
