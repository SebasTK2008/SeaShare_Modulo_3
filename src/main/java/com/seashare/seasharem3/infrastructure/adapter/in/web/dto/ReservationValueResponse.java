package com.seashare.seasharem3.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seashare.seasharem3.domain.valueobject.Money;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

/**
 * Body del {@code POST /api/v1/reservations/{reservation_id}/calculated-value}: el desglose completo
 * de la reserva [SPEC RF-007]. Los montos son {@link Money}, serializado por {@code MoneySerializer}
 * como string decimal de 2 decimales HALF_UP (contracts/README §3.1).
 *
 * Fuente: UC04 plan T012; contrato {@code rest/UC04-valor-calculado-reserva.md} §4 [CONV].
 */
public record ReservationValueResponse(
        @JsonProperty("reservation_id") ReservationId reservationId,
        @JsonProperty("rental_amount") Money rentalAmount,
        @JsonProperty("insurance_amount") Money insuranceAmount,
        @JsonProperty("guarantee_deposit_amount") Money guaranteeDepositAmount,
        @JsonProperty("total_amount") Money totalAmount) {
}
