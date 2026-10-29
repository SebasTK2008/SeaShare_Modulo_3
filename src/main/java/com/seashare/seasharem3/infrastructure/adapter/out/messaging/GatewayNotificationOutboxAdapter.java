package com.seashare.seasharem3.infrastructure.adapter.out.messaging;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import com.seashare.seasharem3.application.port.out.GatewayNotificationPublisher;
import com.seashare.seasharem3.application.port.out.OutboxPort;
import com.seashare.seasharem3.infrastructure.config.amqp.AmqpNames;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica la notificacion del webhook de la pasarela en la cola interna de notificaciones mediante
 * el outbox transaccional del nucleo: la notificacion queda en {@code outbox_message} dentro de la
 * transaccion del llamador y el relay la entrega al worker despues del acuse de recibo.
 *
 * <p>El cuerpo es el JSON de la notificacion con la forma snake_case de los contratos
 * ({@code type}, {@code action}, {@code data_id}).</p>
 *
 * <p>Fuente: UC05 plan T019; contracts/README §3.1, §3.5 y §4.4.</p>
 */
@Component
public class GatewayNotificationOutboxAdapter implements GatewayNotificationPublisher {

    private final OutboxPort outbox;
    private final JsonMapper jsonMapper;

    public GatewayNotificationOutboxAdapter(OutboxPort outbox, JsonMapper jsonMapper) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(GatewayWebhookNotification notification) {
        outbox.publish(AmqpNames.EXCHANGE_COMMANDS, AmqpNames.ROUTING_GATEWAY_NOTIFICATION,
                jsonMapper.writeValueAsString(notification));
    }
}
