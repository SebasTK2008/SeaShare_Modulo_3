package com.seashare.seasharem3.domain.exception;

/**
 * Recurso inexistente en la pasarela (404). No es una falla de comunicación ni un veredicto de
 * negocio: el evento se registra para revisión y no se reintenta.
 * Fuente: contrato {@code pasarela-comando-reembolso.md} §4.
 */
public class GatewayResourceNotFoundException extends DomainException {

    public GatewayResourceNotFoundException(String message) {
        super(message);
    }

    public GatewayResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
