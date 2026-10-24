package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.ReservationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Firma publicada por UC07 (plan §Puertos expuestos): {@code status} usa el enum compartido
 * {@link ReservationStatus} (10 estados); la operación se deriva de reservationId + status + statusChangedAt.
 * El valor desconocido se rechaza en el adaptador de mensajería y se registra como {@code operational_failure};
 * el servicio solo recibe estados conocidos.
 */
public record ReservationStatusCommand(
        UUID reservationId,
        ReservationStatus status,
        Instant statusChangedAt,
        String paymentTokenRef,
        String paymentMethodType,
        Map<String, String> paymentMetadata
) {
}
