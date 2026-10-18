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

    VALIDATION_ERROR(400, false, "Petición inválida"),
    BATCH_SIZE_EXCEEDED(400, false, "Lote demasiado grande"),
    INVALID_DATE_RANGE(400, false, "Rango de fechas inválido"),
    INVALID_PERIOD(400, false, "Período inválido"),
    UNAUTHENTICATED(401, false, "Firma del webhook inválida"),
    RESERVATION_INFO_NOT_FOUND(404, true, "Información de reserva no encontrada"),
    CHARGE_INTENT_NOT_FOUND(404, true, "Intención de cobro no encontrada"),
    PARAMETERS_NOT_CONFIGURED(404, true, "Parámetros financieros no configurados"),
    PAGE_OUT_OF_RANGE(404, false, "Página fuera de rango"),
    METHOD_NOT_ALLOWED(405, false, "Método no permitido"),
    NOT_ACCEPTABLE(406, false, "Representación no aceptable"),
    UNSUPPORTED_MEDIA_TYPE(415, false, "Tipo de contenido no soportado"),
    BASE_RATE_NOT_AVAILABLE(422, false, "Tarifa base no disponible"),
    RESERVATION_INFO_INCOMPLETE(422, false, "Información de reserva incompleta"),
    PARAMETERS_SAVE_FAILED(500, true, "No se pudo guardar la configuración"),
    INTERNAL_ERROR(500, true, "Error interno"),
    FLEET_UNAVAILABLE(503, true, "Servicio de flota no disponible"),
    FINANCIAL_PARAMETERS_NOT_CONFIGURED(503, true, "Parámetros financieros no configurados");

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
