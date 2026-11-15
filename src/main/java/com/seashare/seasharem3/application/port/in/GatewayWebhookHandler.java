package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;

/**
 * Manejador de un tipo de notificacion del webhook de la pasarela.
 *
 * <p>UC05 implementa {@code payment} (resultado del cobro) y UC09 vuelve a implementar
 * {@code payment} (resultados de sus reembolsos). El despachador ejecuta <em>todos</em> los
 * manejadores registrados para el tipo, no solo el primero (D-UC09-10). Un tipo sin manejador se
 * registra en {@code operational_failure} y se confirma la recepcion.</p>
 */
public interface GatewayWebhookHandler {

    /** Tipo soportado; Mercado Pago solo notifica {@code payment} en esta integracion. */
    String supportedType();

    void handle(GatewayWebhookNotification notification);
}
