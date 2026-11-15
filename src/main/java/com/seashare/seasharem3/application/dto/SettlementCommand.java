package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

import java.util.UUID;

/**
 * Comando de liquidacion publicado para UC07/UC08: reserva, desencadenante/alcance, dispute_id
 * cuando aplica e identidad de operacion. No acepta un monto arbitrario.
 * Fuente: UC10 plan T005/T005a y contratos de liquidacion.
 */
public record SettlementCommand(ReservationId reservationId, SettlementTrigger trigger, SettlementScope scope,
                                UUID disputeId, String operationKey) {
}
