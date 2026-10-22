package com.seashare.seasharem3.domain.exception;

/**
 * No existe la intención de cobro referenciada (catálogo README §3.4: {@code CHARGE_INTENT_NOT_FOUND},
 * HTTP 404 {@code retryable=true} porque la intención se publica de forma asíncrona).
 * Fuente: UC06/UC09/UC10 (consulta del cobro original). Núcleo compartido [CONV]: los servicios la lanzan
 * y {@code GlobalExceptionHandler} la traduce centralmente.
 */
public class ChargeIntentNotFoundException extends DomainException {

    public ChargeIntentNotFoundException(String message) {
        super(message);
    }
}
