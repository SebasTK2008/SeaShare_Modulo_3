package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Body del {@code GET /api/v1/reservations/{reservation_id}/payment-confirmation}.
 * Los montos son {@link BigDecimal}, serializados como string decimal de 2 cifras HALF_UP
 * (contracts/README §3.1); los no disponibles viajan como {@code null}.
 *
 * <p>Fuente: UC06 plan T006; contrato {@code rest/UC06-confirmacion-pago.md} §4 [CONV].</p>
 */
@Schema(description = "Estado vigente del cobro y datos disponibles de la intención persistida; no consulta a la pasarela",
        requiredProperties = {"reservation_id", "status", "detail", "authorized_amount", "captured_amount",
                "released_amount", "charged_amount", "external_reference"})
public record PaymentConfirmationResponse(
        @Schema(description = "Reserva consultada", implementation = String.class, type = "string", format = "uuid",
                example = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")
        @JsonProperty("reservation_id") ReservationId reservationId,
        @Schema(description = "Estado del cobro; UNKNOWN indica resultado todavía desconocido",
                allowableValues = {"IN_PROCESS", "APPROVED", "REJECTED", "CANCELLED", "EXPIRED", "UNKNOWN"}, example = "APPROVED")
        @JsonProperty("status") PaymentConfirmationStatus status,
        @Schema(description = "Detalle del estado si está disponible, o null", types = {"string", "null"}, nullable = true)
        @JsonProperty("detail") String detail,
        @Schema(description = "Monto autorizado en COP; string de 2 decimales o null si no está disponible",
                implementation = String.class, type = "string", types = {"string", "null"}, nullable = true, example = "990000.00")
        @JsonProperty("authorized_amount") BigDecimal authorizedAmount,
        @Schema(description = "Monto capturado en COP; string de 2 decimales o null si no está disponible",
                implementation = String.class, type = "string", types = {"string", "null"}, nullable = true, example = "990000.00")
        @JsonProperty("captured_amount") BigDecimal capturedAmount,
        @Schema(description = "Suma neta liberada de reembolsos confirmados por UC09, en COP; null si no hay reembolsos confirmados",
                implementation = String.class, type = "string", types = {"string", "null"}, nullable = true, example = "50000.00")
        @JsonProperty("released_amount") BigDecimal releasedAmount,
        @Schema(description = "Monto cobrado en COP; string de 2 decimales o null si no está disponible",
                implementation = String.class, type = "string", types = {"string", "null"}, nullable = true, example = "990000.00")
        @JsonProperty("charged_amount") BigDecimal chargedAmount,
        @Schema(description = "Referencia externa de la pasarela, o null", types = {"string", "null"}, nullable = true,
                example = "pg-8841")
        @JsonProperty("external_reference") String externalReference) {
}
