package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;
import com.seashare.seasharem3.application.port.out.SettlementGatewayPort;

import java.util.Objects;

/**
 * Libro interno simulado de liquidaciones (UC10).
 *
 * <p>No contacta a Mercado Pago: registra la liquidacion en el libro interno y devuelve una
 * referencia determinista derivada de la clave idempotente del intento. Asi un reintento del worker
 * obtiene la misma referencia y el registro inmutable sigue siendo unico por intencion
 * (D-UC10-10). El resultado siempre es {@code approved}; no existe una pasarela real que pueda
 * rechazar la operacion en esta entrega.</p>
 */
public class SimulatedSettlementGatewayAdapter implements SettlementGatewayPort {

    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_DETAIL = "simulated internal settlement";
    private static final String REFERENCE_PREFIX = "sim-settlement-";

    @Override
    public GatewaySettlementResult send(GatewaySettlementRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.idempotencyKey(), "request.idempotencyKey");
        String externalId = REFERENCE_PREFIX + request.idempotencyKey();
        return new GatewaySettlementResult(STATUS_APPROVED, externalId, STATUS_DETAIL, request.amount());
    }
}
