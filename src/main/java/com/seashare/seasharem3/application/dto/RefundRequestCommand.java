package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.RefundTrigger;

import java.util.UUID;

/** Firma publicada por UC09 (plan §Puertos expuestos): disputeId nullable (solo con DISPUTE_REJECTED). */
public record RefundRequestCommand(
        UUID reservationId,
        RefundTrigger trigger,
        UUID disputeId,
        String operationKey
) {
}
