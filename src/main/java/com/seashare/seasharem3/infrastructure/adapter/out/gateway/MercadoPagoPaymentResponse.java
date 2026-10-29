package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

/**
 * Respuesta de Mercado Pago a la solicitud de cobro ({@code POST /v1/payments}) y a la consulta del
 * pago ({@code GET /v1/payments/{id}}).
 *
 * <p>{@code id} es el identificador del pago en la pasarela: se guarda en
 * {@code charge_intent.external_reference} y con el se asocian el webhook y la conciliacion.
 * {@code status_detail} es el detalle tecnico del estado y puede venir ausente.</p>
 *
 * <p>Fuente: UC05 plan T012 y T021; contrato {@code pasarela-comando-cobro.md} §4.</p>
 */
public record MercadoPagoPaymentResponse(Long id, String status, String statusDetail) {
}
