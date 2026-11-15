package com.seashare.seasharem3.infrastructure.config.web;

import com.seashare.seasharem3.infrastructure.adapter.in.web.CorrelationIdFilter;
import com.seashare.seasharem3.infrastructure.adapter.in.web.dto.doc.ApiProblemDetails;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            components.addParameters(CORRELATION_COMPONENT, new Parameter()
                    .name(CorrelationIdFilter.HEADER).in("header").required(false)
                    .description("Cadena libre de correlación. Si se omite o está vacía, el servidor genera una.")
                    .schema(new StringSchema()).example("qa-estimates-001"));
            components.addHeaders(CORRELATION_COMPONENT, new Header()
                    .description("Identificador recibido en X-Correlation-Id o generado por el servidor")
                    .schema(new StringSchema()).example("qa-estimates-001"));

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
