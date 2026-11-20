package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body del {@code POST /api/v1/reservations/{reservation_id}/calculated-value}: el desglose completo
 * de la reserva [SPEC RF-007]. Los montos son {@link Money}, serializado por {@code MoneySerializer}
 * como string decimal de 2 decimales HALF_UP (contracts/README §3.1).
 *
 * Fuente: UC04 plan T012; contrato {@code rest/UC04-valor-calculado-reserva.md} §4 [CONV].
 */
@Schema(description = "Desglose calculado y registrado de la reserva, incluido el depósito de garantía",
        requiredProperties = {"reservation_id", "rental_amount", "insurance_amount", "guarantee_deposit_amount", "total_amount"})
public record ReservationValueResponse(
        @Schema(description = "Reserva calculada", implementation = String.class, type = "string", format = "uuid",
                example = "b7d0e2a1-6c44-4f1b-8a9d-3e5f7a1c9b10")
        @JsonProperty("reservation_id") ReservationId reservationId,
        @Schema(description = "Alquiler bruto en COP; string de 2 decimales", implementation = String.class,
                type = "string", example = "900000.00")
        @JsonProperty("rental_amount") Money rentalAmount,
        @Schema(description = "Seguro náutico en COP; string de 2 decimales", implementation = String.class,
                type = "string", example = "60000.00")
        @JsonProperty("insurance_amount") Money insuranceAmount,
        @Schema(description = "Depósito de garantía en COP; string de 2 decimales", implementation = String.class,
                type = "string", example = "30000.00")
        @JsonProperty("guarantee_deposit_amount") Money guaranteeDepositAmount,
        @Schema(description = "Alquiler + seguro + depósito, en COP; string de 2 decimales", implementation = String.class,
                type = "string", example = "990000.00")
        @JsonProperty("total_amount") Money totalAmount) {
}
