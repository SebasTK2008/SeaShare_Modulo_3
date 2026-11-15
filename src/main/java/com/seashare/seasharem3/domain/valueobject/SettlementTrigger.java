package com.seashare.seasharem3.domain.valueobject;

/**
 * Desencadenante de una intención de liquidación.
 * Fuente: UC10 plan (`SettlementCommand.trigger`); UC07 aporta los estados de reserva y UC08 el desenlace de disputa.
 * [CONV] catálogo congelado en el núcleo compartido (los planes no lo publicaban):
 * {@code COMPLETED} y cancelaciones vienen de UC07; {@code DISPUTE_COMPLETED} lo envía UC08 con
 * {@code SettlementScope.DEPOSIT} y {@code dispute_id}.
 */
public enum SettlementTrigger {
    COMPLETED,
    CANCELLED_MODERATE,
    CANCELLED_STRICT,
    DISPUTE_COMPLETED
}
