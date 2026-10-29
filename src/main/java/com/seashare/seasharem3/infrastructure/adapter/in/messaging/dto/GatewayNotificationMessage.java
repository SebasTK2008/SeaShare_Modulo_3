package com.seashare.seasharem3.infrastructure.adapter.in.messaging.dto;

/**
 * Cuerpo de la notificacion del webhook de la pasarela publicada por UC05 en la cola interna
 * {@code finance.gateway-notification.v1}.
 *
 * <p>Los componentes usan {@code camelCase} y el {@code JsonMapper} de Spring Boot aplica la
 * estrategia {@code SNAKE_CASE} del contrato: {@code type}, {@code action} y {@code data_id}.</p>
 *
 * <p>Fuente: UC05 plan T019; contrato {@code pasarela-webhook-resultados.md} §2.</p>
 */
public record GatewayNotificationMessage(String type, String action, String dataId) {
}
