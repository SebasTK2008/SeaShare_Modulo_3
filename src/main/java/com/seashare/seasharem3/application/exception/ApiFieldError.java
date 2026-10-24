package com.seashare.seasharem3.application.exception;

/**
 * Violación de validación reportada en el campo {@code errors} del Problem Details
 * ({@code [{"field": "...", "message": "..."}]}, contracts/README §3.3).
 * [CONV] núcleo compartido.
 */
public record ApiFieldError(String field, String message) {

    public ApiFieldError {
        field = field == null ? "" : field;
        message = message == null ? "" : message;
    }
}
