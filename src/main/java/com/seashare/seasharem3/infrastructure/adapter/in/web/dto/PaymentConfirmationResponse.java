package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.PaymentConfirmationStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.math.BigDecimal;

/**
 * Body del {@code GET /api/v1/reservations/{reservation_id}/payment-confirmation}.
 * Los montos son {@link BigDecimal}, serializados como string decimal de 2 cifras HALF_UP
 * (contracts/README §3.1); los no disponibles viajan como {@code null}.
 *
 * <p>Fuente: UC06 plan T006; contrato {@code rest/UC06-confirmacion-pago.md} §4 [CONV].</p>
 */
public record PaymentConfirmationResponse(
        @JsonProperty("reservation_id") ReservationId reservationId,
        @JsonProperty("status") PaymentConfirmationStatus status,
        @JsonProperty("detail") String detail,
        @JsonProperty("authorized_amount") BigDecimal authorizedAmount,
        @JsonProperty("captured_amount") BigDecimal capturedAmount,
        @JsonProperty("released_amount") BigDecimal releasedAmount,
        @JsonProperty("charged_amount") BigDecimal chargedAmount,
        @JsonProperty("external_reference") String externalReference) {
}
