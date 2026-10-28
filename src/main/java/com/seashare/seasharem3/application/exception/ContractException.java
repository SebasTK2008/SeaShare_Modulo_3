package com.seashare.seasharem3.application.exception;

import java.util.List;

/**
 * Error de contrato controlado que un servicio de caso de uso lanza para que el adaptador web lo
 * convierta en Problem Details con el {@code code} del catálogo (contracts/README §3.3–§3.4).
 * La capa de aplicación no conoce HTTP: solo elige el {@link ErrorCode}.
 * [CONV] núcleo compartido.
 */
public class ContractException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiFieldError> errors;

    public ContractException(ErrorCode code, String detail) {
        this(code, detail, List.of(), null);
    }

    public ContractException(ErrorCode code, String detail, List<ApiFieldError> errors) {
        this(code, detail, errors, null);
    }

    public ContractException(ErrorCode code, String detail, Throwable cause) {
        this(code, detail, List.of(), cause);
    }

    public ContractException(ErrorCode code, String detail, List<ApiFieldError> errors, Throwable cause) {
        super(detail, cause);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode code() {
        return code;
    }

    /** Errores por campo; solo se serializan en {@link ErrorCode#VALIDATION_ERROR} (contracts/README §3.3). */
    public List<ApiFieldError> errors() {
        return errors;
    }
}
