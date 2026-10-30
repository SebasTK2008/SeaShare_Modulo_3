package com.seashare.seasharem3.domain.exception;

/**
 * Rechazo de negocio de la pasarela (400 no reintentable: token, collector_id, fondos, plazo).
 * No es una falla de comunicacion: el resultado es definitivo y no se reintenta.
 * Fuente: contratos de reembolso/liquidacion §5.
 */
public class GatewayBusinessRejectionException extends DomainException {

    public GatewayBusinessRejectionException(String message) {
        super(message);
    }

    public GatewayBusinessRejectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
