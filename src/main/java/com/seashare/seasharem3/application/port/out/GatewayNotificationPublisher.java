package com.seashare.seasharem3.application.port.out;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;

/**
 * Publica la notificacion del webhook de la pasarela en la cola interna de notificaciones, dentro
 * de la transaccion del llamador (outbox): la notificacion queda persistida antes de que la
 * pasarela reciba el acuse de recibo.
 *
 * <p>Es un puerto propio de UC05, que tambien lo usan UC09 y UC10 para sus tipos de evento; el
 * enrutado y la serializacion quedan en la capa de infraestructura (general-plan §3.4-2).
 *
 * <p>Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §3.2.</p>
 */
public interface GatewayNotificationPublisher {

    void publish(GatewayWebhookNotification notification);
}
