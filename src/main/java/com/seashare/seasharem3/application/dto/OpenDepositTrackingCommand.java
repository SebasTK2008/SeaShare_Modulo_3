package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.ReservationId;

/**
 * Comando para abrir el seguimiento del depósito (compare-and-set de deposit_disposition).
 * Fuente: UC07 plan T018 (`OpenDepositTrackingUseCase.open`) y UC08 plan (dueño, D-34).
 * GAP: composición no publicada; se deriva reserva + identidad de operación.
 */
public record OpenDepositTrackingCommand(ReservationId reservationId, String operationKey) {
}
