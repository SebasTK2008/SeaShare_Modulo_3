package com.seashare.seasharem3.domain.exception;

/**
 * UC02 no devolvió tarifa base para la embarcación solicitada (equivale a E6).
 *
 * <p>Se modela como una especialización permanente de {@link BaseRateException}: el listener la
 * registra en {@code operational_failure} y confirma el mensaje sin reintentos. Fuente: UC03 plan
 * T009 y D-UC03-04.</p>
 */
public class BaseRateUnavailableException extends BaseRateException {

    public BaseRateUnavailableException(String message) {
        super(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, message);
    }

    public BaseRateUnavailableException(String message, Throwable cause) {
        super(BaseRateFailureReason.BASE_RATE_NOT_AVAILABLE, message, cause);
    }
}
