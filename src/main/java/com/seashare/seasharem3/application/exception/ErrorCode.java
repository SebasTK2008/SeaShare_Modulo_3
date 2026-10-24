package com.seashare.seasharem3.application.exception;

/**
 * Catálogo de códigos de error de los contratos REST (contracts/README §3.4), con su HTTP y
 * marca {@code retryable}. Fuente: contracts/README §3.3–§3.4 y §4 (tabla de decisión).
 * [CONV] núcleo compartido: los servicios de caso de uso lanzan {@link ContractException} con uno
 * de estos códigos y el adaptador web los traduce a Problem Details; ningún UC define códigos nuevos
 * sin pasar antes por la tabla de decisión (§4.2) y el catálogo (§3.4).
 * Excepción: {@link #UNAUTHENTICATED} (401) solo aplica al webhook de la Pasarela (verificación de
 * firma, contracts/README §4.4); no es control de acceso del sistema (general-plan §7.1).
 */
public enum ErrorCode {

    VALIDATION_ERROR(400, false, "Invalid request"),
    BATCH_SIZE_EXCEEDED(400, false, "Batch too large"),
    INVALID_DATE_RANGE(400, false, "Invalid date range"),
    INVALID_PERIOD(400, false, "Invalid period"),
    UNAUTHENTICATED(401, false, "Invalid webhook signature"),
    RESERVATION_INFO_NOT_FOUND(404, true, "Reservation information not found"),
    CHARGE_INTENT_NOT_FOUND(404, true, "Charge intent not found"),
    PARAMETERS_NOT_CONFIGURED(404, true, "Financial parameters not configured"),
    PAGE_OUT_OF_RANGE(404, false, "Page out of range"),
    METHOD_NOT_ALLOWED(405, false, "Method not allowed"),
    NOT_ACCEPTABLE(406, false, "Not acceptable"),
    UNSUPPORTED_MEDIA_TYPE(415, false, "Unsupported media type"),
    BASE_RATE_NOT_AVAILABLE(422, false, "Base rate not available"),
    RESERVATION_INFO_INCOMPLETE(422, false, "Reservation information incomplete"),
    PARAMETERS_SAVE_FAILED(500, true, "Financial parameters save failed"),
    INTERNAL_ERROR(500, true, "Internal error"),
    FLEET_UNAVAILABLE(503, true, "Fleet service unavailable"),
    FINANCIAL_PARAMETERS_NOT_CONFIGURED(503, true, "Financial parameters not configured");

    private final int httpStatus;
    private final boolean retryable;
    private final String title;

    ErrorCode(int httpStatus, boolean retryable, String title) {
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.title = title;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    public String title() {
        return title;
    }
}
