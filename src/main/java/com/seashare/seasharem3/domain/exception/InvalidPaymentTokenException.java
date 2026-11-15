package com.seashare.seasharem3.domain.exception;

/**
 * Token del medio de pago inválido o rechazado por la pasarela (UC05 RF-013 / 400 no reintentable);
 * UC07 la captura y la registra en {@code operational_failure}.
 * Fuente: UC05 plan T004. Núcleo compartido [CONV]: UC05 la lanza y UC07 la consume.
 */
public class InvalidPaymentTokenException extends DomainException {

    public InvalidPaymentTokenException(String message) {
        super(message);
    }

    public InvalidPaymentTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
