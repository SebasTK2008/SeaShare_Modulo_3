package com.seashare.seasharem3.infrastructure.adapter.in.webhook.dto.doc;

import io.swagger.v3.oas.annotations.media.Schema;

/** Solo documentación: la respuesta real sigue siendo el Map construido por GatewayWebhookController. */
@Schema(description = "Acuse de recepción; no implica que el cobro o reembolso haya sido aprobado",
        requiredProperties = {"status"})
public record GatewayWebhookAcknowledgement(
        @Schema(description = "Notificación recibida", allowableValues = {"received"}, example = "received") String status) {
}
