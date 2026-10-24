package com.seashare.seasharem3.domain.exception;

/**
 * Página solicitada fuera del rango disponible (catálogo README §3.4: {@code PAGE_OUT_OF_RANGE}, HTTP 404).
 * Fuente: UC12/UC13 (paginación del informe). Núcleo compartido [CONV]: los servicios la lanzan y
 * {@code GlobalExceptionHandler} la traduce centralmente.
 */
public class InvalidPageException extends DomainException {

    public InvalidPageException(String message) {
        super(message);
    }
}
