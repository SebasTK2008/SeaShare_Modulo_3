package com.seashare.seasharem3.domain.exception;

/**
 * No existe la información de reserva consultada (catálogo README §3.4:
 * {@code RESERVATION_INFO_NOT_FOUND}, HTTP 404 {@code retryable=true} porque la creación es asíncrona).
 * Fuente: UC04/UC05/UC06 (cobro y valor calculado dependen del upsert de UC03). Núcleo compartido [CONV]:
 * los servicios la lanzan y {@code GlobalExceptionHandler} la traduce centralmente.
 */
public class ReservationInfoNotFoundException extends DomainException {

    public ReservationInfoNotFoundException(String message) {
        super(message);
    }
}
