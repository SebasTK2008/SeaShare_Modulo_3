package com.seashare.seasharem3.domain.exception;

/**
 * La reserva no tiene valor total calculado (UC05 RF-009); UC07 la captura y la registra en
 * {@code operational_failure} (UC07 RF-010/RNF-003).
 * Fuente: UC05 plan T004. Núcleo compartido [CONV]: UC05 la lanza y UC07 la consume.
 */
public class ReservationValueNotCalculatedException extends DomainException {

    public ReservationValueNotCalculatedException(String message) {
        super(message);
    }
}
