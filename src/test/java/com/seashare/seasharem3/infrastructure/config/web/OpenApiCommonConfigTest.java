package com.seashare.seasharem3.infrastructure.config.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiCommonConfigTest {

    private final GlobalOpenApiCustomizer customizer = new OpenApiCommonConfig().commonApiDocumentation();

    @Test
    void registersErrorSchemasAndOptionalCorrelationParameterWithoutPaths() {
        new ApplicationContextRunner().withUserConfiguration(OpenApiCommonConfig.class).run(context -> {
            assertThat(context).hasSingleBean(GlobalOpenApiCustomizer.class);
            OpenAPI openApi = new OpenAPI();
            context.getBean(GlobalOpenApiCustomizer.class).customise(openApi);

            Components components = openApi.getComponents();
            assertThat(components.getSchemas()).containsKeys("ProblemDetails", "ProblemFieldError");
            Schema<?> problem = components.getSchemas().get("ProblemDetails");
            assertThat(problem.getProperties()).containsKeys(
                    "type", "title", "status", "detail", "instance", "code", "retryable", "errors");
            assertThat(problem.getRequired()).containsExactlyInAnyOrder(
                    "type", "title", "status", "detail", "code", "retryable");
            assertThat(problem.getProperties().get("errors").getItems().get$ref())
                    .isEqualTo("#/components/schemas/ProblemFieldError");
            Parameter parameter = components.getParameters().get("XCorrelationId");
            assertThat(parameter.getName()).isEqualTo("X-Correlation-Id");
            assertThat(parameter.getIn()).isEqualTo("header");
            assertThat(parameter.getRequired()).isFalse();
            assertThat(parameter.getSchema().getType()).isEqualTo("string");
            assertThat(parameter.getSchema().getFormat()).isNull();
            assertThat(components.getHeaders().get("XCorrelationId").getSchema().getType()).isEqualTo("string");
        });
    }

    @Test
    void documentsCorrelationOnAllExistingResponsesWithoutAddingErrorStatuses() {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("OK"))
                .addApiResponse("400", new ApiResponse().description("Invalid request")));
        Operation health = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("Health")));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/estimates/batch", new PathItem().post(operation))
                .addPathItem("/actuator/health", new PathItem().get(health)));

        customizer.customise(openApi);
        customizer.customise(openApi);

        assertThat(operation.getParameters()).singleElement()
                .satisfies(parameter -> assertThat(parameter.get$ref())
                        .isEqualTo("#/components/parameters/XCorrelationId"));
        assertThat(operation.getResponses()).containsOnlyKeys("200", "400");
        operation.getResponses().values().forEach(response ->
                assertThat(response.getHeaders().get("X-Correlation-Id").get$ref())
                        .isEqualTo("#/components/headers/XCorrelationId"));
        assertThat(health.getParameters()).isNull();
        assertThat(health.getResponses().get("200").getHeaders()).isNull();
    }

    @Test
    void preservesExistingDocumentationAndResolvesReusableResponses() {
        Parameter parameter = new Parameter().in("header").name("x-correlation-id")
                .description("Specific parameter");
        Header header = new Header().description("Specific header");
        ApiResponse reusable = new ApiResponse().description("Reusable response");
        Operation operation = new Operation().addParametersItem(parameter)
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().addHeaderObject("x-correlation-id", header))
                        .addApiResponse("400", new ApiResponse().$ref("#/components/responses/BadRequest")));
        OpenAPI openApi = new OpenAPI().components(new Components()
                        .addSchemas("Existing", new StringSchema()).addResponses("BadRequest", reusable))
                .paths(new Paths().addPathItem("/api/v1/example", new PathItem().get(operation)));

        customizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsKey("Existing");
        assertThat(operation.getParameters()).containsExactly(parameter);
        assertThat(operation.getResponses().get("200").getHeaders()).containsOnlyKeys("x-correlation-id");
        assertThat(operation.getResponses().get("200").getHeaders().get("x-correlation-id")).isSameAs(header);
        assertThat(reusable.getHeaders().get("X-Correlation-Id").get$ref())
                .isEqualTo("#/components/headers/XCorrelationId");
    }
}
