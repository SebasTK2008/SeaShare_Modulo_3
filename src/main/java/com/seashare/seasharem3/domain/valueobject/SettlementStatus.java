package com.seashare.seasharem3.domain.valueobject;

/**
 * Estados internos de una intencion de liquidacion. {@code FAILED} solo se usa como resultado de
 * una solicitud que no pudo registrarse; nunca se persiste como estado de una intencion.
 * Fuente: general-plan §8; UC10 plan D-UC10-14.
 */
public enum SettlementStatus {
    PENDING_SEND,
    IN_PROCESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    COMMUNICATION_ERROR,
    FAILED
}
