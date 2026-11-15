package com.seashare.seasharem3.application.dto;

import java.util.Map;
import java.util.UUID;

/** Firma publicada por UC05 (plan §Puertos expuestos); operationKey derivada de reservationId + statusChangedAt. */
public record ProcessChargeCommand(
        UUID reservationId,
        String paymentTokenRef,
        String paymentMethodType,
        Map<String, String> paymentMetadata,
        String operationKey
) {
}
