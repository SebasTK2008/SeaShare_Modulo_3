package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;

/**
 * Contrato de los manejadores del webhook de la pasarela, despachados por {@code type}.
 * Fuente: contrato external/pasarela-webhook-resultados.md §3; UC05 plan (receptor, firma y despachador
 * de UC05) y planes de UC09/UC10 (registran sus manejadores). [CONV] núcleo compartido.
 *
 * <p>UC05 implementa {@code payment}, UC09 {@code refund} y UC10 {@code merchant_order}. El despachador
 * (adaptador de UC05) solo invoca este puerto; si no hay manejador para el tipo, responde {@code 200} y
 * registra el evento en {@code operational_failure} sin reintentos.
 */
public interface GatewayWebhookHandler {

    /** Tipo soportado ({@code payment}, {@code refund} o {@code merchant_order}). */
    String supportedType();

    /**
     * Procesa la notificación. La asociación con la intención es exclusivamente por
     * {@code external_reference == dataId}; sin intención asociada registra el evento en
     * {@code operational_failure} y confirma recepción sin crear registros inmutables.
     */
    void handle(GatewayWebhookNotification notification);
}
