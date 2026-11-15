package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GatewaySettlementRequest;
import com.seashare.seasharem3.application.dto.GatewaySettlementResult;

/**
 * ACL del libro de liquidaciones (UC10).
 * Fuente: UC10 plan T006 y contrato external/pasarela-comando-liquidacion.md.
 *
 * <p>La implementacion de esta entrega es un libro interno simulado: no hay consulta remota ni
 * dispersion real en Mercado Pago. El puerto solo conserva el envio que necesita el worker; la
 * conciliacion es interna (D-UC10-10).</p>
 */
public interface SettlementGatewayPort {

    GatewaySettlementResult send(GatewaySettlementRequest request);
}
