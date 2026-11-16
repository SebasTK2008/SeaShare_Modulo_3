package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.infrastructure.adapter.in.web.CorrelationIdFilter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.doc.ApiProblemDetails;
import com.seashare.seasharem3.application.exception.ErrorCode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** Convenciones de documentación compartidas; no modifica peticiones ni respuestas de ejecución. */
@Configuration
public class OpenApiCommonConfig {

    private static final String CORRELATION_COMPONENT = "XCorrelationId";
    private static final String PARAMETER_REF = "#/components/parameters/" + CORRELATION_COMPONENT;
    private static final String HEADER_REF = "#/components/headers/" + CORRELATION_COMPONENT;

    @Bean
    public GlobalOpenApiCustomizer commonApiDocumentation() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            Components components = openApi.getComponents();
            ModelConverters.getInstance().readAll(ApiProblemDetails.class).forEach(components::addSchemas);
            // Ejemplos de documentación alineados con el catálogo real, sin ejecutar manejadores de errores.
            for (ErrorCode code : ErrorCode.values()) {
                components.addExamples(code.name(), new Example().summary(code.title()).value(Map.of(
                        "type", "about:blank", "title", code.title(), "status", code.httpStatus(),
                        "detail", code == ErrorCode.INTERNAL_ERROR ? "An unexpected internal error occurred." : code.title(),
                        "code", code.name(), "retryable", code.retryable())));
            }
            components.addParameters(CORRELATION_COMPONENT, new Parameter()
                    .name(CorrelationIdFilter.HEADER).in("header").required(false)
                    .description("Cadena libre de correlación. Si se omite o está vacía, el servidor genera una.")
                    .schema(new StringSchema()).example("qa-estimates-001"));
            components.addHeaders(CORRELATION_COMPONENT, new Header()
                    .description("Identificador recibido en X-Correlation-Id o generado por el servidor")
                    .schema(new StringSchema()).example("qa-estimates-001"));
            registerProblemResponses(components);

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/")) {
                    return;
                }
                item.readOperations().forEach(operation -> {
                    boolean documented = operation.getParameters() != null
                            && operation.getParameters().stream().anyMatch(parameter ->
                                    PARAMETER_REF.equals(parameter.get$ref())
                                            || ("header".equals(parameter.getIn())
                                            && CorrelationIdFilter.HEADER.equalsIgnoreCase(parameter.getName())));
                    if (!documented) {
                        operation.addParametersItem(new Parameter().$ref(PARAMETER_REF));
                    }
                    if (operation.getResponses() != null) {
                        operation.getResponses().values().forEach(response -> {
                            if (response.get$ref() != null && components.getResponses() != null) {
                                String prefix = "#/components/responses/";
                                if (response.get$ref().startsWith(prefix)) {
                                    response = components.getResponses().get(response.get$ref().substring(prefix.length()));
                                }
                            }
                            documentCorrelationHeader(response);
                        });
                    }
                });
            });
        };
    }

    /** Catálogo de respuestas: conserva las condiciones y ejemplos propios de cada operación. */
    private static void registerProblemResponses(Components components) {
        components.addResponses("InternalError", problemResponse("INTERNAL_ERROR: fallo no previsto", ErrorCode.INTERNAL_ERROR));
        components.addResponses("BatchEstimateBadRequest", problemResponse(
                "VALIDATION_ERROR: cuerpo inválido, IDs nulos/no UUID o campos extra; "
                        + "BATCH_SIZE_EXCEEDED: excede el máximo, sin procesar ningún ID",
                ErrorCode.VALIDATION_ERROR, ErrorCode.BATCH_SIZE_EXCEEDED));
        components.addResponses("BatchEstimateUnavailable", problemResponse(
                "FLEET_UNAVAILABLE o FINANCIAL_PARAMETERS_NOT_CONFIGURED; sin estimaciones parciales",
                ErrorCode.FLEET_UNAVAILABLE, ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED));
        components.addResponses("IndividualEstimateBadRequest", problemResponse(
                "VALIDATION_ERROR: cuerpo o pasajeros inválidos; "
                        + "INVALID_DATE_RANGE: fechas inválidas, inicio pasado o fin anterior al inicio",
                ErrorCode.VALIDATION_ERROR, ErrorCode.INVALID_DATE_RANGE));
        components.addResponses("BaseRateNotAvailable", problemResponse(
                "BASE_RATE_NOT_AVAILABLE: embarcación sin tarifa o desconocida por Flota", ErrorCode.BASE_RATE_NOT_AVAILABLE));
        components.addResponses("IndividualEstimateUnavailable", problemResponse(
                "FLEET_UNAVAILABLE o FINANCIAL_PARAMETERS_NOT_CONFIGURED; sin importes asumidos",
                ErrorCode.FLEET_UNAVAILABLE, ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED));
        components.addResponses("ParametersNotConfigured", problemResponse(
                "PARAMETERS_NOT_CONFIGURED: todavía no existe una versión", ErrorCode.PARAMETERS_NOT_CONFIGURED));
        components.addResponses("FinancialParametersBadRequest", problemResponse(
                "VALIDATION_ERROR: campos ausentes, no decimales, porcentajes fuera de 0–100 o seguro negativo", ErrorCode.VALIDATION_ERROR));
        components.addResponses("ParametersSaveFailed", problemResponse(
                "PARAMETERS_SAVE_FAILED o INTERNAL_ERROR; la configuración anterior se conserva",
                ErrorCode.PARAMETERS_SAVE_FAILED, ErrorCode.INTERNAL_ERROR));
        components.addResponses("FinancialRecordsBadRequest", problemResponse(
                "VALIDATION_ERROR: parámetros inválidos, page < 1, size fuera de 1–100, "
                        + "owner_id ausente para OWNER o date_from posterior a date_to", ErrorCode.VALIDATION_ERROR));
        components.addResponses("PageOutOfRange", problemResponse(
                "PAGE_OUT_OF_RANGE: page > max(total_pages, 1)", ErrorCode.PAGE_OUT_OF_RANGE));
        components.addResponses("InvalidReservationId", problemResponse(
                "VALIDATION_ERROR: identificador inválido", ErrorCode.VALIDATION_ERROR));
        components.addResponses("ChargeIntentNotFound", problemResponse(
                "CHARGE_INTENT_NOT_FOUND: no existe intención de cobro para la reserva", ErrorCode.CHARGE_INTENT_NOT_FOUND));
        components.addResponses("ReservationInfoNotFound", problemResponse(
                "RESERVATION_INFO_NOT_FOUND: UC03 aún no registró información de la reserva", ErrorCode.RESERVATION_INFO_NOT_FOUND));
        components.addResponses("ReservationInfoIncomplete", problemResponse(
                "RESERVATION_INFO_INCOMPLETE: información insuficiente para calcular", ErrorCode.RESERVATION_INFO_INCOMPLETE));
        components.addResponses("FinancialParametersNotConfigured", problemResponse(
                "FINANCIAL_PARAMETERS_NOT_CONFIGURED: faltan los parámetros necesarios para el primer cálculo",
                ErrorCode.FINANCIAL_PARAMETERS_NOT_CONFIGURED));
        components.addResponses("FinancialReportBadRequest", problemResponse(
                "INVALID_PERIOD: periodicidad o período inválido; "
                        + "VALIDATION_ERROR: parámetros ausentes/incorrectos u owner_id requerido para OWNER",
                ErrorCode.INVALID_PERIOD, ErrorCode.VALIDATION_ERROR));
        components.addResponses("FinancialReportExportFailed", problemResponse(
                "INTERNAL_ERROR: fallo al generar el CSV", ErrorCode.INTERNAL_ERROR));
        components.addResponses("GatewayWebhookBadRequest", problemResponse(
                "VALIDATION_ERROR: Content-Type no JSON, cuerpo inválido, action ausente/vacío "
                        + "o data.id/type ausentes en la query (después de validar la firma)", ErrorCode.VALIDATION_ERROR));
        components.addResponses("InvalidGatewaySignature", problemResponse(
                "UNAUTHENTICATED: firma ausente/inválida, x-request-id ausente o secreto no configurado", ErrorCode.UNAUTHENTICATED));
        components.addResponses("GatewayNotificationFailed", problemResponse(
                "INTERNAL_ERROR: fallo antes del acuse, por ejemplo al persistir en outbox; Mercado Pago reintenta", ErrorCode.INTERNAL_ERROR));
    }

    private static ApiResponse problemResponse(String description, ErrorCode... codes) {
        MediaType content = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetails"));
        for (ErrorCode code : codes) {
            content.addExamples(code.name(), new Example().$ref("#/components/examples/" + code.name()));
        }
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", content))
                .addHeaderObject(CorrelationIdFilter.HEADER, new Header().$ref(HEADER_REF));
    }

    private static void documentCorrelationHeader(ApiResponse response) {
        if (response == null || response.get$ref() != null) {
            return;
        }
        boolean documented = response.getHeaders() != null && response.getHeaders().keySet().stream()
                .anyMatch(CorrelationIdFilter.HEADER::equalsIgnoreCase);
        if (!documented) {
            response.addHeaderObject(CorrelationIdFilter.HEADER, new Header().$ref(HEADER_REF));
        }
    }
}
