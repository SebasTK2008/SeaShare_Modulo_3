package com.seashare.seasharem3.application.dto;

/**
 * Notificación normalizada del webhook de la pasarela, lista para despacho por {@code type}.
 * Fuente: contrato external/pasarela-webhook-resultados.md §2 (query data.id + type, body action/data.id/type);
 * UC05 plan T019/T020. [CONV] núcleo compartido: el despachador (UC05) la construye y los manejadores
 * de UC05 ({@code payment}), UC09 ({@code refund}) y UC10 ({@code merchant_order}) la consumen.
 *
 * @param type   tipo de recurso notificado ({@code payment}, {@code refund}, {@code merchant_order})
 * @param action acción del evento (p. ej. {@code payment.updated}); puede ser nula si el payload no la trae
 * @param dataId identificador del recurso en la pasarela; asocia la notificación con la intención por
 *               {@code external_reference} (nunca por reserva, monto u hora)
 */
public record GatewayWebhookNotification(String type, String action, String dataId) {
}
