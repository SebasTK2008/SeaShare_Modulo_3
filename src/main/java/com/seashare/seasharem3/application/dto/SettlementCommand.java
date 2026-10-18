package com.seashare.seasharem3.application.dto;

import com.seashare.seasharem3.domain.valueobject.IdempotencyKey;
import com.seashare.seasharem3.domain.valueobject.ReservationId;
import com.seashare.seasharem3.domain.valueobject.SettlementScope;
import com.seashare.seasharem3.domain.valueobject.SettlementTrigger;

import java.util.UUID;

/**
 * Comando de liquidación publicado para UC07/UC08: reserva, desencadenante/alcance, dispute_id cuando aplica e identidad de idempotencia;
 * no acepta un monto arbitrario.
 * Fuente: UC10 plan T005/T005a y §Firmas referenciadas.
 * GAP: composición exacta del record no publicada; tipos de los campos derivados.
 */
public record SettlementCommand(ReservationId reservationId, SettlementTrigger trigger, SettlementScope scope,
                                UUID disputeId, IdempotencyKey idempotencyKey) {
}
