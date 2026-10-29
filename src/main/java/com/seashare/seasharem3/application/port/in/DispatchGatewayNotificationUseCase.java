package com.seashare.seasharem3.application.port.in;

import com.seashare.seasharem3.application.dto.GatewayWebhookNotification;

/**
 * Puerto de entrada del webhook de la pasarela: recibe la notificacion ya verificada y
 * normalizada, la persiste de forma durable y la despacha por {@code type}.
 *
 * <p>El adaptador web solo invoca este puerto; un tipo sin manejador se registra en
 * {@code operational_failure} y se confirma la recepcion sin reintentos.
 *
 * <p>Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §3.</p>
 */
public interface DispatchGatewayNotificationUseCase {

    void dispatch(GatewayWebhookNotification notification);
}
