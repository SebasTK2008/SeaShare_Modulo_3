package com.seashare.seasharem3.domain.exception;

/** Excepción base del dominio. Fuente: general-plan §3.3 y §3.6. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
