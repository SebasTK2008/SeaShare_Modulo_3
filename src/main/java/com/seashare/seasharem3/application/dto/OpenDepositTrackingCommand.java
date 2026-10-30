package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.DepositDispositionStatus;
import com.seashare.seasharem3.domain.valueobject.DisputeStatus;
import com.seashare.seasharem3.domain.valueobject.ReservationId;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando para abrir o aplicar el desenlace del deposito (compare-and-set de deposit_disposition).
 * Fuente: UC07 plan T018, UC08 plan D-34 y UC09/UC10.
 *
 * <p>{@code target} expresa el desenlace solicitado: HELD al completar la reserva,
 * REFUND_REQUESTED para UC09 y SETTLEMENT_REQUESTED para UC10. {@code disputeId},
 * {@code disputeStatus} y {@code completedAt} son opcionales al aplicar un desenlace sobre una
 * fila existente; al crear la fila se exige {@code completedAt}.</p>
 */
public record OpenDepositTrackingCommand(ReservationId reservationId, String operationKey,
                                         DepositDispositionStatus target, UUID disputeId,
                                         DisputeStatus disputeStatus, Instant completedAt) {
}
