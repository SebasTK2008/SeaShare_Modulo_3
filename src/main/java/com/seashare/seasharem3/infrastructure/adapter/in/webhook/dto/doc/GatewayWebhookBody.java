package com.seashare.seasharem3.infrastructure.adapter.in.webhook.dto.doc;

import io.swagger.v3.oas.annotations.media.Schema;

/** Solo documentación: el controller sigue leyendo el cuerpo original como String con Jackson 3. */
@Schema(description = "Notificación JSON de Mercado Pago. Solo action se lee del cuerpo; los demás campos se aceptan "
        + "y se ignoran. data.id y type se toman exclusivamente de la query, aunque también figuren en el cuerpo.",
        requiredProperties = {"action"}, additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public record GatewayWebhookBody(
        @Schema(description = "Acción notificada, no vacía. No determina el resultado financiero; este se consulta de forma asíncrona",
                example = "payment.updated")
        String action) {
}
