package com.seashare.seasharem3.infrastructure.adapter.in.web.dto.doc;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Modelo exclusivo de OpenAPI; no se utiliza como respuesta en tiempo de ejecución.
 * La respuesta real la construye ApiProblemDetailsFactory con ProblemDetail (RFC 9457).
 * Fuente: contracts/README §3.3. Spring MVC puede añadir instance con la ruta de la petición.
 */
@Schema(name = "ProblemDetails", description = "Error RFC 9457 del módulo, con código estable y política de reintento")
public record ApiProblemDetails(
        @Schema(description = "URI que identifica el tipo de problema", format = "uri", example = "about:blank",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
        @Schema(description = "Resumen del catálogo de errores", example = "Invalid request",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(description = "Código HTTP de la respuesta", example = "400",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(description = "Explicación de esta ocurrencia", example = "The request body does not comply with the contract.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        @Schema(description = "URI de la ocurrencia, cuando la añade el framework", format = "uri-reference",
                example = "/api/v1/estimates/batch", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String instance,
        @Schema(description = "Código estable del catálogo de errores", example = "VALIDATION_ERROR",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Indica si repetir la misma petición sin cambios puede tener éxito más adelante",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean retryable,
        @Schema(description = "Detalle por campo cuando está disponible en errores de validación; se omite si no hay detalles",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<ProblemFieldError> errors) {

    @Schema(name = "ProblemFieldError", description = "Detalle de un campo inválido")
    public record ProblemFieldError(
            @Schema(description = "Nombre del campo inválido", example = "boat_ids") String field,
            @Schema(description = "Motivo del error", example = "boat_ids is required") String message) {
    }
}
