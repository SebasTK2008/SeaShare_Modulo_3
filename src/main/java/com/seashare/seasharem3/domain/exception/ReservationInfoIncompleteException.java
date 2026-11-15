package com.seashare.seasharem3.domain.exception;

/**
 * La información de reserva existe pero le faltan datos obligatorios para la operación (catálogo README §3.4:
 * {@code RESERVATION_INFO_INCOMPLETE}, HTTP 422). Fuente: UC04/UC05/UC06 (p. ej. montos calculados ausentes o
 * parámetros congelados no resueltos). Núcleo compartido [CONV]: los servicios la lanzan y
 * {@code GlobalExceptionHandler} la traduce centralmente.
 */
public class ReservationInfoIncompleteException extends DomainException {

    public ReservationInfoIncompleteException(String message) {
        super(message);
    }
}
