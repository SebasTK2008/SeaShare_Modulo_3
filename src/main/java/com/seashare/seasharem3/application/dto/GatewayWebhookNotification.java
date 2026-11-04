package com.seashare.seasharem3.application.dto;

/**
 * Notificacion del webhook de la pasarela ya normalizada.
 *
 * <p>Mercado Pago solo notifica el topic real {@code payment} en esta integracion; UC05 lo consume
 * para el resultado del cobro y UC09 para los reembolsos del pago (D-UC09-10).</p>
 *
 * @param type   tipo de recurso notificado ({@code payment})
 * @param action accion informada por la pasarela (por ejemplo {@code payment.updated})
 * @param dataId identificador del pago notificado
 */
public record GatewayWebhookNotification(String type, String action, String dataId) {
}
