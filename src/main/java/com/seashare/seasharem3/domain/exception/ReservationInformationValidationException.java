package com.seashare.seasharem3.domain.exception;

/**
 * La solicitud de UC03 no cumple el contrato o los invariantes de RF-005 (equivale a E2):
 * fechas invertidas, pasajeros fuera de rango o campos obligatorios ausentes.
 *
 * <p>Es un fallo permanente: el listener lo registra en {@code operational_failure} y confirma el
 * mensaje sin reintentos (contracts/README §4.4). Fuente: UC03 plan T009.</p>
 */
public class ReservationInformationValidationException extends DomainException {

    public ReservationInformationValidationException(String message) {
        super(message);
    }

    public ReservationInformationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
