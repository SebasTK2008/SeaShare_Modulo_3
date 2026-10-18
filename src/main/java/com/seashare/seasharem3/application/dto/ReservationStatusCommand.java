package com.seashare.seasharem3.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Firma publicada por UC07 (plan §Puertos expuestos): status viaja crudo; la operación se deriva de reservationId + status + statusChangedAt. */
public record ReservationStatusCommand(
        UUID reservationId,
        String status,
        Instant statusChangedAt,
        String paymentTokenRef,
        String paymentMethodType,
        Map<String, String> paymentMetadata
) {
}
