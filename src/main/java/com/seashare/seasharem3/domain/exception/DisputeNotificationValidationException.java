package com.seashare.seasharem3.domain.exception;

/**
 * La notificacion de disputa no cumple el contrato de UC08: falta un campo obligatorio, el estado no
 * es canonico o el cuerpo no tiene la forma esperada.
 *
 * <p>Es un fallo permanente: el listener lo registra en {@code operational_failure} y confirma el
 * mensaje sin reintentos (contracts/README §4.4). Fuente: UC08 plan T006/T010 y reglas 1-2.</p>
 */
public class DisputeNotificationValidationException extends DomainException {

    public DisputeNotificationValidationException(String message) {
        super(message);
    }

    public DisputeNotificationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
