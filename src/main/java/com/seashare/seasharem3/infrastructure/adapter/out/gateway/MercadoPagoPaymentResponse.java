package com.seashare.seasharem3.infrastructure.adapter.out.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Respuesta de Mercado Pago a la solicitud de cobro ({@code POST /v1/payments}) y a la consulta del
 * pago ({@code GET /v1/payments/{id}}).
 *
 * <p>{@code id} es el identificador del pago en la pasarela: se guarda en
 * {@code charge_intent.external_reference} y con el se asocian el webhook y la conciliacion.
 * {@code status_detail} es el detalle tecnico del estado y puede venir ausente. {@code refunds}
 * contiene los reembolsos del pago y permite resolverlos desde la notificacion {@code type=payment}.</p>
 *
 * <p>Fuente: UC05 plan T012 y T021; UC09 plan T021; contrato {@code pasarela-comando-cobro.md} §4 y
 * contrato {@code pasarela-comando-reembolso.md} §4.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoPaymentResponse(Long id, String status, String statusDetail,
                                        List<MercadoPagoRefundResponse> refunds) {
}
