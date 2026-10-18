package com.seashare.seasharem3.domain.exception;

/**
 * Falla controlada al obtener/estimar la tarifa base (UC02).
 * Fuente: UC02 plan T014; SPEC RNF-003 y casos extremos de UC01/UC02/UC03.
 */
public class BaseRateException extends DomainException {

    /**
     * Motivo de la falla con su marca transitorio/permanente.
     * Fuente: UC02 plan T014 (transitorios: FLEET_UNAVAILABLE y DYNAMIC_RATE_NOT_CONFIGURED;
     * permanente: BASE_RATE_NOT_AVAILABLE). [CONV] enum anidado (el plan referencia un único archivo).
     */
    public enum BaseRateFailureReason {
        FLEET_UNAVAILABLE(true),
        DYNAMIC_RATE_NOT_CONFIGURED(true),
        BASE_RATE_NOT_AVAILABLE(false);

        private final boolean transientFailure;

        BaseRateFailureReason(boolean transientFailure) {
            this.transientFailure = transientFailure;
        }

        public boolean isTransient() {
            return transientFailure;
        }
    }

    private final BaseRateFailureReason reason;

    public BaseRateException(BaseRateFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BaseRateException(BaseRateFailureReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public BaseRateFailureReason reason() {
        return reason;
    }

    /** {@code true} si el llamador (listener AMQP) debe reintentar según README §4.4. Fuente: UC02 T014. */
    public boolean isTransient() {
        return reason.isTransient();
    }
}
