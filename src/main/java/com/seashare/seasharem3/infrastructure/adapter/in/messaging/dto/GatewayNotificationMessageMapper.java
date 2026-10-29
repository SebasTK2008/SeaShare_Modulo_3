package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Traduce el mensaje de la cola de notificaciones a la notificacion normalizada de la aplicacion.
 * Fuente: UC05 plan T020; precedente de UC03 (mapper del mensaje al dato de aplicacion).
 */
@Component
public class GatewayNotificationMessageMapper {

    public GatewayWebhookNotification toNotification(GatewayNotificationMessage message) {
        Objects.requireNonNull(message, "message");
        return new GatewayWebhookNotification(message.type(), message.action(), message.dataId());
    }
}
