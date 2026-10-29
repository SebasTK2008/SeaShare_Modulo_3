package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import java.math.BigDecimal;

/**
 * Cuerpo de la solicitud de cobro a Mercado Pago ({@code POST /v1/payments}).
 *
 * <p>El monto es un unico importe decimal que incluye alquiler, seguro y deposito (RF-015) y viaja
 * como numero, no como texto: el adaptador usa su propio {@code JsonMapper} porque el de la API
 * serializa los decimales como cadena (contracts/README §3.1).</p>
 *
 * <p>Los componentes usan {@code camelCase} y el mapper del adaptador aplica la estrategia
 * {@code SNAKE_CASE}: {@code transaction_amount}, {@code payment_method_id},
 * {@code external_reference}.</p>
 *
 * <p>Fuente: UC05 plan T012; contrato {@code pasarela-comando-cobro.md} §2.</p>
 */
public record MercadoPagoPaymentRequest(BigDecimal transactionAmount, String token, String description,
        int installments, String paymentMethodId, Payer payer, String externalReference) {

    /** Datos del pagador; el contrato exige {@code payer.email}. */
    public record Payer(String email) {
    }
}
