package com.seashare.seasharem3.domain.exception;

/**
 * Falla de comunicación con la pasarela de pago (timeout, red caída, 5xx): no permite concluir que
 * el cobro falló, solo que su resultado es desconocido y debe reintentarse o conciliarse.
 * Fuente: SPEC RNF-003; UC05 plan §Reglas 5; contrato {@code pasarela-comando-cobro.md} §5.
 */
public class GatewayCommunicationException extends DomainException {

    public GatewayCommunicationException(String message) {
        super(message);
    }

    public GatewayCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
